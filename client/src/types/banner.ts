/**
 * 首页顶部轮播图（`GET /banners`，后端 `BannerVO` 恰为 2 字段）。
 *
 * 契约口径：
 * - `imageUrl` 为**可直接渲染的绝对 URL**，素材统一 **16:10**；
 * - `sort_order` / `status` 由服务端过滤与排序，**不出参**；端上按返回顺序渲染，不排序；
 * - v1 **无跳转字段** —— Banner 上无点击交互。
 */
export interface Banner {
  /** Banner ID（轮播项稳定 key） */
  id: number
  /** 轮播图绝对 URL（16:10 素材；空串 = 待补素材 → 端上占位空态） */
  imageUrl: string
}
