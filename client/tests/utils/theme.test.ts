import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 明暗主题：默认浅色；切换即持久化；启动时读取持久化选择（不跟随系统）。
 * 口径见 docs/ui/client/设计变量.md §1.2 · 公共组件与形态基线.md §1.1。
 */

const storage = new Map<string, string>()

const uniMock = {
  getStorageSync: (key: string) => storage.get(key) ?? '',
  setStorageSync: (key: string, value: string) => void storage.set(key, value),
}

vi.stubGlobal('uni', uniMock)

import { initTheme, isDark, toggleTheme, useTheme } from '@/utils/theme'

beforeEach(() => {
  storage.clear()
  initTheme()
})

describe('主题：默认与切换', () => {
  it('无记录时默认浅色', () => {
    expect(isDark()).toBe(false)
    expect(useTheme().value).toBe('light')
  })

  it('切换在浅色 / 深色间往返，并写入 storage', () => {
    toggleTheme()
    expect(isDark()).toBe(true)
    expect(storage.get('theme')).toBe('dark')

    toggleTheme()
    expect(isDark()).toBe(false)
    expect(storage.get('theme')).toBe('light')
  })
})

describe('主题：启动读取持久化', () => {
  it('记录为 dark ⇒ 启动后保持深色', () => {
    storage.set('theme', 'dark')
    initTheme()
    expect(isDark()).toBe(true)
  })

  it('记录为 light ⇒ 启动后保持浅色', () => {
    storage.set('theme', 'dark')
    initTheme()
    toggleTheme() // 先离开 dark，再以 light 记录重启
    storage.set('theme', 'light')
    initTheme()
    expect(isDark()).toBe(false)
  })

  it('记录为非法值 ⇒ 回落浅色', () => {
    storage.set('theme', 'sepia')
    initTheme()
    expect(isDark()).toBe(false)
  })
})
