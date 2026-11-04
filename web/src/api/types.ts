/** 运营端接口出参与入参类型（字段口径见 docs/api/web/）。 */

export type Page<T> = { records: T[]; total: number }

export type CanteenOut = {
  id: number
  name: string
  /** 状态：`true` 正常 / `false` 已停用 */
  activate: boolean
  /** 封面图：出参为绝对 URL，可为空（仅运营端使用） */
  imageUrl?: string | null
  stallCount: number
  updatedAt: string
}

export type StallBody = {
  canteenId: number
  name: string
  /** 状态：入参可选（缺省 `true`）；出参必填，见 `StallOut` */
  activate?: boolean
  floor?: string | null
  windowNo?: string | null
  /** 封面图：入参为绝对 URL 或空（仅运营端使用） */
  imageUrl?: string | null
}
export type StallOut = StallBody & {
  id: number
  canteenName: string
  /** 状态：`true` 正常 / `false` 已停用（出参必填） */
  activate: boolean
  dishCount: number
  updatedAt: string
}

/** 位置入参：带 `id` 更新、不带 `id` 新增、未出现即删除（整表替换）。 */
export type DishLocationInput = {
  id?: number
  canteenId: number
  stallId?: number | null
  activate?: boolean
}

export type DishBody = {
  name: string
  /** 档案富文本：名称 + 口味 + 场景 + 特点 */
  description?: string | null
  imageUrl?: string | null
  /** 售价（分）；同一道菜的所有位置共用 */
  price?: number | null
  /** 位置列表（整表替换） */
  locations?: DishLocationInput[]
}

export type DishLocationOut = {
  id: number
  canteenId: number
  canteenName: string
  stallId: number | null
  stallName: string
  /** 楼层（取档口）；无档口为空串 */
  floor: string
  activate: boolean
}

export type DishOut = {
  id: number
  name: string
  description: string
  imageUrl?: string | null
  /** 售价（分） */
  price?: number | null
  /** 位置列表 */
  locations: DishLocationOut[]
  updatedAt: string
}

export type FeedbackType = 'product' | 'dish'

export type FeedbackOut = {
  id: number
  type: FeedbackType
  dishId: number | null
  dishName: string
  content: string
  createdAt: string
  handledAt: string | null
}
