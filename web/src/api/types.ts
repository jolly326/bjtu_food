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
  /** 食堂必填：菜品最小定位信息（学生端据此能找到地方） */
  canteenId: number
  /** 档口可选：未指定时菜品只挂在食堂（上下架仍生效） */
  stallId?: number | null
  /** 楼层可选：缺省回落档口楼层 */
  floor?: string | null
  name: string
  imageUrl?: string | null
  /** 上架状态：true 在售 / false 已下架（列名 activate，见 schema/dish.md） */
  activate: boolean
}
export type DishOut = DishBody & {
  id: number
  canteenName: string
  stallName: string
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
