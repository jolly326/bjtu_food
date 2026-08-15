<template>
  <view class="page home-page" :class="{ 'theme-dark': theme.isDark }">
    <!-- 首页头部：两行（头像行 + 整行搜索框），无返回键，右上角留空避让胶囊 -->
    <Header
      variant="home"
      :avatar="isLoggedIn && userInfo?.avatar ? getImageUrl(userInfo.avatar) : ''"
      :nickname="isLoggedIn ? (userInfo?.nickname || '未命名') : '未登录'"
      search-placeholder="搜索你想吃的..."
      @avatar="goProfile"
      @search="goToSearch"
    />

    <!-- 首页广播栏（运营广播 ticker） -->
    <BroadcastBar :items="broadcasts" />

    <scroll-view
      class="scroll-wrap"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
      @scrolltolower="onScrollToLower"
    >
      <!-- 加载骨架屏 -->
      <view v-if="loadingHot" class="home-skeleton">
        <view class="sk-moment skeleton" />
        <view class="sk-grid">
          <view v-for="s in 4" :key="s" class="sk-card skeleton" />
        </view>
      </view>

      <!-- 空状态 -->
      <view v-else-if="isAllEmpty" class="home-empty">
        <IconSvg name="empty" :size="120" color="var(--text-tertiary)" />
        <text class="empty-tip">{{ loadFailed ? '加载失败' : '暂时没有内容' }}</text>
        <text class="empty-sub">{{ loadFailed ? '网络异常或后端未启动，下拉刷新后重试' : '下拉刷新，或确认后端已启动、网络可访问后重试' }}</text>
      </view>

      <block v-else>
        <!-- 万能区域：最新活动预览（最近活动 + 查看全部） -->
        <ActivityPreview :items="activities" />

        <!-- 热门菜品（双列瀑布流 + 无限加载） -->
        <view class="section enter-up" v-if="dishStore.homeHotList.length > 0" :style="{ '--enter-i': 1 }">
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
import { getBroadcasts, type BroadcastItem } from '@/api/notify'
import { getActivities, type ActivityItem } from '@/api/activity'
import { getImageUrl } from '@/utils/image'
import { buildSharePayload } from '@/utils/shareState'
import type { Dish } from '@/types/dish'
import WaterfallList from '@/components/WaterfallList.vue'
import Header from '@/components/header.vue'
import IconSvg from '@/components/IconSvg.vue'
import BroadcastBar from '@/components/BroadcastBar.vue'
import ActivityPreview from '@/components/ActivityPreview.vue'
import AuthSheet from '@/components/AuthSheet.vue'

const theme = useThemeStore()
const dishStore = useDishStore()
const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)
const isLoggedIn = computed(() => userStore.isLoggedIn())

/** 首页头像 → 个人页 */
function goProfile() {
  uni.navigateTo({ url: '/pages/profile/index' })
}
/** 首页搜索图标 → 搜索页 */
function goToSearch() {
  uni.navigateTo({ url: '/pages/find/index' })
}

/** 菜品卡片点击 → 独立详情页（pages-detail/dish） */
function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
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
    const [_, bcRes, actRes] = await Promise.all([
      dishStore.fetchHomeHot(null, null),
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
</script>

<style scoped>
.home-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }

/* ===== 顶部 Header（组件内渲染头像行与整行搜索框，首页不再额外定义） ===== */
.scroll-wrap { flex: 1; overflow-y: auto; width: 100%; padding-bottom: env(safe-area-inset-bottom); }
.section { padding: 0 var(--spacing-md); margin-bottom: var(--spacing-lg); width: 100%; box-sizing: border-box; }

/* ===== 列表底部状态 ===== */
.list-footer { display: flex; align-items: center; justify-content: center; padding: var(--spacing-md) 0; gap: var(--spacing-xs); }
.footer-spinner { width: 28rpx; height: 28rpx; border: 4rpx solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
.footer-text { font-size: var(--font-aux); color: var(--text-tertiary); }
@keyframes spin { to { transform: rotate(360deg); } }

/* ========== 骨架屏 ========== */
.home-skeleton { padding: 0 var(--spacing-md); }
.sk-moment { width: 100%; height: 180rpx; margin: var(--spacing-lg) var(--spacing-md) var(--spacing-md); border-radius: var(--radius-card); }
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
