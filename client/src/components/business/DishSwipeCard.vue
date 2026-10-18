<script setup lang="ts">
/**
 * 美味页 Z 轴堆叠卡片栈中的单卡（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishSwipeCard）。
 *
 * Julienne 纸片卡：卡面 / 三层堆叠副本 / 吊孔由 `PaperCard` 承载；本组件只排内容。
 * - 顶部信息带：左大号装饰评级字母 / 右风味评级两行（FlavorRating size="decor"）。
 * - 内容：圆形菜品图（420rpx，上方留白加大）→ 衬线菜名 → 浅灰辅助信息（价格 · 食堂｜档口）→ 胶囊按钮。
 * - 按钮无图标、不贴卡片底边（下方预留充足留白）。
 */
import ImagePlaceholder from '../base/ImagePlaceholder.vue'
import FlavorRating from './FlavorRating.vue'
import PaperCard from './PaperCard.vue'
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
  <PaperCard>
    <!-- 顶部信息带：左评级字母 / 右风味评级 -->
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
  </PaperCard>
</template>

<style lang="scss" scoped>
/* 顶部信息带：字母作卡片背景层水印（z-index 低于内容层）；右上评级右对齐 */
.card__top {
  position: absolute;
  top: var(--spacing-2xl);
  right: var(--spacing-xl);
  left: var(--spacing-xl);
  z-index: 0;
  pointer-events: none;
}

/* 内容层：居中排布；图片上方到吊孔留足呼吸位（信息带 + 大号字母高 + 间距），不贴卡片顶部 */
.card__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  margin-top: calc(var(--spacing-2xl) * 2 + var(--font-hero));
}

/* 圆形菜品图（视觉重心）：下方留白加大，与菜名呼吸 */
.card__photo {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  width: var(--dish-photo-size);
  height: var(--dish-photo-size);
  margin-bottom: var(--spacing-xl);
  background: transparent;
  border-radius: var(--radius-circle);
}

/* 圆角必须落在这里（原生 <image> 不吃父级 overflow 裁切） */
.card__img {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-circle);
}

/* 菜名：加粗衬线 + 放大字号，卡片主标题 */
.card__name {
  font-family: var(--font-serif);
  font-size: var(--font-hero);
  font-weight: var(--weight-bold);
  color: var(--text-title);
  text-align: center;
}

/* 价格 · 食堂|档口：无衬线 + 适度加重的正文档（副标题行，中度深灰保阅读清晰，不抢菜名重心） */
.card__meta {
  margin-top: var(--spacing-lg);
  font-family: var(--font-sans);
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--text-body);
  text-align: center;
}

/* 按钮：与信息行间距 + 底部留白由 PaperCard 卡面内距承担（不贴底） */
.card__btn {
  position: relative;
  z-index: 1;
  width: 100%;
  margin-top: var(--spacing-xl);
}
</style>
