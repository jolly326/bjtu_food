import { post } from './http'
import { WX_CLOUD_ENV } from './config'
import { getWxApi } from '@/utils/device'
import type { UploadResultVO } from './shared'

/** 微信云存储上传回调的最小类型（平台回调透传，仅取 fileID） */
interface WxCloudUploadResult {
  fileID?: string
}
interface WxCloudCallError {
  errMsg?: string
}

/** 上传结果只透出 `url`：`relativeUrl` 仅本地磁盘降级链路有值，端上展示一律用 `url`（类型仍来自契约） */
type UploadedImage = Pick<UploadResultVO, 'url'>

/** 上传超时 15s：二进制文件比 JSON 请求慢，在请求 12s 基础上放宽，避免上传 promise 永久挂起 */
const UPLOAD_TIMEOUT_MS = 15000

/**
 * 上传本地图片至微信云存储，返回 `cloud://` 文件 ID。
 * - cloud:// 无需 uploadFile 合法域名白名单，`<image>` 原生支持直接显示；后端原样存储该 ID，
 *   展示链路经 getImageUrl 透传（见 utils/image.ts）。
 * - 其他端（H5 等）无可用上传链路（管理端 `/admin/upload/image` 不接受学生 JWT）→ 显式失败，
 *   避免发出必然 403 的请求。
 */
function uploadFile(tempFilePath: string): Promise<{ url: string }> {
  let result!: Promise<{ url: string }>

  // #ifdef MP-WEIXIN
  result = new Promise<{ url: string }>((resolve, reject) => {
    const wxApi = getWxApi()
    if (!wxApi || !wxApi.cloud) {
      reject(new Error('当前环境不支持 wx.cloud'))
      return
    }
    // 超时 / 失败及时 reject 并只 settle 一次，防止调用方的上传中守卫位永久锁死
    let settled = false
    const done = (fn: () => void) => {
      if (!settled) {
        settled = true
        fn()
      }
    }
    const timeoutTimer = setTimeout(() => {
      done(() => {
        if (task && typeof task.abort === 'function') task.abort()
        reject(new Error('上传超时，请重试'))
      })
    }, UPLOAD_TIMEOUT_MS)
    const clearTimer = () => { clearTimeout(timeoutTimer) }
    // cloudPath：images/YYYY-MM-DD/<时间戳>-<随机数><原扩展名>，避免同名覆盖
    const ext = (tempFilePath.match(/\.\w+$/) || ['.jpg'])[0]
    const stamp = Date.now()
    const rand = Math.random().toString(36).slice(2, 8)
    const cloudPath = `images/${new Date().toISOString().slice(0, 10)}/${stamp}-${rand}${ext}`
    const task = wxApi.cloud.uploadFile({
      config: { env: WX_CLOUD_ENV },
      cloudPath,
      filePath: tempFilePath,
      // 平台例外：微信回调透传，仅取其 fileID
      success: (r: WxCloudUploadResult) => { clearTimer(); done(() => resolve({ url: r.fileID ?? '' })) },
      fail: (err: WxCloudCallError) => { clearTimer(); done(() => reject(new Error(err.errMsg || '上传失败，请重试'))) },
    })
  })
  // #endif

  // #ifndef MP-WEIXIN
  result = Promise.reject(new Error('当前运行端不支持图片上传'))
  // #endif

  return result
}

/**
 * UGC 配图上传（评价 / 反馈共用）。
 *
 * 流程：① `wx.cloud.uploadFile` 传至微信云存储取 fileID；② `POST /upload/cloud-image`
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
  result = uploadFile(tempFilePath)
  // #endif

  return result
}
