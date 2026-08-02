<template>
  <view class="page my-moments-page">
    <Header title="我的动态" showBack />

    <!-- 分段：全部 / 审核中 / 已退回 -->
    <view class="segment-wrap">
      <SegmentTabs
        :tabs="segmentTabs"
        :model-value="activeSeg"
        @update:model-value="onSegChange"
      />
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
import SegmentTabs from '@/components/SegmentTabs.vue'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import type { Moment } from '@/types/moment'

type SegKey = 'all' | 'pending' | 'rejected'

const userStore = useUserStore()
const segmentTabs = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '审核中' },
  { key: 'rejected', label: '已退回' },
]
const pendingCount = ref(0)
const rejectedCount = ref(0)
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
      pendingCount.value = pending.length
      rejectedCount.value = rejected.length
    }
  } catch (e: any) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
    moments.value = []
  } finally {
    loading.value = false
  }
}

function onSegChange(key: string) {
  if (activeSeg.value === key) return
  activeSeg.value = key as SegKey
  loadData()
}

function goDetail(m: Moment) {
  // 已退回可直达编辑；其他态进详情
  if (m.auditStatus === 'rejected') {
    uni.navigateTo({ url: `/pages/pages-user/publish-moment?id=${m.id}` })
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
.scroll-wrap { flex: 1; overflow-y: auto; padding: 0; }
.segment-wrap { padding: var(--spacing-sm) var(--spacing-md); background: var(--bg-page); }
.moment-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.skeleton-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.sk-card { width: 100%; height: 280rpx; }
</style>
