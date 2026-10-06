/**
 * HTTP 请求层：传输（按端条件编译） + 请求编排 + 业务码分流。
 * - 微信小程序端：`wx.cloud.callContainer` 调微信云托管（走微信内部链路，免合法域名配置）；
 * - 其他端（H5 等）：回退 `uni.request`，供本地联调。
 *
 * 请求在网络异常 / 业务错误时抛出；是否提示、是否渲染失败态由页面与 store 决定。
 * 错误类型见 `./errors`，登录态处置见 `./authFlow`，上传见 `./upload`。
 */
import { API_BASE_URL, WX_CLOUD_ENV, WX_SERVICE, buildContainerPath } from './config'
import { getWxApi } from '@/utils/device'
import { toastInfo } from '@/utils/error'
import { STORAGE_KEY_TOKEN } from '@/constants/storage'
import {
  RATE_LIMIT_PATTERNS,
  RateLimitedError,
  ResourceNotFoundError,
  SurfacedError,
  parseRetryAfter,
} from './errors'
import {
  handleUnauthorized,
  handleUnverified,
  handleWechatLoginRequired,
  isWechatLoginRequired,
  trySilentRelogin,
} from './authFlow'

/** 响应体外壳（仅本模块消费） */
/** 微信云托管调用回调的最小类型（平台回调透传，仅取所需字段） */
interface WxCloudCallResult {
  data?: unknown
}
interface WxCloudCallError {
  errMsg?: string
}

interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 请求体：对象 / 字符串 / 二进制 */
type RequestData = string | object | ArrayBuffer | undefined

/** 请求选项 */
interface RequestOptions {
  header?: Record<string, string>
  /** 注销等敏感调用：401 时禁止「静默登录后重试」（重试会以新游客身份执行，可能误删新账号） */
  skipAuthRetry?: boolean
}

/** 平台响应载体：请求层只关心 body（data），其余字段由微信 / uni 回调自身携带 */
interface RawResponse {
  data?: unknown
}

/**
 * 普通请求超时 12s：云托管实例缩容到零后首次请求需冷启动（通常 3~8s，弱网更久），
 * 8s 在冷启动 + 弱网叠加时易误报超时。H5 分支取同一常量，保证端间口径一致。
 */
const REQUEST_TIMEOUT_MS = 12000

function getToken(): string {
  return uni.getStorageSync(STORAGE_KEY_TOKEN) || ''
}

/** 解析响应体：兼容 JSON 字符串或已解析对象 */
function parseBody<T>(data: unknown): ApiResponse<T> {
  if (typeof data === 'string') {
    try {
      return JSON.parse(data) as ApiResponse<T>
    } catch {
      throw new Error('响应格式错误')
    }
  }
  return data as ApiResponse<T>
}

// ===== 传输层（按端条件编译二选一） =====

// #ifdef MP-WEIXIN
/** 微信端传输：云托管 `callContainer`（免域名白名单）；`settled` 防止超时与回调双重 settle */
async function transportWxCloud(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  data: RequestData | undefined,
  header: Record<string, string>,
): Promise<RawResponse> {
  return new Promise((resolve, reject) => {
    let settled = false
    const done = (fn: () => void) => {
      if (!settled) {
        settled = true
        fn()
      }
    }
    // 平台句柄统一经 utils/device 取（本文件不直接触碰全局 wx）
    const wxApi = getWxApi()
    if (!wxApi || !wxApi.cloud) {
      done(() => reject(new Error('当前环境不支持 wx.cloud')))
      return
    }
    const timeoutTimer = setTimeout(() => {
      done(() => reject(new Error('请求超时')))
    }, REQUEST_TIMEOUT_MS)
    const clearTimer = () => { clearTimeout(timeoutTimer) }
    wxApi.cloud.callContainer({
      config: { env: WX_CLOUD_ENV },
      // 不硬拼 `/api${url}`（context-path 升 /api/v1 后会全站 404）：前缀由 buildContainerPath 从
      // API_BASE_URL 推导，与 config.ts 同源
      path: buildContainerPath(url),
      method,
      data,
      header: {
        'X-WX-SERVICE': WX_SERVICE,
        ...header,
      },
      // 平台例外：微信回调透传，仅取其 data 字段
      success: (r: WxCloudCallResult) => { clearTimer(); done(() => resolve({ data: r?.data })) },
      fail: (err: WxCloudCallError) => { clearTimer(); done(() => reject(new Error(err.errMsg || '网络请求失败'))) },
    })
  })
}
// #endif

// #ifndef MP-WEIXIN
/** 其他端（H5 等）传输：普通 HTTP；仅对尚未完成的 task abort，超时定时器 settle 后清理 */
async function transportHttp(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  data: RequestData | undefined,
  header: Record<string, string>,
): Promise<RawResponse> {
  return new Promise((resolve, reject) => {
    let finished = false
    const timeoutTimer = setTimeout(() => {
      finished = true
      if (task && typeof task.abort === 'function') task.abort()
      reject(new Error('请求超时'))
    }, REQUEST_TIMEOUT_MS)
    const clearTimer = () => {
      if (!finished) clearTimeout(timeoutTimer)
    }
    const task = uni.request({
      url: `${API_BASE_URL}${url}`,
      method,
      data,
      header,
      success: (r) => { clearTimer(); resolve({ data: r.data }) },
      fail: (err) => { clearTimer(); reject(new Error(err.errMsg || '网络请求失败')) },
    })
  })
}
// #endif

/** 非 200 业务码分流：提示 + 抛可识别错误类型（200 不进入本函数） */
function throwForErrorBody<T>(body: ApiResponse<T>): never {
  if (body.code === 4031) {
    // 邮箱未认证：提示 + 认证引导（区别于普通权限拒绝 403）
    void handleUnverified()
    throw new SurfacedError(body.message || '请先完成学号邮箱认证')
  }
  if (body.code === 403) {
    // 普通权限拒绝，两种子情形：① 需微信登录 → 弹窗 + 用户确认后补 openid；
    // ② 越权 / 非本人资源 / 账号禁用 → 仅透传后端 message。两者都不跳认证页。
    const msg = body.message || '无权限访问该内容'
    if (isWechatLoginRequired(msg)) {
      void handleWechatLoginRequired(msg)
    } else {
      toastInfo(msg)
    }
    throw new SurfacedError(msg)
  }
  if (body.code === 4001) {
    // 资源不存在：抛可识别类型、不在此提示 —— 由页面渲染「不存在」文案 + 返回路径
    throw new ResourceNotFoundError(body.message || '内容不存在')
  }
  const msg = body.message || '请求失败'
  if (body.code === 400 && RATE_LIMIT_PATTERNS.some((p) => msg.includes(p))) {
    // 限频：可恢复错误，本层已弹 toast，调用方 catch 后做 UI 退避（禁用按钮 + 倒计时）
    toastInfo(msg)
    throw new RateLimitedError(msg, parseRetryAfter(msg))
  }
  // 普通业务错误：由调用方决定提示方式
  throw new Error(msg)
}

async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  data?: RequestData,
  options?: RequestOptions,
  _retried = false,
): Promise<T> {
  const header: Record<string, string> = {
    Authorization: `Bearer ${getToken()}`,
    ...(options?.header || {}),
  }

  let res: RawResponse
  try {
    // #ifdef MP-WEIXIN
    res = await transportWxCloud(method, url, data, header)
    // #endif
    // #ifndef MP-WEIXIN
    res = await transportHttp(method, url, data, header)
    // #endif
  } catch (e) {
    // 网络层错误（超时 / 断网）：统一提示后抛「已提示」标记，调用方只需回滚状态
    const msg = e instanceof Error ? e.message : '网络异常，请稍后重试'
    toastInfo(msg)
    throw new SurfacedError(msg)
  }

  const body = parseBody<T>(res.data)
  // 空响应 / 网关错误页（Nginx HTML、后端宕机）容错：body 无 code 时统一降级为可识别错误
  if (!body || typeof body.code !== 'number') {
    const detail = typeof res.data === 'string' ? res.data.slice(0, 80) : ''
    throw new Error(detail ? `服务响应异常：${detail}` : '服务响应异常，请稍后重试')
  }
  if (body.code === 401) {
    // 敏感调用（注销等）直接上抛：静默登录可能建出新游客号，重试会误删新账号
    if (options?.skipAuthRetry) {
      throw new Error('登录状态已失效，请重新进入小程序后操作')
    }
    // 启动竞态（请求早于静默登录拿到 token）：先确保静默登录完成再重试一次；
    // 重试仍 401 才视为真正失效，避免游客态启动时的误报
    if (!_retried && await trySilentRelogin()) {
      return request<T>(method, url, data, options, true)
    }
    await handleUnauthorized()
    throw new SurfacedError(body.message || '请先登录')
  }
  if (body.code !== 200) throwForErrorBody(body)

  return body.data as T
}

export async function get<T>(url: string, data?: RequestData): Promise<T> {
  return request<T>('GET', url, data)
}

export async function post<T>(url: string, data?: RequestData): Promise<T> {
  return request<T>('POST', url, data)
}

export async function put<T>(url: string, data?: RequestData): Promise<T> {
  return request<T>('PUT', url, data)
}

export async function del<T>(url: string, data?: RequestData, options?: RequestOptions): Promise<T> {
  return request<T>('DELETE', url, data, options)
}

