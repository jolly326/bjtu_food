<template>
  <view class="page dynamic-page">
    <Header title="动态" :show-back="false" />
    <scroll-view
      class="scroll-wrap"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
      @scrolltolower="onScrollToLower"
    >
      <!-- 加载/失败/空态统一由 StateView 一次承载（ui-feed-loading：空态/错误态复用 EmptyState，不在列表区重复放置加载态） -->
      <StateView
        v-if="moments.length === 0"
        :loading="loading"
        :failed="loadFailed"
        :empty="true"
        error-text="动态加载失败，请重试"
        empty-text="还没有动态，快去发布第一条吧"
        empty-icon="comment"
        :action-text="'发布第一条动态'"
        action-icon="plus"
        @retry="loadData(true)"
        @action="goPublish"
      />

      <view v-else class="moment-list">
        <view v-for="m in moments" :key="m.id">
          <MomentCard
            :moment="m"
            @select="goDetail"
            @go-related="goRelated"
            @more="openMore"
          />
        </view>
      </view>
    </scroll-view>

    <!-- 常驻发布按钮（FAB）：列表/加载态均可直接发布动态，避免仅空态可发布 -->
    <view class="fab fab-publish" role="button" aria-label="发布动态" @tap="goPublish">
      <IconSvg name="plus" :size="44" color="var(--color-on-primary)" />
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

    <!-- 底部常驻菜单栏：首页/动态/我的 三主区切换（仅主根页显示） -->
    <TabBar />
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onShareAppMessage, onShow } from '@dcloudio/uni-app'
import { showTab } from '@/stores/route'
import TabBar from '@/components/TabBar.vue'
import * as momentApi from '@/api/moment'
import type { Moment } from '@/types/moment'
import { useReport } from '@/composables/useReport'
import { buildSharePayload, clearShareState } from '@/utils/share-state'
import MomentCard from './MomentCard.vue'
import MomentActionSheet from '@/components/MomentActionSheet.vue'
import Header from '@/components/AppHeader.vue'
import StateView from '@/components/StateView.vue'
import ReportModal from '@/components/ReportModal.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import IconSvg from '@/components/IconSvg.vue'

const moments = ref<Moment[]>([])
/** 菜品详情跳转独立页（pages/detail/dish） */
function openDishDetail(id: number) {
  if (!id) return
  uni.navigateTo({ url: `/pages/dish/index?id=${id}` })
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
// 分页数据重复或丢失（动态列表竞态守卫，对齐 dish store 的 fetchSeq 方案）
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
    // 动态单「最新」流（问题二：去双 Tab；getMoments 默认 latest）
    const res = await momentApi.getMoments({ page, pageSize })
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
  uni.navigateTo({ url: `/pages/moment/index?id=${m.id}` })
}

function goRelated(m: Moment) {
  if (m.relatedType === 'dish' && m.relatedId) {
    openDishDetail(m.relatedId)
  }
}

function goPublish() {
  uni.navigateTo({ url: '/pages/publish-content/index' })
}

onMounted(() => {
  loadData(true)
})
// 从动态详情返回动态页时：清掉详情页的分享残留，避免右上角分享菜单沿用上一条动态
onShow(() => {
  // 锚定底部菜单栏：动态页始终显示并高亮
  showTab('dynamic')
  clearShareState()
})
onShareAppMessage(() => buildSharePayload())
</script>

<style scoped>
.dynamic-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); overflow: hidden; }

.scroll-wrap { flex: 1; min-height: 0; overflow-y: auto; padding-top: 0; padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom)); }
/* 动态列表：卡片之间固定间距（--spacing-md），配合白卡投影形成呼吸节奏 */
/* 卡间距 14px(=28rpx)，moment-list-detail-polish D1 */
.moment-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: 28rpx; }


/* 常驻发布按钮（FAB）：右下角悬浮，Apple 风格圆底 + 主色填充。
   bottom 须叠加 --tabbar-height，否则被常驻 TabBar 盖住下半截（红线 §4.9 布局）。
   tab-pages-visual-unify：缩小尺寸（112→96rpx）、投影改柔和（modal→float）、
   整体上移（+spacing-xl），避免遮挡列表最后一条动态的互动区（配合列表底部加大留白）。 */
.fab-publish {
  position: fixed;
  right: var(--spacing-lg);
  /* moment-list-detail-polish：直径 96rpx(=48px) 保持，上移 10px(=20rpx) */
  bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom) + var(--spacing-xl) + 20rpx);
  width: 96rpx;
  height: 96rpx;
  border-radius: var(--radius-circle);
  background: var(--color-primary);
  box-shadow: var(--shadow-float);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 60;
  transition: opacity var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.fab-publish:active { opacity: 0.85; }
</style>
