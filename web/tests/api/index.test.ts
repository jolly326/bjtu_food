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

import { canteenApi, dishApi, feedbackApi, stallApi } from '@/api'
import { http } from '@/api/request'

describe('运营端 API 封装', () => {
  it('canteen list 带分页参数', async () => {
    await canteenApi.list(2, 20)
    expect(http.get).toHaveBeenCalledWith('/admin/canteens?page=2&pageSize=20')
  })

  it('stall list 带食堂筛选', async () => {
    await stallApi.list({ canteenId: 3, page: 1 })
    expect(http.get).toHaveBeenCalledWith('/admin/stalls?canteenId=3&page=1')
  })

  it('dish list 带食堂 / 档口 / 状态筛选', async () => {
    await dishApi.list({ canteenId: 2, stallId: 5, activate: true, page: 1 })
    expect(http.get).toHaveBeenCalledWith('/admin/dishes?canteenId=2&stallId=5&activate=true&page=1')
  })

  it('feedback list 带类型与处理状态', async () => {
    await feedbackApi.list({ type: 'product', handled: false, page: 1 })
    expect(http.get).toHaveBeenCalledWith('/admin/feedback?type=product&handled=false&page=1')
  })

  it('markHandled 走 PATCH', async () => {
    await feedbackApi.markHandled(8)
    expect(http.patch).toHaveBeenCalledWith('/admin/feedback/8/handled')
  })
})
