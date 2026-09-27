/**
 * useFeedback —— 意见反馈页（pages/feedback/index.vue）编排逻辑
 *
 * 页面两种进入方式（2026-09-27 改版：**页面内不再有页签**）：
 * ① 默认（「我的」页宫格进入）：**单表单** —— 反馈类型（4 选 1）+ 具体描述 + 截图（≤1 张）；
 *    提交 `POST /feedback`，`type` ∈ bug / suggestion / error / other（服务端 WRITABLE_TYPES 校验）。
 *    草稿本地缓存（仅「类型 + 描述」，图片不缓存 —— 地址重进可能失效）。
 * ② `mode=update`（菜品详情页底栏「反馈错误」跳入，含 `dishId`）：仍是**自动填好这道菜**的纠错表单
 *    （选菜 → 拉详情预填七字段 → 只改差异项），提交独立端点 `POST /dishes/{id}/correction`。
 *    该形态**无页签、也不在页面上暴露切换入口**，普通用户不会看到。
 *
 * ⚠️ 全部逻辑在函数体内执行：由页面在 <script setup> 中同步调用 useFeedback()，
 * 使 onLoad/onUnload/watch 均在组件实例上下文中注册（模块顶层注册会报 "no active component instance"）。
 */
import { ref, reactive, computed, watch } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { submitFeedback, submitDishCorrection } from '@/api/feedback'
import { searchDishes, getDishDetail } from '@/api/dish'
import type { DishListItem } from '@/types/dish'
import type { FeedbackType } from '@/types/feedback'
import { backToHome } from '@/utils/nav'
import { yuanToFen } from '@/utils/money'
import { joinLocation } from '@/utils/dish'
import { useDishAttributeStore } from '@/stores/dish-attribute'

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

  /** 是否「详情页纠错」形态（`mode=update` 深链进入；页面上无切换入口、无页签） */
  const isUpdateMode = ref(false)

  // ---- ① 单表单字段（默认形态） ----
  const form = reactive({
    /** 反馈类型（`''` = 未选；选中值 = FeedbackType，与后端写入值域同源） */
    type: '' as FeedbackType | '',
    /** 具体描述（必填，≤1000 字） */
    content: '',
    /** 截图（选填，≤1 张 COS URL） */
    images: [] as string[],
  })

  /** 描述框占位：随选中类型切换；未选类型时给通用引导（不得出现「请先选类型」之类空占位） */
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

  // ---- ③ 纠错形态字段（`mode=update`；与改版前一致） ----
  /**
   * update：选定菜品 → 详情预填 → 用户只改差异项（无文字说明字段，纯表单）。
   * 金额口径：`price` 为**元字符串**（input 展示/编辑），提交时经 yuanToFen 转分（金额红线）。
   * 食堂名 / 档口：**自由文本**（line-input 预填详情值，无字典 / 无 picker）。
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

  // ---- ④ 菜品搜索弹窗（仅纠错形态使用） ----
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

  /** ListPickerSheet 候选行（名称 + 档口简洁结果，契约：只展示名称+档口） */
  const dishPickerOptions = computed(() =>
    dishCandidates.value.map((d) => ({
      key: String(d.id),
      label: d.name,
      sub: joinLocation(d.canteen, d.stallName),
      image: d.coverImage || '',
    })),
  )

  /** 由 ListPickerSheet 内部防抖 emit('search', kw) 驱动；复用现有菜品搜索 API GET /dishes?keyword= */
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
        // 静默：请求失败不呈现任何占位，异常仅记录
        console.error('[feedback] 搜索菜品失败', err)
        dishCandidates.value = []
      })
  }

  function onDishPick(opt: { key: string }) {
    const d = dishCandidates.value.find((x) => String(x.id) === opt.key)
    if (d) selectDish(d)
  }

  /** 选中菜品：关闭弹窗 → 拉详情预填表单（用户只改差异项） */
  function selectDish(d: DishListItem) {
    update.dish = d
    dishKeyword.value = ''
    dishCandidates.value = []
    dishSearched.value = false
    dishSheetOpen.value = false
    void loadDishDetail(d.id)
  }

  /** 重选：回到选择步骤（清空预填表单） */
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

  /**
   * 拉详情并预填表单（字段值来自 GET /dishes/{id}）；同时把详情投影为列表行写入 update.dish
   * （深链 dishId 进入时 update.dish 为空，由本函数统一补齐，选择器行展示/提交路径 dishId 均依赖它）。
   * 食堂名 / 档口预填：**直接取详情文本**（canteenName / stallName），无字典匹配逻辑。
   */
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
      update.images = [...(d.images || [])]
      clearError('update.dish')
    } catch (err) {
      // 预填失败：保留已选菜品行（可点重选换菜 / 重新触发），其余字段保持空
      console.error('[feedback] 菜品详情加载失败', err)
      uni.showToast({ title: '菜品信息加载失败，请重选', icon: 'none' })
    } finally {
      update.detailLoading = false
    }
  }

  // ---- ⑤ 提交门禁（canSubmit 置灰；置灰点击由外层热区兜底 toast） ----
  const PRICE_PATTERN = /^(?:\d+)(?:\.\d{1,2})?$/

  function priceValid(): boolean {
    const p = update.price.trim()
    if (!p) return false
    const n = Number(p)
    return PRICE_PATTERN.test(p) && Number.isFinite(n) && n > 0 && n <= 9999
  }

  const canSubmit = computed(() => {
    if (!isUpdateMode.value) return !!form.type && !!form.content.trim()
    return (
      !!update.dish &&
      !update.detailLoading &&
      !!update.name.trim() &&
      priceValid() &&
      !!update.canteenName.trim() &&
      !!update.stallName.trim()
    )
  })

  /** 置灰点击提示文案：按当前形态缺失项优先给出 */
  const gateHint = computed(() => {
    if (!isUpdateMode.value) {
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

  // ---- ⑥ 字段级错误 + 提交中状态 ----
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
    setTimeout(() => { scrollIntoView.value = target }, 50)
  }

  // ---- ⑦ 提交（防重复；成功 Toast「已提交，感谢反馈」+ 2 秒自动返回） ----
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
    if (!isUpdateMode.value) {
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
      if (!isUpdateMode.value) {
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
      } else {
        // 纠错端点：dishId 在路径中，请求体七字段平铺
        // （无 payload 包裹、无 type、无 dishId 字段）；price 元 → 分（金额红线）；
        // 公开可提交（匿名允许）；菜品不存在 → 4001，敏感词 → 400 message 直透。
        await submitDishCorrection(update.dish!.id, {
          name: update.name.trim(),
          price: yuanToFen(Number(update.price.trim())), // 元 → 分（换算统一走 utils/money）
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

  // ---- ⑧ 成功态自动返回（用户 2 秒内无输入则 navigateBack） ----
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

  // ---- ⑨ 落点参数 ----
  // · 默认（无参 / mode=issue）：单表单形态，恢复本地草稿；
  // · mode=update（详情页纠错跳转，含 dishId）：纠错形态，直接拉详情预填（跳过搜索）。
  onLoad((opts?: Record<string, string>) => {
    if (opts?.mode === 'update') {
      isUpdateMode.value = true
      void dishAttr.ensureLoaded() // 纠错表单 chips 需机器值 → 中文字典（失败静默，展示原始值）
      const dishId = Number(opts?.dishId ?? 0)
      if (dishId > 0) {
        loadDishDetail(dishId).catch((err) => {
          console.error('[feedback] 深链菜品详情加载失败', err)
          uni.showToast({ title: '菜品信息加载失败，可手动选择菜品', icon: 'none' })
        })
      }
      return
    }
    loadDraft()
  })

  /** 供页面模板/模板回调使用的全部编排绑定 */
  return {
    goBack,
    isUpdateMode,
    // 单表单
    form,
    typePlaceholder,
    onPickType,
    // 纠错表单
    update,
    // 菜品搜索弹窗
    dishSheetOpen,
    dishKeyword,
    dishPickerOptions,
    dishSearched,
    openDishSheet,
    closeDishSheet,
    onDishSearchKw,
    onDishPick,
    resetDish,
    // 错误与提交
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
