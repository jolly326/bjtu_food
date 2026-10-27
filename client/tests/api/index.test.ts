import { beforeEach, describe, expect, it, vi } from 'vitest'

import { dishApi, issueApi } from '@/api'
import { http } from '@/utils/request'

/**
 * API 层：参数拼装（路径 / body / query）必须与 docs/api/client/* 口径一致。
 *
 * <p>拦截点在 `http` 方法本身（spy 调用记录），不触达真实请求层与 `uni` 全局——
 * 实测 `vi.mock('@/utils/request')` 在本项目 vitest 配置下不生效（真实模块仍被加载，
 * 其 readToken 触碰 `uni` 报 ReferenceError），故不走模块 mock；
 * 与 tests/utils/request.test.ts 的「stub 全局」思路同源。
 */

vi.spyOn(http, 'get').mockResolvedValue([] as never)
vi.spyOn(http, 'post').mockResolvedValue({} as never)

beforeEach(() => {
  // 只清调用记录；spy 实现在模块加载时已设置，clear 不移除
  vi.clearAllMocks()
})

describe('issueApi', () => {
  it('submit posts optionId under the dish', async () => {
    await issueApi.submit(9, 2)
    expect(http.post).toHaveBeenCalledWith('/dishes/9/issues', { optionId: 2 })
  })

  it('options reads the dictionary', async () => {
    await issueApi.options()
    expect(http.get).toHaveBeenCalledWith('/dish-issue-options')
  })
})

describe('dishApi', () => {
  it('list reads the full on-sale list', async () => {
    await dishApi.list()
    expect(http.get).toHaveBeenCalledWith('/dishes')
  })

  it('like toggles under the dish', async () => {
    await dishApi.like(9)
    expect(http.post).toHaveBeenCalledWith('/dishes/9/likes')
  })
})
