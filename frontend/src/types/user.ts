export type UserRole = 'visitor' | 'student'

export interface UserInfo {
  id: number
  nickname: string
  avatar: string
  role: UserRole
}

export interface UserStats {
  favoriteCount: number
  reviewCount: number
}
