/**
 * useCorrection —— 菜品信息纠错页（pages/correction/index.vue）编排逻辑
 *
 * **独立页面**（2026-09-27 从意见反馈页迁出，与反馈体系解耦）：
 * 入口只有一处 —— 菜品详情页底栏「反馈错误」；进页即按 `dishId` 拉详情**预填**，
 * 用户只改错的地方，提交走独立端点 `POST /dishes/{id}/correction`（落 `dish_correction` 表）。
 *
 * 与「意见反馈」的边界：本页是**依附某条菜品数据**的专项修正（自带 dishId、字段结构化）；
 * 意见反馈是脱离菜品上下文的通用反馈（纯文本 + 截图）。
 *
 * ⚠️ 全部逻辑在函数体内执行：由页面在 <script setup> 中同步调用 useCorrection()，
 * 使 onLoad/onUnload 均在组件实例上下文中注册（模块顶层注册会报 "no active component instance"）。
 */
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { submitDishCorrection } from '@/api/feedback'
import { searchDishes, getDishDetail } from '@/api/dish'
import type { DishListItem } from '@/types/dish'
import { backToHome } from '@/utils/nav'
import { yuanToFen } from '@/utils/money'
import { joinLocation } from '@/utils/dish'
import { useDishAttributeStore } from '@/stores/dish-attribute'

export function useCorrection() {
  const dishAttr = useDishAttributeStore()

  /** 全页唯一返回实现：有返回栈时 navigateBack；无返回栈才 reLaunch 首页 */
  function goBack() {
    if (getCurrentPages().length > 1) uni.navigateBack()
    else backToHome()
  }

  // ---- ① 表单字段（预填后只改差异项） ----
  /**
   * 金额口径：`price` 为**元字符串**（input 展示/编辑），提交时经 yuanToFen 转分（金额红线）。
   * 食堂名 / 档口：自由文本（预填详情值，可改）。
   */
  const form = reactive({
    /** 搜索选定的菜品（列表行，用于选择器行展示 + 提交路径 dishId） */
    dish: null as DishListItem | null,
    /** 详情拉取中（预填未完成时禁止提交） */
    detailLoading: false,
    name: '',
    price: '',
    canteenName: '',
    stallName: '',
    /** 口味标签 / 食材：预填详情机器值 + 用户自由输入项（chips 增删） */
    flavorTags: [] as string[],
    ingredients: [] as string[],
    /** 图片：最多 1 张（预填菜品首图，可替换） */
    images: [] as string[],
  })

  // ---- ② 菜品选择弹层（用于换一道菜 / 深链失败后手动选） ----
  const dishSheetOpen = ref(false)
  const dishKeyword = ref('')
  const dishCandidates = ref<DishListItem[]>([])
  const dishSearched = ref(false)
  /** 搜索请求序号：丢弃过期响应（竞态守卫） */
  let searchSeq = 0

  function openDishSheet() {
    dishSheetOpen.value = true
  }

  function closeDishSheet() {
    dishSheetOpen.value = false
  }

  const dishPickerOptions = computed(() =>
    dishCandidates.value.map((d) => ({
      key: String(d.id),
      label: d.name,
      sub: joinLocation(d.canteen, d.stallName),
      image: d.coverImage || '',
    })),
  )

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
        if (seq !== searchSeq) return
        dishCandidates.value = list
      })
      .catch((err) => {
        if (seq !== searchSeq) return
        console.error('[correction] 搜索菜品失败', err)
        dishCandidates.value = []
      })
  }

  function onDishPick(opt: { key: string }) {
    const d = dishCandidates.value.find((x) => String(x.id) === opt.key)
    if (d) selectDish(d)
  }

  /** 选中菜品：关闭弹窗 → 拉详情预填 */
  function selectDish(d: DishListItem) {
    form.dish = d
    dishKeyword.value = ''
    dishCandidates.value = []
    dishSearched.value = false
    dishSheetOpen.value = false
    void loadDishDetail(d.id)
  }

  /** 重选：回到选择步骤（清空预填） */
  function resetDish() {
    form.dish = null
    form.name = ''
    form.price = ''
    form.canteenName = ''
    form.stallName = ''
    form.flavorTags = []
    form.ingredients = []
    form.images = []
    dishKeyword.value = ''
    dishCandidates.value = []
    dishSearched.value = false
    clearError('form.dish')
  }

  /** 拉详情并预填（字段值来自 `GET /dishes/{id}`）；菜品行由本函数统一投影补齐 */
  async function loadDishDetail(id: number) {
    form.detailLoading = true
    try {
      const d = await getDishDetail(id)
      form.dish = {
        id: d.id,
        name: d.name,
        price: d.price,
        originalPrice: d.originalPrice,
        coverImage: d.image || '',
        rating: d.rating,
        canteen: d.canteen,
        stallName: d.stallName,
      }
      form.name = d.name || ''
      form.price = d.price > 0 ? String(d.price) : '' // 详情 price 已由 API 层分→元
      form.canteenName = d.canteen || ''
      form.stallName = d.stallName || ''
      form.flavorTags = [...(d.flavorTags || [])]
      form.ingredients = [...(d.ingredients || [])]
      // 图片上限统一 1 张：预填只取首图，用户可替换
      form.images = [...(d.images || [])].slice(0, 1)
      clearError('form.dish')
    } catch (err) {
      console.error('[correction] 菜品详情加载失败', err)
      uni.showToast({ title: '菜品信息加载失败，请重选', icon: 'none' })
    } finally {
      form.detailLoading = false
    }
  }

  // ---- ③ 提交门禁 ----
  const PRICE_PATTERN = /^(?:\d+)(?:\.\d{1,2})?$/

  function priceValid(): boolean {
    const p = form.price.trim()
    if (!p) return false
    const n = Number(p)
    return PRICE_PATTERN.test(p) && Number.isFinite(n) && n > 0 && n <= 9999
  }

  const canSubmit = computed(
    () =>
      !!form.dish &&
      !form.detailLoading &&
      !!form.name.trim() &&
      priceValid() &&
      !!form.canteenName.trim() &&
      !!form.stallName.trim(),
  )

  const gateHint = computed(() => {
    if (!form.dish) return '先选一道菜'
    if (form.detailLoading) return '菜品信息加载中'
    if (!form.name.trim()) return '菜名叫啥？填一下'
    if (!priceValid()) return '价格要像 12.5 这样'
    if (!form.canteenName.trim()) return '填一下食堂名'
    if (!form.stallName.trim()) return '填一下档口'
    return ''
  })

  /** 置灰态点击提示：AppButton 在 disabled 时不 emit press，由 .submit-area 外层热区兜底 */
  function onSubmitAreaTap() {
    if (!canSubmit.value) uni.showToast({ title: gateHint.value, icon: 'none' })
  }

  // ---- ④ 字段级错误 + 提交 ----
  const fieldErrors = reactive<Record<string, string>>({})
  const scrollIntoView = ref('')
  const submitting = ref(false)

  function clearError(key: string) {
    delete fieldErrors[key]
  }

  function markErrors(errs: Record<string, string>) {
    Object.keys(fieldErrors).forEach((k) => delete fieldErrors[k])
    const keys = Object.keys(errs)
    if (!keys.length) return
    keys.forEach((k) => { fieldErrors[k] = errs[k] })
    const idMap: Record<string, string> = {
      'form.dish': 'f-c-dish',
      'form.name': 'f-c-name',
      'form.price': 'f-c-price',
      'form.canteenName': 'f-c-canteenName',
      'form.stallName': 'f-c-stallName',
    }
    const target = idMap[keys[0]] || ''
    scrollIntoView.value = ''
    if (target) setTimeout(() => { scrollIntoView.value = target }, 50)
  }

  async function submit() {
    if (submitting.value) return

    const errs: Record<string, string> = {}
    if (!form.dish) errs['form.dish'] = '先选一道菜'
    else if (form.detailLoading) errs['form.dish'] = '菜品信息加载中'
    else {
      if (!form.name.trim()) errs['form.name'] = '菜名叫啥？填一下'
      if (!priceValid()) errs['form.price'] = '价格要像 12.5 这样'
      if (!form.canteenName.trim()) errs['form.canteenName'] = '填一下食堂名'
      if (!form.stallName.trim()) errs['form.stallName'] = '填一下档口'
    }
    if (Object.keys(errs).length) {
      markErrors(errs)
      uni.showToast({ title: `还有 ${Object.keys(errs).length} 项没填`, icon: 'none' })
      return
    }

    submitting.value = true
    try {
      // dishId 在路径中，请求体七字段平铺；price 元 → 分（金额红线）；
      // 公开可提交（匿名允许）；菜品不存在 → 4001，敏感词 → 400 message 直透。
      await submitDishCorrection(form.dish!.id, {
        name: form.name.trim(),
        price: yuanToFen(Number(form.price.trim())),
        canteenName: form.canteenName.trim(),
        stallName: form.stallName.trim(),
        flavorTags: form.flavorTags.filter(Boolean),
        ingredients: form.ingredients.filter(Boolean),
        images: form.images.filter(Boolean),
      })
      uni.showToast({ title: '已提交，感谢反馈', icon: 'none' })
      setTimeout(goBack, 1500)
    } catch (e) {
      uni.showToast({ title: e instanceof Error && e.message ? e.message : '没发出去，再试一次', icon: 'none' })
    } finally {
      submitting.value = false
    }
  }

  // ---- ⑤ 落点参数：`dishId` 必带（详情页跳入），进页即预填 ----
  onLoad((opts?: Record<string, string>) => {
    void dishAttr.ensureLoaded() // chips 展示机器值 → 中文字典（失败静默，展示原始值）
    const dishId = Number(opts?.dishId ?? 0)
    if (dishId > 0) {
      loadDishDetail(dishId).catch((err) => {
        console.error('[correction] 预填菜品详情失败', err)
        uni.showToast({ title: '菜品信息加载失败，可手动选择菜品', icon: 'none' })
      })
    }
  })

  return {
    goBack,
    form,
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
