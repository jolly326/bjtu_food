/**
 * 菜品详情态（**按页实例**，脱离全局 store）。
 *
 * <p><b>为什么按页</b>：详情态此前挂在 pinia 全局单例（`currentDish` / `reviewList` 等）上，
 * 一旦出现「详情页叠详情页」或快速返回，两页会互相覆盖全局态。本 composable 在
 * `useDishPage()` 内创建**一份本页私有**的响应式状态与竞态守卫，由本页的
 * `useDishReviewCore` / `useDishReviewComposer` 经参数注入共享 ⇒ 根除跨页串态。
 *
 * <p><b>保留竞态语义</b>：用 `createSeqGuard` 在「重置式重拉 / 翻页 / 切菜品」交错时丢弃过期响应，
 * 与抽取前的 store 实现逐字等价。
 */
import { ref } from 'vue'
import * as dishApi from '@/api/dish'
import * as reviewApi from '@/api/review'
import { isResourceNotFound } from '@/api/http'
import { REVIEW_PAGE_SIZE } from '@/constants/paging'
import { createSeqGuard, mergePagedRows } from '@/composables/usePagedList'
import type { DishDetail } from '@/types/dish'
import type { Review } from '@/types/review'

export function useDishDetail() {
  const currentDish = ref<DishDetail | null>(null)
  /** 详情首屏/刷新是否失败（失败 ≠ 加载中 ≠ 不存在）：供页面区分「静默加载中」与「请求失败」 */
  const detailError = ref(false)
  /** 菜品**不存在**（后端 4001）：与「请求失败」互斥，**不可重试** */
  const detailNotFound = ref(false)
  const reviewList = ref<Review[]>([])
  /** 评价首屏/刷新是否失败（失败 ≠ 零评价） */
  const reviewError = ref(false)

  /** 详情请求序号守卫：切菜品 / 重拉时丢弃过期响应，避免旧菜晚到覆盖新详情 */
  const detailGuard = createSeqGuard()
  /** 评价请求序号守卫：翻页 / 换菜时丢弃过期响应，防触底 append 与 reset 交错 */
  const reviewGuard = createSeqGuard()

  async function fetchDetail(id: number) {
    const seq = detailGuard.begin()
    try {
      const detail = await dishApi.getDishDetail(id)
      // 过期响应（期间已切到另一菜品 / 已重置）：丢弃，不覆盖最新详情
      if (!detailGuard.isCurrent(seq)) return
      currentDish.value = detail
      detailError.value = false
      detailNotFound.value = false
    } catch (e) {
      if (!detailGuard.isCurrent(seq)) return
      console.error('加载菜品详情失败', e)
      currentDish.value = null
      // 4001（资源不存在，R8）→ 不存在态（不可重试）；其余（网络 / 5xx）→ 失败态（可重试）
      detailNotFound.value = isResourceNotFound(e)
      detailError.value = !detailNotFound.value
    }
  }

  /** 进入新菜品前清空旧详情与评价态，避免闪现上一道菜（本页状态残留） */
  function resetDishDetail() {
    // 使所有在途详情 / 评价请求失效：旧菜品的响应晚到时不再写入新菜品状态（竞态守卫）
    detailGuard.invalidate()
    reviewGuard.invalidate()
    currentDish.value = null
    detailError.value = false
    detailNotFound.value = false
    reviewList.value = []
    reviewError.value = false
  }

  /**
   * 本地移除一条评价（删除成功后对齐列表，避免为一行变化重拉整页）。
   * 「评价已不存在」（后端 4001）时同样走它 —— 该码重试无意义，按已删除收尾。
   */
  function removeReview(id: number) {
    reviewList.value = reviewList.value.filter((x) => x.id !== id)
  }

  /**
   * 评价区分页（RESTful 子资源 `GET /dishes/{id}/reviews`，排序唯一为时间倒序）。
   *
   * **契约（唯一）**：过期 / 失败一律返回 `null` —— 调用方据此跳过分页推进（避免永久跳过该页）。
   * 分页壳只有 `records`，**到底判据 = 本页返回条数 < `pageSize`**（由调用方判定）。
   */
  async function fetchReviews(
    dishId: number,
    options?: { page?: number; pageSize?: number; append?: boolean },
  ): Promise<{ list: Review[] } | null> {
    const seq = reviewGuard.begin()
    const page = options?.page ?? 1
    const pageSize = options?.pageSize ?? REVIEW_PAGE_SIZE
    try {
      const res = await reviewApi.listDishReviews(dishId, { page, pageSize })
      // 过期响应（期间又有新请求发起 / resetDishDetail 已切菜品）：丢弃，不覆盖最新列表
      if (!reviewGuard.isCurrent(seq)) return null
      if (options?.append) {
        // 去重追加（单一真源 mergePagedRows）：与 usePagedList 同口径，防分页跳号重复行
        reviewList.value = mergePagedRows(reviewList.value, res.list, pageSize).rows
      } else {
        reviewList.value = res.list
      }
      reviewError.value = false
      return res
    } catch (e) {
      console.error('加载评价失败', e)
      if (!reviewGuard.isCurrent(seq)) return null
      if (!options?.append) {
        reviewList.value = []
        reviewError.value = true
      }
      return null
    }
  }

  return {
    currentDish, detailError, detailNotFound, reviewList, reviewError,
    fetchDetail, resetDishDetail, removeReview, fetchReviews,
  }
}

export type DishDetailState = ReturnType<typeof useDishDetail>
