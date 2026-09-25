import type {
  DishListItem, DishDetail, DishQuery,
  GuessLike,
} from '@/types/dish'
import { get } from './http'
import { fenToYuan } from '@/utils/money'
import { recordsOf, totalOf, normalizeImages, type RawRow, type RawPage } from './shared'

/**
 * 多值维**机器值**归一：数组 / 逗号分隔串 → `string[]`。
 *
 * 为何需要归一：R4 把 `dish.ingredients` / `dish.flavor_tags` 列改为 JSON 数组存储（后端出参随之
 * 由「逗号分隔串」变为 JSON 数组），而端上改造（本节）与库表改造分属两批 —— 本函数**同时兼容两种形态**，
 * 使端上无需随库表批次再改一次。
 *
 * 注意：本层**不做「机器值 → 中文」映射** —— 中文一律由四维字典端点提供
 * （`stores/dish-attribute` 的 `labelsOf`），端上零硬编码映射表（§7.40 R4）。
 */
function toMachineList(raw: unknown): string[] {
  if (Array.isArray(raw)) return raw.map((v) => String(v).trim()).filter(Boolean)
  if (raw == null) return []
  return String(raw)
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

/**
 * 列表行归一化（后端 `DishListItemVO` 8 字段 → 端上 `DishListItem`）。
 * <p>
 * 列表**只有 `coverImage` 单值**（不再有 `images` 数组，
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
    // ===== 描述四维：**下发机器值**（中文由四维字典端点提供，端上零硬编码映射表，§7.40 R4） =====
    dietType: String(raw.dietType || ''),
    ingredients: toMachineList(raw.ingredients),
    flavorTags: toMachineList(raw.flavorTags),
    serveTemp: String(raw.serveTemp || ''),
    ratingDistribution: raw.ratingDistribution || [],
  }
}

/**
 * 通用菜品检索（首页网格无限加载 + 搜索结果）。
 * <p>
 * 复用 `GET /dishes`，**仅支持 `keyword` / `mealType` / `page` / `pageSize`**（
 * 食堂 / 价格 / 排序筛选不提供，端上不传 `canteenId` / `minPrice` / `maxPrice` / `sortBy`；
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
 * 猜你喜欢（`GET /dishes/for-you`）。
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
