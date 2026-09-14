/**
 * 评价类型（project_spec.md §3.x.6.4 / ARCH §1.3）
 * 语义统一：原 likeCount 语义统一重命名为 usefulCount（「有用」计数）。
 * 原因：后端 ReviewVO 新增 `usefulCount`，且「有用」走 /reviews/{id}/useful 幂等切换，
 * 与详情页底栏「喜欢」（likeCount）为两个独立概念，禁止混用（§0.6 红线 3）。
 */
export interface Review {
  id: number
  userId: number
  userNickname: string
  userAvatar: string
  dishId: number
  /** 关联菜品名称（我的评价列表由后端联表返回；菜品维度列表可不含） */
  dishName?: string
  rating: number
  content: string
  createTime: string
  /** 「有用」计数（后端 usefulCount） */
  usefulCount?: number
  /** 当前登录用户是否已标记「有用」（仅登录态返回，可选） */
  useful?: boolean
  /** 评价配图（COS URL，≤3 张；2026-09 恢复 UGC 配图，后端 /upload/images 安检后回传 URL） */
  images?: string[]
  /** 内容安检状态（后端 ReviewVO）：pass=通过对外可见；review=机审中，仅作者本人可见 */
  secState?: 'pass' | 'review'
  /** 管理侧隐藏标记（§7.14，后端 isHidden）：仅 /my/reviews 对作者本人返回。
   *  与 secState 语义不同：review=机审中（待过审、仍会对外展示），isHidden=已被隐藏（不再对外展示） */
  isHidden?: boolean
  // 评价扁平化（2026-08-18 决策）：移除楼中楼回复字段 parentId/replyToNickname/replies/repliesHasMore，
  // 菜品评价保留 评分+文字+图片+有用 的口碑形态
}

/** 评价排序方式：最新 / 最有用 */
export type ReviewSort = 'latest' | 'useful'
