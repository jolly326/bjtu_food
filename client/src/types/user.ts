/** 角色仅 STUDENT / ADMIN（对齐 project_spec.md §0.2） */
export type UserRole = 'student' | 'admin'

export interface UserInfo {
  id: number
  /** 学号/工号（校园身份，等于邮箱前缀） */
  username: string
  /** 校园邮箱（{学号}@bjtu.edu.cn） */
  email: string
  nickname: string
  avatar: string
  role: UserRole
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
