import { ref } from 'vue'
import { defineStore } from 'pinia'

export interface BreadcrumbItem {
  label: string
  path?: string
}

export const usePageStore = defineStore('page', () => {
  const breadcrumbs = ref<BreadcrumbItem[]>([])

  function setPage(config: { breadcrumbs: BreadcrumbItem[] }) {
    breadcrumbs.value = config.breadcrumbs
  }

  return { breadcrumbs, setPage }
})
