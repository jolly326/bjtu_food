<script setup lang="ts">
/**
 * 美味页 Z 轴堆叠卡片栈（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishCardStack）。
 *
 * **三张真实卡片**（不是画线）：每往下一层**缩小一点 + 坐标往下坠一点**，默认就露出叠层纸边。
 * - 上层（level 0）：完整卡，可水平拖拽；
 * - 中层（level 1）：`scale 0.96` + 下坠 `24rpx`；
 * - 下层（level 2）：`scale 0.92` + 下坠 `48rpx`。
 *   🔴 缩放围绕**底边**（`transform-origin: bottom center`）⇒ 底边只下坠、不被缩放上抬，
 *   所以下坠量就是**真实露出量**（每层 24rpx，落在圆角 28rpx 内侧 ⇒ 露出的是一段平坦卡边 + 圆角）。
 * - 层数 = 实际剩余卡数 ⇒ 有几张堆几张，划走一张少一层，没有下一张卡就没有纸边。
 * - Drag：仅上层响应**水平**拖拽（垂直滑动忽略），跟手 0.9 阻尼、旋转 ≤ ±3°；
 *   后方层随进度**上浮 + 放大**（offset→0、scale→1），预告即将补位。
 * - Release：位移 < 卡宽 22% ⇒ 回弹（--duration-base）；≥ 22% ⇒ 飞出（--duration-slow）并派发
 *   `swipe(id)`；此时中层由 key 复用**从「小且低」真实地放大上移成为上层**（弹出感），
 *   下层补位成中层，尾部补入新卡。
 * - 加载 / 空态：无卡片时上层渲染骨架卡 / 空态占位卡；流末尾 `finished` 时渲染 `end` 插槽。
 */
import { computed, onBeforeUnmount, ref } from 'vue'
import EmptyBlock from '@/components/base/EmptyBlock.vue'
import DishSwipeCard from './DishSwipeCard.vue'
import PaperCard from './PaperCard.vue'
import { windowWidth } from '@/utils/safeArea'

import type { DishCardOut } from '@/api'

/** 宿主节点虚拟化：组件根直接参与页面 flex 布局（宿主不打断 flex 链路，卡片栈才能撑满区块） */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    cards: DishCardOut[]
    /** 本批卡片已全部划完 ⇒ 渲染 end 插槽（流末尾空态） */
    finished?: boolean
    /** 正在提交的菜品 id（仅该卡按钮 loading；首屏加载由 PageLoading 承担） */
    submitting?: number | null
    /** 限频退避中：顶层卡的「加入记忆」不可点（docs/secur/S-03） */
    wantLocked?: boolean
  }>(),
  { finished: false, submitting: null, wantLocked: false },
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

/** 同槽可见层数：顶层 + 2 层纸边（🔴 后方层数须 = 本值 − 1，与 `GHOST_SCALE.length` 保持一致） */
const MAX_VISIBLE = 3

const stackSlots = computed<StackSlot[]>(() => {
  const slots: StackSlot[] = props.cards
    .slice(0, MAX_VISIBLE)
    .map((card, level) => ({ level, card, key: `card-${card.id}` }))
  // 无卡片时（加载 / 空态 / 结束态）仍需上层槽位承载内容
  if (!slots.length) slots.push({ level: 0, card: undefined, key: 'top-solo' })
  return slots
})

const winWidth = windowWidth()
/** 栈槽左右内距：与页面 gutter / 风物页海报卡同左基准线（与 CSS 的 `--page-gutter` 同步，改值须两处同改） */
const GUTTER_RPX = 32
const rpxToPx = winWidth / 750
const cardWidth = Math.max(1, winWidth - GUTTER_RPX * 2 * rpxToPx)
/** 翻页阈值：卡宽 22% */
const threshold = cardWidth * 0.22
/* ===== 手势参数 ===== */
/** 跟手阻尼：0.9 = 轻微纸片重量感；1 = 完全跟手 */
const DAMPING = 0.9
/** 手势死区（px）：小于此位移不判定为拖拽 */
const DEAD_ZONE = 8
/** 拖拽更新节流（ms，≈ 一帧）：减少频繁 setData 引起的抖动 */
const THROTTLE_MS = 16
/** 飞出时长（ms）—— 🔴 与 CSS `--duration-slow` **同值**，改值须两处同改（否则动画未走完就派发） */
const FLY_MS = 360
/** 飞出目标位移：屏宽的倍数（取 > 1 保证完全离屏） */
const FLY_OUT_FACTOR = 1.3

/* ===== 层叠视觉参数 ===== */
/** 每深一层的缩放（数组下标 = 后方层序，0 = 中层、1 = 下层；上层自身恒为 1，不在此列） */
const GHOST_SCALE = [0.96, 0.92]
/** 每深一层的下坠量（px），约 24rpx（落在圆角 28rpx 内侧 ⇒ 露出平坦卡边 + 圆角） */
const GHOST_LIFT = 24 * rpxToPx
/** 后方层跟随衰减：越深跟随越弱（中层全跟随、下层七成），保持层间差异 */
const FOLLOW_DECAY = 0.3

/** 旋转上限（°） */
const MAX_ROT = 3
/** 旋转饱和比例：位移达卡宽该比例时即达上限 */
const ROT_SATURATE = 0.3

/* ===== 手势状态 ===== */
const dragging = ref(false)
const animating = ref(false)
/** 已判定为垂直滑动 ⇒ 本次手势不再进入横向拖拽 */
const vertical = ref(false)
/** 顶层位移（px，已含阻尼） */
const dx = ref(0)
const startX = ref(0)
const startY = ref(0)

let lastMove = 0
let flyTimer: ReturnType<typeof setTimeout> | null = null

/** 是否允许划走：无卡片时不可 */
function canSwipe(): boolean {
  return props.cards.length > 0
}

/** 回弹归零（未达阈值时复位） */
function reset(): void {
  dragging.value = false
  vertical.value = false
  dx.value = 0
}

function onStart(e: TouchEvent): void {
  if (animating.value || !canSwipe()) return
  const t = e.touches[0]
  startX.value = t.clientX
  startY.value = t.clientY
  vertical.value = false
  dragging.value = false
  dx.value = 0
  lastMove = 0
}

function onMove(e: TouchEvent): void {
  if (animating.value || vertical.value || !canSwipe()) return
  const t = e.touches[0]
  const rawX = t.clientX - startX.value
  const rawY = t.clientY - startY.value
  if (!dragging.value) {
    // 垂直优先：垂直滑动不触发卡片拖拽
    if (Math.abs(rawY) > Math.abs(rawX) && Math.abs(rawY) > DEAD_ZONE) {
      vertical.value = true
      return
    }
    if (Math.abs(rawX) < DEAD_ZONE) return
    dragging.value = true
  }
  // 节流：每帧最多更新一次
  const now = Date.now()
  if (now - lastMove < THROTTLE_MS) return
  lastMove = now
  dx.value = rawX * DAMPING
}

function onEnd(e: TouchEvent): void {
  if (!dragging.value) {
    dragging.value = false
    return
  }
  // 结束帧补正：避免节流丢掉最后一段位移
  const t = e.changedTouches?.[0]
  if (t) dx.value = (t.clientX - startX.value) * DAMPING

  if (Math.abs(dx.value) >= threshold) {
    flyOut(dx.value > 0 ? 'right' : 'left')
    return
  }
  // 未达阈值 ⇒ 回弹（--duration-base）
  reset()
}

/**
 * 沿拖拽方向飞出屏幕，动画走完后派发 `swipe`。
 * 🔴 父组件收到 `swipe` 后才移除该卡，故飞行期间 `cards` 不变 —— 此刻 `cards[0]` 即本张被划走的卡，
 * 直接读其 id 即可，无需在飞出开始时另存快照。
 */
function flyOut(direction: 'left' | 'right'): void {
  if (animating.value || !canSwipe()) return
  animating.value = true
  dragging.value = false
  dx.value = direction === 'right' ? winWidth * FLY_OUT_FACTOR : -winWidth * FLY_OUT_FACTOR
  flyTimer = setTimeout(() => {
    flyTimer = null
    const id = props.cards[0]?.id
    if (id != null) emit('swipe', id)
    dx.value = 0
    animating.value = false
  }, FLY_MS)
}

// 卸载清理：不对已销毁组件继续 setState
onBeforeUnmount(() => {
  if (flyTimer == null) return
  clearTimeout(flyTimer)
  flyTimer = null
})

/** 拖拽进度（0→1）：后方层按它上浮放大 */
const progress = computed(() => Math.min(1, Math.abs(dx.value) / threshold))

/**
 * 旋转：**派生状态**（不手动赋值）—— 拖拽中按位移比例取 ±`MAX_ROT`°，飞出中取 ±8°，静止 0。
 * 静止 / 回弹后 `dx = 0` 且非拖拽 ⇒ 自然归零。
 */
const rot = computed(() => {
  if (animating.value) return dx.value > 0 ? 8 : -8
  if (!dragging.value) return 0
  // 符号天然跟随拖拽方向；位移达卡宽 ROT_SATURATE 时封顶 MAX_ROT
  const ratio = (dx.value / cardWidth / ROT_SATURATE) * MAX_ROT
  return Math.max(-MAX_ROT, Math.min(MAX_ROT, ratio))
})

/** 上层：跟手位移 + 轻微旋转 */
const topStyle = computed(() => ({
  transform: `translateX(${dx.value}px) rotate(${rot.value}deg)`,
  zIndex: 30,
}))

/**
 * 后方层样式：**按层深一次性算好**（level 1 = 中层），替代「每个节点每次渲染各算一遍」。
 * 拖拽进度下向「上层终态」靠拢（上浮 + 放大），预告即将补位。
 * 🔴 缩放围绕底边 ⇒ 下坠量即真实露出量。
 */
const backStyles = computed(() =>
  GHOST_SCALE.map((baseScale, i) => {
    const level = i + 1
    const ease = Math.max(0, 1 - i * FOLLOW_DECAY) * progress.value
    const scale = baseScale + (1 - baseScale) * ease
    const offset = GHOST_LIFT * level * (1 - ease)
    return {
      transform: `translateY(${offset}px) scale(${scale})`,
      zIndex: 30 - level,
    }
  }),
)
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
      :style="slot.level === 0 ? topStyle : backStyles[slot.level - 1]"
      @touchstart="slot.level === 0 ? onStart($event) : undefined"
      @touchmove.stop.prevent="slot.level === 0 ? onMove($event) : undefined"
      @touchend="slot.level === 0 ? onEnd($event) : undefined"
    >
      <view class="stack__cardWrap">
        <!-- 上层：流末尾 / 空态 / 真实卡（唯一可拖拽） -->
        <template v-if="slot.level === 0">
          <slot v-if="finished" name="end" />
          <PaperCard v-else-if="!slot.card">
            <EmptyBlock icon="dish" title="暂无菜品推荐" desc="等待食堂上新美食" />
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
        <!-- 中层 / 下层：**真实后续卡片**（自己的卡面与描边，只露下坠出来的那段卡边；
             阴影随层级递减 —— 中层 / 下层各传一档更弱阴影，实现上浮景深） -->
        <DishSwipeCard
          v-else-if="slot.card"
          :dish="slot.card"
          :paper-shadow="
            slot.level === 1 ? 'var(--shadow-card-back)' : 'var(--shadow-card-back-far)'
          "
        />
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

/* 卡片槽位：左右 --page-gutter(32rpx) —— 与风物页海报卡同左基准线；上 0（卡片顶贴内容区顶部）；
   下 72rpx 保留给纸边下探（延伸进页面让位留白），卡高 = 可用区 − 72rpx */
.stack__slot {
  position: absolute;
  top: 0;
  right: var(--page-gutter);
  bottom: calc(var(--spacing-2xl) + var(--spacing-md));
  left: var(--page-gutter);
  display: flex;
  flex-direction: column;
  will-change: transform;
  backface-visibility: hidden;
  /* 晋升（后层 → 上层）的过渡：略长一点，给出「弹出、放大上移」的过程感 */
  transition: transform var(--duration-slow) var(--ease-out);
}

/* 中层 / 下层：触摸穿透、不可点
   🔴 transform-origin: bottom —— 缩放围绕**底边**进行：底边只下坠、不被上抬，
   于是每层 24rpx（最深处 48rpx）的下坠量就是真实露出量 */
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
</style>
