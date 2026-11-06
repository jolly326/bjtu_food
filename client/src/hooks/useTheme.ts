/**
 * 主题状态 Hook（浅色 / 深色）。
 *
 * 状态为**模块级单例**：全端只有一个主题，页头切换入口与页面根容器读同一份。
 * 首次进入取本地存储；无历史选择时跟随系统主题（读不到系统信息即按浅色）。
 * 切换即写入本地存储，下次进入保持用户选择。
 */
import { computed, ref } from 'vue'

import { NAV, STORAGE } from '@/constants'

export type ThemeMode = 'light' | 'dark'

/** 页面根容器在深色主题下挂的类名，与 `theme/generated-colors.css` 的取值块对应 */
const DARK_CLASS = 'theme-dark'

/** 自定义导航下状态栏文字随主题取反（平台 API 只接受 hex；backgroundColor 必填） */
function applyStatusBar(next: ThemeMode): void {
  try {
    if (typeof uni.setNavigationBarColor !== 'function') return
    const dark = next === 'dark'
    uni.setNavigationBarColor({
      frontColor: dark ? NAV.STATUS_BAR_FRONT_DARK : NAV.STATUS_BAR_FRONT_LIGHT,
      backgroundColor: dark ? NAV.STATUS_BAR_BG_DARK : NAV.STATUS_BAR_BG_LIGHT,
    })
  } catch {
    // 平台不支持时只影响状态栏配色，不阻断主题切换
  }
}

function readSavedMode(): ThemeMode | null {
  try {
    const saved = uni.getStorageSync(STORAGE.THEME)
    return saved === 'light' || saved === 'dark' ? saved : null
  } catch {
    return null
  }
}

function readSystemMode(): ThemeMode {
  try {
    // 优先新接口（getSystemInfoSync 在基础库 3.x 已弃用，调用会刷弃用告警）
    const baseApi = (uni as unknown as { getAppBaseInfo?: () => { theme?: string } })
      .getAppBaseInfo
    const info = baseApi ? baseApi.call(uni) : (uni.getSystemInfoSync() as { theme?: string })
    return info.theme === 'dark' ? 'dark' : 'light'
  } catch {
    return 'light'
  }
}

const mode = ref<ThemeMode>(readSavedMode() ?? readSystemMode())

export function useTheme() {
  const isDark = computed(() => mode.value === 'dark')
  /** 绑定到页面根容器：深色下为 `theme-dark`，浅色下为空串 */
  const themeClass = computed(() => (isDark.value ? DARK_CLASS : ''))

  function persist(next: ThemeMode): void {
    try {
      uni.setStorageSync(STORAGE.THEME, next)
    } catch {
      // 存储不可用时主题仅在本次会话生效，不阻断切换
    }
  }

  function setMode(next: ThemeMode): void {
    if (mode.value === next) return
    mode.value = next
    persist(next)
    applyStatusBar(next)
  }

  function toggleMode(): void {
    setMode(isDark.value ? 'light' : 'dark')
  }

  /** 页面就绪后同步平台侧配色（状态栏前景随主题取反） */
  function applyChrome(): void {
    applyStatusBar(mode.value)
  }

  return { isDark, themeClass, toggleMode, applyChrome }
}
