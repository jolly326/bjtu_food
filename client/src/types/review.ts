/**
 * 评价类型（`specs/review-api-contract` · 2026-09-20 dish-detail-remediation）
 *
 * **双视角分型（2026-09-23 §7.40 R9）**：公开列表与本人视角是**两个类型** ——
 * - `Review`（公开，8 字段）：`GET /dishes/{id}/reviews`
 * - `MyReview`（本人，11 字段 = 公开 8 + `dishId` / `dishName` / `isHidden`）：`GET /my/reviews`
 *
 * 二者 SHALL NOT 用单一 interface 复用 —— 此前共用一个类型、靠「调哪个接口」区分字段集，
 * 属**类型系统无法表达**的契约模糊：公开场景下那 3 个字段是「类型说有、实际没有」的不安全类型。
 *
 * **时间字段唯一为 `createdAt`（2026-09-23 §7.40 R10）**：原端上映射别名 `createTime` 已删除 ——
 * 同一字段两个名会掩盖契约变更（后端改名时端上静默失效且无编译期保护），全链路统一 `createdAt`。
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
  /** 评价发表时间（后端 `createdAt`；重新评价后取新时间） */
  createdAt: string
  /** 评价配图（COS URL，≤3 张；无图统一空数组） */
  images?: string[]
}

/**
 * 我的评价（**本人视角，11 字段**）= 公开 `Review` + 以下三字段。
 *
 * 三字段**必填**（非可选）：本人视角（`GET /my/reviews`）**必然返回**它们，
 * 类型上直接表达该事实，消费方无需再做 `?? ''` 之类的兜底判断。
 */
export interface MyReview extends Review {
  /** 关联菜品 ID（本人视角专属；公开列表不返回 —— 归属已由路径 / 上下文表达） */
  dishId: number
  /** 关联菜品名称（后端 mapper 联表 `dish` 补齐） */
  dishName: string
  /** 管理侧隐藏标记：**仅本人视角返回**（本人可见被隐藏的评价，据此标注「已被隐藏」） */
  isHidden: boolean
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
