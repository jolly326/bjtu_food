import { defineStore } from 'pinia'
import { useCanteenStore } from './canteenStore'
import { useStallStore } from './stallStore'
import { useDishStore } from './dishStore'
import { useReviewStore } from './reviewStore'
import { useUserStore } from './userStore'

export const useAdminStore = defineStore('admin', () => {
  const canteen = useCanteenStore()
  const stall = useStallStore()
  const dish = useDishStore()
  const review = useReviewStore()
  const user = useUserStore()

  return {
    canteens: canteen.list,
    stalls: stall.list,
    dishes: dish.list,
    reviews: review.list,
    users: user.list,
    activeCanteens: canteen.activeList,
    activeStalls: stall.activeList,
    activeDishes: dish.activeList,
    addCanteen: canteen.add,
    updateCanteen: canteen.update,
    deleteCanteen: canteen.remove,
    addStall: stall.add,
    updateStall: stall.update,
    deleteStall: stall.remove,
    addDish: dish.add,
    updateDish: dish.update,
    deleteDish: dish.remove,
    deleteReview: review.remove,
    toggleUserStatus: user.toggleUserStatus,
    updateUserProfile: user.updateProfile,
    get stats() {
      return {
        totalCanteens: canteen.list.length,
        totalStalls: stall.list.length,
        totalDishes: dish.list.length,
        totalReviews: review.list.length,
        totalUsers: user.list.length,
        todayOrders: 0,
      }
    },
  }
})
