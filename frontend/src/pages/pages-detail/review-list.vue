<template>
  <view class="page review-list-page">
    <Header :title="dishId ? '全部评价' : '我的评价'" showBack />

    <view class="filter-bar">
      <view class="filter-tabs">
        <view class="filter-tab" :class="{ active: sort === 'time' }" @tap="changeSort('time')">最新</view>
        <view class="filter-tab" :class="{ active: sort === 'rating' }" @tap="changeSort('rating')">评分</view>
      </view>
      <view class="with-image-switch" :class="{ on: onlyImage }" @tap="toggleOnlyImage">
        <view class="switch-dot" />
        <text class="switch-text">只看带图</text>
      </view>
    </view>

    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <view class="review-list" v-if="list.length > 0">
        <view v-for="rv in list" :key="rv.id" class="review-item" @longpress="onReviewLongPress(rv)">
          <view class="review-header">
            <image v-if="rv.userAvatar" class="review-avatar" :src="getImageUrl(rv.userAvatar)" mode="aspectFill" />
            <view v-else class="review-avatar review-avatar-empty">
              <text class="review-avatar-fallback">{{ EMOJI.dishPlaceholder }}</text>
            </view>
            <view class="review-header-right">
              <view class="review-header-top">
                <text class="review-name">{{ rv.userNickname }}</text>
                <text class="review-time">{{ relativeTime(rv.createTime) }}</text>
              </view>
              <view class="review-stars">
                <text v-for="i in starCount(rv.rating)" :key="i" class="review-star">{{ EMOJI.starFilled }}</text>
              </view>
            </view>
          </view>
          <text class="review-content">{{ rv.content }}</text>
          <view v-if="rv.images && rv.images.length" class="review-images">
            <view v-for="(img, idx) in rv.images" :key="idx" class="review-image-wrapper">
              <image class="review-image" :src="getImageUrl(img)" mode="aspectFill" @tap="previewImage(rv.images!, idx)" />
            </view>
          </view>
          <view class="review-actions">
            <UsefulButton :count="rv.usefulCount || 0" :active="!!rv.useful" @click="handleUseful(rv)" />
          </view>
        </view>
      </view>
      <EmptyState v-else text="暂无评价" />
      <view style="height: var(--spacing-lg)" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import UsefulButton from '@/components/UsefulButton.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getImageUrl } from '@/utils/image'
import { EMOJI } from '@/utils/emoji'
import { getReviewsByDish, getMyReviews, toggleUseful, deleteReview } from '@/api/review'
import { useUserStore } from '@/stores/user'
import type { Review } from '@/types/review'

const userStore = useUserStore()
const dishId = ref(0)
const sort = ref<'time' | 'rating'>('time')
const onlyImage = ref(false)
const list = ref<Review[]>([])
const loading = ref(false)
const refresherTriggered = ref(false)

function starCount(rating: number): number { return Math.round(rating) }

async function loadReviews() {
  if (!dishId.value) {
    // 我的评价模式：当前登录用户的评价列表（后端 GET /my/reviews）
    if (!userStore.requireAuth()) return
    loading.value = true
    try {
      list.value = await getMyReviews({ page: 1, pageSize: 50 })
    } catch {
      list.value = []
    } finally {
      loading.value = false
    }
    return
  }
  loading.value = true
  try {
    list.value = await getReviewsByDish(dishId.value, {
      sort: sort.value,
      isWithImage: onlyImage.value,
    })
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function changeSort(next: 'time' | 'rating') {
  if (sort.value === next) return
  sort.value = next
  loadReviews()
}

function toggleOnlyImage() {
  onlyImage.value = !onlyImage.value
  loadReviews()
}

async function handleUseful(rv: Review) {
  if (!userStore.requireAuth()) return
  const prevUseful = !!rv.useful
  const prevCount = rv.usefulCount || 0
  // 乐观更新（幂等切换：再点取消）
  rv.useful = !prevUseful
  rv.usefulCount = prevUseful ? Math.max(0, prevCount - 1) : prevCount + 1
  try {
    const res = await toggleUseful(rv.id)
    rv.useful = res.useful
    rv.usefulCount = res.usefulCount
  } catch {
    rv.useful = prevUseful
    rv.usefulCount = prevCount
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function previewImage(images: string[], current: number) {
  uni.previewImage({
    urls: images.map(getImageUrl),
    current: getImageUrl(images[current]),
  })
}

/** 删除本人评价（task-12.5，归属校验严格：仅本人 userId） */
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
          uni.showToast({ title: '已删除', icon: 'none' })
        } catch (e: any) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    },
  })
}

function relativeTime(dateStr: string): string {
  if (!dateStr) return ''
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  const diff = Math.floor((now - then) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`
  return dateStr
}

onLoad((query) => {
  if (query?.dishId) dishId.value = Number(query.dishId)
})
onMounted(() => loadReviews())

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadReviews().finally(() => { refresherTriggered.value = false })
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
.review-item { background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-md); margin-bottom: var(--spacing-sm); }
.review-header { display: flex; gap: var(--spacing-sm); align-items: stretch; margin-bottom: var(--spacing-xs); }
.review-avatar { width: 64rpx; height: 64rpx; border-radius: 50%; flex-shrink: 0; background: var(--bg-page); }
.review-avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--border-color); }
.review-avatar-fallback { font-size: 32rpx; line-height: 1; }
.review-header-right { flex: 1; display: flex; flex-direction: column; justify-content: space-between; min-height: 64rpx; }
.review-header-top { display: flex; align-items: center; justify-content: space-between; }
.review-name { font-size: var(--font-headline); font-weight: 500; color: var(--text-primary); }
.review-time { font-size: var(--font-aux); color: var(--text-tertiary); }
.review-stars { display: flex; align-items: center; gap: var(--spacing-xs); }
.review-star { font-size: var(--font-tiny); line-height: 1; }
.review-content { margin: var(--spacing-sm) 0; font-size: var(--font-body); color: var(--text-secondary); line-height: 1.4; display: block; }
.review-images { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.review-image-wrapper { width: 200rpx; height: 200rpx; border-radius: var(--radius-tag); overflow: hidden; background: var(--bg-page); flex-shrink: 0; }
.review-image { width: 100%; height: 100%; display: block; }
.review-actions { margin-top: var(--spacing-xs); display: flex; justify-content: flex-end; }
</style>
