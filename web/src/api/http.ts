/**
 * fetch 封装（web 依赖无 axios）：注入管理端口令、解析 Result<T>、统一错误处理。
 *
 * <p><b>鉴权口径</b>（真源 [C1 管理员登录与访问控制](../../../docs/web/C-账号与访问/C1-管理员登录与访问控制.md)）：
 * **无登录体系** —— 口令由**构建期注入**（`VITE_ADMIN_TOKEN`）并以请求头
 * `X-Admin-Token` 携带（与后端 `AdminTokenFilter` 对应）；口令不匹配 / 账号受限 = **403 = 会话失效**，
 * **不存在 401 分支**，也不做任何登录页跳转（旧 `Bearer` 令牌与 `/login` 体系已整体退役）。
 */
import { ADMIN_TOKEN, API_BASE_URL } from './config'
import { CODE_FORBIDDEN, CODE_OK } from '@/types/common'

export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

/**
 * 会话失效（403）固定文案 —— 页面据此渲染「会话失效态」且**不渲染重试**
 * （重试必然再失败，见 [UI 基线 §1.5 ⑥](../../../docs/web/ui/公共组件与形态基线.md)）。
 */
export const SESSION_INVALID_MESSAGE =
  '管理员口令校验失败（403），请检查构建期注入的 ADMIN_TOKEN'

/** 是否为「会话失效」（403）：页面用它把第 ⑥ 态与普通错误态区分开 */
export function isSessionInvalid(err: unknown): boolean {
  return err instanceof ApiError && err.code === CODE_FORBIDDEN
}

interface RequestOptions {
  /** 请求体为 FormData（文件上传） */
  isForm?: boolean
  /** 自定义超时（毫秒） */
  timeout?: number
}

export async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  path: string,
  body?: unknown,
  options: RequestOptions = {},
): Promise<T> {
  const url = API_BASE_URL + path
  const headers: Record<string, string> = {}
  // 管理端口令：构建期注入，随每次请求带出（无登录态、无令牌刷新）
  if (ADMIN_TOKEN) headers['X-Admin-Token'] = ADMIN_TOKEN

  let payload: BodyInit | undefined
  if (body !== undefined) {
    if (options.isForm && body instanceof FormData) {
      payload = body
    } else {
      headers['Content-Type'] = 'application/json'
      payload = JSON.stringify(body)
    }
  }

  const controller = new AbortController()
  const timer = setTimeout(
    () => controller.abort(),
    options.timeout ?? 30000,
  )

  const init: RequestInit = {
    method,
    headers,
    signal: controller.signal,
  }
  if (payload !== undefined) {
    init.body = payload
  }

  let res: Response
  try {
    res = await fetch(url, init)
  } catch {
    clearTimeout(timer)
    throw new ApiError(0, '网络异常，请检查后端是否启动')
  }
  clearTimeout(timer)

  // 403 = 会话失效（口令不匹配）：统一文案，页面据此渲染第 ⑥ 态，不做跳转
  if (res.status === CODE_FORBIDDEN) {
    throw new ApiError(CODE_FORBIDDEN, SESSION_INVALID_MESSAGE)
  }

  let result: { code: number; message: string; data: T | null }
  try {
    result = await res.json()
  } catch {
    throw new ApiError(res.status, `响应解析失败（HTTP ${res.status}）`)
  }

  if (result.code !== CODE_OK) {
    throw new ApiError(result.code, result.message || `请求失败（${result.code}）`)
  }
  return result.data as T
}

export function get<T>(path: string, params?: object): Promise<T> {
  const qs = toQueryString(params)
  return request<T>('GET', qs ? `${path}?${qs}` : path)
}

export function post<T>(
  path: string,
  body?: unknown,
  options?: RequestOptions,
): Promise<T> {
  return request<T>('POST', path, body, options)
}

export function put<T>(path: string, body?: unknown): Promise<T> {
  return request<T>('PUT', path, body)
}

export function del<T>(path: string, body?: unknown): Promise<T> {
  return request<T>('DELETE', path, body)
}

/**
 * 管理端分页 GET：返回 `{ records, total }`（`AdminPageResult`，见
 * [api/README](../../../docs/api/README.md)）—— 列表页据此渲染「共 N 条 + 页码」，
 * 结束判据 = `total`（**不再**靠「本页条数 < pageSize」推断）。
 */
export function getAdminPage<T>(
  path: string,
  params?: object,
): Promise<{ records: T[]; total: number }> {
  return get<{ records: T[]; total: number }>(path, params)
}

function toQueryString(params?: object): string {
  if (!params) return ''
  const parts: string[] = []
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === '') continue
    parts.push(`${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
  }
  return parts.join('&')
}
