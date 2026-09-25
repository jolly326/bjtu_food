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
 * 食堂/档口是**菜品筛选属性字典**（Q-113/Q-115 → §7.23 第 1 条）：
 * 生命周期只有「按名 upsert（随菜品）/ 改名」，**不提供 addCanteen / addStall / delete***。
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

  return {
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
    deleteReview: review.remove,
    toggleUserStatus: user.toggleUserStatus,
  }
})
