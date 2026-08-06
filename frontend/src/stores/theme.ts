import { ref } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'bjtu-food:theme'

/**
 * 主题（深色模式）状态：手动开关 + 本地持久化。
 * 切换原理：页面根节点挂 .theme-dark class → 命中 App.vue 全局 .theme-dark 选择器的
 * 深色 token 覆盖（CSS 变量继承），全站即时切换，无需逐组件改动。
 */
export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(false)

  /** App 启动时从本地存储恢复主题 */
  function init() {
    try {
      const saved = uni.getStorageSync(STORAGE_KEY)
      isDark.value = saved === 'dark'
    } catch {
      /* 存储不可用时保持浅色默认 */
    }
  }

  function setDark(v: boolean) {
    isDark.value = v
    try {
      uni.setStorageSync(STORAGE_KEY, v ? 'dark' : 'light')
    } catch {
      /* ignore */
    }
  }

  function toggle() {
    setDark(!isDark.value)
  }

  return { isDark, init, setDark, toggle }
})
