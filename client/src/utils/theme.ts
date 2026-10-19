/**
 * 明暗主题（口径见 docs/ui/client/设计变量.md §1.2 · 公共组件与形态基线.md §1.1）。
 *
 * 全站双主题：语义色分浅色 / 深色两组同键声明；页面根视图挂 `data-theme` 切换。
 * 默认浅色；用户切换写入 storage 持久化，App 启动时读取挂载（不跟随系统）。
 * 切换入口暂仅风物页 Header（太阳 / 月亮按钮），作用于全部三个 tab 页。
 */
import { ref } from 'vue'

export type ThemeMode = 'light' | 'dark'

/** storage 持久化键 */
const STORAGE_KEY = 'theme'

/** 模块级单例：三个 tab 页共享同一主题态 */
const theme = ref<ThemeMode>('light')

export function useTheme() {
  return theme
}

/** 切换浅色 / 深色并持久化 */
export function toggleTheme(): void {
  theme.value = theme.value === 'light' ? 'dark' : 'light'
  uni.setStorageSync(STORAGE_KEY, theme.value)
}

/** 启动时读取持久化选择（无记录保持浅色） */
export function initTheme(): void {
  const saved = uni.getStorageSync(STORAGE_KEY)
  if (saved === 'dark' || saved === 'light') {
    theme.value = saved
  }
}
