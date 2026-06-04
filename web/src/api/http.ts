/**
 * HTTP 请求工具
 * 封装 fetch，统一处理 token、错误、响应格式
 * 请求失败（超时/网络不通）时抛出异常，调用方接管降级逻辑
 */

import { API_BASE_URL } from './config'

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE',
  url: string,
  data?: any,
): Promise<T> {
  console.log(`[http] ${method} ${url}`, data || '')

  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 5000)

  try {
    const res = await fetch(`${API_BASE_URL}${url}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`,
      },
      body: data ? JSON.stringify(data) : undefined,
      signal: controller.signal,
    })

    clearTimeout(timeout)
    console.log(`[http] ${method} ${url} → ${res.status}`)

    if (!res.ok) throw new Error(`HTTP ${res.status}`)

    const body: ApiResponse<T> = await res.json()
    if (body.code !== 200) throw new Error(body.message || '请求失败')
    console.log(`[http] ${method} ${url} 成功`)
    return body.data
  } catch (e: any) {
    clearTimeout(timeout)
    console.log(`[http] ${method} ${url} 失败: ${e.message}`)
    throw new Error(e.message || '网络异常')
  }
}

export async function get<T>(url: string): Promise<T> { return request<T>('GET', url) }
export async function post<T>(url: string, data?: any): Promise<T> { return request<T>('POST', url, data) }
export async function put<T>(url: string, data?: any): Promise<T> { return request<T>('PUT', url, data) }
export async function del<T>(url: string): Promise<T> { return request<T>('DELETE', url) }
