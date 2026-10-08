/**
 * UGC 配图的数据模型与**提交时**的上传编排（评价 / 意见反馈 / 菜品纠错三处共用）。
 *
 * <p><b>上传只在「用户确认提交」后发生</b>；提交前图片只存在于**本地临时文件**：
 * <ol>
 *   <li><b>选图</b>（{@link ImagePicker.startPick}）：本地压缩 + 尺寸/大小校验 → 追加本地临时路径。
 *       <b>不写微信云存储、不调任何后端端点</b> ⇒ 用户挑完不提交，云存储桶零写入、无孤儿对象；</li>
 *   <li><b>提交</b>（本模块 {@link submitUgcImages}）：逐张「`wx.cloud.uploadFile` 拿 fileID →
 *       `POST /upload/cloud-image`（内容安检 + 转存 COS）」，拿到正式 URL 后随表单上送。</li>
 * </ol>
 *
 * <p><b>为何不把上云放在选图时</b>：`wx.cloud.uploadFile` 一旦调用即在云存储桶留下对象，
 * 用户放弃提交就成为永不引用的垃圾（云存储侧的 `ugc/` 生命周期规则只作兜底）——
 * 既浪费空间又占配额。改为「提交才上云」后，桶内对象与「已提交内容」一一对应。
 *
 * <p>本模块是纯逻辑（无组件、无响应式），可被单测完整覆盖；平台调用由 `api/upload.ts` 承担。
 */
import { uploadCloudImage, uploadToCloud } from '@/api/upload'
import { errorMessage } from '@/utils/error'

/**
 * 一张 UGC 配图的完整状态。
 *
 * - `preview`：缩略图 / 预览地址 —— 选图后是**本地临时路径**，提交成功后是正式 URL；
 * - `fileId`：微信云存储 fileID，**提交时才产生**（送后端机审）；预填项与已成功项为空；
 * - `url`：后端正式 URL；只有它非空，才表示这张图已经过机审并落到 COS。
 */
export interface UgcImageItem {
  preview: string
  fileId: string
  url: string
}

/** 由「已有正式 URL 列表」构造配图项（菜品纠错带入的菜品原图、已提交成功项） */
export function toUgcItems(urls: readonly string[]): UgcImageItem[] {
  return urls.filter(Boolean).map((url) => ({ preview: url, fileId: '', url }))
}

/** 取配图项中的正式 URL 列表（提交载荷 / diff 比对用；未过机审的项产出空串占位） */
export function ugcItemUrls(items: readonly UgcImageItem[]): string[] {
  return items.map((it) => it.url)
}

/**
 * 逐张定位的失败文案：把后端 message 拼成「第 N 张图片……」。
 *
 * 后端文案本身以「图片」开头（如「图片包含违规内容，无法上传」），直接前置会读成
 * 「第 2 张图片图片包含违规内容」，故先剥掉开头一个「图片」。
 */
export function ugcIndexErrorMessage(index: number, raw: string): string {
  const detail = (raw || '').trim()
  const body = detail.startsWith('图片') ? detail.slice(2) : detail
  return `第 ${index + 1} 张图片${body}`
}

/**
 * 提交时把配图项解析为正式 URL 列表：**逐张「上云存储 → 送机审 → 转存 COS」**，任一张失败即中止。
 *
 * 这是图片**第一次离开本地**的时机：用户点提交前，云存储桶与 COS 都没有它的任何痕迹。
 * 已成功的项就地回填 `url`，用户点重试时只传剩余未成功的张，不会重复上传 / 机审已过的图。
 *
 * 失败时抛 `Error`，message 已含「第 N 张图片」定位信息，由调用方 toast 直透。
 */
export async function submitUgcImages(items: UgcImageItem[]): Promise<string[]> {
  const urls: string[] = []
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    if (item.url) {
      urls.push(item.url)
      continue
    }
    try {
      // 提交链路内的第一次云写入：本地临时路径 → 微信云存储 fileID（用户未提交时桶内零占用）
      if (!item.fileId) {
        const { fileId } = await uploadToCloud(item.preview)
        if (!fileId) throw new Error('上传失败，请重试')
        item.fileId = fileId
      }
      const res = await uploadCloudImage(item.fileId)
      if (!res.url) throw new Error('上传失败，请重试')
      item.url = res.url
      item.preview = res.url
      item.fileId = ''
      urls.push(res.url)
    } catch (e) {
      throw new Error(ugcIndexErrorMessage(i, errorMessage(e, '上传失败，请重试')))
    }
  }
  return urls
}