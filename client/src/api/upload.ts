import { uploadFile } from './http'

/**
 * 上传图片
 * - 微信小程序端：微信云存储 wx.cloud.uploadFile，返回 cloud:// 文件 ID
 * - 其他端（H5 等）：POST /api/upload/image (multipart/form-data)，返回后端绝对 URL
 *
 * @param tempFilePath 本地临时文件路径（从 uni.chooseImage 获取）
 * @returns 上传后可直接存储/展示的图片地址（cloud:// 或 http(s)）
 */
export async function uploadImage(tempFilePath: string): Promise<string> {
  const result = await uploadFile(tempFilePath)
  return result.url
}
