/**
 * 统一请求层（口径见 docs/secur/S-01、docs/api/README.md）。
 *
 * 传输层双通道（构建期内联，由 env 决定）：
 * - **云调用（推荐，真机免域名白名单）**：配置 `VITE_TCB_ENV` ⇒ 走 `wx.cloud.callContainer`，
 *   需在 env 里给出云托管环境 ID；可选 `VITE_TCB_SERVICE` 指定服务名（缺省用环境默认服务）。
 * - **直连域名（回退）**：未配 `VITE_TCB_ENV` 时走 `uni.request` + `VITE_API_BASE_URL`，
 *   真机需在小程序后台配置 request 合法域名。
 *
 * 业务口径不变：token 存 storage；`401` ⇒ 静默重登并重试一次；`429` ⇒ 抛带 `retryAfter`
 * 的错误、不自动重试；信封 `{code,message,data}` 中 `code !== 200` 视为业务失败。
 */

import { CODE_RATE_LIMITED, parseRetrySeconds } from './backoff'

const TOKEN_KEY = 'student_token'
const TCB_ENV = ((import.meta.env.VITE_TCB_ENV as string | undefined) ?? '').trim()
const TCB_SERVICE = ((import.meta.env.VITE_TCB_SERVICE as string | undefined) ?? '').trim()
const BASE_URL = ((import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '').trim()

/** 是否走云调用（配了环境 ID 即启用） */
const USE_CLOUD = Boolean(TCB_ENV)

/** 是否开发构建（生产构建下由 Vite 静态替换为 false） */
const IS_DEV = import.meta.env.DEV
/** 开发期诊断日志：仅 DEV 输出；生产构建被 tree-shaking 剥离，保持控制台干净 */
function devError(...args: unknown[]): void {
  if (IS_DEV) console.error(...args)
}

if (!USE_CLOUD && !BASE_URL) {
  devError(
    '[request] 既未配置 VITE_TCB_ENV（云调用）也未配置 VITE_API_BASE_URL（直连），请求无法发出',
  )
}

type Envelope<T> = { code: number; message: string; data: T | null }

/** 业务失败：携带后端 `code`，供上层做差异化处置（如 `401` 静默重登、`429` 退避）。 */
type BizError = Error & { code: number; retryAfter?: number }

function bizError(code: number, message: string): BizError {
  const retryAfter = code === CODE_RATE_LIMITED ? parseRetrySeconds(message) : undefined
  return Object.assign(new Error(message), { code, retryAfter })
}

/** 请求方法（`uni.request` 类型不含 PATCH，小程序运行时支持，此处收敛） */
type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

/** 传输层响应（两条通道统一成同一形状） */
type TransportResult = { statusCode: number; data: unknown }
type TransportFail = { errMsg?: string }

/** `wx.cloud` 的最小结构（仅取本层用到的两个方法，避免引入小程序全量类型） */
type CloudApi = {
  init: (options: { env: string; traceUser?: boolean }) => void
  callContainer: (options: {
    config: { env: string }
    path: string
    method: HttpMethod
    data?: unknown
    header?: Record<string, string>
    success: (res: TransportResult) => void
    fail: (err: TransportFail) => void
  }) => void
}

let cloudReady = false

/** 取 `wx.cloud`；非小程序环境（单测 / H5）返回 null */
function getCloud(): CloudApi | null {
  const host = globalThis as unknown as { wx?: { cloud?: CloudApi } }
  const cloud = host.wx?.cloud
  return cloud && typeof cloud.callContainer === 'function' ? cloud : null
}

function readToken(): string {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

function writeToken(token: string): void {
  uni.setStorageSync(TOKEN_KEY, token)
}

function clearToken(): void {
  uni.removeStorageSync(TOKEN_KEY)
}

/** 发送一次请求（按 env 选择云调用 / 直连），只负责「发出 + 拿到 statusCode/data」 */
function transport<T>(
  method: HttpMethod,
  path: string,
  data: unknown,
  token: string,
): Promise<T> {
  const header: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}

  return new Promise<T>((resolve, reject) => {
    const onSuccess = (r: TransportResult) => {
      const body = r.data as Envelope<T>
      if (r.statusCode === 200 && body && typeof body.code === 'number') {
        if (body.code === 200) {
          resolve(body.data as T)
          return
        }
        // 业务失败：401 走静默重登，其它错误打出来便于定位
        if (body.code !== 401) {
          devError('[request] 业务失败', method, path, body.code, body.message)
        }
        reject(bizError(body.code, body.message || '请求失败'))
        return
      }
      devError('[request] 非 200 响应', method, path, r.statusCode, r.data)
      reject(new Error(`网络异常(${r.statusCode})`))
    }

    const onFail = (err: TransportFail) => {
      devError('[request] 请求失败', method, path, err.errMsg ?? err)
      reject(new Error(`网络异常: ${err.errMsg ?? 'unknown'}`))
    }

    if (USE_CLOUD) {
      const cloud = getCloud()
      if (!cloud) {
        reject(new Error('当前环境不支持云调用（wx.cloud 不可用），请检查基础库版本'))
        return
      }
      try {
        if (!cloudReady) {
          cloud.init({ env: TCB_ENV, traceUser: true })
          cloudReady = true
        }
        cloud.callContainer({
          config: { env: TCB_ENV },
          path,
          method,
          data,
          // 未指定服务名时由云托管路由到环境默认服务
          header: TCB_SERVICE ? { ...header, 'X-WX-SERVICE': TCB_SERVICE } : header,
          success: onSuccess,
          fail: onFail,
        })
      } catch (error) {
        devError('[request] 云调用初始化失败', error)
        reject(new Error('云调用不可用：请确认小程序已开通云开发并绑定该云托管环境'))
      }
      return
    }

    uni.request({
      url: BASE_URL + path,
      method: method as UniApp.RequestOptions['method'],
      data: data as object | undefined,
      header,
      success: (r) => onSuccess({ statusCode: r.statusCode, data: r.data }),
      fail: (err: TransportFail) => onFail(err),
    })
  })
}

/** 冷启动静默登录：wx.login 换 code，服务端换 token。 */
async function silentLogin(): Promise<string> {
  let code = ''
  try {
    const r = await uni.login({ provider: 'weixin' })
    code = (r as { code?: string }).code ?? ''
  } catch (err) {
    devError('[request] wx.login 失败', err)
    throw new Error('微信登录失败')
  }
  if (!code) {
    devError('[request] wx.login 未返回 code')
    throw new Error('微信登录失败：无 code')
  }
  const res = await transport<{ token: string }>('POST', '/auth/wechat-login', { code }, '')
  writeToken(res.token)
  return res.token
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
    return await transport<T>(method, path, data, token)
  } catch (error) {
    // 仅按业务码判定：401 = 未登录 / 凭证失效 ⇒ 重登并重试一次
    if ((error as Partial<BizError>).code === 401) {
      clearToken()
      const fresh = await silentLogin()
      return await transport<T>(method, path, data, fresh)
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

/** 当前传输通道（仅供诊断 / 页面提示使用） */
export const transportMode: 'cloud' | 'http' = USE_CLOUD ? 'cloud' : 'http'

export { clearToken }
