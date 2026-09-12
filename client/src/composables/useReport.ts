/**
 * 举报逻辑公共 hook（useReport）。
 *
 * 原 openReport 前置 requireAuth（举报被挡在认证后），client-auth-boundary 修订：
 * 举报属免认证行为，游客可直接填写提交，不再弹 AuthSheet。
 */
import { ref, type Ref } from 'vue'
import { submitFeedback } from '@/api/feedback'

export interface UseReportOptions {
  /** 举报对象类型：现网为 'review'（菜品详情的评价），随 submitFeedback.relatedType 使用 */
  type: 'review' | string
  /** 举报弹窗标题，如「举报评价」 */
  title?: string
  /** 举报弹窗占位提示 */
  placeholder?: string
  /** 提交成功后的 Toast 文案 */
  successText?: string
}

export interface UseReportReturn {
  reportOpen: Ref<boolean>
  reportSubmitting: Ref<boolean>
  reportTargetId: Ref<number | null>
  /** 打开举报弹窗（游客可直达，无需认证） */
  openReport: (targetId: number) => void
  /** 提交举报；text 为空时提示并中断 */
  submitReport: (text: string) => Promise<void>
}

export function useReport(options: UseReportOptions): UseReportReturn {
  const reportOpen = ref(false)
  const reportSubmitting = ref(false)
  const reportTargetId = ref<number | null>(null)

  function openReport(targetId: number) {
    reportTargetId.value = targetId
    reportOpen.value = true
  }

  async function submitReport(text: string) {
    const targetId = reportTargetId.value
    if (targetId == null) return
    if (!text) {
      uni.showToast({ title: '请填写举报原因', icon: 'none' })
      return
    }
    reportSubmitting.value = true
    try {
      await submitFeedback({
        type: 'report',
        content: text,
        relatedType: options.type,
        relatedId: targetId,
      })
      uni.showToast({ title: options.successText || '举报已提交', icon: 'success' })
      reportOpen.value = false
    } catch (e: any) {
      uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
    } finally {
      reportSubmitting.value = false
    }
  }

  return { reportOpen, reportSubmitting, reportTargetId, openReport, submitReport }
}
