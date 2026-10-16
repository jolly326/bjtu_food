import { describe, expect, it, vi } from 'vitest'

vi.mock('@/utils/request', () => ({
  http: {
    get: vi.fn(() => Promise.resolve([])),
    post: vi.fn(() => Promise.resolve({})),
    put: vi.fn(() => Promise.resolve({})),
    patch: vi.fn(() => Promise.resolve({})),
    delete: vi.fn(() => Promise.resolve(null)),
  },
}))

import { dishApi, issueApi, recordApi } from '@/api'
import { http } from '@/utils/request'

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
