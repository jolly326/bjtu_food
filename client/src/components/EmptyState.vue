<template>
  <!-- 空态块（公共组件，UI 统一 Loop Round 2 上提）
       背景：空态此前由各页各自手写（notifications / my-reviews / find /
       DishReviewSection / feedback 等 6 套），结构雷同但字号、颜色、内距各异。
       视觉基线（与失败态 `RetryBlock` 同语言、但**更轻**）：**无底色**的居中极简列 ——
       可选图标 + 主文案（次级文字色）+ 可选次文案（三级文字色）+ 可选 CTA（主色胶囊）。
       ⚠️ 与 `RetryBlock` 的分工：RetryBlock = **失败态**（凹陷卡 + 整块可点 + 固定重试语义）；
       本组件 = **空态**（无数据但一切正常，通常不可点，仅在传 `actionText` 时提供 CTA）。
       无障碍：CTA 为 role="button" + aria-label；纯展示态不带交互语义。 -->
  <view class="empty-state" :class="{ 'is-card': card }">
    <IconSvg v-if="icon" :name="icon" :size="iconSize" :color="COLOR_MAP['text-tertiary']" />
    <text v-if="title" class="es-title">{{ title }}</text>
    <text v-if="desc" class="es-desc">{{ desc }}</text>
    <view
      v-if="actionText"
      class="es-action"
      role="button"
      :aria-label="actionText"
      hover-class="pressed"
      @tap="emit('action')"
    >
      <text class="es-action-text">{{ actionText }}</text>
    </view>
    <!-- 额外内容（如自定义插画 / 补充说明）由消费方通过默认插槽提供 -->
    <slot />
  </view>
</template>

<script setup lang="ts">
/**
 * EmptyState —— 「暂无数据」空态展示块
 *
 * 消费方：
 * - pages/notifications/index.vue（暂无通知）
 * - pages/my-reviews/index.vue（游客态 / 删空后的提示）
 *
 * 仅承载展示与 CTA 上抛；数据获取、空/失败判定与重拉路径由各消费方持有。
 */
import IconSvg from './IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'

withDefaults(defineProps<{
  /** 主文案：简短结论（如「暂无通知」） */
  title?: string
  /** 次文案：补充说明或引导（可选） */
  desc?: string
  /** 图标名（可选；不传则不渲染图标，保持最简） */
  icon?: string
  /** 图标尺寸（rpx） */
  iconSize?: number
  /** CTA 文案（可选；传了才渲染主色胶囊按钮） */
  actionText?: string
  /**
   * 卡片变体：白底 + 大圆角 + 柔和投影（与列表卡同表面语言）。
   * 仅用于**整屏居中的主空态**（如搜索无结果）；区块内的小空态保持无底色的最简形态。
   */
  card?: boolean
}>(), {
  iconSize: 44,
  card: false,
})

const emit = defineEmits<{
  /** CTA 点击：由消费方决定动作（如「推荐这道菜」「写第一条评价」） */
  (e: 'action'): void
}>()
</script>

<style scoped>
/* 无底色、居中、极简列 —— 与失败态（bg-soft 凹陷卡）区分：空态不是异常，不该有「错误块」的重量 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xl) 0;
  box-sizing: border-box;
}
/* 卡片变体：整屏居中的主空态保留白卡表面（与列表卡同语言），区块内小空态不用 */
.empty-state.is-card {
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.es-title {
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--text-secondary);
  text-align: center;
}
.es-desc {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  text-align: center;
}
/* CTA：主色胶囊（统一了原「搜索无结果 · 推荐这道菜」与「写第一条评价」两处按钮语言） */
.es-action {
  margin-top: var(--spacing-2xs);
  padding: var(--spacing-2xs) var(--spacing-md);
  border-radius: var(--radius-btn);
  background: var(--color-primary);
  -webkit-tap-highlight-color: transparent;
}
.es-action.pressed { opacity: 0.85; }
.es-action-text {
  font-size: var(--font-small);
  color: var(--color-on-primary);
  font-weight: var(--weight-semibold);
}
</style>
