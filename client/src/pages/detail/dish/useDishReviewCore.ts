/**
 * 评价列表 + 删除编排（评价分页 / 删除本人评价）。
 *
 * 分页封底口径与 `usePagedList` 同源（结束判据 = 本页返回条数 < `pageSize`）；
 * 重置式请求在途时由 `reviewPending` 禁止追加下一页。
 * 本模块**无用户态缓存**（写评价已取消写前判定与「我的评价」态写回）。
 */
import { ref, computed } from 'vue'
import type { Ref } from 'vue'
import { useUserStore } from '@/stores/user'
import type { DishDetailState } from './useDishDetail'
import { deleteReview } from '@/api/review'
import { isResourceNotFound } from '@/api/http'
import { toastError, toastInfo, toastSuccess } from '@/utils/error'
import { MAX_LIST_PAGES, REVIEW_PAGE_SIZE } from '@/constants/paging'
import { isLastPage } from '@/composables/usePagedList'
import { MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'
import type { Review, MyReview } from '@/types/review'
import { REVIEW_GONE_TEXT } from '@/constants/copy'

export function useDishReviewCore(opts: {
  dishId: Ref<number>
  /** 本页私有的详情态（按页实例，脱离全局 store） */
  detail: DishDetailState
}) {
  const userStore = useUserStore()
  const { dishId, detail } = opts

  const reviewList = computed(() => detail.reviewList.value)
  /** 评价首屏/刷新失败态（PR-03）：失败 ≠ 零评价，由评价卡渲染可重试失败块 */
  const reviewFailed = computed(() => detail.reviewError.value)

  /**
   * 非 append（重置式）评价请求在途计数：驱动评价区**在途期空白静默**。
   * 覆盖首屏拉取 / 重试 / 提交后刷新 —— 先清空再拉取期间不得误闪「暂无评价」；
   * 页面上不呈现任何骨架屏 / loading 指示（§4.8 红线）。
   * 用计数而非布尔：删除后重拉、提交后重拉可能并发，计数可正确收敛。
   */
  const reviewPendingCount = ref(0)
  const reviewPending = computed(() => reviewPendingCount.value > 0)

  /** detail-modular-review-cleanup：评价卡内触底分页 */
  const reviewPage = ref(1)
  const reviewFinished = ref(false)
  const reviewLoadingMore = ref(false)
  function resetReviewPaging() {
    reviewPage.value = 1
    reviewFinished.value = false
    reviewLoadingMore.value = false
  }

  /** 重置式评价拉取（首屏 / 重试 / 提交后与删除后刷新共用），置 pending 门控 */
  async function fetchReviewsReset() {
    if (!dishId.value) return
    reviewPendingCount.value += 1
    try {
      await detail.fetchReviews(dishId.value, { pageSize: REVIEW_PAGE_SIZE })
    } finally {
      reviewPendingCount.value -= 1
    }
  }

  /** 触底加载下一页评价（**结束判据 = 本页返回条数 < `pageSize`**） */
  async function onReviewsReachBottom() {
    // 竞态修复：重置式请求（首屏 / 重试）在途时**禁止**追加下一页——
    // 否则 append 会推进 store 的 `reviewFetchSeq`，使在途的 reset 响应被判为过期丢弃
    // ⇒ 列表只剩第 2 页、第 1 页消失（列表内容错乱）。
    if (reviewPending.value) return
    if (!detail.currentDish.value || reviewLoadingMore.value || reviewFinished.value) return
    // 页数封顶：评价区无虚拟化，达上限后停止追加（避免深翻节点无限增长）
    if (reviewPage.value >= MAX_LIST_PAGES) {
      reviewFinished.value = true
      return
    }
    reviewLoadingMore.value = true
    try {
      // 排序唯一时间倒序，端上不传 sort（PR-02）
      const res = await detail.fetchReviews(dishId.value, {
        page: reviewPage.value + 1,
        pageSize: REVIEW_PAGE_SIZE,
        append: true,
      })
      // null = 请求失败/被更新请求过期淘汰（store 竞态守卫）：分页不推进，保留重试机会
      if (!res) return
      reviewPage.value += 1
      // 结束判据（单一真源 isLastPage）：已到末页 ⇒ 不再多发空请求
      if (isLastPage(res.list, REVIEW_PAGE_SIZE)) reviewFinished.value = true
    } catch { /* 底部加载失败静默，后续滚动可重试 */ } finally { reviewLoadingMore.value = false }
  }

  /** 评价失败态点击重试（PR-03）：重置分页后从第 1 页重拉，与进入页面同路径 */
  function onRetryReviews() {
    if (!dishId.value) return
    resetReviewPaging()
    void fetchReviewsReset()
  }

  /**
   * 删除本人评价：成功后重拉列表 + 刷新评分。
   *
   * **4001（评价不存在）**：已在别处删除 / 评价 ID 失效 ⇒ 重试无意义，
   * 按「已不存在」收尾（本地移除 + 提示），不再走通用失败文案。
   */
  function onDeleteReview(rv: Review | MyReview) {
    if (!userStore.requireAuth(() => onDeleteReview(rv))) return
    // 公开视角行才带作者标识（本人视角 MyReviewVO 不含 userId，列表内恒为本人）
    if ('userId' in rv && userStore.userInfo?.id && rv.userId !== userStore.userInfo.id) return
    uni.showModal({
      title: '删除评价',
      content: '确定删除这条评价吗？删除后不可恢复。',
      confirmText: '删除',
      confirmColor: MODAL_CONFIRM_DANGER_COLOR,
      success: async (res) => {
        if (!res.confirm) return
        try {
          await deleteReview(rv.id)
          toastSuccess('评价已删除')
          resetReviewPaging()
          await fetchReviewsReset()
          detail.fetchDetail(dishId.value)
        } catch (e) {
          if (isResourceNotFound(e)) {
            // 评价已不存在：本地移除即可（不提示「删除失败」误导可重试）
            detail.removeReview(rv.id)
            toastInfo(REVIEW_GONE_TEXT)
            return
          }
          toastError(e, '删除失败')
        }
      },
    })
  }

  return {
    reviewList,
    reviewFailed,
    reviewPending,
    resetReviewPaging,
    fetchReviewsReset,
    onReviewsReachBottom,
    onRetryReviews,
    onDeleteReview,
  }
}
