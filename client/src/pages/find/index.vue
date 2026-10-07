<template>
  <view class="page find-page" :style="{ paddingTop: `${titleBandPx}px` }">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下，接入无需改动既有层级） -->
    <PageWallpaper fixed />
    <!-- 顶部两段式（搜索页头部由 `AppTitleBand` + `SearchBar` 承载）：
         ① 固定标题带：左上角返回 icon（占原页面标题位、与微信胶囊同一水平带）；
         ② 搜索行：与首页完全同款（左搜索胶囊 + 右「搜索」按钮），本页为 input 模式（可输入 + 提交）。
         两段常驻固定（根层不滚动，滚动只发生在内容区内部）。 -->
    <!-- 标题带：有返回 ⇒ 左区「返回」+ 居中区页面名称。
         ⚠️ 页面名称暂定「搜索」（本页语义见 docs/ui/client/搜索.md），如需改文案告诉我。 -->
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
         两态**各自**用 `scroll-view` 承载滚动：页面根 `height: 100vh/100dvh + overflow: hidden`
         ⇒ 页面自身不滚动；容器 `flex: 1 + min-height: 0` ⇒ 定高 ⇒ 内容未超高时既无滚动条、也无空白可滚区。 -->
    <view class="find-body">
      <!-- ============ 发现主页（搜索记录 + 猜你喜欢）============
           ⚠️ 用 `v-show` 而非 `v-if`：`v-if` 会在「点 X 回发现态」时**重建** `scroll-view`
           —— 小程序下新建实例的测量可能早于父级布局完成 ⇒ 高度按 0 计算 ⇒ **整块内容不可见**
           （正是用户报的「回搜索界面看不到搜索记录、返回首页重进才显示」）。常驻 + display 切换
           ⇒ 复用同一个已测量实例，不再重建、不再丢内容。 -->
      <scroll-view v-show="!inFilter" class="discover-body" scroll-y>
        <!-- 极简无卡片：发现态两个区块（搜索记录 / 猜你喜欢）各自一块 `.module-wrap`，
             区块之间**只靠页面留白分组**（容器 flex gap；小程序 WXSS 不支持 `+` 兄弟选择器）。 -->
        <view class="discover-modules">
          <!-- 搜索记录（首位） -->
          <view v-if="historyList.length > 0" class="module-wrap">
            <SectionTitle title="搜索记录">
              <!-- QA-03：破坏性操作补可访问角色与标签（热区见 .history-clear::after） -->
              <!-- 按压反馈：`<text>` 不支持 `hover-class` ⇒ 外层包 `<view>`（视觉与热区不变） -->
              <view
                slot="extra"
                class="section-extra history-clear"
                role="button"
                aria-label="清空搜索历史"
                hover-class="pressed"
                @tap="clearHistory"
              >
                <text class="history-clear-text">清空</text>
              </view>
            </SectionTitle>
            <!-- 搜索记录：缓存上限 4 条、全部直接展示、无「展开/收起」 -->
            <view class="history-chips">
              <view
                v-for="(kw, i) in historyList"
                :key="kw"
                class="history-chip"
                role="button"
                :aria-label="`搜索 ${kw}`"
                hover-class="history-chip-pressed"
                @tap="tapKeyword(kw)"
              >
                <text class="history-chip-text">{{ kw }}</text>
                <view
                  class="history-chip-del"
                  role="button"
                  :aria-label="`删除记录 ${kw}`"
                  hover-class="pressed"
                  hover-stop-propagation
                  @tap.stop="removeHistory(i)"
                >
                  <AppIcon name="close" :size="24" :color="COLOR_MAP['text-tertiary']" />
                </view>
              </view>
            </view>
          </view>

          <!-- 猜你喜欢（GET /dishes/for-you）：后端**每次随机**推送在售菜品名（不看热度、不排序、
               不做个性化）；端上按返回渲染、不写死条数与文案；空数组 / 请求失败 → 整块不渲染 -->
          <view v-if="guessLikeList.length > 0" class="module-wrap">
            <SectionTitle title="猜你喜欢" />
            <view class="history-chips">
              <view
                v-for="(kw) in guessLikeList"
                :key="kw.name"
                class="history-chip history-chip-hot"
                role="button"
                :aria-label="`搜索 ${kw.name}`"
                hover-class="history-chip-pressed"
                @tap="tapKeyword(kw.name, true)"
              >
                <text class="history-chip-text">{{ kw.name }}</text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>

      <!-- ============ 搜索结果态（仅结果态渲染）============
           结果态由本页承担「滚动容器 + 列表编排」，结果卡 = 页内私有 `DishResultCard`
           （布局规格见 docs/ui/client/搜索.md §2「结果行布局」）。 -->
      <!-- ⚠️ 触底事件必须由本 scroll-view 承载：页面根 overflow:hidden + 定高容器下，
           页面级 onReachBottom 不会触发（踩坑记录见 usePagedList 注释） -->
      <scroll-view
        v-if="inFilter && mixedResults.length > 0"
        class="results-host"
        scroll-y
        @scrolltolower="onLoadMoreResults"
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
        <!-- 触底反馈（全局 `.list-foot` 用法）：分页在途给在途提示；已到底给「没有更多结果」 -->
        <view v-if="loadingMore" class="list-foot">
          <text class="list-foot-text">正在加载更多…</text>
        </view>
        <view v-else-if="resultsFinished" class="list-foot">
          <text class="list-foot-text">没有更多结果了</text>
        </view>
      </scroll-view>
      <!-- 搜索失败重试块（MP-012，P3-03 上提为公共组件）：请求已完成且失败 → 失败态块，
           先于空态渲染，避免网络失败被误导向「没搜到」的无结果引导（三态：失败 ≠ 无数据）。
           与空态**共用 `.state-host`**。 -->
      <view v-else-if="inFilter && searchDone && searchFailed" class="state-host">
        <RetryBlock title="搜索加载失败" aria-label="搜索失败，点击重试" :margin="false" @retry="onRetrySearch" />
      </view>
      <!-- 搜索无结果引导（search-no-result-guidance）：请求**已完成**且结果为空才呈现；
           未完成（静默）或失败（走上方重试块）不渲染，避免闪现/误导向。引导把没找到的菜报给我们 -->
      <!-- 统一空态组件：卡片变体；`.state-host` 只承担整屏居中占位，
           视觉全在公共 `EmptyState` 内（与失败态同语言） -->
      <view v-else-if="inFilter && searchDone" class="state-host">
        <EmptyState
          card
          icon="search"
          :icon-size="48"
          :title="`没搜到「${emptyTitleKeyword}」相关的菜`"
          desc="把它报给我们，让更多同学也能找到"
          action-text="推荐这道菜"
          @action="goContributeNotFound"
        />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 搜索页（薄壳）。
 *
 * <p><b>本文件只做三件事</b>：① 页面布局与固定头部；② 发现态 / 结果态的互斥渲染；
 * ③ 把跨态动作转交 `useFindState`。**不含任何业务逻辑**——
 * 搜索记录、猜你喜欢、检索与分页分别见同目录的 composable。
 *
 * <p><b>为何是薄壳而非两个子组件</b>：发现态与结果态各自用 `scroll-view` 承载滚动，
 * 且发现态**必须常驻**（见下方 `v-show` 说明）——拆成子组件会改变 `v-show` 的作用域，
 * 有让该处已修复缺陷复现的风险。改为「逻辑拆开、DOM 不动」，同样达成解耦且零 DOM 变更。
 *
 * <p><b>导航语义</b>：返回**恒退出本页**（首页 → 本页为 `navigateTo`，返回即回首页）。
 * 结果态的退出**不由返回键承担**：改由搜索框右侧「清空」承担（清词 + 回发现态）——
 * 返回键在结果态「先退状态、再退页」的两段语义不可见，用户会读作「按了返回却没退页」。
 */
import { computed, onMounted } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import PageWallpaper from '@/components/PageWallpaper.vue'
import AppTitleBand from '@/components/AppTitleBand.vue'
import SearchBar from '@/components/SearchBar.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppIcon from '@/components/AppIcon.vue'
import DishResultCard from './DishResultCard.vue'
import { useFindState } from './useFindState'
import { useDiscover } from './useDiscover'
import { backToHome } from '@/utils/back'
import { dishDetailUrl, feedbackUrl } from '@/utils/routes'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import { useNavMetrics } from '@/utils/useNavMetrics'
import { COLOR_MAP } from '@/theme/tokens'

const {
  keyword,
  // 发现态
  historyList,
  // 结果态
  inFilter,
  searchDone,
  searchFailed,
  searching,
  mixedResults,
  // 动作
  loadHistory,
  removeHistory,
  clearHistory,
  tapKeyword,
  onSearchConfirm,
  clearKeyword,
  onRetrySearch,
  onLoadMoreResults,
  loadingMore,
  resultsFinished,
} = useFindState()
// 猜你喜欢属发现态数据源，独立于两态编排（见 useDiscover 的模块说明）
const { guessLikeList, load: loadDiscover } = useDiscover()

/** 固定标题带高（px）：带为 `position: fixed`，页面根层须用等量 padding 顶开内容 */
const { titleBandPx } = useNavMetrics()

/** 无结果标题的关键词展示截断：超过 10 字符取前 10 + `…`（与 `.history-chip-text` 省略口径同源） */
const emptyTitleKeyword = computed(() =>
  keyword.value.length > 10 ? `${keyword.value.slice(0, 10)}…` : keyword.value,
)

function onBack() {
  backToHome()
}

/** 搜索无结果引导 → 反馈页（落默认 issue 模式；落点唯一构造函数） */
function goContributeNotFound() {
  uni.navigateTo({ url: feedbackUrl() })
}

/** 结果点击 → 菜品详情（搜索仅菜品，无独立档口 / 食堂结果） */
function goToMixed(id: number) {
  if (!id) return
  uni.navigateTo({ url: dishDetailUrl(id) })
}

onMounted(() => {
  loadHistory()
  loadDiscover()
})

onShareAppMessage(() => buildSharePayload())
// 从菜品详情返回搜索页：清掉分享残留，避免右上角分享菜单沿用详情页内容；
// 同时重读搜索历史 —— 本页被页面栈缓存时 onMounted 不再执行，以存储为真源重读
// 可保证「搜索记录」始终最新（与 clearKeyword 时的重读互为兜底）。
onShow(() => {
  clearShareState()
  loadHistory()
})
</script>

<style scoped>
/* 页面根不带底色：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层 */
.find-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; overflow: hidden; box-sizing: border-box; }
.find-page { min-height: 0; }
/* `min-height: 0` 必需：全局 `.page` 兜底写了 `min-height: 100vh / 100dvh`，而移动端
   `100vh`（最大视口）通常 **大于** `100dvh`（当前视口）；二者同时存在时 min 胜出
   ⇒ 页根比可视区高出一截 ⇒ **页面本身**多出一段可滚区（内容并未超屏也会滚）。
   自带滚动容器的页根必须把 min-height 归零，把高度交给 `height: 100dvh` + 内部 scroll-view。 */
/* 搜索行宿主（搜索页 UI §2）：标题带下沿 → 搜索行上沿 = --spacing-md（同属「头部单元」）；
   搜索行下沿 → 内容首块 = --spacing-lg（块间）。搜索行左侧 gutter 由 SearchBar 内部自持（与首页同源） */
.find-search-row { padding-top: var(--spacing-md); padding-bottom: var(--spacing-lg); box-sizing: border-box; }
/* 内容区：占满 header 之外的剩余高度；两个分支**各自**自带滚动容器（发现态 discover-body / 结果态 results-host） */
.find-body { flex: 1; min-height: 0; display: flex; flex-direction: column; overflow: hidden; }
/* 发现态滚动容器：由 `<view>` + `overflow-y:auto` 改为 `scroll-view` ——
   小程序只保证 `scroll-view` 可滚动，`view` 自滚在 iOS / 部分安卓上不可靠，内容超高会被
   `overflow: hidden` 的页根裁掉且不可达。⚠️ `scroll-view` 自身**不写** `overflow-y`
   （滚动由组件内部实现，外挂 CSS 会在 H5 叠出第二根滚动条）。
   `flex: 1 + min-height: 0` ⇒ 容器定高 ⇒ 内容未超高时既不出现滚动条、也没有可滚的空白。 */
/* 底部 = 块间距 + `env(safe-area-inset-bottom)`：本页为非 TabBar 页，
   滚动区末块（分组卡）无安全区时会被 Home Indicator 压住 */
.discover-body { flex: 1; min-height: 0; padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }
/* 发现态模块容器：**flex column + gap** 承担模块之间的留白（小程序 WXSS 不支持 `+` 兄弟选择器），
   左右 gutter 在此；首块上间距的唯一来源 = 搜索行下 padding（UI 文档 §2：块间 `--spacing-lg`）。 */
.discover-modules {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  padding: 0 var(--page-gutter);
}
/* 结果态滚动容器：
   flex 链占满剩余高度；底部留白随容器自带（末行为结果卡 / `.list-foot`，
   故底部 = 块间距 + `env(safe-area-inset-bottom)`，避免压在 Home Indicator 下） */
.results-host {
  flex: 1;
  min-height: 0;
  padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom));
}
/* 结果列表：左右 gutter + 底部间距；卡间纵向间距用 **flex gap** ——
   不用 `+` 兄弟选择器（mp-weixin WXSS 不保证支持，本文件上方有登记）。
   Round 21e（用户拍板）：结果**顶部对齐**、自上而下自然阅读；空白读感由「搜索行常驻 +
   结果态与发现态/空态/失败态互斥」保证，不靠居中补偿。 */
.mixed-list {
  margin: 0 var(--page-gutter) var(--spacing-md);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
}

/* 空态 / 失败态**共用宿主**：
   只承担「占满内容区 + 顶部对齐」与边距，视觉分别由公共 `EmptyState`（卡片变体）/ `RetryBlock` 承担。 */
.state-host {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  /* 空态 / 失败态**顶部对齐**（不居中）：内容区上方即呈现，避免短文案被推到屏幕中部 */
  justify-content: flex-start;
  margin: var(--spacing-lg);
  box-sizing: border-box;
}

/* ---------- 词条 chip（搜索记录 / 猜你喜欢共用）----------
   两组 chip 同款形态，仅常态底色不同：记录 = 浅底、推荐 = 暖黄底。 */
.history-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
}
.history-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2xs);
  /* 触达基线：chip 视觉高度小于 88rpx 时用 min-height 兜到基线，`::after` 再扩命中盒 */
  min-height: var(--tap-target-size);
  padding: 0 var(--spacing-sm);
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  -webkit-tap-highlight-color: transparent;
}
/* 猜你喜欢 = 暖黄底深棕字（与搜索记录的浅底区分） */
.history-chip-hot { background: var(--bg-soft-yellow); }
/* 视觉底色延伸不到命中盒时，用伪元素把热区补齐到触达基线 */
.history-chip::after,
.history-clear::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: var(--tap-target-size);
  transform: translateY(-50%);
}
.history-chip,
.history-clear { position: relative; }
.history-chip-text { font-size: var(--font-small); color: var(--text-body); }
.history-chip-hot .history-chip-text { color: var(--text-primary); }
.history-chip-pressed { opacity: 0.7; }
/* 单条删除钮：视觉仅 24rpx 图标，靠 `::after` 把热区扩到触达基线 */
.history-chip-del {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--spacing-lg);
  height: var(--spacing-lg);
}
.history-chip-del::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: var(--tap-target-size);
  height: var(--tap-target-size);
  transform: translate(-50%, -50%);
}

/* 「清空」动作（SectionTitle 右上角 extra 槽）：文字按钮，触达同样由 `::after` 兜底 */
.section-extra {
  display: flex;
  align-items: center;
  min-height: var(--tap-target-size);
}
.history-clear-text { font-size: var(--font-small); color: var(--text-tertiary); }
</style>
