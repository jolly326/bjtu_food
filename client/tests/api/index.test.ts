import { beforeEach, describe, expect, it, vi } from 'vitest'

import { dishApi, issueApi, recordApi } from '@/api'
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
vi.spyOn(http, 'patch').mockResolvedValue({} as never)
vi.spyOn(http, 'delete').mockResolvedValue(null as never)

beforeEach(() => {
  // 只清调用记录；spy 实现在模块加载时已设置，clear 不移除
  vi.clearAllMocks()
})

describe('recordApi', () => {
  it('want posts to /my/records with dishId', async () => {
    await recordApi.want(7)
    expect(http.post).toHaveBeenCalledWith('/my/records', { dishId: 7 })
  })

  it('react patches the record with 5/3/1', async () => {
    await recordApi.react(3, 5)
    expect(http.patch).toHaveBeenCalledWith('/my/records/3', { reaction: 5 })
  })

  it('cancel deletes the record', async () => {
    await recordApi.cancel(3)
    expect(http.delete).toHaveBeenCalledWith('/my/records/3')
  })
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
  it('builds query with canteenId and pageSize', async () => {
    await dishApi.list({ canteenId: 4, pageSize: 20 })
    expect(http.get).toHaveBeenCalledWith('/dishes?canteenId=4&pageSize=20')
  })

  it('omits query when no params', async () => {
    await dishApi.list()
    expect(http.get).toHaveBeenCalledWith('/dishes')
  })
})

