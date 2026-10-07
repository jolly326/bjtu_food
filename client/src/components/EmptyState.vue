<template>
  <!-- 空态块（公共组件，UI 统一 Loop Round 2 上提）
        视觉基线：**无底色**的居中极简列 —— 可选图标 + 主文案（次级文字色）+ 可选次文案（三级文字色）
        + 可选 CTA（主色胶囊）。**空 / 加载态一律不给灰底或白色色块**，简约到「icon + 文本」即可。
        ⚠️ 与 `RetryBlock` 的分工：RetryBlock = **失败态**（整块可点 + 固定重试语义）；
        本组件 = **空态**（无数据但一切正常，通常不可点，仅在传 `actionText` 时提供 CTA）。
        无障碍：CTA 为 role="button" + aria-label；纯展示态不带交互语义。
        状态播报：根节点 `role="status"` + `aria-live="polite"` —— 空态是「数据到位了但没有内容」
        这一状态变化，读屏用户需被动感知（否则页面在语义上静止，无法区分「加载中/已加载但为空」）。 -->
        <view class="empty-state" :class="{ 'is-card': card }" role="status" aria-live="polite">
    <AppIcon v-if="icon" :name="icon" :size="iconSize" :color="COLOR_MAP[iconColor]" />
    <text v-if="title" class="es-title">{{ title }}</text>
    <text v-if="desc" class="es-desc">{{ desc }}</text>
    <!-- CTA 走公共 `ContentButton`（内容宽胶囊唯一实现，触达恒为 88rpx 基线） -->
    <ContentButton v-if="actionText" class="es-action" :text="actionText" @press="emit('action')" />
    <!-- 额外内容（如自定义插画 / 补充说明）由消费方通过默认插槽提供 -->
    <slot />
  </view>
</template>

<script setup lang="ts">
/**
 * EmptyState —— 「暂无数据」空态展示块
 *
 * 消费方：
 * - pages/notifications/index.vue（暂无通知；无底色）
 * - pages/my-reviews/index.vue（游客态 / 删空后的提示；card 变体）
 * - pages/home/HomeContent.vue（首页零菜品；无底色）
 * - pages/find/index.vue（搜索无结果引导；card 变体）
 * - pages/detail/dish/DishReviewSection.vue（评价区零评价 / 筛选无结果；无底色；零评价态带
 *   `star-filled` 图标，`iconColor` 取 `star` = `--color-star` 同源实色）
 *
 * 仅承载展示与 CTA 上抛；数据获取、空/失败判定与重拉路径由各消费方持有。
 */
import AppIcon from './AppIcon.vue'
import ContentButton from './ContentButton.vue'
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
  /**
   * 图标语义色键（COLOR_MAP 键名；默认三阶末档灰）。
   * AppIcon 的 color 不解析 var()（data-uri 内为字面量），故传键名经 COLOR_MAP 取实色。
   */
  iconColor?: keyof typeof COLOR_MAP
  /** CTA 文案（可选；传了才渲染主色胶囊按钮） */
  actionText?: string
  /**
   * 整屏居中的主空态：仅放宽内距（左右也留出呼吸位）。
   * **不带底色** —— 空态是内容状态，不需要色块；区块内小空态用默认形态即可。
   */
  card?: boolean
}>(), {
  iconSize: 44,
  iconColor: 'text-tertiary',
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
/* `card` 变体：整屏居中的主空态只放宽内距，**不给底色 / 圆角 / 投影** ——
   空态是「暂无数据」的内容状态，白卡 / 灰块会把页面切出一块与内容无关的色块。 */
.empty-state.is-card {
  padding: var(--spacing-xl) var(--spacing-lg);
}
.es-title {
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--text-secondary);
  text-align: center;
  /* 文案槽断行保护（基线 §1.8 / §2.6）：标题最多 2 行折叠；插值文案（如搜索关键词）
     的超长无空格串在折叠处换行而非横向溢出。 */
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.es-desc {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  text-align: center;
  /* 同 §1.8 断行口径：超长无空格串（URL / 长英文）不横向溢出卡壳 */
  overflow-wrap: anywhere;
}
/* CTA 底色 / 圆角 / 触达 / 禁用档全部由公共 `ContentButton` 承担，此处只留上间距 */
.es-action { margin-top: var(--spacing-2xs); }
</style>
