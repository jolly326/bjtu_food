import { request } from './request'
import type { UserInfo } from '@/types/user'

export function login(code: string): Promise<{ token: string; userInfo: UserInfo }> {
  // MVP mock login
  return Promise.resolve({
    token: 'mock_token_' + Date.now(),
    userInfo: {
      id: 1,
      nickname: '交大学子',
      avatar: '',
      role: 'student',
    },
  })
}

export function getUserInfo(): Promise<UserInfo> {
  return Promise.resolve({
    id: 1,
    nickname: '交大学子',
    avatar: '',
    role: 'student',
  })
}
