import { post } from './http'

export interface UploadResult {
  url: string
}

/** 上传图片（菜品 / Banner 配图），返回绝对 URL。 */
export function uploadImage(file: File): Promise<UploadResult> {
  const form = new FormData()
  form.append('file', file)
  return post<UploadResult>('/admin/upload', form, { isForm: true })
}
