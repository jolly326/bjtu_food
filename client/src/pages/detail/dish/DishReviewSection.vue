<template>
  <!-- 评价卡：整卡一张（卡头 + flat 条目）；三态齐全（加载中静默 / 失败可重试 / 零评价鼓励态）
       P3-01：卡头改用 SectionTitle（§4.9「分区标题一律 SectionTitle」），评价数与综合评分标题同档。
       2026-09-20：标题行加「只看有图」开关；评价卡删除「有用」按钮；排序唯一时间倒序、无切换入口。 -->
  <view class="review-section" id="review-section">
    <view class="review-card">
      <!-- 评价数 +「只看有图」开关经 SectionTitle 具名 slot 承载（纯展示，不跨组件分发具名 slot 到深层） -->
      <SectionTitle title="评价" no-margin>
        <template #extra>
          <view class="review-head-right">
            <!-- 评价数口径文案：全量 = 纯数字（标题「评价」+ N）；开启只看有图 = 「有图 N」，
                 避免筛选口径下被读成「评价 N」。在途期与失败态不显示，避免 0 值误导（失败 ≠ 零评价）。 -->
            <text v-if="!pending && !loadFailed" class="review-count">{{ totalLabel }}</text>
            <view
              class="image-only"
              :class="{ on: imageOnly }"
              role="switch"
              :aria-checked="imageOnly ? 'true' : 'false'"
              aria-label="只看有图"
              hover-class="pressed"
              @tap="emit('toggle-image-only')"
            >
              <text class="image-only-text">只看有图</text>
              <view class="image-only-track">
                <view class="image-only-knob" />
              </view>
            </view>
          </view>
        </template>
      </SectionTitle>

      <!-- ① spec §4.8 / a11y 红线：不设加载骨架/loading 指示。首屏拉取与「只看有图」切换的在途期
           （pending）本区块不渲染任何内容，保持空白静默——不得误闪空态文案。 -->

      <!-- ② 失败态：可重试（此前静默吞成「暂无评价」，用户误以为确实没人评；§7.20 PR-03 失败态必备） -->
      <RetryBlock v-if="loadFailed" :margin="false" @retry="emit('retry')" />

      <!-- ③ 有数据 / ④ 只看有图无结果 / ⑤ 零评价鼓励态（在途期整体不渲染） -->
      <template v-else-if="!pending">
        <!-- 有数据：评价列表（无「有用」入口；排序唯一时间倒序） -->
        <view v-if="reviews.length > 0" class="review-list">
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

        <!-- 「只看有图」下无带图评价：明确文案，不给「写评价」动作（该菜可能已有无图评价） -->
        <view v-else-if="imageOnly" class="review-empty">
          <text class="review-empty-title">暂无带图评价</text>
          <text class="review-empty-desc">关掉「只看有图」可查看全部评价</text>
        </view>

        <!-- 零评价鼓励态：明确「还没有人评」+ 给出可执行入口（写评价，不新增页面；
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
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ReviewItem from './ReviewItem.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import type { Review } from '@/types/review'

const props = defineProps<{
  reviews: Review[]
  total: number
  currentUserId?: number
  /** 评价首屏/刷新是否失败（失败 ≠ 零评价，渲染可重试失败态） */
  loadFailed?: boolean
  /** 「只看有图」开关选中态（服务端过滤，切换由页面重置分页并清空列表后重拉） */
  imageOnly: boolean
  /**
   * 重置式评价请求在途（首屏 / 只看有图切换 / 重试 / 提交后刷新）：
   * 为真时本区块空白静默，不渲染列表与空态（避免切换瞬间误闪「暂无带图评价 / 还没有人评价」）。
   */
  pending: boolean
}>()

/** 评价数口径文案：全量 = 纯数字；「只看有图」下为筛选口径「有图 N」 */
const totalLabel = computed(() => (props.imageOnly ? `有图 ${props.total}` : String(props.total)))

const emit = defineEmits<{
  (e: 'delete', review: Review): void
  (e: 'report', review: Review): void
  (e: 'more', review: Review): void
  /** 失败态点击重试：页面侧重拉评价列表（与进入页面同路径） */
  (e: 'retry'): void
  /** 零评价空态 → 写评价（页面侧走 requireAuth → ReviewComposer） */
  (e: 'write'): void
  /** 切换「只看有图」：页面侧重置分页 + 清空列表后按新口径重拉 */
  (e: 'toggle-image-only'): void
}>()
</script>

<style scoped>
/* 纵向间距与信息卡 / 综合评分卡（CardSection: margin 上下 --spacing-sm）对齐，避免 16/24rpx 混用 */
.review-section { margin: var(--spacing-sm) var(--spacing-md) 0; }
.review-card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: var(--spacing-sm) var(--spacing-md);
}
.review-list { display: flex; flex-direction: column; }

/* ===== 标题行右位：评价数 +「只看有图」开关 ===== */
.review-head-right { display: flex; align-items: center; gap: var(--spacing-md); }
.review-count { font-size: var(--font-aux); color: var(--text-tertiary); font-variant-numeric: tabular-nums; }
/* 开关：文字 + 迷你轨道；视觉小、命中区经 ::after 透明覆盖扩至 ≥88rpx（a11y 44pt 下限） */
.image-only {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  -webkit-tap-highlight-color: transparent;
}
.image-only::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 88rpx;
  height: 88rpx;
  transform: translate(-50%, -50%);
}
.image-only-text { font-size: var(--font-aux); color: var(--text-secondary); font-weight: var(--weight-medium); white-space: nowrap; }
.image-only.on .image-only-text { color: var(--color-primary-text); }
/* 关态轨道可见性：底色改 --border-bold（中性灰轨道，与白卡形成可辨层次），
   不再用近白填充（--bg-placeholder #F0ECE8 对白卡对比 ≈1.17:1 不可辨）。
   尺寸放大到接近系统开关比例（原 60×34rpx 约为其一半）；命中区仍由 ::after 保证 ≥88rpx。 */
.image-only-track {
  width: 88rpx;
  height: 52rpx;
  border-radius: var(--radius-pill);
  background: var(--border-bold);
  padding: 4rpx;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  transition: background var(--duration-fast) var(--ease-out);
}
.image-only.on .image-only-track { background: var(--color-primary); }
.image-only-knob {
  width: 40rpx;
  height: 40rpx;
  border-radius: var(--radius-circle);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
  transform: translateX(0);
  transition: transform var(--duration-fast) var(--ease-out);
}
/* 行程 = 轨道内容宽(88−2×4) − 旋钮宽 40 = 40rpx */
.image-only.on .image-only-knob { transform: translateX(40rpx); }
@media (prefers-reduced-motion: reduce) {
  .image-only-track, .image-only-knob { transition: none; }
}

/* ===== 失败态：视觉由公共 RetryBlock 承担，此处仅补卡内上下呼吸 ===== */
.review-card :deep(.retry-block) { margin: var(--spacing-sm) 0; }

/* ===== 空态（零评价 / 只看有图无结果）：居中轻量文案 + 主色胶囊动作（不抢占列表主视觉） ===== */
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
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 88rpx;
  margin-top: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-xl);
  border-radius: var(--radius-pill);
  background: var(--color-primary);
  -webkit-tap-highlight-color: transparent;
}
.review-empty-action.pressed { opacity: 0.85; }
.review-empty-action-text { font-size: var(--font-small); color: var(--color-on-primary); font-weight: var(--weight-semibold); }
</style>
