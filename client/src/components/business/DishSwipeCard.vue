<script setup lang="ts">
/**
 * 推荐扑克卡片（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishSwipeCard）。
 * 单张卡：圆形菜品图 -> 菜名 -> 元信息行 -> 去品尝按钮。
 * 堆叠与划走手势由页面管理（本组件只负责单张内容与点击）。
 */
import ImagePlaceholder from '../base/ImagePlaceholder.vue'
import WantButton from './WantButton.vue'

defineProps<{
  dish: {
    id: number
    name: string
    price: number
    imageUrl?: string | null
    avgReaction?: number | null
    wanted: boolean
    canteenName: string
    stallName: string
  }
  submitting?: boolean
}>()

const emit = defineEmits<{ (e: 'want'): void }>()

function yuan(fen: number): string {
  return (fen / 100).toFixed(2)
}
</script>

<template>
  <view class="card">
    <view class="card__photo">
      <image v-if="dish.imageUrl" class="card__img" :src="dish.imageUrl" mode="aspectFill" />
      <ImagePlaceholder v-else />
    </view>

    <text class="card__name wrap-anywhere">{{ dish.name }}</text>

    <view class="card__meta">
      <text class="card__reaction">{{ dish.avgReaction ?? '暂无评价' }}</text>
      <text class="card__dot">·</text>
      <text class="card__price">¥{{ yuan(dish.price) }}</text>
      <text class="card__dot">·</text>
      <text class="card__place">{{ dish.canteenName }} | {{ dish.stallName }}</text>
    </view>

    <WantButton :tasted="dish.wanted" :loading="submitting" @press="emit('want')" />
  </view>
</template>

<style lang="scss" scoped>
.card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-lg);
  padding: var(--spacing-xl);
  background: var(--module-bg);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
}

.card__photo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--dish-photo-size);
  height: var(--dish-photo-size);
  overflow: hidden;
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
}

.card__img {
  width: 100%;
  height: 100%;
}

.card__name {
  font-size: var(--font-h2);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
  text-align: center;
}

.card__meta {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-sm);
  font-size: var(--font-body);
}

.card__reaction {
  color: var(--text-body);
}

.card__dot {
  color: var(--text-tertiary);
}

.card__price {
  color: var(--color-price);
}

.card__place {
  color: var(--text-tertiary);
}
</style>
