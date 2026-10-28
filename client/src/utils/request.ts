/**
 * 统一请求层（口径见 docs/secur/S-01、docs/api/README.md）。
 *
 * 传输层双通道（构建期内联，由 env 决定）：
 * - **云调用（推荐，真机免域名白名单）**：配置 `VITE_TCB_ENV` ⇒ 走 `wx.cloud.callContainer`，
 *   需在 env 里给出云托管环境 ID；可选 `VITE_TCB_SERVICE` 指定服务名（缺省用环境默认服务）。
 * - **直连域名（回退）**：未配 `VITE_TCB_ENV` 时走 `uni.request` + `VITE_API_BASE_URL`，
 *   真机需在小程序后台配置 request 合法域名。
 *
 * 工程能力：请求超时、并发去重、可取消（直连通道）、GET 条件请求（ETag / 304）。
 * 业务口径不变：token 存 storage；`401` ⇒ 静默重登并重试一次；`429` ⇒ 抛带 `retryAfter`
 * 的错误、不自动重试；信封 `{code,message,data}` 中 `code !== 200` 视为业务失败。
 */

import { CODE_RATE_LIMITED, parseRetrySeconds } from './backoff'

const TOKEN_KEY = 'student_token'
const TCB_ENV = ((import.meta.env.VITE_TCB_ENV as string | undefined) ?? '').trim()
const TCB_SERVICE = ((import.meta.env.VITE_TCB_SERVICE as string | undefined) ?? '').trim()
const BASE_URL = ((import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '').trim()

/** 请求超时（毫秒）：避免慢请求悬挂 */
const REQUEST_TIMEOUT_MS = 15000

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

/** 主动取消（页面卸载）产生的错误：上层应静默忽略，不落错误态 */
type AbortedError = Error & { aborted: true }

function abortedError(): AbortedError {
  return Object.assign(new Error('请求已取消'), { aborted: true } as const)
}

export function isAbortedRequest(error: unknown): boolean {
  return Boolean((error as { aborted?: boolean } | null)?.aborted)
}

/** 请求方法（`uni.request` 类型不含 PATCH，小程序运行时支持，此处收敛） */
type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

/** 传输层响应（两条通道统一成同一形状） */
type TransportResult = { statusCode: number; data: unknown; header?: Record<string, string> }
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
  // 身份变化时清空 GET 条件缓存，避免命中上一账号的 liked 等用户态数据
  if (readToken() !== token) getCache.clear()
  uni.setStorageSync(TOKEN_KEY, token)
}

function clearToken(): void {
  uni.removeStorageSync(TOKEN_KEY)
  getCache.clear()
}

/** 直连通道的活跃任务（云调用无 abort 能力，仅 http 通道可取消） */
const activeTasks = new Set<ReturnType<typeof uni.request>>()

/** 取消全部在途直连请求（页面卸载 / 重进时清理，避免叠加） */
export function abortAllRequests(): void {
  activeTasks.forEach((task) => {
    try {
      task.abort()
    } catch {
      // 已结束的任务忽略
    }
  })
  activeTasks.clear()
  // 已取消的请求不应被后续同键请求复用
  inflight.clear()
}

/** GET 条件请求缓存：按 path 记录 ETag 与上次结果 */
type GetCache = { etag: string; data: unknown }
const getCache = new Map<string, GetCache>()

/** 并发去重：同一 method+path+body 在途请求复用同一 Promise */
const inflight = new Map<string, Promise<unknown>>()

/** 发送一次请求（按 env 选择云调用 / 直连），只负责「发出 + 拿到 statusCode/data/header」 */
function transport(
  method: HttpMethod,
  path: string,
  data: unknown,
  token: string,
  extraHeader?: Record<string, string>,
): Promise<TransportResult> {
  const header: Record<string, string> = token ? { Authorization: `Bearer ${token}` } : {}
  if (extraHeader) Object.assign(header, extraHeader)

  return new Promise<TransportResult>((resolve, reject) => {
    const onSuccess = (r: TransportResult) => resolve(r)

    const onFail = (err: TransportFail) => {
      const message = err.errMsg ?? ''
      if (/abort|cancel/i.test(message)) {
        reject(abortedError())
        return
      }
      devError('[request] 请求失败', method, path, message || err)
      reject(new Error(`网络异常: ${message || 'unknown'}`))
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

    const taskRef: { current?: ReturnType<typeof uni.request> } = {}
    taskRef.current = uni.request({
      url: BASE_URL + path,
      method: method as UniApp.RequestOptions['method'],
      data: data as object | undefined,
      header,
      timeout: REQUEST_TIMEOUT_MS,
      success: (r) => {
        if (taskRef.current) activeTasks.delete(taskRef.current)
        onSuccess({ statusCode: r.statusCode, data: r.data, header: r.header })
      },
      fail: (err: TransportFail) => {
        if (taskRef.current) activeTasks.delete(taskRef.current)
        onFail(err)
      },
    })
    if (taskRef.current) activeTasks.add(taskRef.current)
  })
}

/** 解析信封：`code === 200` 返回 data；其余抛业务错误 */
function unwrap<T>(res: TransportResult): T {
  const body = res.data as Envelope<T>
  if (res.statusCode === 200 && body && typeof body.code === 'number') {
    if (body.code === 200) {
      return body.data as T
    }
    // 业务失败：401 走静默重登，其它错误打出来便于定位
    if (body.code !== 401) {
      devError('[request] 业务失败', body.code, body.message)
    }
    throw bizError(body.code, body.message || '请求失败')
  }
  devError('[request] 非 200 响应', res.statusCode, res.data)
  throw new Error(`网络异常(${res.statusCode})`)
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
  const res = await transport('POST', '/auth/wechat-login', { code }, '')
  const body = unwrap<{ token: string }>(res)
  writeToken(body.token)
  return body.token
}

/** 带 token 发送；GET 走 ETag 条件请求，304 命中本地缓存。 */
async function send<T>(
  method: HttpMethod,
  path: string,
  data: unknown,
  token: string,
): Promise<T> {
  const cached = method === 'GET' ? getCache.get(path) : undefined
  const extra = cached ? { 'If-None-Match': cached.etag } : undefined
  const res = await transport(method, path, data, token, extra)
  if (res.statusCode === 304 && cached) {
    return cached.data as T
  }
  const parsed = unwrap<T>(res)
  if (method === 'GET') {
    const etag = res.header?.ETag ?? res.header?.etag
    if (etag) getCache.set(path, { etag, data: parsed })
  }
  return parsed
}

/** 带凭证的请求；401 时静默重登并重试一次。 */
async function runRequest<T>(method: HttpMethod, path: string, data?: unknown): Promise<T> {
  let token = readToken()
  if (!token) {
    token = await silentLogin()
  }

  try {
    return await send<T>(method, path, data, token)
  } catch (error) {
    // 仅按业务码判定：401 = 未登录 / 凭证失效 ⇒ 重登并重试一次
    if ((error as Partial<BizError>).code === 401) {
      clearToken()
      const fresh = await silentLogin()
      return await send<T>(method, path, data, fresh)
    }
    throw error
  }
}

/** 对外请求入口：并发去重（同法同路径同体在途时复用）。 */
export function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  path: string,
  data?: unknown,
): Promise<T> {
  const key = `${method} ${path} ${data == null ? '' : JSON.stringify(data)}`
  const existing = inflight.get(key)
  if (existing) {
    return existing as Promise<T>
  }
  const task = runRequest<T>(method, path, data)
    .then((result) => {
      // 写操作可能改变 GET 结果，清空条件缓存，避免旧 ETag 命中脏数据
      if (method !== 'GET') getCache.clear()
      return result
    })
    .finally(() => {
      inflight.delete(key)
    })
  inflight.set(key, task)
  return task
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
