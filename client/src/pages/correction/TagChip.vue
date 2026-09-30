<template>
  <!--
    TagChip（correction 包内私有）—— 属性标签（选中态 / 候选态）。

    两态语义（UI 稿 §4 属性编辑区）：
     · `selected`：已选中——主色浅底 + 主色描边 + 深主色文字，可挂右上角删除叉（`closable`）；
     · `candidate`：候选（未选中）——次级浅底 + 正文棕文字，无描边、无叉。
    点标签体 = 选择 / 取消（语义由父级 `AttributeGroup` 决定，本组件只上报 `pick`）；
    点删除叉 = 移除该项（`@tap.stop`，避免冒泡成一次 `pick`）。

    `pickable`（P1 返工）—— **可点性由消费方决定，不由 variant 猜**：
     · 候选面板里的 chip（含「已选中」态的候选，需支持反选）⇒ `pickable`（默认 true，可点 + 按压反馈）；
     · 已选中区的 chip ⇒ `:pickable="false"`（**点标签体无任何行为**，删除只走右上角叉）
       —— 避免「有 role=button / 按压反馈却什么都不做」的死 affordance。
  -->
  <view
    class="tag-chip"
    :class="variant === 'selected' ? 'tag-chip--on' : 'tag-chip--off'"
    :role="pickable ? 'button' : 'none'"
    :aria-label="ariaLabel"
    :aria-pressed="ariaPressed"
    :hover-class="pickable ? 'tag-chip--pressed' : 'none'"
    hover-stay-time="80"
    @tap="onPick"
  >
    <text class="tag-chip-text">{{ label }}</text>
    <!-- 删除叉：命中区 64rpx×64rpx（= 32pt；图标视觉 R40 起 24rpx）。
         换算口径：1rpx = 0.5pt（750rpx 设计宽），故 64rpx = 32pt、88rpx = 44pt —— 原文案把
         「44rpx」当成「44pt」是 2 倍换算错误，此处已订正。命中区经负外边距居中抵消 ⇒ 不撑高 chip 视觉。
         ⚠️ R40：图标 20 → 24rpx 后，负外边距须按 (64 − 24) / 2 = 20rpx 重算（见 `.tag-chip-x`），
            否则命中盒会溢出 chip 边框、破坏「两态等高不跳变」（UI 稿裁决②）。 -->
    <view
      v-if="closable"
      class="tag-chip-x"
      role="button"
      :aria-label="`删除 ${label}`"
      hover-class="tag-chip-x--pressed"
      hover-stay-time="80"
      @tap.stop="emit('remove')"
    >
      <IconSvg name="close" :size="24" :color="COLOR_MAP['primary-text']" />
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * TagChip —— 属性标签（选中态 / 候选态）
 *
 * 消费方：`AttributeGroup`（已选中区、候选面板两处）。
 * 颜色全走语义 token（禁裸 hex）；图标走 `IconSvg`（禁 emoji / 文本当图标）；
 * 按压用 hover-class 的透明度微降（禁 transform: scale）。
 */
import { computed } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'

const props = withDefaults(defineProps<{
  /** 标签文案（后端中文值，端上零翻译） */
  label: string
  /** `selected` = 选中态（主色浅底 + 描边）；`candidate` = 候选态（次级浅底） */
  variant?: 'selected' | 'candidate'
  /** 是否展示右上角删除叉（仅选中态有意义） */
  closable?: boolean
  /** 无障碍标签（缺省按标签文案） */
  ariaLabel?: string
  /**
   * 标签体是否可点（回抛 `pick`）。
   * `false` ⇒ 不带 `role=button` / `aria-pressed` / `hover-class` 等交互语义（仅删除叉可点）。
   */
  pickable?: boolean
}>(), {
  variant: 'candidate',
  closable: false,
  ariaLabel: '',
  pickable: true,
})

const emit = defineEmits<{
  /** 点标签体：由父级决定「加入 / 取消 / 替换」 */
  (e: 'pick'): void
  /** 点删除叉：移除该项 */
  (e: 'remove'): void
}>()

/**
 * `aria-pressed`：仅可点态下发；不可点态返回 `undefined` ⇒ 属性整体不下发（不留死语义）。
 */
const ariaPressed = computed<boolean | undefined>(() =>
  props.pickable ? props.variant === 'selected' : undefined,
)

/** 仅 `pickable` 时回抛 `pick`（不可点态不留任何交互语义） */
function onPick() {
  if (props.pickable) emit('pick')
}
</script>

<style scoped>
/* 触达：内边距 sm/md（16/24rpx）+ 文字行 30.8rpx + 描边 4rpx ⇒ 视觉高 ≈67rpx（≈33.5pt），
   组内节距由 `--spacing-sm` 行距补足（`.ag-selected` / `.ag-chips`） */
.tag-chip {
  display: flex;
  align-items: center;
  gap: var(--spacing-2xs);
  padding: var(--spacing-sm) var(--spacing-md);
  /* R40：圆角由 `--radius-pill`（999rpx 全圆）改为 `--radius-icon`（24rpx）——
     UI 稿裁决⑤（App.vue 无 28rpx 档，取标度内最近档；**禁**裸写 28rpx）。
     圆角只影响轮廓，**不改变盒高** ⇒ 视觉高仍 67rpx（裁决④）。 */
  border-radius: var(--radius-icon);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
/* 选中态：主色浅底 + 主色细描边 + 深主色文字（与候选态一眼可辨） */
.tag-chip--on {
  background: var(--color-primary-soft);
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
  /* line-height 固定 1.4 ⇒ 字重变化**不改变文字行高**，两态仍保等高（裁决①/④） */
  line-height: 1.4;
  font-weight: var(--weight-medium);
}
/* R40：选中态文字提至 `--weight-semibold`(600)，与候选态(500)拉开层级（UI 稿 §4 诉求③）。
   ⚠️ 必须用 600 而非 500：小程序端 500 多数机型无真字重、会回落 400（与首页稿同源裁决）。 */
.tag-chip--on .tag-chip-text { color: var(--color-primary-text); font-weight: var(--weight-semibold); }
.tag-chip--off .tag-chip-text { color: var(--text-body); }

/* 删除叉：命中区 64rpx×64rpx（≥64rpx 返工口径）；负外边距抵消 ⇒ chip 视觉高度不变、不越出 chip 边框。
   R40：图标 20 → 24rpx，负外边距按 (64 − 24) / 2 = 20rpx 重算（垂直方向抵消 16rpx 内边距 + 4rpx 余量，
   原 20rpx 图标对应 -17rpx）。右侧留白仍取 24rpx，维持裁决②「× 紧贴文字 + 归属感」的布局口径。 */
.tag-chip-x {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  margin: -20rpx -22rpx -20rpx 0;
  -webkit-tap-highlight-color: transparent;
}
.tag-chip-x--pressed { opacity: 0.5; }
</style>
