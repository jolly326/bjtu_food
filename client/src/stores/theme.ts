import { ref } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'bjtu-food:theme'

/**
 * 主题（深色模式）状态：手动开关 + 本地持久化 + 跟随系统。
 * 切换原理：页面根节点挂 .theme-dark class → 命中 App.vue 全局 .theme-dark 选择器的
 * 深色 token 覆盖（CSS 变量继承），全站即时切换，无需逐组件改动。
 *
 * 跟随系统（C-09）：未手动设置过时，默认取系统主题（uni.getAppBaseInfo().theme），
 * 并在支持的环境监听系统主题变化（wx.onThemeChange）自动同步。
 */
export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(false)
  /** 是否已手动设置过（决定启动时是否跟随系统主题） */
  const hasManualSetting = ref(false)

  /** 读取系统主题（'dark' | 'light'），不支持时返回 'light' */
  function getSystemTheme(): 'dark' | 'light' {
    try {
      // 微信基础库建议用 getAppBaseInfo（getSystemInfoSync 已弃用）；uni-app 跨端同名 API
      // @ts-ignore uni-app 类型声明滞后
      const info = uni.getAppBaseInfo?.() || {}
      return (info as { theme?: string }).theme === 'dark' ? 'dark' : 'light'
    } catch {
      return 'light'
    }
  }

  /** App 启动时恢复主题：手动设置优先，否则跟随系统 */
  function init() {
    try {
      const saved = uni.getStorageSync(STORAGE_KEY)
      if (saved === 'dark' || saved === 'light') {
        isDark.value = saved === 'dark'
        hasManualSetting.value = true
      } else {
        isDark.value = getSystemTheme() === 'dark'
      }
    } catch {
      /* 存储不可用时跟随系统（默认浅色） */
      isDark.value = getSystemTheme() === 'dark'
    }
    // 跟随系统主题变化（微信小程序等支持环境）
    try {
      // @ts-ignore wx.onThemeChange 仅微信小程序
      if (typeof wx !== 'undefined' && wx.onThemeChange) {
        // @ts-ignore
        wx.onThemeChange((res: { theme: string }) => {
          if (!hasManualSetting.value) isDark.value = res.theme === 'dark'
        })
      }
    } catch {
      /* ignore */
    }
  }

  function setDark(v: boolean) {
    isDark.value = v
    hasManualSetting.value = true
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
