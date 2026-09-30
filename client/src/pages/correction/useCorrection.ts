/**
 * 楼层固定字典（R40 · UI 稿「楼层控件」）：**存储值 ↔ 展示汉字**双向映射，端上常量。
 *
 * ⚠️ **只存在于端上**：后端 `floor` 契约（string、非空、≤16）与落库值（`B1` / `1F` …）**全部不变**，
 *   汉字**仅是展示层**产物，**永不进入提交路径**（采纳后 `stall.floor` 仍存 `B1`/`1F` 而非汉字）。
 * ⚠️ 管理端 `stall.floor` 仍是自由文本（≤16 字），故详情值**可能不在字典内** —— 见 `floorDisplay` 的兜底。
 */
export const FLOOR_OPTIONS: readonly { value: string; label: string }[] = [
  { value: 'B1', label: '负一层' },
  { value: '1F', label: '一层' },
  { value: '2F', label: '二层' },
  { value: '3F', label: '三层' },
  { value: '4F', label: '四层' },
]

/**
 * 存储值 → 展示文案（R40）。
 * - 命中字典 ⇒ 汉字（如 `B1` → 「负一层」），由 `FloorPickerSheet` 高亮对应行；
 * - **非空但未命中**（如 `B2` / `5F`）⇒ **原值原样展示**，第二返回值 `false` 供表单挂「不在预设范围」提示
 *   （**不因无法映射而清空或报错**：原值随 `form.floor` 保留，未动即无 diff）；
 * - 空串 ⇒ 占位「请选择楼层」（必填校验不放行，错误小字由表单层呈现）。
 *
 * @returns `[展示文案, 是否命中字典]`
 */
export function floorDisplay(raw: string): [string, boolean] {
  const v = (raw ?? '').trim()
  if (!v) return ['请选择楼层', false]
  const hit = FLOOR_OPTIONS.find((o) => o.value === v)
  return hit ? [hit.label, true] : [v, false]
}

/**
 * useCorrection —— 菜品信息纠错页（pages/correction/index.vue）编排逻辑
 *
 * **独立页面**（与「意见反馈」解耦）：入口**唯一** —— 菜品详情页信息卡名称行「信息有误?」；
 * 进页即按导航参数 `dishId` **锚定该菜品**（页内不提供切换入口），拉详情预填全部字段，
 * 用户只改错的地方 → **只提交改动过的项**（局部提交 / patch），未改动的不上传。
 *
 * 描述属性走**动态属性模型**（值即中文）：维度名与当前值取自 `GET /dishes/{id}`；
 * 编辑态参考候选**按需**取 `GET /dishes/{id}/attributes`（仅该菜现有维度；候选只是提示，
 * 用户恒可自由输入候选之外的新值）；提交经 `attributes` 对象（键 = 维度 `fieldKey`，仅改动维度）。
 * 展示侧中文由详情直出，**端上零翻译 / 零字典**。
 *
 * ⚠️ 全部逻辑在函数体内执行：由页面在 <script setup> 中同步调用 useCorrection()，
 * 使 onLoad/onUnload 均在组件实例上下文中注册（模块顶层注册会报 "no active component instance"）。
 */
import { ref, reactive, computed } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { submitDishCorrection } from '@/api/feedback'
import { getDishDetail, getDishEditAttributes } from '@/api/dish'
import { isResourceNotFound } from '@/api/http'
import { useRateLimitCooldown } from '@/composables/useRateLimitCooldown'
import type { DishCorrectionPayload } from '@/types/feedback'
import { backToHome } from '@/utils/back'
import { UGC_IMAGE_MAX } from '@/constants/ugc'
import { yuanToFen } from '@/utils/money'

/** 描述属性编辑项（表单内一个维度的可编辑模型） */
export interface AttributeEditor {
  /** 维度键（camelCase）＝ 提交时 `attributes` 的键 */
  fieldKey: string
  /** 维度中文名（来自详情 `attributes[].name`） */
  name: string
  /** 取值类型：`single`（单值）｜ `multi`（多值）—— 取自编辑态端点，缺省按详情值形态推断 */
  valueType: 'single' | 'multi'
  /**
   * 参考候选值（编辑态端点的 `options`，中文文本、按频次倒序）。
   * **空 = 无候选**（该维度暂无参考值，或候选接口失败）⇒ 端上隐藏「候选入口」，仅留自定义输入。
   */
  candidates: string[]
  /** 当前选中的中文值（`single` 至多 1 项） */
  selected: string[]
}

/**
 * 表单模型（页内私有子件 `CorrectionForm` 的入参形状）。
 * `price` 恒为**元字符串**（页面不换算金额；提交前由 API 层 `yuanToFen` 转分）。
 */
export interface CorrectionFormModel {
  name: string
  price: string
  canteenName: string
  /** 楼层（契约 `DishCorrectionReq.floor`；归属档口，仅楼层改动也算有效改动） */
  floor: string
  stallName: string
  images: string[]
  attributes: AttributeEditor[]
}

/** 必填字段键（门禁与置灰提示共用一份，禁两处各写一遍） */
const REQUIRED_KEYS = ['name', 'price', 'canteenName', 'floor', 'stallName'] as const

export function useCorrection() {
  /** 全页唯一返回实现：有返回栈时 navigateBack；无返回栈才 reLaunch 首页 */
  function goBack() {
    if (getCurrentPages().length > 1) uni.navigateBack()
    else backToHome()
  }

  // 页面级定时器句柄：均登记于此，`onUnload` 统一清理（避免回调打到已销毁页 / 已卸载组件）
  /** 提交成功自动返回 */
  let goBackTimer: ReturnType<typeof setTimeout> | null = null
  /** 首错定位（`scrollIntoView` 需等一帧，见 `markErrors`） */
  let anchorTimer: ReturnType<typeof setTimeout> | null = null
  onUnload(() => {
    if (goBackTimer) clearTimeout(goBackTimer)
    if (anchorTimer) clearTimeout(anchorTimer)
    goBackTimer = null
    anchorTimer = null
  })

  // ---- ① 锚定菜品与表单字段 ----
  /** 进页即由导航参数锚定（页内不可切换） */
  const dishId = ref(0)
  /**
   * 预填数据源（`GET /dishes/{id}`）；预填未完成时禁止提交。
   * 初值 **true**（返工口径）：`onLoad` 之前即视为「载入中」⇒ 首帧不渲染全空表单，
   * 避免预填就绪前用户面对空字段（乃至误提交空改动）。
   */
  const loading = ref(true)
  /** 菜品不存在 / 已下架（后端 4001）：与网络失败区别对待，只给返回 */
  const notFound = ref(false)
  /** 预填请求失败（网络 / 服务端故障，可重试） */
  const loadFailed = ref(false)
  /** 锚定卡展示：菜品名（只读） */
  const dishName = ref('')
  /** 锚定卡展示：「食堂 · 楼层 · 档口」（三段；段数 / 顺序与本页锚定卡一致，故不复用两段口径的 `joinLocation`） */
  const dishLocation = ref('')

  /**
   * 金额口径：`price` 为**元字符串**（input 展示/编辑），提交时经 `yuanToFen` 转分（金额红线）。
   * 食堂名 / 档口名：自由文本（预填详情值，可改）。
   * 楼层（R40）：**恒存后端存储值**（`B1` / `1F` …），汉字只在展示层由 `floorDisplay` 映射 ——
   * 故 diff 比对 / 必填校验 / 提交组装三处**均无需感知映射**，未命中字典的原值也原样保留（不产生 diff）。
   */
  const form = reactive<CorrectionFormModel>({
    name: '',
    price: '',
    canteenName: '',
    /** 楼层（契约已增补 `floor`；归属档口，仅楼层改动也属有效改动） */
    floor: '',
    stallName: '',
    /** 图片（预填菜品图，可增删，≤3 张） */
    images: [] as string[],
    /** 描述属性编辑项（维度顺序 = 详情返回顺序） */
    attributes: [] as AttributeEditor[],
  })

  /** 预填快照：判「有无改动」的基线（改动项 = 当前值 ≠ 基线值） */
  const baseline = reactive({
    name: '',
    price: '',
    canteenName: '',
    floor: '',
    stallName: '',
    images: [] as string[],
    attributes: {} as Record<string, string[]>,
  })

  // ---- ② 进页预填（详情 + 编辑态候选并行） ----
  async function loadDish(id: number) {
    loading.value = true
    loadFailed.value = false
    notFound.value = false
    try {
      const [detail, attrDefs] = await Promise.all([
        getDishDetail(id),
        // 编辑态候选**按需取一次**（仅进本编辑页取，且只含该菜现有维度 —— 端上不预取任何字典）；
        // 失败静默：各组候选为空 ⇒ 隐藏候选入口、仅留自定义输入，不阻塞纠错（文档边界口径）
        getDishEditAttributes(id).catch(() => []),
      ])
      dishName.value = detail.name
      // 锚定卡楼层段**同步走字典映射**（R40）：与下方楼层单元格同为汉字，避免同页一处汉字一处 `B1`；
      // 未命中字典时 `floorDisplay` 原样返回存储值，锚定卡照实显示（不做二次加工）。
      dishLocation.value = [detail.canteen, floorDisplay(detail.floor || '')[0], detail.stallName]
        .filter(Boolean)
        .join(' · ')

      form.name = detail.name
      // 详情 price 已由 API 层分 → 元（页面不换算金额）
      form.price = detail.price > 0 ? String(detail.price) : ''
      form.canteenName = detail.canteen
      form.floor = detail.floor || ''
      form.stallName = detail.stallName
      form.images = detail.images.slice(0, UGC_IMAGE_MAX)

      // 描述属性：维度名 / 当前值取自详情；候选与单多选取自编辑端点（按 fieldKey 对齐）
      form.attributes = (detail.attributes || [])
        .filter((item) => !!item.fieldKey)
        .map((item) => {
          const def = attrDefs.find((x) => x.fieldKey === item.fieldKey)
          const initial = Array.isArray(item.value)
            ? item.value.map(String)
            : String(item.value ?? '') ? [String(item.value)] : []
          return {
            fieldKey: item.fieldKey,
            name: item.name,
            valueType: def?.valueType ?? (Array.isArray(item.value) ? 'multi' : 'single'),
            candidates: def?.options ?? [],
            selected: initial,
          }
        })

      baseline.name = form.name
      baseline.price = form.price
      baseline.canteenName = form.canteenName
      baseline.floor = form.floor
      baseline.stallName = form.stallName
      baseline.images = [...form.images]
      baseline.attributes = {}
      for (const ed of form.attributes) baseline.attributes[ed.fieldKey] = [...ed.selected]
    } catch (e) {
      console.error('[correction] 菜品详情加载失败', e)
      // 4001（菜品不存在 / 已下架）不可重试；其余按可重试失败态呈现
      notFound.value = isResourceNotFound(e)
      loadFailed.value = !notFound.value
    } finally {
      loading.value = false
    }
  }

  function retryLoad() {
    if (!dishId.value) return
    return loadDish(dishId.value)
  }

  // ---- ③ 改动项（局部提交口径）：只上传「当前值 ≠ 预填值」的字段 ----
  function sameList(a: string[], b: string[]): boolean {
    if (a.length !== b.length) return false
    return a.every((x, i) => x === b[i])
  }

  /** 与基线逐项比对得出的**改动集合**（即提交请求体；空对象 = 无改动） */
  const diff = computed<DishCorrectionPayload>(() => {
    const payload: DishCorrectionPayload = {}
    if (!dishName.value) return payload
    if (form.name.trim() !== baseline.name) payload.name = form.name.trim()
    if (form.price.trim() !== baseline.price) payload.price = yuanToFen(Number(form.price.trim() || 0))
    if (form.canteenName.trim() !== baseline.canteenName) payload.canteenName = form.canteenName.trim()
    if (form.floor.trim() !== baseline.floor) payload.floor = form.floor.trim()
    if (form.stallName.trim() !== baseline.stallName) payload.stallName = form.stallName.trim()
    if (!sameList(form.images, baseline.images)) payload.images = form.images.filter(Boolean)
    const attrs: Record<string, string | string[]> = {}
    for (const ed of form.attributes) {
      if (sameList(ed.selected, baseline.attributes[ed.fieldKey] ?? [])) continue
      attrs[ed.fieldKey] = ed.valueType === 'multi' ? ed.selected : (ed.selected[0] ?? '')
    }
    if (Object.keys(attrs).length) payload.attributes = attrs
    return payload
  })

  /** 是否有改动（无改动 ⇒ 禁用提交：局部提交下无可提交内容，后端也会 400「未提交任何改动」） */
  const hasChange = computed(() => Object.keys(diff.value).length > 0)

  // ---- ④ 提交门禁 ----
  /** 售价格式：正数、至多两位小数（元），上限 9999 元 */
  const PRICE_PATTERN = /^(?:\d+)(?:\.\d{1,2})?$/

  function priceValid(): boolean {
    const p = form.price.trim()
    if (!p) return false
    const n = Number(p)
    return PRICE_PATTERN.test(p) && Number.isFinite(n) && n > 0 && n <= 9999
  }

  /** 必填项校验（五字段；返回首个未过项的中文名，全部通过返回 ''） */
  function firstMissingRequired(): string {
    const labels: Record<(typeof REQUIRED_KEYS)[number], string> = {
      name: '菜品名称',
      price: '售价',
      canteenName: '食堂名称',
      floor: '楼层',
      stallName: '档口名称',
    }
    for (const key of REQUIRED_KEYS) {
      if (key === 'price' ? !priceValid() : !form[key].trim()) return labels[key]
    }
    return ''
  }

  const canSubmit = computed(
    () =>
      !!dishId.value &&
      !loading.value &&
      // 退避期禁提交：后端 2 次/分钟，连点只会持续延长封锁（2026-09-29）
      !cooling() &&
      !firstMissingRequired() &&
      hasChange.value,
  )

  /** 置灰态点击提示（由表单提交区外热区 toast 透出） */
  const gateHint = computed(() => {
    if (!dishId.value) return '缺少菜品信息'
    if (loading.value) return '菜品信息加载中'
    if (cooling()) return `提交过于频繁，请 ${cooldownSeconds.value} 秒后再试`
    const missing = firstMissingRequired()
    // 售价格式错与「未填」是两种原因，提示分开给，便于定位
    if (missing === '售价' && form.price.trim()) return '售价要像 12.5 这样'
    if (missing) return `还差「${missing}」没填`
    if (!hasChange.value) return '你还没有改动任何信息'
    return ''
  })

  // ---- ⑤ 字段级错误 + 提交 ----
  const fieldErrors = reactive<Record<string, string>>({})
  const scrollIntoView = ref('')
  const submitting = ref(false)
  /** 提交失败原因（页面底部橙字提示；**保留已填内容**，不清空表单） */
  const submitError = ref('')
  /** 提交限频退避（2026-09-29）：后端 2 次/分钟，手滑连点会持续撞限频、越拖越长 */
  const { cooldownSeconds, cooling, handleError: handleRateLimit, clearCooldown } = useRateLimitCooldown()

  /** 字段级错误键 → 滚动定位锚点（id 由 CorrectionForm 各字段行承载） */
  const FIELD_ANCHORS: Record<string, string> = {
    'form.name': 'f-c-name',
    'form.price': 'f-c-price',
    'form.canteenName': 'f-c-canteenName',
    'form.floor': 'f-c-floor',
    'form.stallName': 'f-c-stallName',
  }

  function clearError(key: string) {
    delete fieldErrors[key]
    // 用户开始改内容即撤下上一次的失败提示（保留内容、只收起过期提示）
    submitError.value = ''
  }

  function markErrors(errs: Record<string, string>) {
    Object.keys(fieldErrors).forEach((k) => delete fieldErrors[k])
    const keys = Object.keys(errs)
    if (!keys.length) return
    keys.forEach((k) => { fieldErrors[k] = errs[k] })
    const target = FIELD_ANCHORS[keys[0]] || ''
    scrollIntoView.value = ''
    // 先清空再延时置值（同一锚点二次触发时 `scroll-into-view` 才能重新生效）；
    // 句柄与 `goBackTimer` 同口径 —— 离开页面统一在 `onUnload` 清理
    if (anchorTimer) clearTimeout(anchorTimer)
    if (target) anchorTimer = setTimeout(() => { scrollIntoView.value = target }, 50)
  }

  async function submit() {
    // 退避期：禁重复提交（弱网手滑连点会持续撞限频、把封锁越拖越长）
    if (submitting.value || cooling()) return

    const errs: Record<string, string> = {}
    if (!dishId.value) errs['form.name'] = '缺少菜品信息'
    else if (loading.value) errs['form.name'] = '菜品信息加载中'
    else {
      if (!form.name.trim()) errs['form.name'] = '菜名叫啥？填一下'
      if (!priceValid()) errs['form.price'] = form.price.trim() ? '售价要像 12.5 这样' : '填一下售价'
      if (!form.canteenName.trim()) errs['form.canteenName'] = '填一下食堂名称'
      if (!form.floor.trim()) errs['form.floor'] = '填一下楼层'
      if (!form.stallName.trim()) errs['form.stallName'] = '填一下档口名称'
      // 空改动 = 无可提交内容（后端亦 400「未提交任何改动」）——定位到首个必填项以免「点了没反应」
      if (!Object.keys(errs).length && !hasChange.value) {
        errs['form.name'] = '你还没有改动任何信息'
      }
    }
    if (Object.keys(errs).length) {
      markErrors(errs)
      uni.showToast({ title: Object.values(errs)[0], icon: 'none' })
      return
    }

    submitting.value = true
    submitError.value = ''
    try {
      // 局部提交：只上传改动项（dishId 在路径；price 元 → 分，金额红线）
      await submitDishCorrection(dishId.value, diff.value)
      clearCooldown()
      uni.showToast({ title: '提交成功，等待审核', icon: 'none' })
      if (goBackTimer) clearTimeout(goBackTimer)
      goBackTimer = setTimeout(goBack, 1500)
    } catch (e) {
      // 限频：请求层已弹过 toast（内含「请 N 秒后再试」），此处只进倒计时退避、不重复 toast；
      // 其它失败（业务 400 / 网络）同样**不改表单**，只把原因落在页面底部橙字提示上。
      handleRateLimit(e)
      submitError.value = e instanceof Error && e.message ? e.message : '提交失败，请稍后再试'
    } finally {
      submitting.value = false
    }
  }

  // ---- ⑥ 落点参数：`dishId` 必带（详情页底栏跳入），进页即预填 ----
  onLoad((opts?: Record<string, string>) => {
    const id = Number(opts?.dishId ?? 0)
    if (id > 0) {
      dishId.value = id
      void loadDish(id)
      return
    }
    // 缺 dishId：本页不提供跨菜品选择（入口唯一），直接提示并返回
    uni.showToast({ title: '缺少菜品信息', icon: 'none' })
    goBack()
  })

  /**
   * 对外只暴露**页面实际消费**的成员（「零消费即删」）：
   * `dishId` / `cooldownSeconds` / `cooling` / `hasChange` 均只在编排内部使用
   * （`cooling` + `cooldownSeconds` 已由 `gateHint` 的「请 N 秒后再试」文案对外透出，
   * `hasChange` 已并入 `canSubmit` / `gateHint`），故不再导出，避免死接口。
   */
  return {
    goBack,
    dishName,
    dishLocation,
    loading,
    notFound,
    loadFailed,
    retryLoad,
    form,
    fieldErrors,
    scrollIntoView,
    submitting,
    /** 提交失败原因（页面底部橙字；空 = 无提示） */
    submitError,
    clearError,
    canSubmit,
    gateHint,
    submit,
  }
}
