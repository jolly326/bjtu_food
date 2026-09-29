<template>
  <view class="page find-page" :style="{ paddingTop: `${titleBandPx}px` }">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下，接入无需改动既有层级） -->
    <PageWallpaper fixed />
    <!-- 顶部两段式（搜索页头部由 `AppTitleBand` + `SearchBar` 承载）：
         ① 固定标题带：左上角返回 icon（占原页面标题位、与微信胶囊同一水平带）；
         ② 搜索行：与首页完全同款（左搜索胶囊 + 右「搜索」按钮），本页为 input 模式（可输入 + 提交）。
         两段常驻固定（根层不滚动，滚动只发生在内容区内部）。 -->
    <!-- 标题带（UI 统一 Loop Round 15 两区版）：有返回 ⇒ 左区「返回」+ 居中区页面名称。
         ⚠️ 页面名称暂定「搜索」（本页语义见 docs/ui/client-搜索.md），如需改文案告诉我。 -->
    <AppTitleBand back title="搜索" @back="onBack" />
    <view class="find-search-row">
      <SearchBar
        mode="input"
        v-model="keyword"
        :searching="searching"
        :disabled="!keyword.trim()"
        @search="onSearchConfirm"
        @clear="clearKeyword"
      />
    </view>

    <!-- 结果态无筛选条：食堂 / 价格筛选不提供——
         搜索页头部回到「输入框 + 结果」，不再有筛选胶囊与下拉面板。 -->

    <!-- 内容区（find-page-layout-restructure）：双态分支互斥。
         发现态 = 搜索记录 + 猜你喜欢；结果态 = 结果列表。
         两态**各自**用 `scroll-view` 承载滚动（Round 26 复核）：页面根 `height: 100vh/100dvh + overflow: hidden`
         ⇒ 页面自身不滚动；容器 `flex: 1 + min-height: 0` ⇒ 定高 ⇒ 内容未超高时既无滚动条、也无空白可滚区。 -->
    <view class="find-body">
      <!-- ============ 发现主页（搜索记录 + 猜你喜欢）============
           ⚠️ 用 `v-show` 而非 `v-if`（Round 27 缺陷修复）：`v-if` 会在「点 X 回发现态」时**重建** `scroll-view`
           —— 小程序下新建实例的测量可能早于父级布局完成 ⇒ 高度按 0 计算 ⇒ **整块内容不可见**
           （正是用户报的「回搜索界面看不到搜索记录、返回首页重进才显示」）。常驻 + display 切换
           ⇒ 复用同一个已测量实例，不再重建、不再丢内容。 -->
      <scroll-view v-show="!inFilter" class="discover-body" scroll-y>
        <template>
          <!-- 搜索记录（首位）
               ⚠️ Round 27c（间距真因修复）：分组卡的外间距**必须落在页面自己的节点上**。
               直接给组件传 class（`<CardSection class="discover-card">`）时，小程序端该 class 落进的是
               **组件宿主节点**，而宿主默认**不是块级盒** ⇒ `margin` 被**静默忽略**（横向全丢、纵向也丢）
               ⇒ 卡片左右贴屏幕边、两张卡还相贴（页面 wxss 里那条规则看似生效、实际不产生布局）。
               故改为「外层 `view.discover-card` 承担间距 + 卡壳 `flush` 把自身外边距归零」。 -->
          <view v-if="historyList.length > 0" class="discover-card">
            <CardSection flush>
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
                  role="button"
                  :aria-label="`搜索 ${kw}`"
                  hover-class="history-chip-pressed"
                  @tap="goKeyword(kw)"
                >
                  <text class="history-chip-text">{{ kw }}</text>
                  <view
                    class="history-chip-del"
                    role="button"
                    :aria-label="`删除记录 ${kw}`"
                    @tap.stop="removeHistory(i)"
                  >
                    <IconSvg name="close" :size="24" :color="COLOR_MAP['text-tertiary']" />
                  </view>
                </view>
              </view>
            </CardSection>
          </view>

          <!-- 猜你喜欢（GET /dishes/for-you）：后端**每次随机**推送在售菜品名（不看热度、不排序、
               不做个性化）；端上按返回渲染、不写死条数与文案；空数组 / 请求失败 → 整块不渲染 -->
          <view v-if="guessLikeList.length > 0" class="discover-card">
            <CardSection flush>
              <SectionTitle title="猜你喜欢" />
              <view class="history-chips">
                <view
                  v-for="(kw) in guessLikeList"
                  :key="kw.name"
                  class="history-chip history-chip-hot"
                  role="button"
                  :aria-label="`搜索 ${kw.name}`"
                  hover-class="history-chip-pressed"
                  @tap="goKeyword(kw.name, true)"
                >
                  <text class="history-chip-text">{{ kw.name }}</text>
                </view>
              </view>
            </CardSection>
          </view>

        </template>
      </scroll-view>

      <!-- ============ 搜索结果态（仅结果态渲染）============
           Round 21b：原 `FindResults` 并入本页 —— 抽出结果卡后其职责只剩「滚动容器 + 列表编排」，
           单独成件无意义；结果卡 = 页内私有 `DishResultCard`（布局规格见 docs/ui/client-搜索.md §2「结果行布局」）。 -->
      <scroll-view
        v-if="inFilter && mixedResults.length > 0"
        class="results-host"
        scroll-y
      >
        <view class="mixed-list">
          <DishResultCard
            v-for="item in mixedResults"
            :key="`${item.type}-${item.id}`"
            :item="item"
            :keyword="keyword"
            @select="goToMixed"
          />
        </view>
      </scroll-view>
      <!-- 搜索失败重试块（MP-012，P3-03 上提为公共组件）：请求已完成且失败 → 失败态块，
           先于空态渲染，避免网络失败被误导向「没搜到」的无结果引导（三态：失败 ≠ 无数据）。
           Round 28：与空态**共用 `.state-host`**（原 `.find-retry-host` / `.find-empty-host` 两条规则逐字相同）。 -->
      <view v-else-if="inFilter && searchDone && searchFailed" class="state-host">
        <RetryBlock title="搜索加载失败" aria-label="搜索失败，点击重试" :margin="false" @retry="onRetrySearch" />
      </view>
      <!-- 搜索无结果引导（search-no-result-guidance）：请求**已完成**且结果为空才呈现；
           未完成（静默）或失败（走上方重试块）不渲染，避免闪现/误导向。引导把没找到的菜报给我们 -->
      <!-- 统一空态组件（UI 统一 Loop Round 3）：卡片变体；`.state-host` 只承担整屏居中占位，
           视觉全在公共 `EmptyState` 内（与失败态同语言） -->
      <view v-else-if="inFilter && searchDone" class="state-host">
        <EmptyState
          card
          icon="search"
          :icon-size="48"
          :title="`没搜到「${keyword}」相关的菜`"
          desc="把它报给我们，让更多同学也能找到"
          action-text="推荐这道菜"
          @action="goContributeNotFound"
        />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import { useDishStore } from '@/stores/dish'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import { dishDetailUrl, feedbackUrl } from '@/utils/routes'
import { backToHome } from '@/utils/back'
import { joinLocation } from '@/utils/dish'
import type { MixedResultItem } from '@/types/dish'
import IconSvg from '@/components/IconSvg.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import EmptyState from '@/components/EmptyState.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import CardSection from '@/components/CardSection.vue'
import AppTitleBand from '@/components/AppTitleBand.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import SearchBar from '@/components/SearchBar.vue'
import DishResultCard from './DishResultCard.vue'
import { COLOR_MAP, MODAL_CONFIRM_DANGER_COLOR } from '@/theme/tokens'
import { useNavMetrics } from '@/utils/useNavMetrics'

const dishStore = useDishStore()

/** 固定标题带高（px）：带为 `position: fixed`，页面根层须用等量 padding 顶开内容 */
const { titleBandPx } = useNavMetrics()

/**
 * 返回：**恒退出本页**（首页 → 本页为 `navigateTo`，返回即回首页）。
 *
 * 结果态的退出**不由返回键承担**：改由搜索框右侧「清空 X」承担（清词 + 回发现态，见 `clearKeyword`）。
 * 理由：返回键在结果态「先退状态、再退页」的两段语义**不可见**，用户会读作「按了返回却没退页」。
 */
function onBack() {
  backToHome()
}

/* 返回回首页：统一复用 utils/back.backToHome（navigateBack 保留返回动画，无上一页时 reLaunch 首页兜底） */

const keyword = ref('')

// ===== 搜索历史（本地缓存，预留接口位） =====
const HISTORY_KEY = 'find_search_history'
/** 搜索记录上限 4 条（find-page-layout-restructure 2.6：缓存与展示一致、无展开收起） */
const HISTORY_MAX = 4
const historyList = ref<string[]>([])

/** 猜你喜欢词列表（来源：后端 GET /dishes/for-you，由 loadDiscover → fetchGuessLike 拉取）。
    Round 28：改用 `storeToRefs` —— 原先对 store getter 再包一层 `computed`，属冗余包装。 */
const { guessLikeList } = storeToRefs(dishStore)

function loadHistory() {
  try {
    const raw = uni.getStorageSync(HISTORY_KEY)
    if (Array.isArray(raw)) historyList.value = raw.slice(0, HISTORY_MAX)
  } catch {
    /* 读取异常：**保留内存副本**，不清空 —— 本函数自 Round 27 起会在「回发现态 / onShow」时多次调用，
       瞬时读取失败不应把用户已看到的搜索记录清掉 */
  }
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

// 搜索模式：结果页为复合型混合列表，无排序/筛选
const inFilter = ref(false)
/** 搜索请求是否已完成（成功/失败均置真，过期请求不置）：用于区分「静默加载中」与「无结果引导」，避免空态闪现 */
const searchDone = ref(false)
/** 提交中（驱动「搜索」按钮禁用态；skill §2 `loading-buttons` / §8 `submit-feedback`）：
    仅**最新一次**请求可清除——过期请求返回时不得复位，否则会提前解除新请求的禁用态 */
const searching = ref(false)
/** 最近一次已完成搜索是否失败（MP-012）：失败 ≠ 无结果，失败渲染重试块而非「没搜到」空态 */
const searchFailed = ref(false)

// ===== 结果态筛选（食堂 / 价格）不提供 =====
// 搜索页不再持有任何筛选状态：不传 canteenId / minPrice / maxPrice，也不传排序参数
// （排序口径唯一由后端决定：热度优先、不设排序入口）。

/* 搜索结果项类型来自公共 `@/types/dish.MixedResultItem`（UI 统一 Loop Round 17：
   原先本页 `MixedResult` 与 FindResults 内 `MixedResultItem` 是逐字段重复的两份定义） */
const mixedResults = ref<MixedResultItem[]>([])


/** 确认/回车搜索（SearchBar input 模式的 @search：回车 / 点「搜索」按钮） */
function onSearchConfirm() {
  const kw = keyword.value.trim()
  if (!kw) return
  pushHistory(kw)
  doMixedSearch(kw)
}

/**
 * 清空关键词 = 「重新开始」：清词 **并** 退出结果态回发现态。
 *
 * ⚠️ 只清 `keyword` 会留下两个坑：① 输入框已空、列表仍是旧结果（状态与内容不一致）；
 * ② 此后点「搜索」无词可搜 —— 旧实现静默 return，用户读作「点了没反应」。
 */
function clearKeyword() {
  keyword.value = ''
  if (inFilter.value) exitFilter()
}

/**
 * 词条点击（搜索记录 / 猜你喜欢）：以该词发起搜索。
 *
 * **写入口径（Round 31 用户口径变更）**：
 * · **「猜你喜欢」词条 → 写入搜索记录**（`record = true`）—— 它同样是一次**用户主动发起的搜索**，
 *   与「打字后提交」在用户心智里等价，理应可回溯（此前不写入造成「搜过却没有记录」的困惑）；
 * · **「搜索记录」词条 → 不写入**（`record = false`）—— 该词本就在记录内，重搜无需再置顶；
 *   上限仅 4 条，重复写入只会打乱既有顺序。
 */
function goKeyword(kw: string, record = false) {
  keyword.value = kw
  if (record) pushHistory(kw)
  doMixedSearch(kw)
}

// ===== 复合型搜索（直接复用菜品检索接口） =====
// C13 竞态守卫：慢请求结果不得覆盖后发的快请求（参照 review.vue searchSeq 模式）
let mixedSearchSeq = 0
async function doMixedSearch(kw?: string) {
  // 搜索页唯一入口 = 关键词（不提供食堂 / 价格筛选，无关键词则不发起检索）
  if (!kw) return
  // 竞态守卫（mixedSearchSeq）已保证后发请求覆盖先发结果；此处不设防重入锁，
  // 否则用户连续搜索新词时会被静默丢弃、界面停留在旧结果。
  const seq = ++mixedSearchSeq
  inFilter.value = true
  searchDone.value = false
  searchFailed.value = false
  searching.value = true
  try {
    // 复用 store.search（GET /dishes?keyword，返回平铺 DishListItem[]），金额/图片已在 api 层归一；
    // 端上不传任何筛选 / 排序参数
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
        // B8 副信息：食堂名 + 档口名（口径统一走 `utils/dish.joinLocation`）
        const sub = joinLocation(d.canteen, d.stallName)
        return {
          type: 'dish' as const,
          id: d.id,
          name: d.name,
          // 列表唯一图片字段 coverImage（列表 VO 不含 images 数组）
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
  } finally {
    // 仅最新一次请求可解除「提交中」：过期请求返回时不得复位，否则新请求的禁用态会被提前清掉
    if (seq === mixedSearchSeq) searching.value = false
  }
}

/** 重试当前检索：结果态失败恢复走此路径（重试块 @tap；按当前关键词重跑，竞态守卫在 doMixedSearch 内） */
function onRetrySearch() {
  return doMixedSearch(keyword.value.trim())
}

/** 搜索无结果引导 → 反馈页（落默认 issue 模式；落点唯一构造函数） */
function goContributeNotFound() {
  uni.navigateTo({ url: feedbackUrl() })
}

/** 结果点击：菜品跳详情页（搜索仅菜品，无独立档口 / 食堂结果 / 详情页）。
    Round 28：原 `openDishDetail` 仅此一处调用，且与本函数重复判空 ⇒ 合并为单点守卫。 */
function goToMixed(id: number) {
  if (!id) return
  uni.navigateTo({ url: dishDetailUrl(id) })
}

function exitFilter() {
  inFilter.value = false
  mixedResults.value = []
  searchDone.value = false
  searchFailed.value = false
  // 修复：退出结果态时递增序号使在途旧请求失效，避免其返回后写回 mixedResults 造成数据残留
  mixedSearchSeq += 1
  // Round 27 缺陷修复：回发现态时**重读搜索历史**（以存储为唯一真源）。
  // 此前只在 onMounted 读一次 —— 本页被页面栈缓存（返回再进不重新挂载）时，内存副本一旦滞后于存储，
  // 搜索记录就不会更新（用户报的「刚搜过的词回发现态看不到」）。
  loadHistory()
}

async function loadDiscover() {
  try {
    // 发现态数据源只剩「猜你喜欢」词条（无食堂字典端点）
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
// 从菜品详情返回搜索页：清掉分享残留，避免右上角分享菜单沿用详情页内容；
// Round 27：同时重读搜索历史 —— 本页被页面栈缓存时 onMounted 不再执行，以存储为真源重读
// 可保证「搜索记录」始终最新（与 exitFilter 时的重读互为兜底）。
onShow(() => {
  clearShareState()
  loadHistory()
})
</script>

<style scoped>
/* 页面根不带底色（UI 统一 Loop Round 11）：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层 */
.find-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; overflow: hidden; box-sizing: border-box; }
/* 搜索行宿主（搜索页 UI §2）：标题带下沿 → 搜索行上沿 = --spacing-md（同属「头部单元」）；
   搜索行下沿 → 内容首块 = --spacing-lg（块间）。搜索行左侧 gutter 由 SearchBar 内部自持（与首页同源） */
.find-search-row { padding-top: var(--spacing-md); padding-bottom: var(--spacing-lg); box-sizing: border-box; }
/* 内容区：占满 header 之外的剩余高度；两个分支**各自**自带滚动容器（发现态 discover-body / 结果态 results-host） */
.find-body { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
/* 发现态滚动容器（Round 26）：由 `<view>` + `overflow-y:auto` 改为 `scroll-view` ——
   小程序只保证 `scroll-view` 可滚动，`view` 自滚在 iOS / 部分安卓上不可靠，内容超高会被
   `overflow: hidden` 的页根裁掉且不可达。⚠️ `scroll-view` 自身**不写** `overflow-y`
   （滚动由组件内部实现，外挂 CSS 会在 H5 叠出第二根滚动条）。
   `flex: 1 + min-height: 0` ⇒ 容器定高 ⇒ 内容未超高时既不出现滚动条、也没有可滚的空白。 */
.discover-body { flex: 1; min-height: 0; padding-bottom: var(--spacing-lg); }
/* 分组卡外壳（**页面自有节点**，不是组件宿主）：承担每张卡的左右 gutter + 纵向块间距。
   ⚠️ Round 27c（真因，此前两版都没修对）：间距**必须落在页面自己的节点上** ——
   曾把本类直接传给 `<CardSection>`：小程序端该类落进**组件宿主节点**，而宿主默认**不是块级盒**
   ⇒ `margin` 被**静默忽略**（横向全丢、纵向也丢 ⇒ 「卡片左右贴屏幕边 + 两张卡相贴」）。
   现由外层 `<view>` 承担，卡壳只传 `flush`（把自身外边距归零，避免双层）。
   首卡上间距的**唯一来源 = 搜索行下 padding**（UI 文档 §2：块间 `--spacing-lg`），故本类上外边距为 0。
   ⚠️ 选型理由（实测教训）：小程序 WXSS 支持的选择器仅 `.class / #id / element / element,element / ::after / ::before`
   —— **不得用通配符 `*`**（实测报 `error at token '*'`），**也不依赖 `+` / `~` 兄弟选择器**；
   且 uni 本地构建**不校验**这些，只有微信开发者工具会拦。 */
.discover-card { display: block; margin: 0 var(--spacing-md) var(--spacing-lg); }
/* 结果态滚动容器（Round 21b：FindResults 并入本页）：
   flex 链占满剩余高度；底部留白（原 FindResults .results-scroll）随容器自带 */
.results-host {
  flex: 1;
  min-height: 0;
  padding-bottom: var(--spacing-lg);
}
/* 结果列表：左右 gutter + 底部间距；卡间纵向间距用 **flex gap** ——
   不用 `+` 兄弟选择器（mp-weixin WXSS 不保证支持，本文件上方有登记）。
   Round 21e（用户拍板）：结果**顶部对齐**、自上而下自然阅读 —— 原「单条结果垂直居中」
   （.mixed-list.single，为修「悬顶读作没加载完」而设）已移除；空白读感由「搜索行常驻 +
   结果态与发现态/空态/失败态互斥」缓解，不再用居中补偿。 */
.mixed-list {
  margin: 0 var(--spacing-md) var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

/* 空态 / 失败态**共用宿主**（Round 28 合并：两条规则原先逐字重复，仅注释不同）：
   只承担整屏居中占位与边距，视觉分别由公共 `EmptyState`（卡片变体）/ `RetryBlock` 承担。 */
.state-host {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  margin: var(--spacing-lg);
  box-sizing: border-box;
}
/* 搜索页头部为「输入框 + 结果」，无筛选行 / FilterBar 样式 */

/* 区块通用 */
.section-extra { flex-shrink: 0; }

/* 历史搜索 */
/* QA-03 修复：视觉保持轻量小文字链，命中区经 ::after 透明覆盖扩至 ≥88rpx（Apple 44pt 触达下限） */
/* 「清空」是**破坏性操作**，需可被发现（UI 文档 §2）：字号 aux(22rpx) → small(24rpx)、色 tertiary → secondary；
   视觉仍远弱于分组标题（不抢层级），命中区继续由下方 ::after 扩至 ≥88rpx。 */
.history-clear { position: relative; font-size: var(--font-small); color: var(--text-secondary); font-weight: var(--weight-medium); padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-tag); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
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
  /* skill §2 `tap-delay`：消除移动端点击延迟 */
  touch-action: manipulation;
}
/* 按压反馈（skill §2 `press-feedback`，CRITICAL 级）：可点元素必须有点按反馈。
   旧实现只声明了 `transition: background` 却**没有任何按压态** —— 等于「点了完全没反应」。 */
.history-chip-pressed { background: var(--bg-placeholder); }
.history-chip-hot.history-chip-pressed { background: var(--bg-soft-orange); }
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
/* 「猜你喜欢」词条 vs 搜索记录**必须可区分**（UI 文档 §3）：推荐词 = 暖黄底 + 深棕字。
   fallback 仅用于色板落地前的过渡——`--bg-soft-yellow` / `--text-body` 落地（§7.39）后区分自动生效；
   ⚠️ 落地后不得再依赖 fallback（回落会让两区块 chip 完全同款）。 */
.history-chip-hot { background: var(--bg-soft-yellow, var(--bg-soft)); }
.history-chip-hot .history-chip-text { color: var(--text-body, var(--text-secondary)); }
</style>
