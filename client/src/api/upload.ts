import { uploadFile, post } from './http'
import { WX_CLOUD_ENV } from './config'

/**
 * 头像图片上传（**仅限头像等本人非公开用途**）。
 *
 * ⚠️ 合规红线（spec §5.a）：本函数**不做任何内容安检**，且命名曾为泛化的 `uploadImage`，
 * 极易被新页面误用于 UGC 公开内容而绕过微信内容安检。
 *   → **禁止用于 UGC 公开内容**（评价配图 / 反馈配图等一切他人可见的图）。
 *   → UGC 必须走 `uploadUgcImage`（云存储 fileID → POST /upload/images，含 imgSecCheck 安检 + COS 转存）。
 * 当前唯一合法调用点：pages/profile（本人头像）。
 *
 * - 微信小程序端：微信云存储 wx.cloud.uploadFile，返回 cloud:// 文件 ID
 * - 其他端（H5 等）：POST /api/upload/image (multipart/form-data)，返回后端绝对 URL
 *
 * @param tempFilePath 本地临时文件路径（从 uni.chooseImage 获取）
 * @returns 上传后可直接存储/展示的图片地址（cloud:// 或 http(s)）
 */
export async function uploadAvatarImage(tempFilePath: string): Promise<string> {
  const result = await uploadFile(tempFilePath)
  return result.url
}

/** UGC 图片上传超时：对齐 http.ts UPLOAD_TIMEOUT_MS（15s），防止上传 promise 永久挂起 */
const UGC_UPLOAD_TIMEOUT_MS = 15000

/**
 * UGC 配图上传（评价 / 反馈共用）。
 *
 * 流程（后端契约 2026-09，POST /api/upload/images）：
 * 1. wx.cloud.uploadFile 上传到微信云存储（cloudPath: ugc/{yyyyMMdd}/{时间戳+随机}.jpg）拿 fileID；
 * 2. POST /upload/images { fileId }，由后端做内容安检并转存 COS，返回正式 URL；
 * 3. 违规图片后端返回 400「图片包含违规内容，无法上传」，经 http 层统一抛 message，
 *    由调用方（ImagePicker）toast 透出。
 *
 * 平台例外：wx 句柄为微信运行时对象，未纳入项目 TS 类型（同 http.ts 说明）。
 *
 * @param tempFilePath 本地临时文件路径（chooseMedia/compressImage 产物）
 * @returns 后端 COS 正式 URL（提交评价/反馈时随 images 数组上送）
 */
export function uploadUgcImage(tempFilePath: string): Promise<{ url: string }> {
  let result!: Promise<{ url: string }>

  // ===== 微信小程序端：云存储 fileID → 后端安检转存 COS =====
  // #ifdef MP-WEIXIN
  result = (async () => {
    // 平台例外：wx 句柄为微信运行时对象，未纳入项目 TS 类型（同 http.ts 说明）
    const wxApi: any = (globalThis as any).wx
    if (!wxApi || !wxApi.cloud) {
      throw new Error('当前环境不支持 wx.cloud')
    }
    const fileId = await new Promise<string>((resolve, reject) => {
      // MP-003 同款 settled 模式：超时/失败及时 reject，防调用方上传中状态锁死
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
      }, UGC_UPLOAD_TIMEOUT_MS)
      const clearTimer = () => { clearTimeout(timeoutTimer) }
      // cloudPath：ugc/YYYYMMDD/<时间戳>-<随机数>.jpg（本地时区日期，避免同名覆盖）
      const now = new Date()
      const ymd = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`
      const rand = Math.random().toString(36).slice(2, 8)
      const cloudPath = `ugc/${ymd}/${Date.now()}-${rand}.jpg`
      const task = wxApi.cloud.uploadFile({
        config: { env: WX_CLOUD_ENV },
        cloudPath,
        filePath: tempFilePath,
        // 平台例外：微信回调透传，仅取其 fileID
        success: (r: any) => { clearTimer(); done(() => resolve(String(r?.fileID || ''))) },
        fail: (err: any) => { clearTimer(); done(() => reject(new Error(err?.errMsg || '上传失败，请重试'))) },
      })
    })
    if (!fileId) throw new Error('上传失败，请重试')
    // 后端安检 + 转存 COS：违规返回 400「图片包含违规内容，无法上传」（http 层抛 message）
    return post<{ url: string }>('/upload/images', { fileId })
  })()
  // #endif

  // ===== 其他端（H5 等）：无 wx.cloud，回退 multipart 直传后端（/upload/image，返回后端 URL） =====
  // #ifndef MP-WEIXIN
  result = uploadFile(tempFilePath)
  // #endif

  return result
}
