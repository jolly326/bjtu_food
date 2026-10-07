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
 * - cloud:// 无需 uploadFile 合法域名白名单，`<image>` 原生支持直接显示；
 * - **UGC 配图两段式链路的第一步**：只落微信云存储（fileID 供提交时送后端机审 + 转存 COS），
 *   用户取消提交时不会在 COS 留下孤儿对象；未提交的 fileID 由云存储 `ugc/` 前缀的生命周期规则过期清理。
 */
export function uploadToCloud(tempFilePath: string): Promise<{ fileId: string }> {
  let result!: Promise<{ fileId: string }>

  // #ifdef MP-WEIXIN
  result = new Promise<{ fileId: string }>((resolve, reject) => {
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
    // cloudPath：`ugc/YYYY-MM-DD/<时间戳>-<随机数><原扩展名>`，避免同名覆盖。
    // 前缀 `ugc/` 与云存储控制台的生命周期规则同源（未提交即取消的中间产物由该规则过期清理）。
    const ext = (tempFilePath.match(/\.\w+$/) || ['.jpg'])[0]
    const stamp = Date.now()
    const rand = Math.random().toString(36).slice(2, 8)
    const cloudPath = `ugc/${new Date().toISOString().slice(0, 10)}/${stamp}-${rand}${ext}`
    const task = wxApi.cloud.uploadFile({
      config: { env: WX_CLOUD_ENV },
      cloudPath,
      filePath: tempFilePath,
      // 平台例外：微信回调透传，仅取其 fileID
      success: (r: WxCloudUploadResult) => { clearTimer(); done(() => resolve({ fileId: r.fileID ?? '' })) },
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
 * UGC 配图两段式链路的**第二步**：微信云存储 fileID → 后端内容安检（机审）→ 转存 COS → 返回正式 URL。
 *
 * <p>端点只接受微信云存储 fileID，故选图时必须先落云存储（本步不可省）；但**提交前不调本端点**，
 * 避免用户取消提交时在 COS 产生孤儿对象。
 */
export function uploadCloudImage(fileId: string): Promise<UploadedImage> {
  return post<UploadResultVO>('/upload/cloud-image', { fileId })
}

/**
 * 头像上传（个人信息编辑页，选完即传的单张链路）。
 *
 * <p>与 UGC 配图的差别：头像是**账号资料**、无「多张 + 逐张机审」语义，也不存在「取消表单提交」的中间态
 * （选图后只落本地态、点保存才写库），故保持「选图 → 云存储 → 后端机审转存 COS」一次完成。
 */
export function uploadAvatarImage(tempFilePath: string): Promise<UploadedImage> {
  let result!: Promise<UploadedImage>

  // #ifdef MP-WEIXIN
  result = (async () => {
    const { fileId } = await uploadToCloud(tempFilePath)
    if (!fileId) throw new Error('上传失败，请重试')
    return uploadCloudImage(fileId)
  })()
  // #endif

  // #ifndef MP-WEIXIN
  result = uploadToCloud(tempFilePath).then(({ fileId }) => ({ url: fileId }))
  // #endif

  return result
}