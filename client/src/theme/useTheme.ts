/**
 * 明暗主题状态（口径见 docs/ui/client/设计变量.md §1.2 · 公共组件与形态基线.md §1.1）。
 *
 * 全站双主题：语义色分浅色 / 深色两组同键声明；页面根视图挂 `data-theme` 切换。
 * 默认浅色；用户切换写入 storage 持久化，App 启动时读取挂载（不跟随系统）。
 */
import { ref } from 'vue'

import { STORAGE } from '@/constants'

export type ThemeMode = 'light' | 'dark'

/** 模块级单例：页面共享同一主题态 */
const theme = ref<ThemeMode>('light')

export function useTheme() {
  return theme
}

/** 启动时读取持久化选择（无记录保持浅色） */
export function initTheme(): void {
  const saved = uni.getStorageSync(STORAGE.THEME)
  if (saved === 'dark' || saved === 'light') {
    theme.value = saved
  }
}
