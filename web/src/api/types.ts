/** 运营端接口出参与入参类型（字段口径见 docs/api/web/）。 */

export type Page<T> = { records: T[]; total: number }

export type CanteenOut = {
  id: number
  name: string
  /** 状态：`true` 正常 / `false` 已停用 */
  activate: boolean
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
}
export type StallOut = StallBody & {
  id: number
  canteenName: string
  /** 状态：`true` 正常 / `false` 已停用（出参必填） */
  activate: boolean
  dishCount: number
  updatedAt: string
}

export type DishBody = {
  stallId: number
  name: string
  imageUrl?: string | null
  /** 上架状态：true 在售 / false 已下架（列名 activate，见 schema/dish.md） */
  activate: boolean
}
export type DishOut = DishBody & {
  id: number
  stallName: string
  canteenName: string
  /** 点赞数：只读派生缓存列 = COUNT(dish_rating.liked = 1) */
  likeCount: number
  updatedAt: string
}

export type IssueOut = {
  id: number
  dishId: number
  dishName: string
  canteenName: string
  stallName: string
  optionName: string
  createdAt: string
  handledAt: string | null
  /** 该菜当前待处理纠错条数（服务端聚合；列表按它降序优先） */
  dishFeedbackCount: number
}

export type OptionOut = { id: number; name: string; refCount: number; updatedAt: string }
