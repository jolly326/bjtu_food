import type {
  Dish, DishDetail, DishQuery,
  HotSearch,
} from '@/types/dish'
import { get, post } from './http'
import { fenToYuan, yuanToFen } from '@/utils/money'
import { recordsOf, totalOf, normalizeImages, type RawRow, type RawPage } from './shared'

/**
 * 描述四维机器值 → 中文展示值映射（**唯一真源**，`project_spec.md` §7.28）。
 *
 * 后端出参（公开 DishVO 与 `seed_data.sql`）一律为**英文机器值**，中文仅作展示：
 * - `dietType`：meat 荤 / half 半荤 / veg 素 / halal 清真
 * - `ingredients`（逗号分隔）：pork 猪 / beef 牛 / lamb 羊 / chicken 鸡 / duck 鸭 / fish 鱼虾 /
 *   egg 蛋 / tofu 豆制品 / mushroom 菌菇 / veg 青菜 / noodle 面 / rice 米
 * - `flavorTags`（逗号分隔）：spicy 辣 / numbing 麻 / sour 酸 / sweet 甜 / salty 咸 /
 *   umami 鲜 / light 清淡 / heavy 重口（吸收原「辣度」语义）
 * - `serveTemp`：hot 热食 / room 常温 / ice 冰
 *
 * 映射只在本 API 层完成一次（沿用原 `TAG_MAP` 模式），视图层（DishInfoCard）
 * 一律直取 `Dish` 上的中文串，**禁止在页面/组件层再做二次映射**。
 * 未命中映射的值按原样透传（兼容后端未来新增取值，不会丢维度）。
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
 * 后端行 → 端上 Dish 归一化（模块私有，2026-09-20 任务 6.3 零消费扫描确认外部零引用，
 * 唯一消费方为本文件 `searchDishesPage` / `getDishDetail`，故收敛为模块私有，PR-05）。
 */
function toDish(raw: RawRow): Dish {
  const images = normalizeImages(raw.images ?? raw.image)
  return {
    id: Number(raw.id),
    name: raw.name || '',
    // 价格唯一数据源：展示值恒取 price（现价，已含折扣）
    price: fenToYuan(raw.price),
    // 原价（分→元）：空值不产出字段；是否折扣由展示层按 originalPrice > price 判定
    originalPrice: raw.originalPrice != null ? fenToYuan(raw.originalPrice) : undefined,
    image: images[0] || '',
    images,
    rating: raw.avgRating ?? raw.rating ?? 0,
    ratingCount: raw.ratingCount ?? raw.rating_count ?? 0,
    description: raw.description || '',
    canteen: raw.canteenName || raw.canteen || '',
    stallName: raw.stallName || '',
    floor: raw.floor || '',
    // ===== 描述四维（英文机器值 → 中文展示值；视图层直取，禁二次映射） =====
    dietType: DIET_TYPE_MAP[String(raw.dietType || '')] || '',
    ingredients: toDisplayList(raw.ingredients, INGREDIENT_MAP),
    flavorTags: toDisplayList(raw.flavorTags, FLAVOR_TAG_MAP),
    serveTemp: SERVE_TEMP_MAP[String(raw.serveTemp || '')] || '',
  }
}

function toDishDetail(raw: RawRow): DishDetail {
  return {
    ...toDish(raw),
    ratingDistribution: raw.ratingDistribution || [],
  }
}

/**
 * 通用菜品检索（筛选结果页 + 首页无限加载）
 * 复用 GET /dishes，支持 keyword / canteenId / minPrice / maxPrice / sortBy / sortOrder / page / pageSize。
 * 金额 minPrice/maxPrice 由前端「元」在 API 层转「分」提交（§3.x 金额红线）。
 * 返回分页结果（list + total），供瀑布流无限加载去重与触底判断。
 */
export async function searchDishesPage(query: DishQuery): Promise<{ list: Dish[]; total: number }> {
  const params: Record<string, unknown> = {
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.keyword) params.keyword = query.keyword
  if (query.canteenId != null) params.canteenId = query.canteenId
  if (query.minPrice != null) params.minPrice = yuanToFen(query.minPrice)
  if (query.maxPrice != null) params.maxPrice = yuanToFen(query.maxPrice)
  if (query.sortBy) params.sortBy = query.sortBy
  if (query.sortOrder) params.sortOrder = query.sortOrder

  // MP-08：响应定型为分页载体 RawPage（行结构仍宽松 → RawRow），不再用裸 any
  const res = await get<RawPage>('/dishes', params)
  const list = recordsOf<RawRow>(res).map(toDish)
  return { list, total: totalOf(res) }
}

/** 兼容旧调用：返回平铺 Dish[]（find 搜索流消费） */
export async function searchDishes(query: DishQuery): Promise<Dish[]> {
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

/** 热搜 TOP10（GET /dishes/hot-search，一期为菜品热度派生的热门词条） */
export async function getHotSearch(): Promise<HotSearch[]> {
  // MP-08：热搜是裸数组响应，定型为 RawRow[]
  const raw = await get<RawRow[]>('/dishes/hot-search')
  // MP-03：HotSearch 收敛为 { keyword }——唯一消费方（find 热搜 chip）只读 keyword
  return (raw || []).map((item: RawRow) => ({
    keyword: item.keyword || '',
  }))
}

/**
 * 首页热门瀑布流：无限加载分页走 /dishes?sortBy=heat&sortOrder=desc；
 * price 为可选价格区间（元），透传既有 minPrice/maxPrice。
 * 辣度筛选项已随「辣度维度」下线（标签/辣度筛选入口 SHALL NOT 存在）。
 */
export async function getHotDishesPage(
  page: number,
  pageSize = 20,
  price?: { min?: number; max?: number },
): Promise<{ list: Dish[]; total: number }> {
  return searchDishesPage({ sortBy: 'heat', sortOrder: 'desc', page, pageSize, minPrice: price?.min, maxPrice: price?.max })
}
