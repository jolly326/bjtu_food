// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

/**
 * 渲染测试基建自检（先于一切组件测试存在）。
 *
 * <p>存在的意义：把「能否在 node 下挂载小程序组件」这个**基建前提**变成可执行断言。
 * 若日后升级 happy-dom / @vue/test-utils 或调整 vitest 配置导致挂载失效，
 * 首个失败的就是这个文件，而不是某个业务测试里的莫名报错。
 */
describe('渲染测试基建自检', () => {
  it('能挂载并渲染 DOM 元素', () => {
    const C = defineComponent({ render: () => h('div', { class: 'x' }, 'hi') })
    const w = mount(C)
    expect(w.find('.x').text()).toBe('hi')
  })

  it('小程序标签（view / text）可被当自定义元素正常挂载与查询', () => {
    const C = defineComponent({
      render: () => h('view', { class: 'card' }, [h('text', { class: 't' }, '红烧肉')]),
    })
    const w = mount(C)
    expect(w.find('.card').exists()).toBe(true)
    expect(w.find('.t').text()).toBe('红烧肉')
  })

  it('响应式状态更新后 DOM 同步', async () => {
    const C = defineComponent({
      data: () => ({ n: 0 }),
      render() {
        return h('view', { class: 'v', onClick: () => (this.n = 1) }, String(this.n))
      },
    })
    const w = mount(C)
    expect(w.find('.v').text()).toBe('0')
    await w.find('.v').trigger('click')
    expect(w.find('.v').text()).toBe('1')
  })

  it('props 驱动重渲染（父级为唯一真源的模式前提）', async () => {
    const Child = defineComponent({
      props: { name: { type: String, default: '' } },
      render() {
        return h('text', { class: 'n' }, this.name)
      },
    })
    const w = mount(Child, { props: { name: '甲' } })
    expect(w.find('.n').text()).toBe('甲')
    await w.setProps({ name: '乙' })
    expect(w.find('.n').text()).toBe('乙')
  })
})