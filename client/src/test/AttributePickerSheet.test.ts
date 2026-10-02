// @vitest-environment happy-dom
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import AttributePickerSheet from '../pages/correction/AttributePickerSheet.vue'
import { installUni, inputEvent } from './stubs'

/**
 * `AttributePickerSheet` 渲染回归测试。
 *
 * <p>该弹层是**草稿语义**最重的地方，也是 B-8 拆分的风险点（键盘抬升 + 焦点回收）。
 * 锁死四件事：
 * ① **草稿不外泄**：未确认的勾选绝不回抛（关闭即丢弃）；
 * ② single 点项即选即关；点当前项 = 无改动但仍关闭；
 * ③ 搜索框阈值按**总数**判（打字时不闪没）；
 * ④ 自定义值去重、不参与搜索过滤。
 */

/** 造 N 个候选 */
function cands(n: number): string[] {
  return Array.from({ length: n }, (_, i) => `候选${i + 1}`)
}

function mountSheet(over: Record<string, unknown> = {}) {
  return mount(AttributePickerSheet, {
    props: {
      visible: true,
      name: '口味',
      valueType: 'multi',
      selected: [],
      candidates: ['辣', '麻', '甜'],
      ...over,
    },
    global: {
      stubs: {
        // BaseSheet 只做遮罩 / 标题 / 关闭上抛，用 stub 隔离其 uni 依赖
        BaseSheet: {
          name: 'BaseSheetStub',
          props: ['visible', 'title'],
          emits: ['close'],
          template: '<view class="base-sheet-stub"><slot /></view>',
        },
        IconSvg: true,
        TagChip: {
          // ⚠️ 真实事件名是 `pick`（不是 `tap`）：模板里是 `@pick="onPick(v)"`。
          // 写成 `tap` 则 emit 无人监听，表现为「点了没反应」而组件本身无错。
          props: ['label', 'selected', 'variant', 'checkable', 'selectedStyle'],
          emits: ['pick'],
          template: '<view class="chip-stub" @click="$emit(\'pick\')">{{ label }}</view>',
        },
      },
    },
  })
}

beforeEach(() => {
  installUni()
})

describe('AttributePickerSheet · 草稿不外泄', () => {
  it('多选勾选后不立即回抛（须点确认）', async () => {
    const w = mountSheet({ valueType: 'multi', candidates: ['辣', '麻'] })
    await w.findAll('.chip-stub')[0].trigger('click')
    // 关键：草稿阶段的勾选不得污染主表单
    expect(w.emitted('update')).toBeFalsy()
    expect(w.emitted('close')).toBeFalsy()
  })

  it('点确认才回抛草稿并关闭', async () => {
    const w = mountSheet({ valueType: 'multi', candidates: ['辣', '麻'] })
    await w.findAll('.chip-stub')[0].trigger('click')
    await w.find('.aps-confirm').trigger('tap')
    expect(w.emitted('update')?.[0]).toEqual([['辣']])
    expect(w.emitted('close')).toHaveLength(1)
  })

  it('同一项再点一次 = 取消勾选（toggle）', async () => {
    const w = mountSheet({ valueType: 'multi', candidates: ['辣', '麻'] })
    // 勾上 → 再取消（每次都重新查询：勾选态变化会重建 chip）
    await w.findAll('.chip-stub')[0].trigger('click')
    await w.findAll('.chip-stub')[0].trigger('click')
    await w.find('.aps-confirm').trigger('tap')
    expect(w.emitted('update')?.[0]).toEqual([[]])
  })
})

describe('AttributePickerSheet · 单选', () => {
  it('点项即选即关（回抛单元素数组）', async () => {
    const w = mountSheet({ valueType: 'single', selected: [], candidates: ['辣', '麻'] })
    await w.findAll('.chip-stub')[0].trigger('click')
    expect(w.emitted('update')?.[0]).toEqual([['辣']])
    expect(w.emitted('close')).toHaveLength(1)
  })

  it('点当前项 = 无改动（不 emit update）但仍关闭', async () => {
    const w = mountSheet({ valueType: 'single', selected: ['辣'], candidates: ['辣', '麻'] })
    await w.findAll('.chip-stub')[0].trigger('click')
    // 语义是「就它了 / 算了离开」—— 不产生 diff，但必须能关掉弹层
    expect(w.emitted('update')).toBeFalsy()
    expect(w.emitted('close')).toHaveLength(1)
  })

  it('单选不渲染确认按钮（点项即生效，无需二次确认）', () => {
    const w = mountSheet({ valueType: 'single', candidates: ['辣'] })
    expect(w.find('.aps-confirm').exists()).toBe(false)
  })
})

describe('AttributePickerSheet · 搜索', () => {
  it('候选 < 12 时不渲染搜索框（扫一眼比打字快）', () => {
    const w = mountSheet({ candidates: cands(11) })
    expect(w.find('.aps-search-input').exists()).toBe(false)
  })

  it('候选 ≥ 12 时渲染搜索框', () => {
    const w = mountSheet({ candidates: cands(12) })
    expect(w.find('.aps-search-input').exists()).toBe(true)
  })

  it('子串过滤候选行', async () => {
    const w = mountSheet({ candidates: cands(12) })
    // 「候选1」是子串，故也会命中 候选10/11/12 —— 过滤是 includes 而非前缀
    await w.find('.aps-search-input').trigger('input', inputEvent('候选1'))
    expect(w.findAll('.chip-stub').map((c) => c.text())).toEqual(['候选1', '候选10', '候选11', '候选12'])
  })

  it('搜索按**总数**判阈值（过滤后不足 12 条也不闪没）', async () => {
    const w = mountSheet({ candidates: cands(12) })
    await w.find('.aps-search-input').trigger('input', inputEvent('候选1'))
    // 判据是 props.candidates.length，不是过滤后条数
    expect(w.find('.aps-search-input').exists()).toBe(true)
  })

  it('无匹配时展示空态', async () => {
    const w = mountSheet({ candidates: cands(12) })
    await w.find('.aps-search-input').trigger('input', inputEvent('zzz'))
    expect(w.findAll('.chip-stub')).toHaveLength(0)
  })

  it('搜索**只过滤候选**，自定义值恒可见', async () => {
    const w = mountSheet({ candidates: cands(12), selected: ['我自己的值'], valueType: 'multi' })
    await w.find('.aps-search-input').trigger('input', inputEvent('zzz'))
    expect(w.text()).toContain('我自己的值')
  })
})

describe('AttributePickerSheet · 自定义值', () => {
  it('已选但不在候选池中的值恒展示为自定义项', () => {
    const w = mountSheet({ candidates: ['辣'], selected: ['我自己的值'], valueType: 'multi' })
    expect(w.text()).toContain('我自己的值')
  })

  it('多选添加后保留输入框展开（连填几个是常态）', async () => {
    const w = mountSheet({ valueType: 'multi', candidates: ['辣'] })
    await w.find('.aps-custom-entry').trigger('tap')
    await w.find('.aps-custom-input').trigger('input', inputEvent('新值'))
    await w.find('.aps-custom-add').trigger('tap')
    expect(w.find('.aps-custom-input').exists()).toBe(true)
  })

  it('添加后清空文本（便于继续填下一个）', async () => {
    const w = mountSheet({ valueType: 'multi', candidates: ['辣'] })
    await w.find('.aps-custom-entry').trigger('tap')
    await w.find('.aps-custom-input').trigger('input', inputEvent('新值'))
    await w.find('.aps-custom-add').trigger('tap')
    expect((w.find('.aps-custom-input').element as HTMLInputElement).value).toBe('')
  })

  it('重复添加同一自定义值被去重（静默不重复）', async () => {
    const w = mountSheet({ valueType: 'multi', candidates: ['辣'], selected: ['新值'] })
    await w.find('.aps-custom-entry').trigger('tap')
    await w.find('.aps-custom-input').trigger('input', inputEvent('新值'))
    await w.find('.aps-custom-add').trigger('tap')
    await w.find('.aps-confirm').trigger('tap')
    expect(w.emitted('update')?.[0]).toEqual([['新值']])
  })

  it('空白输入时「添加」不出现（`v-if="customDraft.trim()"`）', async () => {
    const w = mountSheet({ valueType: 'multi', candidates: ['辣'] })
    await w.find('.aps-custom-entry').trigger('tap')
    await w.find('.aps-custom-input').trigger('input', inputEvent('   '))
    expect(w.find('.aps-custom-add').exists()).toBe(false)
  })

  it('回车（@confirm）等价于点「添加」', async () => {
    const w = mountSheet({ valueType: 'multi', candidates: ['辣'] })
    await w.find('.aps-custom-entry').trigger('tap')
    await w.find('.aps-custom-input').trigger('input', inputEvent('回车值'))
    await w.find('.aps-custom-input').trigger('confirm')
    await w.find('.aps-confirm').trigger('tap')
    expect(w.emitted('update')?.[0]).toEqual([['回车值']])
  })

  it('单选添加自定义值 = 替换并关闭', async () => {
    const w = mountSheet({ valueType: 'single', selected: ['辣'], candidates: ['辣'] })
    await w.find('.aps-custom-entry').trigger('tap')
    await w.find('.aps-custom-input').trigger('input', inputEvent('新值'))
    await w.find('.aps-custom-add').trigger('tap')
    expect(w.emitted('update')?.[0]).toEqual([['新值']])
    expect(w.emitted('close')).toHaveLength(1)
  })

  it('重新打开时收起自定义输入（撤销聚焦请求，不抢下次焦点）', async () => {
    const w = mountSheet({ candidates: ['辣'] })
    await w.find('.aps-custom-entry').trigger('tap')
    expect(w.find('.aps-custom-input').exists()).toBe(true)
    await w.setProps({ visible: false })
    await w.setProps({ visible: true })
    expect(w.find('.aps-custom-input').exists()).toBe(false)
  })
})

describe('AttributePickerSheet · 清空与确认', () => {
  it('有值时渲染清空按钮', () => {
    const w = mountSheet({ valueType: 'multi', selected: ['辣'], candidates: ['辣'] })
    expect(w.find('.aps-clear').exists()).toBe(true)
  })

  it('无值时不给清空按钮', () => {
    const w = mountSheet({ valueType: 'multi', selected: [], candidates: ['辣'] })
    expect(w.find('.aps-clear').exists()).toBe(false)
  })

  it('清空是动作而非草稿：直接回抛空数组并关闭（无需二次确认）', async () => {
    const w = mountSheet({ valueType: 'multi', selected: ['辣'], candidates: ['辣'] })
    await w.find('.aps-clear').trigger('tap')
    expect(w.emitted('update')?.[0]).toEqual([[]])
    expect(w.emitted('close')).toHaveLength(1)
  })

  it('多选即使 0 项也渲染确认按钮（主操作不能消失）', () => {
    const w = mountSheet({ valueType: 'multi', selected: [], candidates: [] })
    expect(w.find('.aps-confirm').exists()).toBe(true)
  })

  it('确认按钮带已选数（关层前可见「改了几个」）', async () => {
    const w = mountSheet({ valueType: 'multi', candidates: ['辣', '麻'] })
    expect(w.find('.aps-confirm').text()).toBe('确认')
    await w.findAll('.chip-stub')[0].trigger('click')
    await w.findAll('.chip-stub')[1].trigger('click')
    expect(w.find('.aps-confirm').text()).toContain('已选 2')
  })
})
