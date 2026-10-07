import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { installDomStub, pressEscape } from './support/dom-stub'
import { useOverlayLayer, overlayDepth, __resetOverlayState } from '@/composables/useOverlayLayer'

/**
 * 浮层可叠加（详情抽屉上再开处置弹窗、抽屉里再开大图预览）。本用例锁定两条口径：
 *  1. **ESC 只由栈顶响应** ⇒ 一次 ESC 只关一层；
 *  2. **背景滚动锁引用计数 + 原样还原** ⇒ 关内层不破坏外层的锁，退出最外层还原页面原始值。
 *
 * ⚠️ 这两条是 P0 缺陷（抽屉开着背景能滚、一次 ESC 关掉两层）的回归护栏。
 */
let uninstall: () => void

beforeEach(() => {
  uninstall = installDomStub()
  __resetOverlayState()
})

afterEach(() => {
  __resetOverlayState()
  uninstall()
})

describe('useOverlayLayer · ESC 只关栈顶', () => {
  it('两层叠加：一次 ESC 只关闭栈顶那一层', () => {
    const closed: string[] = []
    const drawer = useOverlayLayer('drawer', null, () => closed.push('drawer'))
    const modal = useOverlayLayer('modal', null, () => closed.push('modal'))

    drawer.attach()
    modal.attach()
    expect(overlayDepth()).toBe(2)

    pressEscape()
    expect(closed).toEqual(['modal'])

    // 模拟父层响应关闭：ESC → close → open=false → watch 触发 detach（栈顶出栈）
    modal.detach()
    pressEscape()
    expect(closed).toEqual(['modal', 'drawer'])
  })

  it('栈顶层关闭后，下层重新成为栈顶', () => {
    const closed: string[] = []
    const drawer = useOverlayLayer('drawer', null, () => closed.push('drawer'))
    const modal = useOverlayLayer('modal', null, () => closed.push('modal'))
    drawer.attach()
    modal.attach()

    modal.detach()
    pressEscape()
    expect(closed).toEqual(['drawer'])
  })
})

describe('useOverlayLayer · 背景滚动锁', () => {
  it('打开即锁背景滚动', () => {
    const l = useOverlayLayer('drawer', null, () => undefined)
    l.attach()
    expect(document.body.style.overflow).toBe('hidden')
    l.detach()
  })

  it('内层关闭不破坏外层的锁', () => {
    document.body.style.overflow = 'auto'
    const outer = useOverlayLayer('drawer', null, () => undefined)
    const inner = useOverlayLayer('modal', null, () => undefined)
    outer.attach()
    inner.attach()

    inner.detach()
    expect(document.body.style.overflow).toBe('hidden')

    outer.detach()
    expect(document.body.style.overflow).toBe('auto')
  })

  it('计数归零后还原为空串（原始值本就为空时）', () => {
    document.body.style.overflow = ''
    const l = useOverlayLayer('drawer', null, () => undefined)
    l.attach()
    l.detach()
    expect(document.body.style.overflow).toBe('')
  })

  it('重复 detach 不会让计数变负、也不会误解锁', () => {
    const a = useOverlayLayer('drawer', null, () => undefined)
    const b = useOverlayLayer('modal', null, () => undefined)
    a.attach()
    b.attach()
    a.detach()
    a.detach()
    expect(document.body.style.overflow).toBe('hidden')
    b.detach()
    expect(document.body.style.overflow).toBe('')
  })
})