/**
 * 评价类型（§7.40 R9 / R10）
 *
 * **双视角分型（R9）**：公开列表与本人视角是两个类型 ——
 * - `Review`（公开，8 字段）：`GET /dishes/{id}/reviews`
 * - `MyReview`（本人，10 字段 = 公开 8 + `dishId` / `dishName`）：`GET /my/reviews`
 *
 * 二者 SHALL NOT 用单一 interface 复用：公开场景下本人专属字段是「类型说有、实际没有」的不安全类型。
 *
 * **时间字段唯一为 `createdAt`（R10）**：全链路统一 `createdAt`，不使用别名 `createTime`。
 *
 * 评价类型不含「有用」计数 `usefulCount` 与已赞态 `useful`（无投票端点、字段、排序口径与端上按钮）。
 */
export interface Review {
  id: number
  userId: number
  userNickname: string
  userAvatar: string
  rating: number
  content: string
  /** 评价发表时间（后端 `createdAt`；重新评价后取新时间） */
  createdAt: string
  /** 评价配图（COS URL，≤3 张；无图统一空数组） */
  images?: string[]
}

/**
 * 我的评价（**本人视角，10 字段**）= 公开 `Review` + 以下两字段。
 *
 * 三字段**必填**（非可选）：本人视角（`GET /my/reviews`）**必然返回**它们，
 * 类型上直接表达该事实，消费方无需再做 `?? ''` 之类的兜底判断。
 */
export interface MyReview extends Review {
  /** 关联菜品 ID（本人视角专属；公开列表不返回 —— 归属已由路径 / 上下文表达） */
  dishId: number
  /** 关联菜品名称（后端 mapper 联表 `dish` 补齐） */
  dishName: string
}

/**
 * 评价提交成功回调载荷（写评价 / 重新评价共用的 UI 事件契约，唯一声明处）。
 * 由 `useDishPage.onReviewSubmitted` 与 `ReviewComposer` 共同消费：
 * - `mode`：`create` 首次发表 / `update` 覆盖式重评（决定是否本地写回「我的评价」态）；
 * - `reviewId`：评价 ID——`update` 为本人评价 ID、`create` 为 POST 出参返回的新评价 ID；
 *   两种模式均**本地写回**底栏态，无须回读「我的评价」；
 * - `rating` / `content` / `images`：提交后的最新值，供底栏态与预填本地写回。
 */
export interface ReviewSubmittedPayload {
  mode: 'create' | 'update'
  reviewId: number
  rating: number
  content: string
  images: string[]
}

// 评价排序唯一为时间倒序（新评价在前），端上不持有排序状态、不传 sort（PR-02 / PR-05：零消费类型不留存）。
