import type {
  DishListItem, DishDetail, DishQuery,
  DishAttribute, GuessLike, DishView,
} from '@/types/dish'
import { get } from './http'
import { fenToYuan } from '@/utils/money'
import { DEFAULT_PAGE_SIZE } from '@/constants/paging'
import {
  recordsOf, normalizeImages, type RawPage,
  type DishListItemVO, type DishDetailVO,
  type DishAttributeItem, type DishAttributeEditVO, type DishViewVO, type GuessLikeVO,
} from './shared'

/**
 * 列表行归一化（`DishListItemVO` 8 字段 → 端上 `DishListItem`）。
 * 入参用生成强类型：后端改名 / 改类型会**编译期报错**，而非真机上字段变空白。
 * 仍需归一化（有意的端上适配，非契约缺失）：① 金额分→元；② `canteenName`→`canteen`、
 * `avgRating`→`rating` 别名；③ 零值兜底。
 */
function toDishListItem(raw: DishListItemVO): DishListItem {
  return {
    id: Number(raw.id),
    name: raw.name || '',
    // 价格唯一数据源：展示值恒取 price（现价，已含折扣）；分 → 元
    price: fenToYuan(raw.price),
    // 原价（分→元）：空值不产出字段；是否折扣由展示层按 originalPrice > price 判定
    originalPrice: raw.originalPrice != null ? fenToYuan(raw.originalPrice) : null,
    coverImage: raw.coverImage || '',
    // 零评价 → null（消费方据此不渲染评分区）
    rating: raw.avgRating != null && raw.avgRating !== ('' as unknown) ? Number(raw.avgRating) : null,
    canteen: raw.canteenName || '',
    stallName: raw.stallName || '',
  }
}

/**
 * 详情描述属性项归一化：中文值**原样透出**（值即中文，端上零翻译）。
 * 后端 `value` 为 Object（单值字符串 / 多选数组），OpenAPI 未声明 itemSchema ⇒ 生成类型降级，
 * 故此处收窄为端上两种形态；非 `string` / `string[]` 时降级为 `''`（不抛错、不裂图）。
 */
function toDishAttribute(raw: DishAttributeItem): DishAttribute {
  const value = raw.value
  return {
    fieldKey: String(raw.fieldKey || ''),
    name: String(raw.name || ''),
    value: typeof value === 'string' || Array.isArray(value) ? (value as string | string[]) : '',
  }
}

/** 详情归一化（后端 `DishDetailVO` 11 字段 → 端上 `DishDetail`） */
function toDishDetail(raw: DishDetailVO): DishDetail {
  return {
    id: Number(raw.id),
    name: raw.name || '',
    price: fenToYuan(raw.price),
    originalPrice: raw.originalPrice != null ? fenToYuan(raw.originalPrice) : null,
    description: raw.description || '',
    images: normalizeImages(raw.images),
    // 零评价 → null（消费方据此呈现「暂无评分」）
    rating: raw.avgRating != null && raw.avgRating !== ('' as unknown) ? Number(raw.avgRating) : null,
    canteen: raw.canteenName || '',
    stallName: raw.stallName || '',
    floor: raw.floor || '',
    // ===== 描述属性：值即中文 ⇒ 端上直渲 `value`，零映射表（R4） =====
    attributes: Array.isArray(raw.attributes) ? raw.attributes.map(toDishAttribute) : [],
  }
}

/**
 * 通用菜品检索（首页网格无限加载 + 搜索结果）：`GET /dishes`。
 * 仅支持 `keyword` / `view` / `page` / `pageSize` / `seed`（食堂 / 价格 / 排序筛选不提供，
 * 筛选与排序由服务端按所选视图唯一决定）。分页壳只有 `records`：调用方以「本页条数 < `pageSize`」判到底。
 */
export async function searchDishesPage(query: DishQuery): Promise<{ list: DishListItem[] }> {
  const params: Record<string, unknown> = {
    page: query.page ?? 1,
    pageSize: query.pageSize ?? DEFAULT_PAGE_SIZE,
  }
  if (query.keyword) params.keyword = query.keyword
  if (query.view) params.view = query.view
  if (query.seed) params.seed = query.seed

  const res = await get<RawPage<DishListItemVO>>('/dishes', params)
  return { list: recordsOf<DishListItemVO>(res).map(toDishListItem) }
}

/** `searchDishesPage` 的平铺形态（find 搜索流消费） */
export async function searchDishes(query: DishQuery): Promise<DishListItem[]> {
  return (await searchDishesPage(query)).list
}

export async function getDishDetail(id: number): Promise<DishDetail> {
  const raw = await get<DishDetailVO>(`/dishes/${id}`)
  return toDishDetail(raw)
}

/**
 * 猜你喜欢（`GET /dishes/for-you`）。
 *
 * 传入**会话级** `seed`（可选）⇒ 服务端按 `CRC32(seed:ID)` 稳定伪随机序取数：
 * 同一次会话内多次进入发现态拿到同一批菜品名，**重进小程序**才整体重洗
 * ；不传 ⇒ 服务端退回 `ORDER BY RAND()` 真随机（向后兼容）。
 * 出参只有 `name`；条数与文案由服务端决定，端上不写死、不排序。
 */
export async function listGuessLike(seed?: string): Promise<GuessLike[]> {
  const params: Record<string, unknown> = {}
  if (seed) params.seed = seed

  const raw = await get<GuessLikeVO[]>('/dishes/for-you', params)
  return (raw || []).map((item) => ({
    name: String(item.name || ''),
  }))
}

/** 首页筛选视图字典（GET /dishes/views）：横向筛选栏数据源，文案与顺序全由后端下发 */
export async function listDishViews(): Promise<DishView[]> {
  const raw = await get<DishViewVO[]>('/dishes/views')
  // 端上只认 key + label（无 null 特例：默认视图「为你推荐」也是普通 key）
  return (raw || []).map((item) => ({
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
interface DishEditAttribute {
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
export async function listDishEditAttributes(dishId: number): Promise<DishEditAttribute[]> {
  const raw = await get<DishAttributeEditVO[]>(`/dishes/${dishId}/attributes`)
  return (raw || []).map((item) => ({
    fieldKey: String(item.fieldKey || ''),
    valueType: item.valueType === 'multi' ? 'multi' : 'single',
    options: Array.isArray(item.options)
      ? (item.options as unknown[]).map((o) => String(o ?? '')).filter(Boolean)
      : [],
  }))
}
