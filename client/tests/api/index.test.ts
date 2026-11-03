import { beforeEach, describe, expect, it, vi } from 'vitest'

import { dishApi, feedbackApi } from '@/api'
import { http } from '@/utils/request'

/**
 * API 层：参数拼装（路径 / body）必须与 docs/api/client/* 口径一致。
 *
 * <p>拦截点在 `http` 方法本身（spy 调用记录），不触达真实请求层与 `uni` 全局。
 */

vi.spyOn(http, 'get').mockResolvedValue([] as never)
vi.spyOn(http, 'post').mockResolvedValue({} as never)

beforeEach(() => {
  vi.clearAllMocks()
})

describe('dishApi', () => {
  it('list reads the full on-sale list', async () => {
    await dishApi.list()
    expect(http.get).toHaveBeenCalledWith('/dishes')
  })

  it('recommend posts the query', async () => {
    await dishApi.recommend('想吃辣')
    expect(http.post).toHaveBeenCalledWith('/dishes/recommend', { query: '想吃辣' })
  })
})

describe('feedbackApi', () => {
  it('submit posts dish feedback with dishId', async () => {
    await feedbackApi.submit({ type: 'dish', dishId: 9, content: '东区更贵' })
    expect(http.post).toHaveBeenCalledWith('/feedback', {
      type: 'dish',
      dishId: 9,
      content: '东区更贵',
    })
  })

  it('product feedback omits dishId', async () => {
    await feedbackApi.submit({ type: 'product', content: '希望支持语音输入' })
    expect(http.post).toHaveBeenCalledWith('/feedback', {
      type: 'product',
      content: '希望支持语音输入',
    })
  })
})
