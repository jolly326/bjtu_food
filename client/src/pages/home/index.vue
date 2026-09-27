<template>
  <!-- 页面骨架（§11 结构性决议「回到原布局 + 磨砂」，2026-09-27）：`.home-page` 是一个 **flex 列**——
       `padding-top` = 固定标题带高、`padding-bottom` = 菜单栏 + 安全区，中间是**滚动区**（`flex: 1`），
       滚动区内含：Banner（首块）→ **吸顶容器（搜索区 + 大类标签栏，一个组件）** → 双列网格。
       ⇒ **标题带不需要任何表面**：滚动区起点就在它下沿，内容不从它背后经过；
       ⇒ **吸顶容器是唯一需要表面的一条**：卡片会从它背后滚过 ——
       未吸顶时**完全透明**，吸顶后铺**背景图的原样切片**（位置 = 背景图**去掉顶部标题带那一段**，
       即与页底逐像素同源；偏移基准取**实测**滚动区顶边，消除 1–2px 误差 —— 详见 §11.1）。 -->
  <view class="page home-page" :style="pageStyle">
    <!-- ===== 壁纸层（UI 文档 §11.1）：本地壁纸 + 纱，`fixed` **视口锚定**、铺满整屏、不随内容滚动 =====
         实现 = 公共组件 `components/PageWallpaper.vue`（§12）：本地图必须由 `<image>` 渲染
         （小程序 WXSS `background-image` 取不到包内本地路径）。
         · 这是**页面级**壁纸层（`fixed`、铺满视口、不随内容滚动）；
         · ⚠️ 本页另有一处**容器内切片**（吸顶容器里的 `.home-sticky-slice`，见下方）：它**不是**多余拷贝，
           而是"卡片会从吸顶容器背后滚过"时唯一能遮挡的表面；未吸顶时该切片不渲染 ⇒ 与页底天然连续。 -->
    <PageWallpaper fixed :height-px="viewportHeightPx" />

    <!-- ===== 固定标题带（跨页统一，docs/ui/client-首页菜品浏览.md §1） =====
         · `position: fixed` **永久固定在页面左上角**，不随页面滚动移动、不随 Banner 滚出；
         · 与微信右上角**原生胶囊同一条水平线**（行高 = 胶囊高、垂直中心对齐），右侧按胶囊避让；
         · 文案**按页配置**（首页 = 「知行食记」，搜索页等各填自己的），位置 / 高度 / 对齐 / 配色跨页一致；
         · 纯文本、无点击行为；层叠高于 Banner 与网格；
         · ⚠️ **恒透明、不铺任何表面**（2026-09-27 结构性决议）：滚动区已从工具栏下沿开始，
           **没有内容会从标题带背后穿过**，故带背后直接露出 `fixed` 页底壁纸即可。 -->
    <AppTitleBand title="知行食记" />

    <!-- 滚动容器：**不受控**（无 `:scroll-top` / `:scroll-with-animation`）。
         · 它是 flex 列里唯一 `flex: 1` 的行 ⇒ 顶边 = **固定标题带下沿**（页面 `padding-top` 让出）、
           底边 = 菜单栏上沿（页面 `padding-bottom` 让出）；
         · 内含：Banner（首块，**滚出即在标题带下沿被裁**）→ 吸顶容器（搜索区 + 标签栏）→ 双列网格；
         · 数据更新走「首屏拉取（onLoad）+ onShow 兜底重拉 + 失败重试块（HomeContent 内）」三条既有路径；
           不强制回顶。 -->
    <scroll-view
      class="scroll-wrap"
      scroll-y
      :lower-threshold="LOWER_THRESHOLD_PX"
      @scroll="onScroll"
      @scrolltolower="onScrollToLower"
    >
      <view class="home-scroll-body">
        <!-- ===== Banner：滚动区首块，**四周留白的圆角图片卡**（§3.1）=====
             实现已抽入 `pages/home/HomeBanner.vue`（§12 组件拆分）：`GET /banners` 数据加载 /
             多张自动轮播 + 指示点 / 空与单张失败「灰底 + 中性 empty」空态。
             左右 12px 边距与四角圆角在组件内；上间距（标题带下沿 → Banner 上缘 12px）
             由 `.home-scroll-body` 的 padding-top 承担；块高由本页下发（16:10，§3.3）。 -->
        <HomeBanner :height-px="bannerHeightPx" />

        <!-- ===== 吸顶容器（搜索区 + 横向大类标签栏）：**一个组件、一起吸顶** =====
             · **原生粘性定位**（`position: sticky` + `top: 0` = 滚动区顶 = 固定标题带下沿）：
               位移完全由渲染层原生滚动驱动 —— **不监听滚动、不做逐帧对齐、无任何状态**；
             · 表面 = **背景图的原样切片**（2026-09-27 决议：放弃磨砂，改切片）：
               **未吸顶 = 完全透明**；跨过锁定点吸顶后**立即铺上切片**——
               切片位置 = **背景图去掉顶部标题带那一段**（内层按实测基准上移，使切片盒子落回视口原点
               ⇒ 显示的就是"该位置本来那一段壁纸"，与页底逐像素同源）；
               因为吸顶后容器顶边是**常量**，这里只按常量偏移 ⇒ **不逐帧采样** ⇒ 不滞后 1–2 帧 ⇒ 不撕裂；
             · 纵向间距（§7.1）：上 padding = Banner 下缘 → 搜索区 16px；
               搜索区 ↔ 标签栏由 `.mt-tab` 内偏置（24rpx = 12px）承担；
               下 padding 8px **+ 标签行自带 ≈8px 行底余量** = 标签栏 → 网格 ≈16px。 -->
        <view class="home-sticky">
          <!-- 吸顶态表面层：外层裁切 + 内层壁纸（按 `sliceStyle` 贴回视口原点）。
               · 只在**吸顶态**渲染（未吸顶时容器完全透明）；
               · 内层用**页底同款壁纸**（同 src ⇒ 命中缓存、无额外请求），盒高同为实测视口高
                 ⇒ `aspectFill` 裁剪与页底**逐像素同源**；
               ⚠️ 内层必须是 `absolute`：`fixed` 会逃出本层的 `overflow: hidden`、直接铺满整屏。 -->
          <view v-if="pinned" class="home-sticky-slice">
            <!-- 偏移挂在**本页自己的节点**上（不依赖父组件 style 透传到子组件根——那条链路若失效，
                 偏移就会变成 0，切片会取到背景图**最顶上**那一段，看起来就是"偏高"）。
                 内层壁纸按原样铺满一屏 ⇒ `aspectFill` 裁剪与页底逐像素同源。 -->
            <view class="home-sticky-slice-offset" :style="sliceStyle">
              <PageWallpaper :height-px="viewportHeightPx" />
            </view>
          </view>

          <!-- 搜索行：与搜索页同源（`SearchBar`）
               —— 左搜索胶囊 + 右独立「搜索」按钮，均为进搜索页的入口 -->
          <SearchBar class="home-search" mode="entry" @tap="goToSearch" />

          <!-- 横向大类标签栏：与搜索区同属本吸顶容器（一个组件）；
               标签集合与文案完全来自字典（GET /dishes/meal-types）。 -->
          <HomeMealTabs
            class="home-tabs"
            :items="dishStore.mealTypeList"
            :active-value="dishStore.filterMealType"
            @select="onMealTypeSelect"
          />
        </view>

        <!-- 双列瀑布流（未选大类 = 推荐流·会话种子伪随机序；选中大类 = 该类热度序） -->
        <HomeContent @retry="retryWaterfall" />
      </view>
    </scroll-view>

    <!-- 底部常驻菜单栏：首页 / 我的 两主区切换（仅主根页显示）。
         菜单栏**恒透明**（背后即 `fixed` 页底壁纸；UI 统一 Loop Round 5 起为唯一行为，不再有 `wallpaper` 开关）；
         滚动区底边已在它**上沿**（页面 `padding-bottom` 让出）⇒ 卡片不会从它背后滚过。 -->
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onLoad, onShow, onShareAppMessage } from '@dcloudio/uni-app'
import { showTab } from '@/stores/route'
import { useDishStore } from '@/stores/dish'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import { PATH } from '@/utils/routes'
import { useNavMetrics } from '@/utils/useNavMetrics'
import { getWindowInfo } from '@/utils/device'
import AppTitleBand from '@/components/AppTitleBand.vue'
import SearchBar from '@/components/SearchBar.vue'
import HomeBanner from './HomeBanner.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import HomeMealTabs from './HomeMealTabs.vue'
import HomeContent from './HomeContent.vue'
import TabBar from '@/components/TabBar.vue'

const dishStore = useDishStore()

/** Banner 宽高比锁定 **16:10**（UI 文档 §3.3）：素材必须同比例出图，混比例会导致切换时块高抖动、吸顶阈值漂移 */
const BANNER_ASPECT_RATIO = 10 / 16
/** Banner 与屏幕**左右缘**的间距（px）：与页面级 gutter `--spacing-md` 同值（§3.1 四周留白） */
const BANNER_GUTTER_PX = 12
/** Banner 最小高度兜底（px）：窄屏下不至于压成一条；**不再叠加标题带高**（Banner 已是独立图片卡，§3.3） */
const BANNER_MIN_HEIGHT_PX = 160
/**
 * 内容区**宽屏限宽**（px）：与 `App.vue` 的 `@media (min-width: 768px) { .scroll-wrap { max-width: 720px } }` **同源**。
 * ⚠️ Banner 在滚动区**内部**，其实际宽度受该 CSS 限宽约束；定高若仍按**满屏宽**推导，比例会失真：
 * 1024 宽窗口 → 卡片实际 696 × JS 给 625 ≈ **1.11:1**（近正方）；1440 宽 → ≈ **0.79:1**（竖图）。
 * 改 `App.vue` 的限宽值时**必须同步本常量**。
 */
const CONTENT_MAX_WIDTH_PX = 720
/**
 * 触底提前量（px）：距底部还有该距离时就触发加载更多。
 * 默认 50px 会让用户「滚到底再等」，提前量把网络时延藏进滚动过程里（无限滚动更顺）。
 */
const LOWER_THRESHOLD_PX = 300

/* ===== 顶部度量（跨页统一实现，`useNavMetrics`）=====
   状态栏高 / 导航行高 / 胶囊高 / 胶囊避让量一律从该 composable 取——**页面不再自算**
   （`client-page-structure`：页面 SHALL NOT 各自计算导航尺寸）。本页只消费 `titleBandPx`：
   用它给页面加 `padding-top`，把常驻工具栏与滚动区整体压到固定标题带之下；
   标题带内部的居中与避让由 `AppTitleBand` 自持、搜索行高度由 `SearchBar` 自持。 */
const { titleBandPx } = useNavMetrics()
/** 窗口宽（px）：Banner 16:10 定高用（页面自持，与胶囊度量无关） */
const windowWidthPx = ref(375)
/**
 * 视口高（px）：**页面级**壁纸层的盒子高度（另有一处吸顶容器内的切片复用同一 `src` 与同一个值，
 * 以保证两边的 `aspectFill` 裁剪逐像素同源）。
 * ⚠️ 用**实测值**而不是 `vh`：`vh` 在部分机型上取整偏差会让壁纸铺不满 / 与视口对不齐。
 */
const viewportHeightPx = ref(812)

onMounted(() => {
  // 平台取值统一走 `utils/device`（Round 17：本文件不再触碰全局 `wx`，故无 `@ts-ignore`）
  const win = getWindowInfo()
  windowWidthPx.value = (win && win.windowWidth) || 375
  viewportHeightPx.value = (win && win.windowHeight) || 812
})

/** 内容实际可用宽（px）= `min(屏宽, 宽屏限宽)`——Banner 卡宽与定高都基于它（≥768px 窗口下即 720） */
const contentWidthPx = computed(() => Math.min(windowWidthPx.value, CONTENT_MAX_WIDTH_PX))
/** Banner **卡片宽**（px）= 内容可用宽 − 左右各 12px（§3.1 四周留白） */
const bannerWidthPx = computed(() => contentWidthPx.value - BANNER_GUTTER_PX * 2)

/**
 * Banner 总高（px）= `max(卡片宽 × 10/16, 最小高度兜底)`，**卡片宽 = min(屏宽, 720) − 左右各 12px**（§3.1 四周留白）。
 * ⚠️ 兜底项**不再**叠加「标题带高 + 运营最小可视高」：Banner 已是**独立图片卡**、不在标题带背后，
 * 标题带不占用它的高度；若继续相加，主流机型会从 16:10 被顶到 ≈1.4:1，**按 16:10 出的素材会被裁两侧**。
 * 375 宽：卡片宽 351 → max(219, 160) = **219**（正是 16:10）；仅窄屏（如 320：卡片宽 296 → 185）才由兜底接管。
 * ≥768 宽：卡片宽被 CSS 限宽夹到 **696** → max(435, 160) = **435**（仍是 16:10，不再随窗口继续拉高）。
 */
const bannerHeightPx = computed(() => Math.max(
  Math.round(bannerWidthPx.value * BANNER_ASPECT_RATIO),
  BANNER_MIN_HEIGHT_PX,
))

/* ===== Banner 数据由其自身组件 `HomeBanner.vue` 拉取（§12 组件拆分）；本页只下发块高 ===== */

/**
 * 页面骨架内联样式（§11 结构性决议，2026-09-27）：
 * · `height` = **实测视口高**（不用 `vh`：部分机型取整偏差会让底行露白）；
 * · `padding-top` = 固定标题带高 → 把常驻工具栏与滚动区整体压到标题带之下；
 * · `padding-bottom` = 菜单栏 + 底部安全区 → 滚动区底边落在菜单栏**上沿**，
 *   于是卡片既不会停在菜单栏背后，**滚动时也不会从它背后穿过**。
 * 三者配合 ⇒ 全页可视区被切成固定的三段（标题带 / 工具栏 / 滚动区），**任何横条背后都没有内容经过**
 * ⇒ **标题带**背后没有内容经过；但**吸顶容器**（搜索区 + 标签栏）背后有卡片滚过 ⇒
 * 它是全页唯一需要表面的一条（吸顶后铺背景图切片），由 `@scroll` 驱动离散开关 —— 详见 §11.1。
 */
const pageStyle = computed(() => ({
  height: `${viewportHeightPx.value}px`,
  paddingTop: `${titleBandPx.value}px`,
  paddingBottom: 'calc(var(--tabbar-height) + env(safe-area-inset-bottom))',
}))

/** Banner **上缘**与固定标题带下沿的间距（px）：§3.1（滚动区 `padding-top`） */
const BANNER_TOP_GAP_PX = 12
/** 吸顶锁定所需的滚动距离（px）= `H_gap + H_b`（§11 常量 `L`，≈231）：容器流内落点即 Banner 下缘 */
const lockScrollPx = computed(() => BANNER_TOP_GAP_PX + bannerHeightPx.value)

/* ===== 切片显隐：**只在吸顶态铺**（2026-09-27 决议）=====
   未吸顶 ⇒ 完全透明（此时容器背后就是页底壁纸本体，天然连续、无接缝）；
   跨过锁定点 ⇒ 立即铺上「背景图去掉顶部标题带那一段」的切片，挡住从容器背后滚过的卡片。 */
const pinned = ref(false)

/** 平台例外：uni scroll-view 滚动回调未纳入项目 TS 类型，只声明真正读取的字段 */
function onScroll(e: { detail?: { scrollTop?: number } }) {
  const top = e?.detail?.scrollTop ?? 0
  // 量化到整数 px + 「值未变则不写」：避免亚像素抖动触发无意义的样式下发
  const next = Math.round(top) >= lockScrollPx.value
  if (next === pinned.value) return
  pinned.value = next
}

/**
 * 切片偏移微调（px）：**正值 = 顶部多留 ⇒ 切片内容更靠下**；负值 = 往上取。
 *
 * 语义（2026-09-27 决议）：切片显示「背景图**去掉顶部一个 AppTitleBand 高度**之后的那一段」——
 * 基准 = 实测滚动区顶边（= 标题带下沿）⇒ 切片内容的起点正好落在**标题带下沿**，其上那一条（= 标题带高）
 * 被跳过，不与标题带抢同一段画面。若真机上仍判"偏高"，只调这一个数（如 `+12` / `+24`）即可，不必改结构。
 */
const SLICE_OFFSET_TUNE_PX = 0

/**
 * 切片偏移基准（px）= **标题带高 `titleBandPx`** ＋ 微调量。
 *
 * ⚠️ 为什么直接用 `titleBandPx`（而不是另测一次"滚动区顶边"）：
 *   · `titleBandPx` 同时就是 ① `AppTitleBand` 的 `height`（`bandStyle`）② 页面的 `padding-top`
 *     （= 滚动区顶边）③ 吸顶后容器的顶边 ⇒ **三者同源**，取它即可精确对齐，无需任何二次测量；
 *   · 曾额外用 `createSelectorQuery` 量过一次滚动区顶边（理论值应与 `titleBandPx` 相等），
 *     但引入了异步查询 + 回退分支 + 时序风险 —— **很可能就是"偏高"的元凶**，故已删除。
 *
 * 注：`titleBandPx` **不是写死的常量**（各机型状态栏 + 胶囊高度不同：iPhone SE / 14 Pro / 安卓各异），
 * 但由 `useNavMetrics()` 统一实测 ⇒ 每台设备上是**确定值**，且页面 SHALL NOT 自算导航尺寸。
 */
const sliceBasePx = computed(() => titleBandPx.value + SLICE_OFFSET_TUNE_PX)

/**
 * 吸顶容器**切片内层壁纸**的视口对齐修正（§11.1）——让它与页底壁纸层的盒子**完全重合**：
 * · `top` = `−(滚动区视口顶边)`：上移后内层盒子正好落回**视口原点** ⇒
 *   切片显示的就是"背景图去掉顶部标题带那一段"里、**本容器所在的那一条**，与页底逐像素同源；
 *   基准是**常量**（吸顶后位置恒定）⇒ 不逐帧采样 ⇒ 不滞后 1–2 帧 ⇒ 不撕裂；
 * · `left` / `width`：宽屏（H5 / 桌面）下 `.scroll-wrap` 被限宽并居中，而页底壁纸层是**满窗宽**
 *   ⇒ 把内层也摊成「满窗宽 + 反向偏移」，否则两边 `aspectFill` 裁剪不同源（画面对不齐）。
 */
const sliceStyle = computed(() => {
  const bandLeftPx = Math.max(0, (windowWidthPx.value - CONTENT_MAX_WIDTH_PX) / 2)
  return {
    top: `-${sliceBasePx.value}px`,
    left: `-${bandLeftPx}px`,
    width: `${windowWidthPx.value}px`,
  }
})

/** 切换大类：写回 store（内部重置分页并刷新列表）；**不重置滚动位置**，保持当前吸顶 / 初始态 */
async function onMealTypeSelect(value: string | null) {
  await dishStore.setHomeMealType(value)
}

/** 搜索入口：搜索胶囊与右侧「搜索」按钮共用（均进搜索页 A-03） */
function goToSearch() {
  uni.navigateTo({ url: PATH.find })
}

/** 列表失败重试：走与首屏同一条重拉路径 */
async function retryWaterfall() {
  await dishStore.fetchHomeDishes(true)
}

function onScrollToLower() {
  // `void`：滚动事件回调不消费 Promise（失败态与页码回退由 store 内部处理）
  void dishStore.loadMoreHomeDishes()
}

onLoad(() => {
  // Banner 自持数据加载（`HomeBanner.vue` 挂载时发起），与列表**并行**：Banner 失败不阻塞首屏网格
  // 大类字典：不 await（失败降级为仅「全部」），保证首屏列表不被字典阻塞
  void dishStore.fetchMealTypes()
  void dishStore.fetchHomeDishes(true)
})

onShow(() => {
  showTab('home')
  clearShareState()
  // 大类字典兜底重试：**仅「从未成功」时才真发请求**（store 内 `mealTypeLoaded` 守卫），失败不阻塞首屏。
  // Round 17：先前注释承诺的守卫并不存在（每次 onShow 都真发一次请求），本轮已在 store 内补齐 ⇒ 注释与实现一致。
  void dishStore.fetchMealTypes()
})

onShareAppMessage(() => {
  return buildSharePayload()
})
</script>

<style scoped lang="scss">
/* 页面：整屏壁纸（`PageWallpaper` 的 `<image>` 层）+ 叠在其上的暖色「纱」。
   ⚠️ **结构性骨架**（§11，2026-09-27）：本页是一个 **flex 列**，三段高度由内联 `pageStyle` 下发——
     padding-top = 标题带高（标题带 `fixed`，不占位）→ 常驻工具栏从标题带下沿开始；
     padding-bottom = 菜单栏 + 安全区 → 滚动区底边落在菜单栏上沿；
     中间的滚动区 `flex: 1` ⇒ 顶边 = 工具栏下沿。
   结果：**标题带**背后没有内容经过；**吸顶容器**背后有卡片滚过 ⇒ 只有它需要表面
   （吸顶后铺背景图切片，由 `@scroll` 驱动离散开关 —— 详见 §11.1）。 */
.home-page {
  /* 纱（wash）是**全站 token**：`--page-wash`（定义在 `App.vue` 的 `page{}`）；
     本页唯一的 `PageWallpaper`（页底壁纸层）自动取到该 token —— 横条一律透明，露出的就是这一层。
     改纱的浓淡 = 改 `App.vue` 里那一处（全站生效，不要在本页另立色值）。 */
  display: flex;
  flex-direction: column;
  /* 显式定高（Round 26）：原先只靠全局 `.page { min-height: 100vh }` 兜底，现改为页面自持声明，
     与其它页根同口径（vh + dvh 双声明）。`box-sizing: border-box`（全局重置）⇒ padding 含在高度内
     ⇒ 页根恰好一屏；滚动区 = 一屏 − 标题带 − 菜单栏，短内容不越界 ⇒ 无滚动条、无空白可滚区。 */
  height: 100vh;
  height: 100dvh;
  box-sizing: border-box;
  /* 页面根**不带底色**（UI 统一 Loop Round 11 修复）：根层叠上下文里「流内块背景」晚于「负层级子层」绘制，
     页面根若有底色会把 `z-index: var(--z-page-bg)`（−1）的壁纸层整块盖住 ⇒ 表现为「奶黄底、壁纸不可见」。
     底色已下沉到全局 `page{}`（App.vue），它天然在壁纸与所有内容之下。
     页面底**不再**用 background-image：壁纸 + 纱由 `PageWallpaper`（两层）承担（§11.1） */
  position: relative;
  overflow: hidden;
}

/* ===== 页面底壁纸层（§11.1）=====
   **页面级**壁纸层：`fixed` 视口锚定 + 偏移 0，铺满整个视口 —— 连顶部标题带那一条也已覆盖。
   层级由组件自身的 `--z-page-bg`（−1）承担：负层级压在本页背景之上、流内内容之下 ⇒ **本页不再覆写 z-index**
   （旧实现曾把它抬到 0、并把下面 `.scroll-wrap` 抬到 1，二者互为补丁；组件 token 化后全站同一机制，
   首页无需例外 —— UI 统一 Loop Round 5）。
   ⚠️ 与「吸顶容器切片」的关系（UI 统一 Loop Round 9 核对结论）：切片的 `z-index: -1` 是**相对于
   `.home-sticky` 自己的层叠上下文**（该容器 `position: sticky` + `z-index: var(--z-header)` ⇒ 自成上下文），
   与本层的 `--z-page-bg` **互不影响** ⇒ 页底壁纸层与吸顶切片可并存、**无层叠冲突**。 */

/* 固定标题带 / 标题样式已抽入公共组件 `components/AppTitleBand.vue`——
   首页与搜索页共用同一实现，避免两套样式漂移。
   **本页不再为它铺任何表面**（标题带切片已取消，2026-09-26）：`fixed` 页底壁纸已铺满视口、连顶部一条也覆盖。 */

/* ===== 吸顶容器（搜索区 + 大类标签栏）：**一个组件、一起吸顶** =====
   · `position: sticky` + `top: 0`（= 滚动区顶 = 固定标题带下沿）⇒ 位移由渲染层原生驱动，
     **不监听滚动、不做逐帧对齐、无状态**；
   · 纵向间距（§7.1）：padding-top 16px = Banner 下缘 → 搜索区；
     搜索区 ↔ 标签栏 = `.mt-tab` 内偏置 24rpx（12px）；
     padding-bottom 8px **+ 标签行自带 ≈8px 行底余量** = 标签栏 → 网格 ≈16px。 */
.home-sticky {
  /* `-webkit-sticky` 必须写在 `sticky` 之前：旧 WebKit（iOS Safari 15.4 及更早）只认带前缀的写法 */
  position: -webkit-sticky;
  position: sticky;
  top: 0;
  width: 100%;
  z-index: var(--z-header);
  box-sizing: border-box;
  padding-top: var(--spacing-lg);
  padding-bottom: var(--spacing-sm);
}
/* 吸顶态表面：外层裁切 + 内层壁纸（按实测基准贴回视口原点 ⇒ 显示"去掉顶部标题带那一段"里的对应一条）
   · 负 z ⇒ 画在容器自身（无底色）之上、搜索行 / 标签栏之下，并盖住从背后滚过的卡片；
   · 未吸顶时本层不渲染（`v-if="pinned"`）⇒ 容器完全透明；
   · ⚠️ 内层必须是 `absolute`：`fixed` 会逃出本层的 `overflow: hidden`、直接铺满整屏。 */
.home-sticky-slice {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  overflow: hidden;
  z-index: -1;
  pointer-events: none;
}
/* 偏移层：`top` / `left` / `width` 由内联 `sliceStyle` 下发
   （`top` = −基准 ⇒ 内层的视口位置回到原点，切片内容从**标题带下沿**开始，跳过顶部那一条） */
.home-sticky-slice-offset {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
}
/* 搜索行样式已抽入公共组件 `components/SearchBar.vue`——首页与搜索页共用同一实现（含高度 = 本机真实胶囊高）。 */

/* ===== 滚动区：`flex: 1` ⇒ 顶边 = 标签栏下沿、底边 = 菜单栏上沿（页面 padding-bottom 让出）=====
   网格在它内部滚动；**吸顶容器是例外**：卡片会从其背后滚过 ⇒ 吸顶态铺一层**背景图切片**
   （`.home-sticky-slice`，2026-09-27 决议），并由滚动监听驱动一个**离散开关** `pinned`
   （只在跨过锁定点翻转一次，不做逐帧对齐）。除该处外，本页无其它表面 ——
   （UI 统一 Loop Round 9 修正：原注释"全页零表面、零切片、零滚动监听"已与实现不符）。 */
.scroll-wrap {
  /* 抬到页面底壁纸层之上：绝对定位层默认画在流内内容之上 */
  position: relative;
  flex: 1;
  width: 100%;
  box-sizing: border-box;
  min-height: 0;
  /* 底部留白**恒为 0**：本页菜单栏留白已由**页根 `padding-bottom`** 在结构上让出（滚动区底边 = 菜单栏上沿，§11）。
     Round 26：全局 `.scroll-wrap` 的 tabbar 兜底 padding 已**删除**（它会让非 Tab 页凭空多出
     ≈ tabbar(50px) + 安全区(≈34px) 的死留白）；本行保留为显式声明，防将来有人再往全局加兜底。 */
  padding-bottom: 0;
}
.home-scroll-body {
  /* 固定标题带下沿 → Banner 上缘 = --spacing-md（12px，§3.1） */
  padding: var(--spacing-md) 0 0;
}

/* Banner 及其空态样式已随组件抽入 `pages/home/HomeBanner.vue`（§12）。 */
</style>
