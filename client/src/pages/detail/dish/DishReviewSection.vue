<template>
  <!-- 评价卡：整卡一张（卡头 + flat 条目）；无评价时卡内静默（无空态/加载提示） -->
  <view class="review-section" id="review-section">
    <view class="review-card">
      <view class="review-card-head">
        <text class="review-card-title">评价 ({{ total }})</text>
      </view>

      <view class="review-list" v-if="reviews.length > 0">
        <ReviewItem
          v-for="rv in reviews"
          :key="rv.id"
          :review="rv"
          :current-user-id="currentUserId"
          flat
          hide-useful
          @delete="emit('delete', $event)"
          @report="emit('report', $event)"
          @more="emit('more', $event)"
        />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import ReviewItem from './ReviewItem.vue'
import type { Review } from '@/types/review'

defineProps<{
  reviews: Review[]
  total: number
  currentUserId?: number
}>()

const emit = defineEmits<{
  (e: 'delete', review: Review): void
  (e: 'report', review: Review): void
  (e: 'more', review: Review): void
}>()
</script>

<style scoped>
.review-section { margin: var(--spacing-md) var(--spacing-md) 0; }
.review-card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: var(--spacing-sm) var(--spacing-md);
}
.review-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) 0 var(--spacing-sm);
}
/* dish-detail-visual-polish：评价标题与综合评分标题同档（600 左对齐） */
.review-card-title {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  flex: 1;
  min-width: 0;
}
.review-list { display: flex; flex-direction: column; }
</style>
