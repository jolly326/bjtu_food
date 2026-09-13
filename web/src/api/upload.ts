import { API_BASE_URL } from './config'
import type { ApiResponse } from './http'
import { emitUnauthorized } from './http'

export interface UploadImageResult {
  url: string
  relativeUrl: string
}

/**
 * 图片上传（独立 fetch 通道：multipart 不走 http.ts 的 JSON request）。
 * 与 http.ts 对齐的兜底（WEB-107）：
 * ① 401 走同一 emitUnauthorized 失效广播（清 token + 跳登录），不再各自为政；
 * ② 响应 JSON.parse 包保护——网关 502/504 返 HTML 时不裸抛 SyntaxError，给可读错误；
 * ③ 5s 超时（AbortController），避免上传请求无界挂起。
 */
export async function uploadImage(file: File): Promise<UploadImageResult> {
  const formData = new FormData()
  formData.append('file', file)

  // 超时 30s：图片体积（手机原图常 2-5MB，且需后端中转再上传 COS）远大于普通 JSON 请求，
  // 沿用 http.ts 的 5s 会在正常上传下频繁误报「上传超时」。
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 30000)

  try {
    const res = await fetch(`${API_BASE_URL}/upload/image`, {
      method: 'POST',
      headers: {
        'X-Admin-Token': import.meta.env.VITE_ADMIN_TOKEN || '',
      },
      body: formData,
      signal: controller.signal,
    })

    // ① 401 与 http.ts 同通道处理；403 语义对齐（无权限 ≠ 未登录）
    if (res.status === 401) {
      emitUnauthorized()
      throw new Error('登录已失效，请重新登录')
    }
    if (res.status === 403) {
      throw new Error('无权限执行此操作')
    }

    const text = await res.text()
    if (!text) {
      throw new Error('上传接口响应体为空')
    }

    // ② 网关异常（502/504）可能返回 HTML 错误页而非 JSON：解析失败时给可读错误而非 SyntaxError
    let body: ApiResponse<UploadImageResult>
    try {
      body = JSON.parse(text) as ApiResponse<UploadImageResult>
    } catch {
      throw new Error(res.ok ? '上传服务响应格式异常' : `上传服务异常（HTTP ${res.status}）`)
    }

    if (!res.ok || body.code !== 200) {
      throw new Error(body.message || `图片上传失败（HTTP ${res.status}）`)
    }
    if (!body.data?.url || !body.data?.relativeUrl) {
      throw new Error('上传接口返回缺少图片地址')
    }
    return body.data
  } catch (e: any) {
    // 超时中止的 AbortError 转可读文案；其余错误保留原始 message（无 message 时兜底中文）
    if (e?.name === 'AbortError') throw new Error('上传超时，请稍后重试')
    throw new Error(e?.message || '图片上传失败，请检查网络后重试')
  } finally {
    clearTimeout(timeout)
  }
}
