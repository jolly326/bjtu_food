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
      <!-- 胶囊高度不再传硬编码：FilterBar 组件内按 navMetrics.getCapsuleHeight 自取（与 AppHeader 同一真源，MP-017） -->
      <FilterBar
        class="fb-host"
        :canteens="dishStore.canteenList"
        :selected-canteen-id="selectedCanteenId"
        :price-range="dishStore.filterPrice"
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
        <!-- 瀑布流：按所选食堂过滤；未选 = 全部。末尾贡献卡片由 HomeContent 承载（含筛选无结果脱困动作） -->
        <HomeContent :filtered="hasFilter" @clear-filter="onClearFilter" @retry="retryWaterfall" />
      </view>
    </scroll-view>

    <!-- 底部常驻菜单栏：首页/我的 两主区切换（仅主根页显示） -->
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onLoad, onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { showTab } from '@/stores/route'
import { useDishStore } from '@/stores/dish'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import { PATH } from '@/utils/routes'
import Header from '@/components/AppHeader.vue'
import FilterBar from '@/components/FilterBar.vue'
import HomeContent from './HomeContent.vue'
import TabBar from '@/components/TabBar.vue'
import type { FilterTab } from '@/types/filter-tab'

const dishStore = useDishStore()

const refresherTriggered = ref(false)

/** 选择价格区间：写回 store 并刷新当前筛选流（区间单位为元，透传 api 层统一转分，无新契约） */
async function onPriceSelect(range: { min?: number; max?: number }) {
  await dishStore.setHomePrice(range)
}

/**
 * 当前选中食堂 id（null = 全部）——MP-03：**由 store 的 filterTab 派生**，页面不再自持一份。
 * 此前页面 selectedCanteenId 与 store filterTab.canteenId 是两个真源：
 * 下拉选项选中态读前者、列表请求读后者，二者在「清除筛选 / 首屏 ensureBoot」等路径上会不一致
 * （如 filterTab 已被换掉而页面 ref 未同步 → 胶囊回显与内容不匹配）。
 */
const selectedCanteenId = computed<number | null>(() => {
  const tab = dishStore.filterTab
  return tab && tab.type === 'canteen' && tab.canteenId != null ? tab.canteenId : null
})

/** 按 id 取食堂名（选中态派生后，构造 canteen tab 时不能再读「尚未更新的派生值」） */
function canteenNameOf(id: number | null): string {
  if (id == null) return ''
  return dishStore.canteenList.find((c) => c.id === id)?.name || ''
}
const selectedCanteenName = computed(() => canteenNameOf(selectedCanteenId.value))

function canteenTab(id: number, name: string): FilterTab {
  return { key: `canteen-${id}`, label: name, type: 'canteen', canteenId: id }
}

/** 首拉：食品列表就绪后默认加载「全部」（热度流）；返回 Promise 供「首屏渲染后」时机串接 */
let bootstrapped = false
async function ensureBoot() {
  if (bootstrapped) return
  bootstrapped = true
  await dishStore.fetchFilterDishes(dishStore.defaultFilterTab(), true)
}
watch(
  () => dishStore.canteenList.length,
  () => ensureBoot(),
  { immediate: true },
)

/**
 * 食堂筛选：只按该食堂刷新筛选流（表单显隐由 FilterBar 自持）。
 * MP-03：选中态不再写页面本地 ref —— fetchFilterDishes 会同步写入 filterTab，
 * 上面的 selectedCanteenId 由它派生，胶囊回显与列表条件天然同源。
 */
function onCanteenSelect(id: number | null) {
  const tab = id == null ? dishStore.defaultFilterTab() : canteenTab(id, canteenNameOf(id) || '食堂')
  dishStore.fetchFilterDishes(tab, true)
}

/** 是否存在生效的筛选条件（食堂 / 价格任一）——驱动首页贡献卡片的上下文文案（见 contribution-entry） */
const hasFilter = computed(
  () =>
    selectedCanteenId.value != null ||
    dishStore.filterPrice.min != null ||
    dishStore.filterPrice.max != null,
)

/**
 * 清除全部筛选（贡献卡片「清除筛选」次级动作）：清空价格区间并回到「全部」食堂。
 * MP-03：改为 store 的 clearHomeFilter —— 一次交互只发一次列表请求。
 */
function onClearFilter() {
  dishStore.clearHomeFilter()
}

function goToSearch() {
  uni.navigateTo({ url: PATH.find })
}

/** 重试当前筛选流：下拉刷新复用同一条重拉路径（食堂列表缺失时先补拉） */
async function retryWaterfall() {
  if (dishStore.canteenList.length === 0) {
    await dishStore.fetchCanteens()
  }
  // 重试当前生效的筛选流：filterTab 是唯一真源，缺失时退回默认热度流（MP-03）
  const tab = dishStore.filterTab ?? dishStore.defaultFilterTab()
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

function loadData() {
  // 首页仅加载食品列表；确保食堂列表就绪（红色筛选下拉依赖 canteenList）
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
  // 食堂字典最小失效机制：进程常驻期间回首页按节流窗口后台重拉（失败保留旧列表），
  // 保证管理端改食堂/档口名后最终可见（spec §7.7 附加核查）；内部自带节流与去重，onShow 高频触发安全
  void dishStore.refreshCanteensIfStale()
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
