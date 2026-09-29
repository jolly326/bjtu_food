/**
 * 举报逻辑 hook（useReport）——菜品详情页包内私有编排（仅本页使用，就近置于页面包）。
 *
 * 举报属免认证行为，游客可直接填写提交。
 * 写入口 = `POST /reviews/{id}/report`（RESTful 子资源：举报对象为被举报的评价 ID）。
 */
import { ref, type Ref } from 'vue'
import { reportReview } from '@/api/feedback'

export interface UseReportOptions {
  /** 提交成功后的 Toast 文案 */
  successText?: string
}

export interface UseReportReturn {
  reportOpen: Ref<boolean>
  reportSubmitting: Ref<boolean>
  /** 打开举报弹窗（游客可直达，无需认证）；targetId = 被举报的评价 ID */
  openReport: (targetId: number) => void
  /** 提交举报：reasonValue = 弹层单选的举报原因机器值（字典下发项） */
  submitReport: (reasonValue: string) => Promise<void>
}

export function useReport(options: UseReportOptions = {}): UseReportReturn {
  const reportOpen = ref(false)
  const reportSubmitting = ref(false)
  const reportTargetId = ref<number | null>(null)

  function openReport(targetId: number) {
    reportTargetId.value = targetId
    reportOpen.value = true
  }

  async function submitReport(reasonValue: string) {
    const targetId = reportTargetId.value
    if (targetId == null) return
    if (!reasonValue) {
      uni.showToast({ title: '请选择举报原因', icon: 'none' })
      return
    }
    reportSubmitting.value = true
    try {
      await reportReview(targetId, { reason: reasonValue })
      uni.showToast({ title: options.successText || '举报已提交', icon: 'success' })
      reportOpen.value = false
    } catch (e) {
      uni.showToast({ title: (e as Error)?.message || '提交失败', icon: 'none' })
    } finally {
      reportSubmitting.value = false
    }
  }

  return { reportOpen, reportSubmitting, openReport, submitReport }
}
