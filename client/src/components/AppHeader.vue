<template>
  <!-- 首页头部（两态结构中的**常驻吸顶头部**，2026-09-21 home-ui-refresh）：
       自上而下 = 页面标题「知行食记」→ **默认 slot（首页在此注入渐变 Banner）** → 通栏搜索卡（右侧内含「筛选」按钮）
       →（横向大类标签栏由页面紧随其后渲染）。
       初始态顺序与 home-page-presentation 一致：标题 → Banner → 通栏搜索框 → 大类标签栏 → 双列网格。
       底色不再是暖砖红：容器透明，露出页面顶部「浅米白 → 淡橙」渐变（--bg-page-grad-*）。
       本块位于滚动区**之外**常驻（吸顶实现 = 固定头部 + 既有 .scroll-wrap，禁用 position: sticky）；
       Banner 由滚动量驱动「折叠收起」（页面侧实现，不动本组件高度口径），收起后不吃高度 →
       吸顶态只剩「标题 + 完整搜索框」（+ 紧随其后的标签栏），Banner 不可见。
       2026-09-21 走查回退：标题行以**叠加层**绘制在 Banner 背景之上（Banner 通栏上移一个标题行高、
       垫在标题背后 = 标题的背景，用户要求），故标题行需 position:relative + z-index 抬升层级，
       否则后绘制的 Banner 裁剪窗口会盖住标题文字。
       胶囊避让：标题行右侧按 navMetrics 真源避让微信原生胶囊；标题与胶囊同行但分居左右，不重叠。 -->
       <view v-if="variant === 'home'" class="header-wrap home" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))', '--status-h': statusBarHeight + 'px', '--nav-h': navBarHeight + 'px', '--capsule-h': capsuleHeight + 'px' }">
       <view class="home-title-row" :style="{ height: navBarHeight + 'px', paddingRight: navPadRight }">
       <text v-if="title" class="home-title">{{ title }}</text>
       </view>
    <!-- 默认 slot：首页把渐变 Banner 注入「标题行」与「搜索行」之间
         （home-page-presentation 初始态顺序 = 标题 → Banner → 搜索框 → 标签栏 → 网格）。
         ⚠️ 本组件仅此**一个未命名 slot**：微信小程序单 slot 模式即够用，无需 `multipleSlots`，
         规避「多具名 slot 在 mp-weixin 塌缩」的既有教训；slot 也绝不放进 v-for（同名 slot 多次实例化必塌缩）。
         高度由页面按滚动量内联驱动（无 transition），故这里只留插槽位、不写任何高度/动效口径。 -->
    <slot />

    <!-- 搜索行：position:relative 使其绘制在 Banner 绝对图层（slot 注入）之上；
         transform 由页面下发（初始态位于 Banner 下方，随页面刚体上移至锁定） -->
    <view class="home-search-row" :style="{ position: 'relative', transform: searchShift }">
      <view
        class="home-search"
        role="search"
        :aria-label="searchPlaceholder"
        hover-class="home-search-pressed"
        @tap="$emit('search')"
      >
        <IconSvg name="search" :size="'18px'" :color="COLOR_MAP['text-tertiary']" class="home-search-icon" />
        <text class="home-search-placeholder">{{ searchPlaceholder }}</text>
        <!-- 「筛选」按钮：常驻搜索框内部右侧（文字 + 线性下拉箭头）。
             点击仅展开筛选面板（@tap.stop 阻断冒泡，不触发搜索跳转）——见 home-filter spec。 -->
        <view
          class="home-filter-btn"
          :class="{ open: filterOpen }"
          role="button"
          aria-label="筛选"
          hover-class="home-filter-btn-pressed"
          hover-stop-propagation
          @tap.stop="$emit('filter')"
        >
          <text class="home-filter-text">{{ filterLabel }}</text>
          <IconSvg
            class="home-filter-icon"
            :name="filterOpen ? 'arrow-up' : 'arrow-down'"
            :size="'16px'"
            :color="filterOpen ? COLOR_MAP['on-primary'] : COLOR_MAP['text-secondary']"
          />
        </view>
      </view>
    </view>
  </view>

  <!-- 搜索页：返回箭头 + 可输入搜索框 + 清除按钮（find 页复用，消除自绘 header 漂移） -->
  <view v-else-if="variant === 'search'" class="header-wrap search" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))', '--nav-h': navBarHeight + 'px', '--capsule-h': capsuleHeight + 'px' }">
    <view class="search-nav" :style="{ height: navBarHeight + 'px', paddingRight: navPadRight }">
      <view class="back-area" @tap="handleBack" role="button" aria-label="返回">
        <IconSvg name="arrow-left" :size="'22px'" :color="COLOR_MAP['text-white']" class="back-arrow" />
      </view>
      <view class="search-box">
        <IconSvg name="search" :size="'18px'" :color="COLOR_MAP['text-tertiary']" class="search-box-icon" />
        <input
          class="search-box-input"
          :value="modelValue"
          type="text"
          confirm-type="search"
          :placeholder="searchPlaceholder"
          placeholder-class="search-box-ph"
          :adjust-position="true"
          @input="onSearchInput"
          @confirm="onSearchConfirm"
        />
        <view v-if="modelValue" class="search-box-clear" @tap="$emit('clear')">
          <IconSvg name="close" :size="'16px'" :color="COLOR_MAP['text-tertiary']" />
        </view>
      </view>
    </view>
  </view>

  <!-- 通用/二级页：返回箭头 + 居中标题 + 右上角留空 -->
  <view v-else class="header-wrap" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))', '--nav-h': navBarHeight + 'px' }">
    <view class="nav" :class="{ 'nav--with-back': showBack }" :style="{ height: navBarHeight + 'px' }">
      <view
        v-if="showBack"
        class="back-area"
        @tap="handleBack"
        role="button"
        aria-label="返回"
      >
        <IconSvg name="arrow-left" :size="'22px'" :color="COLOR_MAP['text-white']" class="back-arrow" />
      </view>
      <text class="title">{{ title }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import IconSvg from './IconSvg.vue'
import { getNavBarHeight, getCapsuleHeight } from '@/utils/navMetrics'
import { COLOR_MAP } from '@/theme/tokens'

const props = withDefaults(defineProps<{
  /** home=首页头部（仅搜索框）；search=返回箭头+可输入搜索框（find 页）；default=二级页返回箭头+标题 */
  variant?: 'home' | 'search' | 'default'
  title?: string
  /** 首页/搜索框占位 */
  searchPlaceholder?: string
  /** search variant 双向绑定的搜索关键词 */
  modelValue?: string
  /** 是否显示返回箭头；从首页头像 navigateTo 进入二级页时传 true，TabBar 直入时传 false */
  showBack?: boolean
  /**
   * home variant 专用：搜索框右侧「筛选」按钮文案。
   * 默认「筛选」；已选食堂时由页面回显食堂名（省略号只作用于本按钮，不挤压搜索占位文案）。
   */
  filterLabel?: string
  /** home variant 专用：筛选面板是否展开（驱动箭头方向与展开态底色，不改变按钮行为） */
  filterOpen?: boolean
  /**
   * home variant 专用：搜索行位移（CSS transform 字符串，由页面按滚动量下发）。
   * 首页滚动交互（home-scroll-interaction）：初始态搜索框位于 Banner 下方、随页面刚体上移，
   * 跨阈值后锁定在标题行之下；继承位移即等于「与页面刚体同步滚动」。其他 variant 忽略。
   */
  searchShift?: string
}>(), {
  variant: 'default',
  title: '',
  searchPlaceholder: '搜索菜品、档口或食堂',
  modelValue: '',
  showBack: true,
  filterLabel: '筛选',
  filterOpen: false,
  searchShift: '',
})

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'search'): void
  (e: 'clear'): void
  (e: 'update:modelValue', value: string): void
  /** home variant：点击搜索框右侧「筛选」按钮（展开筛选面板，不触发搜索跳转） */
  (e: 'filter'): void
}>()

const statusBarHeight = ref(20)
const navBarHeight = ref(56)
const capsuleHeight = ref(32)
// 是否微信小程序环境（决定右上角是否避让原生胶囊）；非微信端（H5）右侧留白收窄
const isWeChat = ref(false)
const rightPad = ref('180rpx')
/** 导航行右侧留白（胶囊避让真源）：overridden 于 `search` variant 的 `.search-nav`
 *  与 `home` variant 的 `.home-title-row`（标题行）——**唯一计算式仍在，但消费方已是两处行容器**。
 *  ⚠️ home 的搜索行（`.home-search-row`）**不再消费本值**：它位于标题行下方、与胶囊不同行，
 *  右缘只需遵循页面级 gutter（--spacing-md）；因此「首页与搜索页搜索框右缘对齐」不再由本式保证，
 *  首页搜索框右缘 = 屏宽 − --spacing-md，搜索页 = 屏宽 − navPadRight，属既定差异。 */
const navPadRight = computed(() => `calc(env(safe-area-inset-right, 0px) + ${rightPad.value})`)

onMounted(() => {
  // 兼容老基础库：getWindowInfo 不存在时回退 getSystemInfoSync（避免拿不到 statusBarHeight 导致刘海遮挡）
  // @ts-ignore - 跨端兼容（H5 无 wx，退化为固定值）
  const win = (typeof wx !== 'undefined')
    // @ts-ignore
    ? (wx.getWindowInfo ? wx.getWindowInfo() : (wx.getSystemInfoSync ? wx.getSystemInfoSync() : null))
    : null
  const sb = (win && win.statusBarHeight) || 20
  statusBarHeight.value = sb
  // 仅在微信小程序环境避让右上角胶囊；H5/其余端收窄右侧留白，避免搜索框右侧大片空白
  // @ts-ignore - 跨端兼容（H5 无 wx）
  isWeChat.value = typeof wx !== 'undefined'
  // @ts-ignore - 微信胶囊按钮位置（右上角原生组件），用于对齐高度与右侧留白
  const mb = (typeof wx !== 'undefined' && wx.getMenuButtonBoundingClientRect) ? wx.getMenuButtonBoundingClientRect() : null
  if (mb && mb.height) {
    navBarHeight.value = getNavBarHeight(sb, mb)
    capsuleHeight.value = getCapsuleHeight(mb)
    // 右侧留白 = 胶囊左边到屏幕右缘的距离（px，各机型近似恒定 ~94px），
    //    再用 +8px 留一点间隙，使搜索框右缘停在胶囊左侧而非贴住它。
    //    必须用 px 而非 rpx：胶囊尺寸由微信按设备写死、不随屏宽缩放，rpx 会换机型就歪。
    const ww = (win && win.windowWidth) || 375
    rightPad.value = isWeChat.value ? `${Math.max(ww - mb.left + 8, 0)}px` : '0px'
  } else {
    rightPad.value = isWeChat.value ? '180rpx' : '0rpx'
  }
})

function handleBack() {
  emit('back')
}
// 平台例外：uni input 事件对象未纳入项目 TS 类型，取 e.detail.value（MP-08 标注例外）
function onSearchInput(e: any) {
  emit('update:modelValue', e.detail.value)
}
function onSearchConfirm() {
  emit('search')
}
</script>

<style scoped>
.header-wrap {
  width: 100%;
  box-sizing: border-box;
  background: var(--color-primary);
  border-bottom: none;
  position: sticky;
  top: 0;
  z-index: var(--z-header);
  /* 底部留白：全站 header 总高以「搜索页(find)」为基准，其 .search-nav 带此留白，
     故此处必须以同一 token（--spacing-sm）复刻，否则搜索页会比其余所有页面高 16rpx。
     ⚠️ 改此值必须同步改 find/index.vue 的 .search-nav —— 两者共用 --spacing-sm，
     只要都引用该 token 就不会漂移；真正要防的是其中一方整条留白被删。
     胶囊居中只由 paddingTop + 行高(--nav-h) 决定，本留白不影响胶囊对齐。 */
  padding-bottom: var(--spacing-sm);
}

/* ===== 通用/二级页：返回 + 居中标题 ===== */
.nav {
  display: flex;
  align-items: center;
  position: relative;
  box-sizing: border-box;
}
.back-area {
  position: absolute;
  left: var(--spacing-sm);
  top: 0;
  bottom: 0;
  width: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-tap-highlight-color: transparent;
}
.back-arrow { line-height: 1; }
/* 标题绝对居中：无论有无返回箭头，始终相对导航行真正水平居中（不再因左侧补偿而偏右） */
.title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
  /* 导航标题档（36rpx / 600）——client-visual-language R3 五档映射，与菜品名/昵称的
     一级标题档（32rpx / 600）区分，避免同一语义出现多套字号 */
  font-size: var(--font-h3);
  font-weight: var(--weight-semibold);
  color: var(--text-white);
  max-width: 60%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 首页头部（两态常驻头部）：标题行 + 搜索行 =====
   底色（2026-09-21 走查回退）：整个头部 = **Banner 渐变分区**——「知行食记」标题、今日推荐模块
     同处一个「浅橙 → 淡橙」连续渐变内（用户要求：标题在 Banner 区块内部左上角，不得独立成条）；
     滚动时今日推荐内容折叠滑出，标题 + 搜索框 + 标签栏在此渐变上常驻吸顶。
   吸顶：本块是滚动区**之外**的常驻节点（固定头部 + 内部滚动壳），故就地取消基类的
     position: sticky（home-page-presentation 明令禁用 sticky）。
   高度：比单行的搜索页 / 二级页头部多一行标题行 —— 属两态结构的既定差异；
     search / default variant 的行高口径（--capsule-h + --nav-h + --spacing-sm）不变。 */
/* 2026-09-21 走查：home 头部容器**不得有任何实心 / 渐变底**——
   基类 .header-wrap 的 `--color-primary` 实心底与本变体的渐变一并置为透明；
   顶部视觉由 Banner 图层（图片 / 占位卡片）承担，搜索卡、标签栏自持表面语言。 */
.header-wrap.home {
  background: transparent;
  background-image: none;
  position: relative;
  top: auto;
}
/* 行1：标题行，高度对齐系统导航栏，右侧避让微信原生气囊。
   左缘 = 全站页面级 gutter --spacing-md（24rpx，与 .scroll-wrap / .home-banner 同轴）；
   右侧由模板内联 paddingRight（navPadRight，胶囊避让真源）覆盖，本 shorthand 右值不参与生效，
   故改左值不影响胶囊避让。 */
.home-title-row {
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-md);
  box-sizing: border-box;
  /* 2026-09-21 走查回退：标题叠加绘制在 Banner 背景之上（Banner 通栏上移一个标题行高垫底，
     见 pages/home 的 .home-banner-wrap 负 margin）——后绘制的 Banner 会盖住先绘制的标题，
     故本行必须抬升层级 */
  position: relative;
  z-index: 2;
}
/* 页面标题「知行食记」：比导航标题档（--font-h3）更高一档，作为首页唯一的一级标题 */
.home-title {
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  line-height: 1.1;
  letter-spacing: var(--tracking-h2);
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 行2：通栏搜索行（位于标题行下方，与胶囊不同行 → 不重叠）。
   左右留白 = 全站页面级 gutter --spacing-md（24rpx）——与 .home-banner 左缘、标签栏首项文字缘同轴。
   本行右缘同时是「筛选」按钮与筛选面板的对齐基准（面板 right = 本内边距 + 搜索框内边距），
   改本值必须同步 HomeFilterPanel 的 .hf-panel right，否则面板右缘与按钮错位。 */
.home-search-row {
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-md);
  box-sizing: border-box;
}
.home-search {
  flex: 1;
  min-width: 0;
  /* ::after 命中区的定位包含块（D5） */
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: var(--capsule-h, 32px);
  padding: 0 var(--spacing-md);
  /* 白色实底（浅色模式），深色模式自动切换为卡片底色；可见性优于透明底。
     加柔和投影，让白色搜索卡从「浅米白 → 淡橙」页面渐变底上「悬浮」抽出、与底色分层
     （home-ui-refresh 已取消暖砖红顶栏，容器透明，投影不再用于从红栏中跳出）。
     ⚠️ 只加投影、不改高度：头部须与 find 页 .search-nav 等高（ui-surface-consistency 全站头部高度统一）。 */
  background: var(--bg-card);
  border-radius: var(--radius-pill);
  box-shadow: var(--shadow-float);
  -webkit-tap-highlight-color: transparent;
}
/* 按压反馈（D4）：白底搜索卡 → 浅底（§4.9 统一按压语言），与首页 dish-card / hf / mt-tab 反馈同族 */
.home-search-pressed { background: var(--bg-soft); }
/* 无障碍（D5）：搜索框本体高 = 胶囊高（32px），低于 44px 触达下限。
   透明 ::after 上下各扩 16rpx（＝搜索行上下两条空留白带的宽度：上为 Banner 下留白
   --spacing-sm、下为标签栏上留白 .mt-bar padding-top --spacing-sm，故不侵占相邻可点件），
   左右各扩到行内边距（--spacing-md，与 .home-search-row 的 padding 同源）→「整行可点」；
   扩张后左右边缘恰好落在行左右边缘（x=0 / 屏宽 − 24rpx），不越出行外。
   命中区 ≈ 96rpx × 整行 = 48px ✅；不改变搜索框视觉尺寸、不挤压占位与「筛选」按钮宽度。 */
.home-search::after {
  content: '';
  position: absolute;
  top: -16rpx;
  bottom: -16rpx;
  left: calc(-1 * var(--spacing-md));
  right: calc(-1 * var(--spacing-md));
}
.home-search-icon { flex-shrink: 0; line-height: 1; }
/* 占位文案：占据剩余宽度，与右侧「筛选」按钮互不挤压（占位被裁时只裁占位） */
.home-search-placeholder {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  color: var(--text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* ===== 搜索框右侧「筛选」按钮（home-filter spec：常驻、线性箭头、按内容定宽） ===== */
.home-filter-btn {
  flex-shrink: 0;
  /* 定位包含块（::after 扩展命中区）+ 提升层级：
     .home-search::after 作为伪元素是父盒的「最后一个子节点」，同层会盖住本按钮，
     故按钮须显式抬到它之上，保证「筛选」的点击不被搜索框吞掉（D5） */
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-2xs) var(--spacing-sm);
  border-radius: var(--radius-pill);
  -webkit-tap-highlight-color: transparent;
}
/* 无障碍（D5）：按钮本体 ≈40rpx（20px）高，远低于 44px 触达下限。
   透明 ::after 上下各扩 28rpx → 命中高度 ≈96rpx = 48px ✅
   （扩张量落在胶囊外那两条空留白带内：上 = Banner 下留白、下 = 标签栏上留白），
   横向不扩 → 不挤占搜索占位宽度、不与相邻可点件争抢命中（FilterBar 同法） */
.home-filter-btn::after {
  content: '';
  position: absolute;
  top: -28rpx;
  bottom: -28rpx;
  left: 0;
  right: 0;
}
/* 按压反馈（D4）：收起态（透明底）→ 浅底；展开态（主色填充底）→ 底色由 .open 保持主色、
   仅降透明度（本组特异性高于 .home-search-pressed 与全局 .pressed，不叠加双重反馈） */
.home-filter-btn-pressed { background: var(--bg-soft); }
.home-filter-btn.open.home-filter-btn-pressed { opacity: 0.85; }
/* 展开态：主色填充底 + 反白文字（--color-primary 即填充档，白字 5.18:1 ✅） */
.home-filter-btn.open { background: var(--color-primary); }
.home-filter-btn.open .home-filter-text { color: var(--color-on-primary); }
.home-filter-text {
  /* 已选食堂时回显食堂名：省略号只作用于本按钮文案，不挤压搜索占位（占位 flex:1 已让位） */
  max-width: 240rpx;
  font-size: var(--font-small);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.home-filter-icon { flex-shrink: 0; line-height: 1; }

/* ===== 搜索 variant（find 页）：返回箭头 + 可输入搜索框 + 清除按钮 ===== */
.search-nav {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  /* find 页反馈：返回钮收窄，避免占用搜索框过多宽度 */
  padding-left: var(--spacing-sm);
  padding-right: var(--spacing-lg);
  box-sizing: border-box;
}
/* 搜索态下返回区域改为行内（默认 variant 为绝对定位以居中标题），共享 .back-arrow 图标；
   收窄命中区（44→32px），把更多宽度让给搜索框 */
.search-nav .back-area { position: static; width: 32px; flex-shrink: 0; }
.search-box {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: var(--capsule-h, 32px);
  padding: 0 var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-pill);
  box-sizing: border-box;
}
.search-box-icon { flex-shrink: 0; line-height: 1; }
.search-box-input { flex: 1; min-width: 0; font-size: var(--font-body); color: var(--text-primary); }
.search-box-ph { color: var(--text-tertiary); }
.search-box-clear {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: var(--spacing-xs);
  border-radius: var(--radius-tag);
  -webkit-tap-highlight-color: transparent;
}
.search-box-clear:active { opacity: 0.55; }
</style>
