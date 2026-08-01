<template>
  <view class="page my-publish-page">
    <Header title="我的发布" showBack />

    <view class="tabs">
      <view class="tab" :class="{ active: activeTab === 'dish' }" @tap="switchTab('dish')">菜品</view>
      <view class="tab" :class="{ active: activeTab === 'stall' }" @tap="switchTab('stall')">档口·食堂</view>
    </view>

    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <!-- 菜品发布列表 -->
      <view v-if="activeTab === 'dish'">
        <view v-if="dishes.length > 0" class="list">
          <view v-for="item in dishes" :key="item.id" class="publish-item" @tap="goEditDish(item)">
            <image v-if="item.image" class="item-img" :src="getImageUrl(item.image)" mode="aspectFill" />
            <view v-else class="item-img item-img-empty">
              <IconSvg name="dish" :size="56" color="var(--text-tertiary)" class="item-img-fallback" />
            </view>
            <view class="item-info">
              <text class="item-name">{{ item.name }}</text>
              <text class="item-meta">¥{{ item.price }}<text v-if="item.tags"> · {{ item.tags }}</text></text>
              <text v-if="item.auditStatus === 'rejected' && item.rejectReason" class="item-reason">退回原因：{{ item.rejectReason }}</text>
            </view>
            <StatusBadge :status="(item.auditStatus as any) || 'pending'" />
          </view>
        </view>
        <EmptyState v-else text="还没有发布菜品" />
      </view>

      <!-- 档口·食堂发布列表（GET /my/stalls） -->
      <view v-else>
        <view v-if="stalls.length > 0" class="list">
          <view v-for="item in stalls" :key="item.id" class="publish-item">
            <view v-if="item.images && item.images.length" class="item-img">
              <image class="item-img-el" :src="getImageUrl(item.images[0])" mode="aspectFill" />
            </view>
            <view v-else class="item-img item-img-empty">
              <IconSvg :name="item.type === 'canteen' ? 'home' : 'dish'" :size="56" color="var(--text-tertiary)" class="item-img-fallback" />
            </view>
            <view class="item-info">
              <text class="item-name">{{ item.type === 'canteen' ? '食堂：' : '档口：' }}{{ item.name }}</text>
              <text v-if="item.location" class="item-meta"><IconSvg name="location" :size="22" color="var(--text-tertiary)" /> {{ item.location }}</text>
              <text v-if="item.auditStatus === 'rejected' && item.rejectReason" class="item-reason">退回原因：{{ item.rejectReason }}</text>
            </view>
            <StatusBadge :status="(item.auditStatus as any) || 'pending'" />
          </view>
        </view>
        <EmptyState v-else text="还没有发布档口·食堂" />
      </view>

      <view class="publish-actions" v-if="activeTab === 'dish'">
        <AppButton text="发布新菜品" @click="goPublishDish" />
        <AppButton text="提交档口·食堂" type="outline" margin="16rpx 0 0" @click="goSubmitStall" />
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppButton from '@/components/AppButton.vue'
import { getImageUrl } from '@/utils/image'
import IconSvg from '@/components/IconSvg.vue'
import { getMyDishes } from '@/api/publish'
import { getMyStalls } from '@/api/stall'
import type { MyPublishDish } from '@/types/dish'
import type { MyPublishStall } from '@/types/canteen'

const activeTab = ref<'dish' | 'stall'>('dish')
const dishes = ref<MyPublishDish[]>([])
const stalls = ref<MyPublishStall[]>([])
const loading = ref(false)
const refresherTriggered = ref(false)

async function loadDishes() {
  loading.value = true
  try {
    dishes.value = await getMyDishes()
  } catch {
    dishes.value = []
  } finally {
    loading.value = false
  }
}

async function loadStalls() {
  loading.value = true
  try {
    stalls.value = await getMyStalls()
  } catch {
    stalls.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(tab: 'dish' | 'stall') {
  activeTab.value = tab
  if (tab === 'dish') loadDishes()
  else loadStalls()
}

function goEditDish(item: MyPublishDish) {
  uni.navigateTo({ url: `/pages/profile/publish-dish?id=${item.id}` })
}

function goPublishDish() {
  uni.navigateTo({ url: '/pages/profile/publish-dish' })
}

function goSubmitStall() {
  uni.navigateTo({ url: '/pages/profile/submit-stall' })
}

onShow(() => { if (activeTab.value === 'dish') loadDishes() })

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  const task = activeTab.value === 'dish' ? loadDishes() : loadStalls()
  task.finally(() => { refresherTriggered.value = false })
}
</script>

<style scoped>
.my-publish-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.tabs { display: flex; padding: var(--spacing-md) var(--spacing-lg) 0; gap: var(--spacing-lg); }
.tab { font-size: var(--font-body); color: var(--text-secondary); font-weight: 500; padding: var(--spacing-xs) 0; position: relative; transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.tab.active { color: var(--color-primary); font-weight: 700; }
.tab:active { transform: scale(var(--press-scale)); }
.tab.active::after { content: ''; position: absolute; left: 50%; bottom: 0; transform: translateX(-50%); width: 40rpx; height: 6rpx; border-radius: 6rpx; background: var(--color-primary); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md); }
.list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.publish-item { display: flex; align-items: center; gap: var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-md); box-shadow: var(--shadow-card); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.publish-item:active { transform: scale(var(--press-scale)); }
.item-img { width: 120rpx; height: 120rpx; border-radius: var(--radius-icon); flex-shrink: 0; background: var(--bg-page); overflow: hidden; }
.item-img-el { width: 100%; height: 100%; }
.item-img-empty { display: flex; align-items: center; justify-content: center; }
.item-img-fallback { font-size: 56rpx; line-height: 1; }
.item-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.item-name { font-size: var(--font-caption); font-weight: 600; color: var(--text-primary); }
.item-meta { font-size: var(--font-aux); color: var(--text-secondary); }
.item-reason { font-size: var(--font-tiny); color: var(--color-error); line-height: 1.4; }
.publish-actions { margin-top: var(--spacing-lg); }
</style>
