<script setup lang="ts">
/**
 * 美食 tab（设计稿 docs/ui/client/推荐菜品.md；功能规则 docs/func/client/A2、A3、B1）。
 * 扑克式单卡：左滑或右滑划走（本会话不再出现），右上角食堂筛选，卡片下方「去品尝」。
 */
import { computed, onMounted, ref } from 'vue'
import EmptyState from '@/components/base/EmptyState.vue'
import RetryBlock from '@/components/base/RetryBlock.vue'
import TabBar from '@/components/base/TabBar.vue'
import CanteenFilter from '@/components/business/CanteenFilter.vue'
import DishSwipeCard from '@/components/business/DishSwipeCard.vue'
import { canteenApi, dishApi, recordApi } from '@/api'
import { navMetrics } from '@/utils/safeArea'

type Card = {
  id: number
  name: string
  price: number
  imageUrl?: string | null
  avgReaction?: number | null
  wanted: boolean
  canteenName: string
  stallName: string
}

const cards = ref<Card[]>([])
const canteens = ref<{ id: number; name: string }[]>([])
const canteenId = ref<number | undefined>(undefined)
const swiped = ref<Set<number>>(new Set())
const submitting = ref(false)
const failed = ref(false)
const loading = ref(true)
/** 顶部安全区：避开状态栏 / 刘海与右上角胶囊 */
const topInset = navMetrics().topInset

/** 本会话未划走、未耗尽的卡片（划走的卡片不再出现） */
const visible = computed(() => cards.value.filter((c) => !swiped.value.has(c.id)))

async function load() {
  loading.value = true
  failed.value = false
  try {
    const [dishList, canteenList] = await Promise.all([
      dishApi.list({ canteenId: canteenId.value, pageSize: 20 }),
      canteenApi.list(),
    ])
    cards.value = dishList.records
    swiped.value = new Set()
    canteens.value = canteenList
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function onWant(dish: Card) {
  submitting.value = true
  try {
    await recordApi.want(dish.id)
    dish.wanted = true
  } catch {
    uni.showToast({ title: '提交失败，请重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

/** 划到下一张即视为上一张被划走（本会话不再出现） */
function onSwipe(event: { detail: { current: number } }) {
  const gone = cards.value.filter((c) => !swiped.value.has(c.id))
  const target = gone[event.detail.current - 1]
  if (target) swiped.value.add(target.id)
}

function onFilterChange(id?: number) {
  canteenId.value = id
  load()
}

onMounted(load)
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <view class="topbar">
      <view class="topbar__left">
        <text class="topbar__title">美食</text>
        <text class="topbar__date">{{ new Date().toLocaleDateString('zh-CN') }}</text>
      </view>
      <CanteenFilter :options="canteens" :model-value="canteenId" @pick="onFilterChange" />
    </view>

    <RetryBlock v-if="failed" @retry="load" />

    <swiper
      v-else-if="visible.length"
      class="stack"
      :circular="false"
      :duration="220"
      @change="onSwipe"
    >
      <swiper-item v-for="card in visible" :key="card.id" class="stack__item">
        <DishSwipeCard :dish="card" :submitting="submitting" @want="onWant(card)" />
      </swiper-item>
    </swiper>

    <EmptyState v-else-if="!loading" title="今天就刷到这儿了" subtitle="明天再来看看吧" />

    <TabBar active="dish" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: 100vh;
  overflow: hidden;
  padding-bottom: calc(var(--tabbar-height) + var(--tabbar-gap) + env(safe-area-inset-bottom));
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md) var(--page-gutter);
}

.topbar__left {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2xs);
}

.topbar__title {
  font-size: var(--font-display);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--text-title);
}

.topbar__date {
  font-size: var(--font-small);
  color: var(--text-tertiary);
}

.stack {
  flex: 1;
  min-height: 0;
  padding: 0 var(--page-gutter) var(--spacing-xl);
}

.stack__item {
  display: flex;
  box-sizing: border-box;
  padding-bottom: var(--spacing-sm);
}
</style>
