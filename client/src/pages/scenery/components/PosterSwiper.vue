<script setup lang="ts">
/**
 * 海报卡片栈（规格见 docs/ui/client/风物.md §2 / §3）。
 * 同一槽位堆叠**至多 3 张**海报卡：顶层完整不透明、可水平拖拽；中 / 下层按层深下坠、半透明，
 * 纯装饰不可交互；划走顶层后下一层上浮接替，**划到末张即停**（不循环、不回跳）。
 * 为空 / 加载失败 ⇒ 渲染 3 张占位卡（同样按本栈堆叠，见 docs/ui/client/风物.md §4）。
 *
 * 槽位尺寸由宿主区块分配：本组件绝对定位铺满定位祖先（页面里的 `.posterArea__swiper` 已
 * `position: relative` 且由 `flex: 1` 取得高度）。
 * 拖拽阈值与层间下坠量在脚本内按屏宽换算（与 CSS 的 `--page-gutter` / `--spacing-*` 同步）。
 */
import { computed, ref, watch } from 'vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import { windowWidth } from '@/utils/safeArea'

import type { PosterOut } from '@/api'

/** 宿主节点虚拟化：组件根直接参与页面布局 */
defineOptions({ options: { virtualHost: true } })

/** 空 / 失败时铺开的占位卡张数（口径见 docs/ui/client/风物.md §2） */
const PLACEHOLDER_COUNT = 3
/** 同槽可见层数：顶层 + 至多 2 层下层 */
const VISIBLE_LEVELS = 3
/** 卡面底层淡装饰字符（衬线超大号、--text-decor 淡色、压在图片下层） */
const DECOR_CHAR = '食'

const props = defineProps<{
  posters: PosterOut[]
}>()

/** 有数据用真实海报；否则用等卡位的占位卡（负 id 只作 key，不与真实主键冲突） */
const items = computed<PosterOut[]>(() => {
  if (props.posters.length) return props.posters
  return Array.from({ length: PLACEHOLDER_COUNT }, (_, index) => ({
    id: -1 - index,
    imageUrl: '',
  }))
})

/** 当前顶层在 items 中的下标：划走一张 +1，末张保持展示 */
const index = ref(0)
watch(items, () => {
  index.value = 0
})

/** 堆叠槽位：level 即层深（0 = 顶层）；key 用海报 id ⇒ 层间晋升是同一节点的连续过渡 */
interface StackSlot {
  level: number
  poster: PosterOut
  key: string
}

const stackSlots = computed<StackSlot[]>(() =>
  items.value
    .slice(index.value, index.value + VISIBLE_LEVELS)
    .map((poster, level) => ({ level, poster, key: `poster-${poster.id}` })),
)

/** 顶层之后仍有卡 ⇒ 可划；末张停住 */
const canSwipe = computed(() => index.value < items.value.length - 1)

const winWidth = windowWidth()
const rpxToPx = winWidth / 750
/** 卡片左右各让出页面基准线（与 CSS --page-gutter 同值）⇒ 卡宽 = 屏宽 − 双 gutter */
const GUTTER_RPX = 32
const cardWidth = Math.max(1, winWidth - GUTTER_RPX * 2 * rpxToPx)
/** 翻页阈值：卡宽 22% */
const threshold = cardWidth * 0.22
/** 飞出时长：与样式里的 --duration-slow 同值（360ms），动画走完再让下一层晋升 */
const FLY_MS = 360
/** 每深一层的下坠量：12rpx（中 12 / 下 24，露出一段卡边） */
const GHOST_LIFT = 12 * rpxToPx
/** 下层不透明度（纯装饰层） */
const GHOST_OPACITY = 0.2

const dragging = ref(false)
const animating = ref(false)
const vertical = ref(false)
const dx = ref(0)
const startX = ref(0)
const startY = ref(0)
/** 拖拽更新节流（16ms ≈ 一帧），减少频繁 setData 引起的抖动 */
let lastMove = 0

/** 顶层：跟手位移（拖拽中关过渡；松手后回弹或飞出） */
const topStyle = computed(() => ({
  transform: `translateX(${dx.value}px)`,
  zIndex: 30,
}))

/** 中 / 下层：按层深下坠 + 半透明 ⇒ 下坠量即真实露出的一段卡边 */
function layerStyle(level: number) {
  return {
    transform: `translateY(${GHOST_LIFT * level}px)`,
    opacity: GHOST_OPACITY,
    zIndex: 30 - level,
  }
}

function onStart(e: TouchEvent) {
  if (animating.value || !canSwipe.value) return
  const t = e.touches[0]
  startX.value = t.clientX
  startY.value = t.clientY
  vertical.value = false
  dragging.value = false
  dx.value = 0
  lastMove = 0
}

function onMove(e: TouchEvent) {
  if (animating.value || vertical.value || !canSwipe.value) return
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
  dx.value = rawX
}

function onEnd(e: TouchEvent) {
  if (!dragging.value) {
    dragging.value = false
    return
  }
  // 结束帧补正：避免节流丢掉最后一段位移
  const t = e.changedTouches?.[0]
  if (t) dx.value = t.clientX - startX.value
  if (Math.abs(dx.value) >= threshold) {
    flyOut(dx.value > 0 ? 'right' : 'left')
    return
  }
  // 未达阈值 ⇒ 回弹（--duration-base）
  dragging.value = false
  vertical.value = false
  dx.value = 0
}

/** 沿拖拽方向飞出屏幕，动画走完后下一层晋升为顶层 */
function flyOut(direction: 'left' | 'right') {
  if (animating.value || !canSwipe.value) return
  animating.value = true
  dragging.value = false
  dx.value = direction === 'right' ? winWidth * 1.3 : -winWidth * 1.3
  setTimeout(() => {
    index.value += 1
    dx.value = 0
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
      @touchcancel="slot.level === 0 ? onEnd($event) : undefined"
    >
      <view class="stack__card">
        <text class="stack__decor">{{ DECOR_CHAR }}</text>
        <image
          v-if="slot.poster.imageUrl"
          class="stack__img"
          :src="slot.poster.imageUrl"
          mode="aspectFill"
          :lazy-load="true"
        >
          <template #error>
            <view class="stack__ph"><ImagePlaceholder :size="48" /></view>
          </template>
          <template #loading>
            <view class="stack__ph"><ImagePlaceholder :size="48" /></view>
          </template>
        </image>
        <view v-else class="stack__ph"><ImagePlaceholder :size="48" /></view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 绝对定位铺满定位祖先（页面的 .posterArea__swiper）—— 槽位高度由宿主区块分配 */
.stack {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
}

/* 卡片槽位：左右各留页面基准线（卡缘与「今日食单」同线）；
   底部让出最深一层的下坠量 ⇒ 叠层纸边不压 TabBar 呼吸位。
   晋升（下层 → 顶层）的过渡：位移 + 不透明度同步，给出「上浮接替」的过程感 */
.stack__slot {
  position: absolute;
  top: 0;
  right: var(--page-gutter);
  bottom: var(--spacing-md);
  left: var(--page-gutter);
  will-change: transform;
  backface-visibility: hidden;
  transition:
    transform var(--duration-base) var(--ease-out),
    opacity var(--duration-base) var(--ease-out);
}

/* 中 / 下层：半透明装饰层，触摸穿透、不可点 */
.stack__slot--back {
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

/* 海报卡：圆角 --radius-card-stack + 阴影 --shadow-card-stack（风物页专属卡形态）；
   内距 --spacing-lg ⇒ 图片在卡内居中、四周留白；卡内溢出裁掉（装饰字符可出血到卡外） */
.stack__card {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 100%;
  height: 100%;
  padding: var(--spacing-lg);
  overflow: hidden;
  background: var(--bg-placeholder);
  border-radius: var(--radius-card-stack);
  box-shadow: var(--shadow-card-stack);
}

/* 卡面底层淡装饰字符：左上角出血、衬线超大号、--text-decor，压在图片 / 占位下层且不响应交互 */
.stack__decor {
  position: absolute;
  top: calc(var(--spacing-2xl) * -1);
  left: calc(var(--spacing-lg) * -1);
  z-index: 0;
  font-family: var(--font-serif);
  font-size: calc(var(--font-hero) * 3);
  font-weight: var(--weight-regular);
  line-height: 1;
  color: var(--text-decor);
  pointer-events: none;
}

/* 海报图：卡内居中（留白由卡片内距承担）、圆角同卡片；层级在装饰字符之上 */
.stack__img {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  border-radius: var(--radius-card-stack);
}

/* 占位层：图未就绪 / 破图时卡内的居中占位，与图片同层级 */
.stack__ph {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
}
</style>
