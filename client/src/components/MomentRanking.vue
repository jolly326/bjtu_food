<template>
  <view class="ranking-block">
    <view class="ranking-head">
      <view class="ranking-title">
        <IconSvg name="fire" :size="36" color="var(--color-like)" class="ranking-title-icon" />
        <text class="ranking-title-text">热门榜单</text>
      </view>
      <text class="ranking-sub">本周最火动态</text>
    </view>

    <!-- 加载态：骨架行（不阻塞信息流） -->
    <view v-if="loading" class="ranking-loading">
      <view v-for="s in 3" :key="s" class="rank-row sk skeleton" />
    </view>

    <!-- 失败 / 空态：仅本区块兜底，信息流不受影响（community-discovery 9.4 验收） -->
    <view v-else-if="failed || items.length === 0" class="ranking-empty">
      <IconSvg name="fire" :size="40" color="var(--text-quaternary)" class="ranking-empty-icon" />
      <text class="ranking-empty-text">{{ failed ? '榜单加载失败' : '暂无热门动态' }}</text>
    </view>

    <!-- 榜单列表：纵向 Top N，每条 = 排名序号 + 作者 + 内容摘要 + 热度值 -->
    <view v-else class="ranking-list">
      <view
        v-for="(m, i) in items"
        :key="m.id"
        class="rank-row enter-up"
        :style="{ '--enter-i': Math.min(i, 8) }"
        role="button"
        aria-label="查看该动态"
        @tap="goDetail(m)"
      >
        <text class="rank-no" :class="`rank-no-${i + 1}`">{{ i + 1 }}</text>
        <view class="rank-main">
          <view class="rank-author">
            <image v-if="m.userAvatar" class="rank-avatar" :src="m.userAvatar" mode="aspectFill" lazy-load />
            <text class="rank-nickname">{{ m.userNickname || '匿名用户' }}</text>
            <text v-if="m.relatedName && m.relatedType !== 'none'" class="rank-related">· {{ relatedLabel(m) }}</text>
          </view>
          <text class="rank-content">{{ m.content }}</text>
          <view class="rank-meta">
            <view class="rank-metric">
              <IconSvg name="thumb" :size="26" color="var(--color-like)" class="rank-metric-icon" />
              <text class="rank-metric-num">{{ m.usefulCount }}</text>
            </view>
            <view class="rank-metric">
              <IconSvg name="comment" :size="26" color="var(--text-secondary)" class="rank-metric-icon" />
              <text class="rank-metric-num">{{ m.commentCount }}</text>
            </view>
            <text class="rank-heat">热度 {{ heat(m) }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import IconSvg from './IconSvg.vue'
import type { Moment } from '@/types/moment'

withDefaults(defineProps<{
  items: Moment[]
  loading?: boolean
  failed?: boolean
}>(), {
  loading: false,
  failed: false,
})

const emit = defineEmits<{
  (e: 'select', moment: Moment): void
}>()

/** 热度值 = usefulCount*2 + commentCount（R2 / R3 排序口径，前端仅作展示） */
function heat(m: Moment): number {
  return (m.usefulCount || 0) * 2 + (m.commentCount || 0)
}

function relatedLabel(m: Moment): string {
  const prefix = m.relatedType === 'dish' ? '菜品' : m.relatedType === 'stall' ? '档口' : ''
  return `${prefix}·${m.relatedName || ''}`
}

function goDetail(m: Moment) {
  emit('select', m)
}
</script>

<style scoped>
.ranking-block {
  margin: var(--spacing-md) var(--spacing-md) 0;
  padding: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  -webkit-tap-highlight-color: transparent;
}
.ranking-head { display: flex; align-items: baseline; justify-content: space-between; gap: var(--spacing-sm); }
.ranking-title { display: flex; align-items: center; gap: var(--spacing-xs); }
.ranking-title-icon { font-size: var(--font-subtitle); line-height: 1; }
.ranking-title-text { font-size: var(--font-subtitle); font-weight: var(--weight-bold); color: var(--text-primary); letter-spacing: var(--tracking-h3); }
.ranking-sub { font-size: var(--font-aux); color: var(--text-tertiary); }

.ranking-loading { margin-top: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-sm); }
.ranking-empty { margin-top: var(--spacing-md); display: flex; flex-direction: column; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-lg) 0; }
.ranking-empty-icon { line-height: 1; }
.ranking-empty-text { font-size: var(--font-aux); color: var(--text-quaternary); }

.ranking-list { margin-top: var(--spacing-sm); display: flex; flex-direction: column; }
.rank-row { display: flex; align-items: flex-start; gap: var(--spacing-sm); padding: var(--spacing-sm) 0; border-top: 1rpx solid var(--border-color); transition: background-color var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.rank-row:first-child { border-top: none; }
.rank-row:active { background-color: var(--bg-soft); }
.rank-no {
  flex-shrink: 0;
  width: 40rpx;
  text-align: center;
  font-size: var(--font-subtitle);
  font-weight: var(--weight-bold);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
  line-height: 1.4;
}
.rank-no-1 { color: var(--color-like); }
.rank-no-2 { color: var(--color-warning); }
.rank-no-3 { color: var(--color-primary); }
.rank-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.rank-author { display: flex; align-items: center; gap: var(--spacing-xs); min-width: 0; }
.rank-avatar { width: 40rpx; height: 40rpx; border-radius: var(--radius-xs); background: var(--bg-page); flex-shrink: 0; }
.rank-nickname { font-size: var(--font-small); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rank-related { font-size: var(--font-aux); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex-shrink: 0; }
.rank-content { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.45; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.rank-meta { display: flex; align-items: center; gap: var(--spacing-md); margin-top: var(--spacing-2xs); }
.rank-metric { display: inline-flex; align-items: center; gap: var(--spacing-2xs); }
.rank-metric-icon { line-height: 1; }
.rank-metric-num { font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
.rank-heat { font-size: var(--font-aux); color: var(--text-tertiary); font-variant-numeric: tabular-nums; }

/* 骨架行 */
.sk { height: 96rpx; border-radius: var(--radius-tag); }
.skeleton { background: linear-gradient(90deg, var(--bg-soft) 25%, var(--bg-card) 37%, var(--bg-soft) 63%); background-size: 400% 100%; animation: shimmer 1.4s ease infinite; }
@keyframes shimmer { 0% { background-position: 100% 0; } 100% { background-position: -100% 0; } }
@media (prefers-reduced-motion: reduce) {
  .skeleton { animation: none; }
}

/* 入场 stagger（与 MomentCard 同约定：enter-up + --enter-i） */
.enter-up { animation: enterUp var(--duration-base) var(--ease-out) both; animation-delay: calc(var(--enter-i, 0) * 40ms); }
@keyframes enterUp { from { opacity: 0; transform: translateY(16rpx); } to { opacity: 1; transform: translateY(0); } }
@media (prefers-reduced-motion: reduce) {
  .enter-up { animation: none; }
}
</style>
