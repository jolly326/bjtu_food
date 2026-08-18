import type { UserInfo } from '@/types/user'
import { get, post, put } from './http'

function toFrontendRole(role?: string): UserInfo['role'] {
  // 后端现已直接存储 student / admin（§0.2 仅两种角色），无需再做 USER→STUDENT 映射。
  // 直接透传并收敛类型：显式识别 admin，其余（含缺省/未知）归一为 student。
  return (role === 'admin' ? 'admin' : 'student') as UserInfo['role']
}

/** 判断学号是否为纯数字（校园身份学号），仅此才用 {学号}@bjtu.edu.cn 推导校园邮箱 */
function isStudentNumber(s: string): boolean {
  return /^\d+$/.test(s.trim())
}

function toUserInfo(resp: any, fallbackId = 0): UserInfo {
  const user = resp?.userInfo || resp?.user || resp || {}
  // 后端 LoginResp 透传 userId/username/nickname/avatar/role/verified/bindEmail/guestShortId（见 auth/dto/LoginResp）
  const username = String(user.username || resp?.username || '')
  // 微信登录体系：游客态 username 为 'wx_'+openid 尾 16 位，非学号 → 不推导校园邮箱（email 留空）
  const email = user.email || resp?.email || (isStudentNumber(username) ? deriveCampusEmail(username) : '')
  return {
    // 后端恒返回 userId；0 仅作防御性兜底（不伪造有效用户 ID）
    id: Number(user.id ?? resp?.userId ?? fallbackId),
    username,
    email,
    nickname: user.nickname || resp?.nickname || '食客',
    avatar: user.avatar || resp?.avatar || '',
    role: toFrontendRole(user.role || resp?.role),
    // 微信登录体系（§5.y）：verified / bindEmail / guestShortId 由后端 wechat-login / verify-email 返回
    verified: !!(user.verified ?? resp?.verified),
    bindEmail: user.bindEmail || resp?.bindEmail || user.bind_email || undefined,
    guestShortId: user.guestShortId || resp?.guestShortId || undefined,
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
  const resp = await post<any>('/auth/wechat-login', { code })
  return {
    token: resp.token,
    userInfo: toUserInfo(resp),
  }
}

/** 学号邮箱认证（§5.y.5 POST /auth/verify-email）：验证码绑定当前微信 → verified=true */
export async function verifyEmail(code: string): Promise<AuthResult> {
  const resp = await post<any>('/auth/verify-email', { code })
  return {
    token: resp.token,
    userInfo: toUserInfo(resp),
  }
}

/** 读取当前账号信息（§5.y.5 GET /auth/profile：游客态亦可读，含 verified/bindEmail/guestShortId） */
export async function getProfile(): Promise<UserInfo> {
  const resp = await get<any>('/auth/profile')
  return toUserInfo(resp)
}

export async function updateProfile(data: { nickname?: string; avatar?: string }): Promise<UserInfo> {
  const resp = await put<any>('/auth/profile', data)
  return toUserInfo(resp)
}
