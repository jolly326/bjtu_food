<template>
  <view class="page home-page">
    <Header title="食在交大" />
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
          <swiper v-if="dishStore.homeBanners.length > 0" class="home-swiper" indicator-dots indicator-color="rgba(255,255,255,0.4)"
            indicator-active-color="#FFFFFF" autoplay interval="3000" circular>
            <swiper-item v-for="(item, idx) in dishStore.homeBanners" :key="idx">
              <view class="swiper-slide" @tap="handleBannerTap(item)">
                <image v-if="item.image" class="swiper-img" :src="item.image" mode="aspectFill" @error="item.image = ''" />
                <view class="swiper-overlay" />
                <text class="swiper-title">{{ item.title }}</text>
                <text class="swiper-subtitle">{{ item.subtitle }}</text>
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

      <!-- 食堂入口（横滑卡片）：图 + 名称；点击进食堂详情（游客可进） -->
        <view class="section enter-up" v-if="canteens.length > 0" :style="{ '--enter-i': 1 }">
          <SectionTitle title="食堂入口" />
          <scroll-view class="horiz-scroll" scroll-x show-scrollbar="false">
            <view class="horiz-track">
              <view
                v-for="item in canteens"
                :key="item.name"
                class="canteen-card"
                :class="{ pressed }"
                @touchstart="pressed = true"
                @touchend="pressed = false"
                @touchcancel="pressed = false"
                @mousedown="pressed = true"
                @mouseup="pressed = false"
                @mouseleave="pressed = false"
                @tap="goToCanteen(item.name)"
              >
                <image v-if="item.image" class="canteen-img" :src="item.image" mode="aspectFill" />
                <view v-else class="canteen-img canteen-img-placeholder">
                  <IconSvg name="dish" :size="80" color="var(--text-tertiary)" class="canteen-illu" />
                </view>
                <view class="canteen-overlay" />
                <text class="canteen-name">{{ item.name }}</text>
              </view>
            </view>
          </scroll-view>
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
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import IconSvg from '@/components/IconSvg.vue'
import { useDishStore } from '@/stores/dish'
import { getBroadcasts } from '@/api/broadcast'
import type { Dish } from '@/types/dish'
import type { BannerItem } from '@/types/banner'

const dishStore = useDishStore()

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
      if (b.targetId) uni.navigateTo({ url: `/pages/pages-detail/dish?id=${b.targetId}` })
      break
    case 'canteen':
      uni.navigateTo({ url: `/pages/pages-detail/canteen?canteen=${encodeURIComponent(b.text)}` })
      break
    case 'stall':
      uni.navigateTo({ url: '/pages/pages-detail/stall' })
      break
    case 'url':
      if (b.targetUrl) uni.navigateTo({ url: `/pages/webview/index?src=${encodeURIComponent(b.targetUrl)}` })
      break
    case 'community':
    default:
      uni.switchTab({ url: '/pages/community/index' })
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

function goToCommunity() {
  uni.switchTab({ url: '/pages/community/index' })
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
    await Promise.all([
      dishStore.fetchHomeBanners(),
      dishStore.fetchCanteens(),
      dishStore.fetchCanteenImages(),
      dishStore.fetchHomeHot(),
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
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

/** Banner 按 target_type 跳转（project_spec §3.x.2，task-12.9 去除 ACTIVITY，URL 走 web-view/复制链接） */
function handleBannerTap(banner: BannerItem) {
  switch (banner.targetType) {
    case 'DISH':
      if (banner.targetId) uni.navigateTo({ url: `/pages/pages-detail/dish?id=${banner.targetId}` })
      break
    case 'URL':
      if (banner.targetUrl) {
        const url = banner.targetUrl
        // 公众号文章 / H5：尝试 web-view 打开，未配置业务域名时回落复制链接
        uni.navigateTo({
          url: `/pages/webview/index?src=${encodeURIComponent(url)}`,
          fail: () => {
            uni.setClipboardData({ data: url, success: () => uni.showToast({ title: '链接已复制', icon: 'none' }) })
          },
        })
      }
      break
    case 'NONE':
    default:
      break
  }
}
</script>

<style scoped>
.home-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; width: 100%; padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom)); }
.swiper-section { padding: var(--spacing-sm) var(--spacing-md) 0; margin-bottom: var(--spacing-lg); }
.home-swiper { height: 320rpx; border-radius: var(--radius-card); overflow: hidden; }
.swiper-slide { height: 100%; display: flex; flex-direction: column; justify-content: center; align-items: center; position: relative; background: var(--color-primary); }
.swiper-img { position: absolute; inset: 0; width: 100%; height: 100%; }
.swiper-overlay { position: absolute; inset: 0; background: linear-gradient(to top, var(--overlay-dark-strong) 0%, var(--overlay-dark-soft) 50%, rgba(0,0,0,0) 100%); }
.swiper-title { font-size: var(--font-h2); font-weight: 700; letter-spacing: -0.01em; color: var(--text-white); margin-bottom: 10rpx; z-index: 1; }
.swiper-subtitle { font-size: var(--font-body); color: var(--text-white-secondary); z-index: 1; }
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
  animation: broadcast-up 0.45s var(--ease-out);
}
@keyframes broadcast-up {
  from { transform: translateY(100%); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
.broadcast-text {
  flex: 1;
  min-width: 0;
  font-size: var(--font-aux);
  color: var(--text-secondary);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.broadcast-single { opacity: 0.6; }

/* ===== 食堂入口横滑卡片 ===== */
.horiz-scroll { overflow-x: auto; white-space: nowrap; }
.horiz-scroll::-webkit-scrollbar { display: none; }
.horiz-track { display: inline-flex; gap: var(--spacing-md); padding-bottom: 4rpx; }
.canteen-card {
  position: relative;
  width: 240rpx;
  height: 300rpx;
  border-radius: var(--radius-card);
  overflow: hidden;
  box-shadow: var(--shadow-card);
  flex-shrink: 0;
  background: var(--bg-page);
  transition: transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.canteen-card.pressed { transform: scale(0.97); }
.broadcast-bar.pressed { transform: scale(var(--press-scale)); }
.canteen-img { width: 100%; height: 100%; }
.canteen-img-placeholder { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.canteen-illu { opacity: 0.3; }
.swiper-placeholder { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.swiper-ph-text { font-size: var(--font-aux); color: var(--text-tertiary); }
.canteen-overlay { position: absolute; inset: 0; background: linear-gradient(to top, var(--overlay-dark-deep) 0%, var(--overlay-dark-soft) 50%, rgba(0,0,0,0) 100%); }
.canteen-name { position: absolute; left: var(--spacing-md); bottom: var(--spacing-md); right: var(--spacing-md); font-size: var(--font-caption); font-weight: 700; color: var(--text-white); z-index: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

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
.empty-illu { font-size: 140rpx; line-height: 1; opacity: 0.32; margin-bottom: var(--spacing-md); }
.empty-tip { font-size: var(--font-card); font-weight: 600; color: var(--text-secondary); }
.empty-sub { margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; line-height: 1.5; }

@media (prefers-reduced-motion: reduce) {
  .footer-spinner { animation-duration: 1.4s; }
}
</style>
