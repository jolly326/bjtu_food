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
  rating: number
  content: string
  images: string[]
  createTime: string
  /** 「有用」计数（后端 usefulCount） */
  usefulCount?: number
  /** 当前登录用户是否已标记「有用」（仅登录态返回，可选） */
  useful?: boolean
}

export interface ReviewSubmit {
  dishId: number
  rating: number
  content: string
  images: string[]
  /** 是否同步为社区动态（评价与动态打通：同步的动态直接可见，无需审核） */
  shareToMoment?: boolean
}

/** 评价排序方式：最新 / 最有用 */
export type ReviewSort = 'latest' | 'useful'
