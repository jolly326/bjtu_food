<template>
  <view class="page home-page">
    <!-- 首页头部容器：暖砖红底，仅承载搜索框（与微信导航栏同高） -->
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
        class="fb-host"
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
      :scroll-with-animation="false"
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
      @scrolltolower="onScrollToLower"
    >
      <view class="home-content">
        <!-- 瀑布流：按所选食堂过滤；未选 = 全部 -->
        <HomeContent />
      </view>
    </scroll-view>

    <!-- 底部常驻菜单栏：首页/动态/我的 三主区切换（仅主根页显示） -->
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
import { PATH } from '@/utils/routes'
import Header from '@/components/AppHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import HomeContent from './HomeContent.vue'
import TabBar from '@/components/TabBar.vue'
import type { FilterTab } from '@/types/filter-tab'

const dishStore = useDishStore()
const locationStore = useLocationStore()

const refresherTriggered = ref(false)

/** 胶囊高度（px），与 AppHeader 同一取值口径 */
const capsuleHeight = ref(32)

/** 选择价格区间：写回 store 并刷新当前筛选流（区间单位为元，透传 api 层统一转分，无新契约） */
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
  uni.navigateTo({ url: PATH.find })
}

/** 重试当前筛选流：下拉刷新复用同一条重拉路径（食堂列表缺失时先补拉） */
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

function onScrollToLower() {
  dishStore.loadMoreFilterDishes()
}

async function onRefresh() {
  refresherTriggered.value = true
  await retryWaterfall()
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
/* 筛选条：定位在红头之下、瀑布流之上，承载筛选/排序胶囊 + 最右筛选图标。
   tab-pages-visual-unify：筛选栏落在页面底色（--bg-page 浅米灰）区，
   与上方白色悬浮搜索卡形成明度分层，二者层级可辨。 */
.filter-bar {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-lg);
  /* 表面统一：筛选条与内容区同为凹陷面（--bg-page）且无分隔线，与下方 scroll-view 视觉一体 */
  background: var(--bg-page);
}
/* ⚠️ 关键：自定义组件在小程序里是一个真实节点（<filter-bar>），.filter-bar 的 flex item 是宿主而非组件内的 .fb-row。
   宿主默认 flex:0 1 auto → 宽度按内容收缩、不撑满；此时组件内 .fb-row 的 width:100%/flex:1 只是「撑满一个内容宽的宿主」，
   没有任何剩余空间可分配，筛选 icon 会紧贴两颗按钮而不是靠右。必须让宿主撑满，icon 才能贴筛选行最右。 */
.fb-host {
  flex: 1;
  min-width: 0;
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
  /* 预留底部菜单栏高度，避免内容被 TabBar 遮挡；不再叠加 spacing-lg（否则最后卡片/触底加载区会悬空在 TabBar 上方，与 dynamic 页口径统一为 tabbar-height + env） */
  padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
}
.home-content {
  padding: 0;
}


</style>
