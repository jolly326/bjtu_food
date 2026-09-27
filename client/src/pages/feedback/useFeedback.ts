/**
 * useFeedback —— 意见反馈页（pages/feedback/index.vue）编排逻辑
 *
 * 页面**只有一张表单**（2026-09-27 改版，用户口径）：
 * ① 反馈类型（4 选 1 竖排单选）→ ② 具体描述（占位随类型切换）→ ③ 截图（选填 ≤1 张）+ 本地草稿；
 * 提交 `POST /feedback`，`type` ∈ bug / suggestion / error / other（服务端 WRITABLE_TYPES 校验）。
 *
 * **两种进入方式只差一个默认值**：
 * · 「我的」页宫格进入（`feedbackUrl()`）⇒ 类型空、等用户选（恢复本地草稿）；
 * · 菜品详情页底栏「反馈错误」进入（`feedbackUrl('update', dishId)`）⇒ 类型**默认勾选「菜品信息纠错」**
 *   （描述框占位随之切换；菜品由用户按占位提示自行写明）。页面**无页签、无模式切换入口**。
 *
 * ⚠️ 全部逻辑在函数体内执行：由页面在 <script setup> 中同步调用 useFeedback()，
 * 使 onLoad/onUnload/watch 均在组件实例上下文中注册（模块顶层注册会报 "no active component instance"）。
 */
import { ref, reactive, computed, watch } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { submitFeedback } from '@/api/feedback'
import type { FeedbackType } from '@/types/feedback'
import { backToHome } from '@/utils/nav'

/** 「菜品信息纠错」类型值（详情页「反馈错误」跳入时的默认选中项） */
const TYPE_DISH_CORRECTION: FeedbackType = 'error'

export function useFeedback() {
  /**
   * 全页唯一返回实现：有返回栈时 navigateBack；无返回栈（redirectTo 直达）才 reLaunch 首页。
   * 手动返回与成功态自动返回（scheduleAutoBack）共用本函数，不再各写一份栈判断。
   */
  function goBack() {
    if (getCurrentPages().length > 1) uni.navigateBack()
    else backToHome()
  }

  // ---- ① 表单字段 ----
  const form = reactive({
    /** 反馈类型（`''` = 未选；选中值 = FeedbackType，与后端写入值域同源） */
    type: '' as FeedbackType | '',
    /** 具体描述（必填，≤1000 字） */
    content: '',
    /** 截图（选填，≤1 张 COS URL） */
    images: [] as string[],
  })

  /** 描述框占位：随选中类型切换；未选类型时给通用引导（不得出现空占位） */
  const typePlaceholder = computed(() => {
    const t = FEEDBACK_TYPES.find((x) => x.value === form.type)
    return t?.placeholder ?? '先选一个反馈类型，再描述你遇到的问题'
  })

  function onPickType(value: FeedbackType) {
    form.type = value
  }

  // ---- ② 本地草稿（只存「类型 + 描述」；图片是 COS 地址、重进可能失效，故不缓存） ----
  const DRAFT_KEY = 'feedback_draft'

  function loadDraft() {
    try {
      const raw = uni.getStorageSync(DRAFT_KEY) as { type?: FeedbackType; content?: string } | ''
      if (raw && typeof raw === 'object') {
        form.type = raw.type ?? ''
        form.content = typeof raw.content === 'string' ? raw.content : ''
      }
    } catch {
      /* 读取异常：静默（草稿是增强能力，失败不影响填写） */
    }
  }

  function saveDraft() {
    try {
      uni.setStorageSync(DRAFT_KEY, { type: form.type, content: form.content })
    } catch {
      /* 写入异常：静默 */
    }
  }

  function clearDraft() {
    try {
      uni.removeStorageSync(DRAFT_KEY)
    } catch {
      /* 清除异常：静默 */
    }
  }

  watch(() => [form.type, form.content], saveDraft)

  // ---- ③ 提交门禁（canSubmit 置灰；置灰点击由外层热区兜底 toast） ----
  const canSubmit = computed(() => !!form.type && !!form.content.trim())

  /** 置灰点击提示文案：按缺失项优先给出 */
  const gateHint = computed(() => {
    if (!form.type) return '先选一个反馈类型'
    if (!form.content.trim()) return '再写两句，描述一下问题'
    return ''
  })

  /** 置灰态点击提示：AppButton 在 disabled 时不 emit press，由 .submit-area 外层热区兜底 */
  function onSubmitAreaTap() {
    if (!canSubmit.value) uni.showToast({ title: gateHint.value, icon: 'none' })
  }

  // ---- ④ 字段级错误 + 提交中状态 ----
  const fieldErrors = reactive<Record<string, string>>({})
  const scrollIntoView = ref('')
  const submitting = ref(false)

  function clearError(key: string) {
    delete fieldErrors[key]
    cancelAutoBack()
  }

  function markErrors(errs: Record<string, string>) {
    Object.keys(fieldErrors).forEach((k) => delete fieldErrors[k])
    const keys = Object.keys(errs)
    if (!keys.length) return
    keys.forEach((k) => { fieldErrors[k] = errs[k] })
    // 「类型」无独立滚动锚点（在表单首屏可见），只登记文案不定位
    const target = keys[0] === 'form.content' ? 'f-form-content' : ''
    scrollIntoView.value = ''
    if (target) setTimeout(() => { scrollIntoView.value = target }, 50)
  }

  // ---- ⑤ 提交（防重复；成功 Toast「已提交，感谢反馈」+ 2 秒自动返回） ----
  function resetForm() {
    form.type = ''
    form.content = ''
    form.images = []
  }

  async function submit() {
    if (submitting.value) return

    const errs: Record<string, string> = {}
    if (!form.type) errs['form.type'] = '先选一个反馈类型'
    if (!form.content.trim()) errs['form.content'] = '再写两句，描述一下问题'

    if (Object.keys(errs).length) {
      markErrors(errs)
      uni.showToast({ title: `还有 ${Object.keys(errs).length} 项没填`, icon: 'none' })
      return
    }

    submitting.value = true
    try {
      const content = form.content.trim()
      if (content.length > 1000) {
        uni.showToast({ title: '内容不能超过1000字', icon: 'none' })
        return
      }
      const images = form.images.filter(Boolean)
      await submitFeedback({
        type: form.type as FeedbackType,
        content,
        images: images.length ? images : undefined,
      })
      clearDraft()
      // 成功反馈 + 返回（口径按拍板：固定 Toast 文案）
      uni.showToast({ title: '已提交，感谢反馈', icon: 'none' })
      resetForm()
      scheduleAutoBack()
    } catch (e) {
      // Error 分支取 message（请求层抛 Error）；非 Error 兜底通用文案
      uni.showToast({ title: e instanceof Error && e.message ? e.message : '没发出去，再试一次', icon: 'none' })
    } finally {
      submitting.value = false
    }
  }

  // ---- ⑥ 成功态自动返回（用户 2 秒内无输入则 navigateBack） ----
  let backTimer: ReturnType<typeof setTimeout> | null = null
  onUnload(() => {
    if (backTimer) clearTimeout(backTimer)
  })

  function scheduleAutoBack() {
    if (backTimer) clearTimeout(backTimer)
    backTimer = setTimeout(goBack, 2000)
  }

  function cancelAutoBack() {
    if (backTimer) {
      clearTimeout(backTimer)
      backTimer = null
    }
  }

  // ---- ⑦ 落点参数 ----
  // · 默认（无参 / mode=issue）：恢复本地草稿，类型待用户选；
  // · mode=update（菜品详情页「反馈错误」跳转）：**同一张表单**，仅把类型默认勾成「菜品信息纠错」
  //   （`dishId` 不再用于预填 —— 表单不需要拉详情；菜品由用户按描述框占位提示写明）。
  onLoad((opts?: Record<string, string>) => {
    loadDraft()
    if (opts?.mode === 'update') form.type = TYPE_DISH_CORRECTION
  })

  /** 供页面模板/模板回调使用的全部编排绑定 */
  return {
    goBack,
    form,
    typePlaceholder,
    onPickType,
    fieldErrors,
    scrollIntoView,
    submitting,
    clearError,
    canSubmit,
    gateHint,
    onSubmitAreaTap,
    submit,
  }
}
