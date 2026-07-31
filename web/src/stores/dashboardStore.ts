import { ref } from 'vue'
import { defineStore } from 'pinia'
import { get } from '@/api/http'

export interface DashboardMetrics {
  range: string
  newDishCount: number
  newReviewCount: number
  totalDishCount: number
  totalReviewCount: number
  hotCanteens: { id: number; name: string; score: number }[]
  hotDishes: { id: number; name: string; score: number }[]
  viewTrend: { dates: string[]; values: number[] }
  reviewTrend: { dates: string[]; values: number[] }
}

export type DashRange = 'week' | 'month' | 'all'

export const useDashboardStore = defineStore('dashboard', () => {
  const metrics = ref<DashboardMetrics | null>(null)
  const loading = ref(false)
  const range = ref<DashRange>('week')

  async function load(rangeValue: DashRange = range.value) {
    range.value = rangeValue
    loading.value = true
    try {
      metrics.value = await get<DashboardMetrics>('/admin/dashboard', { range: rangeValue })
    } catch {
      metrics.value = null
    } finally {
      loading.value = false
    }
  }

  return { metrics, loading, range, load }
})
