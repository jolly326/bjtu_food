/**
 * 评价类型（`specs/review-api-contract` · 2026-09-20 dish-detail-remediation）
 *
 * 公开评价行恰含 8 字段：id / userId / userNickname / userAvatar / rating / content /
 * images / createdAt（端上映射为 `createTime`）；`dishId` / `dishName` / `isHidden`
 * **仅「我的评价」**（GET /my/reviews）返回，公开列表（GET /dishes/{id}/reviews）不含。
 *
 * 已全链删除（SHALL NOT 回流）：「有用」计数 `usefulCount` 与已赞态 `useful`
 * （投票端点、字段、排序口径、端上按钮一并下线）。
 */
export interface Review {
  id: number
  userId: number
  userNickname: string
  userAvatar: string
  rating: number
  content: string
  /** 评价发表时间（后端 createdAt；重新评价后取新时间） */
  createTime: string
  /** 评价配图（COS URL，≤3 张；无图统一空数组） */
  images?: string[]
  /** ===== 以下三字段仅「我的评价」（GET /my/reviews）返回 ===== */
  /** 关联菜品 ID */
  dishId?: number
  /** 关联菜品名称（后端联表返回） */
  dishName?: string
  /** 管理侧隐藏标记：仅 /my/reviews 对作者本人返回（事后处置口径，非「已删除」） */
  isHidden?: boolean
}
/**
 * 评价提交成功回调载荷（写评价 / 重新评价共用的 UI 事件契约，唯一声明处）。
 * 由 `useDishPage.onReviewSubmitted` 与 `ReviewComposer` 共同消费：
 * - `mode`：`create` 首次发表 / `update` 覆盖式重评（决定是否本地写回「我的评价」态）；
 * - `rating` / `content` / `images`：提交后的最新值，供底栏态与预填本地写回。
 */
export interface ReviewSubmittedPayload {
  mode: 'create' | 'update'
  rating: number
  content: string
  images: string[]
}

// 原 `ReviewSort`（latest|useful）已于 2026-09-14 删除：评价排序唯一为时间倒序（新评价在前），
// 端上不持有排序状态、不传 sort（PR-02 / PR-05：零消费类型不留存）。
