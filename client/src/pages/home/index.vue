<template>
  <view class="page home-page">
    <!-- 首页头部容器：朱砂红底，仅承载搜索框（与微信导航栏同高） -->
    <view class="home-top">
      <Header
        variant="home"
        search-placeholder="搜索你想吃的..."
        @search="goToSearch"
      />
    </view>

    <!-- 筛选行：左=全部食堂 / 全部价格（仅展开时红底），最右=筛选图标（常驻，暂不挂跳转） -->
    <view class="filter-bar">
      <FilterBar
        :canteens="dishStore.canteenList"
        :selected-canteen-id="selectedCanteenId"
        :price-range="dishStore.filterPrice"
        :capsule-height="36"
        @canteen-select="onCanteenSelect"
        @price-select="onPriceSelect"
      />
    </view>

    <scroll-view
      ref="scrollView"
      class="scroll-wrap"
      scroll-y
      :scroll-top="scrollTop"
      :scroll-with-animation="false"
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
      @scroll="onScroll"
      @scrolltolower="onScrollToLower"
    >
      <!-- 加载骨架屏（贴合真实首屏：双列瀑布流） -->
      <view v-if="loadingHot" class="home-skeleton">
        <view class="sk-waterfall">
          <view class="sk-col">
            <view v-for="s in 3" :key="'l' + s" class="sk-wcard skeleton" />
          </view>
          <view class="sk-col">
            <view v-for="s in 3" :key="'r' + s" class="sk-wcard skeleton" />
          </view>
        </view>
      </view>

      <view v-if="!loadingHot" class="home-content">
        <!-- 瀑布流：按所选食堂过滤；未选 = 全部 -->
        <HomeFeed :load-failed="loadFailed" @retry="retryWaterfall" />
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 回到顶部悬浮按钮 -->
    <view
      v-if="showBackTop"
      class="fab fab-backtop"
      :class="{ 'fab-show': showBackTop }"
      @tap="scrollToTop"
      aria-label="回到顶部"
    >
      <IconSvg name="up" :size="44" color="var(--color-primary)" />
    </view>

    <!-- 底部常驻菜单栏：首页/社区/我的 三主区切换（仅主根页显示） -->
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { onLoad, onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { showTab } from '@/stores/route'
import { useDishStore } from '@/stores/dish'
import { useLocationStore } from '@/stores/location'
import { getLocationIfAuthorized } from '@/utils/location'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import Header from '@/components/AppHeader.vue'
import IconSvg from '@/components/IconSvg.vue'
import FilterBar from '@/components/FilterBar.vue'
import HomeFeed from '@/components/HomeFeed.vue'
import TabBar from '@/components/TabBar.vue'
import type { FilterTab } from '@/types/filter-tab'

const dishStore = useDishStore()
const locationStore = useLocationStore()

const loadingHot = ref(true)
const loadFailed = ref(false)
const refresherTriggered = ref(false)

/** 胶囊高度（px），与 AppHeader 同一取值口径 */
const capsuleHeight = ref(32)

/** 选择价格区间：写回 store 并刷新当前筛选流（后端既有 minPrice/maxPrice，无新契约）。
 *  价格区间文案回显由 FilterBar 内部用 fenToYuan 换算（红线：禁止裸算 /100）。 */
async function onPriceSelect(range: { min?: number; max?: number }) {
  await dishStore.setHomePrice(range)
}

/** 当前选中食堂 id（null = 全部） */
const selectedCanteenId = ref<number | null>(null)
const selectedCanteenName = computed(
  () => dishStore.canteenList.find((c) => c.id === selectedCanteenId.value)?.name || '',
)

function defaultTab(): FilterTab {
  return { key: 'all', label: '全部', type: 'recommend' }
}
function canteenTab(id: number, name: string): FilterTab {
  return { key: `canteen-${id}`, label: name, type: 'canteen', canteenId: id }
}

/** 首拉：食品列表就绪后默认加载「全部」（热度流） */
let bootstrapped = false
async function ensureBoot() {
  if (bootstrapped) return
  bootstrapped = true
  await dishStore.fetchFilterDishes(defaultTab(), true)
  loadingHot.value = false
  loadFailed.value = false
}
watch(
  () => dishStore.canteenList.length,
  () => ensureBoot(),
  { immediate: true },
)

/** 食堂筛选：写回选中 id 并按该食堂刷新筛选流（表单显隐由 FilterBar 自持） */
function onCanteenSelect(id: number | null) {
  selectedCanteenId.value = id
  const tab = id == null ? defaultTab() : canteenTab(id, selectedCanteenName.value || '食堂')
  dishStore.fetchFilterDishes(tab, true)
}

function goToSearch() {
  uni.navigateTo({ url: '/pages/find/index' })
}

/** 失败态重试 */
async function retryWaterfall() {
  if (dishStore.canteenList.length === 0) {
    await dishStore.fetchCanteens()
  }
  const tab =
    selectedCanteenId.value == null
      ? defaultTab()
      : canteenTab(selectedCanteenId.value, selectedCanteenName.value || '食堂')
  dishStore.fetchFilterDishes(tab, true)
}

const scrollView = ref()
const scrollTop = ref(0)
const showBackTop = ref(false)
let lastScrollTop = 0

function onScroll(e: any) {
  const t = e.detail?.scrollTop || 0
  // 下滑超过一屏显示回到顶部；同时驱动无限滚动
  showBackTop.value = t > 600
  lastScrollTop = t
}
function scrollToTop() {
  scrollTop.value = lastScrollTop > 0 ? 0 : -1
  // 触发 scroll-view 回到顶部后复位，便于下次再触发
  requestAnimationFrame(() => {
    scrollTop.value = 0
  })
}
function onScrollToLower() {
  dishStore.loadMoreFilterDishes()
}

async function onRefresh() {
  refresherTriggered.value = true
  bootstrapped = false
  await ensureBoot()
  refresherTriggered.value = false
}

/** 静默定位（方案 C）：仅已授权才取坐标，未授权不弹窗；拿到后刷新本地距离，使「距你」即时生效 */
async function syncLocation() {
  if (locationStore.location) return
  const loc = await getLocationIfAuthorized()
  if (loc) {
    locationStore.setLocation(loc)
    dishStore.refreshLocalDistance()
  }
}

function loadData() {
  // 与原差异：广播条已移除，首页仅加载食品列表；定位走静默授权（onShow 拉起），不阻塞首屏
  // 确保食堂列表就绪（红色筛选下拉依赖 canteenList）
  if (dishStore.canteenList.length === 0) dishStore.fetchCanteens()
}

onLoad(() => {
  loadData()
})

// 读取原生胶囊高度，使独立筛选 chip 与 header 搜索框高度对齐（与 AppHeader 同一口径）
onMounted(() => {
  // @ts-ignore - 跨端兼容（H5 无 wx，退化为默认 32px）
  const mb = (typeof wx !== 'undefined' && wx.getMenuButtonBoundingClientRect) ? wx.getMenuButtonBoundingClientRect() : null
  if (mb && mb.height) capsuleHeight.value = mb.height
})

onShow(() => {
  // 锚定底部菜单栏：首页始终显示并高亮（页面已就绪，最可靠时机）
  showTab('home')
  // 返回首页清理分享态，避免无限循环（uni 分享机制硬限制）
  clearShareState()
  // 兜底：若首屏因遮挡/竞态未拉起，再次确保
  if (!bootstrapped) ensureBoot()
  // 静默定位（方案 C）：仅已授权才取坐标，未授权不弹窗，避免首页强制定位打断浏览
  void syncLocation()
})

onShareAppMessage(() => {
  return buildSharePayload()
})
</script>

<style scoped lang="scss">
.home-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-page);
  position: relative;
  overflow: hidden;
}
/* 头部容器：仅承载朱砂红 header，相对定位供可能的下拉锚定 */
.home-top {
  position: relative;
  z-index: 20;
}
/* 白底横置筛选条（与 find 页一致）：定位在红头之下、瀑布流之上，承载筛选/排序胶囊 */
.filter-bar {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-lg);
  /* 表面统一：筛选条与内容区同为凹陷面（--bg-page），消除白条割裂感；发丝线衔接 Header */
  background: var(--bg-page);
  border-bottom: 1rpx solid var(--border-color);
}
/* 结果计数：贴右、固定不收缩，读 dishStore.filterTotal */
.filter-count {
  margin-left: auto;
  flex-shrink: 0;
  font-size: var(--font-aux);
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
}
.scroll-wrap {
  flex: 1;
  width: 100%;
  box-sizing: border-box;
  min-height: 0;
  /* 预留底部菜单栏高度，避免内容被 TabBar 遮挡 */
  padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
}
.home-content {
  padding: 0;
}
.home-skeleton {
  padding: var(--spacing-md);
}
.sk-waterfall {
  display: flex;
  gap: var(--spacing-md);
}
.sk-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}
.sk-wcard {
  width: 100%;
  border-radius: var(--radius-card);
}

/* 回到顶部悬浮按钮 */
.fab {
  position: fixed;
  right: var(--spacing-lg);
  bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom) + var(--spacing-lg));
  width: 88rpx;
  height: 88rpx;
  border-radius: var(--radius-circle);
  background: var(--bg-card);
  box-shadow: var(--shadow-bar);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transform: translateY(20rpx);
  z-index: 50;
  -webkit-tap-highlight-color: transparent;
}
.fab-show {
  opacity: 1;
  transform: translateY(0);
}
</style>
