<script setup lang="ts">
/**
 * 记忆 tab（设计稿 docs/ui/client/记忆.md；功能规则 docs/func/client/B4、B2、B3）。
 *
 * Julienne 时间流：页头（hero 衬线加粗标题 + 正下方数字日期）→ 横向年月导航
 * （当前月加深、其余浅灰弱化）→ 倒序日期分组（`MM.DD` 衬线日期标记）→
 * 每日横向滚动纸片卡行；卡片表面 / 圆角 / 描边 / 阴影与美味页完全统一。
 * 卡内自上而下：左上衬线菜名 → 居中圆形菜品图 → **右下角集中三图标**
 * （笑脸三态评分 [最醒目] / 气泡反馈 [中] / 垃圾桶删除 [最弱]）。
 * 每个日期组底层挂半透明大号日数字装饰（不遮挡卡片）。
 */
import { computed, onMounted, ref } from 'vue'
import BaseSheet from '@/components/base/BaseSheet.vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import RetryBlock from '@/components/base/RetryBlock.vue'
import TabBar from '@/components/base/TabBar.vue'
import AppIcon from '@/components/base/AppIcon.vue'
import MonthTimeline from '@/components/business/MonthTimeline.vue'
import ReactionPicker from '@/components/business/ReactionPicker.vue'
import { issueApi, recordApi } from '@/api'
import type { IssueOptionOut, RecordOut } from '@/api'
import { formatDayLabel, todayLabelNumeric } from '@/utils/date'
import { navMetrics } from '@/utils/safeArea'
import { useTheme } from '@/utils/theme'
import { cooldownFrom, useCooldown } from '@/utils/backoff'

/** 写入口的限频退避键（docs/secur/S-03）—— 评分 / 删除 / 纠错各自独立计数 */
const REACT_KEY = 'records:react'
const CANCEL_KEY = 'records:cancel'
const ISSUE_KEY = 'records:issue'
const { locked: reactLocked } = useCooldown(REACT_KEY)
const { locked: cancelLocked } = useCooldown(CANCEL_KEY)
const { locked: issueLocked } = useCooldown(ISSUE_KEY)

const records = ref<RecordOut[]>([])
const loading = ref(true)
const failed = ref(false)
const activeMonth = ref('')
/** 顶部安全区：避开状态栏 / 刘海与右上角胶囊 */
const topInset = navMetrics().topInset
/** 页头日期：固定值，避免每次渲染重复格式化（`2026/10/18` 数字斜线风格） */
const today = todayLabelNumeric()
/** 全站主题（明暗切换作用于三个 tab 页） */
const theme = useTheme()

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

/** 日期组底层装饰数字：日部分（`DD`，如 `18`），与 `MM.DD` 日期标记呼应 */
function motifOfDay(day: string): string {
  return day.slice(8, 10)
}

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
  if (reactLocked.value) return
  try {
    await recordApi.react(record.id, value)
    record.reaction = value
  } catch (error) {
    cooldownFrom(REACT_KEY, error)
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

async function onCancel(record: RecordOut) {
  if (cancelLocked.value) return
  try {
    await recordApi.cancel(record.id)
    records.value = records.value.filter((r) => r.id !== record.id)
  } catch (error) {
    cooldownFrom(CANCEL_KEY, error)
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
  if (!target || issueLocked.value) return
  try {
    await issueApi.submit(target.dishId, optionId)
    sheetOpen.value = false
    uni.showToast({ title: '已收到反馈', icon: 'none' })
  } catch (error) {
    cooldownFrom(ISSUE_KEY, error)
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

/** 空态跳转：去美味 tab */
function goRecipes() {
  uni.switchTab({ url: '/pages/recipes/index' })
}

onMounted(load)
</script>

<template>
  <view class="page" :data-theme="theme" :style="{ paddingTop: topInset + 'px' }">
    <!-- 页头：hero 衬线加粗标题 + 正下方数字日期（对齐美味 Topbar） -->
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
            <view class="record__phLine record__phLine--name" />
            <view class="record__photoWrap">
              <view class="record__phCircle" />
            </view>
            <view class="record__ops">
              <view class="record__phDot" />
              <view class="record__phDot" />
              <view class="record__phDot" />
            </view>
          </view>
        </scroll-view>
      </view>
    </scroll-view>

    <view v-else-if="!records.length" class="recordEmpty">
      <AppIcon class="recordEmpty__icon" name="book" :size="64" tone="muted" />
      <text class="recordEmpty__title">暂无美食记忆</text>
      <text class="recordEmpty__desc">去美味页标记品尝过的餐食，收藏属于你的校园美食记忆</text>
      <view class="recordEmpty__btn" hover-class="recordEmpty__btn--pressed" @tap="goRecipes">
        去美味页
      </view>
    </view>

    <scroll-view v-else class="body" scroll-y enhanced :show-scrollbar="false">
      <view v-for="[day, list] in grouped" :key="day" class="group">
        <!-- 日期组底层装饰：半透明大号日数字（不遮挡卡片，卡片行层级在上） -->
        <text class="group__motif">{{ motifOfDay(day) }}</text>
        <text class="group__day">{{ formatDayLabel(day) }}</text>
        <scroll-view class="group__row" scroll-x enhanced :show-scrollbar="false">
          <view v-for="record in list" :key="record.id" class="record">
            <!-- 菜名：卡片顶部左上，衬线一级主文字 -->
            <text class="record__name wrap-anywhere">{{ record.name }}</text>

            <!-- 圆形菜品图：放大、垂直居中（卡片视觉主元素） -->
            <view class="record__photoWrap">
              <view class="record__photo">
                <image
                  v-if="record.imageUrl"
                  :src="record.imageUrl"
                  class="record__img"
                  mode="aspectFill"
                />
                <ImagePlaceholder v-else :size="48" bare />
              </view>
            </view>

            <!-- 右下角集中三图标：笑脸评分（最醒目）/ 气泡反馈（中）/ 垃圾桶删除（最弱） -->
            <view class="record__ops">
              <ReactionPicker
                :model-value="record.reaction"
                :locked="reactLocked"
                @pick="onReact(record, $event)"
              />
              <view
                class="record__icon"
                :class="{ 'is-disabled': issueLocked }"
                hover-class="record__icon--pressed"
                @tap="onOpenIssue(record)"
              >
                <view class="record__iconCircle">
                  <AppIcon name="message-circle" :size="32" tone="default" />
                </view>
              </view>
              <view
                class="record__icon record__icon--plain"
                :class="{ 'is-disabled': cancelLocked }"
                hover-class="record__icon--pressed"
                @tap="onCancel(record)"
              >
                <AppIcon name="trash-2" :size="28" tone="muted" />
              </view>
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
        :class="{ 'sheet__opt--locked': issueLocked }"
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

/* 日期分组：装饰数字的定位祖先（组内底层背景） */
.group {
  position: relative;
  margin-bottom: var(--spacing-2xl);
}

/* 日期组底层装饰：半透明大号日数字 —— 挂在对应日期组背景（非页面底部），
   不参与交互、不遮挡卡片（日期标题与卡片行层级在上） */
.group__motif {
  position: absolute;
  right: calc(var(--spacing-2xl) * -1);
  bottom: 0;
  z-index: 0;
  font-family: var(--font-serif);
  font-size: var(--memory-motif-size);
  font-weight: var(--weight-heavy);
  line-height: 1;
  color: var(--text-tertiary);
  opacity: 0.06;
  pointer-events: none;
}
/* 页头：垂直位置与左右边距对齐美味 Topbar（上 40 / 左右 32 / 下 16） */
.topbar {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2xs);
  padding: var(--spacing-xl) var(--page-gutter) var(--spacing-sm);
}

/* 主标题：与美味页「美味」完全一致（hero 档衬线 + 中黑字重） */
.topbar__title {
  font-family: var(--font-serif);
  font-size: var(--font-hero);
  font-weight: var(--weight-medium);
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

/* 日期标记：MM.DD 衬线放大，置于卡片组左上方作时间分割标记（层级在装饰数字之上） */
.group__day {
  position: relative;
  z-index: 1;
  display: block;
  padding: 0 var(--page-gutter) var(--spacing-md);
  font-family: var(--font-serif);
  font-size: var(--font-display);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
}

/* 骨架：日期标记占位 */
.group__day--ph {
  width: 40%;
  height: var(--font-display);
  margin: 0 var(--page-gutter) var(--spacing-md);
  padding: 0;
  background: var(--bg-placeholder);
  border-radius: var(--radius-xs);
}

/* 卡片行：横向滚动，右侧留出 gutter；层级在装饰数字之上 */
.group__row {
  position: relative;
  z-index: 1;
  width: 100%;
  padding-right: var(--page-gutter);
  white-space: nowrap;
}

/* 记录卡：宽扁纸片卡 —— 表面与美味卡片族完全统一（同色填充 + 细描边 + 大圆角 + 柔和浅阴影） */
.record {
  display: inline-flex;
  flex-direction: column;
  box-sizing: border-box;
  width: var(--record-card-width);
  height: var(--record-card-height);
  margin-left: var(--page-gutter);
  padding: var(--spacing-lg);
  background: var(--bg-page);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card-stack);
  vertical-align: top;
}

/* 菜名：卡片顶部左上，衬线一级主文字 */
.record__name {
  font-family: var(--font-serif);
  font-size: var(--font-h3);
  font-weight: var(--weight-semibold);
  line-height: 1.3;
  color: var(--text-title);
  text-align: left;
}

/* 圆图：放大、垂直居中（卡片视觉主元素） */
.record__photoWrap {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 0;
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

/* 圆角必须落在这里（原生 <image> 不吃父级 overflow 裁切） */
.record__img {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-circle);
}

/* 右下角集中三图标：笑脸评分（最醒目）/ 气泡反馈（中）/ 垃圾桶删除（最弱） */
.record__ops {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-md);
}

/* 圆形图标按钮：命中区 88rpx，视觉圆 64rpx 细描边（Julienne 角标形态） */
.record__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--tap-target-size);
  height: var(--tap-target-size);
}

.record__iconCircle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: calc(var(--tap-target-size) - var(--spacing-md));
  height: calc(var(--tap-target-size) - var(--spacing-md));
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-circle);
}

/* 垃圾桶：无圆环的纯线性小图标（视觉最弱，降低误触概率；命中区仍满足触达下限） */
.record__icon--plain {
  width: var(--tap-target-size);
  height: var(--tap-target-size);
}

/* 禁用（限频退避）：非主色可点件 ⇒ opacity .5，禁点由全局 .is-disabled 承担 */
.record__icon.is-disabled {
  opacity: 0.5;
}

.record__icon--pressed {
  opacity: 0.6;
}

/* 加载骨架：淡占位块，同新卡结构（菜名 / 圆图 / 图标位） */
.record--ph {
  pointer-events: none;
}

.record__phLine {
  background: var(--bg-placeholder);
  border-radius: var(--radius-xs);
}

.record__phLine--name {
  width: 60%;
  height: var(--font-h3);
}

.record__phCircle {
  width: var(--record-photo-size);
  height: var(--record-photo-size);
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
}

.record__phDot {
  width: calc(var(--tap-target-size) - var(--spacing-md));
  height: calc(var(--tap-target-size) - var(--spacing-md));
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
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
