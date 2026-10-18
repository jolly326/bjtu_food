<script setup lang="ts">
/**
 * 美味页 Z 轴堆叠卡片栈（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishCardStack）。
 *
 * 三层纸片堆叠（恒定 3 层视觉，对标 Julienne 卡片栈）：
 * - Idle：顶层完整展示（最宽）；第二 / 三层依次**收窄**（scale 0.96 / 0.92）并**向上错位**
 *   10 / 20rpx —— 从顶层卡上方露出层叠纸边；🔴 **不透明、无边缘虚化**，立体感仅由
 *   **宽度差异 + 上移位移 + 卡面柔和浅阴影**承担。
 * - Drag：仅顶层响应水平拖拽（垂直滑动忽略），跟手带轻微阻尼，旋转 ≤ ±3°；下层同步放大归位。
 * - Release：拖拽 < 卡宽 22% ⇒ 回弹（--duration-base）；≥ 22% ⇒ 沿方向飞出（--duration-slow），
 *   下一张晋升顶层；剩余两层向上小幅上移，尾部补入新卡（队列有货）或纸片虚影（恒定 3 层）。
 * - 加载 / 空态：顶层渲染骨架卡 / 空态占位卡；流末尾：`finished` 时渲染 `end` 插槽。
 */
import { computed, ref } from 'vue'
import DishCardPlaceholder from './DishCardPlaceholder.vue'
import DishSwipeCard from './DishSwipeCard.vue'
import PaperCard from './PaperCard.vue'
import { windowWidth } from '@/utils/safeArea'

import type { DishCardOut } from '@/api'

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

/** 堆叠槽位：顶层 + 2 层后方（恒定 3 层）；队列有货用真实卡，不足用纸片虚影补位 */
interface StackSlot {
  level: number
  card?: DishCardOut
  /** 卡片 id 作 key ⇒ 划走顶层后剩余卡的 DOM 节点被复用上移，堆叠动画连续 */
  key: string
}

const stackSlots = computed<StackSlot[]>(() => {
  const real = props.cards.slice(0, 3)
  return [0, 1, 2].map((level) => ({
    level,
    card: real[level],
    key: real[level] ? `card-${real[level].id}` : `ghost-${level}`,
  }))
})

const winWidth = windowWidth()
/** 栈槽左右各留一页 gutter（与 CSS `--page-gutter` 同步，改 token 须同步此值） */
const GUTTER_RPX = 32
const rpxToPx = winWidth / 750
const cardWidth = Math.max(1, winWidth - GUTTER_RPX * 2 * rpxToPx)
/** 翻页阈值：卡宽 22% */
const threshold = cardWidth * 0.22
/** 飞出时长：与样式里的 --duration-slow 同值（360ms），保证派发 swipe 时动画刚好走完 */
const FLY_MS = 360
/** 后方卡缩放：第二 / 三层依次收窄（顶层最宽）—— 宽度差异体现纸片堆叠 */
const GHOST_SCALE = [1, 0.96, 0.92]
/** 后方卡单层上移量：10rpx（层叠错落，从顶层卡上方露出纸边） */
const GHOST_LIFT = 10 * rpxToPx

const dragging = ref(false)
const animating = ref(false)
const vertical = ref(false)
const dx = ref(0)
const rot = ref(0)
const startX = ref(0)
const startY = ref(0)
/** 拖拽更新节流（16ms ≈ 一帧），减少频繁 setData 引起的抖动 */
let lastMove = 0

const progress = computed(() => Math.min(1, Math.abs(dx.value) / threshold))

const topStyle = computed(() => ({
  transform: `translateX(${dx.value}px) rotate(${rot.value}deg)`,
  zIndex: 20,
}))

/** 第 level 层（1 = 第二层）的纸片层样式：收窄 + 向上错位、**不透明无虚化**；
   拖拽进度下向顶层终态靠拢（放大回 1、上移归零），保证晋升连续 */
function ghostStyle(level: number) {
  const p = progress.value
  const baseScale = GHOST_SCALE[level]
  // 层级越深跟随越弱（第二层全跟随、第三层七成），保持层间差异
  const ease = Math.max(0, 1 - (level - 1) * 0.3) * p
  const scale = baseScale + (1 - baseScale) * ease
  const offset = -GHOST_LIFT * level * (1 - ease)
  return {
    transform: `translateY(${offset}px) scale(${scale})`,
    zIndex: 20 - level,
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
          ? ['stack__slot--top', { 'stack__slot--dragging': dragging, 'stack__slot--fly': animating }]
          : 'stack__slot--ghost'
      "
      :style="slot.level === 0 ? topStyle : ghostStyle(slot.level)"
      @touchstart="slot.level === 0 ? onStart($event) : undefined"
      @touchmove.stop.prevent="slot.level === 0 ? onMove($event) : undefined"
      @touchend="slot.level === 0 ? onEnd($event) : undefined"
    >
      <view class="stack__cardWrap">
        <!-- 顶层：骨架 / 流末尾 / 空态 / 真实卡（唯一可拖拽） -->
        <template v-if="slot.level === 0">
          <DishCardPlaceholder v-if="loading" />
          <slot v-else-if="finished" name="end" />
          <PaperCard v-else-if="!slot.card">
            <view class="stack__empty">
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
        <!-- 后方层：真实下一张卡（队列有货）或纸片虚影（补位，恒定 3 层） -->
        <DishSwipeCard v-else-if="slot.card" :dish="slot.card" />
        <view v-else class="stack__ghost" />
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

/* 卡片槽位：整页主容器 —— 上下留白均衡（三层堆叠在信息区块与 TabBar 容器间垂直居中） */
.stack__slot {
  position: absolute;
  top: var(--spacing-2xl);
  right: var(--page-gutter);
  bottom: var(--spacing-2xl);
  left: var(--page-gutter);
  display: flex;
  flex-direction: column;
  will-change: transform, opacity;
  backface-visibility: hidden;
  transition:
    transform var(--duration-base) var(--ease-out),
    opacity var(--duration-base) var(--ease-out);
}

.stack__slot--top {
  z-index: 20;
}

.stack__slot--ghost {
  pointer-events: none;
}

/* 拖拽中不跟过渡（直接跟手） */
.stack__slot--dragging {
  transition: none;
}

/* 飞出：--duration-slow */
.stack__slot--fly {
  transition:
    transform var(--duration-slow) var(--ease-out),
    opacity var(--duration-slow) var(--ease-out);
}

/* 卡片入场：轻微上浮淡入 */
.stack__cardWrap {
  display: flex;
  flex: 1;
  flex-direction: column;
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

/* 纸片虚影（队列尾部不足 3 张时的补位层）：同卡面轮廓 + 柔和浅阴影，无内容 */
.stack__ghost {
  display: flex;
  flex: 1;
  box-sizing: border-box;
  width: 100%;
  background: var(--bg-page);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card-stack);
}

/* 空态占位卡：与栈卡同卡面（表面由 PaperCard 承担），内容居中纵向排布 */
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
</style>
