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

    <!-- 首页广播栏（运营广播 ticker） -->
    <BroadcastBar :items="broadcasts" @select="onBroadcastTap" />

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
      <!-- 加载骨架屏（结构贴合真实首屏：广播条 + 两卡万能区 + 双列瀑布流，避免加载完成跳变） -->
      <view v-if="loadingHot" class="home-skeleton">
        <view class="sk-broadcast skeleton" />
        <view class="sk-universal">
          <view class="sk-ucard skeleton" />
          <view class="sk-ucard skeleton" />
        </view>
        <view class="sk-waterfall">
          <view class="sk-col">
            <view v-for="s in 3" :key="'l' + s" class="sk-wcard skeleton" />
          </view>
          <view class="sk-col">
            <view v-for="s in 3" :key="'r' + s" class="sk-wcard skeleton" />
          </view>
        </view>
      </view>

      <!-- 空状态 -->
      <view v-else-if="isAllEmpty" class="home-empty">
        <IconSvg name="empty" :size="120" color="var(--text-tertiary)" />
        <text class="empty-tip">{{ loadFailed ? '加载失败' : '暂时没有内容' }}</text>
        <text class="empty-sub">{{ loadFailed ? '网络异常或后端未启动，下拉刷新后重试' : '下拉刷新，或确认后端已启动、网络可访问后重试' }}</text>
      </view>

      <block v-else>
        <!-- 两列万能区：最新活动 / 反馈菜品 -->
        <view class="section enter-up" :style="{ '--enter-i': 0 }">
          <UniversalGrid :has-activity="activities.length > 0" @open-activity="goToActivity" @open-feedback="goToFeedback" />
        </view>

        <!-- 未授权定位：轻提示开启，首页瀑布流「距你」才有数据 -->
        <view v-if="showLocHint" class="loc-hint" @tap="enableLocation">
          <text class="loc-hint-text">开启定位，查看菜品距你多远</text>
          <text class="loc-hint-arrow">›</text>
        </view>

        <!-- 区块标题（B.7）：朱砂红竖条 + 大字，强化层级 -->
        <view class="section block-title enter-up" v-if="dishStore.homeHotList.length > 0" :style="{ '--enter-i': 1 }">
          <view class="block-title-left">
            <text class="block-title-bar" />
            <text class="block-title-text">热门菜品</text>
          </view>
          <view class="block-title-more" @tap="goToFind">
            <text>查看全部</text>
            <text class="block-title-arrow">›</text>
          </view>
        </view>

        <!-- 热门菜品（双列瀑布流 + 无限加载） -->
        <view class="section enter-up" v-if="dishStore.homeHotList.length > 0" :style="{ '--enter-i': 2 }">
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
import { getBroadcasts, type BroadcastItem } from '@/api/notify'
import { getActivities, type ActivityItem } from '@/api/activity'
import { getImageUrl } from '@/utils/image'
import { buildSharePayload } from '@/utils/shareState'
import type { Dish } from '@/types/dish'
import WaterfallList from '@/components/WaterfallList.vue'
import Header from '@/components/header.vue'
import IconSvg from '@/components/IconSvg.vue'
import BroadcastBar from '@/components/BroadcastBar.vue'
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
/** 区块标题「查看全部」→ 发现页（复用 find 列表） */
function goToFind() {
  uni.navigateTo({ url: '/pages/find/index' })
}

/** 菜品卡片点击 → 独立详情页（pages-detail/dish） */
function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

/** 两列万能区：最新活动 → 活动页 */
function goToActivity() {
  uni.navigateTo({ url: '/pages/activity/index' })
}
/** 两列万能区：反馈菜品 → 反馈页 */
function goToFeedback() {
  uni.navigateTo({ url: '/pages/feedback/index' })
}

/** 未授权定位时展示轻提示（首页瀑布流「距你」才有数据） */
const showLocHint = computed(() =>
  !loadingHot.value &&
  !locationStore.location &&
  dishStore.homeHotList.length > 0
)
/** 点击提示开启定位，成功后重拉首页热门以本地重算距离 */
async function enableLocation() {
  try {
    const loc = await getUserLocation()
    if (loc) {
      locationStore.setLocation(loc)
      await dishStore.fetchHomeHot()
    }
  } catch (e) {
    uni.showToast({ title: '定位未开启', icon: 'none' })
  }
}

/** 首页广播条点击 → 按 type/targetId 跳转（动态详情 / 活动页 / 动态流） */
function onBroadcastTap(item: BroadcastItem) {
  if (!item) return
  switch (item.type) {
    case 'MOMENT':
      if (item.targetId) {
        uni.navigateTo({ url: `/pages/pages-detail/moment?id=${item.targetId}` })
        return
      }
      uni.navigateTo({ url: '/pages/community/index' })
      break
    case 'ACTIVITY':
      uni.navigateTo({ url: '/pages/activity/index' })
      break
    default:
      uni.navigateTo({ url: '/pages/community/index' })
  }
}

const loadingHot = ref(true)
const loadFailed = ref(false)
const refresherTriggered = ref(false)

// 广播栏（运营广播）+ 万能区域（最新活动）
const broadcasts = ref<BroadcastItem[]>([])
const activities = ref<ActivityItem[]>([])

/** 全板块无数据：用于展示友好空状态 */
const isAllEmpty = computed(() =>
  !loadingHot.value &&
  dishStore.homeHotList.length === 0
)

async function loadData() {
  loadingHot.value = true
  loadFailed.value = false
  try {
    // 先取定位（会话级缓存），首页热门「距你」才能本地算距离
    await ensureLocation()
    const [_, bcRes, actRes] = await Promise.all([
      dishStore.fetchHomeHot(),
      getBroadcasts(),
      getActivities({ page: 1, pageSize: 2 }),
    ])
    broadcasts.value = bcRes || []
    activities.value = (actRes || []).slice(0, 2)
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

/** 触底加载更多（热门瀑布流无限加载） */
function onScrollToLower() {
  if (dishStore.homeHotFinished || dishStore.homeHotLoadingMore) return
  dishStore.loadMoreHomeHot()
}

/** 回到顶部（A.4）：受控 scroll-view 滚动到顶 */
const scrollView = ref()
const scrollTop = ref(0)
const showBackTop = ref(false)
function onScroll(e: any) {
  scrollTop.value = e.detail.scrollTop
  showBackTop.value = scrollTop.value > 600
}
function scrollToTop() {
  scrollTop.value = 0
}
</script>

<style scoped>
.home-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }

/* ===== 顶部 Header（组件内渲染头像行与整行搜索框，首页不再额外定义） ===== */
.scroll-wrap { flex: 1; overflow-y: auto; width: 100%; padding-bottom: env(safe-area-inset-bottom); }
/* A.2 区块间距放大到 48rpx 量级，首屏更透气（替代原 spacing-md/lg 拥挤间距） */
.section { padding: 0 var(--spacing-md); margin: var(--spacing-xl) 0; width: 100%; box-sizing: border-box; }

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
.sk-broadcast { width: calc(100% - var(--spacing-md) * 2); height: 64rpx; margin: 0 var(--spacing-md) var(--spacing-md); border-radius: var(--radius-card); box-sizing: border-box; }
.sk-universal { display: flex; gap: var(--spacing-md); padding: 0 var(--spacing-md); box-sizing: border-box; }
.sk-ucard { flex: 1; height: 72rpx; border-radius: var(--radius-card); }
.sk-waterfall { display: flex; gap: var(--spacing-md); padding: var(--spacing-md); box-sizing: border-box; }
.sk-col { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-md); }
.sk-wcard { width: 100%; height: 300rpx; border-radius: var(--radius-card); }

/* ===== 区块标题（B.7）：朱砂红竖条 + 大字，强化视觉层级 ===== */
.block-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0; }
.block-title-left { display: flex; align-items: center; gap: var(--spacing-sm); }
.block-title-bar { width: 8rpx; height: 32rpx; border-radius: 4rpx; background: var(--color-primary); flex-shrink: 0; }
.block-title-text { font-size: var(--font-h2); font-weight: var(--weight-bold); color: var(--text-primary); letter-spacing: var(--tracking-h3); }
.block-title-more { display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-sm); min-height: 44px; border-radius: var(--radius-tag); -webkit-tap-highlight-color: transparent; }
.block-title-more:active { background: var(--bg-soft); }
.block-title-more text { font-size: var(--font-aux); color: var(--text-secondary); }
.block-title-arrow { font-size: 32rpx; line-height: 1; color: var(--text-tertiary); }

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
