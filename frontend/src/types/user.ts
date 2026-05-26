export type UserRole = 'visitor' | 'student'

export interface UserInfo {
  id: number
  nickname: string
  avatar: string
  role: UserRole
}

export interface UserState {
  token: string
  userInfo: UserInfo | null
  role: UserRole
}
