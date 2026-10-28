/**
 * 图片 URL 工具：地图磁贴缩略图（docs/ui/client/美食地图.md §5）。
 *
 * 开关由构建期 env 决定：`VITE_USE_COS_THUMB=true` 时给 COS / CDN 图片追加
 * `imageMogr2/thumbnail/...` 处理参数；缺省（false）原图直出，确保任何环境
 * 都不会因处理参数不可用而加载失败。
 */
const THUMB_ENABLED =
  ((import.meta.env.VITE_USE_COS_THUMB as string | undefined) ?? '').trim() === 'true'

/** 按需派生缩略图 URL；未开启或无 URL 时原样返回。 */
export function thumbUrl(url: string | null | undefined, width = 400): string {
  if (!url) return ''
  if (!THUMB_ENABLED || url.includes('imageMogr2')) return url
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}imageMogr2/thumbnail/${width}x`
}
