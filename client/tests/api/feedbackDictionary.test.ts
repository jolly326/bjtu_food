import { describe, it, expect, vi, beforeEach } from 'vitest'
import { listFeedbackOptions, listReportOptions } from '@/api/feedback'

/**
 * 反馈 / 举报选项字典 API 层回归测试。
 *
 * <p>两个字典端点同构（`OptionVO[]`：id + name，仅启用项、id 升序下发）：
 * ① 各自命中**正确路径**（`/feedback-options` / `/report-options`）；
 * ② 逐项兜底收敛为必填模型（契约的 `?` 只是 OpenAPI 保守表达），非法项剔除；
 * ③ 非数组响应安全降级为空数组（由调用方渲染失败态）。
 */

vi.mock('@/api/http', () => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}))

import { get } from '@/api/http'

const getMock = vi.mocked(get)

beforeEach(() => {
  getMock.mockReset()
})

describe('选项字典 API（feedback-options / report-options）', () => {
  it('listFeedbackOptions 请求 GET /feedback-options 并收敛为 {id,name} 列表', async () => {
    getMock.mockResolvedValueOnce([
      { id: 1, name: '程序功能Bug' },
      { id: 2, name: '产品功能建议' },
    ])
    const rows = await listFeedbackOptions()
    expect(getMock).toHaveBeenCalledWith('/feedback-options')
    expect(rows).toEqual([
      { id: 1, name: '程序功能Bug' },
      { id: 2, name: '产品功能建议' },
    ])
  })

  it('listReportOptions 请求 GET /report-options（与反馈字典同构）', async () => {
    getMock.mockResolvedValueOnce([{ id: 1, name: '垃圾广告' }])
    const rows = await listReportOptions()
    expect(getMock).toHaveBeenCalledWith('/report-options')
    expect(rows).toEqual([{ id: 1, name: '垃圾广告' }])
  })

  it('缺 id / 空名称的脏项剔除，不进渲染列表', async () => {
    getMock.mockResolvedValueOnce([
      { id: 0, name: '无ID项' },
      { name: '缺ID项' },
      { id: 3, name: '' },
      { id: 4, name: '有效项' },
    ])
    const rows = await listFeedbackOptions()
    expect(rows).toEqual([{ id: 4, name: '有效项' }])
  })

  it('响应非数组（异常壳）安全降级为空数组，不抛错', async () => {
    getMock.mockResolvedValueOnce(undefined as unknown as Array<{ id: number; name: string }>)
    await expect(listReportOptions()).resolves.toEqual([])
  })
})
