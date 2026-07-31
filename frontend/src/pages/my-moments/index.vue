<template>
  <view class="page my-moments-page">
    <Header title="我的动态" showBack />

    <!-- 分段：全部 / 审核中 / 已退回 -->
    <view class="segment">
      <view
        v-for="seg in segments"
        :key="seg.key"
        class="seg-item"
        :class="{ active: activeSeg === seg.key }"
        @tap="switchSeg(seg.key)"
      >
        <text class="seg-text">{{ seg.label }}</text>
        <text v-if="seg.count > 0" class="seg-count">{{ seg.count }}</text>
      </view>
    </view>

    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <view v-if="loading && moments.length === 0" class="skeleton-list">
        <view v-for="s in 3" :key="s" class="sk-card skeleton" />
      </view>

      <EmptyState
        v-else-if="moments.length === 0"
        :text="emptyText"
        icon="comment"
        @retry="loadData"
      />

      <view v-else class="moment-list">
        <MomentCard
          v-for="m in moments"
          :key="m.id"
          :moment="m"
          :show-audit="true"
          @tap="goDetail"
          @go-related="goRelated"
        />
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Header from '@/components/header.vue'
import MomentCard from '@/components/MomentCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import type { Moment } from '@/types/moment'

type SegKey = 'all' | 'pending' | 'rejected'

const userStore = useUserStore()
const segments: { key: SegKey; label: string; count: number }[] = [
  { key: 'all', label: '全部', count: 0 },
  { key: 'pending', label: '审核中', count: 0 },
  { key: 'rejected', label: '已退回', count: 0 },
]
const activeSeg = ref<SegKey>('all')
const moments = ref<Moment[]>([])
const loading = ref(false)
const refresherTriggered = ref(false)

const emptyText = computed(() => {
  if (activeSeg.value === 'pending') return '暂无审核中的动态'
  if (activeSeg.value === 'rejected') return '暂无被退回的动态'
  return '你还没有发布动态'
})

async function loadData() {
  if (!userStore.requireAuth()) return
  loading.value = true
  try {
    const auditStatus = activeSeg.value === 'all' ? undefined : activeSeg.value
    moments.value = await momentApi.getMyMoments(auditStatus)
    // 统计（全量拉取一次用于徽标）
    if (activeSeg.value === 'all') {
      const [pending, rejected] = await Promise.all([
        momentApi.getMyMoments('pending'),
        momentApi.getMyMoments('rejected'),
      ])
      segments[1].count = pending.length
      segments[2].count = rejected.length
      segments[0].count = moments.value.length
    }
  } catch (e: any) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
    moments.value = []
  } finally {
    loading.value = false
  }
}

function switchSeg(seg: SegKey) {
  if (activeSeg.value === seg) return
  activeSeg.value = seg
  loadData()
}

function goDetail(m: Moment) {
  // 已退回可直达编辑；其他态进详情
  if (m.auditStatus === 'rejected') {
    uni.navigateTo({ url: `/pages/publish-moment/index?id=${m.id}` })
  } else {
    uni.navigateTo({ url: `/pages/pages-detail/moment?id=${m.id}` })
  }
}

function goRelated(m: Moment) {
  if (m.relatedType === 'dish' && m.relatedId) {
    uni.navigateTo({ url: `/pages/pages-detail/dish?id=${m.relatedId}` })
  } else if (m.relatedType === 'stall' && m.relatedId) {
    uni.navigateTo({ url: `/pages/pages-detail/stall?id=${m.relatedId}` })
  }
}

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => { refresherTriggered.value = false })
}

onMounted(() => { loadData() })
</script>

<style scoped>
.my-moments-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.segment { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-lg); background: var(--bg-card); border-bottom: 2rpx solid var(--border-color); }
.seg-item { display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); border-radius: var(--radius-tag); background: var(--bg-soft); transition: background 0.15s; -webkit-tap-highlight-color: transparent; }
.seg-text { font-size: var(--font-body); color: var(--text-secondary); font-weight: 600; }
.seg-count { font-size: 20rpx; color: var(--text-tertiary); background: var(--bg-card); border-radius: 999rpx; padding: 0 var(--spacing-xs); min-width: 28rpx; text-align: center; }
.seg-item.active { background: var(--color-primary-soft); }
.seg-item.active .seg-text { color: var(--color-primary); }
.moment-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.skeleton-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.sk-card { width: 100%; height: 280rpx; }
</style>
