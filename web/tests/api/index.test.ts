import { describe, expect, it, vi } from 'vitest'

vi.mock('@/api/request', () => ({
  http: {
    get: vi.fn(() => Promise.resolve({ records: [], total: 0 })),
    post: vi.fn(() => Promise.resolve({})),
    put: vi.fn(() => Promise.resolve({})),
    patch: vi.fn(() => Promise.resolve({})),
    delete: vi.fn(() => Promise.resolve(null)),
  },
  upload: vi.fn(),
  readToken: vi.fn(() => ''),
  writeToken: vi.fn(),
  clearToken: vi.fn(),
}))

import { canteenApi, dishApi, issueApi, optionApi, recordApi, stallApi } from '@/api'
import { http } from '@/api/request'

describe('运营端 API 封装', () => {
  it('canteen list 带分页参数', async () => {
    await canteenApi.list(2, 20)
    expect(http.get).toHaveBeenCalledWith('/admin/canteens?page=2&size=20')
  })

  it('stall list 带食堂筛选', async () => {
    await stallApi.list({ canteenId: 3, page: 1 })
    expect(http.get).toHaveBeenCalledWith('/admin/stalls?canteenId=3&page=1')
  })

  it('dish list 带三重筛选', async () => {
    await dishApi.list({ stallId: 5, status: 'on', page: 1 })
    expect(http.get).toHaveBeenCalledWith('/admin/dishes?stallId=5&status=on&page=1')
  })

  it('record list 的 reaction=none 原样传递', async () => {
    await recordApi.list({ reaction: 'none', page: 1 })
    expect(http.get).toHaveBeenCalledWith('/admin/dish-records?reaction=none&page=1')
  })

  it('issue list 默认传 handled=false', async () => {
    await issueApi.list({ handled: false })
    expect(http.get).toHaveBeenCalledWith('/admin/dish-issues?handled=false')
  })

  it('setHandled 走 PATCH', async () => {
    await issueApi.setHandled(8, true)
    expect(http.patch).toHaveBeenCalledWith('/admin/dish-issues/8', { handled: true })
  })

  it('option remove 指向字典端点', async () => {
    await optionApi.remove(4)
    expect(http.delete).toHaveBeenCalledWith('/admin/dish-issue-options/4')
  })
})
