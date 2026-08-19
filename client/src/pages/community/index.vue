<template>
  <view class="page community-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="最新动态" @back="backToHome" />
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
        :text="loadFailed ? '动态加载失败，请重试' : '还没有动态，快去发布第一条吧'"
        icon="comment"
        :retry="loadFailed"
        :action-text="!loadFailed ? '发布第一条动态' : ''"
        action-icon="plus"
        @retry="loadData(true)"
        @action="goPublish"
      />

      <view v-else class="moment-list">
        <!-- enter-up + --enter-i：列表 stagger 入场（全局 enterFade var(--duration-base) + 40ms 间隔） -->
        <MomentCard
          v-for="(m, i) in moments"
          :key="m.id"
          class="enter-up"
          :style="{ '--enter-i': Math.min(i, 8) }"
          :moment="m"
          @select="goDetail"
          @go-related="goRelated"
          @more="openMore"
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

    <!-- 常驻发布按钮（FAB）：列表/加载态均可直接发布动态，避免仅空态可发布 -->
    <view class="fab fab-publish" role="button" aria-label="发布动态" @tap="goPublish">
      <IconSvg name="plus" :size="48" color="var(--color-on-primary)" />
    </view>

    <!-- 举报弹窗（共享组件） -->
    <ReportModal
      :open="reportOpen"
      title="举报动态"
      placeholder="请描述举报原因…"
      confirm-text="提交举报"
      :submitting="reportSubmitting"
      @update:open="reportOpen = $event"
      @submit="submitReport"
    />

    <!-- 三点菜单：分享 / 举报（页面根级挂载，scroll-view 外 fixed 层级才正确） -->
    <MomentActionSheet
      :open="moreOpen"
      :moment="moreMoment"
      @update:open="moreOpen = $event"
      @report="openReportForMoment"
    />

    <!-- 认证弹层（未登录点赞/评论等 requireAuth 入口统一在此弹出） -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import * as momentApi from '@/api/moment'
import type { Moment } from '@/types/moment'
import { useReport } from '@/composables/useReport'
import { buildSharePayload, clearShareState } from '@/utils/shareState'
import { backToHome } from '@/utils/nav'
import MomentCard from '@/components/MomentCard.vue'
import MomentActionSheet from '@/components/MomentActionSheet.vue'
import Header from '@/components/header.vue'
import EmptyState from '@/components/EmptyState.vue'
import ReportModal from '@/components/ReportModal.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import IconSvg from '@/components/IconSvg.vue'

const theme = useThemeStore()
const moments = ref<Moment[]>([])
/** 菜品详情跳转独立页（pages-detail/dish） */
function openDishDetail(id: number) {
  if (!id) return
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${id}` })
}

/* ===== 三点菜单（MomentCard @more → 页面级 ActionSheet） ===== */
const moreOpen = ref(false)
const moreMoment = ref<Moment | null>(null)

function openMore(m: Moment) {
  moreMoment.value = m
  moreOpen.value = true
}

/* ===== 动态举报（ActionSheet @report → ReportModal，逻辑收敛到 useReport hook） ===== */
const { reportOpen, reportSubmitting, openReport, submitReport } =
  useReport({ type: 'moment', title: '举报动态', placeholder: '请描述举报原因…' })

function openReportForMoment(m: Moment) {
  openReport(m.id)
}
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)
const loadFailed = ref(false)
const refresherTriggered = ref(false)

let page = 1
const pageSize = 10
// 请求序号：下拉刷新/切标签自增，使在途的旧请求结果失效，避免快速触底+刷新并发导致页码跳号、
// 分页数据重复或丢失（社区列表竞态守卫，对齐 dish store 的 fetchSeq 方案）
let fetchSeq = 0

async function loadData(reset = false) {
  if (reset) {
    page = 1
    finished.value = false
    moments.value = []
  }
  const seq = ++fetchSeq
  loading.value = true
  loadFailed.value = false
  try {
    // 单流：始终按「最新」倒序拉取（task-14 §1.3 已决议去除推荐 Tab）
    const res = await momentApi.getMoments({ tab: 'latest', page, pageSize })
    // 过期响应（期间又触发刷新/加载更多）丢弃，避免旧结果覆盖新列表
    if (seq !== fetchSeq) return
    moments.value = page === 1 ? res.list : [...moments.value, ...res.list]
    // M02 修复：基于本页实际返回量判据（本地 sort 不干扰），不足一页即到底
    if (res.list.length < pageSize) finished.value = true
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
    openDishDetail(m.relatedId)
  }
}

function goPublish() {
  uni.navigateTo({ url: '/pages/pages-user/publish-content/index' })
}

onMounted(() => { loadData(true) })
// 从动态详情返回社区时：清掉详情页的分享残留，避免右上角分享菜单沿用上一条动态
onShow(() => clearShareState())
onShareAppMessage(() => buildSharePayload())
</script>

<style scoped>
.community-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-top: 0; padding-bottom: env(safe-area-inset-bottom); }
.moment-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.skeleton-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.sk-card { width: 100%; height: 280rpx; }
.list-footer { display: flex; align-items: center; justify-content: center; padding: var(--spacing-md) 0; gap: var(--spacing-xs); }
.footer-spinner { width: 28rpx; height: 28rpx; border: 4rpx solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
.footer-text { font-size: var(--font-aux); color: var(--text-tertiary); }
@keyframes spin { to { transform: rotate(360deg); } }

@media (prefers-reduced-motion: reduce) {
  .footer-spinner { animation: none; }
}

/* 常驻发布按钮（FAB）：右下角悬浮，Apple 风格圆底 + 主色填充 */
.fab-publish {
  position: fixed;
  right: var(--spacing-lg);
  bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom));
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 12rpx 28rpx rgba(0, 0, 0, 0.22);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 60;
  transition: transform var(--duration-fast) ease, opacity var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.fab-publish:active { transform: scale(0.92); opacity: 0.85; }
</style>
