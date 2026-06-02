import { ref } from 'vue'
import { defineStore } from 'pinia'

export interface BreadcrumbItem {
  label: string
  path?: string
}

export const usePageStore = defineStore('page', () => {
  const breadcrumbs = ref<BreadcrumbItem[]>([])
  const showSearch = ref(false)
  const searchPlaceholder = ref('搜索...')
  const searchQuery = ref('')

  function setPage(config: { breadcrumbs: BreadcrumbItem[]; showSearch?: boolean; searchPlaceholder?: string }) {
    breadcrumbs.value = config.breadcrumbs
    showSearch.value = config.showSearch ?? false
    searchPlaceholder.value = config.searchPlaceholder ?? '搜索...'
    searchQuery.value = ''
  }

  return { breadcrumbs, showSearch, searchPlaceholder, searchQuery, setPage }
})
