<template>
  <view class="page my-submissions-page">
    <Header title="我的提交" showBack />

    <view class="tabs">
      <view class="tab" :class="{ active: activeTab === 'apply' }" @tap="switchTab('apply')">实体</view>
      <view class="tab" :class="{ active: activeTab === 'moment' }" @tap="switchTab('moment')">动态</view>
    </view>

    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <!-- 实体申请 -->
      <view v-if="activeTab === 'apply'">
        <view v-if="applyList.length > 0" class="list">
          <view v-for="item in applyList" :key="'a'+item.id" class="sub-item">
            <view class="item-main">
              <text class="item-title">{{ item.title }}</text>
              <text class="item-meta">{{ entityTypeLabel(item.entityType) }} · {{ actionLabel(item.action) }}</text>
            </view>
            <view class="item-right">
              <StatusBadge v-if="item.status === 'approved' && item.off" status="approved" />
              <StatusBadge v-else :status="(item.status as any)" />
              <text v-if="item.status === 'approved' && item.off" class="off-tag"><IconSvg name="lock" :size="22" color="var(--text-tertiary)" /> 已下架</text>
            </view>
          </view>
        </view>
        <EmptyState v-else text="还没有实体提交记录" />
      </view>

      <!-- 动态 -->
      <view v-else>
        <view v-if="momentList.length > 0" class="list">
          <view v-for="item in momentList" :key="'m'+item.id" class="sub-item" @tap="goMoment(item.id)">
            <view class="item-main">
              <text class="item-title">{{ item.title }}</text>
              <text class="item-meta">{{ item.off ? '已下架' : '动态' }}</text>
            </view>
            <view class="item-right">
              <StatusBadge :status="(item.status as any)" />
              <text class="item-arrow"><IconSvg name="arrow" :size="28" color="var(--text-tertiary)" /></text>
            </view>
          </view>
        </view>
        <EmptyState v-else text="还没有动态提交记录" />
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconSvg from '@/components/IconSvg.vue'
import { useUserStore } from '@/stores/user'
import { getMySubmissions } from '@/api/apply'
import type { SubmissionVO, ApplyEntityType, ApplyType } from '@/api/apply'

const userStore = useUserStore()
const activeTab = ref<'apply' | 'moment'>('apply')
const all = ref<SubmissionVO[]>([])
const loading = ref(false)
const refresherTriggered = ref(false)

const applyList = computed(() => all.value.filter(s => s.type === 'apply'))
const momentList = computed(() => all.value.filter(s => s.type === 'moment'))

function entityTypeLabel(t?: ApplyEntityType): string {
  if (t === 'DISH') return '菜品'
  if (t === 'STALL') return '档口'
  if (t === 'CANTEEN') return '食堂'
  return '实体'
}
function actionLabel(a?: ApplyType): string {
  if (a === 'NEW') return '新增'
  if (a === 'CLOSE') return '下架/关闭'
  if (a === 'CHANGE') return '变更'
  return ''
}

async function loadAll() {
  if (!userStore.requireAuth()) return
  loading.value = true
  try {
    all.value = await getMySubmissions()
    // 默认选中有数据的标签
    if (applyList.value.length === 0 && momentList.value.length > 0) activeTab.value = 'moment'
  } catch {
    all.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(tab: 'apply' | 'moment') {
  activeTab.value = tab
}

function goMoment(id: number) {
  uni.navigateTo({ url: `/pages/pages-detail/moment?id=${id}` })
}

onShow(() => { loadAll() })

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadAll().finally(() => { refresherTriggered.value = false })
}
</script>

<style scoped>
.my-submissions-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.tabs { display: flex; padding: var(--spacing-md) var(--spacing-md) 0; gap: var(--spacing-md); }
.tab { font-size: var(--font-body); color: var(--text-secondary); font-weight: 500; padding: var(--spacing-xs) 0; position: relative; transition: transform 160ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.tab.active { color: var(--color-primary); font-weight: 700; }
.tab:active { transform: scale(var(--press-scale)); }
.tab.active::after { content: ''; position: absolute; left: 50%; bottom: 0; transform: translateX(-50%); width: 40rpx; height: 6rpx; border-radius: 6rpx; background: var(--color-primary); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0; }
.list { display: flex; flex-direction: column; gap: var(--spacing-sm); padding: 0 var(--spacing-md); }
.sub-item { display: flex; align-items: center; gap: var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-md); box-shadow: var(--shadow-card); transition: transform 160ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.sub-item:active { transform: scale(var(--press-scale)); }
.item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.item-title { font-size: var(--font-caption); font-weight: 600; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-meta { font-size: var(--font-aux); color: var(--text-tertiary); }
.item-right { display: flex; align-items: center; gap: var(--spacing-xs); flex-shrink: 0; }
.off-tag { font-size: var(--font-tiny); color: var(--text-tertiary); }
.item-arrow { font-size: var(--font-body); color: var(--text-tertiary); }

@media (prefers-reduced-motion: reduce) {
  .tab, .sub-item { transition: none; }
  .tab:active, .sub-item:active { transform: none; }
}
</style>
