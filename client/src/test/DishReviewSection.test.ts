// @vitest-environment happy-dom
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import DishReviewSection from '../pages/detail/dish/DishReviewSection.vue'
import { installUni } from './stubs'
import type { Review } from '../types/review'

/**
 * `DishReviewSection` 星级筛选条渲染回归测试。
 *
 * <p>测试重点是**「筛选无结果」时的死路修复**：
 * ① 有评价 ⇒ 筛选条渲染；
 * ② 🔴 选中某星级但服务端返回 0 条 ⇒ **筛选条必须仍然渲染**——
 *    否则「全部 / ⭐5~⭐1」全部消失，用户**没有任何出口切回全部**，页面锁死；
 * ③ 零评价且未筛选 ⇒ 不渲染筛选条（无可筛项），走「快来抢首评 ⭐ 5.0」号召。
 */

/** 造一条评价 */
function review(id: number, rating: number): Review {
  return {
    id,
    rating,
    content: '好吃',
    images: [],
    createdAt: '2026-10-01 12:00:00',
  } as unknown as Review
}

/** 挂载（子组件用 stub 隔离，本次只验本组件的渲染契约） */
function mountSection(over: Record<string, unknown> = {}) {
  return mount(DishReviewSection, {
    props: {
      reviews: [] as Review[],
      count: 0,
      pending: false,
      ...over,
    },
    global: {
      stubs: {
        CardSection: { template: '<view><slot /></view>' },
        SectionTitle: { template: '<view><slot name="extra" /></view>' },
        ReviewItem: true,
        RetryBlock: true,
        EmptyState: {
          props: ['title', 'description'],
          template: '<view class="empty-stub">{{ title }}</view>',
        },
        IconSvg: true,
      },
    },
  })
}

describe('DishReviewSection 星级筛选条', () => {
  beforeEach(() => installUni())

  it('有评价时渲染筛选条', () => {
    const w = mountSection({ reviews: [review(1, 5)], count: 1 })
    expect(w.find('.rating-filter').exists()).toBe(true)
  })

  it('🔴 筛选无结果时筛选条仍渲染（避免无法切回「全部」的死路）', () => {
    // 选中 ⭐1 但服务端返回 0 条：列表空，筛选条必须保留
    const w = mountSection({ reviews: [], count: 0, ratingFilter: 1 })
    expect(w.find('.rating-filter').exists()).toBe(true)
    // 同时展示「筛选无结果」空态（而非零评价号召）
    expect(w.find('.empty-stub').text()).toContain('暂无 1 星评价')
  })

  it('零评价且未筛选时不渲染筛选条，展示抢首评号召', () => {
    const w = mountSection({ reviews: [], count: 0, ratingFilter: null })
    expect(w.find('.rating-filter').exists()).toBe(false)
    expect(w.find('.empty-stub').text()).toContain('快来抢首评 ⭐ 5.0')
  })

  it('筛选条高亮当前选中项，点击回抛 rating（null = 全部）', async () => {
    const w = mountSection({ reviews: [review(1, 5)], count: 1, ratingFilter: 5 })
    const active = w.findAll('.filter-chip').filter((c) =>
      c.classes().includes('filter-chip--active'),
    )
    expect(active).toHaveLength(1)
    expect(active[0].text()).toBe('⭐5')

    // 点击「全部」应回抛 null
    await w.findAll('.filter-chip')[0].trigger('tap')
    expect(w.emitted('filter')?.[0]).toEqual([null])
  })
})