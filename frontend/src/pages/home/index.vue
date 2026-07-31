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
        <text class="empty-illu">{{ EMOJI.dishPlaceholder }}</text>
        <text class="empty-tip">暂时没有内容</text>
        <text class="empty-sub">下拉刷新，或确认后端已启动、网络可访问后重试</text>
      </view>

      <block v-else>
        <!-- Banner 轮播（按 target_type 跳转）；无数据时整块隐藏，不留空白区 -->
        <view class="swiper-section enter-up" v-if="dishStore.homeBanners.length > 0" :style="{ '--enter-i': 0 }">
          <swiper class="home-swiper" indicator-dots indicator-color="rgba(255,255,255,0.4)"
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
        </view>

        <!-- 广播通知条：细长横向 ticker，左侧喇叭图标 + 一行省略文案 + 右侧查看全部，点击进社区 -->
      <view class="section enter-up broadcast-section" v-if="true" :style="{ '--enter-i': 1 }">
        <view
          class="broadcast-bar"
          :class="{ pressed: momentPressed }"
          @touchstart="momentPressed = true"
          @touchend="momentPressed = false"
          @touchcancel="momentPressed = false"
          @mousedown="momentPressed = true"
          @mouseup="momentPressed = false"
          @mouseleave="momentPressed = false"
          @tap="goToCommunity"
        >
          <text class="broadcast-icon">{{ EMOJI.bell }}</text>
          <text class="broadcast-text">{{ EMOJI.fire }} 同学们都在吃什么 · 最新动态</text>
          <text class="broadcast-more">查看全部 ›</text>
        </view>
      </view>

      <!-- 食堂入口（横滑卡片）：图 + 名称 + 营业状态徽标；点击进食堂详情（游客可进） -->
        <view class="section enter-up" v-if="canteens.length > 0" :style="{ '--enter-i': 1 }">
          <view class="section-head">
            <view class="section-bar" />
            <text class="section-title">食堂入口</text>
          </view>
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
                  <text class="canteen-illu">{{ EMOJI.canteenDish }}</text>
                </view>
                <view class="canteen-overlay" />
                <view class="status-badge" :class="item.open ? 'open' : 'closed'">
                  <text class="status-emoji">{{ item.open ? EMOJI.lockOpen : EMOJI.lock }}</text>
                  <text class="status-text">{{ item.open ? '营业中' : '休息中' }}</text>
                </view>
                <text class="canteen-name">{{ item.name }}</text>
              </view>
            </view>
          </scroll-view>
        </view>

        <!-- 热门菜品（双列瀑布流 + 无限加载） -->
        <view class="section enter-up" v-if="dishStore.homeHotList.length > 0" :style="{ '--enter-i': 2 }">
          <view class="section-head">
            <view class="section-bar" />
            <text class="section-title">热门菜品</text>
            <text class="section-sub">· 上拉加载更多</text>
          </view>
          <WaterfallList :list="dishStore.homeHotList">
            <template #card="{ item: dish }">
              <DishCard :dish="dish" @click="goToDetail" />
            </template>
          </WaterfallList>

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
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { EMOJI } from '@/utils/emoji'
import Header from '@/components/header.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import DishCard from '@/components/DishCard.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish } from '@/types/dish'
import type { BannerItem } from '@/types/banner'

const dishStore = useDishStore()

const currentCanteen = ref('')
const pressed = ref(false)
const momentPressed = ref(false)
const loading = ref(true)
const refresherTriggered = ref(false)

interface CanteenEntry { name: string; image: string; open: boolean }

const canteens = computed<CanteenEntry[]>(() =>
  dishStore.canteenList.map(item => ({
    name: item.name,
    image: dishStore.canteenImageMap[item.name] || item.icon || '',
    // 食堂营业状态：一期无 canteen 级 business_hours，默认 open 展示（stub，等待后端补全）
    open: true,
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
  } catch (e) {
    console.error('[home] 首页数据加载失败', e)
  } finally {
    loading.value = false
  }
}

onLoad(() => { loadData() })

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
        // 公众号文章 / H5：尝试 web-view 打开，未配置业务域名时回落复制链接
        uni.navigateTo({
          url: `/pages/webview/index?src=${encodeURIComponent(banner.targetUrl)}`,
          fail: () => {
            uni.setClipboardData({ data: banner.targetUrl, success: () => uni.showToast({ title: '链接已复制', icon: 'none' }) })
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
.scroll-wrap { flex: 1; overflow-y: auto; }
.swiper-section { padding: var(--spacing-sm) var(--spacing-md) 0; margin-bottom: var(--spacing-lg); }
.home-swiper { height: 320rpx; border-radius: var(--radius-card); overflow: hidden; }
.swiper-slide { height: 100%; display: flex; flex-direction: column; justify-content: center; align-items: center; position: relative; background: var(--color-gradient); }
.swiper-img { position: absolute; inset: 0; width: 100%; height: 100%; }
.swiper-overlay { position: absolute; inset: 0; background: linear-gradient(to top, var(--overlay-dark-strong) 0%, var(--overlay-dark-soft) 50%, rgba(0,0,0,0) 100%); }
.swiper-title { font-size: var(--font-h2); font-weight: 700; letter-spacing: -0.01em; color: var(--text-white); margin-bottom: 10rpx; z-index: 1; }
.swiper-subtitle { font-size: var(--font-body); color: var(--text-white-secondary); z-index: 1; }
.section { padding: 0 var(--spacing-md); margin-bottom: var(--spacing-lg); }

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
  font-size: 30rpx;
  line-height: 1;
  flex-shrink: 0;
  opacity: 0.7;
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
.broadcast-more {
  flex-shrink: 0;
  font-size: var(--font-aux);
  color: var(--color-primary);
  font-weight: 600;
}
.section-head { display: flex; align-items: center; margin-bottom: var(--spacing-sm); }
.section-bar { width: 8rpx; height: 32rpx; border-radius: 999rpx; background: var(--color-primary); margin-right: var(--spacing-xs); flex-shrink: 0; }
.section-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); letter-spacing: -0.01em; }
.section-sub { font-size: var(--font-aux); color: var(--text-tertiary); margin-left: var(--spacing-xs); }

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
.broadcast-bar.pressed { transform: scale(0.985); }
.canteen-img { width: 100%; height: 100%; }
.canteen-img-placeholder { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.canteen-illu { font-size: 80rpx; line-height: 1; opacity: 0.3; }
.canteen-overlay { position: absolute; inset: 0; background: linear-gradient(to top, var(--overlay-dark-deep) 0%, var(--overlay-dark-soft) 50%, rgba(0,0,0,0) 100%); }
.canteen-name { position: absolute; left: var(--spacing-md); bottom: var(--spacing-md); right: var(--spacing-md); font-size: var(--font-caption); font-weight: 700; color: var(--text-white); z-index: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.status-badge { position: absolute; top: var(--spacing-sm); left: var(--spacing-sm); z-index: 2; display: inline-flex; align-items: center; gap: 4rpx; padding: 4rpx 12rpx; border-radius: var(--radius-tag); backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px); }
.status-badge.open { background: var(--color-success-soft); }
.status-badge.closed { background: var(--bg-card); }
.status-emoji { font-size: 20rpx; line-height: 1; }
.status-text { font-size: 20rpx; font-weight: 700; }
.status-badge.open .status-text { color: var(--color-success); }
.status-badge.closed .status-text { color: var(--text-tertiary); }

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
