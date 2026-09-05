<template>
  <!-- 评价卡：整卡一张（卡头 + flat 条目 + 到底/加载中），与动态卡片形态趋同 -->
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
      <view v-else class="review-empty">
        <text class="review-empty-text">还没有人评价过这道菜</text>
      </view>

      <!-- 评价到底/加载中提示（卡内触底加载全部评价） -->
      <view v-if="reviews.length > 0 && loadingMore" class="review-more-hint">加载中…</view>
      <view v-else-if="reviews.length > 0 && finished" class="review-more-hint">没有更多了</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import ReviewItem from '@/components/ReviewItem.vue'
import type { Review } from '@/types/review'

defineProps<{
  reviews: Review[]
  total: number
  currentUserId?: number
  loadingMore: boolean
  finished: boolean
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
.review-card-title {
  font-size: var(--font-h2);
  font-weight: var(--weight-heavy);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h2);
  flex: 1;
  min-width: 0;
}
.review-list { display: flex; flex-direction: column; }
.review-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--spacing-sm); padding: var(--spacing-lg) 0; }
.review-empty-text { font-size: var(--font-small); color: var(--text-tertiary); }
.review-more-hint { text-align: center; font-size: var(--font-tiny); color: var(--text-tertiary); padding: var(--spacing-md) 0 var(--spacing-2xs); }
</style>
