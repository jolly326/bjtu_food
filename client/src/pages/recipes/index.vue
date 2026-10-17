<script setup lang="ts">
/**
 * 菜谱 tab（由「美食」重构更名；设计稿 docs/ui/client/推荐菜品.md；功能规则 docs/func/client/A2、A3、B1）。
 * 扑克式**堆叠卡片**：左右拖拽翻卡（划走的卡片本会话不再出现），右上角食堂筛选，卡片下方「去品尝」。
 */
import { computed, onMounted, ref } from 'vue'
import EmptyState from '@/components/base/EmptyState.vue'
import TabBar from '@/components/base/TabBar.vue'
import CanteenFilter from '@/components/business/CanteenFilter.vue'
import DishCardPlaceholder from '@/components/business/DishCardPlaceholder.vue'
import DishCardStack from '@/components/business/DishCardStack.vue'
import { canteenApi, dishApi, recordApi } from '@/api'
import { navMetrics } from '@/utils/safeArea'

type Card = {
  id: number
  name: string
  price: number
  imageUrl?: string | null
  avgReaction?: number | null
  wanted: boolean
  canteenName: string
  stallName: string
}

const cards = ref<Card[]>([])
const canteens = ref<{ id: number; name: string }[]>([])
const canteenId = ref<number | undefined>(undefined)
const swiped = ref<Set<number>>(new Set())
const submitting = ref(false)
const failed = ref(false)
const loading = ref(true)
/** 顶部安全区：避开状态栏 / 刘海与右上角胶囊 */
const topInset = navMetrics().topInset
/** 页头日期：固定值，避免每次渲染重复格式化 */
const today = new Date().toLocaleDateString('zh-CN')

/** 本会话未划走、未耗尽的卡片（划走的卡片不再出现） */
const visible = computed(() => cards.value.filter((c) => !swiped.value.has(c.id)))

async function load() {
  loading.value = true
  failed.value = false
  try {
    const [dishList, canteenList] = await Promise.all([
      dishApi.list({ canteenId: canteenId.value, pageSize: 20 }),
      canteenApi.list(),
    ])
    cards.value = dishList.records
    swiped.value = new Set()
    canteens.value = canteenList
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

/** 本会话内「去品尝」返回的记录 id（用于取消）—— 服务端只给 `wanted` 布尔，不带 id */
const recordIdByDish = ref<Map<number, number>>(new Map())

/** 优先用本会话缓存的记录 id；否则回查清单按 dishId 找（记录量级小） */
async function findRecordId(dishId: number): Promise<number | null> {
  const cached = recordIdByDish.value.get(dishId)
  if (cached != null) return cached
  const list = await recordApi.list()
  return list.find((r) => r.dishId === dishId)?.id ?? null
}

/** 「去品尝 / 取消品尝」切换：未品尝 ⇒ POST；已品尝 ⇒ DELETE（仅未评分可撤销） */
async function onWant(dish: Card) {
  if (submitting.value) return
  submitting.value = true
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
  } catch (error) {
    // 含边界：已评分的记录不可撤销（后端 400），此时保持已品尝态
    uni.showToast({ title: (error as Error).message || '操作失败，请重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

/** 卡片被拖拽滑出 ⇒ 本会话不再出现，下一张上浮补位 */
function onSwipe(id: number) {
  swiped.value.add(id)
}

function onFilterChange(id?: number) {
  canteenId.value = id
  load()
}

/** 空态动作（见 docs/ui/client/推荐菜品.md §4）：换一批 = 重新请求重掷序列；重看 = 重置本会话划走记录 */
const emptyActions = [
  { key: 'reshuffle', icon: 'refresh-cw', label: '换一批' },
  { key: 'replay', icon: 'eye', label: '重看' },
]

function onEmptyAction(key: string) {
  if (key === 'reshuffle') {
    load()
    return
  }
  swiped.value = new Set()
}

onMounted(load)
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <view class="topbar">
      <view class="topbar__left">
        <text class="topbar__title">菜谱</text>
        <text class="topbar__date">{{ today }}</text>
      </view>
      <CanteenFilter :options="canteens" :model-value="canteenId" @pick="onFilterChange" />
    </view>

    <DishCardPlaceholder v-if="loading || failed" />

    <DishCardStack
      v-else-if="visible.length"
      :cards="visible"
      :submitting="submitting"
      @swipe="onSwipe"
      @want="onWant"
    />

    <EmptyState
      v-else-if="!loading"
      title="今天就这些了"
      subtitle="明天再来看看吧"
      :actions="emptyActions"
      @action="onEmptyAction"
    />

    <TabBar active="recipes" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: 100vh;
  overflow: hidden;
  padding-bottom: calc(
    var(--tabbar-height) + var(--tabbar-gap) + var(--spacing-md) + env(safe-area-inset-bottom)
  );
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md) var(--page-gutter);
}

.topbar__left {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2xs);
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
</style>
