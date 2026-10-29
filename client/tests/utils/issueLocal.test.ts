import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 纠错提交本地去重记忆：提交前查重、提交后登记（端上软提示依据）。
 * 口径见 docs/func/client/B-轻量反馈/B2-菜品纠错.md。
 */

const storage = new Map<string, string>()

const uniMock = {
  getStorageSync: (key: string) => storage.get(key) ?? '',
  setStorageSync: (key: string, value: string) => void storage.set(key, value),
}

vi.stubGlobal('uni', uniMock)

import { hasSubmitted, markSubmitted } from '@/utils/issueLocal'

beforeEach(() => {
  storage.clear()
})

describe('issueLocal：本地提交记忆', () => {
  it('未登记的组合视为未提交', () => {
    expect(hasSubmitted(1, 2)).toBe(false)
  })

  it('登记后按 菜+选项 维度命中', () => {
    markSubmitted(1, 2)
    expect(hasSubmitted(1, 2)).toBe(true)
    // 同菜不同选项、同选项不同菜，互不影响
    expect(hasSubmitted(1, 3)).toBe(false)
    expect(hasSubmitted(9, 2)).toBe(false)
  })

  it('多条登记互不覆盖，storage 为字符串数组', () => {
    markSubmitted(1, 2)
    markSubmitted(3, 4)
    expect(hasSubmitted(1, 2)).toBe(true)
    expect(hasSubmitted(3, 4)).toBe(true)
    expect(storage.get('dish-issue-submitted')).toEqual(['1:2', '3:4'])
  })
})
