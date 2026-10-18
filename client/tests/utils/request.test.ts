import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 请求层：静默重登判据必须是**业务码 401**，不得用文案匹配。
 * 口径见 docs/api/README.md「业务状态码」与 docs/secur/S-01。
 */

type RequestOptions = {
  url: string
  method: string
  header?: Record<string, string>
  success: (res: { statusCode: number; data: unknown }) => void
  fail: (err: unknown) => void
}

const storage = new Map<string, string>()

const uniMock = {
  getStorageSync: (key: string) => storage.get(key) ?? '',
  setStorageSync: (key: string, value: string) => void storage.set(key, value),
  removeStorageSync: (key: string) => void storage.delete(key),
  login: vi.fn(() => Promise.resolve({ code: 'wx-code' })),
  request: vi.fn<(options: RequestOptions) => void>(),
}

vi.stubGlobal('uni', uniMock)

import { request } from '@/utils/request'

function envelope(code: number, data: unknown, message = '') {
  return { code, message, data }
}

beforeEach(() => {
  storage.clear()
  uniMock.login.mockClear()
  uniMock.request.mockReset()
})

describe('请求层：401 静默重登', () => {
  it('业务码 401 ⇒ 重登一次并重试原请求', async () => {
    storage.set('student_token', 'stale')
    let canteenCalls = 0

    uniMock.request.mockImplementation((options) => {
      if (options.url.endsWith('/auth/wechat-login')) {
        options.success({ statusCode: 200, data: envelope(200, { token: 'fresh' }) })
        return
      }
      canteenCalls += 1
      options.success({
        statusCode: 200,
        data: canteenCalls === 1 ? envelope(401, null, '未登录或凭证已失效') : envelope(200, []),
      })
    })

    await expect(request('GET', '/canteens')).resolves.toEqual([])
    expect(uniMock.login).toHaveBeenCalledTimes(1)
    expect(storage.get('student_token')).toBe('fresh')
    expect(canteenCalls).toBe(2)
  })

  it('非 401 业务失败 ⇒ 文案含「凭证」也不重登', async () => {
    storage.set('student_token', 'ok')

    uniMock.request.mockImplementation((options) => {
      options.success({ statusCode: 200, data: envelope(400, null, '服务端未配置微信小程序凭证') })
    })

    await expect(request('GET', '/canteens')).rejects.toThrow('服务端未配置微信小程序凭证')
    expect(uniMock.login).not.toHaveBeenCalled()
  })
})

describe('请求层：429 退避', () => {
  it('限频错误带 retryAfter，且不重登、不重试', async () => {
    storage.set('student_token', 'ok')
    let calls = 0

    uniMock.request.mockImplementation((options) => {
      calls += 1
      options.success({ statusCode: 200, data: envelope(429, null, '操作过于频繁，请 45 秒后再试') })
    })

    const failure = (await request('GET', '/canteens').catch((error: unknown) => error)) as {
      code: number
      retryAfter?: number
    }
    expect(failure.code).toBe(429)
    expect(failure.retryAfter).toBe(45)
    expect(calls).toBe(1)
    expect(uniMock.login).not.toHaveBeenCalled()
  })
})
