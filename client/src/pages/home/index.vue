<script setup lang="ts">
/**
 * 菜品首页（单页）—— 设计稿 docs/ui/client/菜品首页.md；
 * 功能规则 docs/func/client/A-浏览与发现/A1-搜索找菜.md、docs/func/client/B-轻量反馈/B1-文本反馈.md。
 *
 * 单一结果态：首页即当前条件下的在售位置结果集。冷启动为无条件结果（全量在售），
 * 提交一句话后为条件结果，清空条件回填全量集；布局与组件恒定。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue'

import { dishApi } from '@/api'
import type { DishVO } from '@/api'
import PageLoading from '@/components/PageLoading.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import { abortAllRequests, isAbortedRequest } from '@/utils/request'
import DishGrid from './components/DishGrid.vue'
import FeedbackPanel from './components/FeedbackPanel.vue'
import HomeHeader from './components/HomeHeader.vue'
import SearchDock from './components/SearchDock.vue'

/** 全量在售集：冷启动拉取并缓存，供清空回填 */
const allDishes = ref<DishVO[]>([])
/** 当前结果集：无条件时 = allDishes，条件结果由 recommend 返回 */
const resultDishes = ref<DishVO[]>([])
const conditioned = ref(false)
const loading = ref(true)
const failed = ref(false)
/** 页头顶部偏移（px）：= 状态栏高度，页头内容区自状态栏下沿起算 */
const topInset = ref(0)
/** 页头内容区高（px）：= 微信胶囊行高，品牌块与胶囊垂直居中对齐 */
const headerHeight = ref(44)

const query = ref('')
const submittedQuery = ref('')
const searching = ref(false)
const feedbackOpen = ref(false)
/** 当前展开描述全文的位置 id；null = 无展开 */
const expandedId = ref<number | null>(null)
const feedbackDishId = ref<number | null>(null)

const visibleDishes = computed(() => (conditioned.value ? resultDishes.value : allDishes.value))
const cardCount = computed(() =>
  visibleDishes.value.reduce((n, dish) => n + dish.locations.length, 0),
)


const emptyKind = computed<'none' | 'plain' | 'catalog'>(() => {
  if (cardCount.value > 0) return 'none'
  return conditioned.value ? 'plain' : 'catalog'
})

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  expandedId.value = null
  try {
    const list = await dishApi.list()
    allDishes.value = [...list].sort((a, b) => a.name.localeCompare(b.name, 'zh-Hans-CN'))
    resultDishes.value = []
    conditioned.value = false
  } catch (error) {
    if (isAbortedRequest(error)) return
    failed.value = true
  } finally {
    loading.value = false
  }
}

/** 提交一句 query：请求 → 结果集整体替换；失败保持当前结果集 */
async function submit(text: string): Promise<void> {
  const keyword = text.trim()
  expandedId.value = null
  if (!keyword) {
    clearSearch()
    return
  }
  searching.value = true
  try {
    const result = await dishApi.recommend(keyword)
    query.value = keyword
    submittedQuery.value = keyword
    resultDishes.value = result.dishes
    conditioned.value = true
  } catch {
    uni.showToast({ title: '搜索失败，请重试', icon: 'none' })
  } finally {
    searching.value = false
  }
}

function clearSearch(): void {
  query.value = ''
  submittedQuery.value = ''
  resultDishes.value = []
  conditioned.value = false
  expandedId.value = null
}

function onSearch(text: string): Promise<void> {
  return submit(text)
}

function toggleFull(locationId: number): void {
  expandedId.value = expandedId.value === locationId ? null : locationId
}

function collapseFull(): void {
  expandedId.value = null
}

function openFeedback(dishId: number): void {
  feedbackDishId.value = dishId
  feedbackOpen.value = true
}

function measureTopInset(): void {
  try {
    const windowInfoApi = (
      uni as unknown as { getWindowInfo?: () => { statusBarHeight?: number } }
    ).getWindowInfo
    const statusBar = windowInfoApi
      ? windowInfoApi.call(uni).statusBarHeight || 0
      : uni.getSystemInfoSync().statusBarHeight || 0

    let navHeight = 44
    const menuApi = (
      uni as unknown as {
        getMenuButtonBoundingClientRect?: () => { top?: number; height?: number }
      }
    ).getMenuButtonBoundingClientRect
    if (typeof menuApi === 'function') {
      const menu = menuApi.call(uni)
      if (
        menu &&
        typeof menu.top === 'number' &&
        typeof menu.height === 'number' &&
        menu.height > 0 &&
        menu.top >= statusBar
      ) {
        navHeight = (menu.top - statusBar) * 2 + menu.height
      }
    }

    topInset.value = statusBar
    headerHeight.value = navHeight
  } catch {
    topInset.value = 0
    headerHeight.value = 44
  }
}

onMounted(() => {
  measureTopInset()
  void load()
})
onUnmounted(abortAllRequests)
</script>

<template>
  <view class="home">
    <HomeHeader :top-inset="topInset" :header-height="headerHeight" />
    <view class="home__body">
      <PageLoading v-if="loading" />
      <RetryBlock v-else-if="failed" @retry="load" />
      <DishGrid
        v-else
        :dishes="visibleDishes"
        :expanded-id="expandedId"
        :empty-kind="emptyKind"
        @open="toggleFull"
        @collapse="collapseFull"
        @feedback="openFeedback"
        @clear="clearSearch"
      />

      <SearchDock
        v-if="!loading && !failed"
        v-model="query"
        :searching="searching"
        @search="onSearch"
        @clear="clearSearch"
      />
    </view>

    <FeedbackPanel v-model="feedbackOpen" :dish-id="feedbackDishId" />
  </view>
</template>

<style lang="scss" scoped>
.home {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  box-sizing: border-box;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-page);
  color: var(--text-body);
}

.home__body {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}
</style>
