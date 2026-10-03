import { get, post, put, del } from './http'
import type { BannerAdminVO, BannerSaveReq, OnOffStatus, SortItemsReq } from '@/types/common'

/** A5 Banner 列表（按 `order` 升序；**含已停用**；不分页、不筛选） */
export function listBanners(): Promise<BannerAdminVO[]> {
  return get<BannerAdminVO[]>('/admin/banners')
}

/** A5 新增（默认**启用**） */
export function createBanner(req: BannerSaveReq): Promise<BannerAdminVO> {
  return post<BannerAdminVO>('/admin/banners', req)
}

/** A5 编辑（换图；可编辑字段整体替换） */
export function updateBanner(id: number, req: BannerSaveReq): Promise<null> {
  return put<null>(`/admin/banners/${id}`, req)
}

/** A5 启停（**只改 `status`**，显式传目标状态；非 toggle） */
export function updateBannerStatus(id: number, status: OnOffStatus): Promise<null> {
  return put<null>(`/admin/banners/${id}/status`, { status })
}

/** A5 排序（拖拽后**整体提交全量行**，边界见 web/README 的「拖拽排序提交」通用约定） */
export function sortBanners(req: SortItemsReq): Promise<null> {
  return put<null>('/admin/banners/sort', req)
}

/** A5 删除 */
export function deleteBanner(id: number): Promise<null> {
  return del<null>(`/admin/banners/${id}`)
}
