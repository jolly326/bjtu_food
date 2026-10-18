<script setup lang="ts">
/**
 * 记忆 tab（设计稿 docs/ui/client/记忆.md；功能规则 docs/func/client/B4、B2、B3）。
 *
 * Julienne 时间流：页头（hero 衬线标题 + 正下方「星期X，X月X日」日期）→ 横向年月导航
 * （当前月加深、其余浅灰弱化）→ 倒序日期分组（`MM.DD` 衬线日期标记）→
 * 每日横向滚动纸片卡行；卡片表面 / 圆角 / 描边 / 阴影与美味页完全统一。
 * 卡内自上而下：左上衬线粗体菜名 → 居中圆形大图（卡宽 75%）→ **底部同一基线**
 * （左反馈 / 右三态评分，均为纯线性图标、无底座）；删除走**长按浮层**，卡面无入口。
 * 每个日期组底层挂半透明大号日数字装饰（不遮挡卡片）。
 */
import { computed, onMounted, ref } from 'vue'
import BaseSheet from '@/components/base/BaseSheet.vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import RetryBlock from '@/components/base/RetryBlock.vue'
import TabBar from '@/components/base/TabBar.vue'
import AppIcon from '@/components/base/AppIcon.vue'
import MonthTimeline from './components/MonthTimeline.vue'
import ReactionPicker from './components/ReactionPicker.vue'
import { issueApi, recordApi } from '@/api'
import type { IssueOptionOut, RecordOut } from '@/api'
import { formatDayLabel, todayLabelWeekdayCN } from '@/utils/date'
import AppHeader from '@/components/base/AppHeader.vue'
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
/** 页头日期：固定值，避免每次渲染重复格式化（`星期日，10月18日` 星期在前） */
const today = todayLabelWeekdayCN()
/** 全站主题（明暗切换作用于三个 tab 页） */
const theme = useTheme()

/** 纠错弹层 */
const sheetOpen = ref(false)
const sheetTarget = ref<RecordOut | null>(null)
const options = ref<IssueOptionOut[]>([])

/** 长按浮层（擦除记忆）—— 卡片表层不展示删除入口 */
const eraseOpen = ref(false)
const eraseTarget = ref<RecordOut | null>(null)

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

async function onReact(record: RecordOut, value: number | null) {
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

/** 卡片辅助行：`档口 · N元`（价格单位「分」→ 元，整数去掉 .00；无档口时回退食堂名） */
function metaLine(record: RecordOut): string {
  const place = record.stallName || record.canteenName || ''
  const yuan = record.price > 0 ? Number((record.price / 100).toFixed(2)) : null
  if (!place) return yuan === null ? '' : `${yuan}元`
  return yuan === null ? place : `${place} · ${yuan}元`
}

/** 长按卡片整区：唤起极简浮层（仅「擦除记忆」，不做红色警示） */
function onLongPress(record: RecordOut) {
  eraseTarget.value = record
  eraseOpen.value = true
}

/** 浮层内确认擦除 */
function onErase() {
  const target = eraseTarget.value
  eraseOpen.value = false
  if (target) void onCancel(target)
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
  uni.switchTab({ url: '/pages/cuisine/index' })
}

onMounted(load)
</script>

<template>
  <view class="page" :data-theme="theme">
    <!-- 全局页头（品牌 + 明暗切换；与 TabBar 同级，三页统一） -->
    <AppHeader />

    <!-- 页级标题：hero 衬线 + 正下方数字日期 -->
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
          <view class="group__cards">
            <view v-for="c in 3" :key="c" class="record record--ph">
              <view class="record__phLine record__phLine--name" />
              <view class="record__phLine record__phLine--meta" />
              <view class="record__photoWrap">
                <view class="record__phCircle" />
              </view>
              <view class="record__ops">
                <view class="record__phDot" />
                <view class="record__phDot" />
              </view>
            </view>
          </view>
        </scroll-view>
      </view>
    </scroll-view>

    <view v-else-if="!records.length" class="recordEmpty">
      <AppIcon class="recordEmpty__icon" name="notebook" :size="64" tone="muted" />
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
          <!-- 行内容器：左右各留 gutter（滚动容器自身的 padding 不计入可滚宽度） -->
          <view class="group__cards">
            <view
              v-for="record in list"
              :key="record.id"
              class="record"
              hover-class="record--pressed"
              @longpress="onLongPress(record)"
            >
              <!-- 顶部左上：菜名（衬线粗体，卡片一级标题；右上角整片留空） -->
              <text class="record__name wrap-anywhere">{{ record.name }}</text>
              <!-- 辅助行：紧贴菜名下方、同左对齐（档口 · 价格） -->
              <text v-if="metaLine(record)" class="record__meta">{{ metaLine(record) }}</text>

              <!-- 中部：圆形菜品大图（卡内可用宽度 75%，上下留白均等） -->
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

              <!-- 底部单行（同一基线、两端对齐）：左「反馈」纯文本 / 右三态表情线性图标 -->
              <view class="record__ops">
                <view
                  class="record__feedback"
                  :class="{ 'is-disabled': issueLocked }"
                  hover-class="record__feedback--pressed"
                  @tap="onOpenIssue(record)"
                >
                  <text class="record__feedbackText">反馈</text>
                </view>
                <ReactionPicker
                  :model-value="record.reaction"
                  :locked="reactLocked"
                  @pick="onReact(record, $event)"
                />
              </view>
            </view>
          </view>
        </scroll-view>
      </view>
    </scroll-view>

    <!-- 长按浮层：仅「擦除记忆」，无红色警示 -->
    <BaseSheet v-model="eraseOpen" title="擦除这条记忆？">
      <view
        class="sheet__opt"
        :class="{ 'sheet__opt--locked': cancelLocked }"
        hover-class="sheet__opt--pressed"
        @tap="onErase"
      >
        <text class="sheet__optLabel">擦除记忆</text>
      </view>
    </BaseSheet>

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

    <TabBar active="memory" />
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
  padding: var(--spacing-xs) var(--page-gutter) var(--spacing-sm);
}

/* 主标题：与美味页「美味」完全一致（hero 档衬线 + 中黑字重） */
.topbar__title {
  font-family: var(--font-serif);
  font-size: var(--font-hero);
  font-weight: var(--weight-regular);
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
  font-weight: var(--weight-regular);
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

/* 卡片行：横向滚动；层级在装饰数字之上 */
.group__row {
  position: relative;
  z-index: 1;
  width: 100%;
  white-space: nowrap;
}

/* 行内容器：左右各留 gutter（滚动容器自身的 padding 不计入可滚宽度，故落在内层）；
   卡间留一档呼吸位，滑动时互相露出侧边 */
.group__cards {
  display: inline-flex;
  gap: var(--spacing-md);
  box-sizing: border-box;
  padding: 0 var(--page-gutter);
}

/* 记录卡：竖版圆角卡（高 > 宽）—— 圆角 / 阴影与美味页卡片统一；
   顶部菜名 + 中部圆形大图 + 底部两枚线性图标 */
.record {
  display: inline-flex;
  flex: none;
  flex-direction: column;
  box-sizing: border-box;
  width: var(--record-card-width);
  height: var(--record-card-height);
  padding: var(--spacing-2xl) var(--spacing-xl) var(--spacing-xl);
  background: var(--bg-page);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card-stack);
  vertical-align: top;
  transition: transform var(--duration-base) var(--ease-out);
}

/* 卡片点击 / 长按反馈：仅轻微缩放，无花哨动效 */
.record--pressed {
  transform: scale(0.98);
}

/* 菜名：顶部内边距之下、左上起点，衬线粗体、卡片内最大字号（一级标题） */
.record__name {
  display: -webkit-box;
  overflow: hidden;
  font-family: var(--font-serif);
  font-size: var(--font-h3);
  font-weight: var(--weight-bold);
  line-height: 1.3;
  color: var(--text-title);
  text-align: left;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

/* 辅助行：档口 · 价格 —— 紧贴菜名正下方、同左对齐、不换行；衬线细体浅灰 */
.record__meta {
  margin-top: var(--spacing-2xs);
  font-family: var(--font-serif);
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
  white-space: nowrap;
}

/* 中部：圆形菜品大图（视觉主体），上下留白均等 */
.record__photoWrap {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 0;
  margin-top: var(--spacing-lg);
}

.record__photo {
  display: flex;
  flex: none;
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

/* 底部：同一水平基线 —— 左反馈 / 右三态评价 */
.record__ops {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--spacing-lg);
}

/* 反馈：纯文本操作 —— 无下划线、无底色、无外框；命中区仍满足触达下限 */
.record__feedback {
  display: flex;
  align-items: center;
  min-height: var(--tap-target-size);
  padding-right: var(--spacing-sm);
}

/* 反馈文案：衬线浅灰，字号与正文同级（保证可点可辨） */
.record__feedbackText {
  font-family: var(--font-serif);
  font-size: var(--font-subtitle);
  font-weight: var(--weight-regular);
  line-height: 1;
  color: var(--text-tertiary);
}

/* 点击态：仅文字颜色轻微加深 */
.record__feedback--pressed .record__feedbackText {
  color: var(--text-body);
}

/* 禁用（限频退避）：opacity .5，禁点由全局 .is-disabled 承担 */
.record__feedback.is-disabled {
  opacity: 0.5;
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

.record__phLine--meta {
  width: 45%;
  height: var(--font-small);
  margin-top: var(--spacing-2xs);
}

.record__phCircle {
  width: var(--record-photo-size);
  height: var(--record-photo-size);
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
}

/* 图标位占位：与线性图标同尺寸（无圆形底） */
.record__phDot {
  width: calc(var(--tap-target-size) - var(--spacing-2xl));
  height: calc(var(--tap-target-size) - var(--spacing-2xl));
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
  font-weight: var(--weight-regular);
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
