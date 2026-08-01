/** 角色仅 STUDENT / ADMIN（对齐 project_spec.md §0.2） */
export type UserRole = 'student' | 'admin'

export interface UserInfo {
  id: number
  nickname: string
  avatar: string
  role: UserRole
}

export interface UserStats {
  /** 发布数（菜品/档口/食堂贡献） */
  publishedCount?: number
  /** 待审数 */
  pendingCount?: number
  /** 收藏数：favorite 模块已移除、喜欢计数落库方案待评估，后端暂以 0 占位，前端不臆测语义 */
  favoriteCount?: number
  /** 评价数（StatsRow 三宫格唯一使用字段，后端 /auth/stats 权威来源） */
  reviewCount: number
  /** 历史兼容字段（旧契约），新逻辑以 reviewCount 为准 */
  likeCount?: number
}
