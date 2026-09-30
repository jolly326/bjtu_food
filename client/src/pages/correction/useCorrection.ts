/**
 * useCorrection —— 菜品信息纠错页（pages/correction/index.vue）编排逻辑
 *
 * **独立页面**（与「意见反馈」解耦）：入口**唯一** —— 菜品详情页底栏「反馈错误」；
 * 进页即按导航参数 `dishId` **预绑定该菜品**（表单内不允许切换），拉详情预填全部字段，
 * 用户只改错的地方 → **只提交改动过的项**（局部提交 / patch），未改动的不上传。
 *
 * 描述属性走**动态属性模型**（值即中文）：编辑态参考候选按需取 `GET /dishes/{id}/attributes`
 * （只含该菜现有维度；候选仅为提示，可自由输入新值）；提交经 `attributes` 对象（键 = 维度 `fieldKey`）。
 * 展示侧中文由 `GET /dishes/{id}` 的 `attributes[].value` 直出，**端上零翻译**。
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
import { joinLocation } from '@/utils/dish'

/** 描述属性编辑项（表单内一个维度的可编辑模型） */
export interface AttributeEditor {
  /** 维度键（camelCase）＝ 提交时 `attributes` 的键 */
  fieldKey: string
  /** 维度中文名（来自详情 `attributes[].name`） */
  name: string
  /** 取值类型：`single`（单值）｜ `multi`（多值） */
  valueType: 'single' | 'multi'
  /** 参考候选值（中文文本，按频次倒序）；仅为提示，**恒可自由输入新值** */
  options: string[]
  /** 当前选中的机器值（`single` 至多 1 项） */
  selected: string[]
}

export function useCorrection() {
  /** 全页唯一返回实现：有返回栈时 navigateBack；无返回栈才 reLaunch 首页 */
  function goBack() {
    if (getCurrentPages().length > 1) uni.navigateBack()
    else backToHome()
  }

  /** 提交成功自动返回定时器句柄：离开页面时清理，避免返回打到已销毁页 */
  let goBackTimer: ReturnType<typeof setTimeout> | null = null
  onUnload(() => {
    if (goBackTimer) clearTimeout(goBackTimer)
  })

  // ---- ① 绑定菜品与表单字段 ----
  /** 进页即由导航参数预绑定（表单内不可切换） */
  const dishId = ref(0)
  /** 预填数据源（`GET /dishes/{id}`）；预填未完成时禁止提交 */
  const loading = ref(false)
  /** 菜品不存在 / 已下架（后端 4001）：与网络失败区别对待，只给返回 */
  const notFound = ref(false)
  /** 预填请求失败（网络 / 服务端故障，可重试） */
  const loadFailed = ref(false)
  /** 已绑定菜品的展示信息（名称 + 位置，取自详情，只读） */
  const dishName = ref('')
  const dishLocation = ref('')

  /**
   * 金额口径：`price` 为**元字符串**（input 展示/编辑），提交时经 yuanToFen 转分（金额红线）。
   * 食堂名 / 档口：自由文本（预填详情值，可改）。
   */
  const form = reactive({
    name: '',
    price: '',
    canteenName: '',
    stallName: '',
    /** 图片（预填菜品首图，可增删，≤3 张） */
    images: [] as string[],
    /** 描述属性编辑项（维度顺序 = 详情返回顺序） */
    attributes: [] as AttributeEditor[],
  })

  /** 预填快照：判「有无改动」的基线（改动项 = 当前值 ≠ 基线值） */
  const baseline = reactive({
    name: '',
    price: '',
    canteenName: '',
    stallName: '',
    images: [] as string[],
    attributes: {} as Record<string, string[]>,
  })

  // ---- ② 进页预填（详情 + 编辑态属性选项并行） ----
  async function loadDish(id: number) {
    loading.value = true
    loadFailed.value = false
    notFound.value = false
    try {
      const [detail, attrDefs] = await Promise.all([
        getDishDetail(id),
        // 编辑态候选值**按需**取；失败静默（维度退化为自由文本，不阻塞纠错）
        getDishEditAttributes(id).catch(() => []),
      ])
      dishName.value = detail.name
      dishLocation.value = joinLocation(detail.canteen, detail.stallName)

      form.name = detail.name
      // 详情 price 已由 API 层分 → 元
      form.price = detail.price > 0 ? String(detail.price) : ''
      form.canteenName = detail.canteen
      form.stallName = detail.stallName
      form.images = detail.images.slice(0, UGC_IMAGE_MAX)

      // 描述属性：维度与当前值取自详情；候选值取自编辑端点（按 fieldKey 对齐）
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
            options: def?.options ?? [],
            selected: initial,
          }
        })

      baseline.name = form.name
      baseline.price = form.price
      baseline.canteenName = form.canteenName
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
  const PRICE_PATTERN = /^(?:\d+)(?:\.\d{1,2})?$/

  function priceValid(): boolean {
    const p = form.price.trim()
    if (!p) return false
    const n = Number(p)
    return PRICE_PATTERN.test(p) && Number.isFinite(n) && n > 0 && n <= 9999
  }

  const canSubmit = computed(
    () =>
      !!dishId.value &&
      !loading.value &&
      // 退避期禁提交：后端 2 次/分钟，连点只会持续延长封锁（2026-09-29）
      !cooling() &&
      !!form.name.trim() &&
      priceValid() &&
      !!form.canteenName.trim() &&
      !!form.stallName.trim() &&
      hasChange.value,
  )

  const gateHint = computed(() => {
    if (!dishId.value) return '缺少菜品信息'
    if (loading.value) return '菜品信息加载中'
    if (!form.name.trim()) return '菜名叫啥？填一下'
    if (!priceValid()) return '价格要像 12.5 这样'
    if (!form.canteenName.trim()) return '填一下食堂名'
    if (!form.stallName.trim()) return '填一下档口'
    if (!hasChange.value) return '你还没有改动任何信息'
    return ''
  })

  /** 置灰态点击提示：AppButton 在 disabled 时不 emit press，由 .submit-area 外层热区兜底 */
  function onSubmitAreaTap() {
    if (!canSubmit.value) uni.showToast({ title: gateHint.value, icon: 'none' })
  }

  // ---- ⑤ 字段级错误 + 提交 ----
  const fieldErrors = reactive<Record<string, string>>({})
  const scrollIntoView = ref('')
  const submitting = ref(false)
  /** 提交限频退避（2026-09-29）：后端 2 次/分钟，手滑连点会持续撞限频、越拖越长 */
  const { cooldownSeconds, cooling, handleError: handleRateLimit, clearCooldown } = useRateLimitCooldown()

  function clearError(key: string) {
    delete fieldErrors[key]
  }

  function markErrors(errs: Record<string, string>) {
    Object.keys(fieldErrors).forEach((k) => delete fieldErrors[k])
    const keys = Object.keys(errs)
    if (!keys.length) return
    keys.forEach((k) => { fieldErrors[k] = errs[k] })
    const idMap: Record<string, string> = {
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
    // 退避期：禁重复提交（弱网手滑连点会持续撞限频、把封锁越拖越长）
    if (submitting.value || cooling()) return

    const errs: Record<string, string> = {}
    if (!dishId.value) errs['form.name'] = '缺少菜品信息'
    else if (loading.value) errs['form.name'] = '菜品信息加载中'
    else {
      if (!form.name.trim()) errs['form.name'] = '菜名叫啥？填一下'
      if (!priceValid()) errs['form.price'] = '价格要像 12.5 这样'
      if (!form.canteenName.trim()) errs['form.canteenName'] = '填一下食堂名'
      if (!form.stallName.trim()) errs['form.stallName'] = '填一下档口'
      if (!hasChange.value) errs['form.name'] = '你还没有改动任何信息'
    }
    if (Object.keys(errs).length) {
      markErrors(errs)
      uni.showToast({ title: Object.values(errs)[0], icon: 'none' })
      return
    }

    submitting.value = true
    try {
      // 局部提交：只上传改动项（dishId 在路径；price 元 → 分，金额红线）
      await submitDishCorrection(dishId.value, diff.value)
      clearCooldown()
      uni.showToast({ title: '已提交，感谢反馈', icon: 'none' })
      if (goBackTimer) clearTimeout(goBackTimer)
      goBackTimer = setTimeout(goBack, 1500)
    } catch (e) {
      // 限频：请求层已弹过 toast（内含「请 N 秒后再试」），此处只进倒计时退避、**不再重复提示**
      if (!handleRateLimit(e)) {
        uni.showToast({ title: e instanceof Error && e.message ? e.message : '没发出去，再试一次', icon: 'none' })
      }
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

  return {
    goBack,
    dishId,
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
    /** 限频剩余秒数（>0 时模板应禁用提交并展示倒计时） */
    cooldownSeconds,
    /** 是否处于限频退避期 */
    cooling,
    clearError,
    canSubmit,
    gateHint,
    hasChange,
    onSubmitAreaTap,
    submit,
  }
}
