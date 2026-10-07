import { describe, it, expect, beforeEach } from 'vitest'
import { resolveImageUrl } from '@/utils/image'

/**
 * `resolveImageUrl` 是管理端图片地址的**单一真源**：后端返回相对路径时，
 * 裸 `<img src="相对路径">` 会打到 web 域名根路径而不是 API 基址 ⇒ 缩略图裂图。
 */
describe('resolveImageUrl · 空值与伪协议', () => {
  it('空值一律返回空串（调用方据此走占位）', () => {
    expect(resolveImageUrl(null)).toBe('')
    expect(resolveImageUrl(undefined)).toBe('')
    expect(resolveImageUrl('')).toBe('')
  })

  it('data / blob 伪协议原样返回（不可拼基址）', () => {
    expect(resolveImageUrl('data:image/png;base64,AAA')).toBe('data:image/png;base64,AAA')
    expect(resolveImageUrl('blob:http://x/1')).toBe('blob:http://x/1')
  })
})

describe('resolveImageUrl · 绝对地址原样', () => {
  it('COS 绝对 URL 原样返回', () => {
    const u = 'https://xxx.cos.ap-beijing.myqcloud.com/images/a.jpg'
    expect(resolveImageUrl(u)).toBe(u)
  })

  it('带查询参数的绝对 URL 原样返回', () => {
    const u = 'https://cdn.example.com/a.jpg?imageMogr2/thumbnail/400x'
    expect(resolveImageUrl(u)).toBe(u)
  })

  it('站点绝对路径 /static/ 原样返回（不由 API 基址承载）', () => {
    expect(resolveImageUrl('/static/logo.png')).toBe('/static/logo.png')
  })
})

describe('resolveImageUrl · 相对路径归一', () => {
  it('相对路径拼 API 基址', () => {
    const out = resolveImageUrl('/images/a.jpg')
    expect(out).not.toBe('/images/a.jpg')
    expect(out.endsWith('/images/a.jpg')).toBe(true)
  })

  it('裸文件名（无前导斜杠）补斜杠后拼基址', () => {
    const out = resolveImageUrl('1791018833128-e0co1c.jpg')
    expect(out.endsWith('/1791018833128-e0co1c.jpg')).toBe(true)
  })

  it('剥离已存在的 /api 前缀，避免出现 /api/api 双重前缀', () => {
    const a = resolveImageUrl('/api/images/a.jpg')
    const b = resolveImageUrl('/images/a.jpg')
    expect(a).toBe(b)
    expect(a.includes('/api/api')).toBe(false)
  })
})