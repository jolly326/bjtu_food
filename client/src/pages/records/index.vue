<script setup lang="ts">
/**
 * 回味 tab（设计稿 docs/ui/client/我的食谱.md；功能规则 docs/func/client/B4、B2、B3）。
 * 月份时间线 + 月内按日期分组卡片行；就地三态评分；未评分可取消；纠错入口。
 */
import { computed, onMounted, ref } from 'vue'
import BaseSheet from '@/components/base/BaseSheet.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import RetryBlock from '@/components/base/RetryBlock.vue'
import TabBar from '@/components/base/TabBar.vue'
import MonthTimeline from '@/components/business/MonthTimeline.vue'
import ReactionPicker from '@/components/business/ReactionPicker.vue'
import { issueApi, recordApi } from '@/api'
import { navMetrics } from '@/utils/safeArea'

type Record = {
  id: number
  dishId: number
  name: string
  imageUrl?: string | null
  wantedAt: string
  reaction?: number | null
}

const records = ref<Record[]>([])
const loading = ref(true)
const failed = ref(false)
const activeMonth = ref('')
/** 顶部安全区：避开状态栏 / 刘海与右上角胶囊 */
const topInset = navMetrics().topInset

/** 纠错弹层 */
const sheetOpen = ref(false)
const sheetTarget = ref<Record | null>(null)
const options = ref<{ id: number; name: string }[]>([])

const months = computed(() => {
  const set = new Set(records.value.map((r) => r.wantedAt.slice(0, 7)))
  return [...set].sort().reverse()
})

const grouped = computed(() => {
  const list = records.value.filter((r) => r.wantedAt.slice(0, 7) === activeMonth.value)
  const map = new Map<string, Record[]>()
  for (const r of list) {
    const day = r.wantedAt.slice(0, 10)
    const bucket = map.get(day) ?? []
    bucket.push(r)
    map.set(day, bucket)
  }
  return [...map.entries()].sort((a, b) => (a[0] < b[0] ? 1 : -1))
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    records.value = await recordApi.list()
    if (!activeMonth.value && months.value.length) activeMonth.value = months.value[0]
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

async function onReact(record: Record, value: number) {
  try {
    await recordApi.react(record.id, value)
    record.reaction = value
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

async function onCancel(record: Record) {
  try {
    await recordApi.cancel(record.id)
    records.value = records.value.filter((r) => r.id !== record.id)
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

async function onOpenIssue(record: Record) {
  sheetTarget.value = record
  sheetOpen.value = true
  if (!options.value.length) {
    try {
      options.value = await issueApi.options()
    } catch {
      uni.showToast({ title: '选项加载失败', icon: 'none' })
    }
  }
}

async function onPickOption(optionId: number) {
  const target = sheetTarget.value
  if (!target) return
  try {
    await issueApi.submit(target.dishId, optionId)
    sheetOpen.value = false
    uni.showToast({ title: '已收到反馈', icon: 'none' })
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

onMounted(load)
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <view class="topbar">
      <text class="topbar__title">回味</text>
      <text class="topbar__date">{{ new Date().toLocaleDateString('zh-CN') }}</text>
    </view>

    <MonthTimeline
      v-if="months.length"
      :months="months"
      :model-value="activeMonth"
      @pick="activeMonth = $event"
    />

    <RetryBlock v-if="failed" @retry="load" />

    <EmptyState v-else-if="!records.length && !loading" title="暂无饮食记录" />

    <scroll-view v-else class="body" scroll-y enhanced :show-scrollbar="false">
      <view v-for="[day, list] in grouped" :key="day" class="group">
        <text class="group__day">{{ day.slice(5).replace('-', '.') }}</text>
        <scroll-view class="group__row" scroll-x enhanced :show-scrollbar="false">
          <view v-for="record in list" :key="record.id" class="record">
            <text class="record__name wrap-anywhere">{{ record.name }}</text>
            <view class="record__photo">
              <image v-if="record.imageUrl" :src="record.imageUrl" class="record__img" mode="aspectFill" />
              <view v-else class="record__ph" />
            </view>
            <ReactionPicker :model-value="record.reaction" @pick="onReact(record, $event)" />
            <view class="record__ops">
              <text v-if="record.reaction == null" class="record__op" @tap="onCancel(record)">取消</text>
              <text class="record__op" @tap="onOpenIssue(record)">信息有误</text>
            </view>
          </view>
        </scroll-view>
      </view>
    </scroll-view>

    <BaseSheet v-model="sheetOpen" title="哪里不对？">
      <view
        v-for="opt in options"
        :key="opt.id"
        class="sheet__opt"
        hover-class="sheet__opt--pressed"
        @tap="onPickOption(opt.id)"
      >
        <text class="sheet__optLabel">{{ opt.name }}</text>
      </view>
    </BaseSheet>

    <TabBar active="records" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: 100vh;
  overflow: hidden;
}

.topbar {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: var(--spacing-md) var(--page-gutter) var(--spacing-xs);
}

.topbar__title {
  font-size: var(--font-display);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--text-title);
}

.topbar__date {
  font-size: var(--font-small);
  color: var(--text-tertiary);
}

.body {
  flex: 1;
  min-height: 0;
  padding-bottom: calc(var(--tabbar-height) + var(--tabbar-gap) + env(safe-area-inset-bottom) + var(--spacing-lg));
}

.group {
  margin-bottom: var(--spacing-xl);
}

.group__day {
  display: block;
  padding: 0 var(--page-gutter) var(--spacing-sm);
  font-size: var(--font-h3);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
}

.group__row {
  width: 100%;
  white-space: nowrap;
}

.record {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
  width: var(--record-card-width);
  margin-left: var(--page-gutter);
  padding: var(--spacing-md);
  background: var(--module-bg);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
  vertical-align: top;
}

.record__name {
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--text-title);
  text-align: center;
}

.record__photo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--record-photo-size);
  height: var(--record-photo-size);
  overflow: hidden;
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
}

.record__img {
  width: 100%;
  height: 100%;
}

.record__ph {
  width: 100%;
  height: 100%;
}

.record__ops {
  display: flex;
  gap: var(--spacing-md);
}

.record__op {
  font-size: var(--font-small);
  color: var(--text-tertiary);
}

.sheet__opt {
  display: flex;
  align-items: center;
  height: var(--tap-target-size);
  border-bottom: 1rpx solid var(--border-color);
}

.sheet__opt:last-child {
  border-bottom: none;
}

.sheet__opt--pressed {
  opacity: 0.7;
}

.sheet__optLabel {
  font-size: var(--font-body);
  color: var(--text-title);
}
</style>
