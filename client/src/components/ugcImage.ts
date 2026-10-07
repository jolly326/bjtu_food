/**
 * UGC 配图「两段式上传」的数据模型与提交编排（评价 / 意见反馈 / 菜品纠错三处共用）。
 *
 * 为什么两段式：后端 `POST /upload/cloud-image` 做内容安检（机审）并把图转存到 COS。
 * 若在选图时就调该端点，用户挑完图不提交就离开，COS 里就会留下永不被引用的孤儿对象。故拆成两段：
 * 选图时只调 `wx.cloud.uploadFile` 拿 fileID（该端点只收云存储 fileID，这一步不能省），预览走本地临时路径、
 * 不调任何后端端点；提交时才逐张调该端点（机审 + 转存 COS），拿到正式 URL 后随表单上送。
 *
 * 未提交的 fileID 由运维侧清理：微信云存储控制台对 `ugc/` 前缀配 7 天生命周期规则自动过期删除（零代码）。
 *
 * 本模块是纯逻辑（无组件、无响应式），可被单测完整覆盖；平台调用由 `api/upload.ts` 承担。
 */
import { uploadCloudImage } from '@/api/upload'
import { errorMessage } from '@/utils/error'

/**
 * 一张 UGC 配图的完整状态。
 *
 * - `preview`：缩略图 / 预览地址 —— 选图后是本地临时路径，提交成功后是正式 URL；
 * - `fileId`：微信云存储 fileID（提交时送后端机审）；预填项与已成功项为空；
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
 * 提交时把配图项解析为正式 URL 列表（逐张送机审，任一张失败即中止）。
 *
 * 已成功的项就地回填 `url`，用户点重试时只传剩余未成功的张，不会重复机审已过的图。
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