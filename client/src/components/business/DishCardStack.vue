<script setup lang="ts">
/**
 * 美食页 Z 轴堆叠卡片栈（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishCardStack）。
 *
 * - Idle：顶层完整展示；第二层 scale 0.88 / opacity 0.45 / 轻微下偏移，更深层继续缩小降透明。
 * - Drag：仅顶层响应水平拖拽（垂直滑动忽略），跟手带轻微阻尼，旋转 ≤ ±3°；下层同步放大提亮。
 * - Release：拖拽 < 卡宽 22% ⇒ 回弹 260ms；≥ 22% ⇒ 沿方向飞出 320ms，下一张晋升顶层。
 * - 加载/空态：栈位渲染骨架卡 / 空态占位卡。
 * - 流末尾：`finished` 时在同一卡位渲染 `end` 插槽（页面提供「重新浏览 / 随机盲选」卡片）。
 */
import { computed, ref } from 'vue'
import DishCardPlaceholder from './DishCardPlaceholder.vue'
import DishSwipeCard from './DishSwipeCard.vue'
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
  }>(),
  { loading: false, finished: false, submitting: null },
)

const emit = defineEmits<{
  (e: 'swipe', id: number): void
  (e: 'want', card: DishCardOut): void
}>()

/** 顶层 + 3 层预览 */
const layers = computed(() => props.cards.slice(0, 4))

const winWidth = windowWidth()
/** 栈槽左右各留 48rpx（--spacing-2xl），换算为 px */
const rpxToPx = winWidth / 750
const cardWidth = Math.max(1, winWidth - 48 * 2 * rpxToPx)
/** 翻页阈值：卡宽 22% */
const threshold = cardWidth * 0.22

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

/** 第 level 层（1 = 第二层）的景深样式；拖拽进度下同步上浮 */
function ghostStyle(level: number) {
  const p = progress.value
  const baseScale = 0.88 - 0.05 * (level - 1)
  const baseOpacity = 0.45 - 0.15 * (level - 1)
  const baseOffset = 8 * level
  const lift = Math.max(0, 1 - (level - 1) * 0.3) * p
  const scale = baseScale + (1 - baseScale) * lift
  const opacity = baseOpacity + (1 - baseOpacity) * lift
  const offset = baseOffset * (1 - lift)
  return {
    transform: `scale(${scale}) translateY(${offset}px)`,
    opacity,
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
  // 回弹（260ms）
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
  }, 320)
}

</script>

<template>
  <view class="stack">
    <template v-if="loading">
      <view class="stack__slot stack__slot--top" :style="{ zIndex: 20 }">
        <view class="stack__cardWrap">
          <DishCardPlaceholder />
        </view>
      </view>
    </template>

    <template v-else-if="finished">
      <view class="stack__slot stack__slot--top" :style="{ zIndex: 20 }">
        <view class="stack__cardWrap">
          <slot name="end" />
        </view>
      </view>
    </template>

    <template v-else-if="!layers.length">
      <view class="stack__slot stack__slot--top" :style="{ zIndex: 20 }">
        <view class="stack__cardWrap">
          <view class="stack__empty">
            <text class="stack__emptyTitle">暂无菜品推荐</text>
            <text class="stack__emptyHint">等待食堂上新美食</text>
          </view>
        </view>
      </view>
    </template>

    <template v-else>
      <view
        v-for="(card, index) in layers"
        :key="card.id"
        class="stack__slot"
        :class="
          index === 0
            ? [
                'stack__slot--top',
                { 'stack__slot--dragging': dragging, 'stack__slot--fly': animating },
              ]
            : 'stack__slot--ghost'
        "
        :style="index === 0 ? topStyle : ghostStyle(index)"
        @touchstart="index === 0 ? onStart($event) : undefined"
        @touchmove.stop.prevent="index === 0 ? onMove($event) : undefined"
        @touchend="index === 0 ? onEnd($event) : undefined"
      >
        <view class="stack__cardWrap">
          <DishSwipeCard
            :dish="card"
            :submitting="index === 0 && submitting === card.id"
            :dragging="index === 0 && dragging"
            @want="index === 0 ? emit('want', card) : undefined"
          />
        </view>
      </view>
    </template>
  </view>
</template>

<style lang="scss" scoped>
.stack {
  position: relative;
  flex: 1;
  min-height: 0;
  width: 100%;
}

/* 卡片槽位：整页主容器 —— 上 48 / 下 40 / 左右 48，整体下移、底部与发现海报一致 */
.stack__slot {
  position: absolute;
  top: var(--spacing-2xl);
  right: var(--page-gutter);
  bottom: var(--spacing-xl);
  left: var(--page-gutter);
  display: flex;
  flex-direction: column;
  will-change: transform, opacity;
  backface-visibility: hidden;
  transition:
    transform 260ms var(--ease-out),
    opacity 260ms var(--ease-out);
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

/* 飞出 320ms */
.stack__slot--fly {
  transition:
    transform 320ms var(--ease-out),
    opacity 320ms var(--ease-out);
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

/* 空态占位卡：与栈卡同卡面 */
.stack__empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  box-sizing: border-box;
  width: 100%;
  background: var(--bg-card);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
}

.stack__emptyTitle {
  font-family: var(--font-serif);
  font-size: var(--font-h2);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
  text-align: center;
}

.stack__emptyHint {
  font-size: var(--font-small);
  color: var(--text-tertiary);
  text-align: center;
}


</style>
