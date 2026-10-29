/**
 * 顶部 / 底部安全区（口径见 docs/ui/client/公共组件与形态基线.md §1.1）。
 *
 * 自定义导航栏（`navigationStyle: custom`）下画布是全屏的，顶部内容必须避开状态栏与右上角胶囊：
 * - **顶部** = 状态栏高 + 导航栏高：状态栏取 `statusBarHeight`；导航栏高由胶囊（`getMenuButtonBoundingClientRect`）
 *   与状态栏算出 —— 保证右上角不被胶囊压住；
 * - **底部** = 家居条高度（`env(safe-area-inset-bottom)`），由 CSS 承担。
 *
 * 取值在 **px**（与画布手势坐标同单位），窗口尺寸变化时须重新读取。
 */

import { rpxToPx } from './viewport'

/** 底部安全区由 CSS 承担：`padding-bottom: calc(值 + var(--safe-area-inset-bottom))` */
export const CSS_SAFE_AREA_BOTTOM = 'env(safe-area-inset-bottom)'

/** 顶部安全区 = 状态栏 + 胶囊区（px） */
export interface TopSafeArea {
  /** 状态栏高度 */
  statusBarHeight: number
  /** 导航栏高度（胶囊垂直居中所需的高度） */
  navBarHeight: number
  /** 二者之和：画布内容的起始 y */
  total: number
}

/** 取不到胶囊信息时的兜底导航栏高（rpx）——约 2 行图标高，够住大多数胶囊 */
const NAV_BAR_FALLBACK_RPX = 96

/** 状态栏 + 胶囊区高度（px）；接口缺失时回落兜底值，不影响渲染 */
export function topSafeArea(screenWidth: number): TopSafeArea {
  const host = uni as unknown as {
    getWindowInfo?: () => { statusBarHeight?: number }
    getSystemInfoSync?: () => { statusBarHeight?: number }
    getMenuButtonBoundingClientRect?: () => { top?: number; height?: number } | null
  }

  let statusBar = 0
  try {
    statusBar = host.getWindowInfo?.().statusBarHeight ?? host.getSystemInfoSync?.().statusBarHeight ?? 0
  } catch {
    statusBar = 0
  }

  // 导航栏高 =（胶囊底部 − 状态栏）+ 胶囊自身到边界的下留白：这里取「胶囊底 − 状态栏」再补同等留白
  let navBar = rpxToPx(NAV_BAR_FALLBACK_RPX, screenWidth)
  try {
    const rect = host.getMenuButtonBoundingClientRect?.()
    if (rect?.top && rect?.height) {
      navBar = rect.top - statusBar + rect.height + rect.top - statusBar
    }
  } catch {
    // 非小程序环境（单测 / H5）保持兜底值
  }

  return { statusBarHeight: statusBar, navBarHeight: navBar, total: statusBar + navBar }
}
