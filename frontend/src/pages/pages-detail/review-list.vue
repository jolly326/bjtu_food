<template>
  <view class="page review-list-page">
    <Header :title="headerTitle" showBack />

    <scroll-view
      class="scroll-wrap"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      :lower-threshold="80"
      @refresherrefresh="onRefresh"
      @scrolltolower="onLoadMore"
    >
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
import { ref, computed, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import EmptyState from '@/components/EmptyState.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import { getReviewsByDish, getReviewsByStall, getReviewsByCanteen, getMyReviews, deleteReview } from '@/api/review'
import { useUserStore } from '@/stores/user'
import type { Review } from '@/types/review'

const userStore = useUserStore()
const dishId = ref(0)
const stallId = ref(0)
const canteenId = ref(0)
const list = ref<Review[]>([])
const total = ref(0)
const loading = ref(false)
const finished = ref(false)
const page = ref(1)
const pageSize = 20
const refresherTriggered = ref(false)

/** 标题：有 dish/stall/canteen 任一 id 即视为「全部评价」，否则「我的评价」 */
const headerTitle = computed(() => {
  if (dishId.value || stallId.value || canteenId.value) return '全部评价'
  return '我的评价'
})

/** 三态判断：是否有实体（菜品/档口/食堂）筛选 */
const hasEntity = computed(() => !!(dishId.value || stallId.value || canteenId.value))

async function loadReviews(reset = false) {
  if (reset) {
    page.value = 1
    finished.value = false
  }
  loading.value = true
  try {
    let res: { list: Review[]; total: number }
    if (!hasEntity.value) {
      if (!userStore.requireAuth()) return
      const data = await getMyReviews({ page: page.value, pageSize })
      res = { list: data, total: data.length }
    } else if (dishId.value) {
      res = await getReviewsByDish(dishId.value, {
        sort: 'latest',
        isWithImage: false,
        page: page.value,
        pageSize,
      })
    } else if (stallId.value) {
      res = await getReviewsByStall(stallId.value, {
        sort: 'latest',
        isWithImage: false,
        page: page.value,
        pageSize,
      })
    } else {
      res = await getReviewsByCanteen(canteenId.value, {
        sort: 'latest',
        isWithImage: false,
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
  if (query?.stallId) stallId.value = Number(query.stallId)
  if (query?.canteenId) canteenId.value = Number(query.canteenId)
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
.scroll-wrap { flex: 1; overflow-y: auto; padding: 0; }
.review-list { margin-top: var(--spacing-sm); padding: 0 var(--spacing-md); }
.list-end { text-align: center; padding: var(--spacing-lg) 0; }
.list-end-text { font-size: var(--font-aux); color: var(--text-tertiary); }
</style>
