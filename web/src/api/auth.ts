/**
 * 管理端鉴权接口（TD-23）。
 *
 * <p><b>契约唯一真源</b>：[api/web/auth.md](../../../docs/api/web/auth.md)，
 * 本文件不复述字段语义，只做类型映射与路径收敛。
 */
import { get, post } from './http'

/** `POST /admin/auth/login` 出参 */
export interface AdminLoginResult {
  /** 管理端 JWT，登录成功后立即写入 `sessionStorage` */
  token: string
  /** 回显登录名，供身份区展示 */
  username: string
  /** 有效期秒数（默认 86400 = 24h） */
  expiresIn: number
}

/** `GET /admin/auth/me` 出参 —— 只回「我是谁 / 上次何时登录」，**不回 token** */
export interface AdminMe {
  username: string
  /** 最近登录成功时间（`yyyy-MM-dd HH:mm:ss`）；从未登录过时字段不下发 */
  lastLoginAt?: string
}

/** 账密登录；失败统一 `401` +「账号或密码错误」（不区分账号不存在 / 密码错误，防用户名枚举） */
export function login(username: string, password: string): Promise<AdminLoginResult> {
  return post<AdminLoginResult>('/admin/auth/login', { username, password })
}

/** 校验当前登录态 —— 刷新页面 / 进路由守卫时确认 token 是否仍有效 */
export function fetchMe(): Promise<AdminMe> {
  return get<AdminMe>('/admin/auth/me')
}
