<template>
  <view class="page review-list-page" :class="{ 'theme-dark': theme.isDark }">
    <Header :title="`${dishName} · 评价`" @back="backToHome" />
    <scroll-view
      class="scroll-wrap"
      scroll-y
      :scroll-with-animation="!reduceMotion"
      @scrolltolower="loadMore"
    >
      <view class="review-list" v-if="reviewList.length > 0">
        <ReviewItem
          v-for="rv in reviewList"
          :key="rv.id"
          :review="rv"
          :current-user-id="currentUserId"
          hide-useful
          @delete="onDeleteReview"
          @report="onReviewReport"
          @more="onReviewMore"
        />
      </view>
      <EmptyState v-else-if="!loading" text="还没有人评价过这道菜" icon="comment" />

      <view class="loading-more" v-if="loading">加载中…</view>
      <view class="loading-more" v-else-if="finished">没有更多了</view>

      <view style="height: calc(var(--spacing-lg) + env(safe-area-inset-bottom))" />
    </scroll-view>

    <!-- 举报弹窗（共享组件） -->
    <ReportModal
      :open="reportOpen"
      title="举报评价"
      placeholder="请描述举报原因…"
      confirm-text="提交举报"
      :submitting="reportSubmitting"
      @update:open="reportOpen = $event"
      @submit="submitReport"
    />

    <!-- 评价三点菜单：删除/举报（与动态卡一致：点击直接弹层，动作内部再要求登录） -->
    <ReviewActionSheet
      :open="reviewMoreOpen"
      :is-own="reviewMoreIsOwn"
      @update:open="reviewMoreOpen = $event"
      @delete="onReviewMoreDelete"
      @report="onReviewMoreReport"
    />

    <!-- 认证弹层：删除/举报评价需认证入口统一底部弹出 -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { deleteReview } from '@/api/review'
import type { Review } from '@/types/review'
import { useReport } from '@/composables/useReport'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import EmptyState from '@/components/EmptyState.vue'
import ReportModal from '@/components/ReportModal.vue'
import ReviewActionSheet from '@/components/ReviewActionSheet.vue'
import AuthSheet from '@/components/AuthSheet.vue'

const theme = useThemeStore()
const dishStore = useDishStore()
const userStore = useUserStore()

const dishId = ref(0)
const dishName = ref('')
const page = ref(1)
const pageSize = 10
const loading = ref(false)
const finished = ref(false)

const reviewList = computed(() => dishStore.reviewList)
const currentUserId = computed(() => userStore.userInfo?.id)

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

/* ===== 评价三点菜单（ReviewItem @more → 页面级 ReviewActionSheet） ===== */
const reviewMoreOpen = ref(false)
const reviewMoreTarget = ref<Review | null>(null)
const reviewMoreIsOwn = computed(() => {
  const rv = reviewMoreTarget.value
  return rv != null && userStore.userInfo?.id != null && rv.userId === userStore.userInfo.id
})

/** 评价右上角三点：直接弹层（不先要求登录）；删除/举报动作内部再 requireAuth，与动态三点一致 */
function onReviewMore(rv: Review) {
  reviewMoreTarget.value = rv
  reviewMoreOpen.value = true
}

function onReviewMoreDelete() {
  if (reviewMoreTarget.value) onDeleteReview(reviewMoreTarget.value)
}

function onReviewMoreReport() {
  if (reviewMoreTarget.value) onReviewReport(reviewMoreTarget.value)
}

/* ===== 评价举报（收敛到 useReport hook） ===== */
const { reportOpen, reportSubmitting, openReport, submitReport } =
  useReport({ type: 'review', title: '举报评价', placeholder: '请描述举报原因…' })

function onReviewReport(rv: Review) {
  openReport(rv.id)
}
</script>

<style scoped>
.review-list-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; width: 100%; }
/* 评价列表：独立卡片堆叠（ReviewItem 已卡片化，与动态卡片统一；去外层白卡避免双重卡片）
   顶部与 Header 留白：参考首页广播条与 header 间距（md），避免第一条卡片贴死头部 */
.review-list { display: flex; flex-direction: column; gap: var(--spacing-sm); margin: var(--spacing-md) var(--spacing-md) var(--spacing-md); }
.loading-more { text-align: center; font-size: var(--font-aux); color: var(--text-tertiary); padding: var(--spacing-md); }


</style>
