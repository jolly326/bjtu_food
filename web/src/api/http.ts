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
  let requestUrl = url
  if (method === 'GET' && data && Object.keys(data).length > 0) {
    const query = new URLSearchParams()
    Object.entries(data).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') query.append(key, String(value))
    })
    const queryString = query.toString()
    if (queryString) requestUrl += `${requestUrl.includes('?') ? '&' : '?'}${queryString}`
  }

  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 5000)

  try {
    const res = await fetch(`${API_BASE_URL}${requestUrl}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
      },
      body: method !== 'GET' && data ? JSON.stringify(data) : undefined,
      signal: controller.signal,
    })

    clearTimeout(timeout)
    if (!res.ok) throw new Error(`HTTP ${res.status}`)

    const body: ApiResponse<T> = await res.json()
    if (body.code !== 200) throw new Error(body.message || '请求失败')
    return body.data
  } catch (e: any) {
    clearTimeout(timeout)
    throw new Error(e.message || '网络异常')
  }
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

export async function del<T>(url: string): Promise<T> {
  return request<T>('DELETE', url)
}
