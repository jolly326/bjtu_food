<template>
  <!-- 评价卡：整卡一张（卡头 + flat 条目）；三态齐全（加载中 / 失败可重试 / 零评价鼓励态）
       P3-01：卡头改用 SectionTitle（§4.9「分区标题一律 SectionTitle」），评价数与综合评分标题同档 -->
  <view class="review-section" id="review-section">
    <view class="review-card">
      <!-- 评价数经 SectionTitle 既有 extra 能力承载（extraText prop，免具名 slot 跨组件分发） -->
      <SectionTitle title="评价" no-margin :extra-text="String(total)" />

      <!-- ① spec §4.8 不设加载骨架/加载指示：加载期间本区块不渲染，保持空白 -->

      <!-- ② 失败态：可重试（此前静默吞成「暂无评价」，用户误以为确实没人评；§7.20 PR-03 失败态必备） -->
      <RetryBlock v-if="loadFailed" :margin="false" @retry="emit('retry')" />

      <!-- ③ 有数据：评价列表（「有用」按钮保留，P0-06：公开评价的「有用数置顶」需真实入口才有意义） -->
      <view class="review-list" v-else-if="reviews.length > 0">
        <ReviewItem
          v-for="rv in reviews"
          :key="rv.id"
          :review="rv"
          :current-user-id="currentUserId"
          flat
          @delete="emit('delete', $event)"
          @report="emit('report', $event)"
          @more="emit('more', $event)"
        />
      </view>

      <!-- ④ 零评价鼓励态：明确「还没有人评」+ 给出可执行入口（写评价，不新增页面；
             未认证点击由页面侧 requireAuth 弹既有 AuthSheet 引导） -->
      <view v-else class="review-empty">
        <text class="review-empty-title">还没有人评价这道菜</text>
        <text class="review-empty-desc">你的第一条评价，能帮同学避雷，也能帮食堂改进</text>
        <view
          class="review-empty-action"
          role="button"
          aria-label="写第一条评价"
          hover-class="pressed"
          @tap="emit('write')"
        >
          <text class="review-empty-action-text">写第一条评价</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import ReviewItem from './ReviewItem.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import type { Review } from '@/types/review'

defineProps<{
  reviews: Review[]
  total: number
  currentUserId?: number
  /** 评价首屏/刷新是否失败（失败 ≠ 零评价，渲染可重试失败态） */
  loadFailed?: boolean
  /** 评价是否在途（驱动骨架态；有数据时不遮挡列表） */
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'delete', review: Review): void
  (e: 'report', review: Review): void
  (e: 'more', review: Review): void
  /** 失败态点击重试：页面侧重拉评价列表（与进入页面同路径） */
  (e: 'retry'): void
  /** 零评价空态 → 写评价（页面侧走 requireAuth → ReviewComposer） */
  (e: 'write'): void
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
.review-list { display: flex; flex-direction: column; }

/* ===== 失败态：视觉由公共 RetryBlock 承担，此处仅补卡内上下呼吸 ===== */
.review-card :deep(.retry-block) { margin: var(--spacing-sm) 0; }

/* ===== 零评价鼓励态：居中轻量文案 + 主色胶囊动作（不抢占列表主视觉） ===== */
.review-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-lg) var(--spacing-md) var(--spacing-md);
}
.review-empty-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-secondary); text-align: center; }
.review-empty-desc { font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; line-height: 1.5; }
.review-empty-action {
  margin-top: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-xl);
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  -webkit-tap-highlight-color: transparent;
}
.review-empty-action.pressed { opacity: 0.85; }
.review-empty-action-text { font-size: var(--font-small); color: var(--color-on-primary); font-weight: var(--weight-semibold); }
</style>
