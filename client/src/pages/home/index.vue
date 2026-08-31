<template>
  <view class="page home-page" :class="{ 'theme-dark': theme.isDark }">
    <!-- 首页头部容器：朱砂红底（筛选 chip + 搜索框）；红色食堂筛选下拉 anchor 在其正下方，视觉衔接无间隙 -->
    <view class="home-top">
      <Header
        variant="home"
        :selected-canteen="selectedCanteenName"
        search-placeholder="搜索你想吃的..."
        @filter="openFilter"
        @search="goToSearch"
      />
      <CanteenFilter
        v-if="showFilter"
        :canteens="dishStore.canteenList"
        :selected-id="selectedCanteenId"
        @select="onCanteenSelect"
        @close="showFilter = false"
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
        <!-- 未授权定位：轻提示开启，首页瀑布流「距你」才有数据 -->
        <view v-if="showLocHint" class="loc-hint" @tap="enableLocation">
          <text class="loc-hint-text">开启定位，查看菜品距你多远</text>
          <text class="loc-hint-arrow">›</text>
        </view>

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

    <AuthSheet />

    <!-- 底部常驻菜单栏：首页/社区/我的 三主区切换（仅主根页显示） -->
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onLoad, onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { showTab } from '@/stores/route'
import { useThemeStore } from '@/stores/theme'
import { useDishStore } from '@/stores/dish'
import { useLocationStore } from '@/stores/location'
import { getUserLocation } from '@/utils/location'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import Header from '@/components/AppHeader.vue'
import IconSvg from '@/components/IconSvg.vue'
import HomeFeed from '@/components/HomeFeed.vue'
import CanteenFilter from '@/components/CanteenFilter.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import TabBar from '@/components/TabBar.vue'
import type { FilterTab } from '@/types/filter-tab'

const theme = useThemeStore()
const dishStore = useDishStore()
const locationStore = useLocationStore()

const loadingHot = ref(true)
const loadFailed = ref(false)
const refresherTriggered = ref(false)

/** 食堂筛选下拉显隐 */
const showFilter = ref(false)
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

function openFilter() {
  showFilter.value = !showFilter.value
}
function onCanteenSelect(id: number | null) {
  selectedCanteenId.value = id
  showFilter.value = false
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

const showLocHint = computed(
  () => !locationStore.location && dishStore.filterList.length > 0,
)

async function enableLocation() {
  const loc = await getUserLocation()
  if (loc) {
    locationStore.setLocation(loc)
  }
}

function loadData() {
  // 与原差异：广播条已移除，首页仅加载食品列表；定位授权留 Hint 引导
  // 确保食堂列表就绪（红色筛选下拉依赖 canteenList）
  if (dishStore.canteenList.length === 0) dishStore.fetchCanteens()
}

onLoad(() => {
  loadData()
})

onShow(() => {
  // 锚定底部菜单栏：首页始终显示并高亮（页面已就绪，最可靠时机）
  showTab('home')
  // 返回首页清理分享态，避免无限循环（uni 分享机制硬限制）
  clearShareState()
  // 兜底：若首屏因遮挡/竞态未拉起，再次确保
  if (!bootstrapped) ensureBoot()
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
}
/* 头部容器：相对定位，使红色食堂筛选下拉 anchor 在其正下方、与 header 红色块无缝衔接 */
.home-top {
  position: relative;
  z-index: 20;
}
.scroll-wrap {
  flex: 1;
  width: 100%;
  box-sizing: border-box;
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

/* 定位提示条 */
.loc-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: var(--spacing-sm) var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-primary-surface);
  color: var(--color-on-primary-surface);
  border-radius: var(--radius-card);
}
.loc-hint-text { font-size: var(--font-body); }
.loc-hint-arrow { font-size: var(--font-subheading); }

/* 回到顶部悬浮按钮 */
.fab {
  position: fixed;
  right: var(--spacing-lg);
  bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom) + var(--spacing-lg));
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: var(--bg-card);
  box-shadow: var(--shadow-bar);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transform: translateY(20rpx) scale(0.9);
  transition: var(--press-transition);
  z-index: 50;
  -webkit-tap-highlight-color: transparent;
}
.fab-show {
  opacity: 1;
  transform: translateY(0) scale(1);
}
.fab:active { transform: scale(var(--press-scale)); }
</style>
