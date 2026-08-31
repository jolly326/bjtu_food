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
  /** 评价扁平化（2026-08-18 决策）：移除楼中楼回复字段 parentId/replyToNickname/replies/repliesHasMore，
      讨论沉淀到动态评论区，菜品评价仅保留 评分+图文+有用 的口碑形态 */
}

export interface ReviewSubmit {
  dishId: number
  rating: number
  content: string
  images: string[]
  /** 美团式写评标签（口味/分量/性价比…），可选 */
  tags?: string[]
  /** 是否同步为社区动态；拆改后评价不再打通动态，固定 false */
  shareToMoment?: boolean
}

/** 评价排序方式：最新 / 最有用 */
export type ReviewSort = 'latest' | 'useful'
