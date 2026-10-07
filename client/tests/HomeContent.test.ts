// @vitest-environment happy-dom
import { describe, it, expect, beforeEach, vi, afterAll } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import HomeContent from '@/pages/home/HomeContent.vue'
import { useDishStore } from '@/stores/dish'
import { installUni } from './stubs'

/**
 * `HomeContent` 首页列表边界态回归测试（P1-1 / P2-5 / P1-3 落地）：
 * ① 切视图失败且列表非空 ⇒ 顶部失败行「切换失败 · 点击重试」（点击回抛 `retry` + keepList）；
 * ② 切视图在途且列表非空 ⇒ 列表末尾「正在切换…」，旧列表保留不清空；
 * ③ 到底（`homeFinished`）⇒「没有更多了」；封顶（`homePageLimited`）优先于到底；
 * ④ 空态次文案指向真实可达控件（「换个大类看看」，首页无食堂选择器）。
 */

/** store 请求层 mock：用例按需覆写 `searchDishesPage` 的行为 */
vi.mock('@/api/dish', () => ({
  listDishViews: vi.fn(async () => []),
  searchDishesPage: vi.fn(async () => ({ list: [] })),
  searchDishes: vi.fn(async () => []),
  listGuessLike: vi.fn(async () => []),
}))

import * as dishApi from '@/api/dish'

/** 造一条最小菜品行（组件测试只消费 `id` 与列表长度） */
function dish(id: number) {
  return { id } as never
}

/** 挂载（子组件用 stub 隔离，只验本组件的渲染契约与事件上抛） */
function mountContent() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useDishStore()
  const w = mount(HomeContent, {
    global: {
      plugins: [pinia],
      stubs: {
        DishCard: { template: '<view class="dish-card-stub" />' },
        RetryBlock: { template: '<view class="retry-stub" />' },
        EmptyState: {
          props: ['title', 'desc'],
          template: '<view class="empty-stub">{{ title }}|{{ desc }}</view>',
        },
      },
    },
  })
  return { w, store }
}

describe('HomeContent 首页列表边界态', () => {
  beforeEach(() => {
    installUni()
    vi.mocked(dishApi.searchDishesPage).mockReset()
    vi.mocked(dishApi.searchDishesPage).mockResolvedValue({ list: [] } as never)
  })

  afterAll(() => {
    vi.restoreAllMocks()
  })

  it('🔴 切视图失败且列表非空：顶部失败行渲染，点击回抛 retry(true)', async () => {
    const { w, store } = mountContent()
    store.homeList.push(dish(1))
    vi.mocked(dishApi.searchDishesPage).mockRejectedValueOnce(new Error('network'))
    await store.setHomeView(11)
    await nextTick()

    const bar = w.find('.home-error-bar')
    expect(bar.exists()).toBe(true)
    expect(bar.text()).toContain('切换失败 · 点击重试')
    // 旧列表保留在屏（切视图失败不清空）
    expect(w.findAll('.dish-card-stub').length).toBe(1)

    await bar.trigger('tap')
    expect(w.emitted('retry')?.[0]).toEqual([true])
  })

  it('🔴 切视图在途且列表非空：旧列表保留，列表末尾追加「正在切换…」', async () => {
    const { w, store } = mountContent()
    store.homeList.push(dish(1))
    let resolveSwap!: (v: { list: never[] }) => void
    vi.mocked(dishApi.searchDishesPage).mockImplementationOnce(
      () => new Promise((r) => { resolveSwap = r }),
    )
    const swapping = store.setHomeView(12)
    await nextTick()

    const foot = w.find('.list-foot')
    expect(foot.exists()).toBe(true)
    expect(foot.text()).toContain('正在切换…')
    expect(w.findAll('.dish-card-stub').length).toBe(1)

    resolveSwap({ list: [dish(2), dish(3)] })
    await swapping
    await nextTick()
    expect(w.find('.home-error-bar').exists()).toBe(false)
  })

  it('🔴 到底（homeFinished）渲染「没有更多了」；封顶优先于到底', async () => {
    const { w, store } = mountContent()
    store.homeList.push(dish(1))
    store.homeFinished = true
    await nextTick()
    expect(w.find('.list-foot').text()).toContain('没有更多了')

    // 封顶优先级更高：到达保留页数上限时展示「已展示前 N 个」而非「没有更多了」
    store.homePageLimited = true
    await nextTick()
    expect(w.find('.list-foot').text()).toContain('已展示前')
    expect(w.find('.list-foot').text()).not.toContain('没有更多了')
  })

  it('空态次文案指向真实可达控件：「换个大类看看」', () => {
    const { w } = mountContent()
    expect(w.find('.empty-stub').text()).toContain('暂无菜品')
    expect(w.find('.empty-stub').text()).toContain('换个大类看看')
  })
})
