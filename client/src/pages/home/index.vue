<template>
  <view class="page home-page">
    <!-- ===== 固定标题带（跨页统一，2026-09-22 定稿：docs/ui/client-首页菜品浏览.md §1.0） =====
         · `position: fixed` **永久固定在页面左上角**，不随页面滚动移动、不随 Banner 滚出；
         · 与微信右上角**原生胶囊同一条水平线**（行高 = 胶囊高、垂直中心对齐），右侧按胶囊避让；
         · 文案**按页配置**（首页 = 「知行食记」，搜索页等各填自己的），位置 / 高度 / 对齐 / 配色跨页一致；
         · 纯文本、无点击行为；层叠高于 Banner 与吸顶容器 → 任意滚动位置都可读。 -->
    <AppTitleBand title="知行食记" :veil-opacity="titleVeilAlpha" />

    <!-- ===== 吸顶容器（搜索区 + 横向大类标签栏，2026-09-22 定稿） =====
         · 固定层，`top` = 固定标题带下沿（锁定位置**不得压到标题带**）；
         · 初始态整体下移一个 Banner 高（坐在 Banner 之下）；滚动满「Banner 高 − 标题带高」后归零 → 锁定；
         · 内部间距由本容器 padding 承担（§1.2 间距表）：上 padding = Banner→搜索区，下 padding = 标签栏→网格；
         · 背后**无任何图片**（Banner 是页面正常流首块，滚出即消失、不定格为背景）。
         ⚠️ 禁用 position: sticky（小程序基础库行为不一致），故走「固定层 + 内部滚动」。 -->
    <view class="home-sticky" :class="{ 'is-surface': stickySurfaceOn }" :style="stickyStyle">
      <!-- 搜索行：与搜索页同源（`SearchBar`，2026-09-22 抽公共组件）
           —— 左搜索胶囊 + 右独立「搜索」按钮，均为进搜索页的入口 -->
      <SearchBar mode="entry" @tap="goToSearch" />

      <!-- 横向大类标签栏：与搜索区同属吸顶容器；标签集合与文案完全来自字典（GET /dishes/meal-types）。
           下 padding 归零（内联，优先级确定）→「标签栏→网格」间距的唯一来源 = 容器 padding-bottom（§1.2），
           避免与组件自带下 padding 叠加成 32rpx。 -->
      <HomeMealTabs
        class="home-tabs"
        :style="{ paddingBottom: '0' }"
        :items="dishStore.mealTypeList"
        :active-key="dishStore.filterMealType"
        @select="onMealTypeSelect"
      />
    </view>

    <scroll-view
      class="scroll-wrap"
      scroll-y
      :scroll-top="scrollTopProp"
      :scroll-with-animation="false"
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      :lower-threshold="LOWER_THRESHOLD_PX"
      @scroll="onScroll"
      @refresherrefresh="onRefresh"
      @scrolltolower="onScrollToLower"
    >
      <view class="home-scroll-body">
        <!-- ===== Banner：页面正常流首块（自 y=0 起、含状态栏背后），整块 16:10 =====
             · 图片清单来自 `GET /banners`（服务端已按 sort_order 升序、只返回启用项）；
               端上按返回顺序渲染、不排序、不写死 URL 与张数；
             · 多张自动轮播 + 指示点；单张不轮播不显示指示点；
             · 空数组 / 请求失败 / 单张失败 →「灰底 + 菜品 icon」空态（与菜品卡图片占位同款，无文字）；
             · 块高恒定按 BANNER_ASPECT 定高，加载态不改变块高（否则吸顶阈值漂移）；
             · 固定标题带叠在其上（Banner 滚动时从标题带下方滑过）。 -->
        <view class="home-banner" :style="{ height: bannerHeightStyle }">
          <swiper
            v-if="bannerList.length > 0"
            class="banner-swiper"
            :autoplay="bannerList.length > 1"
            :interval="BANNER_AUTOPLAY_INTERVAL"
            circular
            :indicator-dots="bannerList.length > 1"
            indicator-color="rgba(255, 255, 255, 0.45)"
            indicator-active-color="#FFFFFF"
          >
            <swiper-item v-for="b in bannerList" :key="b.id">
              <image
                v-if="b.imageUrl && !failedBannerIds.includes(b.id)"
                class="banner-img"
                :src="b.imageUrl"
                mode="aspectFill"
                @error="onBannerError(b.id)"
              />
              <view v-else class="banner-ph">
                <IconSvg name="dish" :size="120" :color="COLOR_MAP['text-tertiary']" />
              </view>
            </swiper-item>
          </swiper>
          <view v-else class="banner-ph">
            <IconSvg name="dish" :size="120" :color="COLOR_MAP['text-tertiary']" />
          </view>
        </view>

        <!-- 吸顶容器在内容流中的站位（高 = 固定容器实测高，含其上下 padding）：
             保证初始态容器恰好坐在 Banner 之下、且网格从容器下沿起排 -->
        <view class="home-sticky-hold" :style="{ height: stickyHoldH }"></view>

        <!-- 双列瀑布流（当前大类下的热度流，未选 = 全部） -->
        <HomeContent @retry="retryWaterfall" />
      </view>
    </scroll-view>

    <!-- 底部常驻菜单栏：首页 / 我的 两主区切换（仅主根页显示） -->
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import { onLoad, onShow, onReady, onShareAppMessage } from '@dcloudio/uni-app'
import { showTab } from '@/stores/route'
import { useDishStore } from '@/stores/dish'
import * as bannerApi from '@/api/banner'
import type { Banner } from '@/types/banner'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import { PATH } from '@/utils/routes'
import { useNavMetrics } from '@/utils/useNavMetrics'
import IconSvg from '@/components/IconSvg.vue'
import AppTitleBand from '@/components/AppTitleBand.vue'
import SearchBar from '@/components/SearchBar.vue'
import { COLOR_MAP } from '@/theme/tokens'
import HomeMealTabs from './HomeMealTabs.vue'
import HomeContent from './HomeContent.vue'
import TabBar from '@/components/TabBar.vue'

const dishStore = useDishStore()
const refresherTriggered = ref(false)

/** Banner 宽高比锁定 **16:10**（UI 文档 §1.1）：素材必须同比例出图，混比例会导致切换时块高抖动、吸顶阈值漂移 */
const BANNER_ASPECT_RATIO = 10 / 16
/** 多图自动轮播间隔（ms；仅一张时不自动轮播） */
const BANNER_AUTOPLAY_INTERVAL = 4000
/** 运营内容最小可视高（px）：保证「状态栏 + 标题带」之下仍有空间放主文案 / 插画 */
const BANNER_MIN_CONTENT_PX = 120
/**
 * 吸顶容器未实测前的兜底高度（rpx）：上 padding 32 + 搜索行 64（= 胶囊高 32px）
 * + 标签栏上 padding 16 + 标签行 88 + 下 padding 32 = 232rpx（与 §1.2 间距表同源）。
 */
const STICKY_FALLBACK_RPX = 232
/**
 * 触底提前量（px）：距底部还有该距离时就触发加载更多。
 * 默认 50px 会让用户「滚到底再等」，提前量把网络时延藏进滚动过程里（无限滚动更顺）。
 */
const LOWER_THRESHOLD_PX = 300

/* ===== 顶部度量（跨页统一实现，2026-09-22 抽 `useNavMetrics`）=====
   状态栏高 / 导航行高 / 胶囊高 / 胶囊避让量一律从该 composable 取——**页面不再自算**
   （`client-page-structure`：页面 SHALL NOT 各自计算导航尺寸）。本页只消费 `titleBandPx`：
   Banner 总高与吸顶阈值都要用它；标题带内部的居中与避让由 `AppTitleBand` 自持、搜索行高度由 `SearchBar` 自持。 */
const { titleBandPx } = useNavMetrics()
/** 窗口宽（px）：Banner 16:10 定高用（页面自持，与胶囊度量无关） */
const windowWidthPx = ref(375)

onMounted(() => {
  // @ts-ignore - 跨端兼容（H5 无 wx，退化为固定值）
  const win = (typeof wx !== 'undefined')
    // @ts-ignore
    ? (wx.getWindowInfo ? wx.getWindowInfo() : (wx.getSystemInfoSync ? wx.getSystemInfoSync() : null))
    : null
  windowWidthPx.value = (win && win.windowWidth) || 375
})

/**
 * Banner 总高（px）= `max(屏宽 × 10/16, 标题带高 + 运营内容最小可视高)`。
 * ⚠️ 兜底项**不得**再加 `statusBarPx`：`titleBandPx` 已含状态栏高，重复计会凭空多出 ≈44px
 * → 主流机型（iPhone X 类 375×812）会从 16:10 变成 ≈1.49:1，导致**按 16:10 出的素材被裁掉两侧**。
 * 去掉重复计后：375 宽 = max(234, 88 + 120 = 208) = **234**（正是 16:10）；仅窄屏（如 320）才由兜底生效。
 */
const bannerHeightPx = computed(() => Math.max(
  Math.round(windowWidthPx.value * BANNER_ASPECT_RATIO),
  titleBandPx.value + BANNER_MIN_CONTENT_PX,
))
const bannerHeightStyle = computed(() => `${bannerHeightPx.value}px`)

/* ===== Banner 数据（接口下发；空 / 失败 → 灰底 + 菜品 icon 空态） ===== */
const bannerList = ref<Banner[]>([])
/** 单张加载失败的 banner id（该张退化为空态，其余张不受影响、轮播继续） */
const failedBannerIds = ref<number[]>([])
function onBannerError(id: number) {
  if (!failedBannerIds.value.includes(id)) failedBannerIds.value = [...failedBannerIds.value, id]
}
async function loadBanners() {
  try {
    bannerList.value = await bannerApi.getBanners()
  } catch (e) {
    console.error('加载首页轮播图失败', e)
    bannerList.value = []
  }
}

/* ===== 吸顶：滚动量驱动吸顶容器位移（1:1 跟手、无过渡动画） =====
   · 阈值 = Banner 总高 − 标题带高（§5：Banner 下缘抵达标题带下沿时锁定）；
   · 位移 = max(阈值 − scrollTop, 0)；位移为 0 时容器锁定在标题带下沿；
   · 容器自身 padding 承担「Banner→搜索区」「标签栏→网格」的间距，故容器顶边 = bannerH 时视觉间隙已正确。 */
const scrollTop = ref(0)

/** 平台例外：uni scroll-view 滚动回调未纳入项目 TS 类型，只声明真正读取的字段 */
function onScroll(e: { detail?: { scrollTop?: number } }) {
  const raw = e?.detail?.scrollTop ?? 0
  // 量化到整数 px + 「值未变则不写」：避免亚像素抖动触发无意义的 computed 重算与 style 下发
  const top = raw > 0 ? Math.round(raw) : 0
  if (top === scrollTop.value) return
  scrollTop.value = top
}

/** 吸顶容器当前位移量（px）：夹在 [0, Banner 总高 − 标题带高]，1:1 跟手、无过渡 */
const stickyOffsetPx = computed(() => {
  const lockStart = Math.max(bannerHeightPx.value - titleBandPx.value, 0)
  return Math.max(lockStart - Math.max(scrollTop.value, 0), 0)
})

/** 吸顶容器位移的整数量化值（px）：避免亚像素重绘，也减少 style 字符串抖动 */
const stickyOffsetPxRounded = computed(() => Math.round(stickyOffsetPx.value))

/* ===== 顶部两层的「表面」开关（2026-09-22）=====
   目标：**滑动前两层真透明**（Banner 完整占满顶部、不被任何表面遮挡）；
         **滑动后在「零内容窗口」内切成「页面底切片」**——切片与身后的页面底逐像素一致，
         因此切换本身**不可见**，同时把滚上来的网格内容挡住。
   窗口依据（375 宽 iPhone X 类：H_t≈88 / H_b≈234 / H_s≈116，与 UI 文档 §1.3 同源）：
     · 容器：scrollTop = H_b − H_t ≈ 146 时锁定（此刻网格首行**恰好贴到**容器下沿，背后仍是空站位）；
     · 标题带：scrollTop = H_b ≈ 234 时 Banner 完全滚出（内容要到 ≈262 才抵达标题带下沿）。
   提前量 SURFACE_SWITCH_LEAD_PX 用于规避 1px 舍入造成的临界抖动（窗口内有富余，不会露出内容）。 */
const SURFACE_SWITCH_LEAD_PX = 8

/* ===== 纱式淡出（方案 C，UI 文档 §1.3）：标题带的表面不做硬切，改为随滚动渐显 =====
   · 区间 [H_b − TITLE_VEIL_PX, H_b] 内，纱（= 与页面底同源的渐变切片）透明度 0 → 1 线性渐显；
   · Banner 尾部因此「融入页面底色」；区间结束（H_b）时纱已 100%，而网格要到 H_b + H_s − H_t ≈ 262 才抵达标题带下沿
     → 有 ≈28px 富余，绝不会出现「内容透出半透明纱」；
   · 进度**只由 scrollTop 推导**（不用计时器 / CSS 时长动画）→ 猛滑时纱的进度与位置严格同步，不会穿帮；
   · 反向滚动自动对称：纱按 scrollTop 反算，Banner 重新滚入时尾部由「已柔化」逐步回到「完整」。 */
const TITLE_VEIL_PX = 60
/** 纱的透明度（0..1）：线性映射，超出区间自动夹紧 */
const titleVeilAlpha = computed(() => {
  const start = bannerHeightPx.value - TITLE_VEIL_PX
  const p = (scrollTop.value - start) / TITLE_VEIL_PX
  return p <= 0 ? 0 : (p >= 1 ? 1 : Math.round(p * 1000) / 1000)
})
/** 吸顶容器是否改为「页面底切片」表面（即将/已经锁定，背后即将有网格内容进入） */
const stickySurfaceOn = computed(() => {
  const lockStart = Math.max(bannerHeightPx.value - titleBandPx.value, 0)
  return scrollTop.value >= lockStart - SURFACE_SWITCH_LEAD_PX
})
const stickyStyle = computed(() => {
  const offset = stickyOffsetPxRounded.value
  return {
    // `top` = 固定标题带下沿（锁定位置，§1.2）；缺它 → fixed 会落到 `top: auto` 的静态位置（屏幕顶），
    // 搜索条会「飘出顶部」——故本 computed 的每个字段都是**必需**的，改动前必须确认常量来源存在。
    top: `${titleBandPx.value}px`,
    // translate3d：① 整数量化避免亚像素重绘；② 抬升为合成层，滚动时容器只做合成、不重绘（低端机更跟手）
    transform: `translate3d(0, ${offset}px, 0)`,
    // 背景切片对齐（容器是不透明的吸顶面，须与静止的页面顶部渐变逐像素对齐，否则出现色带接缝）：
    // 传入「容器顶边在页面坐标中的位置」，CSS 用它把同一段渐变位移到正确切片。
    '--home-band-top': `${titleBandPx.value + offset}px`,
    // 注：`--capsule-h` 已不再由本容器下发（2026-09-22 组件化）——搜索行高度由 `SearchBar`
    // 内部按 `useNavMetrics` 的真实胶囊高内联设定，避免「页面算一份、组件算一份」两处漂移。
  }
})

/** 吸顶容器实测高（px）：onReady 实测 .home-sticky（含其上下 padding），作为内容流站位高 */
const stickyHpx = ref(0)
onReady(() => {
  uni.createSelectorQuery()
    .select('.home-sticky')
    .boundingClientRect((rect: { height?: number } | null) => {
      if (rect && rect.height) stickyHpx.value = rect.height
    })
    .exec()
})
/** 内容流站位高：未实测前用 232rpx 兜底（与 §1.2 间距表同源） */
const stickyHoldH = computed(() => `${stickyHpx.value || uni.upx2px(STICKY_FALLBACK_RPX)}px`)

/* ===== 下拉刷新：强制回顶（scroll-view 无 scrollTo，用受控 scroll-top 脉冲） =====
   回到初始静止态（Banner 完整展示）；固定标题带始终可见、不受刷新影响。 */
const scrollTopProp = ref(0)
function resetScrollToTop() {
  scrollTop.value = 0
  scrollTopProp.value = 1
  nextTick(() => {
    scrollTopProp.value = 0
  })
}

/** 切换大类：写回 store（内部重置分页并刷新列表）；**不重置滚动位置**，保持当前吸顶 / 初始态 */
async function onMealTypeSelect(key: string | null) {
  await dishStore.setHomeMealType(key)
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
  dishStore.loadMoreHomeDishes()
}

/** 下拉刷新：Banner 与列表并行重拉 + 强制回顶 */
async function onRefresh() {
  refresherTriggered.value = true
  resetScrollToTop()
  await Promise.all([loadBanners(), dishStore.fetchHomeDishes(true)])
  refresherTriggered.value = false
}

onLoad(() => {
  // Banner 与列表**并行**发起：Banner 失败不阻塞首屏网格
  void loadBanners()
  // 大类字典：不 await（失败降级为仅「全部」），保证首屏列表不被字典阻塞
  void dishStore.fetchMealTypes()
  void dishStore.fetchHomeDishes(true)
})

onShow(() => {
  showTab('home')
  clearShareState()
  // 大类字典兜底重试：仅「从未成功」时才发请求（store 内自带守卫），失败不阻塞首屏
  void dishStore.fetchMealTypes()
})

onShareAppMessage(() => {
  return buildSharePayload()
})
</script>

<style scoped lang="scss">
/* 页面：顶部「浅米白 → 淡橙」渐变（token: --bg-page-grad-*），仅覆盖首屏高度，其余回落页面底色 */
.home-page {
  /* 页面底渐变 = 顶部两层「隐形表面」的唯一真源：切片复用同一变量，保证拼接 / 切换逐像素一致 */
  --home-page-grad: linear-gradient(180deg, var(--bg-page-grad-from) 0%, var(--bg-page-grad-to) 420rpx, var(--bg-page) 720rpx);
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--bg-page);
  background-image: var(--home-page-grad);
  position: relative;
  overflow: hidden;
}

/* 固定标题带 / 纱 / 标题样式已抽入公共组件 `components/AppTitleBand.vue`（2026-09-22 change `search-page-refresh`）——
   首页与搜索页共用同一实现，避免两套样式漂移；纱层仍由本页按 `titleVeilAlpha` 驱动。 */

/* ===== 吸顶容器（搜索区 + 标签栏）：固定层，top / transform / 背景切片由脚本内联下发 =====
   padding 承担 §1.2 间距表里的两条**块间**纵向间距（均为 --spacing-lg）：
     · padding-top    = Banner 下缘 → 搜索区上沿
     · padding-bottom = 标签栏下沿 → 网格首行
   背景：**必须不透明**——吸顶态网格要从它背后滚过（否则卡片会透出）；
        用与页面顶栏同一段渐变 + 位移切片（--home-band-top）实现「无缝、无图片背景」的页底表面。 */
.home-sticky {
  position: fixed;
  left: 0;
  right: 0;
  z-index: var(--z-header);
  box-sizing: border-box;
  /* 块间距（§1.2）：块**之间**用 --spacing-lg（Banner↔吸顶块、吸顶块↔内容），块**内部**用 --spacing-sm
     （搜索↔标签栏）——保证「块间 > 块内」「块间 > 卡片间距(--spacing-md)」，视觉上才有分组感 */
  padding-top: var(--spacing-lg);
  padding-bottom: var(--spacing-lg);
  /* 初始态：**完全透明**（容器与 Banner 从不重叠——它恒贴 Banner 下缘，
     透明既满足「Banner 占满顶部」，也比切片更稳妥：1px 舍入也绝不盖住 Banner 末行像素） */
}
/* 即将 / 已经锁定：切为「页面底切片」。切片基准 --home-band-top = 容器顶边在页面坐标中的位置，
   与身后页面底逐像素一致 → 切换不可见；此后网格从它背后滚过被干净裁切（不会透出）。 */
.home-sticky.is-surface {
  background-image: var(--home-page-grad);
  background-repeat: no-repeat;
  background-size: 100% 720rpx;
  background-position-y: calc(-1 * var(--home-band-top, 0px));
}
/* 搜索行样式（搜索胶囊 / 「搜索」按钮 / 命中区扩张）已抽入公共组件 `components/SearchBar.vue`
   （2026-09-22 change `search-page-refresh`）——首页与搜索页共用同一实现（含高度 = 本机真实胶囊高）。 */
/* 标签栏：与搜索区的间距（块内）由组件自带 padding-top（--spacing-sm）承担（§1.2）；
   下 padding 由模板内联归零 → 容器下 padding（--spacing-lg）成为「标签栏→网格」的唯一来源 */
.home-tabs {
  position: relative;
}

.scroll-wrap {
  flex: 1;
  width: 100%;
  box-sizing: border-box;
  min-height: 0;
  /* 预留底部菜单栏高度，避免内容被 TabBar 遮挡 */
  padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
}
.home-scroll-body {
  padding: 0;
}

/* ===== Banner（正常流首块；整块 16:10，自 y=0 含状态栏背后） =====
   图片铺满（aspectFill）；无图 / 失败 → 灰底 + 菜品 icon 空态（与菜品卡图片占位同款，无文字）。 */
.home-banner {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--bg-soft);
}
.banner-swiper {
  width: 100%;
  height: 100%;
}
.banner-img {
  width: 100%;
  height: 100%;
  display: block;
}
.banner-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
/* 吸顶容器在内容流中的站位（高由脚本内联下发，= 容器实测高） */
.home-sticky-hold {
  width: 100%;
}
</style>
