/**
 * 写评价 / 重新评价编排（底栏左钮 → ReviewComposer 底部抽屉）。
 *
 * 「我是否已评价」的判定时机在 **打开表单前**（onOpenReviewComposer）：已认证用户先调
 * 「我的评价（按菜过滤）」——已评价则以重评模式（预填旧值）打开，未评价打开空表单；
 * 判定失败静默按未评价处理。
 * 共享态 `myReview` / `imageOnly` 由 `useDishPage` 拥有并注入；重拉逻辑复用 `useDishReviewCore` 的
 * `fetchReviewsReset` / `resetReviewPaging`（列表与表单共用同一分页口径）。
 */
import { ref, computed } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import { useUserStore } from '@/stores/user'
import { useDishStore } from '@/stores/dish'
import { getMyReviews } from '@/api/review'
import { correctionUrl } from '@/utils/routes'
import type { MyReview, ReviewSubmittedPayload } from '@/types/review'
import type { DishDetail } from '@/types/dish'

export function useDishReviewComposer(opts: {
  dish: ComputedRef<DishDetail | null | undefined>
  dishId: Ref<number>
  myReview: Ref<MyReview | null>
  fetchReviewsReset: () => Promise<void>
  resetReviewPaging: () => void
}) {
  const userStore = useUserStore()
  const dishStore = useDishStore()
  const { dish, dishId, myReview } = opts

  const composerOpen = ref(false)
  /** 底栏主按钮文案：已评价 = 重新评价，未评价 = 写评价（游客恒为「写评价」） */
  const reviewButtonText = computed(() => (myReview.value ? '重新评价' : '写评价'))
  /** 重评预填（评分 / 文字 / 配图）；未评价为 null */
  const composerPrefill = computed(() => {
    const rv = myReview.value
    if (!rv) return null
    return { rating: rv.rating, content: rv.content, images: rv.images ?? [] }
  })
  /** 重评目标评价 ID；未评价为 null（走首次发表 POST） */
  const composerReviewId = computed(() => myReview.value?.id ?? null)

  /** 拉取「我的评价（按菜过滤）」：判定是否已评价，取回评价 ID 与旧值供重评预填 */
  async function loadMyReview() {
    if (!dishId.value) return
    try {
      const res = await getMyReviews({ dishId: dishId.value, page: 1, pageSize: 1 })
      myReview.value = res.list[0] ?? null
    } catch {
      // 判定失败静默降级为「未评价」，不阻塞详情展示
      myReview.value = null
    }
  }

  /**
   * 打开评价表单：先判定「我是否已评价」（见上）。
   * 与删除同款：未认证跳独立认证页，认证成功返回本页后由 onShow 续接（consumePending）。
   */
  async function onOpenReviewComposer() {
    if (!dish.value) return
    if (!userStore.requireAuth(() => void onOpenReviewComposer())) return
    await loadMyReview()
    composerOpen.value = true
  }

  /**
   * 提交成功：两种模式均**本地写回** myReview（底栏就地切为/保持「重新评价」，不回读接口）——
   * - 重评：载荷携带本人评价 ID，原行更新新值；
   * - 首次发表：载荷携带 POST 出参返回的新评价 ID，本地构造「我的评价」；
   * 两种情况均重置分页并按当前「只看有图」口径重拉评价 + 刷新综合评分。
   */
  function onReviewSubmitted(payload: ReviewSubmittedPayload) {
    myReview.value = {
      id: payload.reviewId,
      rating: payload.rating,
      content: payload.content,
      createdAt: new Date().toISOString(),
      images: payload.images,
      // 本人视角专属两字段（本地写回沿用当前菜品上下文）
      dishId: dishId.value,
      dishName: dish.value?.name ?? '',
    }
    opts.resetReviewPaging()
    void opts.fetchReviewsReset()
    dishStore.fetchDetail(dishId.value)
  }

  /**
   * 底栏「反馈错误」：跳反馈页「更新信息」模式并**预选本菜品**
   * （`update` 模式带 dishId ⇒ 进页即拉详情预填七字段表单，跳过搜索步骤）。
   * 落点用唯一构造函数 `feedbackUrl`（禁止手拼 URL）；**免认证** ——
   * `POST /dishes/{id}/correction` 属公开写，游客同样可直达，故不经 `requireAuth`。
   */
  function onCorrectDishInfo() {
    if (!dishId.value) return
    uni.navigateTo({
      url: correctionUrl(dishId.value),
    })
  }

  return {
    composerOpen,
    reviewButtonText,
    composerPrefill,
    composerReviewId,
    onOpenReviewComposer,
    onReviewSubmitted,
    onCorrectDishInfo,
  }
}
