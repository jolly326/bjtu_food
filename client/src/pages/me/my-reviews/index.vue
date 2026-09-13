<template>
  <view class="page my-reviews-page">
    <Header title="我的评价" @back="backToHome" />

    <scroll-view
      class="scroll-wrap"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view class="list">
        <view v-for="r in list" :key="r.id" class="review-card">
          <view class="card-head">
            <text class="dish-name">{{ r.dishName || '菜品' }}</text>
            <view class="rating">
              <IconSvg name="star-filled" :size="24" color="var(--color-primary)" />
              <text class="rating-num">{{ (r.rating || 0).toFixed(1) }}</text>
            </view>
          </view>
          <text class="review-content">{{ r.content }}</text>
          <view class="card-foot">
            <text class="review-time">{{ formatDateTime(r.createTime) }}</text>
            <text
              class="delete-link"
              role="button"
              aria-label="删除评价"
              @tap.stop="onDelete(r)"
            >删除</text>
          </view>
        </view>
      </view>

      <!-- 加载失败重试块（MP-012 同族）：首屏请求失败 ≠ 无评价——极简「加载失败 · 点击重试」行内块，
           先于空态渲染，避免网络失败被误读；恢复走重试块 @tap 或下拉刷新 -->
      <view
        v-if="loadFailed && !loading"
        class="my-reviews-retry"
        role="button"
        aria-label="加载失败，点击重试"
        hover-class="pressed"
        @tap="onRetryLoad"
      >
        <IconSvg name="report" :size="44" color="var(--text-tertiary)" />
        <text class="my-reviews-retry-title">加载失败</text>
        <text class="my-reviews-retry-hint">网络似乎不太顺畅 · 点击重试</text>
      </view>
      <!-- 空态：首次进入无评价保持静默；仅「删除最后一条」触发时给轻提示，避免被误解为加载异常（见 my-reviews） -->
      <view v-else-if="emptiedByDelete" class="empty-tip">
        <text class="empty-text">暂无评价，去菜品详情写一条吧</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 我的评价：评价类 UGC 的用户侧自管理入口（列表 + 本人删除）。
 * - 数据源 GET /my/reviews（后端联表返回 dishName），删除复用 DELETE /reviews/{id}
 * - 空态双口径：首次进入静默；删除导致清空时给轻提示（见 spec my-reviews）
 * - 失败态（MP-012）：首屏/刷新失败渲染「加载失败 · 点击重试」块，与静默空态区分；
 *   分页失败保持静默，可再触底重试
 */
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/AppHeader.vue'
import IconSvg from '@/components/IconSvg.vue'
import { getMyReviews, deleteReview } from '@/api/review'
import type { Review } from '@/types/review'
import { formatDateTime } from '@/utils/time'
import { backToHome } from '@/utils/nav'
import { MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'

const list = ref<Review[]>([])
const loading = ref(false)
const refresherTriggered = ref(false)
/** 仅「删除导致列表清空」时为 true，驱动空态轻提示 */
const emptiedByDelete = ref(false)
/** 首屏/下拉刷新是否失败（MP-012）：失败 ≠ 无评价，失败渲染重试块而非空态 */
const loadFailed = ref(false)
let page = 1
const pageSize = 20
const finished = ref(false)

async function load() {
  if (loading.value) return
  loading.value = true
  try {
    const res = await getMyReviews({ page: 1, pageSize })
    // 成功即清失败态（重试成功后错误块消失）
    loadFailed.value = false
    list.value = res.list
    page = 1
    finished.value = res.list.length < pageSize
    emptiedByDelete.value = false
  } catch (err) {
    // MP-012：首屏失败不再静默吞——置 loadFailed 渲染「加载失败 · 点击重试」块，恢复走重试块或下拉刷新
    console.error('[my-reviews] 加载评价失败', err)
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

/** 重试块 @tap：从第 1 页重拉（与下拉刷新同路径，仅无下拉动画）（MP-012） */
function onRetryLoad() {
  load()
}

async function loadMore() {
  if (finished.value || loading.value) return
  loading.value = true
  try {
    page += 1
    const res = await getMyReviews({ page, pageSize })
    const existIds = new Set(list.value.map(r => r.id))
    list.value = list.value.concat(res.list.filter(r => !existIds.has(r.id)))
    if (res.list.length < pageSize) finished.value = true
  } catch {
    page -= 1
  } finally {
    loading.value = false
  }
}

async function onRefresh() {
  refresherTriggered.value = true
  await load()
  refresherTriggered.value = false
}

/** 删除本人评价：二次确认 → 删除 → 列表移除（删空后给轻提示） */
function onDelete(r: Review) {
  uni.showModal({
    title: '删除评价',
    content: '确定删除这条评价吗？删除后不可恢复。',
    confirmText: '删除',
    confirmColor: MODAL_CONFIRM_DANGER_COLOR,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteReview(r.id)
        list.value = list.value.filter(item => item.id !== r.id)
        uni.showToast({ title: '评价已删除', icon: 'none' })
        emptiedByDelete.value = list.value.length === 0
      } catch (e: any) {
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
  })
}

onShow(() => {
  load()
})
</script>

<style scoped>
.my-reviews-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-page); }
.scroll-wrap { flex: 1; min-height: 0; overflow-y: auto; padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg)); box-sizing: border-box; }

.list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.review-card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  -webkit-tap-highlight-color: transparent;
  box-sizing: border-box;
}

.card-head { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); }
.dish-name {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rating { display: inline-flex; align-items: center; gap: var(--spacing-2xs); flex-shrink: 0; }
.rating-num { font-size: var(--font-small); color: var(--text-secondary); }

.review-content {
  font-size: var(--font-small);
  color: var(--text-primary);
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  overflow: hidden;
}

.card-foot { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); }
.review-time { font-size: var(--font-tiny); color: var(--text-tertiary); }
/* 负向操作弱化：右下角小文字链，不占大按钮位。
   QA-03 修复：视觉保持轻量，命中区经 ::after 透明覆盖扩至 ≥88rpx（Apple 44pt 触达下限）。 */
.delete-link {
  position: relative;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  padding: var(--spacing-2xs) var(--spacing-xs);
}
.delete-link::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 88rpx;
  height: 88rpx;
  transform: translate(-50%, -50%);
}

.empty-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl) var(--spacing-lg);
}
.empty-text { font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; }

/* 加载失败重试块（MP-012）：与 find/activity/feed 重试块同族视觉
   （居中、凹陷面 bg-soft、次级文字色），整块 @tap 触发重拉，无独立按钮 */
.my-reviews-retry {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  margin-top: var(--spacing-lg);
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
  -webkit-tap-highlight-color: transparent;
}
.my-reviews-retry.pressed { opacity: 0.7; }
.my-reviews-retry-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-secondary); text-align: center; }
.my-reviews-retry-hint { font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; }
</style>
