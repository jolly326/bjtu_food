<template>
  <view class="page review-list-page">
    <Header :title="dishId ? '全部评价' : '我的评价'" showBack />

    <view class="filter-bar">
      <view class="filter-tabs">
        <view class="filter-tab" :class="{ active: sort === 'latest' }" @tap="changeSort('latest')">最新</view>
        <view class="filter-tab" :class="{ active: sort === 'useful' }" @tap="changeSort('useful')">最有用</view>
      </view>
      <view class="with-image-switch" :class="{ on: onlyImage }" @tap="toggleOnlyImage">
        <view class="switch-dot" />
        <text class="switch-text">只看带图</text>
      </view>
    </view>

    <scroll-view
      class="scroll-wrap"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      :lower-threshold="80"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
      <SectionTitle :title="dishId ? `全部评价 (${total})` : `我的评价 (${total})`" />
      <view class="review-list" v-if="list.length > 0">
        <ReviewItem
          v-for="rv in list"
          :key="rv.id"
          :review="rv"
          :deletable="dishId === 0"
          @delete="onReviewLongPress"
        />
      </view>
      <EmptyState v-else-if="!loading" text="暂无评价" />
      <EmptyState v-else text="加载中…" />
      <view v-if="list.length > 0 && finished" class="list-end">
        <text class="list-end-text">没有更多了</text>
      </view>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import EmptyState from '@/components/EmptyState.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import { getReviewsByDish, getMyReviews, deleteReview } from '@/api/review'
import { useUserStore } from '@/stores/user'
import type { Review, ReviewSort } from '@/types/review'

const userStore = useUserStore()
const dishId = ref(0)
const sort = ref<ReviewSort>('latest')
const onlyImage = ref(false)
const list = ref<Review[]>([])
const total = ref(0)
const loading = ref(false)
const finished = ref(false)
const page = ref(1)
const pageSize = 20
const refresherTriggered = ref(false)

async function loadReviews(reset = false) {
  if (reset) {
    page.value = 1
    finished.value = false
  }
  loading.value = true
  try {
    let res: { list: Review[]; total: number }
    if (!dishId.value) {
      if (!userStore.requireAuth()) return
      const data = await getMyReviews({ page: page.value, pageSize })
      res = { list: data, total: data.length }
    } else {
      res = await getReviewsByDish(dishId.value, {
        sort: sort.value,
        isWithImage: onlyImage.value,
        page: page.value,
        pageSize,
      })
    }
    total.value = res.total
    if (reset) list.value = res.list
    else list.value = [...list.value, ...res.list]
    finished.value = list.value.length >= res.total
  } catch {
    if (reset) list.value = []
    finished.value = true
  } finally {
    loading.value = false
  }
}

function changeSort(next: ReviewSort) {
  if (sort.value === next) return
  sort.value = next
  loadReviews(true)
}

function toggleOnlyImage() {
  onlyImage.value = !onlyImage.value
  loadReviews(true)
}

function onLoadMore() {
  if (finished.value || loading.value) return
  page.value += 1
  loadReviews(false)
}

/** 删除本人评价（归属校验：仅本人 userId） */
function onReviewLongPress(rv: Review) {
  if (!userStore.userInfo || rv.userId !== userStore.userInfo.id) return
  uni.showModal({
    title: '删除评价',
    content: '确定删除这条评价吗？删除后不可恢复。',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteReview(rv.id)
          list.value = list.value.filter(item => item.id !== rv.id)
          total.value = Math.max(0, total.value - 1)
          uni.showToast({ title: '已删除', icon: 'none' })
        } catch (e: any) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    },
  })
}

onLoad((query) => {
  if (query?.dishId) dishId.value = Number(query.dishId)
})
onMounted(() => loadReviews(true))

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadReviews(true).finally(() => { refresherTriggered.value = false })
}
</script>

<style scoped>
.review-list-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.filter-bar { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md) var(--spacing-lg); background: var(--bg-card); }
.filter-tabs { display: flex; gap: var(--spacing-lg); }
.filter-tab { font-size: var(--font-body); color: var(--text-secondary); font-weight: 500; padding: var(--spacing-xs) 0; position: relative; transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.filter-tab.active { color: var(--color-primary); font-weight: 700; }
.filter-tab:active { transform: scale(var(--press-scale)); }
.filter-tab.active::after { content: ''; position: absolute; left: 50%; bottom: 0; transform: translateX(-50%); width: 40rpx; height: 6rpx; border-radius: 6rpx; background: var(--color-primary); }
.with-image-switch { display: flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-sm); border-radius: 28rpx; background: var(--bg-page); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.with-image-switch:active { transform: scale(var(--press-scale)); }
.with-image-switch.on { background: var(--color-primary-bg); }
.switch-dot { width: 28rpx; height: 28rpx; border-radius: 50%; background: var(--border-bold); transition: background 200ms var(--ease-out); }
.with-image-switch.on .switch-dot { background: var(--color-primary); }
.switch-text { font-size: var(--font-aux); color: var(--text-secondary); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: 0 var(--spacing-md); }
.review-list { margin-top: var(--spacing-sm); }
.list-end { text-align: center; padding: var(--spacing-lg) 0; }
.list-end-text { font-size: var(--font-aux); color: var(--text-tertiary); }
</style>
