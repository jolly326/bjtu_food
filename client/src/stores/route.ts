import { ref } from 'vue'

/** 当前主区 tab key（home/community/profile），空串表示处于二级页 */
export const activeTab = ref<string>('home')
/**
 * 自定义 TabBar 是否可见（仅三主根页可见，navigateTo 二级页隐藏）。
 * 默认 true：首页是小程序入口页，启动即应显示底部菜单栏；
 * 二级页（navigateTo）由拦截器按目标 URL 显式隐藏，主区切换由各自 onShow 锚定。
 */
export const tabVisible = ref<boolean>(true)

// 主根页 route（不带前导斜杠）-> tab key
export const routeMap: Record<string, string> = {
  'pages/home/index': 'home',
  'pages/community/index': 'community',
  'pages/profile/index': 'profile',
}

/** 直接设定 TabBar 显隐与高亮（主根页 onShow 锚定，最稳定，不依赖页面栈时序） */
export function setTab(key: string | null) {
  activeTab.value = key || ''
  tabVisible.value = key !== null
}

/** 主根页 onShow 调用：确保该页显示并高亮 */
export function showTab(key: string) {
  setTab(key)
}

/**
 * 由导航拦截器在跳转发起时（invoke）按目标 URL 直接判定显隐——
 * 目标 URL 已知，无需 getCurrentPages，故最可靠：主根页显示、其余（二级页）隐藏。
 */
export function ensureTabForUrl(url?: string) {
  const path = (url || '').replace(/^\/?/, '').split('?')[0]
  const key = routeMap[path]
  setTab(key || null)
}

/**
 * 依据当前页面栈重算 TabBar 显隐与高亮（用于 navigateBack 等无法预知目标的场景）。
 * 必须在页面已就绪后调用（如导航 complete、App.onShow）：getCurrentPages() 返回非响应式数组，
 * 不能放进 computed，需在这些时机主动调用。
 */
export function syncRoute() {
  const pages = (getCurrentPages?.() ?? []) as any[]
  // 页面栈尚未就绪（如 TabBar 初始挂载）：保留当前显隐，避免误隐藏（首页启动即应可见）
  if (pages.length === 0) return
  const cur = pages[pages.length - 1]
  const raw = (cur?.route as string) || ''
  const path = raw.replace(/^\/+/, '')
  const key = routeMap[path]
  setTab(key || null)
}
