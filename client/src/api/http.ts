/**
 * HTTP request helpers.
 * - 微信小程序端：使用 wx.cloud.callContainer 调用微信云托管后端
 *   （走微信内部链路，无需配置 request 合法域名 / 备案域名）。
 * - 其他端（H5 等）：回退为 uni.request 普通 HTTP 请求（供本地联调 / 非微信端）。
 * Requests throw on network or business errors; pages/stores decide whether to
 * show an empty state or an error message.
 */

import { API_BASE_URL, WX_CLOUD_ENV, WX_SERVICE } from './config'

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/**
 * 「已由请求层提示过」的错误标记。
 * 请求层对网络异常 / 401 / 4031 / 403 已各自提示（4031 另弹 AuthSheet 认证引导；
 * 403 且 message 指向「微信登录」时另弹窗说明 + 用户确认后才重跑微信静默登录补 openid，
 * 依据 spec §7.7 第 1 条（2026-09-14 裁决）：提示 + 用户主动确认，禁止自动重登换登录态），
 * 调用方 catch 到本类型时应只做状态回滚，不再重复提示（避免同一失败弹两条提示）。
 */
export class SurfacedError extends Error {}

/** 请求体：兼容对象 / 纯字符串 / 二进制（原 any 边界收窄为可命名联合；接口类型通过 object 收录） */
export type RequestData = string | object | ArrayBuffer | undefined

/** 请求选项：header 收窄为字符串表（原 any） */
export interface RequestOptions {
  header?: Record<string, string>
  hideLoading?: boolean
  /** 注销等敏感调用：401 时禁止「静默登录后重试」（重试会以新游客身份执行，可能误删新账号） */
  skipAuthRetry?: boolean
}

/** 平台响应载体：请求层只关心 body 内容（data），其余字段由微信/uni 回调自身携带 */
interface RawResponse {
  data?: unknown
}

/** 401 处理进行中标志：避免并发 401（如首页多请求同时失效）重复触发登出+重登+Toast 风暴 */
let _authHandling = false

/** 「登录已失效」Toast 冷却窗口：同文案 5s 内不重复（部署事故期连续 401 时防 Toast 风暴） */
const AUTH_TOAST_COOLDOWN_MS = 5000
let _lastAuthToastAt = 0

/**
 * 普通请求超时：12s。
 * 小程序端走 wx.cloud.callContainer（微信云托管），实例缩容到零后首次请求需冷启动拉起容器
 * （通常 3~8s，弱网下更久），8s 在冷启动 + 弱网叠加时易误报「请求超时」，故放宽至 12s。
 * H5 分支取同一常量，保证端间超时口径一致；上传走更宽的 UPLOAD_TIMEOUT_MS（15s）。
 */
const REQUEST_TIMEOUT_MS = 12000

/**
 * 统一未登录/登录失效处理（§5.x 401 处理）：
 * 清本地登录态 + Toast + 重新触发微信静默登录（wechat-login）。
 * 用动态 import 避免 user store ↔ http 的循环依赖；forceLogout 幂等，可安全延迟执行。
 * 不再使用全局 uni.$on/$emit 事件总线，规避 HMR/模块重复加载导致的重复订阅泄漏。
 * 通过 _authHandling 去重，防止并发 401 放大为多次登录请求与叠加 Toast。
 */
async function handleUnauthorized(): Promise<void> {
  if (_authHandling) {
    // 已有处理在进行：直接等待其完成，不重复登出/重登/弹窗
    return
  }
  _authHandling = true
  try {
    const { useUserStore } = await import('@/stores/user')
    useUserStore().forceLogout()
    // Toast 5s 冷却：部署事故（如后端 5xx 期间 token 校验连续失败）时用户每次操作都会走到这里，
    // 同文案 5s 内不重复弹，避免 Toast 风暴；登出 + 重登流程本身不受冷却影响（401 重试逻辑不变）。
    const now = Date.now()
    if (now - _lastAuthToastAt > AUTH_TOAST_COOLDOWN_MS) {
      _lastAuthToastAt = now
      uni.showToast({ title: '登录已失效，正在重新登录', icon: 'none' })
    }
    // 401 → 重新静默登录（游客态自动恢复）
    await useUserStore().silentLogin()
  } catch {
    // 兜底：极端情况下动态 import 失败，直接清 storage
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
  } finally {
    // 延迟复位，确保后续真正失效的 401 能再次触发引导
    setTimeout(() => { _authHandling = false }, 300)
  }
}

/**
 * 统一「邮箱未认证」处理（4031）：
 * UGC 写操作需 verified=true，游客触发时后端返回 4031（细分业务码）→
 * 前端提示「请先完成学号邮箱认证」并弹认证引导（AuthSheet）。
 * 与普通 403 严格分流：4031 弹认证表单，403 不弹（避免误导用户去改邮箱）。
 */
async function handleUnverified(): Promise<void> {
  uni.showToast({ title: '请先完成学号邮箱认证', icon: 'none' })
  try {
    const { useAuthSheetStore } = await import('@/stores/auth-sheet')
    useAuthSheetStore().show()
  } catch {
    // 兜底：极端情况忽略，仅提示
  }
}

/**
 * 统一「需微信登录」处理（403 且 message 指向微信登录，spec §7.5 / §7.7 第 1 条）：
 * 已认证（verified=1）但账号缺 openid（如仅经邮箱链路建号）时，后端返回 403 +
 * message「请使用微信登录后再发布评价」。端上处置 = 「提示 + 用户主动确认」
 * （依据 spec §7.7 第 1 条，2026-09-14 裁决，禁止自动重登换登录态）：
 * 弹窗说明 + 用户点「重新登录」确认后，才重跑微信静默登录（wx.login → POST /auth/wechat-login）
 * 补齐 openid；**禁止自动重登**——对「无 openid 的历史学号账号」自动重登会静默切到新游客号
 * （登录态无感知互换，且新号 verified=false，重试仍撞 4031），顺滑收益≈0，静默换号代价真实。
 * 用户点取消 / 弹窗调用失败：仅保留提示，不换号、不清登录态；确认重登成功后提示用户重试原操作。
 * **不新增页面、不弹邮箱认证引导**（邮箱已认证，弹它属误导）。
 */
function isWechatLoginRequired(msg: string): boolean {
  return /微信登录/.test(msg)
}

function handleWechatLoginRequired(msg: string): void {
  uni.showModal({
    title: '需要微信登录',
    content: msg,
    confirmText: '重新登录',
    showCancel: true,
    success: (r) => {
      // 取消：弹窗本身已说明原因，仅保留提示，不换号、不清登录态
      if (!r.confirm) return
      void (async () => {
        try {
          const { useUserStore } = await import('@/stores/user')
          const userStore = useUserStore()
          // 关键：必须经 wx.login 重新取 code 换号（后端在 wechat-login 时按 openid 绑定），
          // 而 silentLogin 在「已有 token」分支只刷新 profile、不会补 openid。
          // 故先清本地态（forceLogout 幂等，仅清 token/userInfo，不触发请求），再跑完整静默登录。
          userStore.forceLogout()
          await userStore.silentLogin(true)
          // 成功仅表示已补齐 openid，原操作需用户自行重试
          uni.showToast({ title: '已重新登录，请重试', icon: 'none' })
        } catch {
          // 兜底：重新登录动作失败——仅提示，本地登录态按 401 既有机制自恢复，不在此重复处理
          uni.showToast({ title: '重新登录未完成，请稍后重试', icon: 'none' })
        }
      })()
    },
    fail: () => {
      // 弹窗调用失败（极端环境）：降级为仅提示后端 message，不换号、不清登录态
      uni.showToast({ title: msg, icon: 'none' })
    },
  })
}

function getToken(): string {
  return uni.getStorageSync('token') || ''
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
    // ===== 微信小程序端：走云托管内部链路（免域名白名单） =====
    // #ifdef MP-WEIXIN
    res = await new Promise<RawResponse>((resolve, reject) => {
      let settled = false
      const done = (fn: () => void) => {
        if (!settled) {
          settled = true
          fn()
        }
      }
      // 平台例外：wx 句柄为微信运行时对象，未纳入项目 TS 类型（与 navMetrics 同款说明）
      const wxApi: any = (globalThis as any).wx
      if (!wxApi || !wxApi.cloud) {
        done(() => reject(new Error('当前环境不支持 wx.cloud')))
        return
      }
      // N04 修复：超时定时器保存句柄，settle 后清理；12s 兼顾云托管冷启动（见 REQUEST_TIMEOUT_MS 注释）
      const timeoutTimer = setTimeout(() => {
        done(() => reject(new Error('请求超时')))
      }, REQUEST_TIMEOUT_MS)
      const clearTimer = () => { clearTimeout(timeoutTimer) }
      wxApi.cloud.callContainer({
        config: { env: WX_CLOUD_ENV },
        path: url.startsWith('/api') ? url : `/api${url}`,
        method,
        data,
        header: {
          'X-WX-SERVICE': WX_SERVICE,
          ...header,
        },
        // 平台例外：微信回调透传，仅取其 data 字段
        success: (r: any) => { clearTimer(); done(() => resolve({ data: r?.data })) },
        fail: (err: any) => { clearTimer(); done(() => reject(new Error(err.errMsg || '网络请求失败'))) },
      })
    })
    // #endif

    // ===== 其他端（H5 等）：回退普通 HTTP =====
    // #ifndef MP-WEIXIN
    res = await new Promise<RawResponse>((resolve, reject) => {
      let finished = false
      const timeoutTimer = setTimeout(() => {
        finished = true
        // N04 修复：仅对尚未完成的 task abort，避免对已完成任务重复 abort
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
    // #endif
  } catch (e) {
    // 网络层错误（超时 / 断网）：不抛出裸错误，统一提示；标记已提示，调用方只需回滚状态
    uni.showToast({ title: e instanceof Error ? e.message : '网络异常，请稍后重试', icon: 'none' })
    throw new SurfacedError(e instanceof Error ? e.message : '网络异常，请稍后重试')
  }

  const body = parseBody<T>(res.data)
  // 空响应 / 网关错误页容错：body 可能不是规范的 ApiResponse（如 Nginx 返回 HTML 错误页、
  // 后端宕机返回空数据），直接访问 body.code 会抛 TypeError。统一降级为可识别错误。
  if (!body || typeof body.code !== 'number') {
    const detail = typeof res.data === 'string' ? res.data.slice(0, 80) : ''
    throw new Error(detail ? `服务响应异常：${detail}` : '服务响应异常，请稍后重试')
  }
  if (body.code === 401) {
    // skipAuthRetry（注销等敏感调用）：401 直接上抛——静默登录可能建出新游客号，重试会误删新账号。
    if (options?.skipAuthRetry) {
      throw new Error('登录状态已失效，请重新进入小程序后操作')
    }
    // 401 登录失效 / 启动竞态（请求早于静默登录拿到 token）。
    // 策略：先确保静默登录完成（拿到 token），再自动重试一次；
    // 重试仍 401 才视为真正失效并提示，避免游客态启动时的误报（§5.x）。
    if (!_retried) {
      try {
        const { useUserStore } = await import('@/stores/user')
        await useUserStore().silentLogin()
        return request<T>(method, url, data, options, true)
      } catch {
        // 静默登录失败：降级为原处理
      }
    }
    await handleUnauthorized()
    throw new SurfacedError(body.message || '请先登录')
  }
  if (body.code === 4031) {
    // 4031 = 邮箱未认证（细分业务码，区别于普通权限拒绝 403）。
    // 游客触发需 verified 的 UGC 写接口 → 提示 + 弹认证引导（§5.y/§5.x）。
    void handleUnverified()
    throw new SurfacedError(body.message || '请先完成学号邮箱认证')
  }
  if (body.code === 403) {
    // 403 = 普通权限拒绝，**两种子情形分流**（spec §7.5 / §7.7 第 1 条、Q-111）：
    // ① 已认证但缺 openid（message 含「微信登录」）→ 弹窗说明 + 用户确认后重跑微信静默登录补 openid
    //   （2026-09-14 裁决：提示 + 主动确认，禁止自动重登换登录态），不弹邮箱认证；
    // ② 其他普通无权限（越权 / 非本人资源 / 账号禁用）→ 仅透传后端 message 提示。
    // 两种情形都不弹 AuthSheet（邮箱认证引导），避免把「需微信登录」误导成「需邮箱认证」。
    const msg = body.message || '无权限访问该内容'
    if (isWechatLoginRequired(msg)) {
      void handleWechatLoginRequired(msg)
    } else {
      uni.showToast({ title: msg, icon: 'none' })
    }
    throw new SurfacedError(msg)
  }
  if (body.code !== 200) {
    // 业务错误：由调用方决定提示方式，这里统一抛出 message
    throw new Error(body.message || '请求失败')
  }

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

/** 上传超时（MP-003）：二进制文件比 JSON 请求慢，在 request 12s 基础上放宽至 15s，避免上传 promise 永久挂起 */
const UPLOAD_TIMEOUT_MS = 15000

/**
 * 上传图片。
 * - 微信小程序端：走微信云存储 wx.cloud.uploadFile，返回 cloud:// 文件 ID。
 *   cloud:// 无需 uploadFile 合法域名白名单，且 <image> 组件原生支持直接显示；
 *   后端原样存储该 ID，展示链路经 getImageUrl 透传（见 utils/image.ts）。
 * - 其他端（H5 等）：回退为 uni.uploadFile 上传到后端（需后端可达）。
 */
export function uploadFile(tempFilePath: string): Promise<{ url: string }> {
  const token = getToken()

  let result!: Promise<{ url: string }>

  // ===== 微信小程序端：微信云存储 =====
  // #ifdef MP-WEIXIN
  result = new Promise<{ url: string }>((resolve, reject) => {
    // 平台例外：wx 句柄为微信运行时对象（同 request 说明）
    const wxApi: any = (globalThis as any).wx
    if (!wxApi || !wxApi.cloud) {
      reject(new Error('当前环境不支持 wx.cloud'))
      return
    }
    // MP-003：超时保护（同 request 的 N04 settled 模式），超时/失败及时 reject，
    // 防止调用方上传中守卫位（如 avatarUploading）永久锁死
    let settled = false
    const done = (fn: () => void) => {
      if (!settled) {
        settled = true
        fn()
      }
    }
    const timeoutTimer = setTimeout(() => {
      done(() => {
        if (task && typeof task.abort === 'function') task.abort()
        reject(new Error('上传超时，请重试'))
      })
    }, UPLOAD_TIMEOUT_MS)
    const clearTimer = () => { clearTimeout(timeoutTimer) }
    // cloudPath：images/YYYY-MM-DD/<时间戳>-<随机数><原扩展名>，避免同名覆盖
    const ext = (tempFilePath.match(/\.\w+$/) || ['.jpg'])[0]
    const stamp = Date.now()
    const rand = Math.random().toString(36).slice(2, 8)
    const cloudPath = `images/${new Date().toISOString().slice(0, 10)}/${stamp}-${rand}${ext}`
    const task = wxApi.cloud.uploadFile({
      config: { env: WX_CLOUD_ENV },
      cloudPath,
      filePath: tempFilePath,
      // 平台例外：微信回调透传，仅取其 fileID
      success: (r: any) => { clearTimer(); done(() => resolve({ url: r.fileID })) },
      fail: (err: any) => { clearTimer(); done(() => reject(new Error(err.errMsg || '上传失败，请重试'))) },
    })
  })
  // #endif

  // ===== 其他端（H5 等）：上传到后端 =====
  // #ifndef MP-WEIXIN
  result = new Promise<{ url: string }>((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE_URL}/upload/image`,
      filePath: tempFilePath,
      name: 'file',
      // MP-003：与小程序端一致的上传超时保护
      timeout: UPLOAD_TIMEOUT_MS,
      header: {
        Authorization: `Bearer ${token}`,
      },
      success(res) {
        try {
          const body = JSON.parse(res.data) as ApiResponse<{ url: string }>
          if (body.code === 200) {
            resolve(body.data)
          } else {
            reject(new Error(body.message || '上传失败'))
          }
        } catch {
          reject(new Error('上传响应格式错误'))
        }
      },
      fail(err) {
        // 超时体现在 errMsg（request:fail timeout），区分给出可读文案
        reject(new Error(/timeout/i.test(err?.errMsg || '') ? '上传超时，请重试' : '上传失败，请重试'))
      },
    })
  })
  // #endif

  return result
}
