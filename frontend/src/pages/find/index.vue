<template>
  <view class="page find-page">
    <Header title="发现" />

    <!-- 搜索框（联想，debounce 300ms） -->
    <view class="search-wrap" :style="{ margin: 'var(--spacing-sm) var(--spacing-lg)' }">
      <view class="search-bar-inline">
        <text class="search-icon-img">{{ EMOJI.search }}</text>
        <input
          class="search-input"
          v-model="keyword"
          placeholder="搜索菜品、档口或食堂"
          confirm-type="search"
          @input="onKeywordInput"
          @confirm="onSearchConfirm"
        />
        <text v-if="keyword" class="clear-btn" @tap="clearKeyword">✕</text>
      </view>

      <!-- 搜索联想下拉 -->
      <view v-if="showSuggest && suggestions.length > 0" class="suggest-panel">
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
          <text class="suggest-icon">{{ suggestIcon(s.type) }}</text>
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
      <!-- ============ 发现主页（榜单/宫格） ============ -->
      <view v-if="!inFilter" class="discover-home">
        <!-- 分类宫格 -->
        <view class="block" v-if="categories.length > 0">
          <view class="block-head">
            <view class="section-bar" />
            <text class="section-title">菜品分类</text>
          </view>
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
              <text class="category-icon">{{ cat.icon }}</text>
              <text class="category-label">{{ cat.label }}</text>
            </view>
          </view>
        </view>

        <!-- 热搜 TOP10 -->
        <view class="block" v-if="dishStore.hotSearchList.length > 0">
          <view class="block-head">
            <text class="section-bar" />
            <text class="section-title">{{ EMOJI.fire }} 本周热搜</text>
          </view>
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
              <text class="hotsearch-keyword">{{ h.keyword }}</text>
              <text class="hotsearch-heat">{{ EMOJI.fire }} {{ h.heat }}</text>
            </view>
          </view>
        </view>

        <!-- 新晋黑马 -->
        <view class="block" v-if="dishStore.risingDishes.length > 0">
          <view class="block-head">
            <view class="section-bar" />
            <text class="section-title">{{ EMOJI.new }} 新晋黑马</text>
          </view>
          <scroll-view class="horiz-scroll" scroll-x show-scrollbar="false">
            <view class="horiz-track">
              <DishCard
                v-for="dish in dishStore.risingDishes"
                :key="dish.id"
                class="rising-card"
                :dish="dish"
                @click="goToDetail"
              />
            </view>
          </scroll-view>
        </view>

        <!-- 新上架 -->
        <view class="block" v-if="dishStore.newDishes.length > 0">
          <view class="block-head">
            <view class="section-bar" />
            <text class="section-title">{{ EMOJI.calendar }} 新上架</text>
          </view>
          <WaterfallList :list="dishStore.newDishes">
            <template #card="{ item: dish }">
              <DishCard :dish="dish" @click="goToDetail" />
            </template>
          </WaterfallList>
        </view>
        <EmptyState v-else-if="discoverLoaded" text="暂无新上架菜品" />
      </view>

      <!-- ============ 多维筛选结果页 ============ -->
      <view v-else class="filter-result">
        <view class="filter-bar">
          <view
            class="filter-sort"
            :class="{ active: activeSort === 'heat' }"
            @tap="switchSort('heat')"
          >{{ EMOJI.hot }} 热度</view>
          <view
            class="filter-sort"
            :class="{ active: activeSort === 'rating' }"
            @tap="switchSort('rating')"
          >{{ EMOJI.starFilled }} 评分</view>
          <view
            class="filter-sort"
            :class="{ active: activeSort === 'price' }"
            @tap="switchSort('price')"
          >{{ EMOJI.price }} 价格</view>
          <view class="filter-sort filter-trigger" @tap="openFilterSheet">{{ EMOJI.filter }} 筛选</view>
          <text class="filter-back" @tap="exitFilter">{{ EMOJI.back }} 返回</text>
        </view>

        <view class="filter-summary" v-if="activeFilterSummary">
          <text class="filter-summary-text">{{ activeFilterSummary }}</text>
          <text class="filter-clear" @tap="clearFilter">清除筛选</text>
        </view>

        <WaterfallList v-if="dishStore.dishList.length > 0" :list="dishStore.dishList">
          <template #card="{ item: dish }">
            <DishCard :dish="dish" @click="goToDetail" />
          </template>
        </WaterfallList>
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
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 筛选抽屉（spring 0.8/0.3 + 点击遮罩关闭） -->
    <view v-if="filterSheetOpen" class="sheet-mask" @tap="closeFilterSheet" />
    <view class="filter-sheet" :class="{ open: filterSheetOpen }">
      <view class="sheet-head">
        <text class="sheet-title">筛选</text>
        <text class="sheet-close" @tap="closeFilterSheet">✕</text>
      </view>

      <scroll-view class="sheet-body" scroll-y>
        <!-- 食堂 -->
        <view class="sheet-section">
          <text class="sheet-label">食堂</text>
          <view class="chip-wrap">
            <view
              v-for="c in dishStore.canteenList"
              :key="c.name"
              class="chip"
              :class="{ active: filterCanteen === c.name }"
              @tap="filterCanteen = filterCanteen === c.name ? '' : c.name"
            >{{ c.name }}</view>
          </view>
        </view>

        <!-- 价格区间 -->
        <view class="sheet-section">
          <text class="sheet-label">价格区间（元）</text>
          <view class="price-row">
            <input class="price-input" type="digit" v-model="priceMin" placeholder="最低" />
            <text class="price-dash">—</text>
            <input class="price-input" type="digit" v-model="priceMax" placeholder="最高" />
          </view>
        </view>

        <!-- 口味标签 -->
        <view class="sheet-section">
          <text class="sheet-label">口味 / 品类</text>
          <view class="chip-wrap">
            <view
              v-for="cat in categories"
              :key="cat.key"
              class="chip"
              :class="{ active: filterTag === cat.key }"
              @tap="filterTag = filterTag === cat.key ? '' : cat.key"
            >{{ cat.label }}</view>
          </view>
        </view>
      </scroll-view>

      <view class="sheet-footer">
        <view class="sheet-reset" @tap="resetSheetFilter">重置</view>
        <view class="sheet-apply" @tap="applySheetFilter">查看结果</view>
      </view>
    </view>

    <CustomTabBar current="/pages/find/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { EMOJI } from '@/utils/emoji'
import Header from '@/components/header.vue'
import SearchBar from '@/components/SearchBar.vue'
import DishCard from '@/components/DishCard.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import EmptyState from '@/components/EmptyState.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish, DishSortBy, Suggestion } from '@/types/dish'
import { DISH_CATEGORIES } from '@/constants/categories'

const dishStore = useDishStore()
const keyword = ref('')
const suggestions = ref<Suggestion[]>([])
const showSuggest = ref(false)
const pressedKey = ref('')
const refresherTriggered = ref(false)
const discoverLoaded = ref(false)

// 分类宫格
const categories = DISH_CATEGORIES

// 筛选模式
const inFilter = ref(false)
const activeSort = ref<DishSortBy>('heat')
const filterCanteen = ref('')
const filterTag = ref('')
const priceMin = ref('')
const priceMax = ref('')
const filterSheetOpen = ref(false)
const filterPage = ref(1)
const filterPageSize = 20
const filterTotal = ref(0)
const filterLoadingMore = ref(false)
const filterFinished = ref(false)

const filterCanteenId = computed(() => {
  if (!filterCanteen.value) return undefined
  // 一期无 canteenId 直连，按名称走 canteen 参数兼容
  return undefined
})

const activeFilterSummary = computed(() => {
  const parts: string[] = []
  if (filterCanteen.value) parts.push(`食堂：${filterCanteen.value}`)
  if (filterTag.value) {
    const cat = categories.find(c => c.key === filterTag.value)
    parts.push(`品类：${cat?.label || filterTag.value}`)
  }
  if (priceMin.value || priceMax.value) {
    parts.push(`价格：${priceMin.value || '0'}~${priceMax.value || '∞'}元`)
  }
  return parts.join('  ·  ')
})

function suggestIcon(type: Suggestion['type']): string {
  if (type === 'dish') return EMOJI.dishPlaceholder
  if (type === 'stall') return EMOJI.canteenDish
  return EMOJI.home
}
function suggestTypeLabel(type: Suggestion['type']): string {
  if (type === 'dish') return '菜品'
  if (type === 'stall') return '档口'
  return '食堂'
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
    canteen: filterCanteen.value || undefined,
    canteenId: filterCanteenId.value,
    tag: filterTag.value || undefined,
    minPrice: priceMin.value ? Number(priceMin.value) : undefined,
    maxPrice: priceMax.value ? Number(priceMax.value) : undefined,
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

function switchSort(sort: DishSortBy) {
  if (activeSort.value === sort) return
  activeSort.value = sort
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
function closeFilterSheet() {
  filterSheetOpen.value = false
}
function resetSheetFilter() {
  filterCanteen.value = ''
  filterTag.value = ''
  priceMin.value = ''
  priceMax.value = ''
}
function applySheetFilter() {
  filterSheetOpen.value = false
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
  try {
    await Promise.all([
      dishStore.fetchHotSearch(),
      dishStore.fetchRising(),
      dishStore.fetchNewDishes(),
      dishStore.fetchCanteens(),
    ])
  } catch (e) {
    console.error('[find] 发现页加载失败', e)
  } finally {
    discoverLoaded.value = true
  }
}

onMounted(() => {
  loadDiscover()
})

watch(keyword, (value) => {
  if (!value.trim()) showSuggest.value = false
})
</script>

<style scoped>
.find-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.search-wrap { position: relative; z-index: 20; }
.search-bar-inline {
  display: flex;
  align-items: center;
  background: var(--bg-card);
  border-radius: var(--radius-btn);
  padding: var(--spacing-sm) var(--spacing-lg);
  box-shadow: 0 2rpx 8rpx var(--overlay-dark-faint);
  border: 2rpx solid var(--border-color);
}
.search-icon-img { font-size: 30rpx; line-height: 1; margin-right: var(--spacing-sm); }
.search-input { flex: 1; font-size: var(--font-body); color: var(--text-primary); background: transparent; border: none; outline: none; }
.clear-btn { font-size: var(--font-body); color: var(--text-tertiary); padding: 0 var(--spacing-xs); flex-shrink: 0; }

/* 联想下拉 */
.suggest-panel {
  position: absolute;
  left: var(--spacing-lg);
  right: var(--spacing-lg);
  top: calc(100% + 8rpx);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-modal);
  overflow: hidden;
  z-index: 30;
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
.suggest-icon { font-size: 32rpx; line-height: 1; }
.suggest-name { flex: 1; font-size: var(--font-body); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.suggest-type { font-size: var(--font-aux); color: var(--text-tertiary); flex-shrink: 0; }

/* 区块通用 */
.block { margin-bottom: var(--spacing-lg); padding: 0 var(--spacing-md); }
.block-head { display: flex; align-items: center; margin-bottom: var(--spacing-sm); }
.section-bar { width: 8rpx; height: 32rpx; border-radius: 999rpx; background: var(--color-primary); margin-right: var(--spacing-xs); flex-shrink: 0; }
.section-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); letter-spacing: -0.01em; }

/* 分类宫格 */
.category-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--spacing-md); }
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
.category-icon { font-size: 52rpx; line-height: 1; }
.category-label { font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; }

/* 热搜 */
.hotsearch-list { background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); overflow: hidden; }
.hotsearch-item { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md); border-bottom: 2rpx solid var(--border-color); transition: transform 0.12s ease, background 0.12s ease; -webkit-tap-highlight-color: transparent; }
.hotsearch-item:last-child { border-bottom: none; }
.hotsearch-item.pressed { transform: scale(0.97); background: var(--bg-soft); }
.hotsearch-rank { width: 40rpx; text-align: center; font-size: var(--font-body); font-weight: 800; color: var(--text-tertiary); }
.hotsearch-rank.top { color: var(--color-price); }
.hotsearch-keyword { flex: 1; font-size: var(--font-body); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hotsearch-heat { font-size: var(--font-aux); color: var(--text-tertiary); flex-shrink: 0; }

/* 横滑 */
.horiz-scroll { overflow-x: auto; white-space: nowrap; }
.horiz-scroll::-webkit-scrollbar { display: none; }
.horiz-track { display: inline-flex; gap: var(--spacing-md); padding-bottom: 4rpx; }
.rising-card { width: 320rpx; display: inline-block; }

/* 筛选结果页 */
.filter-bar { display: flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-sm) var(--spacing-md); flex-wrap: wrap; }
.filter-sort { padding: var(--spacing-xs) var(--spacing-md); border-radius: 28rpx; font-size: 26rpx; font-weight: 600; background: var(--bg-placeholder); color: var(--text-secondary); transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 0.12s var(--ease-out); -webkit-tap-highlight-color: transparent; }
.filter-sort.active { background: var(--color-primary); color: var(--text-white); }
.filter-sort:active { transform: scale(0.97); }
.filter-trigger { margin-left: auto; }
.filter-back { font-size: 26rpx; color: var(--color-primary); font-weight: 600; }
.filter-summary { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); padding: 0 var(--spacing-md) var(--spacing-sm); }
.filter-summary-text { flex: 1; font-size: var(--font-aux); color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.filter-clear { font-size: var(--font-aux); color: var(--color-error); flex-shrink: 0; }

/* 列表底部 */
.list-footer { display: flex; align-items: center; justify-content: center; padding: var(--spacing-md) 0; gap: var(--spacing-xs); }
.footer-spinner { width: 28rpx; height: 28rpx; border: 4rpx solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
.footer-text { font-size: var(--font-aux); color: var(--text-tertiary); }
@keyframes spin { to { transform: rotate(360deg); } }

/* 筛选抽屉 */
.sheet-mask { position: fixed; inset: 0; background: var(--overlay-scrim); z-index: 90; }
.filter-sheet {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 100;
  transform: translateY(100%);
  transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1);
  display: flex;
  flex-direction: column;
  max-height: 80vh;
  padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom));
}
.filter-sheet.open { transform: translateY(0); }
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); }
.sheet-close { font-size: var(--font-body); color: var(--text-tertiary); padding: 0 var(--spacing-xs); }
.sheet-body { flex: 1; overflow-y: auto; padding: var(--spacing-md); }
.sheet-section { margin-bottom: var(--spacing-lg); }
.sheet-label { display: block; font-size: var(--font-body); font-weight: 600; color: var(--text-primary); margin-bottom: var(--spacing-sm); }
.chip-wrap { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.chip { padding: var(--spacing-xs) var(--spacing-md); border-radius: var(--radius-tag); font-size: var(--font-aux); background: var(--bg-placeholder); color: var(--text-secondary); transition: background 0.15s, color 0.15s; -webkit-tap-highlight-color: transparent; }
.chip.active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: 700; }
.price-row { display: flex; align-items: center; gap: var(--spacing-sm); }
.price-input { flex: 1; height: 72rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: 0 var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); text-align: center; }
.price-dash { color: var(--text-tertiary); }
.sheet-footer { display: flex; gap: var(--spacing-md); padding: var(--spacing-md); border-top: 2rpx solid var(--border-color); }
.sheet-reset { flex: 1; height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--bg-soft); color: var(--text-secondary); font-weight: 600; -webkit-tap-highlight-color: transparent; }
.sheet-apply { flex: 2; height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--color-primary); color: var(--text-white); font-weight: 700; -webkit-tap-highlight-color: transparent; }

@media (prefers-reduced-motion: reduce) {
  .footer-spinner { animation-duration: 1.4s; }
  .filter-sheet { transition: opacity 0.2s ease; }
}
</style>
