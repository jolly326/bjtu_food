/**
 * HTTP request helpers.
 * - 微信小程序端：使用 wx.cloud.callContainer 调用微信云托管后端
 *   （走微信内部链路，无需配置 request 合法域名 / 备案域名）。
 * - 其他端（H5 等）：回退为 uni.request 普通 HTTP 请求（供本地联调 / 非微信端）。
 * Requests throw on network or business errors; pages/stores decide whether to
 * show an empty state or an error message.
 */

import { API_BASE_URL, WX_CLOUD_ENV, WX_SERVICE } from './config'

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

/** 401 处理进行中标志：避免并发 401（如首页多请求同时失效）重复触发登出+重登+Toast 风暴 */
let _authHandling = false

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
    uni.showToast({ title: '登录已失效，正在重新登录', icon: 'none' })
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
 * 统一无权限/未认证处理（§5.y / §5.x 403）：
 * 社区写操作需 verified=true，游客触发时后端返回 403 →
 * 前端提示「请先完成学号邮箱认证」并弹认证引导（AuthSheet）。
 */
async function handleForbidden(): Promise<void> {
  uni.showToast({ title: '请先完成学号邮箱认证', icon: 'none' })
  try {
    const { useAuthSheetStore } = await import('@/stores/auth-sheet')
    useAuthSheetStore().show()
  } catch {
    // 兜底：极端情况忽略，仅提示
  }
}

function getToken(): string {
  return uni.getStorageSync('token') || ''
}

/** 解析响应体：兼容 JSON 字符串或已解析对象 */
function parseBody<T>(data: any): ApiResponse<T> {
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
  data?: any,
  options?: { header?: any; hideLoading?: boolean },
  _retried = false,
): Promise<T> {
  const header = {
    Authorization: `Bearer ${getToken()}`,
    ...(options?.header || {}),
  }

  let res: any
  try {
    // ===== 微信小程序端：走云托管内部链路（免域名白名单） =====
    // #ifdef MP-WEIXIN
    res = await new Promise<any>((resolve, reject) => {
      let settled = false
      const done = (fn: () => void) => {
        if (!settled) {
          settled = true
          fn()
        }
      }
      const wxApi: any = (globalThis as any).wx
      if (!wxApi || !wxApi.cloud) {
        done(() => reject(new Error('当前环境不支持 wx.cloud')))
        return
      }
      // N04 修复：超时定时器保存句柄，settle 后清理
      const timeoutTimer = setTimeout(() => {
        done(() => reject(new Error('请求超时')))
      }, 8000)
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
        success: (r: any) => { clearTimer(); done(() => resolve(r)) },
        fail: (err: any) => { clearTimer(); done(() => reject(new Error(err.errMsg || '网络请求失败'))) },
      })
    })
    // #endif

    // ===== 其他端（H5 等）：回退普通 HTTP =====
    // #ifndef MP-WEIXIN
    res = await new Promise<any>((resolve, reject) => {
      const task = uni.request({
        url: `${API_BASE_URL}${url}`,
        method,
        data,
        header,
        success: (r: any) => { clearTimer(); resolve(r) },
        fail: err => { clearTimer(); reject(new Error(err.errMsg || '网络请求失败')) },
      })
      let finished = false
      const timeoutTimer = setTimeout(() => {
        finished = true
        // N04 修复：仅对尚未完成的 task abort，避免对已完成任务重复 abort
        if (task && typeof task.abort === 'function') task.abort()
        reject(new Error('请求超时'))
      }, 8000)
      const clearTimer = () => {
        if (!finished) clearTimeout(timeoutTimer)
      }
    })
    // #endif
  } catch (e: any) {
    // 网络层错误（超时 / 断网）：不抛出裸错误，统一提示
    uni.showToast({ title: e.message || '网络异常，请稍后重试', icon: 'none' })
    throw e
  }

  const body = parseBody<T>(res.data)
  // 空响应 / 网关错误页容错：body 可能不是规范的 ApiResponse（如 Nginx 返回 HTML 错误页、
  // 后端宕机返回空数据），直接访问 body.code 会抛 TypeError。统一降级为可识别错误。
  if (!body || typeof body.code !== 'number') {
    const detail = typeof res.data === 'string' ? res.data.slice(0, 80) : ''
    throw new Error(detail ? `服务响应异常：${detail}` : '服务响应异常，请稍后重试')
  }
  if (body.code === 401) {
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
    throw new Error(body.message || '请先登录')
  }
  if (body.code === 4031) {
    // 4031 = 邮箱未认证（细分业务码，区别于普通权限拒绝 403）。
    // 游客触发需 verified 的社区写接口 → 提示 + 弹认证引导（§5.y/§5.x）。
    void handleForbidden()
    throw new Error(body.message || '请先完成学号邮箱认证')
  }
  if (body.code === 403) {
    // 403 = 普通权限拒绝（如越权访问管理接口）：不应误导用户去做邮箱认证。
    uni.showToast({ title: '无权限访问该内容', icon: 'none' })
    throw new Error(body.message || '无权限访问该内容')
  }
  if (body.code !== 200) {
    // 业务错误：由调用方决定提示方式，这里统一抛出 message
    throw new Error(body.message || '请求失败')
  }

  return body.data as T
}

export async function get<T>(url: string, data?: any): Promise<T> {
  return request<T>('GET', url, data)
}

export async function post<T>(url: string, data?: any): Promise<T> {
  return request<T>('POST', url, data)
}

export async function put<T>(url: string, data?: any): Promise<T> {
  return request<T>('PUT', url, data)
}

export async function del<T>(url: string, data?: any): Promise<T> {
  return request<T>('DELETE', url, data)
}

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
    const wxApi: any = (globalThis as any).wx
    if (!wxApi || !wxApi.cloud) {
      reject(new Error('当前环境不支持 wx.cloud'))
      return
    }
    // cloudPath：images/YYYY-MM-DD/<时间戳>-<随机数><原扩展名>，避免同名覆盖
    const ext = (tempFilePath.match(/\.\w+$/) || ['.jpg'])[0]
    const stamp = Date.now()
    const rand = Math.random().toString(36).slice(2, 8)
    const cloudPath = `images/${new Date().toISOString().slice(0, 10)}/${stamp}-${rand}${ext}`
    wxApi.cloud.uploadFile({
      config: { env: WX_CLOUD_ENV },
      cloudPath,
      filePath: tempFilePath,
      success: (r: any) => resolve({ url: r.fileID }),
      fail: (err: any) => reject(new Error(err.errMsg || '上传失败，请重试')),
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
      fail() {
        reject(new Error('上传失败，请重试'))
      },
    })
  })
  // #endif

  return result
}
