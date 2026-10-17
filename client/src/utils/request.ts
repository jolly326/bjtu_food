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

if (!BASE_URL) {
  console.error('[request] VITE_API_BASE_URL 未配置，请检查 client/.env.development / .env.production')
}

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
  let code = ''
  try {
    const r = await uni.login({ provider: 'weixin' })
    code = (r as { code?: string }).code ?? ''
  } catch (err) {
    console.error('[request] wx.login 失败', err)
    throw new Error('微信登录失败')
  }
  if (!code) {
    console.error('[request] wx.login 未返回 code')
    throw new Error('微信登录失败：无 code')
  }
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
  const url = BASE_URL + path
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url,
      method: method as UniApp.RequestOptions['method'],
      data: data as object | undefined,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (r) => {
        const body = r.data as Envelope<T>
        if (r.statusCode === 200 && body && typeof body.code === 'number') {
          if (body.code === 200) {
            resolve(body.data as T)
            return
          }
          // 业务失败：401 走静默重登，其它错误打出来便于定位
          if (body.code !== 401) {
            console.error('[request] 业务失败', method, url, body.code, body.message)
          }
          reject(bizError(body.code, body.message || '请求失败'))
          return
        }
        console.error('[request] 非 200 响应', method, url, r.statusCode, r.data)
        reject(new Error(`网络异常(${r.statusCode})`))
      },
      fail: (err: { errMsg?: string }) => {
        // 最常见、也最容易被静默吞掉：域名未配置 / TLS / 超时 / 网络不可达
        console.error('[request] 请求失败', method, url, err.errMsg ?? err)
        reject(new Error(`网络异常: ${err.errMsg ?? 'unknown'}`))
      },
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
