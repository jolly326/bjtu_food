/**
 * useFeedback —— 意见反馈页（pages/feedback/index.vue）编排逻辑
 *
 * 页面**只有一张表单**（无页签），但**随所选类型切换字段区**（2026-09-27 用户口径 v2）：
 * · 类型 ≠ 「菜品信息纠错」⇒ 具体描述（≤600 字，占位随类型切换）+ 截图（选填 ≤1 张）；
 * · 类型 = 「菜品信息纠错」⇒ **预填纠错表单**（选菜 → 详情预填 → 只改差异项，提交独立端点）。
 *
 * 两种进入方式：
 * · 「我的」页宫格进入（`feedbackUrl()`）⇒ 类型待用户选、恢复本地草稿；
 * · 菜品详情页底栏「反馈错误」进入（`feedbackUrl('update', dishId)`）⇒ 类型**默认「菜品信息纠错」**
 *   并按 `dishId` **自动拉详情预填**（用户只改错的地方）。
 *
 * ⚠️ 全部逻辑在函数体内执行：由页面在 <script setup> 中同步调用 useFeedback()，
 * 使 onLoad/onUnload/watch 均在组件实例上下文中注册（模块顶层注册会报 "no active component instance"）。
 */
import { ref, reactive, computed, watch } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { submitFeedback, submitDishCorrection } from '@/api/feedback'
import { searchDishes, getDishDetail } from '@/api/dish'
import type { DishListItem } from '@/types/dish'
import { FEEDBACK_TYPES, type FeedbackType } from '@/types/feedback'
import { backToHome } from '@/utils/nav'
import { yuanToFen } from '@/utils/money'
import { joinLocation } from '@/utils/dish'
import { useDishAttributeStore } from '@/stores/dish-attribute'

/** 「菜品信息纠错」类型值（切换字段区、详情页深链默认选中） */
const TYPE_DISH_CORRECTION: FeedbackType = 'error'

/** 描述字数上限（用户口径 v2：600 字；服务端上限仍为 1000，端上更严） */
export const CONTENT_MAX = 600

export function useFeedback() {
  const dishAttr = useDishAttributeStore()

  /**
   * 全页唯一返回实现：有返回栈时 navigateBack；无返回栈（redirectTo 直达）才 reLaunch 首页。
   * 手动返回与成功态自动返回（scheduleAutoBack）共用本函数，不再各写一份栈判断。
   */
  function goBack() {
    if (getCurrentPages().length > 1) uni.navigateBack()
    else backToHome()
  }

  // ---- ① 单表单公共字段 ----
  const form = reactive({
    /** 反馈类型（`''` = 未选；选中值 = FeedbackType，与后端写入值域同源） */
    type: '' as FeedbackType | '',
    /** 具体描述（非纠错类型必填，≤600 字） */
    content: '',
    /** 截图（非纠错类型选填，≤1 张 COS URL） */
    images: [] as string[],
  })

  /** 是否「菜品信息纠错」类型（决定字段区：预填纠错表单 vs 描述 + 截图） */
  const isCorrection = computed(() => form.type === TYPE_DISH_CORRECTION)

  /** 描述框占位：随选中类型切换；未选类型给通用引导（纠错类型不走描述框，无占位） */
  const typePlaceholder = computed(() => {
    const t = FEEDBACK_TYPES.find((x) => x.value === form.type)
    return t?.placeholder ?? '先选一个反馈类型，再描述你遇到的问题'
  })

  function onPickType(value: FeedbackType) {
    form.type = value
    // 纠错类型：字典仅供 chips 译中文（失败静默，展示原始值）
    if (value === TYPE_DISH_CORRECTION) void dishAttr.ensureLoaded()
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

  // ---- ③ 纠错字段区（类型 = 菜品信息纠错时渲染） ----
  /**
   * 选定菜品 → 详情预填 → 用户只改差异项。
   * 金额口径：`price` 为**元字符串**（input 展示/编辑），提交时经 yuanToFen 转分（金额红线）。
   */
  const update = reactive({
    /** 搜索选定的菜品（列表行，用于选择器行展示 + 提交路径 dishId） */
    dish: null as DishListItem | null,
    /** 详情拉取中（预填未完成时禁止提交） */
    detailLoading: false,
    name: '',
    price: '',
    /** 食堂名（自由文本，预填详情 canteenName） */
    canteenName: '',
    /** 档口名（自由文本，预填详情 stallName） */
    stallName: '',
    /** 口味标签 / 食材：预填详情机器值 + 用户自由输入项（chips 增删） */
    flavorTags: [] as string[],
    ingredients: [] as string[],
    /** 图片：预填菜品现有图（详情 images）+ 用户新增/删除 */
    images: [] as string[],
  })

  const dishSheetOpen = ref(false)
  const dishKeyword = ref('')
  const dishCandidates = ref<DishListItem[]>([])
  const dishSearched = ref(false)
  /** 搜索请求序号：快速输入/连续触发时丢弃过期响应（竞态守卫） */
  let searchSeq = 0

  function openDishSheet() {
    dishSheetOpen.value = true
  }

  function closeDishSheet() {
    dishSheetOpen.value = false
  }

  /** ListPickerSheet 候选行（名称 + 档口简洁结果） */
  const dishPickerOptions = computed(() =>
    dishCandidates.value.map((d) => ({
      key: String(d.id),
      label: d.name,
      sub: joinLocation(d.canteen, d.stallName),
      image: d.coverImage || '',
    })),
  )

  /** 由 ListPickerSheet 内部防抖 emit('search', kw) 驱动；复用 `GET /dishes?keyword=` */
  function onDishSearchKw(kw: string) {
    dishKeyword.value = kw
    if (!kw.trim()) {
      dishSearched.value = false
      dishCandidates.value = []
      searchSeq++
      return
    }
    const seq = ++searchSeq
    dishSearched.value = true
    searchDishes({ keyword: kw.trim(), page: 1, pageSize: 8 })
      .then((list) => {
        if (seq !== searchSeq) return // 已有更新的搜索发出，丢弃本次过期结果
        dishCandidates.value = list
      })
      .catch((err) => {
        if (seq !== searchSeq) return
        console.error('[feedback] 搜索菜品失败', err)
        dishCandidates.value = []
      })
  }

  function onDishPick(opt: { key: string }) {
    const d = dishCandidates.value.find((x) => String(x.id) === opt.key)
    if (d) selectDish(d)
  }

  /** 选中菜品：关闭弹窗 → 拉详情预填（用户只改错的地方） */
  function selectDish(d: DishListItem) {
    update.dish = d
    dishKeyword.value = ''
    dishCandidates.value = []
    dishSearched.value = false
    dishSheetOpen.value = false
    void loadDishDetail(d.id)
  }

  /** 重选：回到选择步骤（清空预填） */
  function resetDish() {
    update.dish = null
    update.name = ''
    update.price = ''
    update.canteenName = ''
    update.stallName = ''
    update.flavorTags = []
    update.ingredients = []
    update.images = []
    dishKeyword.value = ''
    dishCandidates.value = []
    dishSearched.value = false
    clearError('update.dish')
  }

  /** 拉详情并预填（字段值来自 `GET /dishes/{id}`）；深链 dishId 进入时由本函数统一补齐菜品行 */
  async function loadDishDetail(id: number) {
    update.detailLoading = true
    try {
      const d = await getDishDetail(id)
      update.dish = {
        id: d.id,
        name: d.name,
        price: d.price,
        originalPrice: d.originalPrice,
        coverImage: d.image || '',
        rating: d.rating,
        canteen: d.canteen,
        stallName: d.stallName,
      }
      update.name = d.name || ''
      update.price = d.price > 0 ? String(d.price) : '' // 详情 price 已由 API 层分→元
      update.canteenName = d.canteen || ''
      update.stallName = d.stallName || ''
      update.flavorTags = [...(d.flavorTags || [])]
      update.ingredients = [...(d.ingredients || [])]
      // 图片上限统一为 1 张（用户口径）：预填只取首图，用户可替换
      update.images = [...(d.images || [])].slice(0, 1)
      clearError('update.dish')
    } catch (err) {
      // 预填失败：保留已选菜品行（可重选），其余字段保持空
      console.error('[feedback] 菜品详情加载失败', err)
      uni.showToast({ title: '菜品信息加载失败，请重选', icon: 'none' })
    } finally {
      update.detailLoading = false
    }
  }

  // ---- ④ 提交门禁（canSubmit 置灰；置灰点击由外层热区兜底 toast） ----
  const PRICE_PATTERN = /^(?:\d+)(?:\.\d{1,2})?$/

  function priceValid(): boolean {
    const p = update.price.trim()
    if (!p) return false
    const n = Number(p)
    return PRICE_PATTERN.test(p) && Number.isFinite(n) && n > 0 && n <= 9999
  }

  const canSubmit = computed(() => {
    if (!isCorrection.value) return !!form.type && !!form.content.trim()
    return (
      !!update.dish &&
      !update.detailLoading &&
      !!update.name.trim() &&
      priceValid() &&
      !!update.canteenName.trim() &&
      !!update.stallName.trim()
    )
  })

  /** 置灰点击提示文案：按当前字段区缺失项优先给出 */
  const gateHint = computed(() => {
    if (!isCorrection.value) {
      if (!form.type) return '先选一个反馈类型'
      if (!form.content.trim()) return '再写两句，描述一下问题'
      return ''
    }
    if (!update.dish) return '先选一道菜'
    if (update.detailLoading) return '菜品信息加载中'
    if (!update.name.trim()) return '菜名叫啥？填一下'
    if (!priceValid()) return '价格要像 12.5 这样'
    if (!update.canteenName.trim()) return '填一下食堂名'
    if (!update.stallName.trim()) return '填一下档口'
    return ''
  })

  /** 置灰态点击提示：AppButton 在 disabled 时不 emit press，由 .submit-area 外层热区兜底 */
  function onSubmitAreaTap() {
    if (!canSubmit.value) uni.showToast({ title: gateHint.value, icon: 'none' })
  }

  // ---- ⑤ 字段级错误 + 提交中状态 ----
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
    const idMap: Record<string, string> = {
      'form.content': 'f-form-content',
      'update.dish': 'f-up-dish',
      'update.name': 'f-up-name',
      'update.price': 'f-up-price',
      'update.canteenName': 'f-up-canteenName',
      'update.stallName': 'f-up-stallName',
    }
    const target = idMap[keys[0]] || ''
    scrollIntoView.value = ''
    if (target) setTimeout(() => { scrollIntoView.value = target }, 50)
  }

  // ---- ⑥ 提交（防重复；成功 Toast「已提交，感谢反馈」+ 2 秒自动返回） ----
  function resetForm() {
    form.type = ''
    form.content = ''
    form.images = []
    resetDish()
    dishSheetOpen.value = false
  }

  async function submit() {
    if (submitting.value) return

    const errs: Record<string, string> = {}
    if (!isCorrection.value) {
      if (!form.type) errs['form.type'] = '先选一个反馈类型'
      if (!form.content.trim()) errs['form.content'] = '再写两句，描述一下问题'
    } else {
      if (!update.dish) errs['update.dish'] = '先选一道菜'
      else if (update.detailLoading) errs['update.dish'] = '菜品信息加载中'
      else {
        if (!update.name.trim()) errs['update.name'] = '菜名叫啥？填一下'
        if (!priceValid()) errs['update.price'] = '价格要像 12.5 这样'
        if (!update.canteenName.trim()) errs['update.canteenName'] = '填一下食堂名'
        if (!update.stallName.trim()) errs['update.stallName'] = '填一下档口'
      }
    }

    if (Object.keys(errs).length) {
      markErrors(errs)
      uni.showToast({ title: `还有 ${Object.keys(errs).length} 项没填`, icon: 'none' })
      return
    }

    submitting.value = true
    try {
      if (!isCorrection.value) {
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
      } else {
        // 纠错端点：dishId 在路径中，请求体七字段平铺；price 元 → 分（金额红线）；
        // 公开可提交（匿名允许）；菜品不存在 → 4001，敏感词 → 400 message 直透。
        await submitDishCorrection(update.dish!.id, {
          name: update.name.trim(),
          price: yuanToFen(Number(update.price.trim())),
          canteenName: update.canteenName.trim(),
          stallName: update.stallName.trim(),
          flavorTags: update.flavorTags.filter(Boolean),
          ingredients: update.ingredients.filter(Boolean),
          images: update.images.filter(Boolean),
        })
      }
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

  // ---- ⑦ 成功态自动返回（用户 2 秒内无输入则 navigateBack） ----
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

  // ---- ⑧ 落点参数 ----
  // · 默认（无参）：恢复本地草稿，类型待用户选；
  // · `mode=update`（菜品详情页「反馈错误」跳转）：类型默认「菜品信息纠错」+ 按 dishId **自动预填**。
  onLoad((opts?: Record<string, string>) => {
    loadDraft()
    if (opts?.mode === 'update') {
      form.type = TYPE_DISH_CORRECTION
      void dishAttr.ensureLoaded()
      const dishId = Number(opts?.dishId ?? 0)
      if (dishId > 0) {
        loadDishDetail(dishId).catch((err) => {
          console.error('[feedback] 深链菜品详情加载失败', err)
          uni.showToast({ title: '菜品信息加载失败，可手动选择菜品', icon: 'none' })
        })
      }
    }
  })

  /** 供页面模板/模板回调使用的全部编排绑定 */
  return {
    goBack,
    form,
    isCorrection,
    typePlaceholder,
    onPickType,
    update,
    dishSheetOpen,
    dishKeyword,
    dishPickerOptions,
    dishSearched,
    openDishSheet,
    closeDishSheet,
    onDishSearchKw,
    onDishPick,
    resetDish,
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
