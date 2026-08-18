<template>
  <view class="page my-reviews-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="我的评价" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y>
      <view v-if="loading && list.length === 0" class="skeleton-list">
        <view v-for="s in 3" :key="s" class="sk-item skeleton" />
      </view>
      <!-- 加载失败：显示重试（与空数据语义区分） -->
      <EmptyState v-else-if="loadFailed" text="加载失败，请重试" icon="report" :retry="true" @retry="load" />
      <!-- 白卡包裹评价列表：与菜品/档口详情评价区同款 comment-section，ReviewItem 作为卡内列表项 -->
      <view v-else-if="list.length > 0" class="comment-section">
        <text class="comment-title">我的评价 ({{ list.length }})</text>
        <view class="list">
          <!-- hide-useful：本页为个人评价管理页，与其他评价区一致不设「有用」操作 -->
          <ReviewItem v-for="r in list" :key="r.id" :review="r" hide-useful deletable @delete="onDelete(r)" />
        </view>
      </view>
      <EmptyState v-else text="还没有发表过评价" />
      <view style="height: var(--spacing-xl)" />
    </scroll-view>

    <!-- 认证弹层：游客直访时引导登录，认证成功后自动加载 -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import { getMyReviews, deleteReview } from '@/api/review'
import type { Review } from '@/types/review'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import EmptyState from '@/components/EmptyState.vue'
import AuthSheet from '@/components/AuthSheet.vue'

const userStore = useUserStore()
const theme = useThemeStore()
const list = ref<Review[]>([])
const loading = ref(false)
const loadFailed = ref(false)

async function load() {
  if (!userStore.requireAuth()) return
  loading.value = true
  loadFailed.value = false
  try {
    list.value = await getMyReviews()
  } catch {
    loadFailed.value = true
    /* toast 由 http 层统一处理 */
  } finally {
    loading.value = false
  }
}

function onDelete(review: Review) {
  uni.showModal({
    title: '删除评价',
    content: '确定删除这条评价吗？',
    confirmText: '删除',
    confirmColor: '#FF3B30',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteReview(review.id)
        list.value = list.value.filter((r) => r.id !== review.id)
        uni.showToast({ title: '已删除', icon: 'none' })
      } catch {
        /* ignore */
      }
    },
  })
}

// 游客直访时弹认证；认证成功后自动加载
watch(
  () => userStore.isLoggedIn(),
  (v) => { if (v) load() },
  { immediate: true },
)
</script>

<style scoped>
.my-reviews-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0 0; }
/* 评价白卡：与菜品/档口详情评价区同款（radius-modal 圆角 + 大字负 tracking 标题），token 化 */
/* 我的评价：ReviewItem 已卡片化（与动态卡片统一），title 下直接堆叠卡片，去外层白卡避免双重卡片 */
.comment-section { margin: 0 var(--spacing-md); }
.comment-title { display: block; font-size: var(--font-h3); font-weight: var(--weight-heavy); color: var(--text-primary); letter-spacing: var(--tracking-h3); margin-bottom: var(--spacing-sm); }
.list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.skeleton-list { display: flex; flex-direction: column; gap: var(--spacing-md); margin: var(--spacing-md); }
.sk-item { height: 220rpx; border-radius: var(--radius-card); }
</style>
