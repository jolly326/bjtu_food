<template>
  <!--
    AttributeGroup（correction 包内私有，**核心复用**）—— 单个「描述属性维度」的**字段行**入口。

    R42 形态（方案①「字段行 + 底部弹层」，UI 稿区域2）：
      与「楼层」完全同构的一行 —— 左维度名 / 右当前值摘要 / 单多选标注 / 最右箭头 / 底部横线；
      **整行可点 ⇒ 打开 `AttributePickerSheet`**。候选全览、多选勾选、清空、自定义输入**全部在弹层内**，
      就地不再呈现候选 chip、也不再呈现自定义输入框
      ——「同一维度两处都能选、但能选的东西不一样」是误导性的，已整条废除。

    数据驱动：维度名 / 当前值 / 单多选取自详情，候选取自编辑态端点（端上零硬编码维度、零字典）。
    对外契约与 R41 **完全一致**（props 与 `change` 不变）⇒ `CorrectionForm` / `useCorrection` 零改动。

    `first`（P1 返工）：组间距**不再**用 `.ag:first-child` —— `.ag` 是子组件根节点，mp-weixin 下每个
    实例都被包在各自的宿主节点里 ⇒ `:first-child` 会对**每一组**命中、组间距整体归零。
    改为父级 `v-for` 按 index 显式传 `first`（仅首组去上边距），确定性生效、跨端一致。
  -->
  <view class="ag" :class="{ 'ag--first': first }">
    <view class="ag-row">
      <text class="ag-label">{{ name }}</text>
      <!-- picker 单元格（同 `.row-field--picker`）：整行可点、无 input、不可键入 ⇒ 无聚焦态，
           开合本身由 BaseSheet 表达（与楼层单元格同源口径） -->
      <view
        class="ag-field"
        role="button"
        :aria-label="ariaLabel"
        hover-class="ag-field--pressed"
        hover-stay-time="80"
        @tap="openPicker"
      >
        <text class="ag-text" :class="{ 'ag-text--ph': !hasValue }">{{ summary }}</text>
        <!-- 单 / 多选标注常驻（不随摘要长短消失、不因省略号挪位）：`flex: none` ⇒ 摘要再长也只省略自己 -->
        <text class="ag-tip">{{ valueType === 'single' ? '单选' : '多选' }}</text>
        <IconSvg name="arrow-down" :size="24" :color="COLOR_MAP['text-tertiary']" />
      </view>
    </view>

    <!-- 属性选择弹层（该维度的唯一选择场所）：首次打开置 `pickerMounted` 常驻，
         避免候选池达数十项时每次开合都重建子树；开合动画仍由 `visible` 驱动 -->
    <AttributePickerSheet
      v-if="pickerMounted"
      :visible="pickerOpen"
      :name="name"
      :value-type="valueType"
      :selected="selected"
      :candidates="candidates"
      :selected-style="selectedStyle"
      @close="pickerOpen = false"
      @update="onSheetUpdate"
    />
  </view>
</template>
<script setup lang="ts">
/**
 * AttributeGroup —— 单维度属性字段行（当前值摘要 + 弹层入口）
 *
 * 接口口径（UI 稿「`AttributeGroup` 入参」）：
 * `props: { fieldKey, name, valueType, selected, candidates, first }`，
 * `emits: change(fieldKey, selected)` —— **本组件不直接改 props**，一律回抛新数组由父级写回，
 * 保证「表单值唯一真源在父级编排（`useCorrection`）」。
 *
 * 颜色全走语义 token（禁裸 hex）；图标走 `IconSvg`（禁 emoji / 文本当图标）；
 * 事件统一 `@tap`；按压用 hover-class 透明度微降（禁 `transform: scale`）。
 */
import { computed, nextTick, ref } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import AttributePickerSheet from './AttributePickerSheet.vue'
import { COLOR_MAP } from '@/theme/tokens'

/**
 * 摘要最多直出前 3 项、其余折成「等 N 项」：
 * 超过 3 项仍全列会把右端的「单 / 多选」标注与箭头挤走、或诱导整行换行（行高一跳 ⇒ 表单抖）。
 */
const SUMMARY_MAX = 3

const props = withDefaults(defineProps<{
  /** 维度键（camelCase）＝ 提交时 `attributes` 的键 */
  fieldKey: string
  /** 维度中文名（如「食材」），同时是弹层标题与占位文案的词根 */
  name: string
  /** `single`（单选）｜ `multi`（多选）—— 驱动标注文案与选中态视觉档 */
  valueType: 'single' | 'multi'
  /** 当前已选（预填原始值；`single` 至多 1 项） */
  selected: string[]
  /** 参考候选（编辑态端点 `options`；为空 ⇒ 弹层内明示「暂无参考候选，可直接手动输入」） */
  candidates: string[]
  /** 是否首个分组（由父级 `v-for` 按 index 显式传入；首组去上边距） */
  first?: boolean
}>(), {
  first: false,
})

const emit = defineEmits<{
  /** 选区变化：回抛**新的**已选数组（弹层确认后转发；父级写回，组件不改 props） */
  (e: 'change', fieldKey: string, selected: string[]): void
}>()

const hasValue = computed(() => props.selected.length > 0)
/**
 * 字段行摘要（不点开也能核对全貌 —— 这是「统一进弹层」多出来的那一次点按的主要补偿）：
 * · 空值 ⇒ 灰字「请选择<维度名>」（与 input 占位同档）；
 * · 单选 ⇒ 当前值原文；
 * · 多选 ⇒ 前 3 项顿号连接，超出折成「等 N 项」（`N` = 总数，不是余数 ⇒ 不产生歧义）。
 */
const summary = computed(() => {
  if (!hasValue.value) return `请选择${props.name}`
  if (props.valueType === 'single') return props.selected[0]
  const head = props.selected.slice(0, SUMMARY_MAX).join('、')
  return props.selected.length > SUMMARY_MAX ? `${head} 等${props.selected.length}项` : head
})
/** 读屏文案：维度 + 当前值 + 模式（摘要在 WXML 里被省略号截断，读屏要给全量） */
const ariaLabel = computed(() => {
  const cur = hasValue.value ? props.selected.join('、') : '未选择'
  return `${props.name}，当前${cur}，${props.valueType === 'single' ? '单选' : '可多选'}，共 ${props.candidates.length} 项参考候选，点按选择`
})
/** 选中态视觉档：单选组实心（solid）、多选组浅底（soft）——**两套不得互串**，下发给弹层内的 chip */
const selectedStyle = computed<'soft' | 'solid'>(() => (props.valueType === 'single' ? 'solid' : 'soft'))

/* ===== 弹层编排 =====
   `pickerMounted` 首次打开后置真并**常驻**（候选池可达数十项，每次开合重建子树代价高），
   开合动画仍由 `visible` 驱动 ⇒ 不能在挂载的同一 tick 内置真（BaseSheet 依赖 visible 的变化做动画）。 */
const pickerMounted = ref(false)
const pickerOpen = ref(false)

/** 打开弹层（本行恒可点：候选为空时也一样要能进弹层手动输入） */
function openPicker() {
  pickerMounted.value = true
  nextTick(() => {
    pickerOpen.value = true
  })
}

/** 弹层回抛新已选（多选确认 / 清空、单选点项 / 添加自定义）⇒ 转发 change；表单真源恒在父级编排 */
function onSheetUpdate(next: string[]) {
  emit('change', props.fieldKey, next)
}
</script>

<style scoped>
/* 组间距：与本页 `.row` 的纵向节距同档（首组由父级下发 `first` 去掉，避免与 `.attrs` 上边距叠成双份） */
.ag {
  margin-top: var(--spacing-xs);
}
.ag--first {
  margin-top: 0;
}
/* 行容器：与 `CorrectionForm` 的 `.row` 同节距（上 xs / 下 sm），保证属性四行与上方五个字段**同一节奏** */
.ag-row {
  display: flex;
  align-items: center;
  padding: var(--spacing-xs) 0 var(--spacing-sm);
}
/* 标签列：宽 160rpx（与 `.row-label` 同档，4 字维度名 + 无必填星 ⇒ 不换行）；色档同为次级棕 */
.ag-label {
  flex: none;
  width: var(--form-label-width);
  font-size: var(--font-aux);
  font-weight: var(--weight-medium);
  color: var(--text-secondary);
}
/* picker 单元格（同 `.row-field--picker`）：88rpx = 44pt 触达 + 仅底部横线（禁全包围矩形框）；
   整行可点 ⇒ 88rpx 即**独立可点件**基线 */
.ag-field {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: var(--tap-target-size);
  border-bottom: 1rpx solid var(--border-color);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.ag-field--pressed {
  background: var(--bg-soft);
}
/* 摘要：单行省略（`flex: 1` + `min-width: 0` ⇒ 先省略摘要，标注与箭头恒不被挤走） */
.ag-text {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 空值占位（灰字，与 `.row-ph` / `.row-picker-text--ph` 同档；`text` 不消费 placeholder-class ⇒ 显式给类） */
.ag-text--ph {
  color: var(--text-placeholder);
}
/* 单 / 多选标注：最小字档 + 占位灰（信息性、非强调），`flex: none` ⇒ 不参与省略 */
.ag-tip {
  flex: none;
  font-size: var(--font-tiny);
  color: var(--text-placeholder);
}
</style>