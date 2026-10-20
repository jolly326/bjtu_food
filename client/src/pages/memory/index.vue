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
import { computed, ref } from 'vue'
import { onHide, onShow, onUnload } from '@dcloudio/uni-app'
import RetryBlock from '@/components/base/RetryBlock.vue'
import EmptyBlock from '@/components/base/EmptyBlock.vue'
import PageLoading from '@/components/base/PageLoading.vue'
import MonthTimeline from './components/MonthTimeline.vue'
import MemoryRecordCard from './components/MemoryRecordCard.vue'
import MemoryActionsSheet from './components/MemoryActionsSheet.vue'
import { useMemoryUI } from './components/useMemoryUI'
import { issueApi, recordApi } from '@/api'
import type { IssueOptionOut, RecordOut } from '@/api'
import { formatDayLabel, todayLabelWeekdayCN } from '@/utils/date'
import TabPage from '@/components/base/TabPage.vue'
import { cooldownFrom, useCooldown } from '@/utils/backoff'
import { recordMetaLine } from '@/utils/recordMeta'

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

/** 纠错选项（长按菜单「反馈」展开） */
const options = ref<IssueOptionOut[]>([])

/** 全局互斥状态机（原位评价 / 长按菜单 / 删除确认） */
const { sheetRecordId, closeEval, closeSheet, resetAll } = useMemoryUI()

/** 当前长按菜单 / 确认弹窗指向的记录 */
const sheetRecord = computed<RecordOut | null>(
  () => records.value.find((r) => r.id === sheetRecordId.value) ?? null,
)

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

/** 连续月份轴：从最早记录月 → 当前月（降序，含中间无记录月份），空月份保留节点 */
function continuousMonths(earliest: string): string[] {
  const now = new Date()
  let y = now.getFullYear()
  let m = now.getMonth() // 0-based 当前月
  const [sy, sm] = earliest.split('-').map(Number)
  const startY = sy
  const startM = sm - 1 // 0-based
  const out: string[] = []
  while (y > startY || (y === startY && m >= startM)) {
    out.push(`${y}-${String(m + 1).padStart(2, '0')}`)
    if (m === 0) {
      y -= 1
      m = 11
    } else {
      m -= 1
    }
  }
  return out
}

/** 年月栏数据：有记录 ⇒ 连续月份轴（最早记录月 → 当前月，含空月份）；否则近三月兜底 */
const barMonths = computed<string[]>(() =>
  months.value.length
    ? continuousMonths(months.value[months.value.length - 1])
    : recentMonths(),
)

/** 当前生效月份：选中月即生效（可为空月份，下方不渲染卡片）；未选时回退最近记录月 */
const effectiveMonth = computed(() =>
  months.value.length ? activeMonth.value || months.value[0] : '',
)

/** 分组内的卡片行项目：`meta`（档口 · 价格）在这里**一次算好**，避免模板里每张卡重复求值 */
interface GroupCard {
  record: RecordOut
  meta: string
}

/** 日期分组：短日期标记 + 底层装饰数字 + 该日卡片行 */
interface DayGroup {
  day: string
  motif: string
  cards: GroupCard[]
}

/** 倒序日期分组（同一天的记录归为一组，组内保持原顺序） */
const grouped = computed<DayGroup[]>(() => {
  const list = records.value.filter((r) => r.wantedAt.slice(0, 7) === effectiveMonth.value)
  const map = new Map<string, GroupCard[]>()
  for (const record of list) {
    const day = record.wantedAt.slice(0, 10)
    const bucket = map.get(day) ?? []
    bucket.push({ record, meta: recordMetaLine(record) })
    map.set(day, bucket)
  }
  return [...map.entries()]
    .sort((a, b) => (a[0] < b[0] ? 1 : -1))
    .map(([day, cards]) => ({ day, motif: day.slice(8, 10), cards }))
})

/** 拉全量记录（唯一取数实现：首屏 / 切 tab / 删除后刷新共用） */
async function fetchRecords() {
  records.value = await recordApi.list()
  if (!activeMonth.value && months.value.length) activeMonth.value = months.value[0]
}

async function load() {
  loading.value = true
  failed.value = false
  try {
    await fetchRecords()
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
  if (cancelLocked.value) {
    uni.showToast({ title: '操作过于频繁，请稍后再试', icon: 'none' })
    return
  }
  try {
    await recordApi.cancel(record.id)
    // 先本地移除（即时反馈），再静默向服务端对齐（月份轴 / 分组 / 其他端改动）
    records.value = records.value.filter((r) => r.id !== record.id)
    uni.showToast({ title: '已删除', icon: 'none' })
    // 刷新失败不覆盖已删除的本地结果：下次进入 tab 会再拉
    void fetchRecords().catch(() => {})
  } catch (error) {
    cooldownFrom(CANCEL_KEY, error)
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

/** 纠错选项：进入页面即预拉取（长按菜单「反馈」展开时使用） */
async function loadOptions() {
  if (options.value.length) return
  try {
    options.value = await issueApi.options()
  } catch {
    /* 选项缺失时反馈入口静默降级 */
  }
}

async function onPickOption(optionId: number) {
  const target = sheetRecord.value
  if (!target || issueLocked.value) return
  try {
    await issueApi.submit(target.dishId, optionId)
    closeSheet()
    uni.showToast({ title: '已收到反馈', icon: 'none' })
  } catch (error) {
    cooldownFrom(ISSUE_KEY, error)
    uni.showToast({ title: (error as Error).message, icon: 'none' })
  }
}

/** 删除二次确认 → 删除记录（未评分 / 已评分均可；后端重算评价分） */
function onConfirmDelete(recordId: number) {
  const target = records.value.find((r) => r.id === recordId)
  if (!target) {
    uni.showToast({ title: '记录不存在，请刷新', icon: 'none' })
    return
  }
  void onCancel(target)
}

/** 空态跳转：去美味 tab */
function goRecipes() {
  uni.switchTab({ url: '/pages/cuisine/index' })
}

/** 每次进入 / 切回本 tab 都重新拉取 —— 在美味页新增的记忆返回后立即可见（tab 页不会重新挂载） */
onShow(() => {
  void load()
  void loadOptions()
})
/** 页面隐藏 / Tab 切换：强制销毁评价态与全部弹层（全局互斥铁则 4） */
onHide(resetAll)
onUnload(resetAll)
</script>

<template>
  <!-- 首屏（尚无数据）走整页加载；切 tab 重拉时保留旧列表，不闪白 -->
  <PageLoading v-if="loading && !records.length" />

  <TabPage v-else active="memory">
    <!-- 页级标题：hero 衬线 + 正下方数字日期 -->
    <template #hero>
      <view class="topbar">
        <text class="topbar__title">记忆</text>
        <text class="topbar__date">{{ today }}</text>
      </view>
    </template>

    <!-- 失败态不渲染年月栏：RetryBlock 独占 hero 下方区域并竖直居中（与美味页一致） -->
    <MonthTimeline
      v-if="!failed && barMonths.length"
      :months="barMonths"
      :model-value="effectiveMonth || barMonths[0]"
      @pick="activeMonth = $event"
    />

    <RetryBlock v-if="failed" @retry="load" />

    <EmptyBlock
      v-else-if="!records.length"
      icon="notebook"
      title="暂无美食记忆"
      desc="去美味页标记品尝过的餐食，收藏属于你的校园美食记忆"
      action-text="去美味页"
      @action="goRecipes"
    />

    <scroll-view v-else class="body" scroll-y enhanced :show-scrollbar="false">
      <view v-for="group in grouped" :key="group.day" class="group">
        <text class="group__day">{{ formatDayLabel(group.day) }}</text>
        <scroll-view
          class="group__row"
          scroll-x
          enhanced
          :show-scrollbar="false"
          @scroll="closeEval"
        >
          <!-- 行内容器：左右各留 gutter（滚动容器自身的 padding 不计入可滚宽度） -->
          <view class="group__cards">
            <MemoryRecordCard
              v-for="item in group.cards"
              :key="item.record.id"
              :record="item.record"
              :meta="item.meta"
              @react="(v: number) => onReact(item.record, v)"
            />
          </view>
        </scroll-view>
      </view>
    </scroll-view>

    <!-- 长按菜单 / 纠错表单 / 删除二次确认（全局互斥状态机驱动） -->
    <MemoryActionsSheet
      :record="sheetRecord"
      :issue-options="options"
      :issue-locked="issueLocked"
      :cancel-locked="cancelLocked"
      @submit-issue="onPickOption"
      @confirm-delete="onConfirmDelete"
    />
  </TabPage>
</template>

<style lang="scss" scoped>
/* 日期分组：装饰数字的定位祖先（组内底层背景） */
.group {
  position: relative;
  margin-bottom: var(--spacing-2xl);
}

/* 页头：盒高 / 上下内距 / 盒内垂直居中均对齐美味 Topbar（--spacing-md 上下 / --page-gutter 左右） */
.topbar {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--spacing-sm);
  box-sizing: border-box;
  min-height: var(--hero-box-height);
  padding: var(--spacing-md) var(--page-gutter);
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
  line-height: 1.2;
  color: var(--text-tertiary);
}

.body {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
  /* 年月导航与首个日期组之间的呼吸间距（与组间距同级） */
  // margin-top: var(--spacing-2xl);
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

/* 卡片行：横向滚动；层级在装饰数字之上 */
.group__row {
  position: relative;
  z-index: 1;
  width: 100%;
  white-space: nowrap;
}

/* 行内容器：左内距 = 页面 gutter（与日期标记、页头左缘对齐）；
   右/底留白 = gutter + 挂角探出余量，容纳探出卡片外的挂角头部；卡间留一档呼吸位 */
.group__cards {
  display: inline-flex;
  gap: var(--spacing-md);
  box-sizing: border-box;
  padding: 0 calc(var(--page-gutter) + var(--record-row-peek))
    calc(var(--spacing-md) + var(--record-row-peek)) var(--page-gutter);
}

/* 同组后卡 Z 轴景深：逐级轻微缩小 + 下坠 + 阴影减弱（前三层可见层次），
   横向滑动浏览时呈现「三层堆叠」的层次感；缩放围绕底边 ⇒ 下坠量即真实露出量 */
.group__cards .record:nth-child(2) {
  transform: translateY(var(--record-stack-step)) scale(0.97);
  transform-origin: bottom center;
  box-shadow: var(--shadow-card-back);
}

.group__cards .record:nth-child(3) {
  transform: translateY(calc(var(--record-stack-step) * 2)) scale(0.94);
  transform-origin: bottom center;
  box-shadow: var(--shadow-card-back-far);
}

</style>
