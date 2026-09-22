<template>
  <view class="page find-page" :style="{ paddingTop: `${titleBandPx}px` }">
    <!-- 顶部两段式（2026-09-22 change search-page-refresh：原 `AppHeader` search variant 已退役）：
         ① 固定标题带：左上角返回 icon（占原页面标题位、与微信胶囊同一水平带）；
         ② 搜索行：与首页完全同款（左搜索胶囊 + 右「搜索」按钮），本页为 input 模式（可输入 + 提交）。
         两段常驻固定（根层不滚动，滚动只发生在内容区 / FindResults 内部）。 -->
    <AppTitleBand back @back="onBack" />
    <view class="find-search-row">
      <SearchBar
        mode="input"
        v-model="keyword"
        @search="onSearchConfirm"
        @clear="clearKeyword"
      />
    </view>

    <!-- 结果态筛选条已整体删除（2026-09-22 K2）：食堂 / 价格筛选全量下线——
         搜索页头部回到「输入框 + 结果」，不再有筛选胶囊与下拉面板。 -->

    <!-- 内容区（find-page-layout-restructure）：双态分支互斥。
         发现态 = 静态区块（无页面级滚动容器）；结果态 = 滚动随 FindResults 内容区 -->
    <view class="find-body">
      <!-- ============ 发现主页（未进入结果态）：搜索记录 + 猜你喜欢，静态展示 ============ -->
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
                  <IconSvg name="close" :size="24" :color="COLOR_MAP['text-tertiary']" />
                </view>
              </view>
            </view>
          </CardSection>

          <!-- 猜你喜欢（GET /dishes/for-you）：后端**每次随机**推送在售菜品名（不看热度、不排序、
               不做个性化）；端上按返回渲染、不写死条数与文案；空数组 / 请求失败 → 整块不渲染 -->
          <CardSection v-if="guessLikeList.length > 0">
            <SectionTitle title="猜你喜欢" />
            <view class="history-chips">
              <view
                v-for="(kw) in guessLikeList"
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
        @select="goToMixed"
      />
      <!-- 搜索失败重试块（MP-012，P3-03 上提为公共组件）：请求已完成且失败 → 失败态块，
           先于空态渲染，避免网络失败被误导向「没搜到」的无结果引导（三态：失败 ≠ 无数据）。
           find-retry-host 仅负责整屏居中占位（页面内部滚动容器需撑满剩余高度），视觉全在 RetryBlock 内。 -->
      <view v-else-if="inFilter && searchDone && searchFailed" class="find-retry-host">
        <RetryBlock title="搜索加载失败" aria-label="搜索失败，点击重试" :margin="false" @retry="onRetrySearch" />
      </view>
      <!-- 搜索无结果引导（search-no-result-guidance）：请求**已完成**且结果为空才呈现；
           未完成（静默）或失败（走上方重试块）不渲染，避免闪现/误导向。引导把没找到的菜报给我们 -->
      <view v-else-if="inFilter && searchDone" class="find-empty">
        <view class="fe-icon">
          <IconSvg name="search" :size="48" :color="COLOR_MAP['text-tertiary']" />
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
import { useDishStore } from '@/stores/dish'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import { dishDetailUrl, feedbackEntryUrl } from '@/utils/routes'
import { backToHome } from '@/utils/nav'
import IconSvg from '@/components/IconSvg.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import CardSection from '@/components/CardSection.vue'
import AppTitleBand from '@/components/AppTitleBand.vue'
import SearchBar from '@/components/SearchBar.vue'
import FindResults from './FindResults.vue'
import { COLOR_MAP, MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'
import { useNavMetrics } from '@/utils/useNavMetrics'

const dishStore = useDishStore()

/** 固定标题带高（px）：带为 `position: fixed`，页面根层须用等量 padding 顶开内容 */
const { titleBandPx } = useNavMetrics()

/** 返回：结果态先退回发现态，否则回首页（沿用既有行为） */
function onBack() {
  if (inFilter.value) exitFilter()
  else backToHome()
}

/* 返回回首页：统一复用 utils/nav.backToHome（navigateBack 保留返回动画，无上一页时 reLaunch 首页兜底） */

/** 菜品详情：跳转独立页（pages/detail/dish） */
function openDishDetail(id: number) {
  if (!id) return
  uni.navigateTo({ url: dishDetailUrl(id) })
}
const keyword = ref('')

// ===== 搜索历史（本地缓存，预留接口位） =====
const HISTORY_KEY = 'find_search_history'
/** 搜索记录上限 4 条（find-page-layout-restructure 2.6：缓存与展示一致、无展开收起） */
const HISTORY_MAX = 4
const historyList = ref<string[]>([])

/** 猜你喜欢词列表（来源：后端 GET /dishes/for-you，由 loadDiscover → fetchGuessLike 拉取） */
const guessLikeList = computed(() => dishStore.guessLikeList)

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

// ===== 结果态筛选（食堂 / 价格）已全量下线（2026-09-22 K2） =====
// 搜索页不再持有任何筛选状态：不传 canteenId / minPrice / maxPrice，也不传排序参数
// （排序口径唯一由后端决定：热度优先、不设排序入口）。

/** 混合搜索结果：复用菜品检索接口返回 DishListItem[]（搜索仅针对菜品） */
interface MixedResult {
  type: 'dish'
  id?: number
  name: string
  /** 列表唯一图片字段（后端 coverImage；无图空串 → 结果卡占位空态） */
  image?: string
  /** 副信息：菜品→「食堂 · 档口」（B8 档口名）；档口/食堂→位置 */
  sub?: string
  /** 菜品专属：价格（元，api 层已转） */
  price?: number
  /** 菜品专属：平均评分 */
  rating?: number
  /** 菜品专属：原价（元，> price 时划线展示表示折扣） */
  originalPrice?: number
}
const mixedResults = ref<MixedResult[]>([])

/** 搜索结果（仅菜品单列） */
const filteredMixed = computed(() => mixedResults.value)

/** 确认/回车搜索（SearchBar input 模式的 @search：回车 / 点「搜索」按钮） */
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
  // 搜索页唯一入口 = 关键词（食堂 / 价格筛选已下线，不再支持「无关键词按食堂浏览」）
  if (!kw) return
  // 竞态守卫（mixedSearchSeq）已保证后发请求覆盖先发结果；此处不设防重入锁，
  // 否则用户连续搜索新词时会被静默丢弃、界面停留在旧结果。
  const seq = ++mixedSearchSeq
  inFilter.value = true
  searchDone.value = false
  searchFailed.value = false
  try {
    // 复用 store.search（GET /dishes?keyword，返回平铺 DishListItem[]），金额/图片已在 api 层归一；
    // 端上不传任何筛选 / 排序参数（2026-09-22 K2/K3）
    const list = await dishStore.search({
      keyword: kw,
      page: 1,
      pageSize: 50,
    })
    // 竞态守卫：若期间发起了更新的搜索，丢弃本次过期结果
    if (seq !== mixedSearchSeq) return
    // 结果顺序即后端返回口径（PR-02：端上不排序、不算距离）
    mixedResults.value = list
      .map(d => {
        // B8 副信息：食堂名 + 档口名（顺序与首页 DishCard 的「食堂 | 档口」一致）
        const sub = [d.canteen, d.stallName].filter(Boolean).join(' · ')
        return {
          type: 'dish' as const,
          id: d.id,
          name: d.name,
          // 列表唯一图片字段 coverImage（2026-09-22 D 项拆分；原 images[0] 已随列表 VO 收敛）
          image: d.coverImage || '',
          sub,
          price: d.price,
          rating: d.rating,
          originalPrice: d.originalPrice,
        }
      })
      .filter(r => r.name)
    searchDone.value = true
  } catch (err) {
    // MP-012：失败不再伪装成空结果——置 searchFailed 渲染「加载失败 · 点击重试」块，
    // 与「没搜到」空态区分；结果态恢复走重试块 @tap（onRetrySearch）或重新提交搜索
    console.error('[find] 搜索失败', err)
    if (seq !== mixedSearchSeq) return
    mixedResults.value = []
    searchDone.value = true
    searchFailed.value = true
  }
}

/** 重试当前检索：结果态失败恢复走此路径（重试块 @tap；按当前关键词重跑，竞态守卫在 doMixedSearch 内） */
function onRetrySearch() {
  return doMixedSearch(keyword.value.trim())
}

/** 搜索无结果引导 → 反馈页预选「推荐菜品」空表单（落点唯一构造函数，from=find，见 contribution-entry） */
function goContributeNotFound() {
  uni.navigateTo({ url: feedbackEntryUrl({ type: 'add', from: 'find' }) })
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
  // 修复：退出结果态时递增序号使在途旧请求失效，避免其返回后写回 mixedResults 造成数据残留
  mixedSearchSeq += 1
}

async function loadDiscover() {
  try {
    // 发现态数据源只剩「猜你喜欢」词条（食堂字典端点已随筛选功能下线删除，K4）
    await dishStore.fetchGuessLike()
  } catch (e) {
    // 静默：发现态加载失败不呈现任何占位，异常仅记录
    console.error('[find] 发现页加载失败', e)
  }
}

onMounted(() => {
  loadHistory()
  loadDiscover()
})

onShareAppMessage(() => buildSharePayload())
// 从菜品详情返回搜索页：清掉分享残留，避免右上角分享菜单沿用详情页内容
onShow(() => clearShareState())
</script>

<style scoped>
.find-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); overflow: hidden; box-sizing: border-box; }
/* 搜索行宿主（搜索页 UI §2）：标题带下沿 → 搜索行上沿 = --spacing-md（同属「头部单元」）；
   搜索行下沿 → 内容首块 = --spacing-lg（块间）。搜索行左侧 gutter 由 SearchBar 内部自持（与首页同源） */
.find-search-row { padding-top: var(--spacing-md); padding-bottom: var(--spacing-lg); box-sizing: border-box; }
/* 内容区：占满 header/筛选行之外的剩余高度；滚动职责随分支（发现态静态/结果态 FindResults） */
.find-body { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
/* 发现态：普通内容容器 + 高度兜底（搜索记录上限 4 条内容短；内容超高时由内容区自身滚动兜底） */
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
/* 搜索失败态宿主（P3-03）：仅承担整屏居中占位与边距，视觉全在公共 RetryBlock 内 */
.find-retry-host {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  margin: var(--spacing-lg);
  box-sizing: border-box;
}
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

/* 筛选行 / FilterBar 宿主样式已随「食堂 / 价格筛选全量下线」删除（2026-09-22 K2）；搜索页头部回到「输入框 + 结果」 */

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
/* 「猜你喜欢」词条 vs 搜索记录层级区分：推荐词 = 暖橙黄色板的**浅黄底 + 深棕字**（content-flow-visual）；
   新色板 token 尚未随色板 change 落地时用 fallback 回落到现状 token，避免出现「无底色」 */
.history-chip-hot { background: var(--bg-soft-yellow, var(--bg-soft)); }
.history-chip-hot .history-chip-text { color: var(--text-body, var(--text-secondary)); }
</style>
