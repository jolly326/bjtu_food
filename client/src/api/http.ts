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
 * 统一未登录/登录失效处理：
 * 清空本地 token，并抛出业务异常（页面层再决定如何提示），
 * 同时通过全局事件通知应用层跳转到登录，避免在各处裸弹「401」错误。
 */
function handleUnauthorized(): void {
  uni.removeStorageSync('token')
  uni.removeStorageSync('userInfo')
  // 供 task-02 登录页 / 路由守卫统一拦截跳转
  uni.$emit('auth:unauthorized')
  uni.showToast({ title: '登录已失效，请重新登录', icon: 'none' })
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
      wxApi.cloud.callContainer({
        config: { env: WX_CLOUD_ENV },
        path: url.startsWith('/api') ? url : `/api${url}`,
        method,
        data,
        header: {
          'X-WX-SERVICE': WX_SERVICE,
          ...header,
        },
        success: (r: any) => done(() => resolve(r)),
        fail: (err: any) => done(() => reject(new Error(err.errMsg || '网络请求失败'))),
      })
      setTimeout(() => done(() => reject(new Error('请求超时'))), 8000)
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
        success: resolve,
        fail: err => reject(new Error(err.errMsg || '网络请求失败')),
      })
      setTimeout(() => {
        task.abort()
        reject(new Error('请求超时'))
      }, 8000)
    })
    // #endif
  } catch (e: any) {
    // 网络层错误（超时 / 断网）：不抛出裸错误，统一提示
    uni.showToast({ title: e.message || '网络异常，请稍后重试', icon: 'none' })
    throw e
  }

  const body = parseBody<T>(res.data)
  if (body.code === 401 || body.code === 403) {
    // 401 登录失效；403 通常是 token 失效/权限不足（方法级 @PreAuthorize 对失效 token 返回 403），
    // 均按登录失效处理：清本地登录态并触发全局引导重新登录，避免反复报错
    handleUnauthorized()
    throw new Error(body.message || '请先登录')
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
