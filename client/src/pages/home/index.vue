<template>
  <view class="page home-page">
    <!-- ===== 固定标题带（跨页统一，docs/ui/client-首页菜品浏览.md §1.0） =====
         · `position: fixed` **永久固定在页面左上角**，不随页面滚动移动、不随 Banner 滚出；
         · 与微信右上角**原生胶囊同一条水平线**（行高 = 胶囊高、垂直中心对齐），右侧按胶囊避让；
         · 文案**按页配置**（首页 = 「知行食记」，搜索页等各填自己的），位置 / 高度 / 对齐 / 配色跨页一致；
         · 纯文本、无点击行为；层叠高于 Banner 与吸顶容器 → 任意滚动位置都可读。 -->
    <AppTitleBand title="知行食记" :veil-opacity="titleVeilAlpha" />

    <!-- 滚动容器：**不受控**（无 `:scroll-top` / `:scroll-with-animation`）。
         数据更新走「首屏拉取（onLoad）+ onShow 兜底重拉 + 失败重试块（HomeContent 内）」三条既有路径；
         不强制回顶。 -->
    <scroll-view
      class="scroll-wrap"
      scroll-y
      :lower-threshold="LOWER_THRESHOLD_PX"
      @scroll="onScroll"
      @scrolltolower="onScrollToLower"
    >
      <view class="home-scroll-body">
        <!-- ===== Banner：页面正常流首块（自 y=0 起、含状态栏背后），整块 16:10 =====
             · 图片清单来自 `GET /banners`（服务端已按 sort_order 升序、只返回启用项）；
               端上按返回顺序渲染、不排序、不写死 URL 与张数；
             · 多张自动轮播 + 指示点；单张不轮播不显示指示点；
             · 空数组 / 请求失败 / 单张失败 →「灰底 + **中性 empty 图标**」空态（灰底样式与菜品卡图片占位同款，
               但**图标键取中性 `empty`** —— Banner 是运营位轮播，容器语义 ≠ 菜品，依 §4.9 不得用 `dish` 冒充中性占位）；
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
                <IconSvg name="empty" :size="120" :color="COLOR_MAP['text-tertiary']" />
              </view>
            </swiper-item>
          </swiper>
          <view v-else class="banner-ph">
            <IconSvg name="empty" :size="120" :color="COLOR_MAP['text-tertiary']" />
          </view>
        </view>

        <!-- ===== 吸顶容器（搜索区 + 横向大类标签栏） =====
             · **原生粘性定位**（`position: sticky`）：位移完全由渲染层原生滚动驱动，
               **不走滚动回调 + setData** —— 上滑时位置与内容 1:1 跟手，不会「像临时算出来的」那样滞后 / 闪现；
             · 流内落点紧贴 Banner 下缘（自然位置），滚动满「Banner 高 − 标题带高」时恰好粘在
               固定标题带下沿（= §5 锁定位置，两值同源、不需要任何 JS 位移）；
             · 表面 = 与页面底**同源**的渐变切片，切片基准 `--home-band-top` = 容器顶边在页面
               坐标中的位置（未吸顶随滚动连续变化、吸顶后夹紧为标题带下沿）→ 与身后页面底逐像素
               一致，故可**恒不透明**（无透明↔不透明硬切，也就没有切换瞬间的穿帮）；
             · 内部间距由本容器 padding 承担（§1.2）：上 padding = Banner→搜索区，下 padding = 标签栏→网格；
             · 背后无任何图片（Banner 是正常流首块，滚出即消失、不定格为背景）。 -->
        <view class="home-sticky" :style="stickyStyle">
          <!-- 搜索行：与搜索页同源（`SearchBar`）
               —— 左搜索胶囊 + 右独立「搜索」按钮，均为进搜索页的入口 -->
          <SearchBar mode="entry" @tap="goToSearch" />

          <!-- 横向大类标签栏：与搜索区同属吸顶容器；标签集合与文案完全来自字典（GET /dishes/meal-types）。
               自身上下 padding 已归零 →「搜索区↔标签栏」「标签栏↔网格」的间距各由
               `.mt-tab` 内偏置与容器 padding 单独承担，不再叠加。 -->
          <HomeMealTabs
            class="home-tabs"
            :items="dishStore.mealTypeList"
            :active-key="dishStore.filterMealType"
            @select="onMealTypeSelect"
          />
        </view>

        <!-- 双列瀑布流（当前大类下的热度流，未选 = 全部） -->
        <HomeContent @retry="retryWaterfall" />
      </view>
    </scroll-view>

    <!-- 底部常驻菜单栏：首页 / 我的 两主区切换（仅主根页显示） -->
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onLoad, onShow, onShareAppMessage } from '@dcloudio/uni-app'
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

/** Banner 宽高比锁定 **16:10**（UI 文档 §1.1）：素材必须同比例出图，混比例会导致切换时块高抖动、吸顶阈值漂移 */
const BANNER_ASPECT_RATIO = 10 / 16
/** 多图自动轮播间隔（ms；仅一张时不自动轮播） */
const BANNER_AUTOPLAY_INTERVAL = 4000
/** 运营内容最小可视高（px）：保证「状态栏 + 标题带」之下仍有空间放主文案 / 插画 */
const BANNER_MIN_CONTENT_PX = 120
/**
 * 触底提前量（px）：距底部还有该距离时就触发加载更多。
 * 默认 50px 会让用户「滚到底再等」，提前量把网络时延藏进滚动过程里（无限滚动更顺）。
 */
const LOWER_THRESHOLD_PX = 300

/* ===== 顶部度量（跨页统一实现，`useNavMetrics`）=====
   状态栏高 / 导航行高 / 胶囊高 / 胶囊避让量一律从该 composable 取——**页面不再自算**
   （`client-page-structure`：页面 SHALL NOT 各自计算导航尺寸）。本页只消费 `titleBandPx`：
   Banner 总高、容器吸顶的 `top` 与表面切片基准都要用它；标题带内部的居中与避让由 `AppTitleBand` 自持、
   搜索行高度由 `SearchBar` 自持。 */
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

/* ===== Banner 数据（接口下发；空 / 失败 → 灰底 + 中性 empty 空态，§4.9） ===== */
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

/* ===== 滚动量：只服务「表面切片对齐」，**不驱动位移** =====
   位移完全交给渲染层原生粘性定位（模板 `.home-sticky` 的 `position: sticky`），
   本回调只用来对齐容器表面（背景切片）的渐变基准：该基准滞后 1–2 帧在整条渐变上仅约 1/255 色阶，
   肉眼不可辨，不会闪现。 */
const scrollTop = ref(0)

/** 平台例外：uni scroll-view 滚动回调未纳入项目 TS 类型，只声明真正读取的字段 */
function onScroll(e: { detail?: { scrollTop?: number } }) {
  const raw = e?.detail?.scrollTop ?? 0
  // 量化到整数 px + 「值未变则不写」：避免亚像素抖动触发无意义的 computed 重算与 style 下发
  const top = raw > 0 ? Math.round(raw) : 0
  if (top === scrollTop.value) return
  scrollTop.value = top
}

/* ===== 容器表面（页面底同源切片）的对齐基准 =====
   · 未吸顶：容器顶边在**页面坐标**中的位置 = Banner 总高 − scrollTop（随滚动连续变化）；
   · 吸顶后：容器恒贴固定标题带下沿 → 夹紧为 `titleBandPx`。
   两段同源（Banner 总高 / 标题带高），夹紧保证吸顶期基准恒定 —— 于是表面**任何滚动位置都与身后
   页面底逐像素一致**，容器可以恒不透明，不需要「透明 ↔ 不透明」硬切，也就没有切换瞬间的穿帮。 */
const bandTopPx = computed(() => Math.max(
  bannerHeightPx.value - Math.max(scrollTop.value, 0),
  titleBandPx.value,
))
/** 基准的整数量化值（px）：避免亚像素重绘，也减少 style 字符串抖动 */
const bandTopPxRounded = computed(() => Math.round(bandTopPx.value))

/* ===== 纱式淡出（方案 C，UI 文档 §1.3）：标题带的表面不做硬切，改为随滚动渐显 =====
   · 区间 [H_b − TITLE_VEIL_PX, H_b] 内，纱（= 与页面底同源的渐变切片）透明度 0 → 1 线性渐显；
   · Banner 尾部因此「融入页面底色」；区间结束（H_b）时纱已 100%，而网格要到 H_b + H_s − H_t 才抵达标题带下沿
     → 有余量，绝不会出现「内容透出半透明纱」；
   · 进度**只由 scrollTop 推导**（不用计时器 / CSS 时长动画）→ 猛滑时纱的进度与位置严格同步，不会穿帮；
   · 反向滚动自动对称：纱按 scrollTop 反算，Banner 重新滚入时尾部由「已柔化」逐步回到「完整」。 */
const TITLE_VEIL_PX = 60
/** 纱的透明度（0..1）：线性映射，超出区间自动夹紧 */
const titleVeilAlpha = computed(() => {
  const start = bannerHeightPx.value - TITLE_VEIL_PX
  const p = (scrollTop.value - start) / TITLE_VEIL_PX
  return p <= 0 ? 0 : (p >= 1 ? 1 : Math.round(p * 1000) / 1000)
})

/**
 * 吸顶容器内联样式：
 * · `top` = 固定标题带下沿（§1.2 锁定位置）——`position: sticky` 的粘住阈值，缺它容器不会吸顶；
 * · `--home-band-top` = 容器顶边在页面坐标中的位置，CSS 用它把「页面底同源渐变」位移到正确切片，
 *   使容器表面与身后页底逐像素一致。
 * 位移本身**不在这里**（0 跨线程通信、0 延迟）：由渲染层原生粘性定位承担。
 * 注：`--capsule-h` 不由本容器下发 —— 搜索行高度由 `SearchBar` 内部按真实胶囊高内联设定。
 */
const stickyStyle = computed(() => ({
  top: `${titleBandPx.value}px`,
  '--home-band-top': `${bandTopPxRounded.value}px`,
}))

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
  /* 页面底渐变 = 页面底与吸顶容器表面（切片）的**唯一真源**：同一变量、同一起点，
     容器表面因此能与身后页底逐像素对齐（恒不透明也不会露出接缝） */
  --home-page-grad: linear-gradient(180deg, var(--bg-page-grad-from) 0%, var(--bg-page-grad-to) 420rpx, var(--bg-page) 720rpx);
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: var(--bg-page);
  background-image: var(--home-page-grad);
  position: relative;
  overflow: hidden;
}

/* 固定标题带 / 纱 / 标题样式已抽入公共组件 `components/AppTitleBand.vue`——
   首页与搜索页共用同一实现，避免两套样式漂移；纱层仍由本页按 `titleVeilAlpha` 驱动。 */

/* ===== 吸顶容器（搜索区 + 标签栏）：**原生粘性定位** =====
   · `position: sticky` + 内联 `top`（= 固定标题带下沿）→ 位移完全由渲染层原生滚动驱动，
     **不经过滚动回调 / setData**：上滑时与内容 1:1 跟手，不会「慢半拍」闪现；
   · 流内自然落点紧贴 Banner 下缘，滚动满「Banner 高 − 标题带高」时恰好粘住 ——
     与 UI 文档 §5 的锁定阈值同源，页面不需要任何 JS 位移补偿（fixed + transform 方案已废弃）；
   · 纵向间距（§1.2）：
       padding-top    = Banner 下缘 → 搜索区上沿 = --spacing-lg（16px）；
       padding-bottom = 标签栏下沿 → 网格首行 = --spacing-sm（8px）**+ 标签行自带的 ≈8px 行底余量**
                        = 视觉 ≈16px —— 与「块间 = 16px」的意图一致（旧值 --spacing-lg 会让实际间距
                        叠成 ≈24px，观感「标签栏离卡片太远」）；
   · 表面：**恒不透明**——吸顶态网格要从它背后滚过（否则卡片会透出）。用与页面底同一段渐变 +
     切片基准（--home-band-top = 容器顶边在页面坐标中的位置）实现「逐像素一致、无图片背景」的页底表面。
     恒不透明 = 没有「透明 ↔ 不透明」硬切，也就没有切换瞬间的穿帮。 */
.home-sticky {
  /* `-webkit-sticky` 必须写在 `sticky` 之前：旧 WebKit（iOS Safari 15.4 及更早）只认带前缀的写法。
     最近的滚动祖先即 `.scroll-wrap`（scroll-view 自身是滚动容器），故 `.home-page` 的
     `overflow: hidden` 不在二者之间、不影响粘性定位（它只是页面壳的裁切）。 */
  position: -webkit-sticky;
  position: sticky;
  width: 100%;
  z-index: var(--z-header);
  box-sizing: border-box;
  padding-top: var(--spacing-lg);
  padding-bottom: var(--spacing-sm);
  background-image: var(--home-page-grad);
  background-repeat: no-repeat;
  background-size: 100% 720rpx;
  background-position-y: calc(-1 * var(--home-band-top, 0px));
}
/* 搜索行样式已抽入公共组件 `components/SearchBar.vue`——首页与搜索页共用同一实现（含高度 = 本机真实胶囊高）。 */
/* 标签栏：上下间距全部外置 —— 与搜索区由 `.mt-tab` 自身 padding-top 承担、
   与网格由容器 padding-bottom 承担（两处均不再叠加组件 padding） */
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
   图片铺满（aspectFill）；无图 / 失败 → 灰底 + **中性 empty 图标**空态（灰底同菜品卡占位，图标取 `empty`，
   非 `dish` —— Banner 为运营位轮播，容器语义 ≠ 菜品，§4.9）。 */
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
</style>
