// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { reactive } from 'vue'
import IssueForm from '@/pages/feedback/IssueForm.vue'
import type { FeedbackFormModel } from '@/pages/feedback/useFeedback'

/**
 * `IssueForm` 反馈类型**字典驱动**渲染回归测试。
 *
 * <p>类型选项 = 字典 `GET /feedback-options` 下发（props 注入，端上零硬编码）：
 * ① 选项**只显示名称**（无副标题 hint）；② 选中态 / `pick` 回抛载荷 = 字典项 `id`（optionId）；
 * ③ 字典在途 / 失败两态：失败行点击回抛 `retry-options`（与举报弹层一族做法）。
 */

function makeModel(over: Partial<FeedbackFormModel> = {}) {
  return reactive<FeedbackFormModel>({ optionId: 0, content: '', images: [], ...over })
}

function mountForm(over: Record<string, unknown> = {}, model = makeModel()) {
  return mount(IssueForm, {
    props: {
      model,
      'onUpdate:model': (next: FeedbackFormModel) => Object.assign(model, next),
      errors: {} as Record<string, string>,
      placeholder: '请具体描述你遇到的问题或建议',
      options: [
        { id: 1, name: '程序功能Bug' },
        { id: 2, name: '产品功能建议' },
        { id: 3, name: '其他相关问题' },
      ],
      optionsLoading: false,
      optionsFailed: false,
      ...over,
    },
    global: {
      stubs: {
        AppIcon: true,
        ImagePicker: { props: ['modelValue'], emits: ['update:modelValue', 'pick'], template: '<view class="img-picker-stub" />' },
      },
    },
  })
}

describe('IssueForm 反馈类型字典渲染', () => {
  it('选项按字典顺序渲染，只显示名称（无 hint 副标题）', () => {
    const w = mountForm()
    const rows = w.findAll('.type-row')
    expect(rows).toHaveLength(3)
    expect(rows[0].text()).toBe('程序功能Bug')
    expect(rows[1].text()).toBe('产品功能建议')
    expect(rows[2].text()).toBe('其他相关问题')
    expect(w.find('.type-hint').exists()).toBe(false)
  })

  it('点选选项：pick 回抛字典项 id（optionId），并回抛 clear 撤下类型错误', async () => {
    const w = mountForm()
    await w.findAll('.type-row')[1].trigger('tap')
    expect(w.emitted('pick')).toEqual([[2]])
    expect(w.emitted('clear')).toEqual([['form.optionId']])
  })

  it('选中态由 aria-checked 表达（model.optionId 命中项为 true）', () => {
    const w = mountForm({}, makeModel({ optionId: 3 }))
    const rows = w.findAll('.type-row')
    expect(rows[2].attributes('aria-checked')).toBe('true')
    expect(rows[0].attributes('aria-checked')).toBe('false')
  })

  it('字典在途：渲染「加载中…」提示行，不渲染选项', () => {
    const w = mountForm({ options: [], optionsLoading: true })
    expect(w.find('.type-list').exists()).toBe(false)
    expect(w.find('.type-state').text()).toBe('加载中…')
  })

  it('字典失败：渲染失败行，点击回抛 retry-options', async () => {
    const w = mountForm({ options: [], optionsFailed: true })
    expect(w.find('.type-state').text()).toBe('反馈类型加载失败，点击重试')
    await w.find('.type-state').trigger('tap')
    expect(w.emitted('retry-options')).toHaveLength(1)
  })
})
