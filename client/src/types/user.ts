/** 角色仅 STUDENT / ADMIN（对齐 project_spec.md §0.2） */
type UserRole = 'student' | 'admin'

export interface UserInfo {
  id: number
  /** 学号/工号（校园身份，等于邮箱前缀）；游客态为 'wx_'+openid 尾 16 位 */
  username: string
  /** 校园邮箱（{学号}@bjtu.edu.cn）；游客态未认证时为空 */
  email: string
  nickname: string
  avatar: string
  role: UserRole
  /** 是否已邮箱认证（微信登录体系 §5.y）：true 解锁社区写操作 */
  verified: boolean
  /** 已认证绑定邮箱（bind_email，仅展示用，不公开传播）；未认证为 undefined */
  bindEmail?: string
  /** 游客展示短 ID（后端「食客+ID 尾 4 位」）；未提供时前端本地游客 ID 兜底 */
  guestShortId?: string
}

export interface UserStats {
  /** 发布数（菜品/档口/食堂贡献） */
  publishedCount?: number
  /** 待审数 */
  pendingCount?: number
  /** 评价数（后端 /auth/stats 权威来源） */
  reviewCount: number
  /** 历史兼容字段（旧契约），新逻辑以 reviewCount 为准 */
  likeCount?: number
}
