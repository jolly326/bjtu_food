<script setup lang="ts">
/**
 * 美食页 Z 轴堆叠卡片栈中的单卡（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishSwipeCard）。
 *
 * - 整卡作为页面主容器：填充栈槽（上 48rpx / 下 40rpx / 左右 48rpx）。
 * - **双层卡片**：外层仅细浅边框 + 大圆角 + 极淡阴影（悬浮层）；内层白卡承载全部内容。
 * - 顶部信息位：左群体评级字母 / 右风味评级（绝对定位，见 FlavorRating）。
 * - 内容：圆形菜品图（420rpx，视觉重心）→ 衬线菜名 → 浅灰辅助信息（价格 · 食堂｜档口）→ 胶囊按钮贴底。
 */
import ImagePlaceholder from '../base/ImagePlaceholder.vue'
import FlavorRating from './FlavorRating.vue'
import WantButton from './WantButton.vue'

import type { DishCardOut } from '@/api'

withDefaults(
  defineProps<{
    dish: DishCardOut
    submitting?: boolean
    /** 拖拽中：禁用底部按钮，防止误触 */
    dragging?: boolean
    /** 限频退避中：禁用底部按钮直到倒计时结束（docs/secur/S-03） */
    locked?: boolean
  }>(),
  { submitting: false, dragging: false, locked: false },
)

const emit = defineEmits<{ (e: 'want'): void }>()

function yuan(fen: number): string {
  return (fen / 100).toFixed(2)
}
</script>

<template>
  <view class="card">
    <!-- 内层白卡：承载全部菜品内容 -->
    <view class="card__inner">
      <!-- 顶部信息位：左评级字母 / 右风味评级 -->
      <view class="card__top">
        <FlavorRating :level="dish.flavorLevel ?? null" />
      </view>

      <view class="card__content">
        <view class="card__photo">
          <image v-if="dish.imageUrl" class="card__img" :src="dish.imageUrl" mode="aspectFill" />
          <ImagePlaceholder v-else :size="48" />
        </view>

        <text class="card__name wrap-anywhere">{{ dish.name }}</text>

        <text class="card__meta">
          ¥{{ yuan(dish.price) }} · {{ dish.canteenName }}|{{ dish.stallName }}
        </text>
      </view>

      <view class="card__btn">
        <WantButton
          :tasted="dish.wanted"
          :loading="submitting"
          :disabled="dragging || locked"
          @press="emit('want')"
        />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 外层容器：仅细浅边框 + 大圆角 + 极淡阴影 —— 悬浮层 */
.card {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  box-sizing: border-box;
  width: 100%;
  overflow: hidden;
  padding: var(--spacing-xs);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
  /* 减少 webview 变换重绘抖动 */
  will-change: transform;
  backface-visibility: hidden;
}

/* 内层白卡：承载全部内容（圆角比外层小一档，形成嵌套） */
.card__inner {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  box-sizing: border-box;
  width: 100%;
  /* 顶部 96rpx 预留左右信息位；左右 40rpx；底部 24rpx */
  padding: calc(var(--spacing-2xl) * 2) var(--spacing-xl) var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card-stack);
}

/* 顶部信息位：左评级字母 / 右风味评级 */
.card__top {
  position: absolute;
  top: var(--spacing-lg);
  right: var(--spacing-xl);
  left: var(--spacing-xl);
  z-index: 2;
  pointer-events: none;
}

/* 内容层：居中偏上 */
.card__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  width: 100%;
  margin-top: var(--spacing-md);
}

/* 圆形菜品图（视觉重心） */
.card__photo {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  width: var(--dish-photo-size);
  height: var(--dish-photo-size);
  margin-bottom: var(--spacing-2xl);
  background: transparent;
  border-radius: var(--radius-circle);
}

/* 圆角必须落在这里（原生 <image> 不吃父级 overflow 裁切） */
.card__img {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-circle);
}

/* 菜名：衬线主标题，字号最大 */
.card__name {
  font-family: var(--font-serif);
  font-size: var(--font-display);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
  text-align: center;
}

/* 价格 · 食堂|档口：整行浅灰小字 */
.card__meta {
  font-size: var(--font-small);
  color: var(--text-tertiary);
  text-align: center;
}

/* 按钮：单层暖橙描边 + 暖白填充，贴底 */
.card__btn {
  position: relative;
  z-index: 1;
  width: 100%;
  margin-top: var(--spacing-md);
}
</style>
