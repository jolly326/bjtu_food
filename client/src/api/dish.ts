import type {
  DishListItem, DishDetail, DishQuery,
  GuessLike,
} from '@/types/dish'
import { get, post } from './http'
import { fenToYuan } from '@/utils/money'
import { recordsOf, totalOf, normalizeImages, type RawRow, type RawPage } from './shared'

/**
 * 描述四维机器值 → 中文展示值映射（**唯一真源**，`project_spec.md` §7.28）。
 *
 * 后端出参（公开 `DishDetailVO` 与 `seed_data.sql`）一律为**英文机器值**，中文仅作展示：
 * - `dietType`：meat 荤 / half 半荤 / veg 素 / halal 清真
 * - `ingredients`（逗号分隔）：pork 猪 / beef 牛 / lamb 羊 / chicken 鸡 / duck 鸭 / fish 鱼虾 /
 *   egg 蛋 / tofu 豆制品 / mushroom 菌菇 / veg 青菜 / noodle 面 / rice 米
 * - `flavorTags`（逗号分隔）：spicy 辣 / numbing 麻 / sour 酸 / sweet 甜 / salty 咸 /
 *   umami 鲜 / light 清淡 / heavy 重口（吸收原「辣度」语义）
 * - `serveTemp`：hot 热食 / room 常温 / ice 冰
 *
 * 映射只在本 API 层完成一次，视图层（`DishInfoCard`）一律直取 `DishDetail` 上的中文串，
 * **禁止在页面/组件层再做二次映射**；未命中映射的值按原样透传（兼容后端新增取值）。
 */
const DIET_TYPE_MAP: Record<string, string> = { meat: '荤', half: '半荤', veg: '素', halal: '清真' }
const SERVE_TEMP_MAP: Record<string, string> = { hot: '热食', room: '常温', ice: '冰' }
const INGREDIENT_MAP: Record<string, string> = {
  pork: '猪', beef: '牛', lamb: '羊', chicken: '鸡', duck: '鸭', fish: '鱼虾',
  egg: '蛋', tofu: '豆制品', mushroom: '菌菇', veg: '青菜', noodle: '面', rice: '米',
}
const FLAVOR_TAG_MAP: Record<string, string> = {
  spicy: '辣', numbing: '麻', sour: '酸', sweet: '甜',
  salty: '咸', umami: '鲜', light: '清淡', heavy: '重口',
}

/**
 * 多值字段（`ingredients` / `flavorTags`，逗号分隔英文机器值）→ 端上展示串（顿号分隔中文）。
 * 逐项查表映射，未命中按原样透传；入参为空/非字符串时返回空串，由消费方按「空则该维不渲染」处理。
 */
function toDisplayList(raw: unknown, map: Record<string, string>): string {
  if (raw == null) return ''
  return String(raw)
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => map[s] || s)
    .join('、')
}

/**
 * 列表行归一化（后端 `DishListItemVO` 8 字段 → 端上 `DishListItem`）。
 * <p>
 * 2026-09-22 列表 / 详情出参拆分：列表**只有 `coverImage` 单值**（不再有 `images` 数组，
 * 也不再派生 `image`）；`description` / `floor` / `ratingCount` / 四维等详情专属字段**不再映射**。
 */
function toDishListItem(raw: RawRow): DishListItem {
  return {
    id: Number(raw.id),
    name: raw.name || '',
    // 价格唯一数据源：展示值恒取 price（现价，已含折扣）；分 → 元
    price: fenToYuan(raw.price),
    // 原价（分→元）：空值不产出字段；是否折扣由展示层按 originalPrice > price 判定
    originalPrice: raw.originalPrice != null ? fenToYuan(raw.originalPrice) : undefined,
    coverImage: raw.coverImage || '',
    rating: raw.avgRating ?? raw.rating ?? 0,
    canteen: raw.canteenName || raw.canteen || '',
    stallName: raw.stallName || '',
  }
}

/** 详情归一化（后端 `DishDetailVO` 15 字段 + 评分分布 → 端上 `DishDetail`） */
function toDishDetail(raw: RawRow): DishDetail {
  const images = normalizeImages(raw.images ?? raw.image)
  return {
    id: Number(raw.id),
    name: raw.name || '',
    price: fenToYuan(raw.price),
    originalPrice: raw.originalPrice != null ? fenToYuan(raw.originalPrice) : undefined,
    description: raw.description || '',
    images,
    image: images[0] || '',
    rating: raw.avgRating ?? raw.rating ?? 0,
    ratingCount: raw.ratingCount ?? raw.rating_count ?? 0,
    canteen: raw.canteenName || raw.canteen || '',
    stallName: raw.stallName || '',
    floor: raw.floor || '',
    // ===== 描述四维（英文机器值 → 中文展示值；视图层直取，禁二次映射） =====
    dietType: DIET_TYPE_MAP[String(raw.dietType || '')] || '',
    ingredients: toDisplayList(raw.ingredients, INGREDIENT_MAP),
    flavorTags: toDisplayList(raw.flavorTags, FLAVOR_TAG_MAP),
    serveTemp: SERVE_TEMP_MAP[String(raw.serveTemp || '')] || '',
    ratingDistribution: raw.ratingDistribution || [],
  }
}

/**
 * 通用菜品检索（首页网格无限加载 + 搜索结果）。
 * <p>
 * 复用 `GET /dishes`，**仅支持 `keyword` / `mealType` / `page` / `pageSize`**（2026-09-22 起
 * 食堂 / 价格 / 排序筛选全量下线，端上不再传 `canteenId` / `minPrice` / `maxPrice` / `sortBy`；
 * 排序恒为服务端热度倒序）。返回分页结果供瀑布流去重与「本页条数 < pageSize」判到底。
 */
export async function searchDishesPage(query: DishQuery): Promise<{ list: DishListItem[]; total: number }> {
  const params: Record<string, unknown> = {
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.keyword) params.keyword = query.keyword
  if (query.mealType) params.mealType = query.mealType

  // MP-08：响应定型为分页载体 RawPage（行结构仍宽松 → RawRow），不再用裸 any
  const res = await get<RawPage>('/dishes', params)
  const list = recordsOf<RawRow>(res).map(toDishListItem)
  return { list, total: totalOf(res) }
}

/** 兼容旧调用：返回平铺 `DishListItem[]`（find 搜索流消费） */
export async function searchDishes(query: DishQuery): Promise<DishListItem[]> {
  return (await searchDishesPage(query)).list
}

export async function getDishDetail(id: number): Promise<DishDetail> {
  // MP-08：详情是单行响应，定型为 RawRow
  const raw = await get<RawRow>(`/dishes/${id}`)
  return toDishDetail(raw)
}

/**
 * 上报菜品浏览（POST /dishes/{id}/views，供 view_count / 热度排序派生使用）。
 * 后端 addView 通过 token 取当前用户（SecurityUtil.getCurrentUserId），无需 body；
 * 需登录态（/dishes/** 仅 GET 公开）。浏览埋点属非关键链路，失败静默。
 */
export async function addView(id: number): Promise<void> {
  try {
    await post<void>(`/dishes/${id}/views`)
  } catch {
    /* 静默失败：浏览统计不应阻塞详情展示 */
  }
}

/**
 * 猜你喜欢（`GET /dishes/for-you`）。
 * 2026-09-22 change search-page-refresh：原 `/dishes/hot-search` 改名 + 语义变更为
 * **每次随机抽取在售菜品名**（服务端已去缓存，否则随机退化为全站同一份）。
 */
export async function getGuessLike(): Promise<GuessLike[]> {
  // 裸数组响应，定型为 RawRow[]
  const raw = await get<RawRow[]>('/dishes/for-you')
  // 唯一消费方（find 页「猜你喜欢」chip）只读 keyword
  return (raw || []).map((item: RawRow) => ({
    keyword: item.keyword || '',
  }))
}

/** 菜品大类字典（GET /dishes/meal-types）：首页横向标签栏数据源，文案与顺序全由后端下发 */
export async function getMealTypes(): Promise<{ key: string; label: string; order: number }[]> {
  const raw = await get<RawRow[]>('/dishes/meal-types')
  return (raw || []).map((item: RawRow) => ({
    key: String(item.key || ''),
    label: String(item.label || ''),
    order: Number(item.order ?? 0),
  }))
}
