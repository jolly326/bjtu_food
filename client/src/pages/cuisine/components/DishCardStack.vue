<script setup lang="ts">
/**
 * 美味页 Z 轴堆叠卡片栈（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishCardStack）。
 *
 * **三张真实卡片**（不是画线）：每往下一层**缩小一点 + 坐标往下坠一点**，默认就露出叠层纸边。
 * - 上层（level 0）：完整卡，可水平拖拽；
 * - 中层（level 1）：`scale 0.96` + 下坠 `32rpx`；
 * - 下层（level 2）：`scale 0.92` + 下坠 `64rpx`。
 *   🔴 缩放围绕**底边**（`transform-origin: bottom center`）⇒ 底边只下坠、不被缩放上抬，
 *   所以下坠量就是**真实露出量**（32rpx ≈ 圆角 28rpx 的 1.1 倍，露出的是一段平坦卡边 + 圆角）。
 * - 层数 = 实际剩余卡数 ⇒ 有几张堆几张，划走一张少一层，没有下一张卡就没有纸边。
 * - Drag：仅上层响应**水平**拖拽（垂直滑动忽略），跟手 0.9 阻尼、旋转 ≤ ±3°；
 *   后方层随进度**上浮 + 放大**（offset→0、scale→1），预告即将补位。
 * - Release：位移 < 卡宽 22% ⇒ 回弹（--duration-base）；≥ 22% ⇒ 飞出（--duration-slow）并派发
 *   `swipe(id)`；此时中层由 key 复用**从「小且低」真实地放大上移成为上层**（弹出感），
 *   下层补位成中层，尾部补入新卡。
 * - 加载 / 空态：无卡片时上层渲染骨架卡 / 空态占位卡；流末尾 `finished` 时渲染 `end` 插槽。
 */
import { computed, ref } from 'vue'
import AppIcon from '@/components/base/AppIcon.vue'
import DishCardPlaceholder from './DishCardPlaceholder.vue'
import DishSwipeCard from './DishSwipeCard.vue'
import PaperCard from './PaperCard.vue'
import { windowWidth } from '@/utils/safeArea'

import type { DishCardOut } from '@/api'

/** 宿主节点虚拟化：组件根直接参与页面 flex 布局（宿主不打断 flex 链路，卡片栈才能撑满区块） */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    cards: DishCardOut[]
    loading?: boolean
    /** 本批卡片已全部划完 ⇒ 渲染 end 插槽（流末尾空态） */
    finished?: boolean
    /** 正在提交的菜品 id（仅该卡按钮 loading） */
    submitting?: number | null
    /** 限频退避中：顶层卡的「去品尝」不可点（docs/secur/S-03） */
    wantLocked?: boolean
  }>(),
  { loading: false, finished: false, submitting: null, wantLocked: false },
)

const emit = defineEmits<{
  (e: 'swipe', id: number): void
  (e: 'want', card: DishCardOut): void
}>()

/** 堆叠槽位：上层 + 至多 2 层真实后续卡（层数 = 实际剩余卡数） */
interface StackSlot {
  level: number
  card?: DishCardOut
  /** 卡片 id 作 key ⇒ 划走上层后剩余卡的 DOM 被复用上移放大，晋升过程连续 */
  key: string
}

const stackSlots = computed<StackSlot[]>(() => {
  const slots: StackSlot[] = props.cards
    .slice(0, 3)
    .map((card, level) => ({ level, card, key: `card-${card.id}` }))
  // 无卡片时（加载 / 空态 / 结束态）仍需上层槽位承载内容
  if (!slots.length) slots.push({ level: 0, card: undefined, key: 'top-solo' })
  return slots
})

const winWidth = windowWidth()
/** 栈槽左右内距：比页面 gutter 更宽，把卡片**收窄**（与 CSS 的 `--spacing-2xl` 同步，改值须两处同改） */
const GUTTER_RPX = 48
const rpxToPx = winWidth / 750
const cardWidth = Math.max(1, winWidth - GUTTER_RPX * 2 * rpxToPx)
/** 翻页阈值：卡宽 22% */
const threshold = cardWidth * 0.22
/** 飞出时长：与样式里的 --duration-slow 同值（360ms），保证派发 swipe 时动画刚好走完 */
const FLY_MS = 360
/** 每深一层的缩放：中 4% / 下 8%（往后越来越小） */
const GHOST_SCALE = [1, 0.96, 0.92]
/** 每深一层的下坠量：32rpx（≈ 卡片圆角 28rpx 的 1.1 倍 ⇒ 露出的是**一段平坦卡边 + 圆角**，层次一眼可辨） */
const GHOST_LIFT = 32 * rpxToPx

const dragging = ref(false)
const animating = ref(false)
const vertical = ref(false)
const dx = ref(0)
const rot = ref(0)
const startX = ref(0)
const startY = ref(0)
/** 拖拽更新节流（16ms ≈ 一帧），减少频繁 setData 引起的抖动 */
let lastMove = 0

/** 拖拽进度（0→1）：后方层按它上浮放大 */
const progress = computed(() => Math.min(1, Math.abs(dx.value) / threshold))

/** 上层：跟手位移 + 轻微旋转 */
const topStyle = computed(() => ({
  transform: `translateX(${dx.value}px) rotate(${rot.value}deg)`,
  zIndex: 30,
}))

/** 后方层（level 1 = 中层）：缩小 + 下坠；拖拽时向「上层终态」靠拢（上浮 + 放大）。
 *  🔴 缩放围绕底边 ⇒ 下坠量即真实露出量 */
function layerStyle(level: number) {
  const p = progress.value
  const baseScale = GHOST_SCALE[level]
  // 层级越深跟随越弱（中层全跟随、下层七成），保持层间差异
  const ease = Math.max(0, 1 - (level - 1) * 0.3) * p
  const scale = baseScale + (1 - baseScale) * ease
  const offset = GHOST_LIFT * level * (1 - ease)
  return {
    transform: `translateY(${offset}px) scale(${scale})`,
    zIndex: 30 - level,
  }
}

function onStart(e: TouchEvent) {
  if (animating.value || !props.cards.length) return
  const t = e.touches[0]
  startX.value = t.clientX
  startY.value = t.clientY
  vertical.value = false
  dragging.value = false
  dx.value = 0
  rot.value = 0
  lastMove = 0
}

function onMove(e: TouchEvent) {
  if (animating.value || vertical.value || !props.cards.length) return
  const t = e.touches[0]
  const rawX = t.clientX - startX.value
  const rawY = t.clientY - startY.value
  if (!dragging.value) {
    // 垂直优先：垂直滑动不触发卡片拖拽
    if (Math.abs(rawY) > Math.abs(rawX) && Math.abs(rawY) > 8) {
      vertical.value = true
      return
    }
    if (Math.abs(rawX) < 8) return
    dragging.value = true
  }
  // 节流：每帧最多更新一次
  const now = Date.now()
  if (now - lastMove < 16) return
  lastMove = now
  // 轻微阻尼（纸片重量感）
  const damped = rawX * 0.9
  dx.value = damped
  rot.value = Math.max(-3, Math.min(3, (damped / cardWidth) * 10))
}

function onEnd(e: TouchEvent) {
  if (!dragging.value) {
    dragging.value = false
    return
  }
  // 结束帧补正：避免节流丢掉最后一段位移
  const t = e.changedTouches?.[0]
  if (t) {
    const damped = (t.clientX - startX.value) * 0.9
    dx.value = damped
    rot.value = Math.max(-3, Math.min(3, (damped / cardWidth) * 10))
  }
  if (Math.abs(dx.value) >= threshold) {
    flyOut(dx.value > 0 ? 'right' : 'left')
    return
  }
  // 回弹（--duration-base）
  dragging.value = false
  vertical.value = false
  dx.value = 0
  rot.value = 0
}

/** 沿拖拽方向飞出，并小幅增加旋转 */
function flyOut(direction: 'left' | 'right') {
  if (animating.value || !props.cards.length) return
  const target = direction === 'right' ? winWidth * 1.3 : -winWidth * 1.3
  animating.value = true
  dragging.value = false
  dx.value = target
  rot.value = direction === 'right' ? 8 : -8
  const id = props.cards[0]?.id
  setTimeout(() => {
    if (id != null) emit('swipe', id)
    dx.value = 0
    rot.value = 0
    animating.value = false
  }, FLY_MS)
}
</script>

<template>
  <view class="stack">
    <view
      v-for="slot in stackSlots"
      :key="slot.key"
      class="stack__slot"
      :class="
        slot.level === 0
          ? { 'stack__slot--dragging': dragging, 'stack__slot--fly': animating }
          : 'stack__slot--back'
      "
      :style="slot.level === 0 ? topStyle : layerStyle(slot.level)"
      @touchstart="slot.level === 0 ? onStart($event) : undefined"
      @touchmove.stop.prevent="slot.level === 0 ? onMove($event) : undefined"
      @touchend="slot.level === 0 ? onEnd($event) : undefined"
    >
      <view class="stack__cardWrap">
        <!-- 上层：骨架 / 流末尾 / 空态 / 真实卡（唯一可拖拽） -->
        <template v-if="slot.level === 0">
          <DishCardPlaceholder v-if="loading" />
          <slot v-else-if="finished" name="end" />
          <PaperCard v-else-if="!slot.card">
            <view class="stack__empty">
              <AppIcon class="stack__emptyIcon" name="dish" :size="64" tone="muted" />
              <text class="stack__emptyTitle">暂无菜品推荐</text>
              <text class="stack__emptyHint">等待食堂上新美食</text>
            </view>
          </PaperCard>
          <DishSwipeCard
            v-else
            :dish="slot.card"
            :submitting="submitting === slot.card.id"
            :dragging="dragging"
            :locked="wantLocked"
            @want="emit('want', slot.card)"
          />
        </template>
        <!-- 中层 / 下层：**真实后续卡片**（自己的卡面与描边，只露下坠出来的那段卡边） -->
        <DishSwipeCard v-else-if="slot.card" :dish="slot.card" />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.stack {
  position: relative;
  flex: 1;
  min-height: 0;
  width: 100%;
}

/* 卡片槽位：上 64 / 下 72rpx —— 整组继续上移（上减下加同值 ⇒ 卡高不变 = 可用区 − 136rpx）；
   纸边（最深下探 64rpx）延伸进页面让位留白 */
.stack__slot {
  position: absolute;
  top: calc(var(--spacing-2xl) + var(--spacing-sm));
  right: var(--spacing-2xl);
  bottom: calc(var(--spacing-2xl) + var(--spacing-md));
  left: var(--spacing-2xl);
  display: flex;
  flex-direction: column;
  will-change: transform;
  backface-visibility: hidden;
  /* 晋升（后层 → 上层）的过渡：略长一点，给出「弹出、放大上移」的过程感 */
  transition: transform var(--duration-slow) var(--ease-out);
}

/* 中层 / 下层：触摸穿透、不可点
   🔴 transform-origin: bottom —— 缩放围绕**底边**进行：底边只下坠、不被上抬，
   于是 64 / 128rpx 的下坠量就是真实露出量 */
.stack__slot--back {
  transform-origin: bottom center;
  pointer-events: none;
}

/* 拖拽中不跟过渡（直接跟手） */
.stack__slot--dragging {
  transition: none;
}

/* 飞出：--duration-slow */
.stack__slot--fly {
  transition: transform var(--duration-slow) var(--ease-out);
}

/* 卡片容器：卡片撑满槽位
   🔴 min-height: 0 —— 否则 flex 子项不会低于内容高度，卡片会撑出槽位、盖住下层卡边 */
.stack__cardWrap {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  width: 100%;
  will-change: transform;
  backface-visibility: hidden;
  animation: card-in var(--duration-slow) var(--ease-out);
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(var(--spacing-md));
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 空态占位卡：与栈卡同卡面（表面由 PaperCard 承担），内容居中纵向排布；
   语言与记忆页空态一致 —— 细线图标（弱化）+ 衬线标题 + 浅灰引导 */
.stack__empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  box-sizing: border-box;
  width: 100%;
}

.stack__emptyIcon {
  margin-bottom: var(--spacing-xs);
  opacity: 0.55;
}
</style>
