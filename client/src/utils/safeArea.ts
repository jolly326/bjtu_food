/**
 * 顶部安全区度量（口径见 docs/ui/client/公共组件与形态基线.md §1.1）。
 *
 * 自定义导航栏（`navigationStyle: custom`）下，内容须避开：
 *   ① 状态栏 —— 刘海屏 / 挖孔屏的顶部不可用区（`statusBarHeight` 已含）；
 *   ② 微信右上角**胶囊按钮**区（`getMenuButtonBoundingClientRect`）。
 *
 * 返回的 `topInset` 即「内容起始位置」：状态栏高度 + 自定义导航栏高度。
 */

export interface NavMetrics {
  /** 状态栏高度（px），已覆盖刘海 / 挖孔 */
  statusBarHeight: number
  /** 自定义导航栏高度（px）—— 与胶囊等高并含上下对称留白 */
  navBarHeight: number
  /** 顶部内容起始 inset（px）= statusBarHeight + navBarHeight */
  topInset: number
}

const FALLBACK_NAV_BAR_HEIGHT = 44

/** 状态栏高度：优先新 API（`getWindowInfo`），旧基础库回退 `getSystemInfoSync` */
function readStatusBarHeight(): number {
  try {
    return uni.getWindowInfo().statusBarHeight ?? 0
  } catch {
    return uni.getSystemInfoSync().statusBarHeight ?? 0
  }
}

/** 窗口宽度（px）：优先新 API，旧基础库回退；统一由本模块兜底 */
export function windowWidth(): number {
  try {
    return uni.getWindowInfo().windowWidth || 375
  } catch {
    return uni.getSystemInfoSync().windowWidth || 375
  }
}

export function navMetrics(): NavMetrics {
  const statusBarHeight = readStatusBarHeight()
  let navBarHeight = FALLBACK_NAV_BAR_HEIGHT
  try {
    const capsule = uni.getMenuButtonBoundingClientRect()
    if (capsule && capsule.height) {
      // 胶囊与状态栏的间隙，上下对称 ⇒ 导航栏高 = 胶囊高 + 上下间隙 × 2
      navBarHeight = capsule.height + (capsule.top - statusBarHeight) * 2
    }
  } catch {
    navBarHeight = FALLBACK_NAV_BAR_HEIGHT
  }
  return { statusBarHeight, navBarHeight, topInset: statusBarHeight + navBarHeight }
}
