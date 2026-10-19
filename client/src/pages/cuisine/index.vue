<script setup lang="ts">
/**
 * 美味 tab（设计稿 docs/ui/client/美味.md；功能规则 docs/func/client/A2、A3、B1）。
 *
 * Julienne Z 轴堆叠卡片栈：顶部信息区块（衬线大标题 + 日期）→ 右上食堂筛选 →
 * 纸片卡片栈（Idle/Drag/Release；卡片含顶部吊孔，层线由卡片栈承担）。
 * 点「去品尝」调用 B1 记录接口（自动写入记忆页当日记录），Toast 轻提示、无弹跳。
 * 本批卡片划完 ⇒ 流末尾空态（重新浏览 / 随机盲选，见 docs/func/client/A2）。
 */
import { computed, onMounted, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import RetryBlock from '@/components/base/RetryBlock.vue'
import CanteenFilter from './components/CanteenFilter.vue'
import DishCardStack from './components/DishCardStack.vue'
import DishFlowEnd from './components/DishFlowEnd.vue'
import { canteenApi, dishApi, recordApi } from '@/api'
import type { CanteenOut, DishCardOut } from '@/api'
import { todayLabelWeekdayCN } from '@/utils/date'
import { freshBatch, uniqueById } from '@/utils/dishBatch'
import TabPage from '@/components/base/TabPage.vue'
import { cooldownFrom, useCooldown } from '@/utils/backoff'

/** 「去品尝」入口的限频退避键（docs/secur/S-03） */
const WANT_KEY = 'recipes:want'
const { locked: wantLocked } = useCooldown(WANT_KEY)

/** 当前批次（原顺序，供「重新浏览」复用） */
const batch = ref<DishCardOut[]>([])
/** 当前剩余待滑卡片 */
const cards = ref<DishCardOut[]>([])
const canteens = ref<CanteenOut[]>([])
const canteenId = ref<number | undefined>(undefined)
/** 正在提交的菜品 id（仅该卡按钮进入 loading） */
const submittingId = ref<number | null>(null)
/** 随机盲选在途 */
const surprising = ref(false)
const failed = ref(false)
const loading = ref(true)
/** 流末尾：本批非空且已全部划完（与「无菜品」空态区分） */
const finished = computed(
  () => !loading.value && !failed.value && batch.value.length > 0 && cards.value.length === 0,
)
/** 页头日期：固定值，避免每次渲染重复格式化（Julienne 风格：星期日，10月18日） */
const today = todayLabelWeekdayCN()

async function load() {
  loading.value = true
  failed.value = false
  try {
    const [dishList, canteenList] = await Promise.all([
      dishApi.list({ canteenId: canteenId.value, pageSize: 20 }),
      canteenApi.list(),
    ])
    batch.value = uniqueById(dishList.records)
    cards.value = [...batch.value]
    canteens.value = canteenList
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

/** 切回本 tab 时同步「去品尝」态 —— 记忆页的评分 / 取消会让本页缓存的 `wanted` 失效 */
onShow(async () => {
  if (!batch.value.length) return
  try {
    const list = await recordApi.list()
    const wantedIds = new Set(list.filter((r) => r.reaction == null).map((r) => r.dishId))
    for (const card of batch.value) card.wanted = wantedIds.has(card.id)
  } catch {
    // 同步失败不影响浏览，下次切回本 tab 再试
  }
})

/** 本会话内「去品尝」返回的记录 id（用于取消）—— 服务端只给 `wanted` 布尔，不带 id */
const recordIdByDish = ref<Map<number, number>>(new Map())

/** 优先用本会话缓存的记录 id；否则回查清单按 dishId 找（记录量级小） */
async function findRecordId(dishId: number): Promise<number | null> {
  const cached = recordIdByDish.value.get(dishId)
  if (cached != null) return cached
  const list = await recordApi.list()
  return list.find((r) => r.dishId === dishId)?.id ?? null
}

/** 「去品尝 / 取消品尝」切换：未品尝 ⇒ POST（写入当日记忆）；已品尝 ⇒ DELETE（仅未评分可撤销） */
async function onWant(dish: DishCardOut) {
  if (submittingId.value != null || wantLocked.value) return
  submittingId.value = dish.id
  try {
    if (dish.wanted) {
      const id = await findRecordId(dish.id)
      if (id == null) {
        uni.showToast({ title: '找不到可取消的记录', icon: 'none' })
        return
      }
      await recordApi.cancel(id)
      dish.wanted = false
      recordIdByDish.value.delete(dish.id)
      return
    }
    const record = await recordApi.want(dish.id)
    dish.wanted = true
    recordIdByDish.value.set(dish.id, record.id)
    uni.showToast({ title: '已记录到记忆', icon: 'none', duration: 3000 })
  } catch (error) {
    // 429：按服务端给的秒数禁用本入口，倒计时结束自动恢复
    cooldownFrom(WANT_KEY, error)
    // 含边界：已评分的记录不可撤销（后端 400），此时保持已品尝态
    uni.showToast({ title: (error as Error).message || '操作失败，请重试', icon: 'none' })
  } finally {
    submittingId.value = null
  }
}

/** 卡片飞出后移出栈，下一张晋升顶层（批次保留，供重新浏览） */
function onSwipe(id: number) {
  // 只移除**第一张**匹配的卡（数据里若出现重复菜品，filter 会一次删掉多张）
  const index = cards.value.findIndex((c) => c.id === id)
  if (index >= 0) cards.value = cards.value.filter((_, i) => i !== index)
}

/** 重新浏览：本批原顺序再来一遍，不发请求 */
function onReplay() {
  cards.value = [...batch.value]
}

/** 随机盲选：重新取一批（沿用食堂筛选），排除本批已出现的菜品 */
async function onSurprise() {
  if (surprising.value) return
  surprising.value = true
  try {
    const res = await dishApi.list({ canteenId: canteenId.value, pageSize: 20 })
    const next = freshBatch(res.records, batch.value)
    if (!next.length) {
      uni.showToast({ title: '暂无更多菜品', icon: 'none' })
      return
    }
    batch.value = uniqueById(next)
    cards.value = [...batch.value]
  } catch (error) {
    uni.showToast({ title: (error as Error).message || '加载失败，请重试', icon: 'none' })
  } finally {
    surprising.value = false
  }
}

function onFilterChange(id?: number) {
  canteenId.value = id
  load()
}

onMounted(load)
</script>

<template>
  <TabPage active="cuisine">
    <!-- 页级标题块（hero 插槽）：标题 + 日期 + 筛选器 -->
    <template #hero>
      <view class="topbar">
        <view class="topbar__left">
          <text class="topbar__title">美味</text>
          <text class="topbar__date">{{ today }}</text>
        </view>
        <CanteenFilter :options="canteens" :model-value="canteenId" @pick="onFilterChange" />
      </view>
    </template>

    <RetryBlock v-if="failed" @retry="load" />

    <DishCardStack
      v-else
      :cards="cards"
      :loading="loading"
      :finished="finished"
      :submitting="submittingId"
      :want-locked="wantLocked"
      @swipe="onSwipe"
      @want="onWant"
    >
      <template #end>
        <DishFlowEnd :busy="surprising" @replay="onReplay" @surprise="onSurprise" />
      </template>
    </DishCardStack>
  </TabPage>
</template>

<style lang="scss" scoped>
/* 顶部信息区块（通栏矩形）：承载标题日期 + 筛选器，与下方卡片区域留白统一 */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  /* 与风物问候语 / 记忆标题块等高（--hero-box-height）；上下内距同风物问候语 --spacing-md */
  min-height: var(--hero-box-height);
  padding: var(--spacing-md) var(--page-gutter);
}

.topbar__left {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

/* 大标题：hero 档衬线 + 中黑字重（与风物页标题字重规范统一） */
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
</style>
