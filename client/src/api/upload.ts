import { uploadFile, post } from './http'
import type { UploadResultVO } from './shared'

/**
 * UGC 配图上传结果（**只暴露 `url`**）。
 *
 * <p>服务端出参为 `UploadResultVO{ url, relativeUrl }`，但本函数**只透出 `url`**：
 * `relativeUrl` 仅本地磁盘降级链路返回（COS 链路恒空），
 * 端上展示一律用 `url`（后端已按 `app.public-base-url` 拼好的绝对地址），
 * 故 `relativeUrl` 按「零消费即删」不透出到端上模型——但**类型来源仍是契约**。
 */
type UploadedImage = Pick<UploadResultVO, 'url'>

/**
 * 头像图片上传（**仅限头像等本人非公开用途**）。
 *
 * ⚠️ 合规红线（spec §5.a）：本函数**不做任何内容安检**，仅限头像等本人非公开用途，
 * 禁止用于 UGC 公开内容而绕过微信内容安检。
 *   → **禁止用于 UGC 公开内容**（评价配图 / 反馈配图等一切他人可见的图）。
 *   → UGC 必须走 `uploadUgcImage`（云存储 fileID → POST /upload/cloud-image，含 imgSecCheck 安检 + COS 转存）。
 * 当前唯一合法调用点：pages/profile（本人头像）。
 *
 * - 微信小程序端：微信云存储 wx.cloud.uploadFile，返回 cloud:// 文件 ID
 * - 其他端（H5 等）：无可用上传链路（管理端 /admin/upload/image 不接受学生 JWT）
 *
 * @param tempFilePath 本地临时文件路径（从 uni.chooseImage 获取）
 * @returns 上传后可直接存储/展示的图片地址（cloud:// 或 http(s)）
 */
export async function uploadAvatarImage(tempFilePath: string): Promise<string> {
  const result = await uploadFile(tempFilePath)
  return result.url
}

/**
 * UGC 配图上传（评价 / 反馈共用）。
 *
 * 流程（后端契约 POST /api/upload/cloud-image）：
 * 1. wx.cloud.uploadFile 上传到微信云存储（cloudPath: ugc/{yyyyMMdd}/{时间戳+随机}.jpg）拿 fileID；
 * 2. POST /upload/cloud-image { fileId }，由后端做内容安检并转存 COS，返回正式 URL；
 * 3. 违规图片后端返回 400「图片包含违规内容，无法上传」，经 http 层统一抛 message，
 *    由调用方（ImagePicker）toast 透出。
 *
 * 平台例外：wx 句柄为微信运行时对象，未纳入项目 TS 类型（同 http.ts 说明）。
 *
 * @param tempFilePath 本地临时文件路径（chooseMedia/compressImage 产物）
 * @returns 后端 COS 正式 URL（提交评价/反馈时随 images 数组上送）
 */
export function uploadUgcImage(tempFilePath: string): Promise<UploadedImage> {
  let result!: Promise<UploadedImage>

  // ===== 微信小程序端：复用 http.uploadFile 的云存储上传（含 MP-003 settled 超时守卫）拿到 cloud:// fileID → 后端安检转存 COS =====
  // #ifdef MP-WEIXIN
  result = (async () => {
    // 与 http.uploadFile 同源：微信云存储上传 + settled 超时守卫，避免两处手抄同一套 cloud 上传 + timeout 逻辑（口径漂移风险）
    const { url: fileId } = await uploadFile(tempFilePath)
    if (!fileId) throw new Error('上传失败，请重试')
    // 后端安检 + 转存 COS：违规返回 400「图片包含违规内容，无法上传」（http 层抛 message）
    // 出参用契约 `UploadResultVO`（含 relativeUrl；COS 链路恒空，仅本地磁盘降级链路有值）
    return post<UploadResultVO>('/upload/cloud-image', { fileId })
  })()
  // #endif

  // ===== 其他端（H5 等）：无可用上传链路（管理端 multipart /admin/upload/image 不接受学生 JWT） =====
  // #ifndef MP-WEIXIN
  result = uploadFile(tempFilePath)
  // #endif

  return result
}
