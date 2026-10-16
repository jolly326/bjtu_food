<script setup lang="ts">
/**
 * 橱窗 tab（设计稿 docs/ui/client/档口海报.md；功能规则 docs/func/client/A1）。
 * 问候语（无姓名，随时间三态）+ 一屏一张竖版海报，左右滑动。
 */
import { computed, onMounted, ref } from 'vue'
import EmptyState from '@/components/base/EmptyState.vue'
import RetryBlock from '@/components/base/RetryBlock.vue'
import TabBar from '@/components/base/TabBar.vue'
import PosterSwiper from '@/components/business/PosterSwiper.vue'
import { posterApi } from '@/api'
import { navMetrics } from '@/utils/safeArea'

type Poster = { id: number; imageUrl: string }

const posters = ref<Poster[]>([])
const loading = ref(true)
const failed = ref(false)
/** 顶部安全区：避开状态栏 / 刘海与右上角胶囊 */
const topInset = navMetrics().topInset

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好，小红果'
  if (hour < 17) return '中午好，小红果'
  return '晚上好，小红果'
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    posters.value = await posterApi.list()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <view class="greeting">{{ greeting }}</view>

    <RetryBlock v-if="failed" @retry="load" />

    <PosterSwiper v-else-if="posters.length" :posters="posters" />

    <EmptyState v-else-if="!loading" title="暂无海报" />

    <TabBar active="poster" />
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

.greeting {
  padding: var(--spacing-xl) var(--page-gutter);
  font-size: var(--font-display);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--text-title);
  text-align: center;
}
</style>
