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
          hide-useful
          :deletable="rv.userId === currentUserId"
          @delete="onDeleteReview"
        />
      </view>
      <EmptyState v-else-if="!loading" text="还没有人评价过这道菜" icon="comment" />

      <view class="loading-more" v-if="loading">加载中…</view>
      <view class="loading-more" v-else-if="finished">没有更多了</view>

      <view style="height: calc(var(--spacing-lg) + env(safe-area-inset-bottom))" />
    </scroll-view>
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
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import EmptyState from '@/components/EmptyState.vue'

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
      sort: 'latest',
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
        dishStore.reviewList = dishStore.reviewList.filter(x => x.id !== rv.id)
        dishStore.reviewTotal = Math.max(0, dishStore.reviewTotal - 1)
      } catch (e: any) {
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
  })
}
</script>

<style scoped>
.review-list-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; width: 100%; }
.review-list { margin: var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card-soft); }
.loading-more { text-align: center; font-size: var(--font-aux); color: var(--text-tertiary); padding: var(--spacing-md); }
</style>
