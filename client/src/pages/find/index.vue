<template>
  <view class="page find-page" :class="{ 'theme-dark': theme.isDark }">
    <!-- 顶部固定区（2026-08-03：返回键 + 搜索框 + 结果 tab，均不随滚动；避让状态栏+胶囊） -->
    <view class="search-nav" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))' }">
      <view class="search-nav-row" :style="{ height: navBarHeight + 'px' }">
        <view class="search-back" @tap="inFilter ? exitFilter() : goBackHome()" :class="{ pressed: pressedKey === 'back' }" @touchstart="pressedKey = 'back'" @touchend="pressedKey = ''" @touchcancel="pressedKey = ''">
          <IconSvg name="arrow-left" :size="40" color="#FFFFFF" class="search-back-icon" />
        </view>
        <view class="search-box" :style="{ marginRight: capsuleRightOffset + 'px' }">
          <IconSvg name="search" :size="30" color="var(--text-tertiary)" class="search-box-icon" />
          <input
            class="search-box-input"
            v-model="keyword"
            type="text"
            confirm-type="search"
            placeholder="搜索菜品、档口或食堂"
            placeholder-class="search-box-ph"
            :adjust-position="true"
            @input="onKeywordInput"
            @confirm="onSearchConfirm"
            @blur="onSearchBlur"
          />
          <view class="search-box-clear" v-if="keyword" @tap="clearKeyword">
            <IconSvg name="close" :size="28" color="var(--text-tertiary)" />
          </view>
        </view>
      </view>

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
            <IconSvg v-else name="dish" :size="32" color="var(--text-tertiary)" />
          </view>
          <view class="suggest-text">
            <text class="suggest-name">{{ s.name }}</text>
            <text v-if="s.canteen" class="suggest-sub">{{ s.canteen }}</text>
          </view>
          <view class="suggest-meta" v-if="s.price != null || s.rating != null">
            <text v-if="s.price != null" class="suggest-price">¥{{ s.price.toFixed(2) }}</text>
            <view v-if="s.rating != null" class="suggest-rating">
              <IconSvg name="star" :size="20" color="var(--color-star)" />
              <text class="suggest-rating-num">{{ Number(s.rating).toFixed(1) }}</text>
            </view>
          </view>
        </view>
      </view>
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
        <!-- 首屏骨架：热搜列表加载占位 -->
        <view v-if="discoverLoading" class="discover-skeleton">
          <view class="sk-row"><view v-for="s in 5" :key="s" class="sk-line skeleton" /></view>
        </view>

        <!-- 发现主页加载失败：明确错误 + 重试（避免首屏空白） -->
        <view v-else-if="discoverFailed" class="discover-empty">
          <IconSvg name="empty" :size="96" color="var(--text-tertiary)" />
          <text class="discover-empty-tip">加载失败</text>
          <text class="discover-empty-sub">网络异常或后端未启动，下拉或点击重试</text>
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
              class="history-chip"
              :class="{ pressed: pressedKey === `hot-${kw.keyword}` }"
              @touchstart="pressedKey = `hot-${kw.keyword}`"
              @touchend="pressedKey = ''"
              @touchcancel="pressedKey = ''"
              @mousedown="pressedKey = `hot-${kw.keyword}`"
              @mouseup="pressedKey = ''"
              @mouseleave="pressedKey = ''"
              @tap="goKeyword(kw.keyword)"
            >
              <text class="history-chip-text">{{ kw.keyword }}</text>
            </view>
          </view>
        </CardSection>

        </template>
      </view>

      <!-- ============ 搜索混合结果页（2026-08-03：无标题直接列表；tab 在顶部固定区） ============ -->
      <view v-else class="filter-result filter-enter">
        <view class="mixed-list" v-if="filteredMixed.length > 0">
          <view
            v-for="(item, idx) in filteredMixed"
            :key="`${item.type}-${item.id}`"
            class="mixed-item"
            :class="{ pressed: pressedKey === `m-${idx}` }"
            @touchstart="pressedKey = `m-${idx}`"
            @touchend="pressedKey = ''"
            @touchcancel="pressedKey = ''"
            @mousedown="pressedKey = `m-${idx}`"
            @mouseup="pressedKey = ''"
            @mouseleave="pressedKey = ''"
            @tap="goToMixed(item)"
          >
            <view class="mixed-thumb">
              <image v-if="item.image" :src="item.image" mode="aspectFill" class="mixed-thumb-img" />
              <view v-else class="mixed-thumb-ph">
                <IconSvg name="dish" :size="48" color="var(--text-tertiary)" />
              </view>
            </view>
            <view class="mixed-info">
              <view class="mixed-title-row">
                <text class="mixed-name">{{ item.name }}</text>
              </view>
              <text v-if="item.sub" class="mixed-sub">{{ item.sub }}</text>
              <!-- 菜品：展示价格 + 评分 + 评价数，信息更充实 -->
              <view v-if="item.price != null || item.rating != null" class="mixed-meta">
                <text class="mixed-price" v-if="item.price != null">¥{{ item.price.toFixed(2) }}</text>
                <view v-if="item.rating != null" class="mixed-rating">
                  <IconSvg name="star" :size="24" color="var(--color-star)" />
                  <text class="mixed-rating-num">{{ Number(item.rating).toFixed(1) }}</text>
                  <text v-if="item.ratingCount != null" class="mixed-rating-count">({{ item.ratingCount }})</text>
                </view>
              </view>
              <!-- 档口/食堂：展示副信息（食堂名/位置） -->
              <text class="mixed-sub" v-else-if="item.sub">{{ item.sub }}</text>
              <text v-if="item.distance != null" class="mixed-distance">距你 {{ fmtMixedDistance(item.distance) }}</text>
            </view>
            <IconSvg name="arrow" :size="24" color="var(--text-tertiary)" class="mixed-arrow" />
          </view>
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
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { onShareAppMessage } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { useDishStore } from '@/stores/dish'
import { buildSharePayload } from '@/utils/shareState'
import type { Suggestion } from '@/types/dish'
import { useLocationStore } from '@/stores/location'
import { haversineMeters, getUserLocation } from '@/utils/location'
import IconSvg from '@/components/IconSvg.vue'
import EmptyState from '@/components/EmptyState.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import CardSection from '@/components/CardSection.vue'
import AuthSheet from '@/components/AuthSheet.vue'

const theme = useThemeStore()
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

/** 顶部避让（2026-08-03 修复：状态栏高度 + 右上角胶囊按钮）。
 * 搜索框右侧 margin-right = 胶囊按钮左侧到屏幕右缘的距离，避免搜索框被微信胶囊遮挡。 */
const statusBarHeight = ref(20)
const capsuleRightOffset = ref(0)
const navBarHeight = ref(44)
function measureTopBar() {
  // @ts-ignore
  const win = (typeof wx !== 'undefined' && wx.getWindowInfo) ? wx.getWindowInfo() : null
  statusBarHeight.value = (win && win.statusBarHeight) || 20
  // @ts-ignore - 微信特有：胶囊按钮位置
  const menu = (typeof wx !== 'undefined' && wx.getMenuButtonBoundingClientRect) ? wx.getMenuButtonBoundingClientRect() : null
  if (menu && win) {
    // 搜索框右侧须在胶囊左侧之前结束：margin-right = 屏幕宽 - 胶囊.left + 余量
    capsuleRightOffset.value = win.windowWidth - menu.left + 8
    // 返回行高度对齐系统导航栏（与全站 header 同高）
    if (menu.height) navBarHeight.value = (menu.top - statusBarHeight.value) * 2 + menu.height
  } else {
    capsuleRightOffset.value = 0
  }
}

/** 菜品详情：跳转独立页（pages-detail/dish） */
function openDishDetail(id: number) {
  if (!id) return
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${id}` })
}
const keyword = ref('')
const suggestions = ref<Suggestion[]>([])
const showSuggest = ref(false)
const pressedKey = ref('')
const refresherTriggered = ref(false)
const discoverLoading = ref(true)

// 搜索联想面板的运行时测量 top（px）。Header 高度含状态栏(px)，无法用固定 rpx 对齐，
// 必须在布局完成后用 selectorQuery 实测 Header 底 + searchWrap 高度（refs BLOCKER N1）
const suggestPanelTop = ref(200)

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
  historyList.value = []
  saveHistory()
}

// 搜索模式（2026-08-03：结果页改为复合型混合列表，无排序/筛选）
const inFilter = ref(false)

/** 混合搜索结果：菜品 / 档口 / 食堂 复合流（复用 suggest 接口返回 Suggestion[]） */
interface MixedResult {
  type: Suggestion['type']
  id?: number
  name: string
  image?: string
  /** 副信息：档口→食堂名；食堂→位置；菜品→空（由联想数据结构派生） */
  sub?: string
  /** 菜品专属：价格（分） */
  price?: number
  /** 菜品专属：平均评分 */
  rating?: number
  /** 菜品专属：评价数 */
  ratingCount?: number
  /** 食堂坐标（GCJ-02），来自 suggest 联表，前端本地 Haversine 算「距你 Xm」 */
  lat?: number
  lng?: number
  /** 距用户距离（米）：前端基于定位本地算，未定位/无坐标为 undefined */
  distance?: number
}
const mixedResults = ref<MixedResult[]>([])
const mixedLoading = ref(false)

/** 混合结果本地算距离（米）：基于 locationStore 用户坐标 + Haversine；用户位置不出本机 */
const mixedWithDistance = computed<MixedResult[]>(() => {
  const loc = locationStore.location
  return mixedResults.value.map((r) => {
    if (loc && typeof r.lat === 'number' && typeof r.lng === 'number') {
      return { ...r, distance: haversineMeters(loc, { lat: r.lat, lng: r.lng }) }
    }
    return r
  })
})

/** 搜索结果（仅菜品单列；本地算距离就近排序） */
const filteredMixed = computed(() => mixedWithDistance.value)

/** 距你文案：米/公里自适应 */
function fmtMixedDistance(m: number): string {
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${Math.round(m)}m`
}
/** 输入框失焦：收起联想面板 */
function onSearchBlur() {
  setTimeout(() => { showSuggest.value = false }, 150)
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
  if (!kw) return
  pushHistory(kw)
  doMixedSearch(kw)
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
  // 联想只返回菜品（规范：搜索只针对菜品），直接进详情；兜底仍走关键词搜索
  if (s.type === 'dish' && s.id) {
    openDishDetail(s.id)
  } else if (s.name) {
    doMixedSearch(s.name)
  }
}

function goKeyword(kw: string) {
  keyword.value = kw
  pushHistory(kw)
  doMixedSearch(kw)
}

// ===== 复合型混合搜索（2026-08-03：复用 suggest 接口，美团式混合结果） =====
async function doMixedSearch(kw?: string) {
  if (!kw) return
  inFilter.value = true
  mixedLoading.value = true
  try {
    const list = await dishStore.fetchSuggestions(kw)
    mixedResults.value = list
      .map(s => ({
        type: 'dish' as const,
        id: s.id,
        name: s.name,
        image: s.image,
        // 副信息 = 所属食堂名（后端 suggest 已联表返回 canteen），结果卡展示「食堂」属性
        sub: s.canteen || '',
        price: s.price,
        rating: s.rating,
        ratingCount: s.ratingCount,
        lat: s.latitude != null ? Number(s.latitude) : undefined,
        lng: s.longitude != null ? Number(s.longitude) : undefined,
      }))
      .filter(r => r.name)
  } catch {
    mixedResults.value = []
  } finally {
    mixedLoading.value = false
  }
}

/** 结果点击：菜品跳详情页（搜索仅菜品，无独立档口/食堂结果/详情页） */
function goToMixed(item: MixedResult) {
  if (item.id) openDishDetail(item.id)
}

function exitFilter() {
  inFilter.value = false
  mixedResults.value = []
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

/** 实测顶部固定区（.search-nav）高度，得到联想面板的 top(px)。
 * 顶部 = 状态栏 + 返回行 + 结果 tab（结果态有 tab 更高）。失败时回退。 */
function measureSuggestTop() {
  try {
    uni.createSelectorQuery()
      .select('.search-nav')
      .boundingClientRect()
      .exec((res) => {
        const navRect = res[0] as UniApp.NodeInfo
        const navBottom = navRect?.bottom ?? 0
        if (navBottom > 0) {
          suggestPanelTop.value = navBottom + 4
        } else {
          suggestPanelTop.value = 200
        }
      })
  } catch {
    suggestPanelTop.value = 200
  }
}

onMounted(() => {
  measureTopBar()
  loadHistory()
  ensureLocation()
  loadDiscover()
  // 布局就绪后再测量，避免拿到 0 高度（onReady/nextTick 双保险）
  nextTick(() => measureSuggestTop())
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

// 进入结果态（出现 tab）后重新测量联想面板 top（顶部固定区高度变化）
watch(inFilter, () => {
  nextTick(() => measureSuggestTop())
})

watch(keyword, (value) => {
  if (!value.trim()) showSuggest.value = false
})
</script>

<style scoped>
.find-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-top: var(--spacing-md); padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }

/* ===== 顶部固定区（2026-08-03：返回 + 搜索框 + 结果 tab，位于滚动区外，天然不随滚动） ===== */
.search-nav {
  position: relative;
  z-index: 30;
  /* 朱砂红品牌色块（与首页 header 一致）；白底搜索框浮于其上 */
  background: var(--color-primary);
  padding-left: var(--spacing-md);
  padding-right: var(--spacing-md);
  padding-bottom: 0;
  box-sizing: border-box;
}
.search-nav-row { display: flex; align-items: center; gap: var(--spacing-sm); height: 72rpx; }
.search-back {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
  transition: transform 0.12s var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.search-back.pressed { transform: scale(var(--press-scale)); }
.search-back-icon { flex-shrink: 0; line-height: 1; }
/* 搜索框：圆角灰条 + 放大镜 + 清空（与首页同款视觉） */
.search-box {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: 72rpx;
  padding: 0 var(--spacing-md);
  background: var(--bg-card);
  box-shadow: var(--shadow-card);
  border-radius: var(--radius-pill);
  box-sizing: border-box;
}
.search-box-icon { flex-shrink: 0; line-height: 1; }
.search-box-input { flex: 1; min-width: 0; font-size: var(--font-body); color: var(--text-primary); }
.search-box-ph { color: var(--text-tertiary); }
.search-box-clear { flex-shrink: 0; display: flex; align-items: center; padding: var(--spacing-sm); border-radius: var(--radius-tag); transition: opacity 120ms ease; -webkit-tap-highlight-color: transparent; }
.search-box-clear:active { opacity: 0.55; }

/* 联想下拉：fixed 定位（top 由运行时实测 .search-nav 底边写入 :style）。
   规范：不透明实底（--bg-page）直接覆盖发现主页，不启用毛玻璃（小程序端 backdrop-filter 支持不稳且费性能） */
.suggest-panel {
  position: fixed;
  left: var(--spacing-md);
  right: var(--spacing-md);
  background: var(--bg-page);
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
  /* 联想行卡白底浮于不透明面板之上（规范） */
  background: var(--bg-card);
  /* hairline 分隔（Apple 精致细节） */
  border-bottom: 1rpx solid var(--border-color);
  transition: transform 0.12s ease, background 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.suggest-item:last-child { border-bottom: none; }
.suggest-item.pressed { transform: scale(var(--press-scale)); background: var(--bg-soft); }
.suggest-icon { width: 56rpx; height: 56rpx; border-radius: 16rpx; overflow: hidden; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: var(--bg-page); }
.suggest-thumb { width: 100%; height: 100%; }
.suggest-text { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.suggest-name { font-size: var(--font-body); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.suggest-sub { font-size: var(--font-aux); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.suggest-meta { flex-shrink: 0; display: flex; align-items: baseline; gap: var(--spacing-xs); }
.suggest-price { font-size: var(--font-body); font-weight: var(--weight-bold); color: var(--color-primary); font-variant-numeric: tabular-nums; }
.suggest-rating { display: inline-flex; align-items: center; gap: var(--spacing-2xs); }
.suggest-rating-num { font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--color-star); font-variant-numeric: tabular-nums; }

/* 区块通用 */
.section-extra { flex-shrink: 0; }

/* 历史搜索 */
.history-clear { font-size: var(--font-aux); color: var(--text-tertiary); font-weight: var(--weight-medium); padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-tag); transition: opacity 120ms ease; -webkit-tap-highlight-color: transparent; }
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
  transition: transform 0.12s ease, background 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}
.history-chip.pressed { transform: scale(var(--press-scale)); background: var(--color-primary-soft); }
.history-chip-text { font-size: var(--font-body); color: var(--text-secondary); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-chip-del {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  flex-shrink: 0;
  line-height: 1;
  /* 扩大命中区：padding 撑大可点目标，避免仅图标 12px 难点 */
  padding: var(--spacing-xs);
  margin: calc(-1 * var(--spacing-xs));
  border-radius: 50%;
  transition: opacity 120ms ease;
  -webkit-tap-highlight-color: transparent;
}
.history-chip-del:active { opacity: 0.5; }
/* 历史折叠按钮（2026-08-03：展开/收起） */
.history-toggle { display: flex; align-items: center; justify-content: center; padding: var(--spacing-sm) 0 0; }
.history-toggle-text { font-size: var(--font-aux); color: var(--text-tertiary); font-weight: var(--weight-medium); padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-tag); transition: opacity 120ms ease; -webkit-tap-highlight-color: transparent; }
.history-toggle-text:active { opacity: 0.55; }

/* 搜索混合结果页（2026-08-03：无排序/筛选） */
/* 结果页进场过渡 */
.filter-enter { animation: filter-enter 0.24s var(--ease-out) both; }
@keyframes filter-enter {
  from { opacity: 0; transform: translateY(16rpx); }
  to { opacity: 1; transform: translateY(0); }
}
/* 搜索结果列表（仅菜品，一行一个，左图右信息）。
   Apple Design 列表行卡：20px 大圆角 + hairline 分隔 + 按下背景高亮（Apple 偏好 highlight 而非 scale） */
.mixed-list { margin: var(--spacing-sm) var(--spacing-md); }
.mixed-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  padding: var(--spacing-md);
  box-shadow: var(--shadow-card);
  transition: background-color 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}
.mixed-item + .mixed-item { margin-top: var(--spacing-sm); }
.mixed-item.pressed { background-color: var(--bg-soft); }
.mixed-thumb {
  width: 160rpx;
  height: 160rpx;
  flex-shrink: 0;
  border-radius: 20rpx;
  overflow: hidden;
  background: var(--bg-page);
}
.mixed-thumb-img { width: 100%; height: 100%; }
.mixed-thumb-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.mixed-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.mixed-title-row { display: flex; align-items: center; gap: var(--spacing-xs); }
.mixed-name {
  flex: 1;
  min-width: 0;
  font-size: var(--font-card);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
  line-height: 1.3;
  letter-spacing: var(--tracking-h3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 菜品结果 meta：价格 + 评分 + 评价数 一行 */
.mixed-meta { display: flex; align-items: center; gap: var(--spacing-sm); margin-top: var(--spacing-xs); }
.mixed-price { font-size: var(--font-card); font-weight: var(--weight-bold); color: var(--color-primary); font-variant-numeric: tabular-nums; }
.mixed-rating { display: inline-flex; align-items: center; gap: var(--spacing-2xs); }
.mixed-rating-num { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--color-star); font-variant-numeric: tabular-nums; }
.mixed-rating-count { font-size: var(--font-aux); color: var(--text-tertiary); }
.mixed-sub { font-size: var(--font-aux); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mixed-distance {
  align-self: flex-start;
  margin-top: 2rpx;
  font-size: var(--font-aux);
  font-weight: var(--weight-semibold);
  color: var(--color-primary);
  font-variant-numeric: tabular-nums;
}
.mixed-arrow { flex-shrink: 0; }

/* 发现主页首屏骨架（2026-08-03：分类宫格已删，仅保留热搜列表占位） */
.discover-skeleton { padding: 0 var(--spacing-md); }
.sk-row { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.sk-line { height: 110rpx; border-radius: var(--radius-card); flex: 1; }

/* 发现主页加载失败空态 */
.discover-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl) var(--spacing-lg);
  gap: var(--spacing-sm);
}
.discover-empty-tip { font-size: var(--font-card); font-weight: var(--weight-semibold); color: var(--text-secondary); }
.discover-empty-sub { font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; line-height: 1.5; }
.discover-retry {
  margin-top: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--color-primary);
  border-radius: var(--radius-btn);
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--color-on-primary);
  transition: opacity 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.discover-retry:active { opacity: 0.8; }
/* 高频搜索 vs 搜索记录层级区分：推荐词主色软底，个人记录保持中性灰 */
.history-chip-hot { background: var(--color-primary-soft); }
.history-chip-hot .history-chip-text { color: var(--color-primary); }

@media (prefers-reduced-motion: reduce) {
  .filter-enter { animation: none; }
  .discover-retry { transition: none; }
}
</style>
