<template>
  <view class="page find-page">
    <!-- 顶部固定搜索头：复用 AppHeader search variant（client-page-structure-header-consolidation：消除自绘 header 漂移） -->
    <AppHeader
      variant="search"
      v-model="keyword"
      :show-back="true"
      @back="inFilter ? exitFilter() : goBackHome()"
      @search="onSearchConfirm"
      @clear="clearKeyword"
    />

    <!-- 结果态筛选条：仅出搜索结果时渲染，与首页共用同一 FilterBar（client-filter-bar-consolidation） -->
    <view v-if="inFilter" class="find-filter-row">
      <!-- 胶囊高度不再传硬编码：FilterBar 组件内按 navMetrics.getCapsuleHeight 自取（与 AppHeader 同一真源，MP-017） -->
      <FilterBar
        class="fb-host"
        :canteens="dishStore.canteenList"
        :selected-canteen-id="findCanteenId"
        :price-range="findPrice"
        @canteen-select="onFindCanteenSelect"
        @price-select="onFindPriceSelect"
      />
    </view>

    <!-- 内容区（find-page-layout-restructure）：双态分支互斥。
         发现态 = 静态区块（无页面级 scroll/下拉刷新）；结果态 = 滚动随 FindResults 内容区 -->
    <view class="find-body">
      <!-- ============ 发现主页（未进入结果态）：历史 + 猜你想搜，静态展示 ============ -->
      <view v-if="!inFilter" class="discover-body">
        <template>
          <!-- 搜索记录（首位） -->
          <CardSection v-if="historyList.length > 0">
            <SectionTitle title="搜索记录">
              <!-- QA-03：破坏性操作补可访问角色与标签（热区见 .history-clear::after） -->
              <text
                slot="extra"
                class="section-extra history-clear"
                role="button"
                aria-label="清空搜索历史"
                @tap="clearHistory"
              >清空</text>
            </SectionTitle>
            <!-- 搜索记录收敛（find-page-layout-restructure 2.6）：缓存上限 4 条、全部直接展示、无「展开/收起」 -->
            <view class="history-chips">
              <view
                v-for="(kw, i) in historyList"
                :key="kw"
                class="history-chip"
                @tap="goKeyword(kw)"
              >
                <text class="history-chip-text">{{ kw }}</text>
                <view class="history-chip-del" @tap.stop="removeHistory(i)">
                  <IconSvg name="close" :size="24" color="var(--text-tertiary)" />
                </view>
              </view>
            </view>
          </CardSection>

          <!-- 热搜词（GET /dishes/hot-search，由后端派生；点击直接搜索） -->
          <CardSection v-if="hotSearchList.length > 0">
            <SectionTitle title="猜你想搜" />
            <view class="history-chips">
              <view
                v-for="(kw) in hotSearchList"
                :key="kw.keyword"
                class="history-chip history-chip-hot"
                @tap="goKeyword(kw.keyword)"
              >
                <text class="history-chip-text">{{ kw.keyword }}</text>
              </view>
            </view>
          </CardSection>

        </template>
      </view>

      <!-- ============ 搜索混合结果态：滚动容器在 FindResults 内容区内（仅结果态渲染） ============ -->
      <FindResults
        v-else-if="filteredMixed.length > 0"
        class="results-host"
        :items="filteredMixed"
        :keyword="keyword"
        :refresher-triggered="refresherTriggered"
        @select="goToMixed"
        @refresh="onResultsRefresh"
      />
      <!-- 搜索失败重试块（MP-012）：请求已完成且失败 → 极简「加载失败 · 点击重试」行内块，
           先于空态渲染，避免网络失败被误导向「没搜到」的无结果引导（三态：失败 ≠ 无数据） -->
      <view
        v-else-if="inFilter && searchDone && searchFailed"
        class="find-retry"
        role="button"
        aria-label="搜索失败，点击重试"
        hover-class="pressed"
        @tap="onRetrySearch"
      >
        <IconSvg name="report" :size="44" color="var(--text-tertiary)" />
        <text class="fr-title">搜索加载失败</text>
        <text class="fr-hint">网络似乎不太顺畅 · 点击重试</text>
      </view>
      <!-- 搜索无结果引导（search-no-result-guidance）：请求**已完成**且结果为空才呈现；
           未完成（静默）或失败（走上方重试块）不渲染，避免闪现/误导向。引导把没找到的菜报给我们 -->
      <view v-else-if="inFilter && searchDone" class="find-empty">
        <view class="fe-icon">
          <IconSvg name="search" :size="48" color="var(--text-tertiary)" />
        </view>
        <text class="fe-title">没搜到「{{ keyword }}」相关的菜</text>
        <text class="fe-desc">把它报给我们，让更多同学也能找到</text>
        <view class="fe-btn" role="button" aria-label="推荐这道菜" hover-class="pressed" @tap="goContributeNotFound">
          <text class="fe-btn-text">推荐这道菜</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import { useDishStore, type HomeSortKey } from '@/stores/dish'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import { useLocationStore } from '@/stores/location'
import type { DishSortBy } from '@/types/dish'
import { getUserLocation } from '@/utils/location'
import { PATH, dishDetailUrl, feedbackEntryUrl } from '@/utils/routes'
import IconSvg from '@/components/IconSvg.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import CardSection from '@/components/CardSection.vue'
import FilterBar from '@/components/FilterBar.vue'
import AppHeader from '@/components/AppHeader.vue'
import FindResults from './FindResults.vue'
import { MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'

const dishStore = useDishStore()
const locationStore = useLocationStore()

/** 搜索页为非 tab 二级页：返回回首页（2026-08-03 修复：用 navigateBack 带返回动画；无上一页时兜底 reLaunch） */
function goBackHome() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.reLaunch({ url: PATH.home })
  }
}

/** 菜品详情：跳转独立页（pages/detail/dish） */
function openDishDetail(id: number) {
  if (!id) return
  uni.navigateTo({ url: dishDetailUrl(id) })
}
const keyword = ref('')
const refresherTriggered = ref(false)

// ===== 搜索历史（本地缓存，预留接口位） =====
const HISTORY_KEY = 'find_search_history'
/** 搜索记录上限 4 条（find-page-layout-restructure 2.6：缓存与展示一致、无展开收起） */
const HISTORY_MAX = 4
const historyList = ref<string[]>([])

/** 热搜词列表（来源：后端 GET /dishes/hot-search，由 loadDiscover → fetchHotSearch 拉取，无前端 mock） */
const hotSearchList = computed(() => dishStore.hotSearchList)

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
  // 清空全部历史是破坏性操作，加二次确认防误触（单条删除保留即时，逐条确认会打断）
  uni.showModal({
    title: '清空搜索历史',
    content: '确定要清空全部搜索历史吗？此操作不可恢复。',
    confirmText: '清空',
    confirmColor: MODAL_CONFIRM_DANGER_COLOR,
    success: (res) => {
      if (!res.confirm) return
      historyList.value = []
      saveHistory()
    },
  })
}

// 搜索模式（2026-08-03：结果页改为复合型混合列表，无排序/筛选）
const inFilter = ref(false)
/** 搜索请求是否已完成（成功/失败均置真，过期请求不置）：用于区分「静默加载中」与「无结果引导」，避免空态闪现 */
const searchDone = ref(false)
/** 最近一次已完成搜索是否失败（MP-012）：失败 ≠ 无结果，失败渲染重试块而非「没搜到」空态 */
const searchFailed = ref(false)

// 食堂筛选（find 页独立状态，与首页 selectedCanteenId 隔离）
const findCanteenId = ref<number | null>(null)
function onFindCanteenSelect(id: number | null) {
  findCanteenId.value = id && id > 0 ? id : null
  // 切换食堂即按当前关键词（可空）+ 食堂重新检索
  doMixedSearch(keyword.value.trim())
}

// ===== 结果态筛选（仅 inFilter 渲染，与首页共用 FilterBar：食堂 / 价格，仅展开时红底） =====
const findSortBy = ref<HomeSortKey>('latest')
/** 当前价格区间（元）；回显由 FilterBar 直显元，提交直接透传（api 层统一元→分，禁止二次换算） */
const findPrice = ref<{ min?: number; max?: number }>({})

function findSortParams(key: HomeSortKey): { sortBy: DishSortBy; sortOrder: 'asc' | 'desc' } {
  switch (key) {
    case 'latest': return { sortBy: 'created_at', sortOrder: 'desc' }
    case 'priceAsc': return { sortBy: 'price', sortOrder: 'asc' }
    case 'priceDesc': return { sortBy: 'price', sortOrder: 'desc' }
    case 'hot': return { sortBy: 'heat', sortOrder: 'desc' }
    case 'distance': return { sortBy: 'heat', sortOrder: 'desc' }
  }
}

function onFindPriceSelect(range: { min?: number; max?: number }) {
  findPrice.value = range
  doMixedSearch(keyword.value.trim())
}

/** 混合搜索结果：复用菜品检索接口返回 Dish[]（搜索仅针对菜品） */
interface MixedResult {
  type: 'dish'
  id?: number
  name: string
  image?: string
  /** 副信息：菜品→「档口 · 食堂」（B8 档口名）；档口/食堂→位置 */
  sub?: string
  /** 菜品专属：价格（元，api 层已转） */
  price?: number
  /** 菜品专属：平均评分 */
  rating?: number
  /** 菜品专属：评价数 */
  ratingCount?: number
  /** 菜品专属：所属档口名（B8；副信息展示「档口 · 食堂」） */
  stall?: string
  /** 菜品专属：属性标签原始逗号串 */
  tags?: string
  /** 菜品专属：属性标签中文映射（最多取前 2 个） */
  tagLabels?: string[]
  /** 菜品专属：促销价（元，非空时展示促销角标） */
  promoPrice?: number
  /** 菜品专属：原价（元，promoPrice 非空时划线展示） */
  originalPrice?: number
  /** 食堂坐标（GCJ-02），来自 suggest 联表，前端本地 Haversine 算「距你 Xm」 */
  lat?: number
  lng?: number
  /** 距用户距离（米）：前端基于定位本地算；未定位/坐标缺失回退校区中心，恒有值 */
  distance?: number
}
const mixedResults = ref<MixedResult[]>([])

/** 搜索结果（仅菜品单列；距离已在 doMixedSearch 经 withLocalDistance 写回，未定位回退校区中心，恒有值） */
const filteredMixed = computed(() => mixedResults.value)

/** 确认/回车搜索（AppHeader search variant 的 @search） */
function onSearchConfirm() {
  const kw = keyword.value.trim()
  if (!kw) return
  pushHistory(kw)
  doMixedSearch(kw)
}

function clearKeyword() {
  keyword.value = ''
}

function goKeyword(kw: string) {
  keyword.value = kw
  pushHistory(kw)
  doMixedSearch(kw)
}

// ===== 复合型搜索（2026-08-03 重构：直接复用菜品检索接口） =====
// C13 竞态守卫：慢请求结果不得覆盖后发的快请求（参照 review.vue searchSeq 模式）
let mixedSearchSeq = 0
async function doMixedSearch(kw?: string) {
  // 无关键词但选中了食堂时，仍按食堂浏览（searchDishesPage 支持空 keyword + canteenId）
  if (!kw && !findCanteenId.value) return
  // 竞态守卫（mixedSearchSeq）已保证后发请求覆盖先发结果；此处不设防重入锁，
  // 否则用户连续搜索新词时会被静默丢弃、界面停留在旧结果。
  const seq = ++mixedSearchSeq
  inFilter.value = true
  searchDone.value = false
  searchFailed.value = false
  try {
    // 复用 store.search（GET /dishes?keyword，返回平铺 Dish[]），金额/图片已在 api 层归一
    const list = await dishStore.search({
      keyword: kw,
      page: 1,
      pageSize: 50,
      canteenId: findCanteenId.value ?? undefined,
      ...findSortParams(findSortBy.value),
      minPrice: findPrice.value.min,
      maxPrice: findPrice.value.max,
    })
    // 竞态守卫：若期间发起了更新的搜索，丢弃本次过期结果
    if (seq !== mixedSearchSeq) return
    // 本地算距离（用户坐标 + Haversine；未定位/坐标缺失回退校区中心，保证「距你」恒有值，与首页一致）
    const decorated = dishStore.withLocalDistance(list, false)
    mixedResults.value = decorated
      .map(d => {
        // B8 副信息：档口名 + 食堂名
        const sub = [d.stallName, d.canteen].filter(Boolean).join(' · ')
        // B9 标签（Dish.tags 已是中文数组，最多取前 2 个）
        const tagLabels = (d.tags || []).slice(0, 2)
        return {
          type: 'dish' as const,
          id: d.id,
          name: d.name,
          image: d.image,
          sub,
          price: d.price,
          rating: d.rating,
          ratingCount: d.ratingCount,
          stall: d.stallName,
          tags: (d.tags || []).join(','),
          tagLabels,
          promoPrice: d.promoPrice,
          originalPrice: d.originalPrice,
          lat: d.latitude != null ? Number(d.latitude) : undefined,
          lng: d.longitude != null ? Number(d.longitude) : undefined,
          distance: d.distance,
        }
      })
      .filter(r => r.name)
    searchDone.value = true
  } catch (err) {
    // MP-012：失败不再伪装成空结果——置 searchFailed 渲染「加载失败 · 点击重试」块，
    // 与「没搜到」空态区分；恢复走重试块 @tap（onRetrySearch）或结果态下拉刷新
    console.error('[find] 搜索失败', err)
    if (seq !== mixedSearchSeq) return
    mixedResults.value = []
    searchDone.value = true
    searchFailed.value = true
  }
}

/** 重试当前检索：结果态下拉刷新与恢复均走此路径（按当前关键词/食堂重跑） */
function onRetrySearch() {
  return doMixedSearch(keyword.value.trim())
}

/** 搜索无结果引导 → 反馈页预选「推荐菜品」空表单（落点唯一构造函数，from=find，见 contribution-entry） */
function goContributeNotFound() {
  uni.navigateTo({ url: feedbackEntryUrl({ type: 'add', from: 'find' }) })
}

/** 结果态下拉刷新：FindResults 内容区滚动内置 refresher，上抛到 index 重跑当前检索 */
function onResultsRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  onRetrySearch().finally(() => { refresherTriggered.value = false })
}

/** 结果点击：菜品跳详情页（搜索仅菜品，无独立档口/食堂结果/详情页） */
function goToMixed(id: number) {
  if (id) openDishDetail(id)
}

function exitFilter() {
  inFilter.value = false
  mixedResults.value = []
  searchDone.value = false
  searchFailed.value = false
  // 退出结果态：重置筛选条件，下次进入结果态从默认开始
  findCanteenId.value = null
  findSortBy.value = 'latest'
  findPrice.value = {}
  // 修复：退出结果态时递增序号使在途旧请求失效，避免其返回后写回 mixedResults 造成数据残留
  mixedSearchSeq += 1
}

async function loadDiscover() {
  try {
    await Promise.all([
      dishStore.fetchHotSearch(),
      dishStore.fetchCanteens(),
    ])
  } catch (e) {
    // 静默：发现态加载失败不呈现任何占位，异常仅记录
    console.error('[find] 发现页加载失败', e)
  }
}

onMounted(() => {
  loadHistory()
  ensureLocation()
  loadDiscover()
})

/** 确保拿到用户坐标（会话级缓存，避免重复授权）；失败静默降级（距你显 -） */
async function ensureLocation() {
  if (locationStore.location) return
  try {
    const loc = await getUserLocation()
    if (loc) locationStore.setLocation(loc)
  } catch (e) {
    // 用户拒绝授权 / 定位不可用：静默，距离降级
  }
}
onShareAppMessage(() => buildSharePayload())
// 从菜品详情返回搜索页：清掉分享残留，避免右上角分享菜单沿用详情页内容
onShow(() => clearShareState())
</script>

<style scoped>
.find-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); overflow: hidden; }
/* 内容区：占满 header/筛选行之外的剩余高度；滚动职责随分支（发现态静态/结果态 FindResults） */
.find-body { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
/* 发现态：普通内容容器 + 无下拉刷新的高度兜底（搜索记录上限 4 条内容短；异常超高时可内部滚动兜底，不提供下拉刷新） */
.discover-body { flex: 1; min-height: 0; overflow-y: auto; padding-bottom: var(--spacing-lg); }
/* 结果态宿主：让 FindResults 内容区（filter-result/results-scroll flex 链）填满剩余高度 */
.results-host { flex: 1; min-height: 0; }

/* 搜索无结果引导：居中静态卡片（白底 + 大圆角 + 柔和投影），与列表卡同表面语言 */
.find-empty {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-xl) var(--spacing-lg);
  box-sizing: border-box;
}
/* 搜索失败重试块（MP-012）：与空态同族视觉（居中、凹陷面 bg-soft、次级文字色），
   整块 @tap 触发重拉，无独立按钮——极简行内块，不引入新组件文件 */
.find-retry {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  margin: var(--spacing-lg);
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.find-retry.pressed { opacity: 0.7; }
.fr-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-secondary); text-align: center; }
.fr-hint { font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; }
.fe-icon {
  width: 112rpx;
  height: 112rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  margin-bottom: var(--spacing-xs);
}
.fe-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); text-align: center; }
.fe-desc { font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; line-height: 1.5; }
.fe-btn {
  margin-top: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-xl);
  background: var(--color-primary);
  border-radius: var(--radius-pill);
  -webkit-tap-highlight-color: transparent;
}
.fe-btn.pressed { opacity: 0.85; }
.fe-btn-text { font-size: var(--font-small); color: var(--text-white); font-weight: var(--weight-medium); }

/* 食堂筛选行（header 下方独立一行，与首页共用 FilterBar） */
.find-filter-row {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  /* 下缘留白收窄（find-result-card-polish 5.1：避免与首卡叠加成大 gap） */
  padding: var(--spacing-sm) var(--spacing-lg) var(--spacing-xs);
  /* 表面统一：与首页筛选条一致，使用页面凹陷面且无分隔线，与下方结果列表视觉一体 */
  background: var(--bg-page);
}
/* 同首页：让 <filter-bar> 宿主撑满筛选行，组件内 .fb-row 才有剩余空间把 icon 顶到最右 */
.fb-host {
  flex: 1;
  min-width: 0;
}

/* 区块通用 */
.section-extra { flex-shrink: 0; }

/* 历史搜索 */
/* QA-03 修复：视觉保持轻量小文字链，命中区经 ::after 透明覆盖扩至 ≥88rpx（Apple 44pt 触达下限） */
.history-clear { position: relative; font-size: var(--font-aux); color: var(--text-tertiary); font-weight: var(--weight-medium); padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-tag); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.history-clear::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 88rpx;
  height: 88rpx;
  transform: translate(-50%, -50%);
}
.history-clear:active { opacity: 0.55; }
.history-chips { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.history-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  max-width: 360rpx;
  /* 放大命中区：上下 sm(32rpx)、左右 lg(48rpx)，字号升至 body；可点词条胶囊与页面内小标签语义区分（content-flow-visual-polish 评审回退） */
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--bg-soft);
  border-radius: var(--radius-pill);
  transition: background var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.history-chip-text { font-size: var(--font-body); color: var(--text-secondary); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-chip-del {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  flex-shrink: 0;
  line-height: 1;
  /* 扩大命中区：padding 撑大可点目标，避免仅图标 12px 难点 */
  padding: var(--spacing-xs);
  margin: calc(-1 * var(--spacing-xs));
  border-radius: var(--radius-circle);
  transition: opacity var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.history-chip-del:active { opacity: 0.5; }
/* 高频搜索 vs 搜索记录层级区分：推荐词主色软底，个人记录保持中性灰 */
.history-chip-hot { background: var(--color-primary-soft); }
.history-chip-hot .history-chip-text { color: var(--color-primary); }
</style>
