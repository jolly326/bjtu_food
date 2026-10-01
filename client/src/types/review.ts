/**
 * 评价类型（§7.40 R9 / R10）
 *
 * **双视角分型（R9）**：公开列表与本人视角是**两个互不包含的类型** ——
 * - `Review`（**公开视角，8 字段**）：`GET /dishes/{id}/reviews`
 * - `MyReview`（**本人视角，7 字段**）：`GET /my/reviews`
 *
 * 二者 SHALL NOT 用单一 interface 复用：公开场景下本人专属字段是「类型说有、实际没有」的不安全类型。
 *
 * **本人视角字段构成**：公开 5 个字段（`id` / `rating` / `content` / `images` / `createdAt`）
 * + `dishId` / `dishName` —— **不下发 `userId` / `userNickname` / `userAvatar`**
 * （三者恒等于本人、零信息）⇒ 评价卡在本人视角为**变体形态：不渲染头像 / 昵称**。
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
 * 我的评价（**本人视角，7 字段**）。
 *
 * `dishId` / `dishName` 为**本人视角必然返回**的字段，故定型为非可选
 * （消费方无需再做 `?? ''` 之类的兜底判断）。
 */
export interface MyReview {
  id: number
  rating: number
  content: string
  createdAt: string
  /** 评价配图（COS URL，≤3 张；无图统一空数组） */
  images?: string[]
  /** 关联菜品 ID（本人视角专属；公开列表不返回 —— 归属已由路径 / 上下文表达） */
  dishId: number
  /** 关联菜品名称（后端联表 `dish` 补齐；本页必展示，用于辨识是哪道菜） */
  dishName: string
}

// 评价排序唯一为时间倒序（新评价在前），端上不持有排序状态、不传 sort（PR-02 / PR-05：零消费类型不留存）。
