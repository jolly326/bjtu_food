/**
 * 首页顶部轮播图（`GET /banners`，后端 `BannerVO` 恰为 2 字段）
 *
 * 契约口径（2026-09-22，见 docs/feature/client-首页菜品浏览.md I6 / docs/ui/client-首页菜品浏览.md §1）：
 * - `imageUrl` 为**可直接渲染的绝对 URL**（服务端已转绝对地址），素材统一 **16:10**；
 * - `sort_order` / `status` 由服务端过滤与排序，**不出参**；端上按返回顺序渲染、不排序、不写死 URL；
 * - v1 **无跳转字段**（`targetType` / `targetId` / `targetUrl` 均不存在）——Banner 上无点击交互。
 */
export interface Banner {
  /** Banner ID（轮播项稳定 key） */
  id: number
  /** 轮播图绝对 URL（16:10 素材；可为空串 = 待补素材 → 端上占位空态） */
  imageUrl: string
}
