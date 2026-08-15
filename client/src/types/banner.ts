/**
 * Banner 类型（project_spec.md §3.x.2）
 * 跳转类型使用 target_type 枚举：DISH / URL / NONE（task-12.10 已移除 ACTIVITY）
 */
type BannerTargetType = 'DISH' | 'URL' | 'NONE'

export interface BannerItem {
  id?: number
  title: string
  subtitle?: string
  image: string
  targetType: BannerTargetType
  /** targetType=DISH 时填目标 ID */
  targetId?: number
  /** targetType=URL 时填外链 */
  targetUrl?: string
}
