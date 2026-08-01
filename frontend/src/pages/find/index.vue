<template>
  <view class="page find-page">
    <Header :title="inFilter ? categoryLabel : '发现'" />

    <!-- 搜索框（SearchBar 组件，输入模式 + 联想 debounce 300ms）；左右留白与下方卡片一致（24rpx） -->
    <view class="search-wrap">
      <SearchBar
        v-model="keyword"
        input-mode
        :margin="'0'"
        placeholder="搜索菜品、档口或食堂"
        @search="onSearchConfirm"
        @update:model-value="onKeywordInput"
      />

      <!-- 搜索联想下拉 -->
      <view v-if="showSuggest && suggestions.length > 0" class="suggest-panel" :style="{ top: suggestPanelTop + 'px' }">
        <view
          v-for="s in suggestions"
          :key="`${s.type}-${s.id}-${s.name}`"
          class="suggest-item"
          :class="{ pressed: pressedKey === `s-${s.id}` }"
          @touchstart="pressedKey = `s-${s.id}`"
          @touchend="pressedKey = ''"
          @touchcancel="pressedKey = ''"
          @mousedown="pressedKey = `s-${s.id}`"
          @mouseup="pressedKey = ''"
          @mouseleave="pressedKey = ''"
          @tap="goSuggestion(s)"
        >
          <view class="suggest-icon">
            <image v-if="s.image" :src="s.image" mode="aspectFill" class="suggest-thumb" />
            <IconSvg v-else :name="suggestIcon(s.type)" :size="32" color="var(--text-tertiary)" />
          </view>
          <text class="suggest-name">{{ s.name }}</text>
          <text class="suggest-type">{{ suggestTypeLabel(s.type) }}</text>
        </view>
      </view>
    </view>

    <scroll-view
      class="scroll-wrap"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
      @scrolltolower="onScrollToLower"
    >
      <!-- ============ 发现主页（搜索区重做：历史/发现/热搜） ============ -->
      <view v-if="!inFilter" class="discover-home">
        <!-- 首屏骨架：发现主页三块（历史/分类/热搜）加载中占位 -->
        <view v-if="discoverLoading" class="discover-skeleton">
          <view class="sk-row"><view v-for="s in 6" :key="s" class="sk-chip skeleton" /></view>
          <view class="sk-row"><view v-for="s in 8" :key="s" class="sk-cat skeleton" /></view>
          <view class="sk-row"><view v-for="s in 5" :key="s" class="sk-line skeleton" /></view>
        </view>

        <template v-else>
        <!-- 历史搜索：标签 chip 行，可单个删除 / 一键清空 -->
        <CardSection v-if="historyList.length > 0">
          <SectionTitle title="历史搜索">
            <text slot="extra" class="section-extra history-clear" @tap="clearHistory">清空</text>
          </SectionTitle>
          <view class="history-chips">
            <view
              v-for="(kw, i) in historyList"
              :key="kw"
              class="history-chip"
              :class="{ pressed: pressedKey === `h-${kw}` }"
              @touchstart="pressedKey = `h-${kw}`"
              @touchend="pressedKey = ''"
              @touchcancel="pressedKey = ''"
              @mousedown="pressedKey = `h-${kw}`"
              @mouseup="pressedKey = ''"
              @mouseleave="pressedKey = ''"
              @tap="goKeyword(kw)"
            >
              <text class="history-chip-text">{{ kw }}</text>
              <view class="history-chip-del" @tap.stop="removeHistory(i)">
                <IconSvg name="close" :size="24" color="var(--text-tertiary)" />
              </view>
            </view>
          </view>
        </CardSection>

        <!-- 分类入口宫格：合并为单个 CardSection（含 SectionTitle） -->
        <CardSection v-if="categories.length > 0">
          <SectionTitle title="分类" />
          <view class="category-grid">
            <view
              v-for="cat in categories"
              :key="cat.key"
              class="category-cell"
              :class="{ pressed: pressedKey === `c-${cat.key}` }"
              @touchstart="pressedKey = `c-${cat.key}`"
              @touchend="pressedKey = ''"
              @touchcancel="pressedKey = ''"
              @mousedown="pressedKey = `c-${cat.key}`"
              @mouseup="pressedKey = ''"
              @mouseleave="pressedKey = ''"
              @tap="goCategory(cat)"
            >
              <view class="category-icon">
                <IconSvg :name="categoryIcon(cat.key)" :size="52" color="var(--color-primary)" />
              </view>
              <text class="category-label">{{ cat.label }}</text>
            </view>
          </view>
        </CardSection>

        <!-- 热搜榜单：排名 + 左侧圆角方配图 + 词 + 热度值/关联数 -->
        <CardSection v-if="dishStore.hotSearchList.length > 0">
          <SectionTitle title="本周热搜" />
          <view class="hotsearch-list">
            <view
              v-for="(h, idx) in dishStore.hotSearchList"
              :key="h.keyword"
              class="hotsearch-item"
              :class="{ pressed: pressedKey === `h-${h.keyword}` }"
              @touchstart="pressedKey = `h-${h.keyword}`"
              @touchend="pressedKey = ''"
              @touchcancel="pressedKey = ''"
              @mousedown="pressedKey = `h-${h.keyword}`"
              @mouseup="pressedKey = ''"
              @mouseleave="pressedKey = ''"
              @tap="goKeyword(h.keyword)"
            >
              <text class="hotsearch-rank" :class="{ top: idx < 3 }">{{ idx + 1 }}</text>
              <view class="hotsearch-thumb">
                <image v-if="hotImage(h)" :src="hotImage(h)" mode="aspectFill" class="hotsearch-thumb-img" />
                <IconSvg v-else name="fire" :size="32" color="var(--color-price)" />
              </view>
              <view class="hotsearch-body">
                <text class="hotsearch-keyword">{{ h.keyword }}</text>
                <text class="hotsearch-meta">
                  <text class="hotsearch-heat">{{ h.heat }} 热度</text>
                  <text v-if="h.relatedCount" class="hotsearch-related">· {{ h.relatedCount }} 个关联</text>
                </text>
              </view>
            </view>
          </view>
        </CardSection>
        </template>
      </view>

      <!-- ============ 多维筛选结果页 ============ -->
      <view v-else class="filter-result">
        <!-- 筛选控制条：第一行 = 返回 + 品类标题；第二行 = 排序 + 筛选 -->
        <view class="filter-bar">
          <view class="filter-bar-top">
            <text class="filter-back" @tap="exitFilter"><IconSvg name="arrow-left" :size="26" color="var(--color-primary)" /> 返回</text>
          </view>
          <view class="filter-bar-bottom">
            <SegmentTabs
              class="sort-tabs"
              :tabs="sortTabs"
              :model-value="activeSort"
              light
              @update:model-value="onSortChange"
            />
            <view class="filter-trigger" @tap="openFilterSheet">
              <IconSvg name="filter" :size="26" color="var(--text-secondary)" />
              <text class="filter-trigger-text">筛选</text>
            </view>
          </view>
        </view>

        <CardSection>
          <SectionTitle :title="categoryLabel">
            <text v-if="filterTag" slot="extra" class="section-extra category-count">共 {{ dishStore.dishList.length }} 个菜品</text>
          </SectionTitle>

          <view class="filter-summary" v-if="activeFilterSummary">
            <text class="filter-summary-text">{{ activeFilterSummary }}</text>
            <text class="filter-clear" @tap="clearFilter">清除筛选</text>
          </view>

          <WaterfallList v-if="dishStore.dishList.length > 0" :list="dishStore.dishList" @card-click="goToDetail" />
          <EmptyState
            v-else-if="!dishStore.loading"
            :text="keyword ? '没有找到相关菜品' : '没有符合条件的菜品'"
            :retry="true"
            @retry="reloadFilter"
          />

          <view v-if="dishStore.loading && filterLoadingMore" class="list-footer">
            <view class="footer-spinner" />
            <text class="footer-text">加载中…</text>
          </view>
          <view v-else-if="filterFinished" class="list-footer">
            <text class="footer-text">— 已经到底啦 —</text>
          </view>
        </CardSection>
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 通用筛选抽屉（canteen/price/taste；spring 0.8/0.3 + 手势中断 + 遮罩关闭） -->
    <FilterSheet
      :open="filterSheetOpen"
      :canteen-list="canteenNames"
      :category-list="categories"
      :model-value="filterState"
      @update:open="(v: boolean) => (filterSheetOpen = v)"
      @apply="onFilterSheetApply"
      @reset="resetSheetFilter"
    />

    <CustomTabBar current="/pages/find/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import Header from '@/components/header.vue'
import SearchBar from '@/components/SearchBar.vue'
import IconSvg from '@/components/IconSvg.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import EmptyState from '@/components/EmptyState.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import CardSection from '@/components/CardSection.vue'
import SegmentTabs from '@/components/SegmentTabs.vue'
import FilterSheet from '@/components/FilterSheet.vue'
import type { FilterSheetState } from '@/components/FilterSheet.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish, DishSortBy, Suggestion } from '@/types/dish'
import { DISH_CATEGORIES } from '@/constants/categories'

const dishStore = useDishStore()
const keyword = ref('')
const suggestions = ref<Suggestion[]>([])
const showSuggest = ref(false)
const pressedKey = ref('')
const refresherTriggered = ref(false)
const discoverLoading = ref(true)

// 搜索联想面板的运行时测量 top（px）。Header 高度含状态栏(px)，无法用固定 rpx 对齐，
// 必须在布局完成后用 selectorQuery 实测 Header 底 + searchWrap 高度（refs BLOCKER N1）
const suggestPanelTop = ref(200)

// 分类宫格
const categories = DISH_CATEGORIES

// 排序滑块（SegmentTabs）
const sortTabs = [
  { key: 'heat', label: '热度', icon: 'fire' },
  { key: 'rating', label: '评分', icon: 'star' },
  { key: 'price', label: '价格', icon: 'price' },
]

// 食堂名列表（供 FilterSheet 渲染）
const canteenNames = computed(() => dishStore.canteenList.map(c => c.name))

// ===== 搜索历史（本地缓存，预留接口位） =====
const HISTORY_KEY = 'find_search_history'
const HISTORY_MAX = 12
const historyList = ref<string[]>([])

function loadHistory() {
  try {
    const raw = uni.getStorageSync(HISTORY_KEY)
    if (Array.isArray(raw)) historyList.value = raw.slice(0, HISTORY_MAX)
  } catch { historyList.value = [] }
}
function saveHistory() {
  try { uni.setStorageSync(HISTORY_KEY, historyList.value) } catch { /* ignore */ }
}
function pushHistory(kw: string) {
  const k = kw.trim()
  if (!k) return
  historyList.value = [k, ...historyList.value.filter(x => x !== k)].slice(0, HISTORY_MAX)
  saveHistory()
}
function removeHistory(i: number) {
  historyList.value.splice(i, 1)
  saveHistory()
}
function clearHistory() {
  historyList.value = []
  saveHistory()
}

// 筛选模式
const inFilter = ref(false)
const activeSort = ref<DishSortBy>('heat')
// FilterSheet 双向草稿（canteen/price/taste/spiceLevel）
const filterState = ref<FilterSheetState>({ canteen: '', tag: '', priceMin: '', priceMax: '', spiceLevel: -1 })
const filterTag = ref('')
const filterSheetOpen = ref(false)
const filterPage = ref(1)
const filterPageSize = 20
const filterTotal = ref(0)
const filterLoadingMore = ref(false)
const filterFinished = ref(false)

const filterCanteenId = computed(() => {
  if (!filterState.value.canteen) return undefined
  // 一期无 canteenId 直连，按名称走 canteen 参数兼容
  return undefined
})

// 由分类进入时，展示的品类名（大标题头）
const categoryLabel = computed(() => {
  if (!filterTag.value) return ''
  const cat = categories.find(c => c.key === filterTag.value)
  return cat?.label || filterTag.value
})

const activeFilterSummary = computed(() => {  const parts: string[] = []
  if (filterState.value.canteen) parts.push(`食堂：${filterState.value.canteen}`)
  if (filterState.value.tag) {
    const cat = categories.find(c => c.key === filterState.value.tag)
    parts.push(`品类：${cat?.label || filterState.value.tag}`)
  }
  if (filterState.value.priceMin || filterState.value.priceMax) {
    parts.push(`价格：${filterState.value.priceMin || '0'}~${filterState.value.priceMax || '∞'}元`)
  }
  if (filterState.value.spiceLevel >= 0) {
    const spiceMap: Record<number, string> = { 0: '不辣', 1: '微辣', 2: '中辣', 3: '重辣' }
    parts.push(`辣度：${spiceMap[filterState.value.spiceLevel] || '不限'}`)
  }
  return parts.join('  ·  ')
})

function suggestIcon(type: Suggestion['type']): string {
  if (type === 'dish') return 'dish'
  if (type === 'stall') return 'dish'
  return 'home'
}
function suggestTypeLabel(type: Suggestion['type']): string {
  if (type === 'dish') return '菜品'
  if (type === 'stall') return '档口'
  return '食堂'
}

/** 分类图标名（映射 DISH_CATEGORIES.key → 独立矢量图标，禁止回退 dish，task-14 §0.5） */
function categoryIcon(key: string): string {
  const map: Record<string, string> = {
    noodle: 'noodle',
    rice: 'rice',
    malatang: 'malatang',
    breakfast: 'breakfast',
    midnight: 'midnight',
    fastfood: 'fastfood',
    snack: 'snack',
    drink: 'drink',
  }
  return map[key] || 'dish'
}

/** 热搜左侧配图：若联想数据中存在同名菜品图则展示，否则回落矢量图标（由模板处理） */
function hotImage(h: { keyword: string }): string {
  const match = suggestions.value.find(s => s.type === 'dish' && s.name === h.keyword)
  return match?.image || ''
}

let suggestTimer: ReturnType<typeof setTimeout> | null = null
function onKeywordInput() {
  showSuggest.value = true
  if (suggestTimer) clearTimeout(suggestTimer)
  suggestTimer = setTimeout(async () => {
    if (!keyword.value.trim()) {
      suggestions.value = []
      return
    }
      try {
      suggestions.value = await dishStore.fetchSuggestions(keyword.value)
    } catch {
      suggestions.value = []
    }
  }, 300)
}

function onSearchConfirm() {
  const kw = keyword.value.trim()
  showSuggest.value = false
  suggestions.value = []
  if (kw) pushHistory(kw)
  if (kw) {
    enterFilter({ keyword: kw, sortBy: 'heat' })
  } else {
    enterFilter({ sortBy: 'heat' })
  }
}

function clearKeyword() {
  keyword.value = ''
  suggestions.value = []
  showSuggest.value = false
}

function goSuggestion(s: Suggestion) {
  showSuggest.value = false
  suggestions.value = []
  keyword.value = s.name
  if (s.type === 'dish' && s.id) {
    uni.navigateTo({ url: `/pages/pages-detail/dish?id=${s.id}` })
  } else if (s.type === 'canteen' && s.name) {
    uni.navigateTo({ url: `/pages/pages-detail/canteen?canteen=${encodeURIComponent(s.name)}` })
  } else if (s.type === 'stall' && s.name) {
    dishStore.navParams.stallName = s.name
    uni.navigateTo({ url: '/pages/pages-detail/stall' })
  }
}

function goKeyword(kw: string) {
  keyword.value = kw
  pushHistory(kw)
  enterFilter({ keyword: kw, sortBy: 'heat' })
}

function goCategory(cat: { key: string; label: string }) {
  filterTag.value = cat.key
  enterFilter({ tag: cat.key, sortBy: 'heat' })
}

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

// ===== 筛选逻辑 =====
function buildFilterQuery(page = 1) {
  return {
    keyword: keyword.value.trim() || undefined,
    canteen: filterState.value.canteen || undefined,
    canteenId: filterCanteenId.value,
    tag: filterState.value.tag || undefined,
    minPrice: filterState.value.priceMin ? Number(filterState.value.priceMin) : undefined,
    maxPrice: filterState.value.priceMax ? Number(filterState.value.priceMax) : undefined,
    spiceLevel: filterState.value.spiceLevel >= 0 ? filterState.value.spiceLevel : undefined,
    sortBy: activeSort.value,
    sortOrder: 'desc' as const,
    page,
    pageSize: filterPageSize,
  }
}

function enterFilter(query: Parameters<typeof dishStore.searchPage>[0]) {
  inFilter.value = true
  filterPage.value = 1
  filterFinished.value = false
  dishStore.searchPage(query)
}

async function reloadFilter() {
  filterPage.value = 1
  filterFinished.value = false
  await dishStore.searchPage(buildFilterQuery(1))
  filterTotal.value = dishStore.dishList.length
}

function onSortChange(sort: string) {
  if (activeSort.value === sort) return
  activeSort.value = sort as DishSortBy
  reloadFilter()
}

async function onScrollToLower() {
  if (!inFilter.value) return
  if (filterFinished.value || filterLoadingMore.value) return
  filterLoadingMore.value = true
  const next = filterPage.value + 1
  try {
    const res = await dishStore.searchPage(buildFilterQuery(next))
    filterPage.value = next
    filterTotal.value = res.total
    if (dishStore.dishList.length >= res.total) filterFinished.value = true
  } catch {
    // 忽略，保留已加载
  } finally {
    filterLoadingMore.value = false
  }
}

function openFilterSheet() {
  filterSheetOpen.value = true
}
function resetSheetFilter() {
  filterState.value = { canteen: '', tag: '', priceMin: '', priceMax: '', spiceLevel: -1 }
}
function onFilterSheetApply(state: FilterSheetState) {
  filterState.value = { ...state }
  reloadFilter()
}
function clearFilter() {
  resetSheetFilter()
  keyword.value = ''
  reloadFilter()
}
function exitFilter() {
  inFilter.value = false
}

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  const task = inFilter.value
    ? reloadFilter()
    : loadDiscover()
  Promise.resolve(task).finally(() => { refresherTriggered.value = false })
}

async function loadDiscover() {
  discoverLoading.value = true
  try {
    await Promise.all([
      dishStore.fetchHotSearch(),
      dishStore.fetchCanteens(),
    ])
  } catch (e) {
    console.error('[find] 发现页加载失败', e)
  } finally {
    discoverLoading.value = false
  }
}

/** 实测 Header(.header-wrap) 底边 + search-wrap 高度，得到联想面板的 top(px)。
 * 失败时回退 200px，确保面板不会落在 0 处与 Header 重叠。 */
function measureSuggestTop() {
  try {
    uni.createSelectorQuery()
      .select('.header-wrap')
      .boundingClientRect((headerRes) => {
        const headerRect = headerRes as UniApp.NodeInfo
        uni.createSelectorQuery()
          .select('.search-wrap')
          .boundingClientRect((searchRes) => {
            const searchRect = searchRes as UniApp.NodeInfo
            const headerBottom = headerRect?.bottom ?? 0
            const searchHeight = searchRect?.height ?? 0
            if (headerBottom > 0 && searchHeight >= 0) {
              suggestPanelTop.value = headerBottom + searchHeight
            } else {
              suggestPanelTop.value = 200
            }
          })
          .exec()
      })
      .exec()
  } catch {
    suggestPanelTop.value = 200
  }
}

onMounted(() => {
  loadHistory()
  loadDiscover()
  // 布局就绪后再测量，避免拿到 0 高度（onReady/nextTick 双保险）
  nextTick(() => measureSuggestTop())
})

watch(keyword, (value) => {
  if (!value.trim()) showSuggest.value = false
})
</script>

<style scoped>
.find-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom)); }
.search-wrap { position: sticky; top: 0; z-index: 20; background: var(--bg-page); padding: var(--spacing-md) var(--spacing-md) var(--spacing-sm); }

/* 联想下拉：fixed 定位，脱离 scroll-view 避免 z-index 被裁剪（真机红线） */
.suggest-panel {
  position: fixed;
  left: var(--spacing-md);
  right: var(--spacing-md);
  /* top 由运行时实测（.header-wrap 底边 + .search-wrap 高度，px）写入 :style，
     不再硬编码 rpx，解决刘海屏状态栏 px 导致的错位（refs BLOCKER N1） */
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-modal);
  overflow: hidden;
  z-index: 100;
}
.suggest-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 2rpx solid var(--border-color);
  transition: transform 0.12s ease, background 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.suggest-item:last-child { border-bottom: none; }
.suggest-item.pressed { transform: scale(0.97); background: var(--bg-soft); }
.suggest-icon { width: 56rpx; height: 56rpx; border-radius: var(--radius-tag); overflow: hidden; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: var(--bg-page); }
.suggest-thumb { width: 100%; height: 100%; }
.suggest-name { flex: 1; font-size: var(--font-body); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.suggest-type { font-size: var(--font-aux); color: var(--text-tertiary); flex-shrink: 0; }

/* 区块通用 */
.section-extra { flex-shrink: 0; }

/* 历史搜索 */
.history-clear { font-size: var(--font-aux); color: var(--text-tertiary); font-weight: 500; padding: var(--spacing-xs); }
.history-chips { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.history-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  max-width: 320rpx;
  padding: var(--spacing-xs) var(--spacing-md);
  background: var(--bg-soft);
  border-radius: var(--radius-tag);
  transition: transform 0.12s ease, background 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}
.history-chip.pressed { transform: scale(0.97); background: var(--color-primary-soft); }
.history-chip-text { font-size: var(--font-aux); color: var(--text-secondary); font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-chip-del { font-size: 20rpx; color: var(--text-tertiary); flex-shrink: 0; line-height: 1; }

/* 分类宫格 */
.category-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--spacing-md); margin-top: var(--spacing-md); }
.category-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-md) var(--spacing-xs);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  transition: transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.category-cell.pressed { transform: scale(0.97); }
.category-icon { width: 72rpx; height: 72rpx; display: flex; align-items: center; justify-content: center; }
.category-label { font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; }

/* 热搜 */
.hotsearch-list { background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); overflow: hidden; }
.hotsearch-item { display: flex; align-items: center; gap: var(--spacing-md); padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); transition: transform 0.12s ease, background 0.12s ease; -webkit-tap-highlight-color: transparent; }
.hotsearch-item:last-child { border-bottom: none; }
.hotsearch-item.pressed { transform: scale(0.97); background: var(--bg-soft); }
.hotsearch-rank { width: 44rpx; text-align: center; font-size: var(--font-body); font-weight: 800; color: var(--text-secondary); flex-shrink: 0; }
.hotsearch-rank.top { color: var(--color-price); }
/* 左侧配图：圆角正方形（task-13 §1.2） */
.hotsearch-thumb { width: 72rpx; height: 72rpx; border-radius: var(--radius-card); overflow: hidden; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.hotsearch-thumb-img { width: 100%; height: 100%; }
.hotsearch-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.hotsearch-keyword { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hotsearch-meta { display: flex; align-items: center; gap: var(--spacing-xs); font-size: var(--font-aux); color: var(--text-tertiary); }
.hotsearch-heat { color: var(--color-price); font-weight: 600; }
.hotsearch-related { color: var(--text-tertiary); }

/* 筛选结果页 */
/* 品类数量（SectionTitle #extra 中展示） */
.category-count { font-size: var(--font-aux); color: var(--text-tertiary); font-weight: 500; }
/* 筛选控制条：两行 —— 上行：返回；下行：排序 + 筛选 */
.filter-bar { display: flex; flex-direction: column; gap: var(--spacing-xs); padding: var(--spacing-sm) 0 var(--spacing-xs); }
.filter-bar-top { display: flex; align-items: center; }
.filter-bar-bottom { display: flex; align-items: center; gap: var(--spacing-xs); flex-wrap: nowrap; }
.sort-tabs { flex: 1; min-width: 0; }
.filter-trigger {
  display: inline-flex; align-items: center; gap: 6rpx;
  padding: var(--spacing-xs) var(--spacing-md); border-radius: 28rpx;
  font-size: 26rpx; font-weight: 600; background: var(--bg-card); color: var(--text-secondary);
  box-shadow: var(--shadow-card); flex-shrink: 0; -webkit-tap-highlight-color: transparent;
}
.filter-trigger:active { transform: scale(0.97); }
.filter-trigger-text { line-height: 1; }
.filter-back { display: inline-flex; align-items: center; gap: 6rpx; font-size: 26rpx; color: var(--color-primary); font-weight: 600; flex-shrink: 0; }
.filter-summary { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); padding: 0 0 var(--spacing-sm); margin-top: var(--spacing-sm); }
.filter-summary-text { flex: 1; font-size: var(--font-aux); color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.filter-clear { font-size: var(--font-aux); color: var(--color-error); flex-shrink: 0; }

/* 列表底部 */
.list-footer { display: flex; align-items: center; justify-content: center; padding: var(--spacing-md) 0; gap: var(--spacing-xs); }
.footer-spinner { width: 28rpx; height: 28rpx; border: 4rpx solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
.footer-text { font-size: var(--font-aux); color: var(--text-tertiary); }
@keyframes spin { to { transform: rotate(360deg); } }

/* 发现主页首屏骨架 */
.discover-skeleton { padding: 0 var(--spacing-md); }
.sk-row { display: flex; gap: var(--spacing-sm); margin-bottom: var(--spacing-lg); }
.sk-chip { height: 64rpx; border-radius: var(--radius-tag); flex: 0 0 160rpx; }
.sk-cat { width: 120rpx; height: 150rpx; border-radius: var(--radius-card); flex: 0 0 auto; }
.sk-line { height: 110rpx; border-radius: var(--radius-card); flex: 1; }

@media (prefers-reduced-motion: reduce) {
  .footer-spinner { animation-duration: 1.4s; }
}
</style>
