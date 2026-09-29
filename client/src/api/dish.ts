import type {
  DishListItem, DishDetail, DishQuery,
  DishAttributeItem, GuessLike, MealType,
} from '@/types/dish'
import { get } from './http'
import { fenToYuan } from '@/utils/money'
import { recordsOf, normalizeImages, type RawRow, type RawPage } from './shared'

/**
 * 列表行归一化（后端 `DishListItemVO` 8 字段 → 端上 `DishListItem`）。
 * <p>
 * 列表**只有 `coverImage` 单值**（无 `images` 数组、也不派生 `image`）；
 * `description` / `floor` / `attributes` 等详情专属字段**不再映射**。
 */
function toDishListItem(raw: RawRow): DishListItem {
  return {
    id: Number(raw.id),
    name: raw.name || '',
    // 价格唯一数据源：展示值恒取 price（现价，已含折扣）；分 → 元
    price: fenToYuan(raw.price),
    // 原价（分→元）：空值不产出字段；是否折扣由展示层按 originalPrice > price 判定
    originalPrice: raw.originalPrice != null ? fenToYuan(raw.originalPrice) : null,
    coverImage: raw.coverImage || '',
    // 零评价 → null（消费方据此不渲染评分区）
    rating: raw.avgRating != null && raw.avgRating !== '' ? Number(raw.avgRating) : null,
    canteen: raw.canteenName || raw.canteen || '',
    stallName: raw.stallName || '',
  }
}

/** 详情描述属性项归一化：机器值与中文**原样透出**（后端已整理，端上零翻译） */
function toDishAttributeItem(raw: RawRow): DishAttributeItem {
  return {
    fieldKey: String(raw.fieldKey || ''),
    name: String(raw.name || ''),
    value: raw.value ?? '',
    label: raw.label ?? '',
  }
}

/** 详情归一化（后端 `DishDetailVO` 11 字段 → 端上 `DishDetail`） */
function toDishDetail(raw: RawRow): DishDetail {
  return {
    id: Number(raw.id),
    name: raw.name || '',
    price: fenToYuan(raw.price),
    originalPrice: raw.originalPrice != null ? fenToYuan(raw.originalPrice) : null,
    description: raw.description || '',
    images: normalizeImages(raw.images),
    // 零评价 → null（消费方据此呈现「暂无评分」）
    rating: raw.avgRating != null && raw.avgRating !== '' ? Number(raw.avgRating) : null,
    canteen: raw.canteenName || raw.canteen || '',
    stallName: raw.stallName || '',
    floor: raw.floor || '',
    // ===== 描述属性：后端已整理（机器值 + 中文）⇒ 端上直渲 `label`，零映射表（R4） =====
    attributes: Array.isArray(raw.attributes) ? raw.attributes.map(toDishAttributeItem) : [],
  }
}

/**
 * 通用菜品检索（首页网格无限加载 + 搜索结果）。
 * <p>
 * 复用 `GET /dishes`，**仅支持 `keyword` / `mealType` / `page` / `pageSize` / `seed`**
 * （食堂 / 价格 / 排序筛选不提供；排序由服务端唯一决定）。
 * 分页壳只有 `records`：调用方以「本页返回条数 < `pageSize`」判到底。
 */
export async function searchDishesPage(query: DishQuery): Promise<{ list: DishListItem[] }> {
  const params: Record<string, unknown> = {
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.keyword) params.keyword = query.keyword
  if (query.mealType) params.mealType = query.mealType
  if (query.seed) params.seed = query.seed

  // MP-08：响应定型为分页载体 RawPage（行结构仍宽松 → RawRow），不再用裸 any
  const res = await get<RawPage>('/dishes', params)
  return { list: recordsOf<RawRow>(res).map(toDishListItem) }
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
 * **每次随机抽取在售菜品名**（服务端已去缓存，否则随机退化为全站同一份）；出参只有 `name`。
 */
export async function getGuessLike(): Promise<GuessLike[]> {
  // 裸数组响应，定型为 RawRow[]
  const raw = await get<RawRow[]>('/dishes/for-you')
  // 唯一消费方（find 页「猜你喜欢」chip）只读 name
  return (raw || []).map((item: RawRow) => ({
    name: String(item.name || ''),
  }))
}

/** 菜品大类字典（GET /dishes/meal-types）：首页横向标签栏数据源，文案与顺序全由后端下发 */
export async function getMealTypes(): Promise<MealType[]> {
  const raw = await get<RawRow[]>('/dishes/meal-types')
  // 方案 B：首项后端下发 value=null（为你推荐）；其余项为枚举字符串
  return (raw || []).map((item: RawRow) => ({
    value: item.value != null && String(item.value).trim() !== '' ? String(item.value) : null,
    label: String(item.label || ''),
  }))
}

/**
 * 编辑态属性维度项（`GET /dishes/{id}/attributes` 出参）。
 *
 * **按需**（进菜品纠错编辑界面时才请求）：只返回**该菜现有维度**的可选项
 * —— 维度名与当前值在 `GET /dishes/{id}` 里已有，本端点**不重复下发**。
 */
export interface DishEditAttribute {
  /** 维度键（camelCase），与详情 `attributes[].fieldKey` 对齐 */
  fieldKey: string
  /** 取值类型：`single`（单值）｜ `multi`（多值，值取数组） */
  valueType: 'single' | 'multi'
  /** 该维度全部候选值（后端按序下发，端上按序渲染）；**空数组 = 自由文本维度** */
  options: { valueKey: string; label: string }[]
}

/**
 * 编辑态属性选项（`GET /dishes/{id}/attributes`）。
 * 失败由调用方静默处理（展示原始机器值），不阻塞编辑。
 */
export async function getDishEditAttributes(dishId: number): Promise<DishEditAttribute[]> {
  const raw = await get<RawRow[]>(`/dishes/${dishId}/attributes`)
  return (raw || []).map((item: RawRow) => ({
    fieldKey: String(item.fieldKey || ''),
    valueType: item.valueType === 'multi' ? 'multi' : 'single',
    options: Array.isArray(item.options)
      ? (item.options as RawRow[]).map((o) => ({
          valueKey: String(o.valueKey || ''),
          label: String(o.label || o.valueKey || ''),
        }))
      : [],
  }))
}
