<template>
  <view class="page home-page" :class="{ 'theme-dark': theme.isDark }">
    <!-- 顶部：定位 + 搜索框（2026-08-03 修复：动态避让状态栏 + 微信右上角胶囊按钮，不再用 env(safe-area-inset-top)） -->
    <view class="home-top" :style="{ paddingTop: statusBarHeight + 'px' }">
      <!-- 定位条：点击重新定位（方案 C；授权成功后首页推荐按距离排序） -->
      <view class="loc-bar" :style="{ paddingRight: menuButtonRight + 'px' }" @tap="onLocTap">
        <IconSvg name="location" :size="30" color="var(--text-white)" class="loc-icon" />
        <text class="loc-text">{{ currentLocation }}</text>
        <IconSvg name="arrow-down" :size="22" color="var(--text-white-soft)" class="loc-arrow" />
      </view>
      <view class="home-search" @tap="goToSearch">
        <IconSvg name="search" :size="30" color="var(--text-tertiary)" class="home-search-icon" />
        <text class="home-search-placeholder">搜索菜品 / 食堂…</text>
      </view>
    </view>
    <scroll-view
      class="scroll-wrap"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
      @scrolltolower="onScrollToLower"
    >

      <!-- 加载骨架屏：数据未返回时展示，避免「只有文本」的空壳观感 -->
      <view v-if="loading" class="home-skeleton">
        <view class="sk-banner skeleton" />
        <view class="sk-canteen skeleton" />
        <view class="sk-grid">
          <view v-for="s in 4" :key="s" class="sk-card skeleton" />
        </view>
      </view>

      <!-- 空状态：全部板块无数据（后端未起 / 无数据 / 网络异常）时友好提示，可下拉重试 -->
      <view v-else-if="isAllEmpty" class="home-empty">
        <IconSvg name="empty" :size="120" color="var(--text-tertiary)" />
        <text class="empty-tip">{{ loadFailed ? '加载失败' : '暂时没有内容' }}</text>
        <text class="empty-sub">{{ loadFailed ? '网络异常或后端未启动，下拉刷新后重试' : '下拉刷新，或确认后端已启动、网络可访问后重试' }}</text>
      </view>

      <block v-else>
        <!-- Banner 轮播（按 target_type 跳转）；无数据时限轻量占位，不整块消失 -->
        <view class="swiper-section enter-up" :style="{ '--enter-i': 0 }">
          <swiper v-if="dishStore.homeBanners.length > 0" class="home-swiper" indicator-dots
            :indicator-color="SWIPER_INDICATOR_COLOR"
            :indicator-active-color="SWIPER_INDICATOR_ACTIVE_COLOR" autoplay interval="3000" circular>
            <swiper-item v-for="(item, idx) in dishStore.homeBanners" :key="idx">
              <view class="swiper-slide" :class="{ 'swiper-slide-ph': !item.image }" @tap="handleBannerTap(item)">
                <image v-if="item.image" class="swiper-img" :src="item.image" mode="aspectFill" @error="item.image = ''" />
                <view v-if="item.image" class="swiper-overlay" />
                <text class="swiper-title" :class="{ 'ph': !item.image }">{{ item.title }}</text>
                <text class="swiper-subtitle" :class="{ 'ph': !item.image }">{{ item.subtitle }}</text>
              </view>
            </swiper-item>
          </swiper>
          <view v-else class="home-swiper swiper-placeholder">
            <text class="swiper-ph-text">暂无推荐</text>
          </view>
        </view>

        <!-- 广播通知条：细长 ticker，仅通知图标 + 文本内容，内容每秒上下滚动轮换（task-13 §1.1，去除「查看全部」，按广播类型分发跳转） -->
      <view class="section enter-up broadcast-section" :style="{ '--enter-i': 1 }">
        <view
          class="broadcast-bar"
          :class="{ pressed: momentPressed }"
          @touchstart="momentPressed = true"
          @touchend="momentPressed = false"
          @touchcancel="momentPressed = false"
          @mousedown="momentPressed = true"
          @mouseup="momentPressed = false"
          @mouseleave="momentPressed = false"
        >
          <IconSvg name="broadcast" :size="30" color="var(--text-secondary)" class="broadcast-icon" />
          <view v-if="visibleBroadcasts.length > 0" class="broadcast-ticker">
            <view :key="broadcastIndex" class="broadcast-line broadcast-line-enter" @tap="goBroadcast(broadcastIndex)">
              <text class="broadcast-text">{{ visibleBroadcasts[broadcastIndex]?.text }}</text>
            </view>
          </view>
          <text v-else class="broadcast-text broadcast-single">暂无广播通知</text>
        </view>
      </view>

      <!-- 食堂入口（coverflow 横滑：中间大、两边各露出半张、无限循环） -->
        <view class="section enter-up" v-if="canteens.length > 0" :style="{ '--enter-i': 1 }">
          <SectionTitle title="食堂入口" />
          <swiper class="canteen-swiper" circular previous-margin="180rpx" next-margin="180rpx">
            <swiper-item v-for="item in canteens" :key="item.name">
              <view class="canteen-card" @tap="goToCanteen(item.name)">
                <image v-if="item.image" class="canteen-img" :src="item.image" mode="aspectFill" lazy-load />
                <view v-else class="canteen-img canteen-img-placeholder">
                  <IconSvg name="empty" :size="72" color="var(--text-tertiary)" class="canteen-illu" />
                </view>
                <view class="canteen-overlay" />
                <text class="canteen-name">{{ item.name }}</text>
              </view>
            </swiper-item>
          </swiper>
        </view>

        <!-- 热门菜品（双列瀑布流 + 无限加载） -->
        <view class="section enter-up" v-if="dishStore.homeHotList.length > 0" :style="{ '--enter-i': 2 }">
          <SectionTitle title="热门菜品" />
          <WaterfallList :list="dishStore.homeHotList" @card-click="goToDetail" />

          <!-- 触底加载状态 -->
          <view v-if="dishStore.homeHotLoadingMore" class="list-footer loading">
            <view class="footer-spinner" />
            <text class="footer-text">加载中…</text>
          </view>
          <view v-else-if="dishStore.homeHotFinished" class="list-footer finished">
            <text class="footer-text">— 已经到底啦 —</text>
          </view>
        </view>
      </block>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>
    <CustomTabBar current="/pages/home/index" />
    <DishDetailSheet
      :open="dishSheetOpen"
      :dish-id="sheetDishId"
      top-offset="176rpx"
      @update:open="dishSheetOpen = $event"
    />
  </view>
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
const theme = useThemeStore()
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import WaterfallList from '@/components/WaterfallList.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import IconSvg from '@/components/IconSvg.vue'
import DishDetailSheet from '@/components/DishDetailSheet.vue'
import { useDishStore } from '@/stores/dish'
import { getBroadcasts } from '@/api/notify'
import { buildSharePayload } from '@/utils/shareState'
import { SWIPER_INDICATOR_ACTIVE_COLOR, SWIPER_INDICATOR_COLOR } from '@/constants/ui'
import type { Dish } from '@/types/dish'
import type { BannerItem } from '@/types/banner'
import { getCachedLocation, refreshLocation, type LocationInfo } from '@/utils/location'

const dishStore = useDishStore()

/** 定位展示（方案 C：真实定位 + 手动兜底，缓存持久化；点击 loc-bar 可重新定位） */
const cachedLoc = getCachedLocation()
const currentLocation = ref(cachedLoc.name)
/** 用户经纬度（GCJ-02）：有值则首页推荐按距离排序 */
const userLoc = ref<{ lat: number; lng: number } | null>(cachedLoc.lat != null && cachedLoc.lng != null ? { lat: cachedLoc.lat, lng: cachedLoc.lng } : null)

/** 点击定位条：请求真实定位 → 成功更新展示 + 刷新推荐按距离排序；失败保持现状 */
async function onLocTap() {
  const next: LocationInfo | null = await refreshLocation()
  if (next) {
    currentLocation.value = next.name
    userLoc.value = next.lat != null && next.lng != null ? { lat: next.lat, lng: next.lng } : null
    uni.showToast({ title: next.lat != null ? '已定位：' + next.name : '定位失败，使用默认位置', icon: 'none' })
    // 定位变化 → 刷新推荐（食堂 coverflow + 热门菜品首屏按距离）
    loadData()
  }
}

/**
 * 顶部避让（2026-08-03 修复）：微信自定义导航必须动态适配
 * ① 状态栏高度（刘海屏非固定值，wx.getWindowInfo().statusBarHeight）
 * ② 右上角胶囊按钮（wx.getMenuButtonBoundingClientRect()，定位条让出其右侧宽度，避免被胶囊遮挡）
 */
const statusBarHeight = ref(20)
const menuButtonRight = ref(96)
function measureTopBar() {
  // @ts-ignore - 跨端兼容（H5 无 wx）
  const win = (typeof wx !== 'undefined' && wx.getWindowInfo) ? wx.getWindowInfo() : null
  statusBarHeight.value = (win && win.statusBarHeight) || 20
  // @ts-ignore - 微信特有：胶囊按钮位置
  const menu = (typeof wx !== 'undefined' && wx.getMenuButtonBoundingClientRect) ? wx.getMenuButtonBoundingClientRect() : null
  if (menu && win) {
    // 右侧避让 = 胶囊左边缘距屏幕右边缘的距离（+4 留余量）
    menuButtonRight.value = win.windowWidth - menu.left + 4
  }
}
onMounted(measureTopBar)

/** 首页搜索框 → 跳转搜索页（二级页，2026-08-03 搜索从 tab 改首页入口） */
function goToSearch() {
  uni.navigateTo({ url: '/pages/find/index' })
}

/** 菜品详情底部弹层（task-10：独立页 → sheet） */
const dishSheetOpen = ref(false)
const sheetDishId = ref(0)
function openDishSheet(id: number) {
  if (!id) return
  sheetDishId.value = id
  dishSheetOpen.value = true
}

const currentCanteen = ref('')
const pressed = ref(false)
const momentPressed = ref(false)
const loading = ref(true)
const loadFailed = ref(false)
const refresherTriggered = ref(false)

/** 广播通知：仅通知图标 + 文本内容，内容每秒上下滚动轮换（task-13 §1.1）。
 *  预留多种广播类型，按 type 分发跳转（不写死社区）。
 *  优先用后端公告（接口位）；未接入时回落本地默认公告，保证 UI 可演示。 */
interface BroadcastItem {
  text: string
  type: 'dish' | 'community' | 'url' | 'canteen' | 'stall'
  targetId?: number
  targetUrl?: string
}
const broadcastList = ref<BroadcastItem[]>([])
const broadcastIndex = ref(0)
let broadcastTimer: ReturnType<typeof setInterval> | null = null
/** 仅保留非空文本广播，供 ticker 渲染与点击跳转（与索引严格对应） */
const visibleBroadcasts = computed(() => broadcastList.value.filter(b => b && b.text && b.text.trim()))

function startBroadcastRotation() {
  if (broadcastTimer) clearInterval(broadcastTimer)
  if (visibleBroadcasts.value.length <= 1) return
  broadcastTimer = setInterval(() => {
    broadcastIndex.value = (broadcastIndex.value + 1) % visibleBroadcasts.value.length
  }, 3000)
}

function goBroadcast(index: number) {
  const b = visibleBroadcasts.value[index]
  if (!b) return
  switch (b.type) {
    case 'dish':
      if (b.targetId) openDishSheet(b.targetId)
      break
    case 'canteen':
      uni.navigateTo({ url: `/pages/pages-detail/canteen?canteen=${encodeURIComponent(b.text)}` })
      break
    case 'stall':
      uni.navigateTo({ url: '/pages/pages-detail/stall' })
      break
    case 'url':
      if (b.targetUrl) {
        openWebView(b.targetUrl)
      }
      break
    case 'community':
    default:
      // 无原生 tabBar，切社区用 reLaunch（CustomTabBar 统一管理）
      uni.reLaunch({ url: '/pages/community/index' })
      break
  }
}

async function loadBroadcast() {
  // 后端契约 A.14：GET /broadcasts（公开）。失败回落本地演示公告，保证 UI 可演示。
  try {
    const list = await getBroadcasts()
    broadcastList.value = list
  } catch {
    broadcastList.value = [
      { text: '欢迎来到食在交大，发现校园美食', type: 'community' },
      { text: '同学们都在吃什么 · 最新动态等你来逛', type: 'community' },
      { text: '发布菜品可获「平鉴官」认证，快来贡献', type: 'community' },
    ]
  }
  // 清洗空文本项，避免轮换中出现空行
  broadcastList.value = broadcastList.value.filter(b => b && b.text && b.text.trim())
  broadcastIndex.value = 0
  startBroadcastRotation()
}

interface CanteenEntry { name: string; image: string }

const canteens = computed<CanteenEntry[]>(() =>
  dishStore.canteenList.map(item => ({
    name: item.name,
    image: dishStore.canteenImageMap[item.name] || item.icon || '',
  }))
)

/** 全板块无数据：用于展示友好空状态（覆盖后端未起 / 无数据 / 网络异常等情况） */
const isAllEmpty = computed(() =>
  !loading.value &&
  dishStore.homeBanners.length === 0 &&
  dishStore.homeHotList.length === 0 &&
  dishStore.canteenList.length === 0
)

async function loadData() {
  loading.value = true
  loadFailed.value = false
  try {
    // 定位联动（方案 C）：有用户坐标时食堂/热门推荐按距离排序
    const lat = userLoc.value?.lat ?? null
    const lng = userLoc.value?.lng ?? null
    await Promise.all([
      dishStore.fetchHomeBanners(),
      dishStore.fetchCanteens(lat, lng),
      dishStore.fetchCanteenImages(),
      dishStore.fetchHomeHot(lat, lng),
    ])
    if (canteens.value.length > 0) {
      currentCanteen.value = canteens.value[0].name
    }
    loadBroadcast()
  } catch (e) {
    console.error('[home] 首页数据加载失败', e)
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

onLoad(() => { loadData() })
onShareAppMessage(() => buildSharePayload())

onUnmounted(() => {
  if (broadcastTimer) clearInterval(broadcastTimer)
})

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => { refresherTriggered.value = false })
}

/** 触底加载更多（热门瀑布流无限加载） */
function onScrollToLower() {
  if (dishStore.homeHotFinished || dishStore.homeHotLoadingMore) return
  dishStore.loadMoreHomeHot()
}

function goToCanteen(name: string) {
  uni.navigateTo({ url: `/pages/pages-detail/canteen?canteen=${encodeURIComponent(name)}` })
}

function goToDetail(dish: Dish) {
  openDishSheet(dish.id)
}

/** Banner 按 target_type 跳转（project_spec §3.x.2；URL 类型在小程序内用 web-view 打开公众号文章/H5） */
function handleBannerTap(banner: BannerItem) {
  switch (banner.targetType) {
    case 'DISH':
      if (banner.targetId) openDishSheet(banner.targetId)
      break
    case 'URL':
      if (banner.targetUrl) {
        openWebView(banner.targetUrl)
      }
      break
    case 'NONE':
    default:
      break
  }
}

/** 在小程序内打开外部链接（web-view 页），非法链接回退为复制链接提示 */
function openWebView(targetUrl: string) {
  if (/^https?:\/\/\S+$/i.test(targetUrl)) {
    uni.navigateTo({ url: `/pages/webview/index?url=${encodeURIComponent(targetUrl)}` })
  } else {
    uni.setClipboardData({ data: targetUrl, success: () => uni.showToast({ title: '已复制链接，请到浏览器打开', icon: 'none' }) })
  }
}
</script>

<style scoped>
.home-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }

/* ===== 顶部：定位 + 搜索框（2026-08-03：paddingTop 由 JS 动态设置避让状态栏+胶囊） ===== */
.home-top {
  padding-left: var(--spacing-lg);
  padding-right: var(--spacing-lg);
  padding-bottom: var(--spacing-md);
  background: var(--color-primary-glass);
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  box-sizing: border-box;
}
/* 定位条：垂直高度对齐胶囊按钮区域，右侧动态让出胶囊宽度 */
.loc-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: 64rpx;
  box-sizing: border-box;
  /* 可点（重新定位）：命中区 ≥44px + 按压反馈 */
  padding: 0 var(--spacing-sm);
  margin-left: calc(var(--spacing-sm) * -1);
  border-radius: var(--radius-tag);
  transition: opacity 120ms ease;
  -webkit-tap-highlight-color: transparent;
}
.loc-bar:active { opacity: 0.75; }
.loc-icon { flex-shrink: 0; line-height: 1; }
.loc-text { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-white); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.loc-arrow { flex-shrink: 0; line-height: 1; }
.home-search {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: 72rpx;
  padding: 0 var(--spacing-md);
  background: var(--bg-card);
  border-radius: 36rpx;
  box-shadow: var(--shadow-card);
  -webkit-tap-highlight-color: transparent;
  transition: transform 0.12s var(--ease-out);
}
.home-search:active { transform: scale(var(--press-scale)); }
.home-search-icon { flex-shrink: 0; line-height: 1; }
.home-search-placeholder { font-size: var(--font-body); color: var(--text-tertiary); }
.scroll-wrap { flex: 1; overflow-y: auto; width: 100%; padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom)); }
.swiper-section { padding: var(--spacing-sm) var(--spacing-md) 0; margin-bottom: var(--spacing-lg); }
.home-swiper { height: 320rpx; border-radius: var(--radius-card); overflow: hidden; }
.swiper-slide { height: 100%; display: flex; flex-direction: column; justify-content: center; align-items: center; position: relative; background: var(--color-primary); transition: opacity 120ms ease; -webkit-tap-highlight-color: transparent; }
.swiper-slide:active { opacity: 0.85; }
.swiper-img { position: absolute; inset: 0; width: 100%; height: 100%; }
.swiper-overlay { position: absolute; inset: 0; background: linear-gradient(to top, var(--overlay-dark-strong) 0%, var(--overlay-dark-soft) 50%, transparent 100%); }
.swiper-title { font-size: var(--font-h2); font-weight: var(--weight-bold); letter-spacing: var(--tracking-h2); color: var(--text-white); margin-bottom: 10rpx; z-index: 1; }
.swiper-subtitle { font-size: var(--font-body); color: var(--text-white-secondary); z-index: 1; }
/* 无图回退（2026-08-03 修复突兀）：柔和渐变 + 主色文字，像一张淡色卡片而非深色大色块 */
.swiper-slide-ph { background: var(--color-primary-soft); }
.swiper-slide-ph .swiper-title { color: var(--color-primary); font-size: var(--font-h3); }
.swiper-slide-ph .swiper-subtitle { color: var(--text-secondary); }
.section { padding: 0 var(--spacing-md); margin-bottom: var(--spacing-lg); width: 100%; box-sizing: border-box; }

/* ===== 首页广播通知条（细长 ticker，像系统通知而非内容卡） ===== */
.broadcast-section { margin-bottom: var(--spacing-md); }
.broadcast-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  height: 72rpx;
  padding: 0 var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  transition: transform 0.12s ease, background 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}
.broadcast-icon {
  flex-shrink: 0;
  opacity: 0.7;
}
/* 垂直滚动 ticker：单条当前项 + 上滑入场，绝不空白、不一次滚多条 */
.broadcast-ticker {
  flex: 1;
  min-width: 0;
  height: 40rpx;
  overflow: hidden;
}
.broadcast-line {
  height: 40rpx;
  display: flex;
  align-items: center;
  overflow: hidden;
}
.broadcast-line-enter {
  animation: broadcast-up 0.3s var(--ease-out);
}
@keyframes broadcast-up {
  from { transform: translateY(100%); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .broadcast-line-enter { animation: broadcast-fade 0.2s ease both; }
  @keyframes broadcast-fade {
    from { opacity: 0; }
    to { opacity: 1; }
  }
}
.broadcast-text {
  flex: 1;
  min-width: 0;
  font-size: var(--font-aux);
  color: var(--text-secondary);
  font-weight: var(--weight-medium);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.broadcast-single { opacity: 0.6; }

/* ===== 食堂入口 coverflow 横滑（中间大、两边露出半张、circular 循环） ===== */
.canteen-swiper { width: 100%; height: 280rpx; }
.canteen-card {
  position: relative;
  /* 左右对称居中：总宽 = 100% - 2×xs + 2×xs = 100%（避免左侧 margin 生效、右侧被裁的不对称） */
  width: calc(100% - 2 * var(--spacing-xs));
  height: 100%;
  margin: 0 var(--spacing-xs);
  border-radius: var(--radius-card);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  background: var(--bg-page);
  /* Apple highlight 按压：背景微变而非整卡缩放（与动态卡/find 混合卡一致） */
  transition: background-color 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.canteen-card:active { background-color: var(--bg-soft); }
.broadcast-bar.pressed { transform: scale(var(--press-scale)); }
.canteen-img { width: 100%; height: 100%; }
.canteen-img-placeholder { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.canteen-illu { opacity: 0.3; }
.swiper-placeholder { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.swiper-ph-text { font-size: var(--font-aux); color: var(--text-tertiary); }
.canteen-overlay { position: absolute; inset: 0; background: linear-gradient(to top, var(--overlay-dark-deep) 0%, var(--overlay-dark-soft) 50%, transparent 100%); }
.canteen-name { position: absolute; left: var(--spacing-md); bottom: var(--spacing-md); right: var(--spacing-md); font-size: var(--font-caption); font-weight: var(--weight-bold); color: var(--text-white); z-index: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* ===== 列表底部状态 ===== */
.list-footer { display: flex; align-items: center; justify-content: center; padding: var(--spacing-md) 0; gap: var(--spacing-xs); }
.footer-spinner { width: 28rpx; height: 28rpx; border: 4rpx solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
.footer-text { font-size: var(--font-aux); color: var(--text-tertiary); }
@keyframes spin { to { transform: rotate(360deg); } }

/* ========== 骨架屏 ========== */
.home-skeleton { padding: 0 var(--spacing-md); }
.sk-banner { width: 100%; height: 320rpx; margin-bottom: var(--spacing-md); }
.sk-canteen { width: 100%; height: 200rpx; margin-bottom: var(--spacing-md); }
.sk-grid { display: flex; flex-wrap: wrap; gap: var(--spacing-md); }
.sk-card { width: calc((100% - var(--spacing-md)) / 2); height: 300rpx; }

/* ========== 空状态 ========== */
.home-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl) var(--spacing-lg);
}
.empty-tip { font-size: var(--font-card); font-weight: var(--weight-semibold); color: var(--text-secondary); }
.empty-sub { margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; line-height: 1.5; }

@media (prefers-reduced-motion: reduce) {
  .footer-spinner { animation: none; }
}
</style>
