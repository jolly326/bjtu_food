import { computed } from 'vue'
import { defineStore } from 'pinia'
import { useCanteenStore } from './canteenStore'
import { useStallStore } from './stallStore'
import { useDishStore } from './dishStore'
import { useReviewStore } from './reviewStore'
import { useUserStore } from './userStore'
import type { Canteen, Stall, Dish, Review, User } from '@/types'

/**
 * adminStore：后台聚合层（跨 store 统一 loadAll + 常用写操作转发）。
 *
 * 2026-09-14 P3-08 清理：移除零消费死成员
 *  - `activeCanteens` / `activeStalls` / `activeDishes`（派生自各 store 的 `activeList`，三链一并下沉）；
 *  - `stats` / `todayOrders`（`todayOrders` 恒为 0，与本项目「无下单」定位相悖）。
 *
 * 2026-09-14 Q-113/Q-115 → 2026-09-15 §7.23 第 1 条：食堂/档口是**菜品筛选属性字典**，
 * 生命周期只有「按名 upsert（随菜品）/ 改名」，独立新增端点已删除，
 * 故此处只保留 `updateCanteen` / `updateStall`（改名），**不提供 addCanteen / addStall / delete***。
 */
export const useAdminStore = defineStore('admin', () => {
  const canteen = useCanteenStore()
  const stall = useStallStore()
  const dish = useDishStore()
  const review = useReviewStore()
  const user = useUserStore()

  // 显式标注泛型：避免 Pinia setup store 跨 store 引用时整体推断退化为 any，
  // 否则视图里 store.canteens.find(c => …) 的回调参数会触发 TS7006 隐式 any
  const canteens = computed<Canteen[]>(() => canteen.list)
  const stalls = computed<Stall[]>(() => stall.list)
  const dishes = computed<Dish[]>(() => dish.list)
  const reviews = computed<Review[]>(() => review.list)
  const users = computed<User[]>(() => user.list)

  /**
   * 统一重新加载全部业务数据（确需全量刷新的场景调用，如批量操作结束后的统一刷新）。
   *
   * WEB-09：改为 Promise.allSettled **各域独立容错**——单域失败不再拖垮整页
   * （此前 Promise.all 任一域 reject 会让其余已成功域的结果对调用方不可见）。
   * 语义：成功域的数据照常落库；存在失败域时抛出第一个错误，由调用方进入错误态重试。
   */
  async function loadAll() {
    const results = await Promise.allSettled([
      canteen.loadAll(),
      stall.loadAll(),
      dish.loadAll(),
      review.loadAll(),
      user.loadAll(),
    ])
    const firstRejected = results.find((r): r is PromiseSettledResult<never> & { status: 'rejected' } => r.status === 'rejected')
    if (firstRejected) throw firstRejected.reason
  }

  return {
    loadAll,
    canteens,
    stalls,
    dishes,
    reviews,
    users,
    /** 食堂改名（属性字典唯一编辑动作；新增走菜品按名 upsert，无删除） */
    updateCanteen: canteen.update,
    /** 档口改名（属性字典唯一编辑动作；新增走菜品按名 upsert，无删除） */
    updateStall: stall.update,
    addDish: dish.add,
    updateDish: dish.update,
    deleteDish: dish.remove,
    updateReview: review.update,
    updateReviewSecState: review.updateSecState,
    deleteReview: review.remove,
    toggleUserStatus: user.toggleUserStatus,
  }
})
