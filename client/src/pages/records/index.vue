<script setup lang="ts">
/**
 * 记忆 tab（设计稿 docs/ui/client/我的食谱.md；功能规则 docs/func/client/B4、B2、B3）。
 *
 * Julienne 时间流：页头（衬线标题 + 日期）→ 横向年月导航 → 倒序日期分组（衬线日期标题）
 * → 每日横向滚动卡片行；卡片为奶白纸片（左上评级字母 + 右上风味评级 + 圆图 + 衬线菜名 + 时间小字 + 右下√已品尝）。
 * 就地三态评分（喜欢 / 一般 / 不喜欢）；未评分可取消；纠错入口；空态/加载骨架同 Julienne 风格。
 */
import { computed, onMounted, ref } from 'vue'
import BaseSheet from '@/components/base/BaseSheet.vue'
import RetryBlock from '@/components/base/RetryBlock.vue'
import TabBar from '@/components/base/TabBar.vue'
import AppIcon from '@/components/base/AppIcon.vue'
import FlavorRating from '@/components/business/FlavorRating.vue'
import MonthTimeline from '@/components/business/MonthTimeline.vue'
import ReactionPicker from '@/components/business/ReactionPicker.vue'
import { issueApi, recordApi } from '@/api'
import type { IssueOptionOut, RecordOut } from '@/api'
import { formatClock, formatDayLabel, todayLabel } from '@/utils/date'
import { navMetrics } from '@/utils/safeArea'

const records = ref<RecordOut[]>([])
const loading = ref(true)
const failed = ref(false)
const activeMonth = ref('')
/** 顶部安全区：避开状态栏 / 刘海与右上角胶囊 */
const topInset = navMetrics().topInset
/** 页头日期：固定值，避免每次渲染重复格式化 */
const today = todayLabel()

/** 纠错弹层 */
const sheetOpen = ref(false)
const sheetTarget = ref<RecordOut | null>(null)
const options = ref<IssueOptionOut[]>([])

const months = computed(() => {
  const set = new Set(records.value.map((r) => r.wantedAt.slice(0, 7)))
  return [...set].sort().reverse()
})

/** 无记录时的兜底年月栏（当前月 + 前两个月，纯装饰） */
function recentMonths(): string[] {
  const now = new Date()
  return [0, 1, 2].map((offset) => {
    const d = new Date(now.getFullYear(), now.getMonth() - offset, 1)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
  })
}

/** 年月栏数据：有记录用记录月份，否则用近三月兜底 */
const barMonths = computed(() => (months.value.length ? months.value : recentMonths()))

/** 当前生效月份：选中月不在记录月份时回退到最近记录月 */
const effectiveMonth = computed(() =>
  months.value.length
    ? months.value.includes(activeMonth.value)
      ? activeMonth.value
      : months.value[0]
    : '',
)

/** 页面底层装饰：当前生效月份的数字（无记录时用兜底月份） */
const motifMonth = computed(() => {
  const month = effectiveMonth.value || barMonths.value[0] || ''
  return month.slice(5, 7)
})

const grouped = computed(() => {
  const list = records.value.filter((r) => r.wantedAt.slice(0, 7) === effectiveMonth.value)
  const map = new Map<string, RecordOut[]>()
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

async function onReact(record: RecordOut, value: number) {
  try {
    await recordApi.react(record.id, value)
    record.reaction = value
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

async function onCancel(record: RecordOut) {
  try {
    await recordApi.cancel(record.id)
    records.value = records.value.filter((r) => r.id !== record.id)
  } catch (error) {
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

async function onOpenIssue(record: RecordOut) {
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

/** 空态跳转：去美食 tab */
function goRecipes() {
  uni.switchTab({ url: '/pages/recipes/index' })
}

onMounted(load)
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <!-- 极淡的页面底层装饰：当前月份数字 -->
    <text v-if="motifMonth" class="page__motif">{{ motifMonth }}</text>

    <view class="topbar">
      <text class="topbar__title">记忆</text>
      <text class="topbar__date">{{ today }}</text>
    </view>

    <MonthTimeline
      v-if="barMonths.length"
      :months="barMonths"
      :model-value="effectiveMonth || barMonths[0]"
      @pick="activeMonth = $event"
    />

    <RetryBlock v-if="failed" @retry="load" />

    <scroll-view v-else-if="loading" class="body" scroll-y enhanced :show-scrollbar="false">
      <view v-for="g in 2" :key="g" class="group">
        <view class="group__day group__day--ph" />
        <scroll-view class="group__row" scroll-x enhanced :show-scrollbar="false">
          <view v-for="c in 3" :key="c" class="record record--ph">
            <view class="record__phCircle" />
            <view class="record__phLine" />
            <view class="record__phLine record__phLine--meta" />
          </view>
        </scroll-view>
      </view>
    </scroll-view>

    <view v-else-if="!records.length" class="recordEmpty">
      <AppIcon class="recordEmpty__icon" name="book" :size="64" tone="muted" />
      <text class="recordEmpty__title">暂无美食记忆</text>
      <text class="recordEmpty__desc">去美食页标记品尝过的餐食，收藏属于你的校园美食记忆</text>
      <view class="recordEmpty__btn" hover-class="recordEmpty__btn--pressed" @tap="goRecipes">
        去美食页
      </view>
    </view>

    <scroll-view v-else class="body" scroll-y enhanced :show-scrollbar="false">
      <view v-for="[day, list] in grouped" :key="day" class="group">
        <text class="group__day">{{ formatDayLabel(day) }}</text>
        <scroll-view class="group__row" scroll-x enhanced :show-scrollbar="false">
          <view v-for="record in list" :key="record.id" class="record">
            <FlavorRating class="record__flavor" :level="record.flavorLevel ?? null" />

            <view class="record__photo">
              <image
                v-if="record.imageUrl"
                :src="record.imageUrl"
                class="record__img"
                mode="aspectFill"
              />
              <view v-else class="record__ph" />
            </view>

            <text class="record__name wrap-anywhere">{{ record.name }}</text>
            <text class="record__meta">{{ formatClock(record.wantedAt) }}</text>

            <ReactionPicker :model-value="record.reaction" @pick="onReact(record, $event)" />

            <view class="record__footer">
              <view class="record__ops">
                <text v-if="record.reaction == null" class="record__op" @tap="onCancel(record)">
                  取消
                </text>
                <text class="record__op" @tap="onOpenIssue(record)">信息有误</text>
              </view>
              <AppIcon name="check" :size="24" tone="primary" />
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
  position: relative;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: 100vh;
  overflow: hidden;
}

/* 极淡底层装饰：当前月份数字，不参与交互、不遮挡内容 */
.page__motif {
  position: absolute;
  right: calc(var(--spacing-2xl) * -1);
  bottom: 12%;
  z-index: 0;
  font-family: var(--font-serif);
  font-size: var(--memory-motif-size);
  font-weight: var(--weight-heavy);
  line-height: 1;
  color: var(--text-tertiary);
  opacity: 0.06;
  pointer-events: none;
}

.topbar {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md) var(--page-gutter) var(--spacing-xs);
}

.topbar__title {
  font-family: var(--font-serif);
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
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
  padding-bottom: calc(
    var(--tabbar-height) + var(--tabbar-gap) + var(--spacing-md) + env(safe-area-inset-bottom)
  );
}

/* 日期分组：无分割线，仅靠留白分层 */
.group {
  margin-bottom: var(--spacing-2xl);
}

.group__day {
  display: block;
  padding: 0 var(--page-gutter) var(--spacing-md);
  font-family: var(--font-serif);
  font-size: var(--font-title);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
}

/* 骨架：日期标题占位 */
.group__day--ph {
  width: 40%;
  height: var(--font-title);
  margin: 0 var(--page-gutter) var(--spacing-md);
  padding: 0;
  background: var(--bg-placeholder);
  border-radius: var(--radius-xs);
}

/* 卡片行：横向滚动，右侧留出 gutter */
.group__row {
  width: 100%;
  padding-right: var(--page-gutter);
  white-space: nowrap;
}

/* 记录卡：奶白纸片、无描边、大圆角、柔和投影 */
.record {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
  width: var(--record-card-width);
  margin-left: var(--page-gutter);
  padding: var(--spacing-md);
  background: var(--bg-card-milky);
  border-radius: var(--radius-card-lg);
  box-shadow: var(--module-shadow);
  vertical-align: top;
}

/* 风味评级：卡内首行，与圆形图保持安全间距（文字区不侵入圆图） */
.record__flavor {
  margin-bottom: var(--spacing-xs);
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

.record__name {
  font-family: var(--font-serif);
  font-size: var(--font-h3);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
  text-align: center;
}

.record__meta {
  font-size: var(--font-small);
  color: var(--text-tertiary);
}

.record__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.record__ops {
  display: flex;
  gap: var(--spacing-md);
}

.record__op {
  font-size: var(--font-small);
  color: var(--text-tertiary);
}

/* 加载骨架：淡占位块，圆形图位，无旋转 loading */
.record--ph {
  pointer-events: none;
}

.record__phCircle {
  width: var(--record-photo-size);
  height: var(--record-photo-size);
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
}

.record__phLine {
  width: 60%;
  height: var(--font-body);
  background: var(--bg-placeholder);
  border-radius: var(--radius-xs);
}

.record__phLine--meta {
  width: 40%;
  height: var(--font-small);
}

/* 空态：整体略偏上（底部预留卡片空间），线性图标 + 衬线主文案 + 浅灰引导 + 描边按钮 */
.recordEmpty {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  padding: 0 var(--page-gutter)
    calc(
      var(--tabbar-height) + var(--tabbar-gap) + var(--spacing-md) + env(safe-area-inset-bottom) +
        10vh
    );
}

.recordEmpty__icon {
  margin-bottom: var(--spacing-xs);
  opacity: 0.55;
}

.recordEmpty__title {
  font-family: var(--font-serif);
  font-size: var(--font-h2);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
  text-align: center;
}

.recordEmpty__desc {
  padding: 0 var(--spacing-xl);
  font-size: var(--font-small);
  line-height: 1.6;
  color: var(--text-tertiary);
  text-align: center;
}

.recordEmpty__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--tap-target-size);
  margin-top: var(--spacing-md);
  padding: 0 var(--spacing-xl);
  border: 1rpx solid var(--color-primary);
  border-radius: var(--radius-btn);
  color: var(--text-title);
  font-size: var(--font-subtitle);
  font-weight: var(--weight-medium);
}

.recordEmpty__btn--pressed {
  opacity: 0.6;
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
