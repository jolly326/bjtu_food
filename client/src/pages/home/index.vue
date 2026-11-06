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
import { useTheme } from '@/hooks'
import { cooldownFrom } from '@/utils/backoff'
import { abortAllRequests, isAbortedRequest } from '@/utils/request'
import { NAV } from '@/constants'
import DishGrid from './components/DishGrid.vue'
import ProductFeedbackEntry from './components/ProductFeedbackEntry.vue'
import HomeHeader from './components/HomeHeader.vue'
import SearchDock from './components/SearchDock.vue'

/** 主题：根容器挂 `.theme-dark` 即整页切深色（token 取值随之切换） */
const { themeClass, applyChrome } = useTheme()

/** 全量在售集：冷启动拉取并缓存，供清空回填 */
const allDishes = ref<DishVO[]>([])
/** 当前结果集：无条件时 = allDishes，条件结果由 recommend 返回 */
const resultDishes = ref<DishVO[]>([])
const conditioned = ref(false)
const loading = ref(true)
const failed = ref(false)
/** 页头顶部偏移（px）：= 状态栏高度，页头内容区自状态栏下沿起算 */
const topInset = ref<number>(0)
/** 页头内容区高（px）：= 微信胶囊行高，品牌块与胶囊垂直居中对齐 */
const headerHeight = ref<number>(NAV.DEFAULT_HEADER_HEIGHT_PX)
/** 页头右侧为微信胶囊预留的宽度（px），保证反馈入口不被原生胶囊遮挡 */
const headerRightReserve = ref<number>(NAV.DEFAULT_RIGHT_RESERVE_PX)

const query = ref('')
const searching = ref(false)
const productFeedbackOpen = ref(false)
/** 当前展开反馈表单的菜品 id；null = 无卡展开 */
const feedbackOpenId = ref<number | null>(null)
/** 当前展开描述全文的菜品 id；null = 无展开 */
const expandedId = ref<number | null>(null)

const visibleDishes = computed(() => (conditioned.value ? resultDishes.value : allDishes.value))

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  expandedId.value = null
  feedbackOpenId.value = null
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

/** 搜索序号：只接受最后一次提交的结果，连点 / 清空期间的迟到响应直接丢弃 */
let searchSeq = 0

/** 提交一句 query：请求 → 结果集整体替换；失败保持当前结果集 */
async function submit(text: string): Promise<void> {
  const keyword = text.trim()
  expandedId.value = null
  feedbackOpenId.value = null
  if (!keyword) {
    clearSearch()
    return
  }
  const current = ++searchSeq
  searching.value = true
  try {
    const result = await dishApi.recommend(keyword)
    if (current !== searchSeq) return
    query.value = keyword
    resultDishes.value = result.dishes
    conditioned.value = true
  } catch (error) {
    if (current !== searchSeq) return
    // 命中限频：按服务端文案登记退避（搜索按钮据此禁用并显示剩余秒数）
    const wait = cooldownFrom('search', error)
    uni.showToast({
      title: wait > 0 ? (error as Error).message : '搜索失败，请重试',
      icon: 'none',
    })
  } finally {
    if (current === searchSeq) searching.value = false
  }
}

function clearSearch(): void {
  // 清空即作废在途搜索：否则迟到响应会把结果集又填回来
  searchSeq += 1
  searching.value = false
  query.value = ''
  resultDishes.value = []
  conditioned.value = false
  expandedId.value = null
  feedbackOpenId.value = null
}

function onSearch(text: string): Promise<void> {
  return submit(text)
}

function toggleFull(locationId: number): void {
  expandedId.value = expandedId.value === locationId ? null : locationId
}

function measureTopInset(): void {
  try {
    const windowInfoApi = (
      uni as unknown as {
        getWindowInfo?: () => { statusBarHeight?: number; windowWidth?: number }
      }
    ).getWindowInfo
    const win = windowInfoApi ? windowInfoApi.call(uni) : uni.getSystemInfoSync()
    const statusBar = win.statusBarHeight || 0
    const windowWidth = win.windowWidth || 0

    let navHeight: number = NAV.DEFAULT_HEADER_HEIGHT_PX
    let rightReserve: number = NAV.DEFAULT_RIGHT_RESERVE_PX
    const menuApi = (
      uni as unknown as {
        getMenuButtonBoundingClientRect?: () => {
          top?: number
          height?: number
          left?: number
        }
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
      if (menu && typeof menu.left === 'number' && windowWidth > 0) {
        rightReserve = Math.max(0, windowWidth - menu.left) + NAV.CAPSULE_GAP_PX
      }
    }

    topInset.value = statusBar
    headerHeight.value = navHeight
    headerRightReserve.value = rightReserve
  } catch {
    topInset.value = 0
    headerHeight.value = NAV.DEFAULT_HEADER_HEIGHT_PX
    headerRightReserve.value = NAV.DEFAULT_RIGHT_RESERVE_PX
  }
}

onMounted(() => {
  measureTopInset()
  applyChrome()
  void load()
})
onUnmounted(abortAllRequests)
</script>

<template>
  <view class="home" :class="themeClass">
    <HomeHeader
      :top-inset="topInset"
      :header-height="headerHeight"
      :right-reserve="headerRightReserve"
    >
      <template #feedback>
        <ProductFeedbackEntry
          :open="productFeedbackOpen"
          @update:open="productFeedbackOpen = $event"
        />
      </template>
    </HomeHeader>

    <view class="home__body">
      <PageLoading v-if="loading" />
      <RetryBlock v-else-if="failed" @retry="load" />
      <DishGrid
        v-else
        :dishes="visibleDishes"
        :conditioned="conditioned"
        :expanded-id="expandedId"
        :feedback-open-id="feedbackOpenId"
        @open="toggleFull"
        @feedback-open="feedbackOpenId = $event"
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
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}
</style>
