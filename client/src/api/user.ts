import type { UserInfo, UserStats } from '@/types/user'
import { get, post, put, del } from './http'

function toFrontendRole(role?: string): UserInfo['role'] {
  // 后端现已直接存储 student / admin（§0.2 仅两种角色），无需再做 USER→STUDENT 映射。
  // 直接透传并收敛类型：显式识别 admin，其余（含缺省/未知）归一为 student。
  return (role === 'admin' ? 'admin' : 'student') as UserInfo['role']
}

function toUserInfo(resp: any, fallbackId = 0): UserInfo {
  const user = resp?.userInfo || resp?.user || resp || {}
  // 后端 LoginResp 透传 userId/username/email/nickname/avatar/role（见 auth/dto/LoginResp）
  const username = String(user.username || resp?.username || '')
  return {
    // 后端恒返回 userId；0 仅作防御性兜底（不伪造有效用户 ID）
    id: Number(user.id ?? resp?.userId ?? fallbackId),
    username,
    // 校园邮箱 = {学号}@bjtu.edu.cn，后端无 email 时前端推导，保证字段恒有值
    email: user.email || resp?.email || deriveCampusEmail(username),
    nickname: user.nickname || resp?.nickname || user.username || '交大学子',
    avatar: user.avatar || resp?.avatar || '',
    role: toFrontendRole(user.role || resp?.role),
  }
}

export interface AuthResult {
  token: string
  userInfo: UserInfo
}

/** 校园邮箱 = {学号}@bjtu.edu.cn，前端仅需学号，无需用户手动输入邮箱 */
export function deriveCampusEmail(username: string): string {
  return `${username.trim().toLowerCase()}@bjtu.edu.cn`
}

export async function sendEmailCode(username: string, purpose: 'login' | 'register' | 'reset'): Promise<void> {
  await post('/auth/email-code', { username, purpose })
}

export async function loginByPassword(account: string, password: string): Promise<AuthResult> {
  const resp = await post<any>('/auth/login', { account, password })
  return {
    token: resp.token,
    userInfo: toUserInfo(resp),
  }
}

export async function loginByEmailCode(account: string, code: string): Promise<AuthResult> {
  const resp = await post<any>('/auth/login', { account, code })
  return {
    token: resp.token,
    userInfo: toUserInfo(resp),
  }
}

export async function register(data: {
  username: string
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
  username: string
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

/** 账号注销（STU，DELETE /my/account，逻辑删除 + 失效 token，task-12.8）
 *  需 body { confirm: true } 二次确认（后端契约 A.17）；成功即 token 失效，前端清 token 跳登录。 */
export async function deleteAccount(): Promise<void> {
  await del<void>('/my/account', { confirm: true })
}
