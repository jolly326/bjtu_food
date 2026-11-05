/**
 * 运营端请求层。
 *
 * 口径（docs/secur/S-01、docs/api/CONVENTIONS.md）：
 * - 基址取 `VITE_API_BASE_URL`，缺省走同源 `/api` 反代（dev / preview / 部署同一形态）
 * - `/admin/**` 一律带 `Authorization: Bearer <运营端 JWT>`
 * - 401 => 清 token 并跳登录页
 * - 信封 {code,message,data}：code !== 200 视为业务失败
 * - 传输层失败（后端未启动 / 域名不可达 / 响应非 JSON）统一转成可读错误
 */
const BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/+$/, '') || '/api'
const REQUEST_TIMEOUT_MS = 15_000
const TOKEN_KEY = 'admin_token'
/** 操作员账号名（顶栏展示用，非凭证）—— 与 token 同生命周期持久化，避免刷新丢名 */
const USERNAME_KEY = 'admin_username'

type Envelope<T> = { code: number; message: string; data: T | null }

export function readToken(): string {
  return sessionStorage.getItem(TOKEN_KEY) || ''
}

export function writeToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY)
}

export function readUsername(): string {
  return sessionStorage.getItem(USERNAME_KEY) || ''
}

export function writeUsername(username: string): void {
  sessionStorage.setItem(USERNAME_KEY, username)
}

export function clearUsername(): void {
  sessionStorage.removeItem(USERNAME_KEY)
}

function toLogin() {
  clearToken()
  if (location.hash !== '#/login') {
    location.hash = '#/login'
  }
}

/** 🔴 登录端点不参与「401 即跳登录」：口令错误时的服务端文案要原样回到登录页 */
const LOGIN_PATH = '/admin/sessions'

function url(path: string): string {
  return `${BASE_URL}/${path.replace(/^\/+/, '')}`
}

/** 发起请求并解析信封：网络层 / 协议层故障在这里收敛成可读错误 */
async function send(path: string, init: RequestInit, isLogin: boolean): Promise<Envelope<unknown>> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  let res: Response
  try {
    res = await fetch(url(path), { ...init, signal: controller.signal })
  } catch (e) {
    const reason = (e as Error).name === 'AbortError' ? '请求超时' : '无法连接服务器'
    throw new Error(`${reason}，请确认后端已启动且接口基址可用（${BASE_URL}）`)
  } finally {
    clearTimeout(timer)
  }

  if (res.status === 401 && !isLogin) {
    toLogin()
    throw new Error('未登录或凭证已失效')
  }

  let payload: Envelope<unknown>
  try {
    payload = (await res.json()) as Envelope<unknown>
  } catch {
    throw new Error(`服务响应异常（HTTP ${res.status}），请确认接口基址配置正确（${BASE_URL}）`)
  }
  if (!payload || typeof payload.code !== 'number') {
    throw new Error(`服务响应格式异常（HTTP ${res.status}），请确认接口基址配置正确（${BASE_URL}）`)
  }
  return payload
}

function unwrap<T>(payload: Envelope<unknown>, isLogin: boolean): T {
  if (payload.code === 401) {
    if (!isLogin) {
      toLogin()
      throw new Error('未登录或凭证已失效')
    }
    throw new Error(payload.message || '登录失败')
  }
  if (payload.code !== 200) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data as T
}

async function raw<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = readToken()
  const isLogin = path === LOGIN_PATH
  const payload = await send(
    path,
    {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    },
    isLogin,
  )
  return unwrap<T>(payload, isLogin)
}

export const http = {
  get: <T>(path: string) => raw<T>('GET', path),
  post: <T>(path: string, body?: unknown) => raw<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => raw<T>('PUT', path, body),
  patch: <T>(path: string, body?: unknown) => raw<T>('PATCH', path, body),
  delete: <T>(path: string) => raw<T>('DELETE', path),
}

/** multipart 上传（图片）。 */
export async function upload(file: File): Promise<string> {
  const token = readToken()
  const form = new FormData()
  form.append('file', file)

  const payload = await send(
    '/admin/images',
    {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: form,
    },
    false,
  )
  const data = unwrap<{ url: string } | null>(payload, false)
  if (!data?.url) {
    throw new Error('上传失败')
  }
  return data.url
}
