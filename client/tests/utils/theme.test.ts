import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 明暗主题：默认浅色；启动时读取持久化选择（不跟随系统）。
 * 口径见 docs/ui/client/设计变量.md §1.2 · 公共组件与形态基线.md §1.1。
 */

const storage = new Map<string, string>()

const uniMock = {
  getStorageSync: (key: string) => storage.get(key) ?? '',
  setStorageSync: (key: string, value: string) => void storage.set(key, value),
}

vi.stubGlobal('uni', uniMock)

import { initTheme, useTheme } from '@/theme'

beforeEach(() => {
  storage.clear()
  storage.set('theme', 'light')
  initTheme()
})

describe('主题：默认与启动读取', () => {
  it('无记录时默认浅色（全新模块实例）', async () => {
    vi.resetModules()
    const fresh = await import('@/theme')
    expect(fresh.useTheme().value).toBe('light')
  })

  it('记录为 dark ⇒ 启动后保持深色', () => {
    storage.set('theme', 'dark')
    initTheme()
    expect(useTheme().value).toBe('dark')
  })

  it('记录为 light ⇒ 启动后保持浅色', () => {
    storage.set('theme', 'dark')
    initTheme()
    storage.set('theme', 'light')
    initTheme()
    expect(useTheme().value).toBe('light')
  })

  it('记录为非法值 ⇒ 不被吸收，保持上一次的有效值', () => {
    storage.set('theme', 'dark')
    initTheme()
    storage.set('theme', 'sepia')
    initTheme()
    expect(useTheme().value).toBe('dark')
  })
})
