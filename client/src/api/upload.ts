import { uploadFile } from './http'

/**
 * 上传图片
 * POST /api/upload/image (multipart/form-data)
 *
 * @param tempFilePath 本地临时文件路径（从 uni.chooseImage 获取）
 * @returns 上传后可访问的图片 URL
 */
export async function uploadImage(tempFilePath: string): Promise<string> {
  const result = await uploadFile(tempFilePath)
  return result.url
}
