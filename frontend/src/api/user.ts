import type { UserInfo, UserStats } from '@/types/user'
import { get, post, put, del } from './http'

function toFrontendRole(role?: string): UserInfo['role'] {
  // 后端现已直接存储 student / admin（§0.2 仅两种角色），无需再做 USER→STUDENT 映射。
  // 直接透传并收敛类型：显式识别 admin，其余（含缺省/未知）归一为 student。
  return (role === 'admin' ? 'admin' : 'student') as UserInfo['role']
}

function toUserInfo(resp: any, fallbackId = 1): UserInfo {
  const user = resp?.userInfo || resp?.user || resp || {}
  return {
    id: Number(user.id ?? resp?.userId ?? fallbackId),
    nickname: user.nickname || resp?.nickname || user.username || '交大学子',
    avatar: user.avatar || resp?.avatar || '',
    role: toFrontendRole(user.role || resp?.role),
  }
}

export interface AuthResult {
  token: string
  userInfo: UserInfo
}

export async function sendEmailCode(email: string, purpose: 'login' | 'register' | 'reset'): Promise<void> {
  await post('/auth/email-code', { email, purpose })
}

export async function loginByPassword(account: string, password: string): Promise<AuthResult> {
  const resp = await post<any>('/auth/login', { account, password })
  return {
    token: resp.token,
    userInfo: toUserInfo(resp),
  }
}

export async function loginByEmailCode(email: string, code: string): Promise<AuthResult> {
  const resp = await post<any>('/auth/login', { email, code })
  return {
    token: resp.token,
    userInfo: toUserInfo(resp),
  }
}

export async function register(data: {
  username: string
  email: string
  code: string
  password: string
  nickname: string
}): Promise<AuthResult> {
  const resp = await post<any>('/auth/register', data)
  return {
    token: resp.token,
    userInfo: toUserInfo(resp),
  }
}

export async function resetPassword(data: {
  email: string
  code: string
  newPassword: string
}): Promise<void> {
  await put('/auth/password/reset', data)
}

export async function updateProfile(data: { nickname?: string; avatar?: string }): Promise<UserInfo> {
  const resp = await put<any>('/auth/profile', data)
  return toUserInfo(resp)
}

export async function getUserStats(): Promise<UserStats> {
  return await get('/auth/stats')
}

/** 账号注销（STU，DELETE /my/account，逻辑删除 + 失效 token，task-12.8） */
export async function deleteAccount(): Promise<void> {
  await del<void>('/my/account')
}
