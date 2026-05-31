/**
 * HTTP 请求工具
 * 封装 uni.request，统一处理 token、错误、响应格式
 */

import { API_BASE_URL } from './config'

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

function getToken(): string {
  return uni.getStorageSync('token') || ''
}

async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  data?: any,
  options?: { header?: any; hideLoading?: boolean }
): Promise<T> {
  try {
    const res = await new Promise<any>((resolve, reject) => {
      const task = uni.request({
        url: `${API_BASE_URL}${url}`,
        method,
        data,
        header: {
          'Authorization': `Bearer ${getToken()}`,
          ...(options?.header || {}),
        },
        success: resolve,
        fail: (err) => reject(new Error(err.errMsg || '网络请求失败')),
      })
      // 1 秒兜底超时 —— 开发阶段后端未启动时快速降级到 mock
      setTimeout(() => {
        task.abort()
        reject(new Error('请求超时'))
      }, 1000)
    })

    const body = res.data as ApiResponse<T>

    if (body.code !== 200) {
      throw new Error(body.message || '请求失败')
    }

    return body.data as T
  } catch (e: any) {
    // 网络错误或业务错误
    throw new Error(e.message || '网络异常，请稍后重试')
  }
}

/**
 * GET 请求
 * 请求失败时抛异常，调用方自行决定是否降级 mock
 */
export async function get<T>(url: string, data?: any): Promise<T> {
  console.log('[http] GET', url)
  const result = await request<T>('GET', url, data)
  console.log('[http] GET成功', url)
  return result
}

/**
 * POST 请求
 * 请求失败时抛异常，调用方自行决定是否降级 mock
 */
export async function post<T>(url: string, data?: any): Promise<T> {
  return request<T>('POST', url, data)
}

/**
 * PUT 请求
 * 请求失败时抛异常，调用方自行决定是否降级 mock
 */
export async function put<T>(url: string, data?: any): Promise<T> {
  return request<T>('PUT', url, data)
}

/**
 * DELETE 请求
 * 请求失败时抛异常，调用方自行决定是否降级 mock
 */
export async function del<T>(url: string, data?: any): Promise<T> {
  return request<T>('DELETE', url, data)
}

/**
 * 上传文件（POST multipart/form-data）
 * 上传不自动显示 loading
 */
export function uploadFile(tempFilePath: string): Promise<{ url: string }> {
  const token = getToken()

  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE_URL}/upload/image`,
      filePath: tempFilePath,
      name: 'file',
      header: {
        'Authorization': `Bearer ${token}`,
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
