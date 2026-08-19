/**
 * 举报逻辑公共 hook（useReport）。
 *
 * 原先 openReport / submitReport / reportOpen / reportSubmitting / reportTarget 在
 * community、dish、moment、review-list、my-moments 等 5 处页面逐字复制，现统一收敛到
 * 本 hook，消除重复代码并保证行为一致（requireAuth 前置 → ReportModal 弹窗 → submitFeedback）。
 */
import { ref, type Ref } from 'vue'
import { submitFeedback } from '@/api/feedback'
import { useUserStore } from '@/stores/user'

export interface UseReportOptions {
  /** 举报对象类型：'moment' | 'dish' | 'review' 等，随 submitFeedback.relatedType 使用 */
  type: 'moment' | 'dish' | 'stall' | 'canteen' | 'review' | string
  /** 举报弹窗标题，如「举报动态」 */
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
  /** 打开举报弹窗；未登录时自动触发 requireAuth 引导 */
  openReport: (targetId: number) => void
  /** 提交举报；text 为空时提示并中断 */
  submitReport: (text: string) => Promise<void>
}

export function useReport(options: UseReportOptions): UseReportReturn {
  const reportOpen = ref(false)
  const reportSubmitting = ref(false)
  const reportTargetId = ref<number | null>(null)
  const userStore = useUserStore()

  function openReport(targetId: number) {
    if (!userStore.requireAuth(() => openReport(targetId))) return
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
