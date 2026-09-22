import type { Banner } from '@/types/banner'
import { get } from './http'
import type { RawRow } from './shared'

/**
 * 首页顶部轮播图（`GET /banners`，公开只读、无请求参数）。
 *
 * 服务端已按 `sort_order` 升序、只返回启用项（`status='on'`），且 `imageUrl` 已是绝对 URL；
 * 端上**严格按返回顺序渲染**，不排序、不写死 URL、不写死张数。
 * 无启用项时返回**空数组**（端上退化为「灰底 + 菜品 icon」空态，与菜品卡图片占位同款，不裂图）。
 */
export async function getBanners(): Promise<Banner[]> {
  const raw = await get<RawRow[]>('/banners')
  return (raw || []).map((item: RawRow) => ({
    id: Number(item.id),
    imageUrl: typeof item.imageUrl === 'string' ? item.imageUrl : '',
  }))
}
