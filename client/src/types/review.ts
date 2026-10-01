/**
 * 评价类型。
 *
 * **双视角分型**：公开列表与本人视角是**两个互不包含的类型**，SHALL NOT 合成一个 interface ——
 * 公开场景下本人专属字段会变成「类型说有、实际没有」的不安全类型。
 * - `Review`（公开视角，8 字段）：`GET /dishes/{id}/reviews`
 * - `MyReview`（本人视角，7 字段）：`GET /my/reviews`
 *
 * 时间字段唯一为 `createdAt`（全链路统一，不使用别名 `createTime`）。
 */
export interface Review {
  id: number
  userId: number
  userNickname: string
  userAvatar: string
  rating: number
  content: string
  /** 发表时间（重新评价后取新时间） */
  createdAt: string
  /** 配图（COS URL，≤3 张；无图统一空数组） */
  images?: string[]
}

/** 我的评价（本人视角，7 字段）：`dishId` / `dishName` 为必然返回字段，故定型为非可选，消费方无需兜底 */
export interface MyReview {
  id: number
  rating: number
  content: string
  createdAt: string
  /** 配图（COS URL，≤3 张；无图统一空数组） */
  images?: string[]
  /** 关联菜品 ID（本人视角专属） */
  dishId: number
  /** 关联菜品名称（后端联表补齐；用于辨识是哪道菜） */
  dishName: string
}
