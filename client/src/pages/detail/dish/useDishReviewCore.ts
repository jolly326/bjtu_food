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
import { isResourceNotFound } from '@/api/errors'
import { toastError, toastInfo, toastSuccess } from '@/utils/error'
import { MAX_LIST_PAGES, REVIEW_PAGE_SIZE } from '@/constants/paging'
import { isLastPage } from '@/composables/usePagedList'
import { MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'
import type { Review, MyReview } from '@/types/review'
import { CONFIRM_DELETE_REVIEW, REVIEW_GONE_TEXT, TOAST_REVIEW_DELETED } from '@/constants/copy'
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
   * 非 append（重置式）评价请求在途计数：驱动评价区在途期不渲染列表与空态。
   * 覆盖首屏拉取 / 重试 / 提交后刷新 —— 先清空再拉取期间不得误闪「暂无评价」；
   * 在途只给文字行、不给骨架屏（禁的是伪内容与抖动，不是文字；页面级文字行由详情页承担）。
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

  /**
   * **按星级筛选**（1~5；`null` = 全部）：服务端过滤，选项为静态枚举（免字典）。
   * 🔴 筛选**参与分页** ⇒ 切换时必须先 `resetReviewPaging()` 再重拉，否则第 2 页之后会取到空页。
   */
  const reviewRatingFilter = ref<number | null>(null)

  /** 删除评价在途锁：删除不可逆，重复发请求会产生二次确认弹窗交错 */
  const deleting = ref(false)

  /** 切换星级筛选：重置分页 + 从第 1 页重拉（与首屏同路径） */
  function onFilterRating(next: number | null) {
    // 提交中 / 在途时忽略点击：避免与重置请求竞态（reviewGuard 会丢弃过期响应，但语义上不应受理）
    if (reviewPending.value) return
    if (reviewRatingFilter.value === next) return
    reviewRatingFilter.value = next
    resetReviewPaging()
    void fetchReviewsReset()
  }

  /** 重置式评价拉取（首屏 / 重试 / 提交后与删除后刷新共用），置 pending 门控 */
  async function fetchReviewsReset() {
    if (!dishId.value) return
    reviewPendingCount.value += 1
    try {
      await detail.fetchReviews(dishId.value, {
        pageSize: REVIEW_PAGE_SIZE,
        rating: reviewRatingFilter.value,
      })
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
      // 排序唯一时间倒序，端上不传 sort（PR-02）；星级筛选随分页传递（服务端过滤参与分页）
      const res = await detail.fetchReviews(dishId.value, {
        page: reviewPage.value + 1,
        pageSize: REVIEW_PAGE_SIZE,
        append: true,
        rating: reviewRatingFilter.value,
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
    // 归属判据与 `useDishReviewMenu.reviewMoreIsOwn` 同口径：`userInfo` 未回填（`?.id === undefined`）时一律拦截。
    // 不得写 `userStore.userInfo?.id &&` 的真值短路 —— id 为 0 或资料未回填时该短路会 falsy 放行，删除他人评价。
    if ('userId' in rv && rv.userId !== userStore.userInfo?.id) return
    uni.showModal({
      ...CONFIRM_DELETE_REVIEW,
      confirmColor: MODAL_CONFIRM_DANGER_COLOR,
      success: async (res) => {
        if (!res.confirm) return
        // 删除是唯一带不可逆后果的 UGC 操作：加并发锁，
        // 避免「弹层关闭 → 立刻再点另一条」两个 showModal 回调交错、重复发请求
        if (deleting.value) return
        deleting.value = true
        try {
          await deleteReview(rv.id)
          toastSuccess(TOAST_REVIEW_DELETED)
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
        } finally {
          deleting.value = false
        }
      },
    })
  }

  return {
    reviewList,
    reviewFailed,
    reviewPending,
    /** 当前星级筛选（`null` = 全部）；端上筛选条据此高亮 */
    reviewRatingFilter,
    resetReviewPaging,
    fetchReviewsReset,
    onFilterRating,
    onReviewsReachBottom,
    onRetryReviews,
    onDeleteReview,
  }
}