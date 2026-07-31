/**
 * Banner 接口模块（project_spec.md §3.x.5：GET /canteens/banners）
 *
 * Banner 跳转统一以 targetType 枚举（DISH/URL/NONE）为准，
 * 页面层按 targetType 分发，不再区分旧 type 字段。
 */
import { get } from './http'
import type { BannerItem } from '@/types/banner'

export async function getBanners(): Promise<BannerItem[]> {
  return get<BannerItem[]>('/canteens/banners')
}
