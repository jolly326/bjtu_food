/**
 * HTTP request helpers for uni.request.
 * Requests throw on network or business errors; pages/stores decide whether to
 * show an empty state or an error message.
 */

import { API_BASE_URL } from './config'

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

async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  data?: any,
  options?: { header?: any; hideLoading?: boolean },
): Promise<T> {
  let res: any
  try {
    res = await new Promise<any>((resolve, reject) => {
      const task = uni.request({
        url: `${API_BASE_URL}${url}`,
        method,
        data,
        header: {
          Authorization: `Bearer ${getToken()}`,
          ...(options?.header || {}),
        },
        success: resolve,
        fail: err => reject(new Error(err.errMsg || '网络请求失败')),
      })
      setTimeout(() => {
        task.abort()
        reject(new Error('请求超时'))
      }, 8000)
    })
  } catch (e: any) {
    // 网络层错误（超时 / 断网）：不抛出裸错误，统一提示
    uni.showToast({ title: e.message || '网络异常，请稍后重试', icon: 'none' })
    throw e
  }

  const body = res.data as ApiResponse<T>
  if (body.code === 401) {
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
  console.log('[http] GET', url)
  const result = await request<T>('GET', url, data)
  console.log('[http] GET success', url)
  return result
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
