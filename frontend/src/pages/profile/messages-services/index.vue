<template>
  <view class="page messages-services-page">
    <Header title="消息与服务" showBack />

    <SegmentTabs
      class="seg-wrap"
      :tabs="tabs"
      :model-value="activeGroup"
      @update:model-value="onSwitch"
    />

    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <!-- 我的发布 -->
      <view v-if="activeGroup === 'publish'">
        <view class="sub-tabs">
          <view class="sub-tab" :class="{ active: publishTab === 'dish' }" @tap="switchPublishTab('dish')">菜品</view>
          <view class="sub-tab" :class="{ active: publishTab === 'stall' }" @tap="switchPublishTab('stall')">档口·食堂</view>
        </view>

        <!-- 菜品 -->
        <view v-if="publishTab === 'dish'">
          <view v-if="dishes.length > 0" class="list">
            <view v-for="item in dishes" :key="'d'+item.id" class="publish-item" @tap="goEditDish(item)">
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

        <!-- 档口·食堂 -->
        <view v-else>
          <view v-if="stalls.length > 0" class="list">
            <view v-for="item in stalls" :key="'s'+item.id" class="publish-item">
              <view v-if="item.images && item.images.length" class="item-img">
                <image class="item-img-el" :src="getImageUrl(item.images[0])" mode="aspectFill" />
              </view>
              <view v-else class="item-img item-img-empty">
                <IconSvg :name="item.type === 'canteen' ? 'home' : 'dish'" :size="56" color="var(--text-tertiary)" class="item-img-fallback" />
              </view>
              <view class="item-info">
                <text class="item-name">{{ item.type === 'canteen' ? '食堂：' : '档口：' }}{{ item.name }}</text>
                <view v-if="item.location" class="item-meta item-meta-row">
                  <IconSvg name="location" :size="22" color="var(--text-tertiary)" class="item-meta-icon" />
                  <text class="item-meta-text">{{ item.location }}</text>
                </view>
                <text v-if="item.auditStatus === 'rejected' && item.rejectReason" class="item-reason">退回原因：{{ item.rejectReason }}</text>
              </view>
              <StatusBadge :status="(item.auditStatus as any) || 'pending'" />
            </view>
          </view>
          <EmptyState v-else text="还没有发布档口·食堂" />
        </view>

        <view class="publish-actions" v-if="publishTab === 'dish'">
          <AppButton text="发布新菜品" @click="goPublishDish" />
          <AppButton text="提交档口·食堂" type="outline" margin="16rpx 0 0" @click="goSubmitStall" />
        </view>
      </view>

      <!-- 我的贡献 -->
      <view v-else>
        <view class="sub-tabs">
          <view class="sub-tab" :class="{ active: contributionTab === 'apply' }" @tap="switchContributionTab('apply')">实体</view>
          <view class="sub-tab" :class="{ active: contributionTab === 'moment' }" @tap="switchContributionTab('moment')">动态</view>
        </view>

        <!-- 实体申请 -->
        <view v-if="contributionTab === 'apply'">
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
import AppButton from '@/components/AppButton.vue'
import SegmentTabs from '@/components/SegmentTabs.vue'
import IconSvg from '@/components/IconSvg.vue'
import { getImageUrl } from '@/utils/image'
import { getMyDishes } from '@/api/publish'
import { getMyStalls } from '@/api/stall'
import { getMySubmissions } from '@/api/apply'
import type { MyPublishDish } from '@/types/dish'
import type { MyPublishStall } from '@/types/canteen'
import type { SubmissionVO, ApplyEntityType, ApplyType } from '@/api/apply'

type GroupKey = 'publish' | 'contribution'
const tabs: { key: GroupKey; label: string }[] = [
  { key: 'publish', label: '我的发布' },
  { key: 'contribution', label: '我的贡献' },
]

const activeGroup = ref<GroupKey>('publish')
const publishTab = ref<'dish' | 'stall'>('dish')
const contributionTab = ref<'apply' | 'moment'>('apply')

const dishes = ref<MyPublishDish[]>([])
const stalls = ref<MyPublishStall[]>([])
const all = ref<SubmissionVO[]>([])
const refresherTriggered = ref(false)

const applyList = computed(() => all.value.filter(s => s.type === 'apply'))
const momentList = computed(() => all.value.filter(s => s.type === 'moment'))

async function loadDishes() {
  try { dishes.value = await getMyDishes() } catch { dishes.value = [] }
}
async function loadStalls() {
  try { stalls.value = await getMyStalls() } catch { stalls.value = [] }
}
async function loadAll() {
  try { all.value = await getMySubmissions() } catch { all.value = [] }
}

async function loadGroup(group: GroupKey) {
  if (group === 'publish') {
    await (publishTab.value === 'dish' ? loadDishes() : loadStalls())
  } else {
    await loadAll()
  }
}

function onSwitch(key: string) {
  const group = key as GroupKey
  activeGroup.value = group
  loadGroup(group)
}

function switchPublishTab(tab: 'dish' | 'stall') {
  publishTab.value = tab
  if (activeGroup.value === 'publish') loadGroup('publish')
}
function switchContributionTab(tab: 'apply' | 'moment') {
  contributionTab.value = tab
}

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

function goEditDish(item: MyPublishDish) {
  uni.navigateTo({ url: `/pages/profile/publish-dish?id=${item.id}` })
}
function goPublishDish() {
  uni.navigateTo({ url: '/pages/profile/publish-dish' })
}
function goSubmitStall() {
  uni.navigateTo({ url: '/pages/profile/submit-stall' })
}
function goMoment(id: number) {
  uni.navigateTo({ url: `/pages/pages-detail/moment?id=${id}` })
}

onShow(() => { loadGroup(activeGroup.value) })

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadGroup(activeGroup.value).finally(() => { refresherTriggered.value = false })
}
</script>

<style scoped>
.messages-services-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.seg-wrap { margin: var(--spacing-md); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0; }
.sub-tabs { display: flex; padding: var(--spacing-sm) var(--spacing-md) 0; gap: var(--spacing-md); }
.sub-tab { font-size: var(--font-body); color: var(--text-secondary); font-weight: 500; padding: var(--spacing-xs) 0; position: relative; transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.sub-tab.active { color: var(--color-primary); font-weight: 700; }
.sub-tab:active { transform: scale(var(--press-scale)); }
.sub-tab.active::after { content: ''; position: absolute; left: 50%; bottom: 0; transform: translateX(-50%); width: 40rpx; height: 6rpx; border-radius: 6rpx; background: var(--color-primary); }
.list { display: flex; flex-direction: column; gap: var(--spacing-sm); padding: var(--spacing-md) var(--spacing-md) 0; }
.publish-item { display: flex; align-items: center; gap: var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-md); box-shadow: var(--shadow-card); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.publish-item:active { transform: scale(var(--press-scale)); }
.item-img { width: 120rpx; height: 120rpx; border-radius: var(--radius-icon); flex-shrink: 0; background: var(--bg-page); overflow: hidden; }
.item-img-el { width: 100%; height: 100%; }
.item-img-empty { display: flex; align-items: center; justify-content: center; }
.item-img-fallback { font-size: 56rpx; line-height: 1; }
.item-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.item-name { font-size: var(--font-caption); font-weight: 600; color: var(--text-primary); }
.item-meta { font-size: var(--font-aux); color: var(--text-secondary); }
.item-meta-row { display: flex; align-items: center; gap: var(--spacing-xs); }
.item-meta-icon { flex-shrink: 0; }
.item-meta-text { flex: 1; min-width: 0; }
.item-reason { font-size: var(--font-tiny); color: var(--color-error); line-height: 1.4; }
.publish-actions { padding: var(--spacing-lg) var(--spacing-md) 0; }
.sub-item { display: flex; align-items: center; gap: var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-md); box-shadow: var(--shadow-card); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.sub-item:active { transform: scale(var(--press-scale)); }
.item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.item-title { font-size: var(--font-caption); font-weight: 600; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-meta { font-size: var(--font-aux); color: var(--text-tertiary); }
.item-right { display: flex; align-items: center; gap: var(--spacing-xs); flex-shrink: 0; }
.off-tag { font-size: var(--font-tiny); color: var(--text-tertiary); }
.item-arrow { font-size: var(--font-body); color: var(--text-tertiary); }
</style>
