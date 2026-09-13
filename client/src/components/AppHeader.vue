<template>
  <!-- 首页头部：仅含搜索框（全宽，避让胶囊）。筛选栏已合并为独立 FilterBar 组件，由父级在 header 之下渲染 -->
  <view v-if="variant === 'home'" class="header-wrap home" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))', '--nav-h': navBarHeight + 'px', '--capsule-h': capsuleHeight + 'px' }">
    <view class="home-nav" :style="{ height: navBarHeight + 'px', paddingRight: navPadRight }">
      <view class="home-search" @tap="$emit('search')" role="search" :aria-label="searchPlaceholder">
        <IconSvg name="search" :size="'18px'" color="var(--text-tertiary)" class="home-search-icon" />
        <text class="home-search-placeholder">{{ searchPlaceholder }}</text>
      </view>
    </view>
  </view>

  <!-- 搜索页：返回箭头 + 可输入搜索框 + 清除按钮（find 页复用，消除自绘 header 漂移） -->
  <view v-else-if="variant === 'search'" class="header-wrap search" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))', '--nav-h': navBarHeight + 'px', '--capsule-h': capsuleHeight + 'px' }">
    <view class="search-nav" :style="{ height: navBarHeight + 'px', paddingRight: navPadRight }">
      <view class="back-area" @tap="handleBack" role="button" aria-label="返回">
        <IconSvg name="arrow-left" :size="'22px'" color="var(--text-white)" class="back-arrow" />
      </view>
      <view class="search-box">
        <IconSvg name="search" :size="'18px'" color="var(--text-tertiary)" class="search-box-icon" />
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
          <IconSvg name="close" :size="'16px'" color="var(--text-tertiary)" />
        </view>
      </view>
    </view>
  </view>

  <!-- 通用/二级页：返回箭头 + 居中标题 + 右上角留空 -->
  <view v-else class="header-wrap" :class="{ dark: dark }" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))', '--nav-h': navBarHeight + 'px' }">
    <view class="nav" :class="{ 'nav--with-back': showBack }" :style="{ height: navBarHeight + 'px' }">
      <view
        v-if="showBack"
        class="back-area"
        @tap="handleBack"
        role="button"
        aria-label="返回"
      >
        <IconSvg name="arrow-left" :size="'22px'" color="var(--text-white)" class="back-arrow" />
      </view>
      <text class="title">{{ title }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import IconSvg from './IconSvg.vue'
import { getNavBarHeight, getCapsuleHeight } from '@/utils/navMetrics'

const props = withDefaults(defineProps<{
  /** home=首页头部（仅搜索框）；search=返回箭头+可输入搜索框（find 页）；default=二级页返回箭头+标题 */
  variant?: 'home' | 'search' | 'default'
  title?: string
  /** 首页/搜索框占位 */
  searchPlaceholder?: string
  /** search variant 双向绑定的搜索关键词 */
  modelValue?: string
  /** 深色模式（仅影响无背景变量时的兜底） */
  dark?: boolean
  /** 是否显示返回箭头；从首页头像 navigateTo 进入二级页时传 true，TabBar 直入时传 false */
  showBack?: boolean
}>(), {
  variant: 'default',
  title: '',
  searchPlaceholder: '搜索菜品、档口或食堂',
  modelValue: '',
  dark: false,
  showBack: true,
})

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'search'): void
  (e: 'clear'): void
  (e: 'update:modelValue', value: string): void
}>()

const statusBarHeight = ref(20)
const navBarHeight = ref(56)
const capsuleHeight = ref(32)
// 是否微信小程序环境（决定右上角是否避让原生胶囊）；非微信端（H5）右侧留白收窄
const isWeChat = ref(false)
const rightPad = ref('180rpx')
/** 导航行右侧留白：home 与 search 两 variant **共用此唯一计算式**，
 *  使首页与搜索页搜索框右缘始终对齐 —— 两页宽度差异只应来自 search variant 左侧的返回箭头。 */
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

/* ===== 首页头部（朱砂红底）：仅搜索行 =====
   home variant 无需任何覆盖：红底 / 无底线 / 底部留白已全部由基类 .header-wrap 承载，
   高度统一走「状态栏高 + --nav-h + 底部留白」公式（基准＝搜索页）。
   筛选行是 header 之外的独立 .filter-bar，不占用 header 高度。 */
/* 行1：导航行，高度对齐系统导航栏，右侧避让原生气囊 */
.home-nav {
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-lg);
  box-sizing: border-box;
}
.home-search {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: var(--capsule-h, 32px);
  padding: 0 var(--spacing-md);
  /* 白色实底（浅色模式），深色模式自动切换为卡片底色；可见性优于透明底。
     tab-pages-visual-unify：加柔和投影，让搜索入口从红色顶栏中「悬浮」抽出，弱化红栏厚重感。
     ⚠️ 只加投影、不改高度：头部须与 find 页 .search-nav 等高（ui-surface-consistency 全站头部高度统一）。 */
  background: var(--bg-card);
  border-radius: var(--radius-pill);
  box-shadow: var(--shadow-float);
  -webkit-tap-highlight-color: transparent;
}
.home-search-icon { flex-shrink: 0; line-height: 1; }
.home-search-placeholder { font-size: var(--font-body); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

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
