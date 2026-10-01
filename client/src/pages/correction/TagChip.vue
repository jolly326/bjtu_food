<template>
  <!--
    TagChip（correction 包内私有）—— 属性「候选弹层」里的候选项 / 已选项标签。

    **R42 起本组件只服务 `AttributePickerSheet`**：主表单的属性维度已改为「字段行 + 底部弹层」，
    就地不再呈现任何 chip ⇒ 旧的「已选区删除叉」「不可点的已选 chip」两档一并删除，
    不给仓库留无人消费的分支（`closable` / `pickable=false` 曾只被主表单已选区使用）。

    两态语义（UI 稿「属性编辑区」· 两套选中态由 `selectedStyle` 分流，**禁互串**）：
     · `selected` + `soft`（多选组：食材 / 口味）= 主色浅底 + 主色描边 + 深主色文字；
     · `selected` + `solid`（单选组：饮食属性 / 冷热）= **实心主色底 + 白字**；
     · `candidate`（未选中）= 次级浅底 + 正文棕文字 + **2rpx 透明描边**
       （与选中态等高、不跳位；描边是「选中」独占的视觉标识）。
    `checkable`：选中态附勾选标识 —— 弹层内没有删除叉，「已选」不能只靠底色
    （色觉障碍 / 低亮度下不可辨）。点标签体 = 选择 / 取消，语义由父级决定，本组件只上报 `pick`。

    触达：弹层内 chip 是**独立可点件** ⇒ 视觉高恒 ≥88rpx（= 44pt，1rpx = 0.5pt 换算）；
    旧「主表单密集 chip 67rpx」的登记例外**已随主表单去 chip 化作废**（UI 稿触达口径同步）。
  -->
  <view
    class="tag-chip"
    :class="chipClass"
    role="button"
    :aria-label="ariaLabel"
    :aria-pressed="variant === 'selected'"
    hover-class="tag-chip--pressed"
    hover-stay-time="80"
    @tap="emit('pick')"
  >
    <text class="tag-chip-text">{{ label }}</text>
    <IconSvg v-if="checkable && variant === 'selected'" name="check" :size="24" :color="selectedIconColor" />
  </view>
</template>

<script setup lang="ts">
/**
 * TagChip —— 候选弹层内的属性标签（选中态 / 候选态）
 *
 * 消费方：仅 `AttributePickerSheet`（候选全览 + 自定义值区）。
 * 颜色全走语义 token（禁裸 hex）；图标走 `IconSvg`（禁 emoji / 文本当图标）；
 * 按压用 hover-class 的透明度微降（禁 transform: scale）。
 */
import { computed } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'

const props = withDefaults(defineProps<{
  /** 标签文案（后端中文值，端上零翻译） */
  label: string
  /** `selected` = 选中态；`candidate` = 候选态（次级浅底） */
  variant?: 'selected' | 'candidate'
  /**
   * 选中态视觉档（仅 `variant='selected'` 有意义，由消费方按维度 `valueType` 下发）：
   * `soft` = 多选组（浅底 + 描边 + 橙字）；`solid` = 单选组（实心主色底 + 白字）。**两套不得互串。**
   */
  selectedStyle?: 'soft' | 'solid'
  /**
   * 选中态是否展示**勾选标识**（`IconSvg name="check"`）：弹层内「已选」不能只靠底色表达。
   * 候选态不消费本开关（未选中无需对勾）。
   */
  checkable?: boolean
  /** 无障碍标签（缺省按标签文案） */
  ariaLabel?: string
}>(), {
  variant: 'candidate',
  selectedStyle: 'soft',
  checkable: false,
  ariaLabel: '',
})

const emit = defineEmits<{
  /** 点标签体：由父级决定「加入 / 取消 / 替换 / 选中并关闭」 */
  (e: 'pick'): void
}>()

/** 选中态两套视觉分流（候选态一律 `--off`；两套由消费方按 `valueType` 决定，禁互串） */
const chipClass = computed(() =>
  props.variant !== 'selected'
    ? 'tag-chip--off'
    : props.selectedStyle === 'solid'
      ? 'tag-chip--on-solid'
      : 'tag-chip--on',
)

/** 选中态内的图标色：实心档随白字取白，浅底档取深主色 */
const selectedIconColor = computed(() =>
  props.selectedStyle === 'solid' ? COLOR_MAP['on-primary'] : COLOR_MAP['primary-text'],
)
</script>

<style scoped>
/* 触达：弹层内 chip 是独立可点件 ⇒ 视觉高恒 ≥88rpx（= 44pt；1rpx = 0.5pt 换算口径）。
   文字行 30.8rpx + 内边距 16×2 + 描边 4rpx = 66.8rpx ⇒ 由 `min-height` 抬到 88rpx（居中摆放，不靠加大字号）。
   `box-sizing: border-box` ⇒ 描边与内边距都算在 88rpx 内，两态切换不跳高。 */
.tag-chip {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  min-height: 88rpx;
  padding: var(--spacing-sm) var(--spacing-lg);
  /* 圆角取 `--radius-icon`(24rpx)（App.vue 无 28rpx 档，取标度内最近档；禁裸写 28rpx）。
     圆角只影响轮廓，不改变盒高。 */
  border-radius: var(--radius-icon);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
/* 选中态（多选组档 `soft`）：主色浅底 + 主色细描边 + 深主色文字（与候选态一眼可辨） */
.tag-chip--on {
  background: var(--color-primary-soft);
  border: 2rpx solid var(--color-primary);
}
/* 选中态（单选组档 `solid`）：实心主色底 + 白字；描边取与底同色 ⇒ 不显边、仍与候选态等高（UI 稿禁止项 14） */
.tag-chip--on-solid {
  background: var(--color-primary);
  border: 2rpx solid var(--color-primary);
}
/* 候选态：次级浅底 + 正文棕文字、无描边（描边留给「选中」独占，避免两态混淆） */
.tag-chip--off {
  background: var(--bg-soft);
  border: 2rpx solid transparent;
}
.tag-chip--pressed { opacity: 0.7; }

.tag-chip-text {
  font-size: var(--font-aux);
  /* line-height 固定 1.4 ⇒ 字重变化**不改变文字行高**，两态仍保等高 */
  line-height: 1.4;
  font-weight: var(--weight-medium);
}
/* 选中态文字提至 `--weight-semibold`(600)，与候选态(500)拉开层级。
   ⚠️ 必须用 600 而非 500：小程序端 500 多数机型无真字重、会回落 400（与首页稿同源裁决）。 */
.tag-chip--on .tag-chip-text { color: var(--color-primary-text); font-weight: var(--weight-semibold); }
.tag-chip--on-solid .tag-chip-text { color: var(--color-on-primary); font-weight: var(--weight-semibold); }
.tag-chip--off .tag-chip-text { color: var(--text-body); }
</style>
