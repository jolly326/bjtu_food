/**
 * 统一请求层。
 *
 * 口径（docs/secur/S-01、docs/api/README.md）：
 * - 所有业务端点都要求凭证；token 存本地 storage，缓存 7 天
 * - 业务码 401 => 静默重登（wx.login -> /auth/wechat-login）并**自动重试原请求一次**
 *   （按 `code` 判定，**不做文案匹配** —— 避免「服务端未配置微信小程序凭证」等文案误触发重登）
 * - 429 => 直接抛给页面做退避倒计时
 * - 信封 {code,message,data}：code !== 200 视为业务失败
 */

const TOKEN_KEY = 'student_token'
const BASE_URL = import.meta.env.VITE_API_BASE_URL as string

type Envelope<T> = { code: number; message: string; data: T | null }

/** 业务失败：携带后端 `code`，供上层做差异化处置（如 `401` 静默重登）。 */
type BizError = Error & { code: number }

function bizError(code: number, message: string): BizError {
  return Object.assign(new Error(message), { code })
}

/** uni.request 的方法类型不含 PATCH（小程序运行时支持），此处做一次收敛 */
type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

function readToken(): string {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

function writeToken(token: string): void {
  uni.setStorageSync(TOKEN_KEY, token)
}

function clearToken(): void {
  uni.removeStorageSync(TOKEN_KEY)
}

/** 冷启动静默登录：wx.login 换 code，服务端换 token。 */
async function silentLogin(): Promise<string> {
  const { code } = await uni.login({ provider: 'weixin' })
  const res = await rawRequest<{ token: string }>('POST', '/auth/wechat-login', { code }, '')
  writeToken(res.token)
  return res.token
}

function rawRequest<T>(
  method: HttpMethod,
  path: string,
  data?: unknown,
  token?: string,
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: BASE_URL + path,
      method: method as UniApp.RequestOptions['method'],
      data: data as object | undefined,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (r) => {
        const body = r.data as Envelope<T>
        if (r.statusCode === 200 && body && typeof body.code === 'number') {
          if (body.code === 200) {
            resolve(body.data as T)
          } else {
            reject(bizError(body.code, body.message || '请求失败'))
          }
        } else {
          reject(new Error('网络异常'))
        }
      },
      fail: () => reject(new Error('网络异常')),
    })
  })
}

/** 带凭证的请求；401 时静默重登并重试一次。 */
export async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  data?: unknown,
): Promise<T> {
  let token = readToken()
  if (!token) {
    token = await silentLogin()
  }

  try {
    return await rawRequest<T>(method, path, data, token)
  } catch (error) {
    // 仅按业务码判定：401 = 未登录 / 凭证失效 ⇒ 重登并重试一次
    if ((error as Partial<BizError>).code === 401) {
      clearToken()
      const fresh = await silentLogin()
      return await rawRequest<T>(method, path, data, fresh)
    }
    throw error
  }
}

export const http = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, data?: unknown) => request<T>('POST', path, data),
  put: <T>(path: string, data?: unknown) => request<T>('PUT', path, data),
  patch: <T>(path: string, data?: unknown) => request<T>('PATCH', path, data),
  delete: <T>(path: string) => request<T>('DELETE', path),
}

export { clearToken }
