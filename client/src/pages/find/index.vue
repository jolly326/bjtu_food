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
      <FilterBar
        class="fb-host"
        :canteens="dishStore.canteenList"
        :selected-canteen-id="findCanteenId"
        :price-range="findPrice"
        :capsule-height="36"
        @canteen-select="onFindCanteenSelect"
        @price-select="onFindPriceSelect"
      />
    </view>

    <scroll-view
      class="scroll-wrap"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
    >
      <!-- ============ 发现主页（2026-08-03 重构：去掉分类宫格 → 热搜火排名卡 → 历史搜索折叠弱化） ============ -->
      <view v-if="!inFilter" class="discover-home">
        <LoadingHint v-if="discoverLoading" />

        <!-- 发现主页加载失败：明确错误 + 重试（避免首屏空白） -->
        <view v-else-if="discoverFailed" class="discover-empty">
          <IconSvg name="empty" :size="96" color="var(--text-tertiary)" />
          <text class="discover-empty-tip">加载失败</text>
          <text class="discover-empty-sub">网络异常，下拉或点击重试</text>
          <view class="discover-retry" @tap="loadDiscover">重新加载</view>
        </view>

        <template v-else>
        <!-- 搜索记录（2026-08-03：首位） -->
        <CardSection v-if="historyList.length > 0">
          <SectionTitle title="搜索记录" :bar="false">
            <text slot="extra" class="section-extra history-clear" @tap="clearHistory">清空</text>
          </SectionTitle>
          <view class="history-chips">
            <view
              v-for="(kw, i) in historyExpanded ? historyList : historyList.slice(0, 3)"
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
          <view class="history-toggle" v-if="historyList.length > 3" @tap="historyExpanded = !historyExpanded">
            <text class="history-toggle-text">{{ historyExpanded ? '收起' : `展开全部 ${historyList.length} 条` }}</text>
          </view>
        </CardSection>

        <!-- 热搜词（GET /dishes/hot-search，由后端派生；点击直接搜索） -->
        <CardSection v-if="hotSearchList.length > 0">
          <SectionTitle title="猜你想搜" :bar="false" />
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

      <!-- ============ 搜索混合结果页（2026-08-03：无标题直接列表；tab 在顶部固定区） ============ -->
      <view v-else class="filter-result">
        <LoadingHint v-if="mixedLoading" />

        <!-- A7 逐行 stagger 入场（reduced-motion 兜底见 style） -->
        <view class="mixed-list" v-else-if="filteredMixed.length > 0">
          <DishResultRow
            v-for="item in filteredMixed"
            :key="`${item.type}-${item.id}`"
            :dish="item"
            :keyword="keyword"
            @select="goToMixed"
          />
        </view>
        <EmptyState
          v-else-if="!mixedLoading"
          :text="`没有找到与“${keyword}”相关的结果`"
          :retry="true"
          @retry="doMixedSearch"
        />
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 认证弹层（未登录点赞/写评价等 requireAuth 统一在此弹出） -->
    <AuthSheet />
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
import IconSvg from '@/components/IconSvg.vue'
import EmptyState from '@/components/EmptyState.vue'
import LoadingHint from '@/components/LoadingHint.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import CardSection from '@/components/CardSection.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import FilterBar from '@/components/FilterBar.vue'
import AppHeader from '@/components/AppHeader.vue'
import DishResultRow from './DishResultRow.vue'

const dishStore = useDishStore()
const locationStore = useLocationStore()

/** 搜索页为非 tab 二级页：返回回首页（2026-08-03 修复：用 navigateBack 带返回动画；无上一页时兜底 reLaunch） */
function goBackHome() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.reLaunch({ url: '/pages/home/index' })
  }
}

/** 菜品详情：跳转独立页（pages/detail/dish） */
function openDishDetail(id: number) {
  if (!id) return
  uni.navigateTo({ url: `/pages/dish/index?id=${id}` })
}
const keyword = ref('')
const refresherTriggered = ref(false)
const discoverLoading = ref(true)

// ===== 搜索历史（本地缓存，预留接口位） =====
const HISTORY_KEY = 'find_search_history'
const HISTORY_MAX = 12
const historyList = ref<string[]>([])
/** 历史搜索展开/收起（2026-08-03：默认折叠仅显示 3 条，弱化次级入口） */
const historyExpanded = ref(false)

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
    confirmColor: '#FF3B30',
    success: (res) => {
      if (!res.confirm) return
      historyList.value = []
      saveHistory()
    },
  })
}

// 搜索模式（2026-08-03：结果页改为复合型混合列表，无排序/筛选）
const inFilter = ref(false)

// 食堂筛选（community-review-redesign：find 页独立状态，与首页 selectedCanteenId 隔离）
const findCanteenId = ref<number | null>(null)
function onFindCanteenSelect(id: number | null) {
  findCanteenId.value = id && id > 0 ? id : null
  // 切换食堂即按当前关键词（可空）+ 食堂重新检索
  doMixedSearch(keyword.value.trim())
}

// ===== 结果态筛选（仅 inFilter 渲染，与首页共用 FilterBar：食堂 / 价格，仅展开时红底） =====
// 排序按钮早已从筛选行移除（筛选行只保留食堂+价格两个按钮），排序弹层随之无触发入口；
// findSortBy 仍作为检索入参来源保留（doMixedSearch → findSortParams），待后续排序入口再启用。
const findSortBy = ref<HomeSortKey>('latest')
/** 当前价格区间（分）；文案回显由 FilterBar 内部用 fenToYuan 换算（红线：禁止裸算 /100） */
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
const mixedLoading = ref(false)

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
  mixedLoading.value = true
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
  } catch {
    mixedResults.value = []
  } finally {
    // 竞态修复：仅在 seq 匹配（本次请求仍是最新）时才关闭 loading，
    // 避免旧慢请求返回时把新请求的 loading 提前关闭导致加载中文本闪烁
    if (seq === mixedSearchSeq) mixedLoading.value = false
  }
}

/** 结果点击：菜品跳详情页（搜索仅菜品，无独立档口/食堂结果/详情页） */
function goToMixed(id: number) {
  try { uni.vibrateShort({ type: 'light' }) } catch { /* 部分平台无震动 API，忽略 */ }
  if (id) openDishDetail(id)
}

function exitFilter() {
  inFilter.value = false
  mixedResults.value = []
  // 退出结果态：重置筛选条件，下次进入结果态从默认开始
  findCanteenId.value = null
  findSortBy.value = 'latest'
  findPrice.value = {}
  // 修复：退出结果态时递增序号使在途旧请求失效，避免其返回后写回 mixedResults 造成数据残留
  mixedSearchSeq += 1
}

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  const task = inFilter.value
    ? doMixedSearch(keyword.value.trim())
    : loadDiscover()
  Promise.resolve(task).finally(() => { refresherTriggered.value = false })
}

const discoverFailed = ref(false)

async function loadDiscover() {
  discoverLoading.value = true
  discoverFailed.value = false
  try {
    await Promise.all([
      dishStore.fetchHotSearch(),
      dishStore.fetchCanteens(),
    ])
  } catch (e) {
    console.error('[find] 发现页加载失败', e)
    discoverFailed.value = true
  } finally {
    discoverLoading.value = false
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
.find-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
/* 顶部留白由内容块自己提供（搜索 mixed-list / 发现页首屏占位均为 md，与首页广播条-卡间距一致）；scroll 不再额外叠加 */
.scroll-wrap { flex: 1; overflow-y: auto; padding-top: 0; padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }

/* 食堂筛选行（community-review-redesign：header 下方独立一行，与首页共用 FilterBar） */
.find-filter-row {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-lg);
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
.history-clear { font-size: var(--font-aux); color: var(--text-tertiary); font-weight: var(--weight-medium); padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-tag); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.history-clear:active { opacity: 0.55; }
.history-chips { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.history-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  max-width: 360rpx;
  /* 放大命中区：上下 sm(32rpx)、左右 lg(48rpx)，字号升至 body，不再是细小胶囊 */
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
/* 历史折叠按钮（2026-08-03：展开/收起） */
.history-toggle { display: flex; align-items: center; justify-content: center; padding: var(--spacing-sm) 0 0; }
.history-toggle-text { font-size: var(--font-aux); color: var(--text-tertiary); font-weight: var(--weight-medium); padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-tag); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.history-toggle-text:active { opacity: 0.55; }

/* 搜索混合结果页（2026-08-03：无排序/筛选）
   注：结果页进场过渡（原 .filter-enter / filter-enter）与逐行入场（原 mixed-item-in）
   已于 client-mvp-strip-entrance-anim 剥离，内容静态直接呈现。 */
/* 搜索结果列表（仅菜品，一行一个，左图右信息）。
   Apple Design 列表行卡：20px 大圆角 + hairline 分隔 + 按下背景高亮（Apple 偏好 highlight 而非 scale） */
.mixed-list { margin: var(--spacing-md); }



/* 发现主页加载失败空态 */
.discover-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl) var(--spacing-lg);
  gap: var(--spacing-sm);
}
.discover-empty-tip { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-secondary); }
.discover-empty-sub { font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; line-height: 1.5; }
.discover-retry {
  margin-top: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--color-primary);
  border-radius: var(--radius-btn);
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--color-on-primary);
  transition: opacity var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.discover-retry:active { opacity: 0.8; }
/* 高频搜索 vs 搜索记录层级区分：推荐词主色软底，个人记录保持中性灰 */
.history-chip-hot { background: var(--color-primary-soft); }
.history-chip-hot .history-chip-text { color: var(--color-primary); }

@media (prefers-reduced-motion: reduce) {
  .discover-retry { transition: none; }
}
</style>
