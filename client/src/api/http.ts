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

/**
 * 统一未登录/登录失效处理（§5.x 401 处理）：
 * 清本地登录态 + Toast + 重新触发微信静默登录（wechat-login）。
 * 用动态 import 避免 user store ↔ http 的循环依赖；forceLogout 幂等，可安全延迟执行。
 * 不再使用全局 uni.$on/$emit 事件总线，规避 HMR/模块重复加载导致的重复订阅泄漏。
 */
async function handleUnauthorized(): Promise<void> {
  try {
    const { useUserStore } = await import('@/stores/user')
    useUserStore().forceLogout()
    uni.showToast({ title: '登录已失效，正在重新登录', icon: 'none' })
    // 401 → 重新静默登录（游客态自动恢复）
    useUserStore().silentLogin()
  } catch {
    // 兜底：极端情况下动态 import 失败，直接清 storage
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
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
    const { useAuthSheetStore } = await import('@/stores/authSheet')
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
  if (body.code === 401) {
    // 401 登录失效：清登录态 + 重新静默登录（§5.x）
    void handleUnauthorized()
    throw new Error(body.message || '请先登录')
  }
  if (body.code === 403) {
    // 403 无权限：游客访问需 verified 的社区写接口 → 提示 + 弹认证引导（§5.y/§5.x）
    void handleForbidden()
    throw new Error(body.message || '请先完成学号邮箱认证')
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
 * 上传图片到后端（multipart/form-data）。
 * ⚠️ 说明：uni.uploadFile 走 uploadFile 合法域名白名单，callContainer 无法处理文件上传；
 * 当前云托管测试域名未备案，上传功能在真机/正式环境暂不可用（seed 数据无图，用 emoji 占位降级）。
 * 中期方案：改走微信云开发云存储（wx.cloud.uploadFile）+ cloud:// 文件 ID（显示也不受域名限制）。
 */
export function uploadFile(tempFilePath: string): Promise<{ url: string }> {
  const token = getToken()

  return new Promise((resolve, reject) => {
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
}
