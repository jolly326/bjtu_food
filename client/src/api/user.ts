import type { UserInfo } from '@/types/user'
import { get, post, put, del } from './http'
import type { RawRow } from './shared'

/**
 * 账号信息映射（`UserInfoVO` **5 字段**：id / username / nickname / avatar / bindEmail）。
 *
 * 已删字段端上不再读取：verified（bindEmail 派生冗余，端上经 useUserStore().isVerified() 单点派生）、
 * email（恒 NULL，校园邮箱唯一来源 = bindEmail）、status、createdAt、guestShortId（端上按 id 现算）。
 */
function toUserInfo(raw: RawRow): UserInfo {
  return {
    id: Number(raw.id ?? 0),
    username: String(raw.username || ''),
    nickname: raw.nickname || '食客',
    avatar: raw.avatar || '',
    // 微信登录体系（§5.y）：bindEmail 由后端 wechat-login / verify-email / profile 返回（认证判据 = 其非空）
    bindEmail: raw.bindEmail || raw.bind_email || undefined,
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

/** 发送认证验证码（§5.y.5：校园邮箱由学号推导，仅需学号） */
export async function sendEmailCode(username: string): Promise<void> {
  await post('/auth/email-code', { username })
}

/**
 * 微信静默登录（§5.y.5 POST /auth/wechat-login）：wx.login code → 游客态账号 token+userInfo。
 * 出参 `data` = `LoginVO`（`token` + `userInfo`），故此处按包装结构取值。
 */
export async function wechatLogin(code: string): Promise<AuthResult> {
  const resp = await post<RawRow>('/auth/wechat-login', { code })
  const user = (resp?.userInfo || resp?.user || {}) as RawRow
  return {
    token: String(resp.token || ''),
    userInfo: toUserInfo(user),
  }
}

/**
 * 学号邮箱认证（§5.y.5 POST /auth/verify-email）：验证码绑定当前微信 → 落库 bindEmail
 * （认证态唯一写入点）；JWT 不含 bind_email、实时查库，不重发 token。
 * **出参 `data` 直接为 `UserInfoVO`（无 `userInfo` 外层包装）**。
 */
export async function verifyEmail(code: string): Promise<UserInfo> {
  const resp = await post<RawRow>('/auth/verify-email', { code })
  return toUserInfo(resp)
}

/** 读取当前账号信息（§5.y.5 GET /auth/profile：游客态亦可读，含 bindEmail —— 认证判据来源） */
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
