/**
 * 运营端请求层。
 *
 * 口径（docs/secur/S-01、docs/api/README.md）：
 * - `/admin/**` 一律带 `Authorization: Bearer <运营端 JWT>`
 * - 401 => 清 token 并跳登录页
 * - 信封 {code,message,data}：code !== 200 视为业务失败
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL as string
const TOKEN_KEY = 'admin_token'

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

function toLogin() {
  clearToken()
  if (location.hash !== '#/login') {
    location.hash = '#/login'
  }
}

async function raw<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = readToken()
  const res = await fetch(BASE_URL + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  if (res.status === 401) {
    toLogin()
    throw new Error('未登录或凭证已失效')
  }

  const payload = (await res.json()) as Envelope<T>
  if (payload.code === 401) {
    toLogin()
    throw new Error(payload.message || '未登录')
  }
  if (payload.code !== 200) {
    throw new Error(payload.message || '请求失败')
  }
  return payload.data as T
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

  const res = await fetch(`${BASE_URL}/admin/images`, {
    method: 'POST',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    body: form,
  })
  const payload = (await res.json()) as Envelope<{ url: string }>
  if (payload.code !== 200 || !payload.data) {
    throw new Error(payload.message || '上传失败')
  }
  return payload.data.url
}
