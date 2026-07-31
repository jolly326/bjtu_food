<template>
  <view class="page community-page">
    <Header title="动态" />

    <!-- 推荐 / 最新 两 Tab -->
    <view class="tab-bar">
      <view
        class="tab-item"
        :class="{ active: activeTab === 'latest' }"
        @tap="switchTab('latest')"
      >
        <text class="tab-text">最新</text>
      </view>
      <view
        class="tab-item"
        :class="{ active: activeTab === 'recommend' }"
        @tap="switchTab('recommend')"
      >
        <text class="tab-text">推荐</text>
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
      <view v-if="loading && moments.length === 0" class="skeleton-list">
        <view v-for="s in 3" :key="s" class="sk-card skeleton" />
      </view>

      <EmptyState
        v-else-if="moments.length === 0"
        text="还没有动态，快去发布第一条吧"
        icon="💬"
        :retry="loadFailed"
        @retry="loadData(true)"
      />

      <view v-else class="moment-list">
        <MomentCard
          v-for="m in moments"
          :key="m.id"
          :moment="m"
          @tap="goDetail"
          @go-related="goRelated"
        />
        <!-- 触底状态 -->
        <view v-if="loadingMore" class="list-footer loading">
          <view class="footer-spinner" />
          <text class="footer-text">加载中…</text>
        </view>
        <view v-else-if="finished" class="list-footer finished">
          <text class="footer-text">— 已经到底啦 —</text>
        </view>
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 悬浮发布 ➕ -->
    <view class="fab" :class="{ pressed: fabPressed }" @touchstart="fabPressed = true" @touchend="fabPressed = false" @touchcancel="fabPressed = false" @mousedown="fabPressed = true" @mouseup="fabPressed = false" @mouseleave="fabPressed = false" @tap="goPublish">
      <text class="fab-icon">{{ EMOJI.plus }}</text>
    </view>

    <CustomTabBar current="/pages/community/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import Header from '@/components/header.vue'
import MomentCard from '@/components/MomentCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { EMOJI } from '@/utils/emoji'
import * as momentApi from '@/api/moment'
import type { Moment } from '@/types/moment'

const activeTab = ref<'latest' | 'recommend'>('latest')
const moments = ref<Moment[]>([])
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)
const loadFailed = ref(false)
const refresherTriggered = ref(false)
const fabPressed = ref(false)

let page = 1
const pageSize = 10

async function loadData(reset = false) {
  if (reset) {
    page = 1
    finished.value = false
    moments.value = []
  }
  loading.value = true
  loadFailed.value = false
  try {
    const res = await momentApi.getMoments({ tab: activeTab.value, page, pageSize })
    moments.value = page === 1 ? res.list : [...moments.value, ...res.list]
    if (moments.value.length >= res.total) finished.value = true
    page += 1
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function onScrollToLower() {
  if (loading.value || loadingMore.value || finished.value) return
  loadingMore.value = true
  try {
    await loadData(false)
  } finally {
    loadingMore.value = false
  }
}

function switchTab(tab: 'latest' | 'recommend') {
  if (activeTab.value === tab) return
  activeTab.value = tab
  loadData(true)
}

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData(true).finally(() => { refresherTriggered.value = false })
}

function goDetail(m: Moment) {
  uni.navigateTo({ url: `/pages/pages-detail/moment?id=${m.id}` })
}

function goRelated(m: Moment) {
  if (m.relatedType === 'dish' && m.relatedId) {
    uni.navigateTo({ url: `/pages/pages-detail/dish?id=${m.relatedId}` })
  } else if (m.relatedType === 'stall' && m.relatedId) {
    // 档口详情通过 name 进入；这里用 id 兜底走 stall（若后端支持），否则提示
    uni.navigateTo({ url: `/pages/pages-detail/stall?id=${m.relatedId}` })
  }
}

function goPublish() {
  uni.navigateTo({ url: '/pages/publish-moment/index' })
}

onMounted(() => { loadData(true) })
</script>

<style scoped>
.community-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.tab-bar { display: flex; align-items: center; gap: var(--spacing-md); padding: var(--spacing-sm) var(--spacing-lg); background: var(--bg-card); border-bottom: 2rpx solid var(--border-color); }
.tab-item { padding: var(--spacing-xs) var(--spacing-md); border-radius: var(--radius-tag); transition: background 0.15s; -webkit-tap-highlight-color: transparent; }
.tab-text { font-size: var(--font-body); color: var(--text-secondary); font-weight: 600; }
.tab-item.active { background: var(--color-primary-soft); }
.tab-item.active .tab-text { color: var(--color-primary); }
.moment-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.skeleton-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.sk-card { width: 100%; height: 280rpx; }
.list-footer { display: flex; align-items: center; justify-content: center; padding: var(--spacing-md) 0; gap: var(--spacing-xs); }
.footer-spinner { width: 28rpx; height: 28rpx; border: 4rpx solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
.footer-text { font-size: var(--font-aux); color: var(--text-tertiary); }
@keyframes spin { to { transform: rotate(360deg); } }

/* 悬浮发布 */
.fab {
  position: fixed;
  right: var(--spacing-lg);
  bottom: calc(var(--tabbar-height) + var(--spacing-lg) + env(safe-area-inset-bottom));
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-bar-primary);
  z-index: 80;
  transition: transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.fab.pressed { transform: scale(0.94); }
.fab-icon { font-size: 52rpx; line-height: 1; color: var(--text-white); }

@media (prefers-reduced-motion: reduce) {
  .footer-spinner { animation-duration: 1.4s; }
}
</style>
