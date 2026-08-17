<template>
  <view class="page home-page" :class="{ 'theme-dark': theme.isDark }">
    <!-- 首页头部：单行星胶囊（头像 + 搜索框），朱砂红底，右上角留空避让胶囊 -->
    <Header
      variant="home"
      :avatar="isLoggedIn && userInfo?.avatar ? getImageUrl(userInfo.avatar) : ''"
      :nickname="isLoggedIn ? (userInfo?.nickname || '未命名') : '未登录'"
      search-placeholder="搜索你想吃的..."
      @avatar="goProfile"
      @search="goToSearch"
    />

    <scroll-view
      ref="scrollView"
      class="scroll-wrap"
      scroll-y
      :scroll-top="scrollTop"
      :scroll-with-animation="false"
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
      @scroll="onScroll"
      @scrolltolower="onScrollToLower"
    >
      <!-- 加载骨架屏（结构贴合真实首屏：广播条 + 两卡万能区 + 筛选条 + 双列瀑布流，避免加载完成跳变） -->
      <view v-if="loadingHot" class="home-skeleton">
        <view class="sk-broadcast skeleton" />
        <view class="sk-universal">
          <view class="sk-ucard skeleton" />
          <view class="sk-ucard skeleton" />
        </view>
        <view class="sk-filter skeleton" />
        <view class="sk-waterfall">
          <view class="sk-col">
            <view v-for="s in 3" :key="'l' + s" class="sk-wcard skeleton" />
          </view>
          <view class="sk-col">
            <view v-for="s in 3" :key="'r' + s" class="sk-wcard skeleton" />
          </view>
        </view>
      </view>

      <!-- 真实内容（首屏加载完成后始终渲染：广播/万能区独立于筛选切换，切换 tab 只刷新下方瀑布流） -->
      <view v-if="!loadingHot" class="home-content">
        <!-- 首页广播栏（运营广播 ticker，条内纵向滚动 + 自动轮播；置于滚动区内随滚轮上移） -->
        <BroadcastBar :items="broadcasts" @select="onBroadcastTap" />

        <!-- 两列万能区：最新活动 / 反馈菜品（活动后端暂不补，无活动时不渲染活动入口，避免空洞可点项） -->
        <view class="section enter-up" :style="{ '--enter-i': 0, 'margin-top': '0' }">
          <UniversalGrid @open-activity="goToActivity" @open-feedback="goToFeedback" />
        </view>

        <!-- 未授权定位：轻提示开启，首页瀑布流「距你」才有数据 -->
        <view v-if="showLocHint" class="loc-hint" @tap="enableLocation">
          <text class="loc-hint-text">开启定位，查看菜品距你多远</text>
          <text class="loc-hint-arrow">›</text>
        </view>

        <!-- 筛选 Tab + 瀑布流（合并为单一组件：切换 tab 只刷新下方内容区，不影响上方广播/万能区） -->
        <HomeFeed :load-failed="loadFailed" />
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 回到顶部悬浮按钮（A.4）：右下角，长瀑布流体感 -->
    <view
      v-if="showBackTop"
      class="fab fab-backtop"
      :class="{ 'fab-show': showBackTop }"
      @tap="scrollToTop"
      aria-label="回到顶部"
    >
      <IconSvg name="up" :size="44" color="var(--color-primary)" />
    </view>

    <!-- 认证弹层（未登录点赞/写评价等 requireAuth 统一在此弹出） -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { useLocationStore } from '@/stores/location'
import { getUserLocation } from '@/utils/location'
import type { BroadcastItem } from '@/api/notify'
import { getMoments } from '@/api/moment'
import type { Moment } from '@/types/moment'
import { getImageUrl } from '@/utils/image'
import { buildSharePayload } from '@/utils/shareState'
import Header from '@/components/header.vue'
import IconSvg from '@/components/IconSvg.vue'
import BroadcastBar from '@/components/BroadcastBar.vue'
import HomeFeed from '@/components/HomeFeed.vue'
import UniversalGrid from '@/components/UniversalGrid.vue'
import AuthSheet from '@/components/AuthSheet.vue'

const theme = useThemeStore()
const dishStore = useDishStore()
const userStore = useUserStore()
const locationStore = useLocationStore()
const userInfo = computed(() => userStore.userInfo)
const isLoggedIn = computed(() => userStore.isLoggedIn())

/** 首页头像 → 个人页（带 from=home，使「我的」页显示返回箭头） */
function goProfile() {
  uni.navigateTo({ url: '/pages/profile/index?from=home' })
}
/** 首页搜索图标 → 搜索页 */
function goToSearch() {
  uni.navigateTo({ url: '/pages/find/index' })
}

/** 两列万能区：最新活动 → 活动页 */
function goToActivity() {
  uni.navigateTo({ url: '/pages/activity/index' })
}
/** 两列万能区：反馈菜品 → 反馈页（带 object=dish，进入后预选「内容纠错」类型） */
function goToFeedback() {
  uni.navigateTo({ url: '/pages/feedback/index?object=dish' })
}

/** 未授权定位时展示轻提示（首页瀑布流「距你」才有数据） */
const showLocHint = computed(() =>
  !loadingHot.value &&
  !locationStore.location &&
  dishStore.filterList.length > 0
)
/** 点击提示开启定位，成功后重拉当前筛选以本地重算距离 */
async function enableLocation() {
  try {
    const loc = await getUserLocation()
    if (loc) {
      locationStore.setLocation(loc)
      if (dishStore.filterTab) await dishStore.fetchFilterDishes(dishStore.filterTab, true)
    }
  } catch (e) {
    uni.showToast({ title: '定位未开启', icon: 'none' })
  }
}

/** 首页广播条点击 → 动态入口：有 targetId 跳该条动态详情，否则回落动态列表 */
function onBroadcastTap(item: BroadcastItem) {
  if (!item) return
  if (item.targetId) {
    uni.navigateTo({ url: `/pages/pages-detail/moment?id=${item.targetId}` })
  } else {
    uni.navigateTo({ url: '/pages/community/index' })
  }
}

const loadingHot = ref(true)
const loadFailed = ref(false)
const refresherTriggered = ref(false)

// 广播栏（最新动态摘录）
const broadcasts = ref<BroadcastItem[]>([])

/** 动态 → 广播项：只取动态内容文字，统一类型为 community（动态入口） */
function toBroadcastItem(moment: Moment): BroadcastItem {
  return {
    text: moment.content || '',
    type: 'community',
    targetId: moment.id,
  }
}

async function loadData() {
  loadingHot.value = true
  loadFailed.value = false
  try {
    // 先取定位（会话级缓存），首页推荐「距你」才能本地算距离
    await ensureLocation()
    const momentRes = await getMoments({ tab: 'latest', page: 1, pageSize: 5 })
    // 首页筛选流（默认「面食」维度，扁平平铺美食类型，无综合头条）
    await dishStore.fetchFilterDishes({ key: 'noodle', label: '面食', type: 'tag', payload: 'noodle' }, true)
    // 广播只广播动态（最新动态摘录，作为动态入口）
    broadcasts.value = (momentRes?.list || []).map(toBroadcastItem)
  } catch (e) {
    console.error('[home] 首页数据加载失败', e)
    loadFailed.value = true
  } finally {
    loadingHot.value = false
  }
}

/** 确保拿到用户坐标（会话级缓存，避免重复授权）；失败静默降级 */
async function ensureLocation() {
  if (locationStore.location) return
  try {
    const loc = await getUserLocation()
    if (loc) locationStore.setLocation(loc)
  } catch (e) {
    // 用户拒绝授权 / 定位不可用：静默，距离降级
  }
}

onLoad(() => { loadData() })
onShareAppMessage(() => buildSharePayload())

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => {
    refresherTriggered.value = false
  })
}

/** 触底加载更多（筛选瀑布流无限加载） */
function onScrollToLower() {
  if (dishStore.filterFinished || dishStore.filterLoadingMore) return
  dishStore.loadMoreFilterDishes()
}

/** 回到顶部（A.4）：受控 scroll-view 滚动到顶 */
const scrollView = ref()
const scrollTop = ref(0)
const showBackTop = ref(false)
// 非响应式记录滚动位置，避免每次滚动都 setData（微信小程序受控 scroll-top 每帧回写会严重掉帧）
let scrollPos = 0
let backTopVisible = false
function onScroll(e: any) {
  scrollPos = e.detail.scrollTop
  const now = scrollPos > 600
  // 仅在跨越阈值时翻转，避免每帧 setData
  if (now !== backTopVisible) {
    backTopVisible = now
    showBackTop.value = now
  }
}
function scrollToTop() {
  // 受控 scroll-top 双写 trick：先偏离再归零，确保触发滚动
  scrollTop.value = scrollPos > 0 ? scrollPos - 1 : 1
  requestAnimationFrame(() => {
    scrollTop.value = 0
  })
}
</script>

<style scoped>
.home-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }

/* ===== 顶部 Header（组件内渲染头像行与整行搜索框，首页不再额外定义） ===== */
.scroll-wrap { flex: 1; overflow-y: auto; width: 100%; padding-bottom: env(safe-area-inset-bottom); }
/* 区块纵向节奏统一 24rpx 基准（广播→万能→标题→瀑布流衔接紧凑，消灭异常 72rpx）；左右留白统一单层 24rpx */
.section { padding: 0 var(--spacing-md); margin: var(--spacing-md) 0; width: 100%; box-sizing: border-box; }

/* ===== 列表底部状态 ===== */
.list-footer { display: flex; align-items: center; justify-content: center; padding: var(--spacing-md) 0; gap: var(--spacing-xs); }

/* 未授权定位轻提示条 */
.loc-hint {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin: var(--spacing-md) var(--spacing-md) 0;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--color-accent-soft);
  border-radius: var(--radius-card);
  -webkit-tap-highlight-color: transparent;
}
.loc-hint-text { flex: 1; min-width: 0; font-size: var(--font-aux); color: var(--color-accent); }
.loc-hint-arrow { flex-shrink: 0; font-size: 36rpx; line-height: 1; color: var(--color-accent); }
.footer-spinner { width: 28rpx; height: 28rpx; border: 4rpx solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
.footer-text { font-size: var(--font-aux); color: var(--text-tertiary); }
@keyframes spin { to { transform: rotate(360deg); } }

/* ========== 骨架屏（贴合真实首屏结构） ========== */
.home-skeleton { padding: var(--spacing-md) 0; }
.sk-filter { width: calc(100% - var(--spacing-md) * 2); height: 88rpx; margin: var(--spacing-md) var(--spacing-md) 0; border-radius: var(--radius-card); box-sizing: border-box; }
.sk-broadcast { width: calc(100% - var(--spacing-md) * 2); height: 88rpx; margin: var(--spacing-md) var(--spacing-md) 0; border-radius: var(--radius-card); box-sizing: border-box; }
.sk-universal { display: flex; gap: var(--spacing-md); padding: 0 var(--spacing-md); margin-top: var(--spacing-md); box-sizing: border-box; }
.sk-ucard { flex: 1; height: 100rpx; border-radius: var(--radius-card); }
.sk-waterfall { display: flex; gap: var(--spacing-md); padding: var(--spacing-md); box-sizing: border-box; }
.sk-col { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-md); }
.sk-wcard { width: 100%; height: 300rpx; border-radius: var(--radius-card); }

/* ===== 回到顶部悬浮按钮（A.4 / C.10：命中区 ≥44px） ===== */
.fab-backtop {
  position: fixed;
  right: var(--spacing-lg);
  bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom));
  width: 84rpx;
  height: 84rpx;
  min-width: 44px;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-card);
  border: 2rpx solid var(--color-primary);
  border-radius: 50%;
  box-shadow: var(--shadow-card);
  z-index: 50;
  opacity: 0;
  transform: translateY(16rpx) scale(0.9);
  pointer-events: none;
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.fab-backtop.fab-show { opacity: 1; transform: translateY(0) scale(1); pointer-events: auto; }
.fab-backtop:active { transform: scale(var(--press-scale)); }

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
