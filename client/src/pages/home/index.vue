<template>
  <view class="page home-page">
    <!-- ===== 常驻吸顶头部（两态结构：初始态 / 吸顶态**共用同一块**头部） =====
         实现口径（home-page-presentation）：**固定头部 + 既有 .scroll-wrap**，
         初始态顺序 = 标题「知行食记」→ 渐变 Banner（今日推荐）→ 通栏搜索框 → 大类标签栏 → 双列网格；
         吸顶态 = 标题 + 完整搜索框 + 大类标签栏常驻可见，Banner 不可见。
         ⚠️ 禁用 position: sticky（小程序基础库行为不一致），故全部定位走「固定头部 + 内部滚动」。
         Banner 不再放滚动内容里，而是落在头部内（标题行与搜索行之间），由滚动量驱动折叠收起 ——
         几何上等价于「Banner 随手势滚出」（推导见脚本区 BANNER_* 段注释）。 -->
    <view class="home-top">
      <!-- 头部：标题「知行食记」+ 通栏搜索框（内含右侧「筛选」）+ 大类标签栏，三行**恒定吸顶不位移**。
           Banner（默认 slot 注入）为**绝对定位图层**，垫在头部行背后：随内容 1:1 上滑，
           下缘抵达头部底缘后定格（home-scroll-interaction；观感同详情页顶部大图）。 -->
      <AppHeader
        variant="home"
        title="知行食记"
        search-placeholder="搜索菜品、食堂、套餐"
        :filter-label="filterButtonLabel"
        :filter-open="filterOpen"
        :search-shift="stickyShift"
        @search="goToSearch"
        @filter="toggleFilterPanel"
      >
        <!-- Banner 图层 = **纯图片**（或无图时的占位块），无任何文案层（2026-09-21 定稿：那个位置就是一张图）。
             背景图用 `<image>` **单独加载**；未配置 / 加载失败 → 占位块（灰底 + 餐具图标，与菜品占位同款）。 -->
        <view class="home-banner-layer" :style="{ height: bannerLayerH, transform: bannerShift }">
          <image
            v-if="bannerBgSrc !== '' && !bannerBgFailed"
            class="banner-bg"
            :src="bannerBgSrc"
            mode="aspectFill"
            @error="bannerBgFailed = true"
          />
          <view v-else class="banner-ph">
            <IconSvg name="dish" :size="120" :color="COLOR_MAP['text-tertiary']" />
            <text class="banner-ph-text">Banner 占位图</text>
          </view>
        </view>
      </AppHeader>

      <!-- 横向大类标签栏：与搜索卡同属**吸顶组**——初始态位于 Banner 下方，随页面刚体上移，
           跨锁定线后锁定在标题行之下（位移与搜索卡同源下发）；
           标签文案与顺序完全来自字典响应（GET /dishes/meal-types），字典不可用时降级为仅「全部」。 -->
      <view class="home-sticky-group" :style="{ transform: stickyShift }">
        <HomeMealTabs
          class="home-tabs"
          :items="dishStore.mealTypeList"
          :active-key="dishStore.filterMealType"
          @select="onMealTypeSelect"
        />
      </view>

      <!-- 筛选面板：白底、锚定「筛选」按钮正下方，含「全部」+ 各食堂 + 价格区间；
           与大类标签栏**可叠加**（三维度互不清除）；点击面板外关闭。 -->
      <HomeFilterPanel
        v-if="filterOpen"
        :canteens="dishStore.canteenList"
        :selected-canteen-id="selectedCanteenId"
        :price-range="dishStore.filterPrice"
        @close="closeFilterPanel"
        @canteen-select="onCanteenSelect"
        @price-select="onPriceSelect"
      />
    </view>

    <scroll-view
      class="scroll-wrap"
      scroll-y
      :scroll-top="scrollTopProp"
      :scroll-with-animation="false"
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @scroll="onScroll"
      @refresherrefresh="onRefresh"
      @scrolltolower="onScrollToLower"
    >
      <!-- 顶部补偿：与 Banner 折叠量等量（内联），抵消「头部变矮」带来的额外位移 → 两态切换无跳变 -->
      <view class="home-content" :style="{ paddingTop: contentPadTop }">
        <!-- 瀑布流：按所选食堂 / 大类 / 价格过滤；未选 = 全部（2026-09-21 走查：末尾贡献卡片已删除） -->
        <HomeContent @retry="retryWaterfall" />
      </view>
    </scroll-view>

    <!-- 底部常驻菜单栏：首页/我的 两主区切换（仅主根页显示） -->
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { onLoad, onShow, onReady, onShareAppMessage } from '@dcloudio/uni-app'
import { showTab } from '@/stores/route'
import { useDishStore } from '@/stores/dish'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import { PATH } from '@/utils/routes'
import { getNavBarHeight } from '@/utils/navMetrics'
import IconSvg from '@/components/IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'
import AppHeader from '@/components/AppHeader.vue'
import HomeMealTabs from './HomeMealTabs.vue'
import HomeFilterPanel from './HomeFilterPanel.vue'
import HomeContent from './HomeContent.vue'
import TabBar from '@/components/TabBar.vue'
import type { FilterTab } from '@/types/filter-tab'

const dishStore = useDishStore()

const refresherTriggered = ref(false)

/* ===== 两态结构：Banner 折叠（初始态 / 吸顶态） =====
   spec 要求初始态顺序 = 标题（**叠加于 Banner 背景之上**）→ Banner → 搜索框 → 标签栏 → 网格，吸顶态 = 标题 + 完整搜索框 + 标签栏常驻、
   Banner 不可见，且禁用 position: sticky。Banner 因此被放进常驻头部并**上移一个标题行高、垫在标题背后**，
   由 scroll-view 滚动位移 s 驱动「折叠收起」，并把滚动内容顶部同量下移补偿：

     · 头部内 Banner 可视高度 = H − s（H = Banner 整块高 = 标题行高 + 内容区高，夹在 [0, H]）
     · Banner 内容 translateY(−s)，被上述视口裁掉上缘 → 与「Banner 随手势滚出」逐像素等价
     · 滚动内容 padding-top = s → 抵消「头部变矮」多出来的位移；因「高度 + 补偿 ≡ H」，
       任意 s 下内容区屏幕位置 = Banner 放在滚动流里时的位置 → 两态切换连续、无跳变，回滚对称还原

   为何不写 transition/动效：折叠量必须与滚动量严格 1:1 同步（B + P ≡ H）。任何缓动都会让 Banner
   落后于手势，在过渡期露出瞬时空白带；滚动位移本身已是连续量，故本方案无 CSS 动画/过渡，
   也就不存在需要 prefers-reduced-motion 降级的离散动效。
   ⚠️ Banner 图层（2026-09-21 定稿，详情页大图同款观感）：**绝对定位**垫在头部行背后（z:0），
  随滚动 1:1 上移（transform 由本段驱动）；**定格位移 s\* = 图层高 − 吸顶头部实测高**——
  上滑至 s* 后图层底缘恰抵头部底缘，此后定格为头部背景层，仅菜品列表继续滚动。
  头部行（标题 / 搜索卡 / 标签栏）恒定吸顶不位移。改内容区高度须同步 BANNER_CONTENT_RPX。 */
  const BANNER_CONTENT_RPX = 284
  /** 状态栏高（px）：与 AppHeader 同源（--status-h 同值），计入图层高 */
  const statusBarPx = ref(20)
  /** 标题行高（px）：与 AppHeader 的 navBarHeight 同源换算（navMetrics 真源），计入图层高 */
  const navBarHeightPx = ref(56)
  onMounted(() => {
  // 与 AppHeader 同口径：兼容老基础库取 statusBarHeight，微信端按胶囊位置换算导航行高
  // @ts-ignore - 跨端兼容（H5 无 wx，退化为固定值）
  const win = (typeof wx !== 'undefined')
    // @ts-ignore
    ? (wx.getWindowInfo ? wx.getWindowInfo() : (wx.getSystemInfoSync ? wx.getSystemInfoSync() : null))
    : null
  const sb = (win && win.statusBarHeight) || 20
  statusBarPx.value = sb
  // @ts-ignore - 微信胶囊按钮位置（右上角原生组件）
  const mb = (typeof wx !== 'undefined' && wx.getMenuButtonBoundingClientRect) ? wx.getMenuButtonBoundingClientRect() : null
  navBarHeightPx.value = getNavBarHeight(sb, mb)
  })
  /** Banner 图层高（px）= 状态栏高 + 标题行高 + 内容区高：与滚动位移同单位（uni.upx2px 按窗口宽折算，随设备自适应） */
  const bannerHeightPx = computed(() => statusBarPx.value + navBarHeightPx.value + uni.upx2px(BANNER_CONTENT_RPX))
  /** 图层高样式值 */
  const bannerLayerH = computed(() => toPx(bannerHeightPx.value))

  /** Banner 背景图（**单独加载**）：正式资产到位后把 URL / 本地路径填入本常量即可；
    为空或加载失败时显示占位块（灰底 + 餐具图标，与菜品占位同款）。 */
  const BANNER_BG_SRC = ''
  const bannerBgFailed = ref(false)

/** 滚动位移（px，负值/回弹一律归零）——页面侧唯一滚动真源，只驱动 Banner 折叠，不参与列表分页 */
const scrollTop = ref(0)

/** 吸顶头部总高（px）：onReady 实测 .home-top（标题行 + 搜索卡 + 标签栏），定格线真源 */
const headerHpx = ref(0)
onReady(() => {
  uni.createSelectorQuery()
    .select('.home-top')
    .boundingClientRect((rect: { height?: number } | null) => {
      if (rect && rect.height) headerHpx.value = rect.height
    })
    .exec()
})

/** 标题带高（px）= 状态栏 + 标题行：吸顶组（搜索卡 + 标签栏）的锁定位置真源 */
const titleBandPx = computed(() => statusBarPx.value + navBarHeightPx.value)
/** 定格位移（px）= 图层高 − 头部实测高：上滑至此，图层底缘恰抵头部底缘，此后定格为头部背景层。
    headerH 未实测前用估算值（标题带 + 搜索卡/标签栏 ≈ 176rpx）兜底 */
const bannerStopPx = computed(() =>
  Math.max(bannerHeightPx.value - (headerHpx.value || titleBandPx.value + uni.upx2px(176)), 0),
)
/** 上移量（px）：夹在 [0, 定格位移] —— 越过定格线后 Banner 保持定格，仅菜品列表继续滚动 */
const bannerCollapsedPx = computed(() =>
  Math.min(Math.max(scrollTop.value, 0), bannerStopPx.value),
)

/** px → 样式值（2 位小数）：避免浮点长尾进入内联样式，并让重复值不触发无谓的 setData */
function toPx(value: number): string {
  return `${Math.round(value * 100) / 100}px`
}

/** Banner 图层上移量：随滚动 1:1 刚体上移（详情页大图观感），至定格线停止 */
const bannerShift = computed(() => `translateY(${toPx(-bannerCollapsedPx.value)})`)

/** 吸顶组锁定位移（px）= 图层高 − 标题带高：上滑至此，搜索卡 / 标签栏升到锁定位置（标题行之下） */
const stickyLockPx = computed(() => Math.max(bannerHeightPx.value - titleBandPx.value, 0))
/**
 * 吸顶组（搜索卡 + 标签栏）位移：**初始态整组位于 Banner 下方**（下移 stickyLockPx），
 * 随页面刚体上移（与 Banner、列表同步 1:1），跨锁定线后归零并锁定在标题行之下 ——
 * 即文档 §5「初始态四者属页面正常流、滑动时整体刚体同步上移、跨阈值后组合成粘性吸顶容器」。
 */
const stickyShift = computed(() => toPx(Math.max(stickyLockPx.value - Math.max(scrollTop.value, 0), 0)))
/** 滚动内容顶部留白：= 锁定位移 —— 初始态网格恰从标签栏底缘起排，上滑后随内容 1:1 同步上移 */
const contentPadTop = computed(() => toPx(stickyLockPx.value))

/** 平台例外：uni scroll-view 滚动回调未纳入项目 TS 类型，只声明真正读取的字段（MP-08 口径，替代裸 any） */
function onScroll(e: { detail?: { scrollTop?: number } }) {
  const top = e?.detail?.scrollTop ?? 0
  scrollTop.value = top > 0 ? top : 0
}

/* ===== 下拉刷新回顶（change home-scroll-interaction；取代原 D6「筛选变更回顶」） =====
   定稿交互（功能文档 §5 边界 4）：下拉刷新强制重置滚动位置到顶部，回到【初始态，Banner 完整展示】。
   相应地，筛选变更（大类 / 食堂 / 价格）**不再回顶**——仅刷新列表，当前吸顶 / 初始态保持不变
   （原「筛选变更一律回顶」约定随本 change 废止）。

   实现：scroll-view 没有对外 scrollTo 方法，只能用受控 `scroll-top` 属性驱动，且该属性「值不变即不滚动」，
   故回顶是一枚脉冲：0 → 1 →（下一帧）0。用常量 1 而非「当前滚动位置」是有意的——
   结果集变短时 scroll-view 会自行把位置夹到顶部且**不一定派发 @scroll**，此时页面侧 scrollTop 可能仍是旧值，
   用它当跳板反而会把列表滚下去。

   折叠量则**立即**归 0（不依赖 @scroll 回调）：同样因为上述「不派发回调」的场景，
   若只靠回调，短列表下头部会卡在折叠态；立即归零让「头部展开」与「内容回顶」两个信号必定同时生效。
   代价仅是一帧内头部已展开而内容尚未到位（无缓动、无闪烁，人眼不可辨）。 */
const scrollTopProp = ref(0)

/** 下拉刷新回顶：滚动位置归零 + 折叠量立即归 0 → 回到初始态（仅下拉刷新一个消费方） */
function resetScrollToTop() {
  scrollTop.value = 0
  scrollTopProp.value = 1
  nextTick(() => {
    scrollTopProp.value = 0
  })
}

/** 筛选面板展开态（页面侧唯一真源；面板本身受控，不自行持态） */
const filterOpen = ref(false)

/**
 * 选择价格区间：写回 store 并刷新当前筛选流（区间单位为元，透传 api 层统一转分，无新契约）。
 * home-scroll-interaction：筛选变更**不重置滚动位置**——保持当前吸顶 / 初始态，仅换结果集。
 */
async function onPriceSelect(range: { min?: number; max?: number }) {
  await dishStore.setHomePrice(range)
}

/**
 * 切换菜品大类标签（任务 3.3）：store 内重置分页并刷新当前筛选流；
 * 食堂 / 价格两个维度原样保留（叠加生效，互不清除）。
 * home-scroll-interaction：点标签仅刷新列表，**不重置滚动位置**（吸顶态保持吸顶）。
 */
async function onMealTypeSelect(key: string | null) {
  await dishStore.setHomeMealType(key)
}

/**
 * 当前选中食堂 id（null = 全部）——MP-03：**由 store 的 filterTab 派生**，页面不再自持一份。
 * 此前页面 selectedCanteenId 与 store filterTab.canteenId 是两个真源：
 * 下拉选项选中态读前者、列表请求读后者，二者在「清除筛选 / 首屏 ensureBoot」等路径上会不一致
 * （如 filterTab 已被换掉而页面 ref 未同步 → 胶囊回显与内容不匹配）。
 */
const selectedCanteenId = computed<number | null>(() => {
  const tab = dishStore.filterTab
  return tab && tab.type === 'canteen' && tab.canteenId != null ? tab.canteenId : null
})

/** 按 id 取食堂名（选中态派生后，构造 canteen tab 时不能再读「尚未更新的派生值」） */
function canteenNameOf(id: number | null): string {
  if (id == null) return ''
  return dishStore.canteenList.find((c) => c.id === id)?.name || ''
}
const selectedCanteenName = computed(() => canteenNameOf(selectedCanteenId.value))

/**
 * 「筛选」按钮文案：已选食堂时回显食堂名（省略号只作用于该按钮），未选时为「筛选」。
 * 见 home-filter spec「长食堂名完整显示」：按钮文案不得挤压搜索框占位文案。
 */
const filterButtonLabel = computed(() => selectedCanteenName.value || '筛选')

function canteenTab(id: number, name: string): FilterTab {
  return { key: `canteen-${id}`, label: name, type: 'canteen', canteenId: id }
}

/** 首拉：食品列表就绪后默认加载「全部」（热度流）；返回 Promise 供「首屏渲染后」时机串接 */
let bootstrapped = false
async function ensureBoot() {
  if (bootstrapped) return
  bootstrapped = true
  await dishStore.fetchFilterDishes(dishStore.defaultFilterTab(), true)
}
watch(
  () => dishStore.canteenList.length,
  () => ensureBoot(),
  { immediate: true },
)

/**
 * 食堂筛选：只按该食堂刷新筛选流（面板显隐由页面 filterOpen 受控，面板内选择即关闭）。
 * MP-03：选中态不再写页面本地 ref —— fetchFilterDishes 会同步写入 filterTab，
 * 上面的 selectedCanteenId 由它派生，按钮回显与列表条件天然同源。
 */
function onCanteenSelect(id: number | null) {
  const tab = id == null ? dishStore.defaultFilterTab() : canteenTab(id, canteenNameOf(id) || '食堂')
  dishStore.fetchFilterDishes(tab, true) // 筛选变更不回顶（home-scroll-interaction）
}

/** 展开 / 收起筛选面板；展开时若食堂字典尚未就绪则先补拉（spec：面板展开前补拉） */
function toggleFilterPanel() {
  filterOpen.value = !filterOpen.value
  if (filterOpen.value && dishStore.canteenList.length === 0) {
    void dishStore.fetchCanteens()
  }
}

function closeFilterPanel() {
  filterOpen.value = false
}

function goToSearch() {
  uni.navigateTo({ url: PATH.find })
}

/** 重试当前筛选流：下拉刷新复用同一条重拉路径（食堂列表缺失时先补拉）。
 *  ⚠️ 本路径**不回顶**（D6 例外）：下拉刷新 / 失败重试属于「同条件重拉」，
 *  用户此刻正停在顶部下拉，若再回顶会打断手势；回顶只发生在筛选条件变更时。 */
async function retryWaterfall() {
  if (dishStore.canteenList.length === 0) {
    await dishStore.fetchCanteens()
  }
  // 重试当前生效的筛选流：filterTab 是唯一真源，缺失时退回默认热度流（MP-03）
  const tab = dishStore.filterTab ?? dishStore.defaultFilterTab()
  dishStore.fetchFilterDishes(tab, true)
}

function onScrollToLower() {
  dishStore.loadMoreFilterDishes()
}

/** 下拉刷新：刷新数据的同时强制回顶（home-scroll-interaction 边界 4）→ 回到初始态、Banner 完整展示 */
async function onRefresh() {
  refresherTriggered.value = true
  resetScrollToTop()
  await retryWaterfall()
  refresherTriggered.value = false
}

function loadData() {
  // 首页仅加载食品列表；确保食堂列表就绪（筛选面板依赖 canteenList）
  if (dishStore.canteenList.length === 0) dishStore.fetchCanteens()
  // 大类字典：**不 await**（失败降级为仅「全部」），保证首屏列表不被字典阻塞（任务 3.1）
  void dishStore.fetchMealTypes()
}

onLoad(() => {
  loadData()
})

onShow(() => {
  // 锚定底部菜单栏：首页始终显示并高亮（页面已就绪，最可靠时机）
  showTab('home')
  // 返回首页清理分享态，避免无限循环（uni 分享机制硬限制）
  clearShareState()
  // 兜底：若首屏因遮挡/竞态未拉起，再次确保
  if (!bootstrapped) ensureBoot()
  // 大类字典兜底重试：仅「从未成功」时才发请求（store 内自带守卫），失败不阻塞首屏
  void dishStore.fetchMealTypes()
  // 食堂字典最小失效机制：进程常驻期间回首页按节流窗口后台重拉（失败保留旧列表），
  // 保证管理端改食堂/档口名后最终可见（spec §7.7 附加核查）；内部自带节流与去重，onShow 高频触发安全
  void dishStore.refreshCanteensIfStale()
})

onShareAppMessage(() => {
  return buildSharePayload()
})
</script>

<style scoped lang="scss">
/* 页面：顶部「浅米白 → 淡橙」渐变（token: --bg-page-grad-*，2026-09-21 §7.34 G5），
   渐变仅覆盖首屏高度，其余回落页面底色（--bg-page）。 */
.home-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--bg-page);
  background-image: linear-gradient(180deg, var(--bg-page-grad-from) 0%, var(--bg-page-grad-to) 420rpx, var(--bg-page) 720rpx);
  position: relative;
  overflow: hidden;
}
/* 常驻头部容器：承载 Header（标题 + 搜索框）与「筛选」面板。
   z-index 必须高于下方大类标签栏，面板（绝对定位挂在本容器下）才能盖住标签栏。 */
.home-top {
  position: relative;
  z-index: 30;
}
/* 大类标签栏：属于吸顶头部的一部分（常驻），落在渐变底色区、与白色搜索卡明度可区分；
   z-index 低于 .home-top，使展开的筛选面板盖在标签栏之上。 */
.home-tabs {
  /* 相对定位：绘制层级高于 Banner 绝对图层（图层垫在头部行背后） */
  position: relative;
  position: relative;
  z-index: 20;
}
.scroll-wrap {
  flex: 1;
  width: 100%;
  box-sizing: border-box;
  min-height: 0;
  /* 预留底部菜单栏高度，避免内容被 TabBar 遮挡；不再叠加 spacing-lg（否则最后卡片/触底加载区会悬空在 TabBar 上方，与 dynamic 页口径统一为 tabbar-height + env） */
  padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
}
.home-content {
  padding: 0;
  /* 顶部留白（padding-top）= 定格位移（脚本常量内联）：初始态网格恰从 Banner 底缘起排，
     上滑后随内容 1:1 同步上移（home-scroll-interaction：详情页大图同款观感） */
}
/* ===== Banner 图层（静态运营位；绝对定位垫在头部行背后，随滚动 1:1 刚体上移） =====
   · 图层：absolute 顶到屏幕最顶（.header-wrap 定位上下文的盒顶），高 = 状态栏 + 标题行 + 284rpx（脚本同源）；
     transform 位移由脚本内联（夹在 [0, 定格位移]），下缘抵达头部底缘（.home-top 盒底）后定格为头部背景层；
   · 头部行（标题 / 搜索卡 / 标签栏）position:relative 且后于图层绘制 → 恒在图层之上；
   · **纯图片 / 占位块，无文案层**（2026-09-21 定稿：那个位置就是一张图）。 */
.home-banner-layer {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 0;
  overflow: hidden;
  background: var(--bg-soft);
}
/* 背景图层：绝对定位铺满（单独加载，非组件手绘） */
.banner-bg {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}
/* 占位块：居中餐具图标 + 说明文字（与 DishCard.image-placeholder 同语言，尺寸放大适配 Banner） */
.banner-ph {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
}
.banner-ph-text {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}
/* 注：Banner 图层与吸顶组的位移均为「滚动位置驱动」的几何跟随（非装饰性动画），
   故不对 prefers-reduced-motion 做降级——禁用位移会让头部与滚动内容脱节。 */
</style>
