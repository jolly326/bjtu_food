/** 运营端图片上传（docs/api/web/images.md）：按资源成文件，请求细节留给 `request.ts`。 */

import { http } from './request'

/** multipart 上传单图，返回绝对 URL */
export async function upload(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  const data = await http.form<{ url: string } | null>('/admin/images', form)
  if (!data?.url) {
    throw new Error('上传失败')
  }
  return data.url
}
