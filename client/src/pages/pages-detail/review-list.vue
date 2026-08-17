<template>
  <view class="page review-list-page" :class="{ 'theme-dark': theme.isDark }">
    <Header :title="`${dishName} · 评价`" @back="backToHome" />
    <scroll-view
      class="scroll-wrap"
      scroll-y
      :scroll-with-animation="!reduceMotion"
      @scrolltolower="loadMore"
    >
      <!-- 排序 Tab（#17）：最新 / 最有用，切换即重拉 -->
      <view class="sort-tabs">
        <text
          v-for="s in sortTabs"
          :key="s.value"
          class="sort-tab"
          :class="{ active: sortValue === s.value }"
          role="button"
          :aria-label="s.label"
          @tap="onSortChange(s.value)"
        >{{ s.label }}</text>
      </view>

      <view class="review-list" v-if="reviewList.length > 0">
        <ReviewItem
          v-for="rv in reviewList"
          :key="rv.id"
          :review="rv"
          hide-useful
          :current-user-id="currentUserId"
          @delete="onDeleteReview"
          @reply="onReply"
          @report="onReviewReport"
        />
      </view>
      <EmptyState v-else-if="!loading" text="还没有人评价过这道菜" icon="comment" />

      <view class="loading-more" v-if="loading">加载中…</view>
      <view class="loading-more" v-else-if="finished">没有更多了</view>

      <view style="height: calc(var(--spacing-lg) + env(safe-area-inset-bottom))" />
    </scroll-view>

    <!-- 回复评价输入弹层 -->
    <view v-if="replyOpen" class="reply-mask" @tap="closeReply">
      <view
        class="reply-sheet"
        :style="{ transform: `translateY(${replySheetDy}px)` }"
        @tap.stop
        @touchstart="onReplySheetTouchStart"
        @touchmove="onReplySheetTouchMove"
        @touchend="onReplySheetTouchEnd"
      >
        <view class="reply-drag" />
        <view class="reply-sheet-head">
          <text class="reply-sheet-title">回复{{ replyToNickname ? ' @' + replyToNickname : '' }}</text>
          <text class="reply-sheet-close" @tap="closeReply" role="button" aria-label="关闭">✕</text>
        </view>
        <textarea
          class="reply-input"
          v-model="replyText"
          :placeholder="replyPlaceholder"
          :focus="replyFocus"
          maxlength="500"
          auto-height
        />
        <view class="reply-sheet-actions">
          <text class="reply-cancel" @tap="closeReply" role="button">取消</text>
          <text
            class="reply-send"
            :class="{ disabled: !replyText.trim() || replySubmitting }"
            @tap="submitReply"
            role="button"
            aria-label="发送回复"
          >{{ replySubmitting ? '发送中…' : '发送' }}</text>
        </view>
      </view>
    </view>

    <!-- 举报弹窗（共享组件） -->
    <ReportModal
      :open="reportOpen"
      title="举报评价"
      placeholder="请描述举报原因…"
      confirm-text="提交举报"
      :submitting="reportSubmitting"
      @update:open="reportOpen = $event"
      @submit="submitReviewReport"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { deleteReview, replyReview } from '@/api/review'
import { submitFeedback } from '@/api/feedback'
import type { Review } from '@/types/review'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import EmptyState from '@/components/EmptyState.vue'
import ReportModal from '@/components/ReportModal.vue'

const theme = useThemeStore()
const dishStore = useDishStore()
const userStore = useUserStore()

const dishId = ref(0)
const dishName = ref('')
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)
/** 排序 Tab（#17）：最新 / 最有用 */
const sortValue = ref<'latest' | 'useful'>('latest')
const sortTabs = [
  { value: 'latest' as const, label: '最新' },
  { value: 'useful' as const, label: '最有用' },
]

const reviewList = computed(() => dishStore.reviewList)
const currentUserId = computed(() => userStore.userInfo?.id)

/** 切换排序：重置分页并重新拉取（置脏标记供返回刷新） */
function onSortChange(v: 'latest' | 'useful') {
  if (sortValue.value === v) return
  sortValue.value = v
  dishStore.reviewsDirty = true
  loadPage(1)
}

const reduceMotion = ref(false)
if (typeof window !== 'undefined') {
  reduceMotion.value = !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

onLoad((query) => {
  dishId.value = Number(query?.dishId) || 0
  dishName.value = dishStore.currentDish?.name || '菜品'
  if (!dishId.value) {
    uni.showToast({ title: '缺少菜品ID', icon: 'none' })
    return
  }
  loadPage(1)
})

async function loadPage(p: number) {
  if (loading.value) return
  loading.value = true
  try {
    const res = await dishStore.fetchReviews(dishId.value, {
      sort: sortValue.value,
      isWithImage: false,
      page: p,
      pageSize,
      append: p > 1,
    })
    page.value = p
    finished.value = res.list.length < pageSize
  } finally {
    loading.value = false
  }
}

function loadMore() {
  if (loading.value || finished.value) return
  loadPage(page.value + 1)
}

function onDeleteReview(rv: Review) {
  if (!userStore.requireAuth(() => onDeleteReview(rv))) return
  if (userStore.userInfo?.id && rv.userId !== userStore.userInfo.id) return
  uni.showModal({
    title: '删除评价',
    content: '确定删除这条评价吗？删除后不可恢复。',
    confirmText: '删除',
    confirmColor: '#FF3B30',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteReview(rv.id)
        uni.showToast({ title: '评价已删除', icon: 'none' })
        dishStore.reviewsDirty = true
        // 重拉列表：计数准确 + 重置分页 finished（#2/#4）
        await loadPage(1)
      } catch (e: any) {
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
  })
}

/* ===== 评价回复（楼中楼） ===== */
const replyOpen = ref(false)
const replyFocus = ref(false)
const replySubmitting = ref(false)
const replyText = ref('')
const replyParentId = ref<number | null>(null)
const replyToNickname = ref('')
const replyPlaceholder = ref('回复评价…')

function onReply(rv: Review) {
  if (!userStore.requireAuth(() => onReply(rv))) return
  replyParentId.value = rv.id
  replyToNickname.value = rv.userNickname || ''
  replyPlaceholder.value = `回复 ${rv.userNickname || '匿名用户'}：`
  replyText.value = ''
  replyOpen.value = true
  replyFocus.value = true
}

function closeReply() {
  replyOpen.value = false
  replyText.value = ''
  replyParentId.value = null
  replyToNickname.value = ''
}

/** 在楼中楼树中按 id 定位节点（顶层或任意层级子回复） */
function findReviewNode(nodes: Review[], id: number): Review | null {
  for (const n of nodes) {
    if (n.id === id) return n
    if (n.replies && n.replies.length) {
      const found = findReviewNode(n.replies, id)
      if (found) return found
    }
  }
  return null
}

async function submitReply() {
  if (!replyParentId.value || !replyText.value.trim() || replySubmitting.value) return
  replySubmitting.value = true
  try {
    await replyReview(replyParentId.value, replyText.value.trim())
    uni.showToast({ title: '回复成功', icon: 'success' })
    // 本地插入楼中楼（#7）：不整页重拉，保留分页滚动位置；置脏供返回/下次校正
    const parent = findReviewNode(reviewList.value, replyParentId.value)
    if (parent) {
      if (!parent.replies) parent.replies = []
      parent.replies.push({
        id: -Date.now(), // 临时负 ID 避免与真实冲突；删除会走重拉校正
        userId: userStore.userInfo?.id ?? 0,
        userNickname: userStore.userInfo?.nickname || '我',
        userAvatar: userStore.userInfo?.avatar || '',
        dishId: parent.dishId,
        rating: 0,
        content: replyText.value.trim(),
        images: [],
        createTime: new Date().toISOString(),
        parentId: replyParentId.value,
        replyToNickname: replyToNickname.value || parent.userNickname || '',
      })
    }
    dishStore.reviewsDirty = true
    closeReply()
  } catch (e: any) {
    uni.showToast({ title: e.message || '回复失败', icon: 'none' })
  } finally {
    replySubmitting.value = false
  }
}

/* ===== 回复弹层下拉关闭（#12 轻量 touch 模拟，不引第三方库） ===== */
const replySheetStartY = ref(0)
const replySheetDy = ref(0)
function onReplySheetTouchStart(e: any) {
  replySheetStartY.value = e.touches?.[0]?.clientY ?? 0
  replySheetDy.value = 0
}
function onReplySheetTouchMove(e: any) {
  const y = e.touches?.[0]?.clientY ?? 0
  const dy = y - replySheetStartY.value
  replySheetDy.value = Math.max(0, dy)
}
function onReplySheetTouchEnd() {
  if (replySheetDy.value > 80) {
    closeReply()
  }
  replySheetDy.value = 0
}

/* ===== 评价举报 ===== */
const reportOpen = ref(false)
const reportSubmitting = ref(false)
const reportTarget = ref<{ id: number } | null>(null)

function onReviewReport(rv: Review) {
  if (!userStore.requireAuth(() => onReviewReport(rv))) return
  reportTarget.value = { id: rv.id }
  reportOpen.value = true
}

async function submitReviewReport(text: string) {
  if (!reportTarget.value) return
  reportSubmitting.value = true
  try {
    await submitFeedback({ type: 'report', content: text, relatedType: 'review', relatedId: reportTarget.value.id })
    uni.showToast({ title: '举报已提交', icon: 'success' })
    reportOpen.value = false
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    reportSubmitting.value = false
  }
}
</script>

<style scoped>
.review-list-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; width: 100%; }
.review-list { margin: 0 var(--spacing-md) var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card-soft); }
.loading-more { text-align: center; font-size: var(--font-aux); color: var(--text-tertiary); padding: var(--spacing-md); }

/* 排序 Tab（#17）：轻量文字链，选中主色下划线 */
.sort-tabs { display: flex; align-items: center; gap: var(--spacing-md); margin: var(--spacing-md) var(--spacing-md) var(--spacing-sm); padding: 0 var(--spacing-xs); }
.sort-tab { position: relative; font-size: var(--font-card); font-weight: var(--weight-medium); color: var(--text-tertiary); padding: var(--spacing-2xs) var(--spacing-xs); -webkit-tap-highlight-color: transparent; transition: color 120ms var(--ease-out); }
.sort-tab.active { color: var(--text-primary); font-weight: var(--weight-bold); }
.sort-tab.active::after { content: ''; position: absolute; left: var(--spacing-xs); right: var(--spacing-xs); bottom: 0; height: 4rpx; border-radius: var(--radius-pill, 999rpx); background: var(--color-primary); transition: width 120ms var(--ease-out); }

/* 回复评价输入弹层（底部 sheet，Apple 风格克制；drag indicator + 下拉关闭） */
.reply-mask { position: fixed; inset: 0; z-index: 100; background: var(--overlay-scrim); display: flex; align-items: flex-end; -webkit-tap-highlight-color: transparent; }
.reply-sheet { width: 100%; background: var(--bg-card); border-radius: var(--radius-modal) var(--radius-modal) 0 0; padding: var(--spacing-md); padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom)); box-shadow: var(--shadow-card); transition: transform 160ms var(--ease-out); }
.reply-drag { width: 48rpx; height: 6rpx; border-radius: var(--radius-pill, 999rpx); background: var(--overlay-dark-soft); margin: 0 auto var(--spacing-sm); flex-shrink: 0; }
.reply-sheet-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--spacing-sm); }
.reply-sheet-title { font-size: var(--font-subtitle); font-weight: var(--weight-bold); color: var(--text-primary); }
.reply-sheet-close { font-size: var(--font-body); color: var(--text-tertiary); padding: var(--spacing-2xs) var(--spacing-xs); -webkit-tap-highlight-color: transparent; }
.reply-input { width: 100%; min-height: 120rpx; max-height: 360rpx; background: var(--bg-soft); border-radius: var(--radius-card); padding: var(--spacing-sm); font-size: var(--font-body); color: var(--text-primary); line-height: 1.5; box-sizing: border-box; }
.reply-sheet-actions { display: flex; align-items: center; justify-content: flex-end; gap: var(--spacing-lg); margin-top: var(--spacing-sm); }
.reply-cancel { font-size: var(--font-card); color: var(--text-tertiary); padding: var(--spacing-xs) var(--spacing-md); -webkit-tap-highlight-color: transparent; }
.reply-send { font-size: var(--font-card); font-weight: var(--weight-bold); color: var(--color-primary); padding: var(--spacing-xs) var(--spacing-md); -webkit-tap-highlight-color: transparent; transition: opacity 120ms ease; }
.reply-send.disabled { opacity: 0.4; }
</style>
