/**
 * useFeedback —— 意见反馈页（pages/feedback/index.vue）编排逻辑
 *
 * 面向小程序本身的通用反馈：类型 3 选 1（Bug / 建议 / 其他）+ 描述（≤600 字、占位随类型选择）
 * + 截图（选填，≤`UGC_IMAGE_MAX` 张）+ 本地草稿；提交 `POST /feedback`。
 * 菜品资料有误 / 已经下架走独立页面 `pages/correction/`（菜品问题反馈），本页只有一套字段、无表单形态切换。
 *
 * ⚠️ 全部逻辑在函数体内执行：由页面在 <script setup> 中同步调用，使 onLoad/onUnload/watch
 * 均在组件实例上下文中注册（模块顶层注册会报 "no active component instance"）。
 */
import { ref, reactive, computed, watch } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { createFeedback } from '@/api/feedback'
import { FEEDBACK_TYPES, type FeedbackType } from '@/types/feedback'
import { backToHome } from '@/utils/back'
import { toastError, toastInfo, toastSuccess } from '@/utils/error'
import { useRateLimitCooldown } from '@/composables/useRateLimitCooldown'

/** 描述字数上限（用户口径 600 字；服务端上限 1000，端上更严） */
export const CONTENT_MAX = 600

export function useFeedback() {
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
    return t?.placeholder ?? '先选择反馈类型，再描述你的问题'
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
  // 限频退避：后端 POST /feedback 为 2 次/分钟、10 次/小时，
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
    if (!canSubmit.value) toastInfo(gateHint.value)
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
    for (const k of Object.keys(fieldErrors)) delete fieldErrors[k]
    const keys = Object.keys(errs)
    if (!keys.length) return
    keys.forEach((k) => { fieldErrors[k] = errs[k] })
    // 「类型」无独立滚动锚点（表单首屏可见），只登记文案不定位
    scrollIntoView.value = ''
    if (keys[0] === 'form.content') setTimeout(() => { scrollIntoView.value = 'f-form-content' }, 50)
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
      toastInfo(`还有 ${Object.keys(errs).length} 项没填`)
      return
    }

    submitting.value = true
    try {
      const content = form.content.trim()
      if (content.length > CONTENT_MAX) {
        toastInfo(`内容不能超过${CONTENT_MAX}字`)
        return
      }
      const images = form.images.filter(Boolean)
      await createFeedback({
        type: form.type as FeedbackType,
        content,
        images: images.length ? images : undefined,
      })
      clearDraft()
      clearCooldown()
      toastSuccess('已提交，感谢反馈')
      resetForm()
      scheduleAutoBack()
    } catch (e) {
      // 限频：请求层已弹过 toast（内含「请 N 秒后再试」），此处只进倒计时退避、**不重复提示**
      if (!handleRateLimit(e)) {
        toastError(e, '没发出去，再试一次')
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
    backTimer = setTimeout(backToHome, 2000)
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
    /** 全页唯一返回实现（有返回栈 navigateBack；无返回栈 reLaunch 首页），手动与自动返回共用 */
    goBack: backToHome,
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
