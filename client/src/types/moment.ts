/** 社区动态（task-06，对齐 MomentVO：camelCase 对外字段） */
export type RelatedType = 'none' | 'dish' | 'stall'

/** 审核状态（与全局一致：pending/approved/rejected） */
export type AuditStatus = 'pending' | 'approved' | 'rejected'

export interface Moment {
  id: number
  userId: number
  userNickname: string
  userAvatar: string
  content: string
  /** 图片 URL 列表（VO 已解析逗号串为 List<String>） */
  images: string[]
  relatedType: RelatedType
  /** 关联对象 ID（relatedType=none 时为空） */
  relatedId?: number | null
  /** 关联对象名称（菜品名/档口名） */
  relatedName?: string | null
  /** 关联档口所属食堂名（仅 relatedType=stall 返回，跳档口详情需携带 navParams.canteen） */
  relatedCanteen?: string | null
  /** 审核状态（公开列表仅 approved） */
  auditStatus?: AuditStatus
  /** 退回原因（仅作者/管理员可见） */
  rejectReason?: string | null
  /** 「有用」计数 */
  usefulCount: number
  /** 当前用户是否已点「有用」（仅登录态返回；驱动卡片填充态） */
  useful?: boolean
  /** 评论数 */
  commentCount: number
  /** 下架状态：0=正常 1=下架 */
  status?: number
  /** 创建时间 */
  createdAt?: string
}

export interface MomentComment {
  id: number
  momentId: number
  userId: number
  userNickname: string
  userAvatar: string
  /** 父评论 ID（一层回复；顶层为 null） */
  parentId?: number | null
  /** 父评论昵称（回复 @昵称 展示） */
  replyToNickname?: string | null
  /** 评论图片（最多 3 张，复用 Moment 图床） */
  images?: string[] | null
  content: string
  /** 有用计数（task-12.4） */
  usefulCount?: number
  /** 当前用户是否已点过「有用」（task-12.4） */
  useful?: boolean
  createdAt?: string
}

/** 发布 / 编辑动态请求（对齐 MomentPublishReq） */
export interface MomentPublish {
  content: string
  images?: string[]
  relatedType?: RelatedType
  relatedId?: number | null
}

/** 评论发布请求（对齐 MomentCommentReq） */
export interface MomentCommentPublish {
  content: string
  parentId?: number | null
  images?: string[] | null
}

/** 有用切换结果 */
export interface MomentUsefulResult {
  useful: boolean
  usefulCount: number
}
