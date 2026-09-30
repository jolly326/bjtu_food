import { get, post, put, del } from './http'
import type { BannerAdminVO, BannerSaveReq } from '@/types/common'

/** B-07 首页 Banner：不分页。 */
export function listBanners(): Promise<BannerAdminVO[]> {
  return get<BannerAdminVO[]>('/admin/banners')
}

export function createBanner(req: BannerSaveReq): Promise<BannerAdminVO> {
  return post<BannerAdminVO>('/admin/banners', req)
}

export function updateBanner(id: number, req: BannerSaveReq): Promise<BannerAdminVO> {
  return put<BannerAdminVO>(`/admin/banners/${id}`, req)
}

export function toggleBannerStatus(id: number): Promise<BannerAdminVO> {
  return put<BannerAdminVO>(`/admin/banners/${id}/status`)
}

export function deleteBanner(id: number): Promise<null> {
  return del<null>(`/admin/banners/${id}`)
}
