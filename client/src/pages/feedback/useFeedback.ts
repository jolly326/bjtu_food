/**
 * useFeedback —— 意见反馈页（pages/feedback/index.vue）编排逻辑
 *
 * 页面定位（2026-09-27 与菜品纠错解耦后）：**面向小程序本身的通用反馈** ——
 * 反馈类型 3 选 1（程序功能Bug / 产品功能建议 / 其他相关问题）+ 具体描述（≤600 字、占位随类型切换）
 * + 截图（选填 ≤3 张）+ 本地草稿；提交 `POST /feedback`（type ∈ bug / suggestion / other）。
 *
 * 「菜品信息纠错」**已迁出**为独立页面 `pages/correction/`（仅菜品详情页底栏「反馈错误」进入），
 * 本页因此**只有一套字段**、无表单形态切换。
 *
 * ⚠️ 全部逻辑在函数体内执行：由页面在 <script setup> 中同步调用 useFeedback()，
 * 使 onLoad/onUnload/watch 均在组件实例上下文中注册（模块顶层注册会报 "no active component instance"）。
 */
import { ref, reactive, computed, watch } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { submitFeedback } from '@/api/feedback'
import { FEEDBACK_TYPES, type FeedbackType } from '@/types/feedback'
import { backToHome } from '@/utils/back'
import { useRateLimitCooldown } from '@/composables/useRateLimitCooldown'

/** 描述字数上限（用户口径：600 字；服务端上限仍为 1000，端上更严） */
export const CONTENT_MAX = 600

export function useFeedback() {
  /**
   * 全页唯一返回实现：有返回栈时 navigateBack；无返回栈（redirectTo 直达）才 reLaunch 首页。
   * 手动返回与成功态自动返回（scheduleAutoBack）共用本函数，不再各写一份栈判断。
   */
  function goBack() {
    if (getCurrentPages().length > 1) uni.navigateBack()
    else backToHome()
  }

  // ---- ① 表单字段（全页唯一一套） ----
  const form = reactive({
    /** 反馈类型（`''` = 未选；选中值 = FeedbackType，与后端写入值域同源） */
    type: '' as FeedbackType | '',
    /** 具体描述（必填，≤600 字） */
    content: '',
    /** 截图（选填，≤3 张 COS URL） */
    images: [] as string[],
  })

  /** 描述框占位：随选中类型切换；未选类型给通用引导（不得出现空占位） */
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
  // 限频退避（2026-09-29）：后端 POST /feedback 为 2 次/分钟、10 次/小时，
  // 弱网下手滑连点会持续撞限频、把封锁越拖越长 → 被限频后倒计时禁用并展示剩余秒数。
  const { cooldownSeconds, cooling, handleError: handleRateLimit, clearCooldown } = useRateLimitCooldown()

  const canSubmit = computed(() => !!form.type && !!form.content.trim() && !cooling())

  const gateHint = computed(() => {
    // 退避优先：让用户知道「要等」，而不是看到笼统的「先选类型 / 再写两句」
    if (cooling()) return `提交太频繁，${cooldownSeconds.value} 秒后再试`
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
    // 「类型」无独立滚动锚点（表单首屏可见），只登记文案不定位
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
    // 退避期：禁重复提交（连点只会持续撞限频、延长封锁）
    if (submitting.value || cooling()) return

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
      if (content.length > CONTENT_MAX) {
        uni.showToast({ title: `内容不能超过${CONTENT_MAX}字`, icon: 'none' })
        return
      }
      const images = form.images.filter(Boolean)
      await submitFeedback({
        type: form.type as FeedbackType,
        content,
        images: images.length ? images : undefined,
      })
      clearDraft()
      clearCooldown()
      uni.showToast({ title: '已提交，感谢反馈', icon: 'none' })
      resetForm()
      scheduleAutoBack()
    } catch (e) {
      // 限频：请求层已弹过 toast（内含「请 N 秒后再试」），此处只进倒计时退避、**不重复提示**
      if (!handleRateLimit(e)) {
        // Error 分支取 message（请求层抛 Error）；非 Error 兜底通用文案
        uni.showToast({ title: e instanceof Error && e.message ? e.message : '没发出去，再试一次', icon: 'none' })
      }
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

  // ---- ⑦ 落点参数：本页只有一种形态，进页恢复本地草稿即可 ----
  onLoad(() => {
    loadDraft()
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
    /** 限频剩余秒数（>0 时按钮禁用、gateHint 展示倒计时） */
    cooldownSeconds,
    /** 是否处于限频退避期 */
    cooling,
    clearError,
    canSubmit,
    gateHint,
    onSubmitAreaTap,
    submit,
  }
}
