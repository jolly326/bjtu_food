<script setup lang="ts">
/**
 * 菜品卡片**堆叠 + 拖拽翻卡**（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishCardStack）。
 *
 * - 顶层卡**跟手**：横向拖拽 ⇒ 平移 + 轻微旋转 + 缩放，模拟纸质卡片被拖动的惯性；
 * - 下方叠 **3 层下层卡**（真实后序内容）：缩小 + 降透明度 + 向下偏移露出下沿 → 层叠景深；
 *   拖拽时下层卡**逐步放大、提亮**，做好"上台"过渡；
 * - 松手：位移 ≥ 阈值（卡宽 28%）⇒ 滑出屏幕并派发 `swipe`（该卡本会话不再出现）；否则**回弹归位**；
 * - 左右两向**语义相同**（都是翻到下一张）。
 *
 * 性能约定：拖拽期间只改 `transform` / `opacity`（不触发布局），且图层静态量在 setup 期预计算，
 * 每帧仅做一次插值；滑出定时器在卸载时清理，避免悬挂回调。
 */
import { computed, onUnmounted, ref } from 'vue'
import DishSwipeCard from './DishSwipeCard.vue'

type StackCard = {
  id: number
  name: string
  price: number
  imageUrl?: string | null
  avgReaction?: number | null
  wanted: boolean
  canteenName: string
  stallName: string
}

const props = withDefaults(defineProps<{ cards: StackCard[]; submitting?: boolean }>(), {
  submitting: false,
})

const emit = defineEmits<{ (e: 'swipe', id: number): void; (e: 'want', card: StackCard): void }>()

/** 下层卡层数（景深） */
const GHOST_COUNT = 3
/** 滑出阈值：卡宽的 28% */
const SWIPE_RATIO = 0.28
/** 最大旋转角（度） */
const MAX_ROTATE = 8
/** 下层卡每层向下露出的边缘（16rpx ≈ 8px） */
const GHOST_STEP_PX = 8
/** 滑出动画时长（ms）—— 与 CSS `--duration-base` 一致 */
const FLY_OUT_MS = 200

/** 窗宽（px）：优先新 API，旧基础库回退（同 safeArea.ts 的取值方式） */
function readWindowWidth(): number {
  try {
    return uni.getWindowInfo().windowWidth ?? 375
  } catch {
    return uni.getSystemInfoSync().windowWidth ?? 375
  }
}

/** 卡宽（px）= 窗宽 − 2 × --page-gutter(32rpx ≈ 16px) */
const cardWidth = Math.max(1, readWindowWidth() - 32)
/** 滑出阈值（px）：预计算，避免每帧重复相乘 */
const threshold = cardWidth * SWIPE_RATIO

/**
 * 下层卡图层静态量（**setup 期一次性计算**）：由远及近排列，保证绘制顺序（远的先画）。
 * `level` = 1 最近 … GHOST_COUNT 最远。
 */
const LAYERS = Array.from({ length: GHOST_COUNT }, (_, k) => {
  const level = GHOST_COUNT - k
  return {
    level,
    base: 1 - level * 0.05, // 静止时的缩放
    alpha: 1 - level * 0.3, // 静止时的透明度
    offset: level * GHOST_STEP_PX, // 向下露出量（px）
  }
})

const startX = ref(0)
const dx = ref(0)
const dragging = ref(false)
/** 滑出方向：0 无 / 1 右 / -1 左 */
const flyOut = ref(0)
/** 滑出定时器句柄（卸载时清理） */
let flyOutTimer: ReturnType<typeof setTimeout> | null = null

const top = computed(() => props.cards[0])
/** 进度 0→1：达到阈值时下层卡正好补位到 1 */
const progress = computed(() => Math.min(1, Math.abs(dx.value) / threshold))

/** 当前真实存在的下层卡（不足时自然减少 ⇒ 不渲染空壳节点） */
const ghosts = computed(() =>
  LAYERS.map((layer) => ({ ...layer, card: props.cards[layer.level] })).filter((g) => g.card),
)

const topStyle = computed(() => {
  if (flyOut.value !== 0) {
    return {
      transform: `translateX(${flyOut.value * 130}%) rotate(${flyOut.value * MAX_ROTATE}deg)`,
      opacity: 0,
    }
  }
  const rotate = (dx.value / cardWidth) * MAX_ROTATE
  const scale = 1 + progress.value * 0.02
  return { transform: `translateX(${dx.value}px) rotate(${rotate}deg) scale(${scale})` }
})

/** 下层卡：拖拽时按进度放大 + 提亮（仅插值，静态量来自 LAYERS） */
function ghostStyle(layer: { base: number; alpha: number; offset: number }) {
  const scale = layer.base + progress.value * (1 - layer.base)
  return {
    transform: `scale(${scale}) translateY(${layer.offset}px)`,
    opacity: layer.alpha + progress.value * (1 - layer.alpha),
  }
}

function onStart(e: TouchEvent) {
  if (flyOut.value !== 0) return
  dragging.value = true
  startX.value = e.touches[0].clientX
}

function onMove(e: TouchEvent) {
  if (!dragging.value) return
  dx.value = e.touches[0].clientX - startX.value
}

function onEnd() {
  if (!dragging.value) return
  dragging.value = false
  if (Math.abs(dx.value) < threshold) {
    dx.value = 0 // 回弹：transition 生效
    return
  }
  flyOut.value = dx.value > 0 ? 1 : -1
  const gone = top.value
  if (flyOutTimer) clearTimeout(flyOutTimer)
  flyOutTimer = setTimeout(() => {
    flyOutTimer = null
    flyOut.value = 0
    dx.value = 0
    emit('swipe', gone.id)
  }, FLY_OUT_MS)
}

onUnmounted(() => {
  if (flyOutTimer) {
    clearTimeout(flyOutTimer)
    flyOutTimer = null
  }
})
</script>

<template>
  <view class="stack">
    <!-- 下层卡（由远及近绘制）：只做景深，不接受点击 -->
    <view
      v-for="layer in ghosts"
      :key="'ghost-' + layer.level"
      class="stack__ghost"
      :class="{ 'is-dragging': dragging }"
      :style="ghostStyle(layer)"
    >
      <DishSwipeCard :dish="layer.card" :index="layer.level + 1" />
    </view>

    <!-- 顶层卡：跟手拖拽 -->
    <view
      v-if="top"
      class="stack__top"
      :class="{ 'is-dragging': dragging }"
      :style="topStyle"
      @touchstart="onStart"
      @touchmove="onMove"
      @touchend="onEnd"
      @touchcancel="onEnd"
    >
      <DishSwipeCard :dish="top" :submitting="submitting" :index="1" @want="emit('want', top)" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.stack {
  position: relative;
  flex: 1;
  min-height: 0;
  padding: 0 var(--page-gutter) var(--spacing-sm);
}

.stack__ghost,
.stack__top {
  position: absolute;
  top: 0;
  right: 0;
  bottom: var(--spacing-sm);
  left: 0;
  transition:
    transform var(--duration-base) var(--ease-out),
    opacity var(--duration-base) var(--ease-out);
}

/* 下层卡只做景深，不抢焦点、不接收点击 */
.stack__ghost {
  pointer-events: none;
  transform-origin: center bottom;
}

.stack__top {
  z-index: 1;
}

/* 拖拽中去掉过渡，保证跟手 */
.is-dragging {
  transition: none;
}
</style>
