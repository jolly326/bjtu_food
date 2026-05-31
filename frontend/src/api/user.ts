import type { UserInfo, UserStats } from '@/types/user'
import { post, put, get } from './http'

const MOCK_USER: UserInfo = { id: 1, nickname: '交大学子', avatar: '', role: 'student' }
const MOCK_STATS: UserStats = { favoriteCount: 12, reviewCount: 8 }

export async function login(code: string, studentId: string): Promise<{ token: string; userInfo: UserInfo }> {
  try {
    const resp: any = await post('/auth/login', { username: studentId, password: code })
    return {
      token: resp.token,
      userInfo: {
        id: resp.userId,
        nickname: resp.nickname,
        avatar: resp.avatar || '',
        role: (resp.role as UserInfo['role']) || 'student',
      },
    }
  } catch {
    console.log('[user] login 失败，使用 mock')
    const fallback = { token: 'mock_token_' + Date.now(), userInfo: { ...MOCK_USER, id: Number(studentId) || 1, nickname: studentId + '同学' } }
    return fallback
  }
}

export async function updateProfile(data: { nickname?: string; avatar?: string }): Promise<UserInfo> {
  try {
    const resp: any = await put('/auth/profile', data)
    return {
      id: resp?.id || 1,
      nickname: resp?.nickname || '交大学子',
      avatar: resp?.avatar || '',
      role: (resp?.role as UserInfo['role']) || 'student',
    }
  } catch {
    console.log('[user] updateProfile 失败，使用 mock')
    return { id: 1, nickname: data.nickname || '交大学子', avatar: data.avatar || '', role: 'student' }
  }
}

export async function getUserStats(): Promise<UserStats> {
  try {
    return await get('/auth/stats')
  } catch {
    console.log('[user] getUserStats 失败，使用 mock')
    return MOCK_STATS
  }
}
