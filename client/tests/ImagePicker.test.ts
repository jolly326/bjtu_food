// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

/** mock 上传模块：选图阶段只允许调 uploadToCloud，uploadCloudImage 一旦被调即视为违规（提前送审） */
const uploadToCloud = vi.fn()
const uploadCloudImage = vi.fn()
vi.mock('@/api/upload', () => ({
  uploadToCloud: (p: string) => uploadToCloud(p),
  uploadCloudImage: (id: string) => uploadCloudImage(id),
}))

import ImagePicker from '@/components/ImagePicker.vue'
import { installUni, uniCalls } from './stubs'

/** 微信端平台句柄最小桩：选图 / 压缩 / 读尺寸 / 读大小 / 云存储上传 */
function installWx() {
  const g = globalThis as Record<string, unknown>
  g.wx = {
    chooseMedia: (o: { success: (r: unknown) => void }) =>
      o.success({ tempFiles: [{ tempFilePath: '/tmp/raw.jpg', size: 100 * 1024 }] }),
    compressImage: (o: { src: string; success: (r: unknown) => void }) =>
      o.success({ tempFilePath: o.src }),
    getFileSystemManager: () => ({ getFileInfo: (o: { success: (r: unknown) => void }) => o.success({ size: 50 * 1024 }) }),
  }
}

describe('ImagePicker · 两段式上传（选图阶段）', () => {
  beforeEach(() => {
    installUni()
    installWx()
    uploadToCloud.mockReset()
    uploadCloudImage.mockReset()
    uploadToCloud.mockResolvedValue({ fileId: 'cloud://file-1' })
  })

  it('选图只落微信云存储拿 fileID，不碰后端机审端点', async () => {
    const w = mount(ImagePicker, { props: { modelValue: [] } })
    ;(w.vm as unknown as { startPick: (s: 'album') => Promise<void> }).startPick('album')
    await new Promise((r) => setTimeout(r, 0))
    await new Promise((r) => setTimeout(r, 0))
    expect(uploadToCloud).toHaveBeenCalledTimes(1)
    expect(uploadCloudImage).not.toHaveBeenCalled()
  })

  it('选图后模型项 = 本地预览路径 + 云存储 fileID，正式 URL 为空', async () => {
    const w = mount(ImagePicker, { props: { modelValue: [] } })
    ;(w.vm as unknown as { startPick: (s: 'album') => Promise<void> }).startPick('album')
    await new Promise((r) => setTimeout(r, 0))
    await new Promise((r) => setTimeout(r, 0))
    const emitted = w.emitted('update:modelValue')
    const last = emitted?.[emitted.length - 1]?.[0] as Array<{ preview: string; fileId: string; url: string }>
    expect(last).toEqual([{ preview: '/tmp/raw.jpg', fileId: 'cloud://file-1', url: '' }])
    // 缩略图走本地预览路径（未过机审也能显示）
    expect(w.find('.ip-thumb').attributes('src')).toBe('/tmp/raw.jpg')
  })

  it('云存储上传失败逐张 toast 跳过，不中断其余', async () => {
    uploadToCloud.mockRejectedValueOnce(new Error('上传超时，请重试'))
    const w = mount(ImagePicker, { props: { modelValue: [] } })
    ;(w.vm as unknown as { startPick: (s: 'album') => Promise<void> }).startPick('album')
    await new Promise((r) => setTimeout(r, 0))
    await new Promise((r) => setTimeout(r, 0))
    expect(uniCalls.showToast.map((x) => x.title)).toContain('上传超时，请重试')
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })
})