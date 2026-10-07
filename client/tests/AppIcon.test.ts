// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppIcon from '@/components/AppIcon.vue'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import IdentityCard from '@/components/IdentityCard.vue'
import { COLOR_MAP } from '@/theme/tokens'
import { installUni } from './stubs'

/** 解出 data-uri 里的 SVG 文本，便于断言几何与取色 */
function svgOf(src?: string): string {
  return decodeURIComponent(String(src).replace('data:image/svg+xml,', ''))
}

describe('AppIcon · 统一图标组件', () => {
  it('渲染为 SVG data-uri（微信小程序无原生 <svg>，靠 <image> 承载）', () => {
    const w = mount(AppIcon, { props: { name: 'search', size: 40, color: COLOR_MAP['text-tertiary'] } })
    const img = w.find('image')
    expect(img.attributes('src')).toMatch(/^data:image\/svg\+xml,/)
    expect(svgOf(img.attributes('src'))).toContain('viewBox="0 0 48 48"')
  })

  it('size 支持带单位字符串（rpx 由调用方显式书写）', () => {
    const w = mount(AppIcon, { props: { name: 'star', size: '20px' } })
    expect(w.find('.app-icon').attributes('style')).toContain('20px')
  })

  it('线性图标统一 2 描边 + 圆角端点（IconPark 官方观感）', () => {
    const w = mount(AppIcon, { props: { name: 'check', color: COLOR_MAP.error } })
    const svg = svgOf(w.find('image').attributes('src'))
    expect(svg).toContain('stroke-width="2"')
    expect(svg).toContain('stroke-linecap="round"')
  })

  it('color 直传实色（data-uri 是独立文档，不解析 var()）', () => {
    const w = mount(AppIcon, { props: { name: 'close', color: COLOR_MAP['text-white'] } })
    expect(svgOf(w.find('image').attributes('src'))).toContain(`stroke="${COLOR_MAP['text-white']}"`)
  })

  it('未知图标名回退 empty 占位而非渲染中断', () => {
    const w = mount(AppIcon, { props: { name: 'not-exist-icon' } })
    const fallback = mount(AppIcon, { props: { name: 'empty' } })
    expect(w.find('image').attributes('src')).toBe(fallback.find('image').attributes('src'))
  })
})

describe('ImagePlaceholder · 占位变体', () => {
  it('默认铺灰底', () => {
    const w = mount(ImagePlaceholder)
    expect(w.find('.img-ph').classes()).not.toContain('is-bare')
  })

  it('bare 变体只去底色、保留居中图标（Banner 这类自带底的区块用）', () => {
    const w = mount(ImagePlaceholder, { props: { bare: true } })
    expect(w.find('.img-ph').classes()).toContain('is-bare')
    expect(w.find('image').exists()).toBe(true)
  })
})

describe('IdentityCard · 头像渲染', () => {
  const base = { nickname: '食客 1234', bindEmail: '1234@bjtu.edu.cn', verified: true }

  it('有头像时渲染图片（定尺盒在页面模板内，不依赖组件宿主节点）', () => {
    const w = mount(IdentityCard, { props: { ...base, avatar: 'https://cdn.example.com/a.jpg' } })
    expect(w.find('.identity-card-avatar').exists()).toBe(true)
    expect(w.find('.identity-card-avatar-img').attributes('src')).toContain('cdn.example.com')
  })

  it('图片加载失败回退人形占位（禁裂图）', async () => {
    const w = mount(IdentityCard, { props: { ...base, avatar: 'https://cdn.example.com/a.jpg' } })
    await w.find('.identity-card-avatar-img').trigger('error')
    expect(w.find('.identity-card-avatar-img').exists()).toBe(false)
    expect(w.find('.img-ph').exists()).toBe(true)
  })

  it('换头像后破图态复位（新图必须能显示）', async () => {
    const w = mount(IdentityCard, { props: { ...base, avatar: 'https://cdn.example.com/a.jpg' } })
    await w.find('.identity-card-avatar-img').trigger('error')
    await w.setProps({ avatar: 'https://cdn.example.com/b.jpg' })
    expect(w.find('.identity-card-avatar-img').exists()).toBe(true)
  })

  it('无头像直接渲染人形占位', () => {
    installUni()
    const w = mount(IdentityCard, { props: { ...base, avatar: '' } })
    expect(w.find('.identity-card-avatar-img').exists()).toBe(false)
    expect(w.find('.img-ph').exists()).toBe(true)
  })
})