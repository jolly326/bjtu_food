/**
 * 评价三点菜单（ReviewItem @more → 页面级通用 ActionSheet）：本人删除 / 他人举报。
 *
 * 仅持有菜单 UI 态 + 路由到具体动作（onDeleteReview / onReport），不持有删除/举报实现，
 * 由 `useDishPage` 注入（删除落在 `useDishReviewCore`，举报落在 `useReport`）。
 * ⚠️ `iconColor` 必须传**实色** `COLOR_MAP['error']`（ActionSheet 契约：IconSvg 的 color 不解析 var()）；
 * `textColor` 走 CSS 绑定、`var()` **合法**，故保持 `var(--color-error)` 以随主题。
 */
import { ref, computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { COLOR_MAP } from '@/theme/tokens'
import type { Review, MyReview } from '@/types/review'

export function useDishReviewMenu(opts: {
  onDeleteReview: (rv: Review | MyReview) => void
  onReport: (rv: Review | MyReview) => void
}) {
  const userStore = useUserStore()
  const { onDeleteReview, onReport } = opts

  const reviewMoreOpen = ref(false)
  const reviewMoreTarget = ref<Review | MyReview | null>(null)
  const reviewMoreIsOwn = computed(() => {
    const rv = reviewMoreTarget.value
    if (rv == null || userStore.userInfo?.id == null) return false
    // 公开视角行带作者标识；本人视角（MyReviewVO 无 userId）列表内恒为本人评价
    return 'userId' in rv ? rv.userId === userStore.userInfo.id : true
  })

  const reviewMoreItems = computed(() => {
    if (!reviewMoreTarget.value) return []
    return reviewMoreIsOwn.value
      ? [{ key: 'delete', label: '删除评价', icon: 'delete', iconColor: COLOR_MAP['error'], textColor: 'var(--color-error)' }]
      : [{ key: 'report', label: '举报评价', icon: 'report', iconColor: COLOR_MAP['error'], textColor: 'var(--color-error)' }]
  })

  function onReviewMore(rv: Review | MyReview) {
    reviewMoreTarget.value = rv
    reviewMoreOpen.value = true
  }

  function onReviewMoreSelect(key: string) {
    const rv = reviewMoreTarget.value
    if (!rv) return
    if (key === 'delete') onDeleteReview(rv)
    else if (key === 'report') onReport(rv)
  }

  return {
    reviewMoreOpen,
    reviewMoreItems,
    onReviewMore,
    onReviewMoreSelect,
  }
}
