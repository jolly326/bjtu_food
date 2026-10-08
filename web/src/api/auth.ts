/**
 * 管理端鉴权接口（TD-23）。
 *
 * <p><b>契约唯一真源</b>：[api/web/auth.md](../../../docs/api/web/auth.md)，
 * 本文件不复述字段语义，只做类型映射与路径收敛。
 */
import { get, post } from './http'

/** `POST /admin/auth/login` 出参 */
export interface AdminLoginResult {
  /** 管理端 JWT；**需要第二因子时为 `null`**（此时只下发 mfaTicket） */
  token: string | null
  /** 回显登录名，供身份区展示；需要第二因子时为 `null` */
  username: string | null
  /** 有效期秒数（默认 86400 = 24h）；需要第二因子时为 0 */
  expiresIn: number
  /** 是否还需要完成第二因子（动态口令） */
  mfaRequired: boolean
  /** 第二因子票据（仅 `mfaRequired = true` 时下发；短时、一次性、**不能当 token 用**） */
  mfaTicket: string | null
}

/** `GET /admin/auth/me` 出参 —— 只回「我是谁 / 角色 / 上次何时登录」，**不回 token** */
export interface AdminMe {
  username: string
  /** 最近登录成功时间（`yyyy-MM-dd HH:mm:ss`）；从未登录过时字段不下发 */
  lastLoginAt?: string
  /** 角色：供端上按角色隐显入口（**真正的门控在服务端**） */
  role?: string
  /** 是否已绑定动态口令（端上据此展示「绑定」还是「停用」） */
  mfaEnabled?: boolean
  /** 口令是否已超期（>180 天）⇒ 端上提示改密，**不强制踢出** */
  passwordAging?: boolean
}

/** 动态口令绑定初始化出参 */
export interface MfaSetupResult {
  /** Base32 密钥（可手输录入认证器） */
  secret: string
  /** otpauth URI（供认证器扫码） */
  otpauthUri: string
}

/** 动态口令绑定成功出参 */
export interface MfaEnableResult {
  /** 一次性恢复码（**只下发一次**，服务端仅存哈希） */
  recoveryCodes: string[]
}

/** 账密登录（第一步）；失败统一 `401` +「账号或密码错误」（不区分账号不存在 / 密码错误，防用户名枚举） */
export function login(username: string, password: string): Promise<AdminLoginResult> {
  return post<AdminLoginResult>('/admin/auth/login', { username, password })
}

/** 登录第二步：用第一步的票据 + 动态口令（或一枚恢复码）换取真正的 token */
export function loginWithMfa(mfaTicket: string, code: string): Promise<AdminLoginResult> {
  return post<AdminLoginResult>('/admin/auth/login/mfa', { mfaTicket, code })
}

/** 校验当前登录态 —— 刷新页面 / 进路由守卫时确认 token 是否仍有效，并回填角色与安全提示 */
export function fetchMe(): Promise<AdminMe> {
  return get<AdminMe>('/admin/auth/me')
}

/** 初始化动态口令绑定（返回待绑定密钥；**此时尚未落库**，中途放弃不影响账号） */
export function setupMfa(): Promise<MfaSetupResult> {
  return post<MfaSetupResult>('/admin/auth/mfa/setup')
}

/** 确认绑定（带上认证器算出的口令）⇒ 下发一次性恢复码 */
export function enableMfa(secret: string, code: string): Promise<MfaEnableResult> {
  return post<MfaEnableResult>('/admin/auth/mfa/enable', { secret, code })
}

/** 停用动态口令（🔴 须同时提供当前口令与动态口令，防 token 盗用方顺手摘掉第二因子） */
export function disableMfa(password: string, code: string): Promise<void> {
  return post<void>('/admin/auth/mfa/disable', { password, code })
}

/** 修改口令；成功后响应带一枚新 token（既有 token 全部失效，本端用新 token 续用） */
export function changePassword(
  oldPassword: string,
  newPassword: string,
): Promise<AdminLoginResult> {
  return post<AdminLoginResult>('/admin/auth/password', { oldPassword, newPassword })
}
