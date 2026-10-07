import { describe, it, expect, vi, beforeEach } from 'vitest'

const uploadCloudImage = vi.fn()
vi.mock('@/api/upload', () => ({ uploadCloudImage: (id: string) => uploadCloudImage(id) }))

import { toUgcItems, ugcItemUrls, ugcIndexErrorMessage, submitUgcImages } from '@/components/ugcImage'
import type { UgcImageItem } from '@/components/ugcImage'

function pending(index: number): UgcImageItem {
  return { preview: '/tmp/x.jpg', fileId: 'cloud://file-' + index, url: '' }
}

describe('toUgcItems / ugcItemUrls', () => {
  it('已有正式 URL 直接成为已过机审项（fileId 为空 ⇒ 提交时跳过）', () => {
    expect(toUgcItems(['https://cdn/a.jpg'])).toEqual([
      { preview: 'https://cdn/a.jpg', fileId: '', url: 'https://cdn/a.jpg' },
    ])
  })

  it('空串不产出配图项', () => {
    expect(toUgcItems(['https://cdn/a.jpg', '', 'https://cdn/b.jpg'])).toHaveLength(2)
  })

  it('未过机审的项产出空串占位（保持下标与顺序）', () => {
    expect(ugcItemUrls([toUgcItems(['https://cdn/a.jpg'])[0], pending(2)])).toEqual(['https://cdn/a.jpg', ''])
  })
})

describe('ugcIndexErrorMessage · 逐张定位文案', () => {
  it('序号从 1 起算，并剥掉后端文案开头的一个「图片」', () => {
    expect(ugcIndexErrorMessage(1, '图片包含违规内容，无法上传')).toBe('第 2 张图片包含违规内容，无法上传')
  })

  it('后端文案不含「图片」前缀时原样拼接', () => {
    expect(ugcIndexErrorMessage(0, '上传超时，请重试')).toBe('第 1 张图片上传超时，请重试')
  })
})

describe('submitUgcImages · 提交时机审', () => {
  beforeEach(() => {
    uploadCloudImage.mockReset()
  })

  it('逐张送审并就地回填正式 URL，返回顺序与选择顺序一致', async () => {
    uploadCloudImage
      .mockResolvedValueOnce({ url: 'https://cdn/1.jpg' })
      .mockResolvedValueOnce({ url: 'https://cdn/2.jpg' })
    const items = [pending(1), pending(2)]
    const urls = await submitUgcImages(items)
    expect(uploadCloudImage.mock.calls.map((x) => x[0])).toEqual(['cloud://file-1', 'cloud://file-2'])
    expect(urls).toEqual(['https://cdn/1.jpg', 'https://cdn/2.jpg'])
    expect(items[0]).toEqual({ preview: 'https://cdn/1.jpg', fileId: '', url: 'https://cdn/1.jpg' })
  })

  it('预填项（已有 URL）不重复送审', async () => {
    uploadCloudImage.mockResolvedValueOnce({ url: 'https://cdn/new.jpg' })
    const items = toUgcItems(['https://cdn/old.jpg']).concat([pending(9)])
    const urls = await submitUgcImages(items)
    expect(uploadCloudImage).toHaveBeenCalledTimes(1)
    expect(urls).toEqual(['https://cdn/old.jpg', 'https://cdn/new.jpg'])
  })

  it('重试只传剩余未成功的张（已成功项已回填）', async () => {
    uploadCloudImage
      .mockResolvedValueOnce({ url: 'https://cdn/1.jpg' })
      .mockRejectedValueOnce(new Error('图片包含违规内容，无法上传'))
    const items = [pending(1), pending(2), pending(3)]
    await expect(submitUgcImages(items)).rejects.toThrow('第 2 张图片包含违规内容，无法上传')
    expect(items[0].url).toBe('https://cdn/1.jpg')
    expect(items[1].url).toBe('')

    uploadCloudImage.mockReset()
    uploadCloudImage
      .mockResolvedValueOnce({ url: 'https://cdn/2.jpg' })
      .mockResolvedValueOnce({ url: 'https://cdn/3.jpg' })
    await expect(submitUgcImages(items)).resolves.toEqual([
      'https://cdn/1.jpg',
      'https://cdn/2.jpg',
      'https://cdn/3.jpg',
    ])
    expect(uploadCloudImage).toHaveBeenCalledTimes(2)
    expect(uploadCloudImage.mock.calls.map((x) => x[0])).toEqual(['cloud://file-2', 'cloud://file-3'])
  })

  it('后端未返回 URL 时按失败处理并给出定位文案', async () => {
    uploadCloudImage.mockResolvedValueOnce({ url: '' })
    await expect(submitUgcImages([pending(1)])).rejects.toThrow('第 1 张图片上传失败，请重试')
  })
})