/**
 * 菜品反馈业务 Hook（点赞 / 纠错）。
 * 聚合 429 退避、点赞 toggle、纠错提交（含本地「已反馈过」软提示）、成功提示状态。
 * 页面/组件通过此 Hook 统一处理反馈交互，避免在组件内部重复实现。
 */

import { ref } from 'vue'

import { dishApi, issueApi } from '@/api'
import type { DishVO } from '@/api'
import { useCooldown, cooldownFrom } from '@/utils/backoff'
import { hasSubmitted, markSubmitted } from '@/utils/issueLocal'

export interface UseDishFeedbackOptions {
  dish: DishVO | null
  onLikeChange?: (payload: { id: number; likeCount: number; liked: boolean }) => void
  /** 纠错提交成功后的回调（弹层收起等由组件自行处理） */
  onIssueSubmit?: () => void
}

function toast(title: string): void {
  uni.showToast({ title, icon: 'none', duration: 3000 })
}

export function useDishFeedback(options: UseDishFeedbackOptions) {
  const { dish, onLikeChange, onIssueSubmit } = options

  const likeCount = ref(0)
  const liked = ref(false)
  const likeBusy = ref(false)
  const issueBusy = ref(false)

  const { locked: likeCooling, left: likeLeft } = useCooldown('like')
  const { locked: issueCooling, left: issueLeft } = useCooldown('issue')

  /** 两态点赞 toggle（冷却时轻提示）；返回点赞后的状态，供组件触发 Q 弹 */
  async function toggleLike(): Promise<boolean> {
    if (!dish || likeBusy.value) return liked.value
    if (likeCooling.value) {
      toast(`操作过于频繁，请 ${likeLeft.value} 秒后再试`)
      return liked.value
    }

    likeBusy.value = true
    try {
      const result = await dishApi.like(dish.id)
      likeCount.value = result.likeCount
      liked.value = result.liked
      onLikeChange?.({
        id: result.id,
        likeCount: result.likeCount,
        liked: result.liked,
      })
      toast(result.liked ? '已点赞' : '已取消')
      return result.liked
    } catch (error) {
      const wait = cooldownFrom('like', error)
      toast(
        wait > 0
          ? `操作过于频繁，请 ${wait} 秒后再试`
          : (error as Error).message || '操作失败，请重试',
      )
      return liked.value
    } finally {
      likeBusy.value = false
    }
  }

  /** 提交纠错：本地已提交过的组合直接软提示并阻断（后端不做去重幂等） */
  async function submitIssue(optionId: number): Promise<boolean> {
    if (!dish || issueBusy.value) return false
    if (hasSubmitted(dish.id, optionId)) {
      toast('这条反馈你已经提交过啦')
      return false
    }
    if (issueCooling.value) {
      toast(`操作过于频繁，请 ${issueLeft.value} 秒后再试`)
      return false
    }

    issueBusy.value = true
    try {
      await issueApi.submit(dish.id, optionId)
      markSubmitted(dish.id, optionId)
      toast('已提交，感谢反馈')
      onIssueSubmit?.()
      return true
    } catch (error) {
      const wait = cooldownFrom('issue', error)
      toast(
        wait > 0
          ? `操作过于频繁，请 ${wait} 秒后再试`
          : (error as Error).message || '提交失败，请重试',
      )
      return false
    } finally {
      issueBusy.value = false
    }
  }

  /** 弹窗打开 / 换菜时同步点赞态 */
  function syncFromDish(source: DishVO): void {
    likeCount.value = source.likeCount
    liked.value = source.liked
  }

  // 只导出组件真正消费的项（`likeCount` / `liked` / 冷却剩余秒数在 Hook 内部用于提示与状态）
  return {
    likeBusy,
    issueBusy,
    likeCooling,
    issueCooling,
    toggleLike,
    submitIssue,
    syncFromDish,
  }
}
