import { computed } from 'vue'
import { defineStore } from 'pinia'
import { useCanteenStore } from './canteenStore'
import { useStallStore } from './stallStore'
import { useDishStore } from './dishStore'
import { useReviewStore } from './reviewStore'
import { useUserStore } from './userStore'
import { useBannerStore } from './bannerStore'
import type { Canteen, Stall, Dish, Review, User, Banner } from '@/types'

export const useAdminStore = defineStore('admin', () => {
  const canteen = useCanteenStore()
  const stall = useStallStore()
  const dish = useDishStore()
  const review = useReviewStore()
  const user = useUserStore()
  const banner = useBannerStore()

  // 显式标注泛型：避免 Pinia setup store 跨 store 引用时整体推断退化为 any，
  // 否则视图里 store.canteens.find(c => …) 的回调参数会触发 TS7006 隐式 any
  const canteens = computed<Canteen[]>(() => canteen.list)
  const activeCanteens = computed<Canteen[]>(() => canteen.activeList)
  const stalls = computed<Stall[]>(() => stall.list)
  const activeStalls = computed<Stall[]>(() => stall.activeList)
  const dishes = computed<Dish[]>(() => dish.list)
  const activeDishes = computed<Dish[]>(() => dish.activeList)
  const reviews = computed<Review[]>(() => review.list)
  const users = computed<User[]>(() => user.list)
  const banners = computed<Banner[]>(() => banner.sortedList)
  const activeBanners = computed<Banner[]>(() => banner.activeList)
  const maxBannerSortOrder = computed<number>(() => banner.maxSortOrder)

  // 统一重新加载全部业务数据（进入聚合页时调用，确保最新且覆盖登录前实例化的空态）
  async function loadAll() {
    await Promise.all([
      canteen.loadAll(),
      stall.loadAll(),
      dish.loadAll(),
      banner.loadAll(),
      review.loadAll(),
      user.loadAll(),
    ])
  }

  return {
    loadAll,
    canteens,
    stalls,
    dishes,
    reviews,
    users,
    activeCanteens,
    activeStalls,
    activeDishes,
    addCanteen: canteen.add,
    updateCanteen: canteen.update,
    deleteCanteen: canteen.remove,
    addStall: stall.add,
    updateStall: stall.update,
    deleteStall: stall.remove,
    addDish: dish.add,
    updateDish: dish.update,
    deleteDish: dish.remove,
    updateReview: review.update,
    deleteReview: review.remove,
    toggleUserStatus: user.toggleUserStatus,
    banners,
    activeBanners,
    maxBannerSortOrder,
    addBanner: banner.add,
    updateBanner: banner.update,
    deleteBanner: banner.remove,
    get stats() {
      return {
        totalCanteens: canteen.list.length,
        totalStalls: stall.list.length,
        totalDishes: dish.list.length,
        totalReviews: review.list.length,
        totalUsers: user.list.length,
        totalBanners: banner.list.length,
        todayOrders: 0,
      }
    },
  }
})
