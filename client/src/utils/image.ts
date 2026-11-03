/**
 * 图片 URL 工具：菜品图缩略图（docs/ui/client/公共组件与形态基线.md §1.9）。
 *
 * 开关 `VITE_USE_COS_THUMB=true` 时给 COS / CDN 图片追加腾讯云数据万象的
 * `imageMogr2/thumbnail/<w>x/quality/80/format/webp` 处理参数（菜品图存于腾讯云 COS，原生支持 imageMogr2）；
 * 按承载区真实像素尺寸派生缩略图，绝不把原图塞进小格。缩略图加载失败时经 `useImageSource` 回落原图、再失败才占位。
 */
const THUMB_ENABLED =
  ((import.meta.env.VITE_USE_COS_THUMB as string | undefined) ?? '').trim() === 'true'

/** 按需派生缩略图 URL（限宽 + 质量 + webp）；未开启或无 URL 时原样返回。 */
export function thumbUrl(url: string | null | undefined, width = 400): string {
  if (!url) return ''
  if (!THUMB_ENABLED || url.includes('imageMogr2')) return url
  const sep = url.includes('?') ? '&' : '?'
  return `${url}${sep}imageMogr2/thumbnail/${width}x/quality/80/format/webp`
}
