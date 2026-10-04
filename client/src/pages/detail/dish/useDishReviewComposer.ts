/**
 * 写评价编排（评价标题行右钮 → ReviewComposer 底部抽屉）。
 *
 * **无写前判定**：点按钮**直接打开空表单** —— 不调
 * 「我的评价（按菜过滤）」、不预填旧值、无「重新评价」双态；同一用户对同一菜品的
 * 重复提交由**服务端覆盖**旧评价（`POST /dishes/{id}/reviews`）。
 * 共享态仅 `dish` / `dishId`；重拉逻辑复用 `useDishReviewCore` 的
 * `fetchReviewsReset` / `resetReviewPaging`（列表与表单共用同一分页口径）。
 */
import { ref } from 'vue'
import type { Ref, ComputedRef } from 'vue'
import { useUserStore } from '@/stores/user'
import { correctionUrl } from '@/utils/routes'
import type { DishDetail } from '@/types/dish'

export function useDishReviewComposer(opts: {
  dish: ComputedRef<DishDetail | null | undefined>
  dishId: Ref<number>
  /** 本页私有的详情重拉（按页实例，刷新综合评分） */
  fetchDetail: (id: number) => Promise<void>
  fetchReviewsReset: () => Promise<void>
  resetReviewPaging: () => void
}) {
  const userStore = useUserStore()
  const { dish, dishId, fetchDetail } = opts

  const composerOpen = ref(false)

  /**
   * 打开评价表单：**无任何写前请求**（不判定「我是否已评价」、不预填）。
   * 未认证跳独立认证页，认证成功返回本页后由 onShow 续接（consumePending）重开表单。
   */
  function onOpenReviewComposer() {
    if (!dish.value) return
    if (!userStore.requireAuth(() => void onOpenReviewComposer())) return
    composerOpen.value = true
  }

  /**
   * 提交成功：端上不做「我的评价」态写回（本稿已取消双态与预填）——
   * 仅重置分页并重拉评价列表 + 刷新综合评分（重复提交由服务端覆盖同一行）。
   */
  function onReviewSubmitted() {
    opts.resetReviewPaging()
    void opts.fetchReviewsReset()
    fetchDetail(dishId.value)
  }

  /**
   * 「菜品有问题?」（信息卡名称行）：跳**独立纠错页**并携带当前菜品。
   * 落点用唯一构造函数 `correctionUrl`（禁止手拼 URL）；**免认证** ——
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
    onOpenReviewComposer,
    onReviewSubmitted,
    onCorrectDishInfo,
  }
}
