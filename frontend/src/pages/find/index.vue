<template>
  <view class="page find-page">
    <Header title="发现" />

    <!-- 搜索框（SearchBar 组件，输入模式 + 联想 debounce 300ms）；宽度放宽至接近内容区满宽，左右留白与首页一致 -->
    <view class="search-wrap" :style="{ margin: 'var(--spacing-sm) var(--spacing-md)' }">
      <SearchBar
        v-model="keyword"
        input-mode
        placeholder="搜索菜品、档口或食堂"
        @search="onSearchConfirm"
        @update:model-value="onKeywordInput"
      />

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
        <!-- 历史搜索：标签 chip 行，可单个删除 / 一键清空 -->
        <view class="block" v-if="historyList.length > 0">
          <SectionTitle title="历史搜索">
            <text class="section-extra history-clear" @tap="clearHistory">清空</text>
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
        </view>

        <!-- 分类入口宫格：直接置于搜索框下方（去掉「分类」标题文字，task-13 §1.2） -->
        <view class="block category-block" v-if="categories.length > 0">
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
        </view>

        <!-- 热搜榜单：排名 + 左侧圆角方配图 + 词 + 热度值/关联数 -->
        <view class="block" v-if="dishStore.hotSearchList.length > 0">
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
        </view>
      </view>

      <!-- ============ 多维筛选结果页 ============ -->
      <view v-else class="filter-result" :style="{ padding: '0 var(--spacing-md)' }">
        <view class="filter-bar">
          <view
            class="filter-sort"
            :class="{ active: activeSort === 'heat' }"
            @tap="switchSort('heat')"
          ><IconSvg name="fire" :size="26" :color="activeSort === 'heat' ? 'var(--text-white)' : 'var(--text-secondary)'" /> 热度</view>
          <view
            class="filter-sort"
            :class="{ active: activeSort === 'rating' }"
            @tap="switchSort('rating')"
          ><IconSvg name="star" :size="26" :color="activeSort === 'rating' ? 'var(--text-white)' : 'var(--text-secondary)'" /> 评分</view>
          <view
            class="filter-sort"
            :class="{ active: activeSort === 'price' }"
            @tap="switchSort('price')"
          ><IconSvg name="price" :size="26" :color="activeSort === 'price' ? 'var(--text-white)' : 'var(--text-secondary)'" /> 价格</view>
          <view class="filter-sort filter-trigger" @tap="openFilterSheet"><IconSvg name="filter" :size="26" color="var(--text-secondary)" /> 筛选</view>
          <text class="filter-back" @tap="exitFilter"><IconSvg name="back" :size="26" color="var(--color-primary)" /> 返回</text>
        </view>

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
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 筛选抽屉（spring 0.8/0.3 + 点击遮罩关闭） -->
    <view v-if="filterSheetOpen" class="sheet-mask" @tap="closeFilterSheet" />
    <view class="filter-sheet" :class="{ open: filterSheetOpen }">
      <view class="sheet-head">
        <text class="sheet-title">筛选</text>
        <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" @click="closeFilterSheet" />
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
import Header from '@/components/header.vue'
import SearchBar from '@/components/SearchBar.vue'
import IconSvg from '@/components/IconSvg.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import EmptyState from '@/components/EmptyState.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import SectionTitle from '@/components/SectionTitle.vue'
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
  if (type === 'dish') return 'dish'
  if (type === 'stall') return 'dish'
  return 'home'
}
function suggestTypeLabel(type: Suggestion['type']): string {
  if (type === 'dish') return '菜品'
  if (type === 'stall') return '档口'
  return '食堂'
}

/** 分类图标名（映射 DISH_CATEGORIES.key → 矢量图标） */
function categoryIcon(key: string): string {
  const map: Record<string, string> = {
    noodle: 'dish',
    rice: 'dish',
    malatang: 'fire',
    breakfast: 'dish',
    midnight: 'clock',
    fastfood: 'dish',
    snack: 'dish',
    drink: 'dish',
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
      dishStore.fetchCanteens(),
    ])
  } catch (e) {
    console.error('[find] 发现页加载失败', e)
  } finally {
    discoverLoaded.value = true
  }
}

onMounted(() => {
  loadHistory()
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
.suggest-icon { width: 56rpx; height: 56rpx; border-radius: var(--radius-tag); overflow: hidden; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: var(--bg-page); }
.suggest-thumb { width: 100%; height: 100%; }
.suggest-name { flex: 1; font-size: var(--font-body); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.suggest-type { font-size: var(--font-aux); color: var(--text-tertiary); flex-shrink: 0; }

/* 区块通用 */
.block { margin-bottom: var(--spacing-lg); padding: 0 var(--spacing-md); }
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
  border-radius: 999rpx;
  transition: transform 0.12s ease, background 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}
.history-chip.pressed { transform: scale(0.97); background: var(--color-primary-soft); }
.history-chip-text { font-size: var(--font-aux); color: var(--text-secondary); font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-chip-del { font-size: 20rpx; color: var(--text-tertiary); flex-shrink: 0; line-height: 1; }

/* 发现食堂入口（横滑） */
.discover-canteen { width: 200rpx; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; gap: var(--spacing-xs); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.discover-canteen.pressed { transform: scale(0.97); }
.discover-canteen-img { width: 160rpx; height: 160rpx; border-radius: var(--radius-card); background: var(--bg-page); box-shadow: var(--shadow-card); }
.discover-canteen-placeholder { display: flex; align-items: center; justify-content: center; }
.discover-canteen-illu { font-size: 72rpx; line-height: 1; opacity: 0.3; }
.discover-canteen-name { font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; max-width: 200rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

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
.hotsearch-rank { width: 44rpx; text-align: center; font-size: var(--font-body); font-weight: 800; color: var(--text-tertiary); flex-shrink: 0; }
.hotsearch-rank.top { color: var(--color-price); }
/* 左侧配图：圆角正方形（task-13 §1.2） */
.hotsearch-thumb { width: 72rpx; height: 72rpx; border-radius: var(--radius-card); overflow: hidden; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.hotsearch-thumb-img { width: 100%; height: 100%; }
.hotsearch-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.hotsearch-keyword { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hotsearch-meta { display: flex; align-items: center; gap: var(--spacing-xs); font-size: var(--font-aux); color: var(--text-tertiary); }
.hotsearch-heat { color: var(--color-price); font-weight: 600; }
.hotsearch-related { color: var(--text-tertiary); }

/* 横滑 */
.horiz-scroll { overflow-x: auto; white-space: nowrap; }
.horiz-scroll::-webkit-scrollbar { display: none; }
.horiz-track { display: inline-flex; gap: var(--spacing-md); padding-bottom: 4rpx; }
.rising-card { width: 320rpx; display: inline-block; }

/* 筛选结果页 */
.filter-bar { display: flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-sm) var(--spacing-md); flex-wrap: wrap; }
.filter-sort { display: inline-flex; align-items: center; gap: 6rpx; padding: var(--spacing-xs) var(--spacing-md); border-radius: 28rpx; font-size: 26rpx; font-weight: 600; background: var(--bg-placeholder); color: var(--text-secondary); transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 0.12s var(--ease-out); -webkit-tap-highlight-color: transparent; }
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
