import { computed } from 'vue'
import { defineStore } from 'pinia'
import { useCanteenStore } from './canteenStore'
import { useStallStore } from './stallStore'
import { useDishStore } from './dishStore'
import { useReviewStore } from './reviewStore'
import { useUserStore } from './userStore'
import { useBannerStore } from './bannerStore'

export const useAdminStore = defineStore('admin', () => {
  const canteen = useCanteenStore()
  const stall = useStallStore()
  const dish = useDishStore()
  const review = useReviewStore()
  const user = useUserStore()
  const banner = useBannerStore()

  const canteens = computed(() => canteen.list)
  const activeCanteens = computed(() => canteen.activeList)
  const stalls = computed(() => stall.list)
  const activeStalls = computed(() => stall.activeList)
  const dishes = computed(() => dish.list)
  const activeDishes = computed(() => dish.activeList)
  const reviews = computed(() => review.list)
  const users = computed(() => user.list)
  const banners = computed(() => banner.sortedList)
  const activeBanners = computed(() => banner.activeList)
  const maxBannerSortOrder = computed(() => banner.maxSortOrder)

  return {
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
    addReview: review.add,
    updateReview: review.update,
    deleteReview: review.remove,
    addUser: user.add,
    deleteUser: user.remove,
    toggleUserStatus: user.toggleUserStatus,
    updateUserProfile: user.updateProfile,
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
