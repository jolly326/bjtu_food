// @vitest-environment happy-dom
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import CorrectionForm from '../pages/correction/CorrectionForm.vue'
import { installUni, inputEvent, uniCalls } from './stubs'
import type { AttributeEditor, CorrectionFormModel } from '../pages/correction/useCorrection'

/**
 * `CorrectionForm` 渲染回归测试。
 *
 * <p>该组件的 script 只做「渲染 + 就地写回」，**测试重点在写回契约**：
 * ① 预填未就绪时**不渲染半截表单**（避免误改 / 误提交）；
 * ② 任何用户动作都必须回抛 `clear`（撤下上一次的失败提示）——漏一处就会留过期橙字；
 * ③ 字段值**就地写回 props.model**（父级 reactive 为唯一真源），组件不持有副本。
 */

/** 造一份维度编辑项 */
function attr(
  fieldKey: string,
  selected: string[] = [],
  valueType: 'single' | 'multi' = 'multi',
): AttributeEditor {
  return { fieldKey, name: fieldKey, valueType, candidates: ['辣', '麻'], selected }
}

/** 造表单模型（父级持有，测试直接断言其被写回） */
function makeModel(over: Partial<CorrectionFormModel> = {}) {
  return reactive<CorrectionFormModel>({
    name: '红烧肉',
    price: '12.50',
    canteenName: '一食堂',
    floor: '二层',
    stallName: '窗口1',
    images: [],
    attributes: [],
    ...over,
  })
}

/** 挂载（子组件用 stub 隔离：本次只验本组件的渲染与写回契约） */
function mountForm(over: Record<string, unknown> = {}, model = makeModel()) {
  const wrapper = mount(CorrectionForm, {
    props: {
      model,
      dishName: '红烧肉',
      dishLocation: '一食堂 · 二层 · 窗口1',
      detailLoading: false,
      errors: {} as Record<string, string>,
      submitting: false,
      canSubmit: true,
      gateHint: '',
      submitError: '',
      ...over,
    },
    global: {
      stubs: {
        AppButton: { template: '<view class="app-btn-stub" @click="$emit(\'press\')" />' },
        IconSvg: true,
        ImagePicker: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template:
            '<view class="img-picker-stub" @click="$emit(\'update:modelValue\', [\'a.jpg\', \'b.jpg\'])" />',
        },
        AttributeGroup: {
          // ⚠️ 真实组件接收**扁平 props**（field-key / name / value-type / selected …），
          // 不是单个 editor 对象 —— 写错 prop 名会让 `editor.fieldKey` 读 undefined。
          props: ['fieldKey', 'name', 'valueType', 'selected', 'candidates', 'first'],
          emits: ['change'],
          // ⚠️ 真实契约是 **两个实参** `emit('change', fieldKey, selected)`，
          // 不是传一个数组 —— 父级按 `fieldKey` 反查编辑项，传数组会查不到。
          template:
            '<view class="attr-group-stub" :data-key="fieldKey" @click="$emit(\'change\', fieldKey, [\'甜\'])" />',
        },
        FloorPickerSheet: {
          // 具名 stub：`findComponent({ name })` 才能定位到它（无 name 时无法按名查询）
          name: 'FloorPickerSheetStub',
          props: ['visible'],
          emits: ['select'],
          template: '<view class="floor-sheet-stub" @click="$emit(\'select\', \'三层\')" />',
        },
      },
    },
  })
  return { wrapper, model }
}

beforeEach(() => {
  installUni()
})

describe('CorrectionForm · 预填未就绪', () => {
  it('detailLoading 时只显示载入文案，不渲染任何输入框', () => {
    const { wrapper } = mountForm({ detailLoading: true })
    expect(wrapper.text()).toContain('正在载入菜品信息')
    // 关键：半截表单会诱导用户误改 / 误提交
    expect(wrapper.find('input').exists()).toBe(false)
  })

  it('detailLoading 时不展示锚定的菜名与位置', () => {
    const { wrapper } = mountForm({ detailLoading: true })
    expect(wrapper.text()).not.toContain('红烧肉')
  })

  it('就绪后渲染输入框与锚定信息', () => {
    const { wrapper } = mountForm()
    expect(wrapper.find('input').exists()).toBe(true)
    expect(wrapper.text()).toContain('红烧肉')
  })

  it('dishName 为空时锚定行显示占位破折号', () => {
    const { wrapper } = mountForm({ dishName: '' })
    expect(wrapper.text()).toContain('——')
  })
})

describe('CorrectionForm · 字段就地写回', () => {
  it('输入菜名写回 model 并回抛 clear', async () => {
    const { wrapper, model } = mountForm()
    await wrapper.findAll('input')[0].trigger('input', inputEvent('梅菜扣肉'))

    expect(model.name).toBe('梅菜扣肉')
    expect(wrapper.emitted('clear')?.[0]).toEqual(['form.name'])
  })

  it('输入售价写回 model（**原样字符串，不换算**）', async () => {
    const { wrapper, model } = mountForm()
    // 金额红线：组件只回填字符串，换算在提交前由 correctionDiff 统一做
    await wrapper.findAll('input')[1].trigger('input', inputEvent('8.5'))
    expect(model.price).toBe('8.5')
  })

  it('事件缺少 detail.value 时回填空串（不得写入 undefined）', async () => {
    const { wrapper, model } = mountForm()
    await wrapper.findAll('input')[0].trigger('input', { detail: {} })
    expect(model.name).toBe('')
  })

  it('属性维度变化写回对应编辑项的 selected', async () => {
    const model = makeModel({ attributes: [attr('taste', ['辣'])] })
    const { wrapper } = mountForm({}, model)
    const group = wrapper.find('.attr-group-stub')
    expect(group.attributes('data-key')).toBe('taste')
    await group.trigger('click')
    expect(model.attributes[0].selected).toEqual(['甜'])
  })

  it('属性变化回抛 clear 的键带维度名', async () => {
    const model = makeModel({ attributes: [attr('taste', ['辣'])] })
    const { wrapper } = mountForm({}, model)
    await wrapper.find('.attr-group-stub').trigger('click')
    expect(wrapper.emitted('clear')?.[0]).toEqual(['form.attributes.taste'])
  })

  it('属性区无维度时不渲染（`v-if="model.attributes.length"` 守卫）', () => {
    // 无维度 ⇒ 整块属性区不出现，故不存在「找不到编辑项」这一场景：
    // 父级的 find 兜底是为**异常数据**（模型与模板不同步）准备的。
    const { wrapper } = mountForm({}, makeModel({ attributes: [] }))
    expect(wrapper.find('.attr-group-stub').exists()).toBe(false)
  })

  it('图片变化写回 model.images 并回抛 clear', async () => {
    const { wrapper, model } = mountForm()
    await wrapper.find('.img-picker-stub').trigger('click')
    expect(model.images).toEqual(['a.jpg', 'b.jpg'])
    expect(wrapper.emitted('clear')?.[0]).toEqual(['form.images'])
  })
})

describe('CorrectionForm · 楼层选择', () => {
  it('楼层单元格直接展示 floor 汉字值（值即显示值）', () => {
    const { wrapper } = mountForm({}, makeModel({ floor: '负一层' }))
    expect(wrapper.text()).toContain('负一层')
  })

  it('楼层为空时显示占位「请选择楼层」', () => {
    const { wrapper } = mountForm({}, makeModel({ floor: '' }))
    expect(wrapper.text()).toContain('请选择楼层')
  })

  it('选中后写入所选**汉字**（即存储值）', async () => {
    const model = makeModel({ floor: '二层' })
    const { wrapper } = mountForm({}, model)
    await wrapper.find('.row-field--picker').trigger('tap')
    await wrapper.find('.floor-sheet-stub').trigger('click')
    expect(model.floor).toBe('三层')
  })

  it('选中楼层回抛 clear', async () => {
    const model = makeModel({ floor: '二层' })
    const { wrapper } = mountForm({}, model)
    await wrapper.find('.row-field--picker').trigger('tap')
    await wrapper.find('.floor-sheet-stub').trigger('click')
    expect(wrapper.emitted('clear')?.some((e) => e[0] === 'form.floor')).toBe(true)
  })

  it('提交中禁开楼层弹层（visible 保持 false）', async () => {
    const model = makeModel({ floor: '二层' })
    const { wrapper } = mountForm({ submitting: true }, model)
    await wrapper.find('.row-field--picker').trigger('tap')
    // 弹层受 `visible` 驱动；提交中被 openFloorPicker 拦截 ⇒ visible 仍为 false
    expect(wrapper.findComponent({ name: 'FloorPickerSheetStub' }).props('visible')).toBe(false)
    expect(model.floor).toBe('二层')
  })

  it('未提交时点开楼层弹层（visible 变 true）', async () => {
    const { wrapper } = mountForm({}, makeModel({ floor: '二层' }))
    await wrapper.find('.row-field--picker').trigger('tap')
    expect(wrapper.findComponent({ name: 'FloorPickerSheetStub' }).props('visible')).toBe(true)
  })
})

describe('CorrectionForm · 提交区', () => {
  it('可提交时由 AppButton 自身 emit press，不重复 toast', async () => {
    const { wrapper } = mountForm({ canSubmit: true })
    await wrapper.find('.app-btn-stub').trigger('click')
    expect(uniCalls.showToast).toHaveLength(0)
  })

  it('不可提交时点击热区 → toast 提示且不提交', async () => {
    const { wrapper } = mountForm({ canSubmit: false, gateHint: '你还没有改动任何信息' })
    await wrapper.find('.submit-area').trigger('tap')
    expect(uniCalls.showToast[0]?.title).toBe('你还没有改动任何信息')
  })

  it('不可提交且无提示文案时用兜底文案', async () => {
    const { wrapper } = mountForm({ canSubmit: false, gateHint: '' })
    await wrapper.find('.submit-area').trigger('tap')
    expect(uniCalls.showToast[0]?.title).toBe('还不能提交')
  })

  it('提交中点击热区静默（不弹提示、不提交）', async () => {
    const { wrapper } = mountForm({ canSubmit: false, gateHint: 'x', submitting: true })
    await wrapper.find('.submit-area').trigger('tap')
    expect(uniCalls.showToast).toHaveLength(0)
  })

  it('submitError 非空时展示失败原因', () => {
    const { wrapper } = mountForm({ submitError: '菜品信息加载中' })
    expect(wrapper.find('.submit-error').text()).toContain('菜品信息加载中')
  })

  it('submitError 为空时不渲染失败提示节点', () => {
    const { wrapper } = mountForm({ submitError: '' })
    expect(wrapper.find('.submit-error').exists()).toBe(false)
  })
})

describe('CorrectionForm · 校验错误呈现', () => {
  it('字段有错时展示行内错误小字', () => {
    const { wrapper } = mountForm({ errors: { 'form.name': '菜名叫啥？填一下' } })
    expect(wrapper.find('.row-error').text()).toContain('菜名叫啥？填一下')
  })

  it('字段有错时对应容器加错误态类（底线切错误色）', () => {
    const { wrapper } = mountForm({ errors: { 'form.name': 'x' } })
    expect(wrapper.find('.row-field--error').exists()).toBe(true)
  })

  it('无错误时不加错误态类、不渲染小字', () => {
    const { wrapper } = mountForm({ errors: {} })
    expect(wrapper.find('.row-field--error').exists()).toBe(false)
    expect(wrapper.find('.row-error').exists()).toBe(false)
  })
})
