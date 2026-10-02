import { uploadFile, post } from './http'
import type { UploadResultVO } from './shared'

/** 上传结果只透出 `url`：`relativeUrl` 仅本地磁盘降级链路有值，端上展示一律用 `url`（类型仍来自契约） */
type UploadedImage = Pick<UploadResultVO, 'url'>

/**
 * 头像上传（**仅限本人非公开用途**）。
 *
 * ⚠️ 合规红线：本函数**不做内容安检**，**禁止**用于评价 / 反馈等一切他人可见的 UGC 配图 ——
 * UGC 必须走 {@link uploadUgcImage}（安检 + 转存 COS）。
 * 当前唯一合法调用点：`pages/profile`（本人头像）。
 *
 * @param tempFilePath 本地临时文件路径（`uni.chooseImage` 产物）
 * @returns 可直接存储 / 展示的图片地址（`cloud://` 或 http(s)）
 */
export async function uploadAvatarImage(tempFilePath: string): Promise<string> {
  const result = await uploadFile(tempFilePath)
  return result.url
}

/**
 * UGC 配图上传（评价 / 反馈共用）。
 *
 * 流程：① 小程序端 `wx.cloud.uploadFile` 传至微信云存储取 fileID；② `POST /upload/cloud-image`
 * 由后端做内容安检并转存 COS，返回正式 URL；③ 违规图后端返回 400，由调用方 toast 透出。
 *
 * @param tempFilePath 本地临时文件路径（chooseMedia / compressImage 产物）
 * @returns 后端 COS 正式 URL（提交时随 `images` 数组上送）
 */
export function uploadUgcImage(tempFilePath: string): Promise<UploadedImage> {
  let result!: Promise<UploadedImage>

  // #ifdef MP-WEIXIN
  result = (async () => {
    const { url: fileId } = await uploadFile(tempFilePath)
    if (!fileId) throw new Error('上传失败，请重试')
    return post<UploadResultVO>('/upload/cloud-image', { fileId })
  })()
  // #endif

  // #ifndef MP-WEIXIN
  // 其他端（H5 等）无 UGC 上传链路（管理端 /admin/upload/image 不接受学生 JWT）
  result = uploadFile(tempFilePath)
  // #endif

  return result
}
