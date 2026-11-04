<script setup lang="ts">
/**
 * 菜品首页（单页）—— 设计稿 docs/ui/client/菜品首页.md；
 * 功能规则 docs/func/client/A-浏览与发现/A1-搜索找菜.md、docs/func/client/B-轻量反馈/B1-文本反馈.md。
 *
 * 上方 3 列竖向菜品图标网格；下方常驻搜索框；点图标在网格内插入内联展开卡（不遮挡浏览）。
 * 一句话搜索：命中图标重排填位、未命中退场；零命中显示空态。
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

const dishes = ref<DishVO[]>([])
const loading = ref(true)
const failed = ref(false)
/** 页头顶部偏移（px）：= 状态栏高度，页头内容区自状态栏下沿起算 */
const topInset = ref(0)
/** 页头内容区高（px）：= 微信胶囊行高，品牌块与胶囊垂直居中对齐 */
const headerHeight = ref(44)

const query = ref('')
const searching = ref(false)
/** 搜索命中集合（按相关度降序）；`null` = 未搜索，展示全量 */
const matched = ref<DishVO[] | null>(null)

const feedbackOpen = ref(false)
/** 当前内联展开的菜品 id；`null` = 无展开 */
const expandedId = ref<number | null>(null)

const visibleDishes = computed(() => matched.value ?? dishes.value)
const emptySearch = computed(() => matched.value !== null && matched.value.length === 0)
const expandedDish = computed(
  () => visibleDishes.value.find((dish) => dish.id === expandedId.value) ?? null,
)

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  expandedId.value = null
  try {
    dishes.value = await dishApi.list()
  } catch (error) {
    if (isAbortedRequest(error)) return
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function onSearch(text: string): Promise<void> {
  expandedId.value = null
  const keyword = text.trim()
  if (!keyword) {
    clearSearch()
    return
  }
  searching.value = true
  try {
    const result = await dishApi.recommend(keyword)
    matched.value = result.dishes
  } catch {
    uni.showToast({ title: '搜索失败，请稍后再试', icon: 'none' })
  } finally {
    searching.value = false
  }
}

function clearSearch(): void {
  query.value = ''
  matched.value = null
  expandedId.value = null
}

function openDish(dish: DishVO): void {
  expandedId.value = expandedId.value === dish.id ? null : dish.id
}

function collapseDish(): void {
  expandedId.value = null
}

function openFeedback(): void {
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

    // 内容区高 = 胶囊行高（胶囊上下留白等值），使内容区与胶囊逐像素同顶同底
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
    <!-- appheader 区域：固定页头，与下方 scrollarea 平级，不在 scroll-view 内 -->
    <HomeHeader :top-inset="topInset" :header-height="headerHeight" />
    <view class="home__body">
      <PageLoading v-if="loading" />
      <RetryBlock v-else-if="failed" @retry="load" />
      <DishGrid
        v-else
        :dishes="visibleDishes"
        :empty="emptySearch"
        :expanded-id="expandedId"
        @open="openDish"
        @clear="clearSearch"
        @collapse="collapseDish"
        @feedback="openFeedback"
      />

      <!-- 悬浮 HUD：绝对定位于内容区底部之上，网格从其下方穿过 -->
      <SearchDock
        v-if="!loading && !failed"
        v-model="query"
        :searching="searching"
        @search="onSearch"
        @clear="clearSearch"
      />
    </view>

    <FeedbackPanel v-model="feedbackOpen" :dish-id="expandedDish ? expandedDish.id : null" />
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
