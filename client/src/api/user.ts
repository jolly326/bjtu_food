import type { UserInfo } from '@/types/user'
import { get, post, put, del } from './http'
import type { UserInfoVO, LoginVO } from './shared'
import { DEFAULT_NICKNAME } from '@/constants/copy'

/**
 * 账号信息映射（`UserInfoVO` 4 字段：id / name / avatar / email）。
 * 不读取：`verified`（由 email 单点派生，非空即已认证）、
 * `status` / `createdAt` / `guestShortId` / `username`（身份副行统一渲染 email，裸学号不展示）。
 */
function toUserInfo(raw: UserInfoVO): UserInfo {
  return {
    id: Number(raw.id ?? 0),
    name: raw.name || DEFAULT_NICKNAME,
    avatar: raw.avatar || '',
    // 已认证绑定校园邮箱：后端 wechat-login / verify-email / profile 返回（认证判据 = 其非空）
    email: raw.email || undefined,
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

/** 发送认证验证码 */
export async function sendEmailCode(username: string): Promise<void> {
  await post('/auth/email-code', { username })
}

/**
 * 微信静默登录：wx.login code → 游客态账号 token+userInfo。
 * 出参 `data` = `LoginVO`（`token` + `userInfo`），故此处按包装结构取值。
 */
export async function wechatLogin(code: string): Promise<AuthResult> {
  const resp = await post<LoginVO>('/auth/wechat-login', { code })
  return {
    token: String(resp.token || ''),
    userInfo: toUserInfo(resp.userInfo ?? ({} as UserInfoVO)),
  }
}

/**
 * 学号邮箱认证：验证码绑定当前微信 → 写入 email
 * （认证态唯一写入点）；JWT 不含 email、实时查库，不重发 token。
 * **出参 `data` 直接为 `UserInfoVO`（无 `userInfo` 外层包装）**。
 */
export async function verifyEmail(code: string): Promise<UserInfo> {
  const resp = await post<UserInfoVO>('/auth/verify-email', { code })
  return toUserInfo(resp)
}

/** 读取当前账号信息 */
export async function getProfile(): Promise<UserInfo> {
  const resp = await get<UserInfoVO>('/auth/profile')
  return toUserInfo(resp)
}

export async function updateProfile(data: { name?: string; avatar?: string }): Promise<UserInfo> {
  const resp = await put<UserInfoVO>('/auth/profile', data)
  return toUserInfo(resp)
}

/**
 * 注销账号（合规；账号匿名化 —— 评价 / 反馈保留但去身份化）。
 * `skipAuthRetry`：401 时禁止「静默登录后重试」—— 静默登录会建出新游客号，重试将误删新账号。
 */
export async function deleteAccount(): Promise<void> {
  await del('/auth/account', undefined, { skipAuthRetry: true })
}
