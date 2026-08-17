<template>
  <view class="page my-moments-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="我的动态" @back="backToHome" />

    <!-- 直接展示一列我的动态（无分类 tab；被退回的会通过消息中心提醒） -->
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <view v-if="loading && moments.length === 0" class="skeleton-list">
        <view v-for="s in 3" :key="s" class="sk-card skeleton" />
      </view>

      <!-- 加载失败：与空数据语义区分，提供重试 -->
      <EmptyState v-else-if="loadFailed" text="加载失败，请重试" icon="report" :retry="true" @retry="loadData" />

      <EmptyState
        v-else-if="moments.length === 0"
        text="你还没有发布动态"
        icon="comment"
        @retry="loadData"
      />

      <view v-else class="moment-list">
        <!-- enter-up + --enter-i：列表 stagger 入场（全局 enterFade 0.2s + 40ms 间隔） -->
        <MomentCard
          v-for="(m, i) in moments"
          :key="m.id"
          class="enter-up"
          :style="{ '--enter-i': Math.min(i, 8) }"
          :moment="m"
          :show-audit="true"
          @select="goDetail"
          @go-related="goRelated"
        />
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 认证弹层：游客直访时引导登录，认证成功后自动加载 -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import { useThemeStore } from '@/stores/theme'
import * as momentApi from '@/api/moment'
import type { Moment } from '@/types/moment'
import { buildSharePayload, clearShareState } from '@/utils/shareState'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import MomentCard from '@/components/MomentCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import AuthSheet from '@/components/AuthSheet.vue'

const userStore = useUserStore()
const theme = useThemeStore()
const moments = ref<Moment[]>([])
function openDishDetail(id: number) {
  if (!id) return
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${id}` })
}
const loading = ref(false)
const loadFailed = ref(false)
const refresherTriggered = ref(false)

async function loadData() {
  if (!userStore.requireAuth()) return
  loading.value = true
  loadFailed.value = false
  try {
    moments.value = await momentApi.getMyMoments()
  } catch (e: any) {
    // 网络/业务错误 http 层已统一 toast，页面仅置失败态（空态展示重试），避免重复提示
    loadFailed.value = true
    moments.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(m: Moment) {
  // 已退回可直达编辑；其他态进详情
  if (m.auditStatus === 'rejected') {
    uni.navigateTo({ url: `/pages/pages-user/publish-moment/index?id=${m.id}` })
  } else {
    uni.navigateTo({ url: `/pages/pages-detail/moment?id=${m.id}` })
  }
}

function goRelated(m: Moment) {
  if (m.relatedType === 'dish' && m.relatedId) {
    openDishDetail(m.relatedId)
  }
  // 档口详情页已下线（2026-08-09）：相关档口不再展示跳转入口
}

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => { refresherTriggered.value = false })
}

// 游客直访时弹认证；认证成功后自动加载
watch(
  () => userStore.isLoggedIn(),
  (v) => { if (v) loadData() },
  { immediate: true },
)
onShow(() => clearShareState())
onShareAppMessage(() => buildSharePayload())
</script>

<style scoped>
.my-moments-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: 0; }
.moment-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.skeleton-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.sk-card { width: 100%; height: 280rpx; }
</style>
