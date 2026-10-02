import type { Banner } from '@/types/banner'
import { get } from './http'
import type { BannerVO } from './shared'

/**
 * 首页顶部轮播图（`GET /banners`，公开只读、无请求参数）。
 *
 * 服务端已按 `sort_order` 升序、只返回启用项（`status='on'`），且 `imageUrl` 已是绝对 URL；
 * 端上**严格按返回顺序渲染**，不排序、不写死 URL、不写死张数。
 * 无启用项时返回**空数组**（端上退化为「灰底 + **中性 `empty` 图标**」空态：Banner 是运营位轮播、
 * 容器语义 ≠ 菜品，不得用 `dish` 冒充中性占位；灰底与菜品卡图片占位同款，不裂图）。
 *
 * <p>入参用生成的强类型 {@link BannerVO}。
 */
export async function listBanners(): Promise<Banner[]> {
  const raw = await get<BannerVO[]>('/banners')
  return (raw || []).map((item) => ({
    id: Number(item.id),
    imageUrl: typeof item.imageUrl === 'string' ? item.imageUrl : '',
  }))
}