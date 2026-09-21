/**
 * useFeedback —— 意见反馈页（pages/feedback/index.vue）编排逻辑
 *
 * 页面私有编排（仅本页使用，就近置于页面包，不驻留 composables/）：
 * 抽取自 feedback/index.vue 的 <script setup>（feedback-forms-ux-polish 之后版本），
 * 页面仅保留模板贴片组装与包内子件（SuggestionForm / AddForm / ErrorForm）引用。
 * 职责：
 * - 类型 chip 切换状态（type + watch 联动清空表单）；
 * - 动态字段模型（三类型独立 form 子树）与提交门禁（canSubmit / gateHint）；
 * - 三类底部选择器编排：菜品搜索（searchSeq 竞态守卫 + 空态去补录联动）、
 *   位置（食堂 → 档口两级联动 + 「其他」自定义）、楼层；
 * - 字段级错误定位（markErrors / scrollIntoView）与提交组装 / 自动返回定时器。
 *
 * ⚠️ 全部逻辑在函数体内执行：由页面在 <script setup> 中同步调用 useFeedback()，
 * 使 onLoad/onUnload/watch 均在组件实例上下文中注册（模块顶层注册会报 "no active component instance"）。
 */
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { submitFeedback } from '@/api/feedback'
import type { FeedbackSubmit } from '@/types/feedback'
import { searchDishes, getDishDetail } from '@/api/dish'
import type { Dish } from '@/types/dish'
import { getCanteensWithStalls } from '@/api/canteen'
import type { CanteenWithStalls } from '@/types/canteen'
import { backToHome } from '@/utils/nav'
import { useUserStore } from '@/stores/user'

export function useFeedback() {
  const userStore = useUserStore()
  /**
   * 全页唯一返回实现：有返回栈时 navigateBack；无返回栈（redirectTo 直达）才 reLaunch 首页。
   * 手动返回与成功态自动返回（scheduleAutoBack）共用本函数，不再各写一份栈判断。
   */
  function goBack() {
    if (getCurrentPages().length > 1) uni.navigateBack()
    else backToHome()
  }

  // ---- ① 类型（3 类等宽卡片：左侧 icon + 右侧标题） ----
  // 投稿类目（add / error）desc 须明示处理后上架语义（spec §7.3 / §7.7 第 2 条：投稿为待处理反馈，不得让用户以为提交即生效）
  const types: { value: FeedbackSubmit['type']; label: string; desc: string; icon: string }[] = [
    { value: 'suggestion', label: '提个想法', desc: '建议 / 问题', icon: 'lightbulb-fill' },
    { value: 'add', label: '推荐菜品', desc: '处理后上架', icon: 'dish-fill' },
    { value: 'error', label: '信息不对', desc: '纠错 / 处理后上架', icon: 'report-fill' },
  ]

  /** 投稿类目（add / error）通用说明：提交后由管理员处理，确认后才会展示（spec §7.7 第 2 条） */
  const SUBMIT_PENDING_HINT = '提交后由管理员处理，确认后才会展示'
  /** 投稿类目处理说明可见性：仅 add / error 两类的表单与提交区展示 */
  const submitPendingHint = computed(() => (type.value === 'add' || type.value === 'error' ? SUBMIT_PENDING_HINT : ''))
  const type = ref<FeedbackSubmit['type']>('suggestion')
  /** 来源承接文案：由贡献入口落点参数推导（见 contribution-entry），仅读展示；用户切换类型后清空 */
  const sourceHint = ref('')

  // ---- ② 动态字段（各类型独立状态，切换保留，提交清空） ----
  const form = reactive({
    suggestion: {
      sub: 'idea' as 'idea' | 'problem',
      text: '',
      images: [] as string[],
    },
    add: {
      name: '',
      price: '',
      canteen: '',
      canteenCustom: '',
      stallName: '',
      stallCustom: '',
      floor: '',
      description: '',
      images: [] as string[],
    },
    error: {
      dish: null as Dish | null,
      points: [] as string[],
      correctValues: {} as Record<string, string>,
      evidenceText: '',
      images: [] as string[],
    },
  })

  // ---- feedback-forms-ux-polish：提交门禁（canSubmit 置灰；置灰点击由外层热区兜底 toast） ----
  const canSubmit = computed(() => {
    const t = type.value
    if (t === 'suggestion') return !!form.suggestion.text.trim()
    if (t === 'add') return !!form.add.name.trim() && !!form.add.floor
    if (t === 'error') return !!form.error.dish && form.error.points.length > 0
    return false
  })

  /** 置灰点击提示文案：按当前类型缺失项优先给出（结构必填口径，见 feedback-forms-ux spec） */
  const gateHint = computed(() => {
    const t = type.value
    if (t === 'suggestion') return '请填写反馈内容'
    if (t === 'add') {
      if (!form.add.name.trim()) return '菜名叫啥？填一下'
      if (!form.add.floor) return '楼层必填'
      return ''
    }
    if (t === 'error') {
      if (!form.error.dish) return '先选一道菜'
      if (!form.error.points.length) return '至少选一项'
      return ''
    }
    return ''
  })

  /** 置灰态点击提示：AppButton 在 disabled 时不 emit press，由 .submit-area 外层热区兜底 */
  function onSubmitAreaTap() {
    if (!canSubmit.value) uni.showToast({ title: gateHint.value, icon: 'none' })
  }

  // ---- feedback-forms-ux-polish：类型切换即清空（已拍板口径；watch 单一入口，含菜品空态去补录联动） ----
  watch(type, () => {
    resetForm()
    // 切换类型即脱离原贡献场景，清空来源承接文案（避免与新表单语义矛盾）
    sourceHint.value = ''
  })

  // ---- ③ 信息不对：关联菜品搜索（底部弹窗） ----
  const dishSheetOpen = ref(false)
  const dishKeyword = ref('')
  const dishCandidates = ref<Dish[]>([])
  const dishSearched = ref(false)
  /** 成功态自动返回定时器（⑨ scheduleAutoBack） */
  let backTimer: ReturnType<typeof setTimeout> | null = null
  /** 页面级一次性定时器注册表（markErrors 的 scrollIntoView 定位延迟）：onUnload 统一清理（P0 防越界访问） */
  let pageTimers: ReturnType<typeof setTimeout>[] = []
  onUnload(() => {
    if (backTimer) clearTimeout(backTimer)
    pageTimers.forEach((t) => clearTimeout(t))
    pageTimers = []
  })

  function openDishSheet() {
    dishSheetOpen.value = true
  }

  function closeDishSheet() {
    dishSheetOpen.value = false
  }

  /** 空态「去补录一道」：关闭弹窗并切到推荐菜品类型 */
  function gotoAdd() {
    dishSheetOpen.value = false
    type.value = 'add'
  }

  /** ListPickerSheet 候选行（image/icon/文字）映射 */
  const dishPickerOptions = computed(() =>
    dishCandidates.value.map((d) => ({
      key: String(d.id),
      label: d.name,
      sub: [d.canteen, d.stallName].filter(Boolean).join(' · '),
      image: d.image || '',
    })),
  )

  /** 搜索请求序号：快速输入/连续触发时丢弃过期响应，避免旧请求晚到覆盖新候选（竞态守卫，对齐 review.vue） */
  let searchSeq = 0

  /** 由 ListPickerSheet 内部防抖 emit('search', kw) 驱动；keyword 语义与迁移前一致 */
  function onDishSearchKw(kw: string) {
    cancelAutoBack()
    dishKeyword.value = kw
    if (!kw.trim()) {
      dishSearched.value = false
      dishCandidates.value = []
      searchSeq++
      return
    }
    dishSearched.value = false
    const seq = ++searchSeq
    dishSearched.value = true
    searchDishes({ keyword: kw.trim(), page: 1, pageSize: 6 })
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

  function selectDish(d: Dish) {
    cancelAutoBack()
    form.error.dish = d
    dishKeyword.value = ''
    dishCandidates.value = []
    dishSearched.value = false
    dishSheetOpen.value = false
    clearError('error.dish')
  }

  function resetDish() {
    form.error.dish = null
    dishKeyword.value = ''
    dishCandidates.value = []
    dishSearched.value = false
  }

  // ---- ④ 信息不对：哪里不对（多选）+ 正确信息 ----
  const correctionPoints = [
    { key: 'price', label: '价格不对', short: '价格', icon: 'price', editPlaceholder: '正确价格，如 0.00' },
    { key: 'name', label: '名字写错', short: '名字', icon: 'edit', editPlaceholder: '正确名字' },
    { key: 'location', label: '位置变了', short: '位置', icon: 'location', editPlaceholder: '正确位置，如：一食堂 · 面食窗口' },
    { key: 'attr', label: '图片 / 属性不对', short: '图片属性', icon: 'image', editPlaceholder: '哪里不对，如：口味标错了' },
    { key: 'removed', label: '已下架', short: '已下架', icon: 'report', editPlaceholder: '补充下架说明（可选）' },
    { key: 'other', label: '其他', short: '其他', icon: 'comment', editPlaceholder: '还有啥问题' },
  ]

  function togglePoint(key: string) {
    // 用户交互 = 取消自动返回
    cancelAutoBack()
    // 「已下架」与其他所有选项互斥：选中已下架清空其他；选中其他时取消已下架
    if (key === 'removed') {
      if (form.error.points.includes('removed')) {
        form.error.points = []
      } else {
        form.error.points = ['removed']
      }
    } else {
      const i = form.error.points.indexOf(key)
      if (i >= 0) {
        form.error.points.splice(i, 1)
      } else {
        form.error.points = form.error.points.filter(k => k !== 'removed')
        form.error.points.push(key)
        // 选中时若编辑区尚无内容，预填该菜品当前字段值，用户在此基础上改
        if (!(form.error.correctValues[key] || '').trim()) {
          form.error.correctValues[key] = dishPrevValues.value[key] || ''
        }
      }
    }
    clearError('error.points')
  }

  /** 该菜品当前字段值（编辑区未选中的只读展示，computed 预计算避免模板内函数调用） */
  const dishPrevValues = computed<Record<string, string>>(() => {
    const d = form.error.dish
    const out: Record<string, string> = {}
    if (!d) return out
    out.price = d.price > 0 ? `¥${d.price}` : ''
    out.name = d.name || ''
    out.location = [d.canteen, d.stallName].filter(Boolean).join(' · ')
    const n = d.images?.length || 0
    out.attr = n > 0 ? `${n} 张图片` : ''
    return out
  })

  // ---- ⑤ 位置选择：ListPickerSheet 单实例两级联动（食堂 → 档口，含「其他」自定义） ----
  // P2-11 / PR-12：原 `ref<any[]>` 逃逸已消除，改用 API 层定型 DTO（CanteenWithStalls）
  const canteenTree = ref<CanteenWithStalls[]>([])
  const locSheetOpen = ref(false)
  const locStep = ref<'canteen' | 'stall'>('canteen')

  /** 食堂显示名：选「其他」且有自定义名时显示自定义名 */
  const displayCanteen = computed(() =>
    form.add.canteen === '其他' ? form.add.canteenCustom.trim() || '其他' : form.add.canteen,
  )
  /** 当前食堂下的档口原始列表 */
  const currentStalls = computed<string[]>(() => {
    const c = canteenTree.value.find((x) => x.name === form.add.canteen)
    return (c?.stalls || []).map((s) => s.name)
  })

  /** ListPickerSheet 位置选项：食堂级(canteen icon) / 档口级(stall icon)，末尾追加「其他」(add icon) */
  const locOptions = computed<{ key: string; label: string; icon: string }[]>(() => {
    const base =
      locStep.value === 'canteen'
        ? canteenTree.value.map((c) => ({ key: c.name, label: c.name, icon: 'canteen' }))
        : currentStalls.value.map((name) => ({ key: name, label: name, icon: 'stall' }))
    return [...base, { key: '其他', label: '其他', icon: 'plus' }]
  })

  /** 当前高亮项 key：食堂/档口原始选中值（'' → 无高亮；'其他' 命中末尾项） */
  const locSelectedKey = computed(() => (locStep.value === 'canteen' ? form.add.canteen || null : form.add.stallName || null))

  /** 尾部「其他」自定义输入可见性：当前级已选中「其他」 */
  const locCustomShown = computed(() =>
    locStep.value === 'canteen' ? form.add.canteen === '其他' : form.add.stallName === '其他',
  )
  const locCustomValue = computed(() => (locStep.value === 'canteen' ? form.add.canteenCustom : form.add.stallCustom))
  /**
   * 自定义输入 @input 回调。
   * 平台例外：uni input 事件对象由运行时透传，模板侧类型为 `Event`（无 `detail` 声明），
   * 故形参取 `Event` 并在读取处做一次结构化收窄，避免 `any` 逃逸（同 http.ts wx 句柄说明）。
   */
  function onLocCustomInput(e: Event) {
    const detail = (e as unknown as { detail?: { value?: string } })?.detail
    const v = detail?.value ?? ''
    if (locStep.value === 'canteen') form.add.canteenCustom = v
    else form.add.stallCustom = v
  }

  /** 位置选项点击 → 转发到 pickCanteen/pickStall（保留 toggle 与档口联动清空语义） */
  function onLocSelect(opt: { key: string; label: string }) {
    if (locStep.value === 'canteen') pickCanteen(opt.key)
    else pickStall(opt.key)
  }

  async function loadCanteens() {
    try {
      canteenTree.value = await getCanteensWithStalls()
    } catch {
      canteenTree.value = []
    }
  }

  function openLocationSheet(step: 'canteen' | 'stall') {
    locStep.value = step
    locSheetOpen.value = true
  }

  function closeLocationSheet() {
    locSheetOpen.value = false
  }

  function pickCanteen(name: string) {
    // 再次点击已选项：取消选择（含档口联动清空）
    if (form.add.canteen === name) {
      form.add.canteen = ''
      form.add.canteenCustom = ''
      form.add.stallName = ''
      form.add.stallCustom = ''
      return
    }
    form.add.canteen = name
    form.add.canteenCustom = ''
    // 切换食堂时清空旧档口
    form.add.stallName = ''
    form.add.stallCustom = ''
    if (name === '其他') {
      // A2：其他食堂 → 档口也直接进「其他」自定义输入，保持联动完整
      form.add.stallName = '其他'
      form.add.stallCustom = ''
      locStep.value = 'canteen'
      return
    }
    locStep.value = 'stall'
  }

  function pickStall(name: string) {
    if (form.add.stallName === name) {
      form.add.stallName = ''
      form.add.stallCustom = ''
      return
    }
    form.add.stallName = name
    form.add.stallCustom = ''
  }

  /** A1：档口行点击 —— 未选食堂时提示先选食堂 */
  function onStallRowTap() {
    if (!displayCanteen.value) {
      uni.showToast({ title: '先选择食堂', icon: 'none' })
      return
    }
    openLocationSheet('stall')
  }

  // ---- ⑤.5 楼层选择：ListPickerSheet（1/2/3，icon 沿用原观感） ----
  const floorSheetOpen = ref(false)
  const floorList = ['1', '2', '3']

  /** ListPickerSheet 楼层选项（label 显示「N 楼」，key 存原值用于高亮/回传） */
  const floorPickerOptions = computed<{ key: string; label: string; icon: string }[]>(() =>
    floorList.map((f) => ({ key: f, label: `${f} 楼`, icon: 'canteen' })),
  )

  function openFloorSheet() {
    floorSheetOpen.value = true
  }

  function closeFloorSheet() {
    floorSheetOpen.value = false
  }

  function pickFloor(f: string) {
    // 再次点击已选项取消
    form.add.floor = form.add.floor === f ? '' : f
    clearError('add.floor')
    closeFloorSheet()
  }

  function onFloorSelect(opt: { key: string }) {
    pickFloor(opt.key)
  }

  // ---- ⑥ 字段级错误定位 ----
  const fieldErrors = reactive<Record<string, string>>({})
  const scrollIntoView = ref('')
  /** 提交中状态（防连点 + AppButton loading 绑定） */
  const submitting = ref(false)

  function clearError(key: string) {
    delete fieldErrors[key]
    // 用户重新输入 = 取消自动返回
    cancelAutoBack()
  }

  function markErrors(errs: Record<string, string>) {
    Object.keys(fieldErrors).forEach(k => delete fieldErrors[k])
    const keys = Object.keys(errs)
    if (!keys.length) return
    keys.forEach(k => { fieldErrors[k] = errs[k] })
    const first = keys[0]
    const idMap: Record<string, string> = {
      'suggestion.text': 'f-sug-text',
      'add.name': 'f-add-name',
      'add.price': 'f-add-price',
      'add.floor': 'f-add-floor',
      'error.dish': 'f-dish',
      'error.points': 'f-point',
    }
    const target = idMap[first] || (first.startsWith('error.correct.') ? 'f-point' : '')
    scrollIntoView.value = ''
    const t = setTimeout(() => { scrollIntoView.value = target }, 50)
    pageTimers.push(t)
  }

  // ---- ⑧ 提交组装 ----
  function resetForm() {
    form.suggestion.sub = 'idea'
    form.suggestion.text = ''
    form.suggestion.images = []
    form.add.name = ''
    form.add.price = ''
    form.add.canteen = ''
    form.add.canteenCustom = ''
    form.add.stallName = ''
    form.add.stallCustom = ''
    form.add.floor = ''
    form.add.description = ''
    form.add.images = []
    form.error.dish = null
    form.error.points = []
    form.error.correctValues = {}
    form.error.evidenceText = ''
    form.error.images = []
    dishKeyword.value = ''
    dishCandidates.value = []
    dishSearched.value = false
    locSheetOpen.value = false
    floorSheetOpen.value = false
    dishSheetOpen.value = false
  }

  async function submit() {
    if (submitting.value) return

    const t = type.value
    const errs: Record<string, string> = {}

    // 按类型动态必填校验（收集全部错误）
    if (t === 'suggestion') {
      if (!form.suggestion.text.trim()) errs['suggestion.text'] = '先写两句呗'
    } else if (t === 'add') {
      if (!form.add.name.trim()) errs['add.name'] = '菜名叫啥？填一下'
      if (!form.add.floor.trim()) errs['add.floor'] = '楼层必填'
      // A4：价格格式校验（填了就必须是合法数字）。P3-14：与提示文案「像 12.5 这样」对齐，
      // 补齐「最多两位小数」校验——此前 `12.999` 可通过，与文案承诺的精度不一致（币值最小单位 = 分）。
      const priceStr = form.add.price.trim()
      if (priceStr) {
        // 单一判定：合法数字 + 大于 0 + 不超过 9999 + 小数位 ≤2（拒绝 12.999 / 1.2345 / 1.）
        const PRICE_PATTERN = /^(?:\d+)(?:\.\d{1,2})?$/
        const priceNum = Number(priceStr)
        if (!PRICE_PATTERN.test(priceStr) || !Number.isFinite(priceNum) || priceNum <= 0 || priceNum > 9999) {
          errs['add.price'] = '价格要像 12.5 这样'
        }
      }
    } else if (t === 'error') {
      if (!form.error.dish) errs['error.dish'] = '先选一道菜'
      if (!form.error.points.length) {
        errs['error.points'] = '至少选一项'
      } else {
        for (const key of form.error.points) {
          // attr/removed 文本为选填；其余需填正确信息
          if (key !== 'removed' && key !== 'attr' && !(form.error.correctValues[key] || '').trim()) {
            errs[`error.correct.${key}`] = '填一下正确信息'
          }
        }
      }
    }

    if (Object.keys(errs).length) {
      markErrors(errs)
      uni.showToast({ title: `还有 ${Object.keys(errs).length} 项没填`, icon: 'none' })
      return
    }

    // 组装 content（结构化文本）
    let content = ''
    let relatedType: string | undefined
    // #6 修复：relatedId 与 relatedType 成对赋值，仅在 error 分支设置。
    // 原实现 unconditionally 取 form.error.dish?.id，切到 suggestion/add 类型提交时残留
    // error 的 relatedId 而 relatedType 为 undefined，造成契约不一致。
    let relatedId: number | undefined
    // DEV-01：二级选项（提建议 idea / 报问题 problem）此前仅端上选中、提交时被丢弃；
    // 契约口径＝仅 suggestion 携带，其他类型不传（后端按 type 走白名单，非法值 400）。
    let sub: 'idea' | 'problem' | undefined

    if (t === 'suggestion') {
      content = form.suggestion.text.trim()
      sub = form.suggestion.sub
    } else if (t === 'add') {
      const parts = [`【新增菜品】${form.add.name.trim()}`]
      if (form.add.price.trim()) parts.push(`价格：${form.add.price.trim()}元`)
      // 「其他」时取自定义值
      const canteen = form.add.canteen === '其他' ? form.add.canteenCustom.trim() : form.add.canteen
      const stall = form.add.stallName === '其他' ? form.add.stallCustom.trim() : form.add.stallName
      const loc = [canteen, stall].filter(Boolean).join('·')
      if (loc) parts.push(`位置：${loc}`)
      if (form.add.floor.trim()) parts.push(`楼层：${form.add.floor.trim()}`)
      if (form.add.description.trim()) parts.push(`描述：${form.add.description.trim()}`)
      content = parts.join('\n')
      // add 为新增菜品，无关联已有对象，不传 relatedType
    } else if (t === 'error') {
      const parts: string[] = []
      for (const key of form.error.points) {
        const c = correctionPoints.find(x => x.key === key)
        if (!c) continue
        const text = (form.error.correctValues[key] || '').trim()
        if (key === 'removed') {
          parts.push(text ? `【已下架】\n说明：${text}` : '【已下架】')
          continue
        }
        parts.push(text ? `【${c.label}】\n说明：${text}` : `【${c.label}】`)
      }
      content = parts.join('\n')
      if (form.error.evidenceText.trim()) content += `\n作证：${form.error.evidenceText.trim()}`
      relatedType = 'dish'
      relatedId = form.error.dish?.id
    }

    if (content.length > 1000) { uni.showToast({ title: '内容不能超过1000字', icon: 'none' }); return }

    // 配图（≤3 张 COS URL）：取当前类型子树，非空才上送；违规图片在上传阶段即被后端拦截
    const typeImages = t === 'suggestion' ? form.suggestion.images : t === 'add' ? form.add.images : form.error.images
    const images = (typeImages || []).filter(Boolean)

    submitting.value = true
    try {
      await submitFeedback({
        type: t,
        content,
        sub,
        relatedType,
        relatedId,
        images: images.length ? images : undefined,
      })
      // 成功反馈 + 处理预期说明；游客须明确「未记账号，结果无法单独通知」（见 feedback-receipt）
      uni.showToast({
        title: userStore.isVerified()
          ? '感谢你的反馈！我们会在数个工作日内查看并处理'
          : '感谢你的反馈！我们会在数个工作日内处理，但未记账号、结果无法单独通知你',
        icon: 'none',
      })
      resetForm()
      // 成功态双态：2 秒后无输入则自动返回来源页
      scheduleAutoBack()
    } catch (e) {
      // Error 分支取 message（请求层抛 Error）；非 Error 兜底通用文案
      uni.showToast({ title: e instanceof Error && e.message ? e.message : '没发出去，再试一次', icon: 'none' })
    } finally {
      submitting.value = false
    }
  }

  // ---- ⑨ 成功态自动返回（用户 2 秒内无输入则 navigateBack） ----
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

  /** 来源承接文案：由落点参数推导（不新增参数）；首页卡片 / 搜索无结果 / 详情页纠错三种语义 */
  function buildSourceHint(opts?: Record<string, string>): string {
    if (opts?.from === 'dish') {
      const name = opts?.dishName || form.error.dish?.name || ''
      return name ? `正在纠正：${name}` : '正在纠正这条菜品信息'
    }
    if (opts?.from === 'find') return '搜索没找到，来推荐一道'
    if (opts?.from === 'home') return '从首页「想吃啥没找到」进来'
    return ''
  }

  // ---- 贡献入口落点（统一参数：type / from / dishId / dishName，见 spec contribution-entry） ----
  // 键名唯一：替代原「首页反馈菜品入口」遗留的 object/name/id 分支（该入口已不存在）。
  onLoad(async (opts?: Record<string, string>) => {
    loadCanteens()
    const t = opts?.type
    if (t !== 'suggestion' && t !== 'add' && t !== 'error') return
    type.value = t
    // 时序契约：watch(type) 会触发 resetForm()（含清空 form.error.dish），
    // 因此来源承接文案与关联菜品必须在清空完成之后（nextTick 之后）再注入，否则会被清掉。
    await nextTick()
    sourceHint.value = buildSourceHint(opts)
    const dishId = Number(opts?.dishId ?? 0)
    if (t === 'error' && dishId) {
      try {
        const d = await getDishDetail(dishId)
        // 注入形态契约：必须落成完整 Dish 对象（提交使能 canSubmit 依赖 form.error.dish 非空）
        if (d) form.error.dish = d
      } catch { /* 忽略：详情拉取失败则回退为手动搜索选择菜品 */ }
    }
  })

  /** 供页面模板/模板回调使用的全部编排绑定（名称与抽取前 <script setup> 顶层保持一致） */
  return {
    goBack,
    types,
    type,
    sourceHint,
    submitPendingHint,
    form,
    canSubmit,
    gateHint,
    onSubmitAreaTap,
    // 菜品搜索弹窗
    dishSheetOpen,
    dishKeyword,
    dishPickerOptions,
    dishSearched,
    openDishSheet,
    closeDishSheet,
    onDishSearchKw,
    onDishPick,
    gotoAdd,
    resetDish,
    // 纠错
    correctionPoints,
    togglePoint,
    // 位置选择
    locSheetOpen,
    locStep,
    locOptions,
    locSelectedKey,
    locCustomShown,
    locCustomValue,
    openLocationSheet,
    closeLocationSheet,
    onLocCustomInput,
    onLocSelect,
    onStallRowTap,
    // 楼层
    floorSheetOpen,
    floorPickerOptions,
    openFloorSheet,
    closeFloorSheet,
    onFloorSelect,
    // 错误与提交
    fieldErrors,
    scrollIntoView,
    submitting,
    clearError,
    submit,
  }
}
