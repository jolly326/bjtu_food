// @vitest-environment happy-dom
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

/** mock 上传模块：**选图阶段两个上传接口都不许被调**（图片只在本地），否则即视为「未提交就上云」 */
const uploadToCloud = vi.fn()
const uploadCloudImage = vi.fn()
vi.mock('@/api/upload', () => ({
  uploadToCloud: (p: string) => uploadToCloud(p),
  uploadCloudImage: (id: string) => uploadCloudImage(id),
}))

import ImagePicker from '@/components/ImagePicker.vue'
import { installUni, uniCalls } from './stubs'

/** 微信端平台句柄最小桩：选图 / 压缩 / 读尺寸 / 读大小 */
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

/** 跑完 startPick 的异步链（选图 → 压缩 → 校验） */
async function pickOnce(w: ReturnType<typeof mount>, source: 'album' | 'camera' = 'album') {
  ;(w.vm as unknown as { startPick: (s: 'album' | 'camera') => Promise<void> }).startPick(source)
  await new Promise((r) => setTimeout(r, 0))
  await new Promise((r) => setTimeout(r, 0))
}

describe('ImagePicker · 选图阶段零上云（提交时才上传）', () => {
  beforeEach(() => {
    installUni()
    installWx()
    uploadToCloud.mockReset()
    uploadCloudImage.mockReset()
  })

  it('选图只落本地临时路径：两个上传接口都不调', async () => {
    const w = mount(ImagePicker, { props: { modelValue: [] } })
    await pickOnce(w)
    expect(uploadToCloud).not.toHaveBeenCalled()
    expect(uploadCloudImage).not.toHaveBeenCalled()
  })

  it('选图后模型项 = 本地预览路径，fileId 与正式 URL 均为空', async () => {
    const w = mount(ImagePicker, { props: { modelValue: [] } })
    await pickOnce(w)
    const emitted = w.emitted('update:modelValue')
    const last = emitted?.[emitted.length - 1]?.[0] as Array<{ preview: string; fileId: string; url: string }>
    expect(last).toEqual([{ preview: '/tmp/raw.jpg', fileId: '', url: '' }])
    // 缩略图走本地预览路径（未过机审也能显示）
    expect(w.find('.ip-thumb').attributes('src')).toBe('/tmp/raw.jpg')
  })

  it('删除已选同样只在本地移除，不触发任何上传', async () => {
    const w = mount(ImagePicker, { props: { modelValue: [{ preview: '/tmp/raw.jpg', fileId: '', url: '' }] } })
    await w.find('.ip-remove').trigger('tap')
    const emitted = w.emitted('update:modelValue')
    expect(emitted?.[emitted.length - 1]?.[0]).toEqual([])
    expect(uploadToCloud).not.toHaveBeenCalled()
  })

  it('超限图片逐张 toast 跳过，不中断其余', async () => {
    const g = globalThis as Record<string, unknown>
    // 读大小返回 5MB ⇒ 超过 1MB 上限，本张被跳过
    g.wx = {
      chooseMedia: (o: { success: (r: unknown) => void }) =>
        o.success({ tempFiles: [{ tempFilePath: '/tmp/big.jpg', size: 5 * 1024 * 1024 }] }),
      compressImage: (o: { src: string; success: (r: unknown) => void }) =>
        o.success({ tempFilePath: o.src }),
      getFileSystemManager: () => ({ getFileInfo: (o: { success: (r: unknown) => void }) => o.success({ size: 5 * 1024 * 1024 }) }),
    }
    const w = mount(ImagePicker, { props: { modelValue: [] } })
    await pickOnce(w)
    expect(uniCalls.showToast.length).toBeGreaterThan(0)
    expect(w.emitted('update:modelValue')).toBeUndefined()
    expect(uploadToCloud).not.toHaveBeenCalled()
  })

  it('默认上限 3 张：已选 3 张时不再展示添加格（改为 n/n 计数）', () => {
    const w = mount(ImagePicker, {
      props: {
        modelValue: [
          { preview: '/tmp/1.jpg', fileId: '', url: '' },
          { preview: '/tmp/2.jpg', fileId: '', url: '' },
          { preview: '/tmp/3.jpg', fileId: '', url: '' },
        ],
      },
    })
    expect(w.find('.ip-count-text').text()).toBe('3/3')
    expect(w.find('.ip-add').exists()).toBe(false)
  })
})