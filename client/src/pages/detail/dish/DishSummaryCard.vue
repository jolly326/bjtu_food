<template>
  <CardSection>
    <!-- 分区标题统一走 SectionTitle（§4.9 红线；同页三处标题同一实现，字重/间距/对齐不再漂移） -->
    <SectionTitle title="综合评分" noMargin />
    <view v-if="ratingCount > 0" class="summary-body">
      <view class="summary-left">
        <text class="summary-score">{{ scoreText }}</text>
        <text class="summary-count">{{ ratingCount }} 人评分</text>
      </view>
      <view class="summary-right">
        <view class="dist-item" v-for="item in distribution" :key="item.star">
          <view class="dist-stars">
            <IconSvg
              v-for="n in 5"
              :key="n"
              name="star"
              :size="20"
              :color="n <= item.star ? 'var(--color-primary)' : 'var(--color-star-empty)'"
            />
          </view>
          <text class="dist-star-num">{{ item.star }}</text>
          <view class="dist-bar">
            <view class="dist-fill" :style="{ width: distPct(item.count) }" />
          </view>
          <text class="dist-count">{{ item.count }}</text>
        </view>
      </view>
    </view>
    <view v-else class="summary-empty">
      <text class="summary-empty-text">还没有评分</text>
    </view>
  </CardSection>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import CardSection from '@/components/CardSection.vue'
import IconSvg from '@/components/IconSvg.vue'
import SectionTitle from '@/components/SectionTitle.vue'

interface RatingDistItem {
  star: number
  count: number
}

const props = defineProps<{
  rating: number
  ratingCount: number
  distribution: RatingDistItem[]
}>()

const scoreText = computed(() => (props.rating > 0 ? props.rating.toFixed(1) : '-'))

function distPct(count: number): string {
  const total = props.ratingCount
  if (!total) return '0%'
  return `${Math.round((count / total) * 100)}%`
}
</script>

<style scoped>
/* 分区标题已改用 SectionTitle（§4.9），手写 .summary-head 样式随之删除 */
/* dish-detail-visual-polish：紧凑平衡（左评分区收窄、行距收紧） */
.summary-body { display: flex; align-items: center; gap: var(--spacing-md); }
.summary-left { flex: 0 0 132rpx; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--spacing-2xs); }
.summary-score { font-size: var(--font-h3); font-weight: var(--weight-semibold); color: var(--text-primary); line-height: 1; font-variant-numeric: tabular-nums; }
.summary-count { font-size: var(--font-aux); color: var(--text-tertiary); }
.summary-right { flex: 1; min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: var(--spacing-xs); }
.summary-empty { padding: var(--spacing-md) 0; text-align: center; }
.summary-empty-text { font-size: var(--font-small); color: var(--text-tertiary); }
.dist-item { display: flex; align-items: center; gap: var(--spacing-sm); min-height: 36rpx; }
.dist-stars { flex: 0 0 auto; display: flex; align-items: center; gap: 2rpx; }
.dist-star-num { flex: 0 0 auto; width: 28rpx; text-align: right; font-size: var(--font-aux); color: var(--text-primary); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; }
.dist-bar { flex: 1; min-width: 0; height: 12rpx; border-radius: var(--radius-pill, 999rpx); background: var(--color-star-empty); overflow: hidden; }
.dist-fill { height: 100%; border-radius: var(--radius-pill, 999rpx); background: var(--color-primary) }
.dist-count { flex: 0 0 auto; width: 48rpx; text-align: left; font-size: var(--font-aux); color: var(--text-tertiary); font-variant-numeric: tabular-nums; }
</style>
