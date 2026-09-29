import type {
  DishListItem, DishDetail, DishQuery,
  DishAttributeItem, GuessLike, DishView,
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

/** 详情描述属性项归一化：中文值**原样透出**（值即中文，端上零翻译） */
function toDishAttributeItem(raw: RawRow): DishAttributeItem {
  return {
    fieldKey: String(raw.fieldKey || ''),
    name: String(raw.name || ''),
    value: raw.value ?? '',
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
    // ===== 描述属性：值即中文 ⇒ 端上直渲 `value`，零映射表（R4） =====
    attributes: Array.isArray(raw.attributes) ? raw.attributes.map(toDishAttributeItem) : [],
  }
}

/**
 * 通用菜品检索（首页网格无限加载 + 搜索结果）。
 * <p>
 * 复用 `GET /dishes`，**仅支持 `keyword` / `view` / `page` / `pageSize` / `seed`**
 * （食堂 / 价格 / 排序筛选不提供；筛选与排序由服务端按所选视图唯一决定）。
 * 分页壳只有 `records`：调用方以「本页返回条数 < `pageSize`」判到底。
 */
export async function searchDishesPage(query: DishQuery): Promise<{ list: DishListItem[] }> {
  const params: Record<string, unknown> = {
    page: query.page ?? 1,
    pageSize: query.pageSize ?? 20,
  }
  if (query.keyword) params.keyword = query.keyword
  if (query.view) params.view = query.view
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

/** 首页筛选视图字典（GET /dishes/views）：横向筛选栏数据源，文案与顺序全由后端下发 */
export async function getDishViews(): Promise<DishView[]> {
  const raw = await get<RawRow[]>('/dishes/views')
  // 端上只认 key + label（无 null 特例：默认视图「为你推荐」也是普通 key）
  return (raw || []).map((item: RawRow) => ({
    key: String(item.key ?? ''),
    label: String(item.label || ''),
  }))
}

/**
 * 编辑态属性维度项（`GET /dishes/{id}/attributes` 出参）。
 *
 * **按需**（进菜品纠错编辑界面时才请求）：只返回**该菜现有维度**的参考候选
 * —— 维度名与当前值在 `GET /dishes/{id}` 里已有，本端点**不重复下发**。
 */
export interface DishEditAttribute {
  /** 维度键（camelCase），与详情 `attributes[].fieldKey` 对齐 */
  fieldKey: string
  /** 取值类型：`single`（单值）｜ `multi`（多值，值取数组） */
  valueType: 'single' | 'multi'
  /** 该维度参考候选值（中文文本，按频次倒序）；端上按序渲染 chips，亦可自由输入新值 */
  options: string[]
}

/**
 * 编辑态属性候选（`GET /dishes/{id}/attributes`）。
 * 失败由调用方静默处理（仍可自由填写），不阻塞编辑。
 */
export async function getDishEditAttributes(dishId: number): Promise<DishEditAttribute[]> {
  const raw = await get<RawRow[]>(`/dishes/${dishId}/attributes`)
  return (raw || []).map((item: RawRow) => ({
    fieldKey: String(item.fieldKey || ''),
    valueType: item.valueType === 'multi' ? 'multi' : 'single',
    options: Array.isArray(item.options)
      ? (item.options as unknown[]).map((o) => String(o ?? '')).filter(Boolean)
      : [],
  }))
}
