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
        <!-- 首屏骨架：热搜列表加载占位 -->
        <view v-if="discoverLoading" class="discover-skeleton">
          <view class="sk-row"><view v-for="s in 5" :key="s" class="sk-line skeleton" /></view>
        </view>

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
        <!-- A3 加载骨架屏：搜索请求中显示占位，避免「点了搜索没反应」的错觉 -->
        <view class="mixed-list" v-if="mixedLoading">
          <view v-for="s in 4" :key="`sk-${s}`" class="mixed-item mixed-item-skeleton">
            <view class="mixed-thumb skeleton" />
            <view class="mixed-info">
              <view class="sk-line skeleton sk-name" />
              <view class="sk-line skeleton sk-sub" />
              <view class="sk-line skeleton sk-meta" />
            </view>
          </view>
        </view>

        <!-- A7 逐行 stagger 入场（reduced-motion 兜底见 style） -->
        <view class="mixed-list" v-else-if="filteredMixed.length > 0">
          <view
            v-for="(item, idx) in filteredMixed"
            :key="`${item.type}-${item.id}`"
            class="mixed-item"
            @tap="goToMixed(item)"
          >
            <!-- C12 图片淡入：缩略图加载完成 opacity 过渡 -->
            <view class="mixed-thumb">
              <image v-if="item.image" :src="getImageUrl(getThumbUrl(item.image))" mode="aspectFill" class="mixed-thumb-img" :class="{ loaded: item.loaded }" lazy-load @load="item.loaded = true" />
              <view v-else class="mixed-thumb-ph">
                <IconSvg name="dish" :size="48" color="var(--text-tertiary)" />
              </view>
            </view>
            <view class="mixed-info">
              <!-- 第一行：菜名 + 评分（贴名小号）+ 价格 两端对齐（名称/价格为搜索核心信息） -->
              <view class="mixed-title-row">
                <view class="mixed-name-group">
                  <text class="mixed-name">
                    <text
                      v-for="(seg, si) in splitHighlight(item.name)"
                      :key="si"
                      :class="{ hl: seg.hit }"
                    >{{ seg.text }}</text>
                  </text>
                  <!-- 评分：贴近菜名右侧、小一号/两号（星 + 分数，不含评论数；星星放大与菜名字号匹配） -->
                  <view v-if="item.rating != null" class="mixed-rating-group">
                    <IconSvg name="star-filled" :size="26" color="var(--color-star)" class="mixed-rating-star" />
                    <text class="mixed-rating-num">{{ Number(item.rating).toFixed(1) }}</text>
                  </view>
                </view>
                <!-- 价格组（促销角标 + 促销价/单价 + 原价划线），菜品才有价格 -->
                <view v-if="item.price != null" class="mixed-price-group">
                  <view v-if="item.promoPrice != null" class="mixed-promo-badge">促销</view>
                  <text class="mixed-price" v-if="item.promoPrice != null"><text class="mixed-price-sym">¥</text>{{ item.promoPrice.toFixed(2) }}</text>
                  <text class="mixed-price" v-else><text class="mixed-price-sym">¥</text>{{ item.price.toFixed(2) }}</text>
                  <text v-if="item.promoPrice != null && item.originalPrice != null" class="mixed-original">¥{{ item.originalPrice.toFixed(2) }}</text>
                </view>
              </view>
              <!-- 第二行：标徽（属性标签 chips，主色软底） -->
              <view v-if="item.tagLabels && item.tagLabels.length" class="mixed-tags">
                <text v-for="t in item.tagLabels" :key="t" class="mixed-tag">{{ t }}</text>
              </view>
              <!-- 第三行：位置（档口·食堂 + 距你，两端对齐） -->
              <view class="mixed-sub">
                <text class="mixed-sub-text">
                  <text
                    v-for="(seg, si) in splitHighlight(item.sub || '')"
                    :key="si"
                    :class="{ hl: seg.hit }"
                  >{{ seg.text }}</text>
                </text>
                <text v-if="item.distance != null" class="mixed-dist-seg">距你 {{ fmtMixedDistance(item.distance) }}</text>
              </view>
            </view>
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
import { ref, computed, onMounted } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import { useDishStore, type HomeSortKey } from '@/stores/dish'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import { useLocationStore } from '@/stores/location'
import type { DishSortBy } from '@/types/dish'
import { getUserLocation } from '@/utils/location'
import { getImageUrl, getThumbUrl } from '@/utils/image'
import IconSvg from '@/components/IconSvg.vue'
import EmptyState from '@/components/EmptyState.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import CardSection from '@/components/CardSection.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import FilterBar from '@/components/FilterBar.vue'
import AppHeader from '@/components/AppHeader.vue'

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
  /** 缩略图是否已加载完成（驱动淡入，纯前端渲染态） */
  loaded?: boolean
}
const mixedResults = ref<MixedResult[]>([])
const mixedLoading = ref(false)

/** 搜索结果（仅菜品单列；距离已在 doMixedSearch 经 withLocalDistance 写回，未定位回退校区中心，恒有值） */
const filteredMixed = computed(() => mixedResults.value)

/** 距你文案：米/公里自适应 */
function fmtMixedDistance(m: number): string {
  if (!Number.isFinite(m) || m < 0) return ''
  if (m > 999000) return '>999km'
  return m >= 1000 ? `${(m / 1000).toFixed(1)}km` : `${Math.round(m)}m`
}
/** A1 关键词高亮：将文本按当前 keyword 拆分为 [{text, hit}] 片段，命中段由模板套 .hl（朱砂红），避免 v-html XSS */
function splitHighlight(text: string): { text: string; hit: boolean }[] {
  const kw = keyword.value.trim()
  if (!text || !kw) return [{ text, hit: false }]
  const segs: { text: string; hit: boolean }[] = []
  const lowerText = text.toLowerCase()
  const lowerKw = kw.toLowerCase()
  let start = 0
  let idx = lowerText.indexOf(lowerKw, start)
  while (idx !== -1) {
    if (idx > start) segs.push({ text: text.slice(start, idx), hit: false })
    segs.push({ text: text.slice(idx, idx + kw.length), hit: true })
    start = idx + kw.length
    idx = lowerText.indexOf(lowerKw, start)
  }
  if (start < text.length) segs.push({ text: text.slice(start), hit: false })
  return segs
}
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
    // 避免旧慢请求返回时把新请求的 loading 提前关闭导致骨架屏闪烁
    if (seq === mixedSearchSeq) mixedLoading.value = false
  }
}

/** 结果点击：菜品跳详情页（搜索仅菜品，无独立档口/食堂结果/详情页） */
function goToMixed(item: MixedResult) {
  try { uni.vibrateShort({ type: 'light' }) } catch { /* 部分平台无震动 API，忽略 */ }
  if (item.id) openDishDetail(item.id)
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
/* 顶部留白由内容块自己提供（搜索 mixed-list / 发现 skeleton 均为 md，与首页广播条-卡间距一致）；scroll 不再额外叠加 */
.scroll-wrap { flex: 1; overflow-y: auto; padding-top: 0; padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }

/* 食堂筛选行（community-review-redesign：header 下方独立一行，与首页共用 FilterBar） */
.find-filter-row {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-lg);
  /* 表面统一：与首页筛选条一致，使用页面凹陷面，消除白色割裂条 */
  background: var(--bg-page);
  border-bottom: 1rpx solid var(--border-color);
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
.mixed-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  padding: var(--spacing-md);
  box-shadow: var(--shadow-card);
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}
.mixed-item + .mixed-item { margin-top: var(--spacing-sm); }
.mixed-item.pressed { background-color: var(--bg-soft); }
.mixed-thumb {
  width: 160rpx;
  height: 160rpx;
  flex-shrink: 0;
  border-radius: var(--radius-icon);
  overflow: hidden;
  background: var(--bg-page);
}
/* C12 图片淡入：初始透明，loaded 后置 1 由 transition 淡入 */
.mixed-thumb-img { width: 100%; height: 100%; opacity: 0; transition: opacity 0.32s var(--ease-out); }
.mixed-thumb-img.loaded { opacity: 1; }
.mixed-thumb-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.mixed-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); min-height: 160rpx; justify-content: center; }
/* 第一行：菜名（加大）+ 评分（贴名小号）+ 价格 两端对齐（名称/价格是搜索核心，价格不换行防挤占菜名） */
.mixed-title-row { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); }
.mixed-name-group { flex: 1; min-width: 0; display: flex; align-items: center; gap: var(--spacing-sm); }
.mixed-name {
  /* 不撑满：评分紧贴菜名右侧（而非被推到行尾贴近价格）；长菜名可收缩省略 */
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
  line-height: 1.3;
  letter-spacing: var(--tracking-h3);
  /* A5 菜名两行截断：长菜名不再丢信息 */
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
/* A1 关键词高亮：命中段朱砂红 */
.mixed-name .hl, .mixed-sub .hl { color: var(--color-primary); font-weight: var(--weight-bold); }
/* B9 属性标签 chips */
.mixed-tags { display: flex; flex-wrap: wrap; gap: var(--spacing-2xs); margin-top: 2rpx; }
.mixed-tag {
  font-size: var(--font-tiny);
  line-height: 1.4;
  padding: 2rpx 12rpx;
  border-radius: var(--radius-tag);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: var(--weight-medium);
}
/* B10 促销角标 + 原价划线 */
.mixed-promo-badge {
  font-size: var(--font-tiny);
  line-height: 1.4;
  padding: 2rpx 12rpx;
  border-radius: var(--radius-tag);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-weight: var(--weight-bold);
  /* 价格组 baseline 对齐下居中，避免角标因 padding 偏上 */
  align-self: center;
}
.mixed-original { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }
/* 价格组：促销角标 + 促销价/单价 + 原价划线；flex-shrink:0 防被菜名挤压，baseline 对齐 */
.mixed-price-group { display: flex; align-items: baseline; gap: var(--spacing-2xs); flex-shrink: 0; }
/* A6 价格视觉强化：¥ 符号缩小、数字放大，统一用专用价色 --color-price */
.mixed-price { font-size: var(--font-title); font-weight: var(--weight-bold); color: var(--color-price); font-variant-numeric: tabular-nums; }
.mixed-price-sym { font-size: var(--font-body); font-weight: var(--weight-medium); }
/* 评分组：贴近菜名右侧、小一号/两号（弱化星级，避免喧宾夺主） */
.mixed-rating-group { display: inline-flex; align-items: center; gap: 2rpx; flex-shrink: 0; }
.mixed-rating-star { flex-shrink: 0; }
.mixed-rating-num { font-size: var(--font-small); font-weight: var(--weight-medium); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
/* 第三行位置：左段档口·食堂可省略、右段「距你 Xm」固定不截断，两端对齐，与标徽行分隔 */
.mixed-sub { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--text-secondary); }
.mixed-sub-text { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* A4 距离段：主色强调，无定位时不显示 */
.mixed-dist-seg { flex-shrink: 0; color: var(--color-primary); font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; }

/* 发现主页首屏骨架（2026-08-03：分类宫格已删，仅保留热搜列表占位） */
.discover-skeleton { padding: var(--spacing-md) var(--spacing-md) 0; }
.sk-row { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.sk-line { height: 110rpx; border-radius: var(--radius-card); flex: 1; }

/* A3 搜索结果加载骨架屏（复用 .skeleton 闪烁） */
.mixed-item-skeleton { animation: none; }
.mixed-item-skeleton .mixed-thumb { background: var(--bg-soft); }
.mixed-item-skeleton .mixed-info { gap: var(--spacing-sm); }
.sk-name { height: 32rpx; width: 70%; border-radius: var(--radius-tag); }
.sk-sub { height: 24rpx; width: 50%; border-radius: var(--radius-tag); }
.sk-meta { height: 28rpx; width: 40%; border-radius: var(--radius-tag); }
/* 骨架占位（微光由全局 .skeleton 处理；本页保留灰底） */
.skeleton {
  position: relative;
  overflow: hidden;
  background: var(--bg-soft);
}

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
