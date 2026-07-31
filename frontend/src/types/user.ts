/** 角色仅 STUDENT / ADMIN（对齐 project_spec.md §0.2） */
export type UserRole = 'student' | 'admin'

export interface UserInfo {
  id: number
  nickname: string
  avatar: string
  role: UserRole
}

export interface UserStats {
  likeCount: number
  reviewCount: number
}
