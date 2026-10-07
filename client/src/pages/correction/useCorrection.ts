/**
 * 楼层受控字典（UI 稿「楼层控件」）：**值即汉字**，端上常量。
 *
 * 真源见 `docs/schema/stall.md`（`负一层` / `一层` / `二层` / `三层` / `四层`）：
 * **存储值 = 显示值** ⇒ 端上**零映射、零兜底**（字典外值不存在 —— 新增楼层免迁移，
 * 删除 / 改名须先把该楼层的档口迁到其它楼层再改字典）。
 */
export const FLOOR_OPTIONS: readonly string[] = ['负一层', '一层', '二层', '三层', '四层']

/**
 * useCorrection —— 菜品问题反馈页（pages/correction/index.vue）编排逻辑
 *
 * **独立页面**（与「意见反馈」解耦）：入口**唯一** —— 菜品详情页信息卡名称行「菜品有问题?」；
 * 进页即按导航参数 `dishId` **锚定该菜品**（页内不提供切换入口），拉详情预填全部字段，
 * 用户只改错的地方 → **只提交改动过的项**（局部提交 / patch），未改动的不上传。
 *
 * 描述属性走**动态属性模型**（值即中文）：维度名与当前值取自 `GET /dishes/{id}`；
 * 编辑态参考候选**按需**取 `GET /dishes/{id}/attributes`（仅该菜现有维度；候选只是提示，
 * 用户恒可自由输入候选之外的新值）；提交经 `attributes` 对象（键 = 维度 ID 字符串，仅改动维度）。
 * 展示侧中文由详情直出，**端上零翻译 / 零字典**。
 *
 * ⚠️ 全部逻辑在函数体内执行：由页面在 <script setup> 中同步调用 useCorrection()，
 * 使 onLoad/onUnload 均在组件实例上下文中注册（模块顶层注册会报 "no active component instance"）。
 */
import { ref, reactive, computed } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { createDishCorrection } from '@/api/feedback'
import { getDishDetail, listDishEditAttributes } from '@/api/dish'
import { isResourceNotFound } from '@/api/errors'
import { useRateLimitCooldown } from '@/composables/useRateLimitCooldown'
import type { DishCorrectionPayload, DishProblemType } from '@/types/feedback'
import { buildCorrectionDiff, priceValid as isPriceValid, snapshotAttributes } from './correctionDiff'
import { backToHome } from '@/utils/back'
import { joinLocation } from '@/utils/dish'
import { toastInfo, toastSuccess } from '@/utils/error'
import { CORRECTION_IMAGE_MAX, GONE_IMAGE_MAX } from '@/constants/ugc'

/** 描述属性编辑项（表单内一个维度的可编辑模型） */
export interface AttributeEditor {
  /** 维度 ID ＝ 提交时 `attributes` 的键（字符串形态） */
  dimensionId: number
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
   * **问题类型**（进页第一屏由 `TypePicker` 选定）。
   *
   * <p>**先选类型再进表单** —— 两类的表单与字段完全不同：
   * `field` 要拉详情预填七个改动项；`gone` 一键提交、无需任何字段。
   *
   * <p>**初值空串 = 尚未选择**：UI 稿硬口径「**不选类型不得进入表单**」
   * （避免进错表单再回退），故不给默认类型、未选时不渲染任何表单。
   */
  const problemType = ref<DishProblemType | ''>('')
  /** 是否已选类型（未选 ⇒ 只渲染第一屏类型选择） */
  const typeChosen = computed(() => problemType.value !== '')
  /** 是否 `gone` 型（模板分派用，避免散落的 `problemType === 'gone'` 判断） */
  const isGone = computed(() => problemType.value === 'gone')
  /**
   * 预填数据源（`GET /dishes/{id}`）；预填未完成时禁止提交。
   * 初值 **false**：类型先行 ⇒ 未选 `field` 之前**不发请求**（`gone` 型全程不拉详情），
   * 由 {@link selectType} 在首次切到 `field` 时才置 true 并拉取。
   */
  const loading = ref(false)
  /** 菜品不存在 / 已下架（后端 4001）：与网络失败区别对待，只给返回 */
  const notFound = ref(false)
  /** 预填请求失败（网络 / 服务端故障，可重试） */
  const loadFailed = ref(false)
  /** 锚定卡展示：菜品名（只读） */
  const dishName = ref('')
  /** 锚定卡展示：「食堂 · 楼层 · 档口」（三段；楼层段原样展示 `floor` 汉字） */
  const dishLocation = ref('')

  /**
   * 金额口径：`price` 为**元字符串**（input 展示/编辑），提交时经 `yuanToFen` 转分（金额红线）。
   * 食堂名 / 档口名：自由文本（预填详情值，可改）。
   * 楼层：**值即汉字**（受控字典项原样存放）—— diff 比对 / 必填校验 / 提交组装三处**均无需感知映射**。
   */
  const form = reactive<CorrectionFormModel>({
    name: '',
    price: '',
    canteenName: '',
    /** 楼层（契约 `DishCorrectionReq.floor`；值即汉字、归属档口，仅楼层改动也属有效改动） */
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

  /**
   * `CorrectionForm` 的 `update:model` 落点：子件以整值替换回抛，这里浅合并回 reactive 唯一真源
   * （`attributes` 整组替换即编辑项数组；基线 / diff 均按值比对，不依赖对象同一性）。
   */
  function updateForm(next: CorrectionFormModel) {
    Object.assign(form, next)
  }

  /**
   * `field` 型详情**是否已发起过**预填请求（唯一防抖锚点）。
   * 用户在两项类型间来回点按不会重复打接口；失败态由 `retryLoad` 显式重试。
   */
  const detailRequested = ref(false)

  // ---- ② 进页预填（详情 + 编辑态候选并行） ----
  async function loadDish(id: number) {
    detailRequested.value = true
    loading.value = true
    loadFailed.value = false
    notFound.value = false
    try {
      const [detail, attrDefs] = await Promise.all([
        getDishDetail(id),
        // 编辑态候选**按需取一次**（仅进本编辑页取，且只含该菜现有维度 —— 端上不预取任何字典）；
        // 失败静默：各组候选为空 ⇒ 隐藏候选入口、仅留自定义输入，不阻塞纠错（文档边界口径）
        listDishEditAttributes(id).catch(() => []),
      ])
      dishName.value = detail.name
      // 锚定卡楼层段**原样展示 `floor`**（值即汉字）—— 与下方楼层单元格同为「值即显示值」，全链零映射。
      dishLocation.value = joinLocation(
        detail.canteen,
        detail.floor || '',
        detail.stallName,
      )

      form.name = detail.name
      // 详情 price 已由 API 层分 → 元（页面不换算金额）
      form.price = detail.price > 0 ? String(detail.price) : ''
      form.canteenName = detail.canteen
      form.floor = detail.floor || ''
      form.stallName = detail.stallName
      form.images = detail.images.slice(0, CORRECTION_IMAGE_MAX)

      // 描述属性：维度名 / 当前值取自详情；候选与单多选取自编辑端点（按 dimensionId 对齐）
      form.attributes = (detail.attributes || [])
        .filter((item) => !!item.dimensionId)
        .map((item) => {
          const def = attrDefs.find((x) => x.dimensionId === item.dimensionId)
          const initial = Array.isArray(item.value)
            ? item.value.map(String)
            : String(item.value ?? '') ? [String(item.value)] : []
          return {
            dimensionId: item.dimensionId,
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
      baseline.attributes = snapshotAttributes(form.attributes)
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

  /**
   * 选定 / 切换**问题类型**（第一屏单选，也允许在表单上方改选 ——「表单随类型切换」）。
   *
   * <p>**懒加载**：只有切到 `field` 时才拉详情预填（一次），`gone` 型**全程不拉**
   * （硬约束 3：少一次网络请求 = 更快 = 更可能提交成功）。
   *
   * <p>提交中忽略点按：换表单会让「正在提交的载荷」与「眼前的界面」错位。
   */
  function selectType(next: DishProblemType) {
    if (submitting.value) return
    problemType.value = next
    if (next === 'field' && !detailRequested.value && dishId.value > 0) {
      void loadDish(dishId.value)
    }
  }

  // ---- ③ 改动项（局部提交口径）：只上传「当前值 ≠ 预填值」的字段 ----
  /**
   * 与基线逐项比对得出的**改动集合**（即提交请求体；空对象 = 无改动）。
   *
   * 比对口径（有序/无序、金额元→分、维度集合）已抽至 correctionDiff.ts：
   * 那里是无副作用纯函数，可被单测直接覆盖；此处只负责喂入响应式数据。
   *
   * ⚠️ **必须携带 `type`**（后端**必填**，不做「不传即 field」的兜底）。
   * 本页当前只实现 `field`（信息有误）形态；`gone` 型「一键提交」由独立的类型选择流程提交
   * （载荷仅 `{ type:'gone', note?, images? }`，无差异项）。
   */
  const diff = computed<DishCorrectionPayload>(() => ({
    type: 'field',
    ...buildCorrectionDiff(dishName.value, form, baseline),
  }))

  /**
   * 是否有改动（无改动 ⇒ 禁用提交：局部提交下无可提交内容，后端也会 400「未提交任何改动」）。
   *
   * ⚠️ **判据须排除 `type`** —— `diff` 已固定携带 `type:'field'`（后端必填），
   * 若直接用 `Object.keys().length` 判空，恒为「有改动」⇒ 提交按钮永远可点 ⇒ 空改动会被后端 400。
   */
  const hasChange = computed(
    () => Object.keys(diff.value).some((k) => k !== 'type'),
  )

  // ---- ④ 提交门禁 ----

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
      if (key === 'price' ? !isPriceValid(form.price) : !form[key].trim()) return labels[key]
    }
    return ''
  }

  const canSubmit = computed(
    () =>
      !!dishId.value &&
      !loading.value &&
      // 退避期禁提交：后端 2 次/分钟，连点只会持续延长封锁
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
  /** 提交限频退避：后端 2 次/分钟，手滑连点会持续撞限频、越拖越长 */
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

  /** 提交前校验：返回字段级错误集（空 = 通过）。缺 ID / 加载中只记一条、跳过字段校验 */
  function collectSubmitErrors(): Record<string, string> {
    const errs: Record<string, string> = {}
    if (!dishId.value) { errs['form.name'] = '缺少菜品信息'; return errs }
    if (loading.value) { errs['form.name'] = '菜品信息加载中'; return errs }
    if (!form.name.trim()) errs['form.name'] = '菜名叫啥？填一下'
    if (!isPriceValid(form.price)) errs['form.price'] = form.price.trim() ? '售价要像 12.5 这样' : '填一下售价'
    if (!form.canteenName.trim()) errs['form.canteenName'] = '填一下食堂名称'
    if (!form.floor.trim()) errs['form.floor'] = '填一下楼层'
    if (!form.stallName.trim()) errs['form.stallName'] = '填一下档口名称'
    // 空改动 = 无可提交内容（后端亦 400「未提交任何改动」）——定位到首个必填项以免「点了没反应」
    if (!Object.keys(errs).length && !hasChange.value) errs['form.name'] = '你还没有改动任何信息'
    return errs
  }

  async function submit() {
    // 退避期：禁重复提交（弱网手滑连点会持续撞限频、把封锁越拖越长）
    if (submitting.value || cooling()) return

    const errs = collectSubmitErrors()
    if (Object.keys(errs).length) {
      markErrors(errs)
      toastInfo(Object.values(errs)[0])
      return
    }

    submitting.value = true
    submitError.value = ''
    try {
      // 局部提交：只上传改动项（dishId 在路径；price 元 → 分，金额红线）
      await createDishCorrection(dishId.value, diff.value)
      clearCooldown()
      toastSuccess('提交成功，等待审核')
      if (goBackTimer) clearTimeout(goBackTimer)
      goBackTimer = setTimeout(backToHome, 1500)
    } catch (e) {
      // 限频：请求层已弹过 toast（内含「请 N 秒后再试」），此处只进倒计时退避、不重复 toast；
      // 其它失败（业务 400 / 网络）同样**不改表单**，只把原因落在页面底部橙字提示上。
      handleRateLimit(e)
      submitError.value = e instanceof Error && e.message ? e.message : '提交失败，请稍后再试'
    } finally {
      submitting.value = false
    }
  }

  // ==================== `type=gone`（已经下架）· 一键提交 ====================

  /**
   * gone 型的选填补充（note ≤200 字 / images ≤3 张）。
   * <p>**均可为空** —— 提交按钮恒可用（见 {@link GoneForm} 的硬约束 1）。
   */
  const goneNote = ref('')
  const goneImages = ref<string[]>([])

  /**
   * gone 型提交：载荷**只含** `{type, note?, images?}`，**不带任何差异项**。
   *
   * <p>与 {@link submit} 的区别：
   * <ul>
   *   <li>**无字段门禁**（`collectSubmitErrors` 那套必填校验不适用）；</li>
   *   <li>**不做 diff 比对**（无基线可比）；</li>
   *   <li>失败**保留补充内容**（用户填的说明/图不丢，便于改后重试）。</li>
   * </ul>
   */
  async function submitGone() {
    if (submitting.value || cooling()) return
    submitting.value = true
    submitError.value = ''
    try {
      const note = goneNote.value.trim()
      const images = goneImages.value.slice(0, GONE_IMAGE_MAX)
      await createDishCorrection(dishId.value, {
        type: 'gone',
        // 空值不传（后端允许全不传；传空串会被当作"填了空白"）
        ...(note ? { note } : {}),
        ...(images.length ? { images } : {}),
      })
      clearCooldown()
      toastSuccess('已收到，感谢反馈')
      if (goBackTimer) clearTimeout(goBackTimer)
      goBackTimer = setTimeout(backToHome, 1500)
    } catch (e) {
      handleRateLimit(e)
      submitError.value = e instanceof Error && e.message ? e.message : '提交失败，请稍后再试'
    } finally {
      submitting.value = false
    }
  }

  // ---- ⑥ 落点参数：`dishId` 必带（详情页信息卡「菜品有问题?」跳入）；`type` 可选 ----
  onLoad((opts?: Record<string, string>) => {
    const id = Number(opts?.dishId ?? 0)
    if (!(id > 0)) {
      // 缺 dishId：本页不提供跨菜品选择（入口唯一），直接提示并返回
      toastInfo('缺少菜品信息')
      backToHome()
      return
    }
    dishId.value = id
    // 导航显式带 `type` ⇒ 预选该类型（供将来「从别处直接跳某型」用）；
    // 常规入口（`correctionUrl(dishId)`）不带 ⇒ 进页停在**类型选择第一屏**，由用户选。
    if (opts?.type === 'gone') selectType('gone')
    else if (opts?.type === 'field') selectType('field')
  })

  /**
   * 对外只暴露**页面实际消费**的成员（「零消费即删」）：
   * `dishId` / `cooldownSeconds` / `cooling` / `hasChange` 均只在编排内部使用
   * （`cooling` + `cooldownSeconds` 已由 `gateHint` 的「请 N 秒后再试」文案对外透出，
   * `hasChange` 已并入 `canSubmit` / `gateHint`），故不再导出，避免死接口。
   */
  return {
    /** 全页唯一返回实现（有返回栈 navigateBack；无返回栈 reLaunch 首页） */
    goBack: backToHome,
    /** 问题类型（`TypePicker` 双向；决定下方渲染哪套表单；空串 = 未选） */
    problemType,
    /** 是否已选类型（未选 ⇒ 第一屏只渲染类型选择，不渲染表单） */
    typeChosen,
    /** 是否 `gone` 型（模板分派：`GoneForm` vs `CorrectionForm`） */
    isGone,
    /** 选定 / 切换问题类型（`field` 首次选中时才拉详情预填） */
    selectType,
    dishName,
    dishLocation,
    loading,
    notFound,
    loadFailed,
    retryLoad,
    form,
    /** `CorrectionForm` 的 `update:model` 落点（子件整值替换 → 浅合并回真源） */
    updateForm,
    fieldErrors,
    scrollIntoView,
    submitting,
    /** 提交失败原因（页面底部橙字；空 = 无提示） */
    submitError,
    clearError,
    canSubmit,
    gateHint,
    /** `field` 型提交（局部提交：只带改动项） */
    submit,
    /** gone 型选填补充（v-model 双向） */
    goneNote,
    goneImages,
    /** `gone` 型一键提交（无字段门禁；载荷只含 `{type, note?, images?}`） */
    submitGone,
  }
}
