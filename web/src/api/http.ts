/**
 * fetch 封装（web 依赖无 axios）：注入管理端凭证、解析 `Result<T>`、统一错误处理。
 *
 * <p><b>鉴权口径</b>（真源 [api/web/auth.md](../../../docs/api/web/auth.md) ·
 * [C1 管理员登录与访问控制](../../../docs/func/web/C-账号与访问/C1-管理员登录与访问控制.md)）：
 * 管理后台为**账密登录** —— 先 `POST /admin/auth/login` 换取管理端 JWT（存 `sessionStorage`），
 * 后续请求一律带 `Authorization: Bearer <token>`；🔴 **前端不持有口令**。
 *
 * <p><b>401 = 会话失效</b>（未带 token / 签名无效 / 已过期 / 账号已停用）：由本层**统一**处理
 * 「清 token → 跳登录页」，页面不渲染列表态（[UI 基线 §1.5 ⑥](../../../docs/ui/web/公共组件与形态基线.md)）。
 * <b>403 = 已认证但无权限</b>（预留，当前无角色区分）：按普通业务错误抛出，**不**触发跳转。
 */
import { API_BASE_URL } from './config'
import { clearSession, getToken } from './session'
import { CODE_OK, CODE_UNAUTHORIZED, type AdminPage } from '@/types/common'

class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.code = code
  }
}

/**
 * 会话失效兜底文案 —— 仅当服务端未给 `message` 时使用。
 * 正常情况下 401 的 `message` 会原样透出（如登录失败的「账号或密码错误」），
 * 🔴 绝不能被统一文案吞掉，否则登录页无法提示真实原因。
 */
const SESSION_INVALID_MESSAGE = '登录已失效，请重新登录'

/** 是否为「会话失效」（401）：列表层据此**不渲染**错误态与「重试」（重试必然再失败） */
export function isSessionInvalid(err: unknown): boolean {
  return err instanceof ApiError && err.code === CODE_UNAUTHORIZED
}

/**
 * 401 处置回调（**由路由层注册**，见 `main.ts`）：清 token 之后跳登录页。
 *
 * <p>🔴 **为何不在本文件里直接 `import router`**：`router/index.ts` 会经 `api/auth.ts`
 * 反向依赖本文件，直接引用会在模块初始化期形成**循环依赖**（`router` 可能还是 `undefined`）。
 * 故本模块只「发信号」，跳转由路由层订阅 —— 依赖方向始终保持单向：`router → api → http`。
 */
export type UnauthorizedHandler = () => void

let unauthorizedHandler: UnauthorizedHandler | null = null

/** 注册 401 处置（应用启动时调用一次，见 `main.ts`） */
export function setUnauthorizedHandler(handler: UnauthorizedHandler): void {
  unauthorizedHandler = handler
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
  // 管理端凭证：登录后签发的 JWT，随每次请求带出（无刷新机制，失效即重新登录）
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`

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

  // **先解析体**：401 的 `message` 才是真实原因（如登录失败的「账号或密码错误」），
  // 不能被统一文案吞掉。非 JSON 体时 `result` 为 null，由下方按 HTTP 状态兜底。
  let parsed: { code: number; message: string; data: T | null } | null = null
  try {
    parsed = await res.json()
  } catch {
    parsed = null
  }

  const code = parsed?.code ?? res.status
  const message = parsed?.message || (parsed ? `请求失败（${code}）` : `响应解析失败（HTTP ${res.status}）`)

  // 🔴 401 = 会话失效：清 token → 跳登录页（跳转由路由层注册的 handler 负责）。
  //    HTTP 状态与 body.code **任一**为 401 都算：`AdminAuthFilter` 回 HTTP 401，
  //    而登录失败由 `GlobalExceptionHandler` 承载 body 的 code。
  if (res.status === CODE_UNAUTHORIZED || code === CODE_UNAUTHORIZED) {
    clearSession()
    unauthorizedHandler?.()
    throw new ApiError(CODE_UNAUTHORIZED, message || SESSION_INVALID_MESSAGE)
  }

  if (!parsed) {
    throw new ApiError(res.status, `响应解析失败（HTTP ${res.status}）`)
  }
  if (parsed.code !== CODE_OK) {
    throw new ApiError(parsed.code, message)
  }
  return parsed.data as T
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
 * 管理端分页 GET：返回 `AdminPage<T>`（`AdminPageResult`，见
 * [api/README](../../../docs/api/README.md)）—— 列表页据此渲染「共 N 条 + 页码」，
 * 结束判据 = `total`（**不再**靠「本页条数 < pageSize」推断）。
 */
export function getAdminPage<T>(path: string, params?: object): Promise<AdminPage<T>> {
  return get<AdminPage<T>>(path, params)
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
