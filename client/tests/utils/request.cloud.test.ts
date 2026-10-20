import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 请求层：配置 `VITE_TCB_ENV` 后必须走云调用（`wx.cloud.callContainer`），
 * 不再触发 `uni.request` —— 真机免域名白名单（口径见 docs/api/README.md §服务基路径）。
 */

const storage = new Map<string, string>()

const uniMock = {
  getStorageSync: (key: string) => storage.get(key) ?? '',
  setStorageSync: (key: string, value: string) => void storage.set(key, value),
  removeStorageSync: (key: string) => void storage.delete(key),
  login: vi.fn(() => Promise.resolve({ code: 'wx-code' })),
  request: vi.fn(),
}

const cloudMock = {
  init: vi.fn(),
  callContainer: vi.fn(),
}

vi.stubGlobal('uni', uniMock)
vi.stubGlobal('wx', { cloud: cloudMock })

// 构建期内联的环境变量：云调用模式
vi.stubEnv('VITE_TCB_ENV', 'prod-demo')
vi.stubEnv('VITE_TCB_SERVICE', 'bjtu-food')

const { request, transportMode } = await import('@/utils/request')

beforeEach(() => {
  storage.clear()
  vi.clearAllMocks()
})

describe('请求层：云调用通道', () => {
  it('配置环境 ID ⇒ 走 callContainer，不碰 uni.request', async () => {
    storage.set('student_token', 'ok')
    cloudMock.callContainer.mockImplementation((options: { success: (r: unknown) => void }) => {
      options.success({ statusCode: 200, data: { code: 200, message: '', data: [] } })
    })

    await expect(request('GET', '/canteens')).resolves.toEqual([])
    expect(transportMode).toBe('cloud')
    expect(cloudMock.init).toHaveBeenCalledTimes(1)
    expect(cloudMock.callContainer).toHaveBeenCalledWith(
      expect.objectContaining({
        path: '/canteens',
        method: 'GET',
        header: expect.objectContaining({
          Authorization: 'Bearer ok',
          'X-WX-SERVICE': 'bjtu-food',
        }),
      }),
    )
    expect(uniMock.request).not.toHaveBeenCalled()
  })

  it('云调用失败 ⇒ 抛网络异常，不做业务重试', async () => {
    storage.set('student_token', 'ok')
    cloudMock.callContainer.mockImplementation((options: { fail: (e: unknown) => void }) => {
      options.fail({ errMsg: 'callContainer:fail env not found' })
    })

    await expect(request('GET', '/canteens')).rejects.toThrow('网络异常')
    expect(cloudMock.callContainer).toHaveBeenCalledTimes(1)
  })
})
