import { describe, it, expect, vi, beforeEach } from 'vitest'

const uploadCloudImage = vi.fn()
const uploadToCloud = vi.fn()
vi.mock('@/api/upload', () => ({
  uploadCloudImage: (id: string) => uploadCloudImage(id),
  uploadToCloud: (p: string) => uploadToCloud(p),
}))

import { toUgcItems, ugcItemUrls, ugcIndexErrorMessage, submitUgcImages } from '@/components/ugcImage'
import type { UgcImageItem } from '@/components/ugcImage'

function pending(index: number): UgcImageItem {
  return { preview: '/tmp/x.jpg', fileId: 'cloud://file-' + index, url: '' }
}

/** 选图阶段的真实形态：**只有本地临时路径**，fileId 尚未产生 */
function localPending(index: number): UgcImageItem {
  return { preview: '/tmp/local-' + index + '.jpg', fileId: '', url: '' }
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

describe('submitUgcImages · 提交时才上云 + 逐张机审', () => {
  beforeEach(() => {
    uploadCloudImage.mockReset()
    uploadToCloud.mockReset()
  })

  it('选图阶段的本地项：先上传云存储拿 fileID，再送机审（顺序即「提交才上云」）', async () => {
    uploadToCloud.mockResolvedValueOnce({ fileId: 'cloud://new-1' })
    uploadCloudImage.mockResolvedValueOnce({ url: 'https://cdn/1.jpg' })
    const items = [localPending(1)]

    const urls = await submitUgcImages(items)

    expect(uploadToCloud.mock.calls.map((x) => x[0])).toEqual(['/tmp/local-1.jpg'])
    expect(uploadCloudImage.mock.calls.map((x) => x[0])).toEqual(['cloud://new-1'])
    expect(urls).toEqual(['https://cdn/1.jpg'])
    expect(items[0]).toEqual({ preview: 'https://cdn/1.jpg', fileId: '', url: 'https://cdn/1.jpg' })
  })

  it('上云失败按「第 N 张图片」定位并中止，不调机审端点', async () => {
    uploadToCloud.mockRejectedValueOnce(new Error('上传超时，请重试'))

    await expect(submitUgcImages([localPending(2)])).rejects.toThrow('第 1 张图片上传超时，请重试')
    expect(uploadCloudImage).not.toHaveBeenCalled()
  })

  it('已有 fileId 的项不重复上云（提交中途重试只补未完成那张）', async () => {
    uploadCloudImage.mockResolvedValueOnce({ url: 'https://cdn/2.jpg' })

    await submitUgcImages([pending(2)])

    expect(uploadToCloud).not.toHaveBeenCalled()
    expect(uploadCloudImage).toHaveBeenCalledTimes(1)
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