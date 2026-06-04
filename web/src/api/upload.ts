import { API_BASE_URL } from './config'
import type { ApiResponse } from './http'

export interface UploadImageResult {
  url: string
  relativeUrl: string
}

export async function uploadImage(file: File): Promise<UploadImageResult> {
  const formData = new FormData()
  formData.append('file', file)

  const res = await fetch(`${API_BASE_URL}/upload/image`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${localStorage.getItem('token') || ''}`,
    },
    body: formData,
  })

  const text = await res.text()
  if (!text) {
    throw new Error(res.status === 401 || res.status === 403 ? '请先登录管理员账号' : '上传接口响应体为空')
  }

  const body = JSON.parse(text) as ApiResponse<UploadImageResult>
  if (!res.ok || body.code !== 200) {
    throw new Error(body.message || '图片上传失败')
  }
  if (!body.data?.url || !body.data?.relativeUrl) {
    throw new Error('上传接口返回缺少图片地址')
  }
  return body.data
}
