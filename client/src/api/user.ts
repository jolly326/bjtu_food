import type { UserInfo } from '@/types/user'
import { get, post, put, del } from './http'
import type { RawRow } from './shared'

function toUserInfo(resp: RawRow, fallbackId = 0): UserInfo {
  const user = resp?.userInfo || resp?.user || resp || {}
  // 后端四条账号信息链路透传 id/username/nickname/avatar/verified/bindEmail（恰 6 字段，2026-09-21 spec §7.32；
  // role 字段已随 user.role 列退役移除，2026-09-15）。
  // 已删字段端上不再读取：email（恒 NULL，校园邮箱唯一来源 = bindEmail）、status、guestShortId（端上按 id 现算）。
  return {
    // 后端恒返回 userId；0 仅作防御性兜底（不伪造有效用户 ID）
    id: Number(user.id ?? resp?.userId ?? fallbackId),
    username: String(user.username || resp?.username || ''),
    nickname: user.nickname || resp?.nickname || '食客',
    avatar: user.avatar || resp?.avatar || '',
    // 微信登录体系（§5.y）：verified / bindEmail 由后端 wechat-login / verify-email / profile 返回
    verified: !!(user.verified ?? resp?.verified),
    bindEmail: user.bindEmail || resp?.bindEmail || user.bind_email || undefined,
  }
}

interface AuthResult {
  token: string
  userInfo: UserInfo
}

/** 校园邮箱 = {学号}@bjtu.edu.cn，前端仅需学号，无需用户手动输入邮箱 */
export function deriveCampusEmail(username: string): string {
  return `${username.trim().toLowerCase()}@bjtu.edu.cn`
}

/** 发送认证验证码（§5.y.5：purpose 收窄为 verify 认证用途） */
export async function sendEmailCode(username: string, purpose: 'verify'): Promise<void> {
  await post('/auth/email-code', { username, purpose })
}

/** 微信静默登录（§5.y.5 POST /auth/wechat-login）：wx.login code → 游客态账号 token+userInfo */
export async function wechatLogin(code: string): Promise<AuthResult> {
  const resp = await post<RawRow>('/auth/wechat-login', { code })
  return {
    token: resp.token,
    userInfo: toUserInfo(resp),
  }
}

/** 学号邮箱认证（§5.y.5 POST /auth/verify-email）：验证码绑定当前微信 → verified=true */
export async function verifyEmail(code: string): Promise<AuthResult> {
  const resp = await post<RawRow>('/auth/verify-email', { code })
  return {
    token: resp.token,
    userInfo: toUserInfo(resp),
  }
}

/** 读取当前账号信息（§5.y.5 GET /auth/profile：游客态亦可读，含 verified/bindEmail） */
export async function getProfile(): Promise<UserInfo> {
  const resp = await get<RawRow>('/auth/profile')
  return toUserInfo(resp)
}

export async function updateProfile(data: { nickname?: string; avatar?: string }): Promise<UserInfo> {
  const resp = await put<RawRow>('/auth/profile', data)
  return toUserInfo(resp)
}

/**
 * 注销账号（合规：DELETE /auth/account）：账号匿名化（评价/反馈保留但去身份化）。
 * skipAuthRetry：401 时禁止「静默登录后重试」——静默登录可能建出新游客号，重试会误删新账号。
 */
export async function deleteAccount(): Promise<void> {
  await del('/auth/account', undefined, { skipAuthRetry: true })
}
