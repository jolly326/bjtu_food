/**
 * 顶部安全区度量（口径见 docs/ui/client/公共组件与形态基线.md §1.1）。
 *
 * 自定义导航栏（`navigationStyle: custom`）下，内容须避开：
 *   ① 状态栏 —— 刘海屏 / 挖孔屏的顶部不可用区（`statusBarHeight` 已含）；
 *   ② 微信右上角**胶囊按钮**区（`getMenuButtonBoundingClientRect`）。
 *
 * 返回的 `topInset` 即「内容起始位置」：状态栏高度 + 自定义导航栏高度。
 *
 * 🔴 性能口径：`windowWidth` / `navMetrics` 在运行期恒定，却会被多个页面与组件反复调用
 * （AppHeader 每次挂载、卡片栈按屏宽换算阈值）。二者均为**跨 JS↔原生桥的同步调用**，
 * 故本模块**只在首次读取并缓存**，后续调用直接命中缓存 —— 行为与逐次读取完全一致。
 */

export interface NavMetrics {
  /** 状态栏高度（px），已覆盖刘海 / 挖孔 */
  statusBarHeight: number
  /** 自定义导航栏高度（px）—— 与胶囊等高并含上下对称留白 */
  navBarHeight: number
  /** 顶部内容起始 inset（px）= statusBarHeight + navBarHeight */
  topInset: number
}

/** 取不到系统信息时的兜底值 */
const FALLBACK_NAV_BAR_HEIGHT = 44
const FALLBACK_WINDOW_WIDTH = 375

interface SystemInfo {
  statusBarHeight: number
  windowWidth: number
}

/** 系统信息缓存（窗口尺寸与状态栏在运行期不变） */
let systemInfo: SystemInfo | null = null
/** 安全区度量缓存 */
let metrics: NavMetrics | null = null

/** 真机读取：优先新 API（`getWindowInfo`），旧基础库回退 `getSystemInfoSync` */
function readSystemInfo(): SystemInfo {
  try {
    const info = uni.getWindowInfo()
    return {
      statusBarHeight: info.statusBarHeight ?? 0,
      windowWidth: info.windowWidth || FALLBACK_WINDOW_WIDTH,
    }
  } catch {
    const info = uni.getSystemInfoSync()
    return {
      statusBarHeight: info.statusBarHeight ?? 0,
      windowWidth: info.windowWidth || FALLBACK_WINDOW_WIDTH,
    }
  }
}

function system(): SystemInfo {
  // 惰性初始化：首次调用才走原生桥
  return (systemInfo ??= readSystemInfo())
}

/** 窗口宽度（px）：统一由本模块兜底 */
export function windowWidth(): number {
  return system().windowWidth
}

function computeNavMetrics(): NavMetrics {
  const { statusBarHeight } = system()
  let navBarHeight = FALLBACK_NAV_BAR_HEIGHT
  try {
    const capsule = uni.getMenuButtonBoundingClientRect()
    if (capsule?.height) {
      // 胶囊与状态栏的间隙上下对称 ⇒ 导航栏高 = 胶囊高 + 上下间隙 × 2
      navBarHeight = capsule.height + (capsule.top - statusBarHeight) * 2
    }
  } catch {
    navBarHeight = FALLBACK_NAV_BAR_HEIGHT
  }
  return { statusBarHeight, navBarHeight, topInset: statusBarHeight + navBarHeight }
}

/** 顶部安全区度量（惰性计算并缓存） */
export function navMetrics(): NavMetrics {
  return (metrics ??= computeNavMetrics())
}
