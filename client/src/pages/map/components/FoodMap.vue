<script setup lang="ts">
/**
 * 美食地图画布（设计稿 docs/ui/client/美食地图.md；功能规则 docs/func/client/A1-美食地图.md）。
 *
 * - 2D 内容包围盒：磁贴按洗牌后的顺序落在**错位方格**上（固定网格 + 每载入随机分配，无成簇 / 权重）；
 * - 单指拖拽平移（松手惯性），双指捏合缩放（以双指中点为锚），越界弹性收敛；
 * - 点击判定：位移与时长都在阈值内才命中磁贴 —— 命中通过 `pick` 交给页面唤起弹窗。
 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

import type { DishVO } from '@/api'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import { thumbUrl } from '@/utils/image'
import {
  buildGridIndex,
  buildStaggeredLayout,
  cullTiles,
  hitTestGrid,
  type GridIndex,
  type TileBox,
} from '@/utils/mapLayout'
import { refreshWindowSize, windowSize } from '@/utils/viewport'

/** 磁贴边长（rpx）、网格步长（rpx）—— 放大磁贴、收紧间距，视觉更饱满 */
const TILE_RPX = 240
const CELL_RPX = 300
/** 缩放范围 */
const MIN_SCALE = 0.55
const MAX_SCALE = 3
/** 点击判定阈值 */
const TAP_MOVE_PX = 8
const TAP_DURATION_MS = 320
/** 越界橡皮筋系数（越小越硬） */
const RUBBER = 0.35
/** 惯性衰减 */
const FRICTION = 0.92
const MIN_SPEED = 0.12

interface Tile extends TileBox {
  dish: DishVO
}

const props = defineProps<{ dishes: DishVO[] }>()
const emit = defineEmits<{ (e: 'pick', dish: DishVO): void }>()

const viewport = reactive(windowSize())

const worldW = ref(0)
const worldH = ref(0)
const tx = ref(0)
const ty = ref(0)
const scale = ref(1)
const tiles = ref<Tile[]>([])
const failedIds = reactive(new Set<number>())

/** 网格索引：按中心点把磁贴落格，点击命中从 O(N) 降到常数级 */
let gridIndex: GridIndex<Tile> | null = null

/** 视口裁剪：仅渲染「视口外扩半屏」内的磁贴，DOM 数量与可视区挂钩 */
const visibleTiles = computed<Tile[]>(() =>
  cullTiles(tiles.value, {
    width: viewport.width,
    height: viewport.height,
    tx: tx.value,
    ty: ty.value,
    scale: scale.value,
  }),
)

const worldStyle = computed(() => ({
  width: `${worldW.value}px`,
  height: `${worldH.value}px`,
  transform: `translate3d(${tx.value}px, ${ty.value}px, 0) scale(${scale.value})`,
}))

function tileStyle(tile: Tile) {
  return {
    width: `${tile.size}px`,
    height: `${tile.size}px`,
    left: `${tile.x}px`,
    top: `${tile.y}px`,
  }
}

/** rpx → px（以窗口宽为基准） */
function rpx(value: number): number {
  return (value * viewport.width) / 750
}

function shuffled<T>(list: readonly T[]): T[] {
  const out = [...list]
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    const tmp = out[i]
    out[i] = out[j]
    out[j] = tmp
  }
  return out
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

function bounds() {
  const w = worldW.value * scale.value
  const h = worldH.value * scale.value
  const minX = w <= viewport.width ? (viewport.width - w) / 2 : viewport.width - w
  const maxX = w <= viewport.width ? (viewport.width - w) / 2 : 0
  const minY = h <= viewport.height ? (viewport.height - h) / 2 : viewport.height - h
  const maxY = h <= viewport.height ? (viewport.height - h) / 2 : 0
  return { minX, maxX, minY, maxY }
}

/**
 * 重建布局：**错位方格密铺**。
 *
 * - 固定网格结构（行优先），奇偶行水平错开半格形成砖墙式密铺；
 * - 每次载入把菜品随机映射到固定格子（`shuffled`）—— 结构可预测、落点每次新鲜；
 * - 首屏居中于世界几何中心，进入即见地图中心区。
 */
function rebuild(): void {
  stopAnimation()
  const list = shuffled(props.dishes)
  failedIds.clear()
  if (!list.length) {
    tiles.value = []
    gridIndex = null
    worldW.value = 0
    worldH.value = 0
    return
  }

  const tile = rpx(TILE_RPX)
  const cell = rpx(CELL_RPX)
  const layout = buildStaggeredLayout(list.length, tile, cell)
  worldW.value = layout.worldW
  worldH.value = layout.worldH

  tiles.value = list.map((dish, index) => ({
    dish,
    ...layout.boxes[index]!,
  }))
  gridIndex = buildGridIndex(tiles.value, cell)

  scale.value = 1
  // 首屏落在世界几何中心，并显式收敛到合法边界（极端尺寸/窗口变化下也不越界）
  const b = bounds()
  tx.value = clamp((viewport.width - worldW.value) / 2, b.minX, b.maxX)
  ty.value = clamp((viewport.height - worldH.value) / 2, b.minY, b.maxY)
}

watch(() => props.dishes, rebuild, { immediate: true })

// ---------------- 手势 ----------------
type Mode = 'none' | 'pan' | 'pinch'

let mode: Mode = 'none'
let startX = 0
let startY = 0
let startTx = 0
let startTy = 0
let startAt = 0
let moved = false
let pinchDist = 0
let pinchScale = 1
let anchorWorldX = 0
let anchorWorldY = 0
let lastX = 0
let lastY = 0
let lastAt = 0
let speedX = 0
let speedY = 0
/** 帧调度：有 rAF 用 rAF（H5），小程序无全局 rAF 时回退 setTimeout */
type FrameHandle = number | ReturnType<typeof setTimeout>
let frame: FrameHandle | null = null
const requestFrame = (cb: () => void): FrameHandle =>
  typeof requestAnimationFrame === 'function' ? requestAnimationFrame(cb) : setTimeout(cb, 16)
const cancelFrame = (handle: FrameHandle): void => {
  if (typeof requestAnimationFrame === 'function') cancelAnimationFrame(handle as number)
  else clearTimeout(handle as ReturnType<typeof setTimeout>)
}

const reduceMotion = (() => {
  try {
    const host = globalThis as unknown as {
      wx?: { getSystemSetting?: () => { reduceMotion?: boolean } }
    }
    return host.wx?.getSystemSetting?.().reduceMotion === true
  } catch {
    return false
  }
})()

function now(): number {
  return Date.now()
}

function stopAnimation(): void {
  if (frame != null) {
    cancelFrame(frame)
    frame = null
  }
}

function rubber(nx: number, ny: number): void {
  const b = bounds()
  let x = nx
  let y = ny
  if (nx < b.minX) x = b.minX - (b.minX - nx) * RUBBER
  else if (nx > b.maxX) x = b.maxX + (nx - b.maxX) * RUBBER
  if (ny < b.minY) y = b.minY - (b.minY - ny) * RUBBER
  else if (ny > b.maxY) y = b.maxY + (ny - b.maxY) * RUBBER
  tx.value = x
  ty.value = y
}

/** 弹性收敛到合法边界（duration = 0 即时） */
function settle(duration: number): void {
  stopAnimation()
  if (reduceMotion) duration = 0
  const b = bounds()
  const targetX = clamp(tx.value, b.minX, b.maxX)
  const targetY = clamp(ty.value, b.minY, b.maxY)
  const fromX = tx.value
  const fromY = ty.value
  if (duration <= 0 || (fromX === targetX && fromY === targetY)) {
    tx.value = targetX
    ty.value = targetY
    return
  }
  const startedAt = now()
  const tick = () => {
    const progress = clamp((now() - startedAt) / duration, 0, 1)
    const eased = 1 - (1 - progress) ** 3
    tx.value = fromX + (targetX - fromX) * eased
    ty.value = fromY + (targetY - fromY) * eased
    frame = progress < 1 ? requestFrame(tick) : null
  }
  tick()
}

function inertia(): void {
  if (reduceMotion) {
    settle(0)
    return
  }
  let vx = speedX
  let vy = speedY
  if (Math.abs(vx) < MIN_SPEED && Math.abs(vy) < MIN_SPEED) {
    settle(220)
    return
  }
  stopAnimation()
  const step = () => {
    vx *= FRICTION
    vy *= FRICTION
    const b = bounds()
    const nx = tx.value + vx
    const ny = ty.value + vy
    if (nx < b.minX || nx > b.maxX || ny < b.minY || ny > b.maxY) {
      tx.value = nx
      ty.value = ny
      settle(200)
      return
    }
    tx.value = nx
    ty.value = ny
    if (Math.abs(vx) < MIN_SPEED && Math.abs(vy) < MIN_SPEED) {
      frame = null
      return
    }
    frame = requestFrame(step)
  }
  step()
}

function pointOf(touch: Touch): { x: number; y: number } {
  return { x: touch.clientX, y: touch.clientY }
}

function distance(a: Touch, b: Touch): number {
  return Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY)
}

function beginPinch(touches: TouchList): void {
  const a = touches[0]
  const b = touches[1]
  pinchDist = Math.max(1, distance(a, b))
  pinchScale = scale.value
  const midX = (a.clientX + b.clientX) / 2
  const midY = (a.clientY + b.clientY) / 2
  anchorWorldX = (midX - tx.value) / scale.value
  anchorWorldY = (midY - ty.value) / scale.value
  mode = 'pinch'
  moved = true
}

function onTouchStart(event: TouchEvent): void {
  stopAnimation()
  if (event.touches.length >= 2) {
    beginPinch(event.touches)
    return
  }
  const point = pointOf(event.touches[0])
  mode = 'pan'
  startX = point.x
  startY = point.y
  startTx = tx.value
  startTy = ty.value
  startAt = now()
  moved = false
  lastX = point.x
  lastY = point.y
  lastAt = startAt
  speedX = 0
  speedY = 0
}

function onTouchMove(event: TouchEvent): void {
  if (event.touches.length >= 2) {
    if (mode !== 'pinch') beginPinch(event.touches)
    const a = event.touches[0]
    const b = event.touches[1]
    const next = clamp(pinchScale * (distance(a, b) / pinchDist), MIN_SCALE, MAX_SCALE)
    const midX = (a.clientX + b.clientX) / 2
    const midY = (a.clientY + b.clientY) / 2
    scale.value = next
    rubber(midX - anchorWorldX * next, midY - anchorWorldY * next)
    return
  }

  if (event.touches.length !== 1) return
  if (mode === 'pinch') {
    // 双指变单指：把剩下这根手指作为新的平移起点，避免跳变
    const point = pointOf(event.touches[0])
    mode = 'pan'
    startX = point.x
    startY = point.y
    startTx = tx.value
    startTy = ty.value
    startAt = now()
    moved = true
    lastX = point.x
    lastY = point.y
    lastAt = startAt
    return
  }

  const point = pointOf(event.touches[0])
  if (Math.abs(point.x - startX) > TAP_MOVE_PX || Math.abs(point.y - startY) > TAP_MOVE_PX) {
    moved = true
  }
  rubber(startTx + (point.x - startX), startTy + (point.y - startY))

  const at = now()
  if (at > lastAt) {
    speedX = ((point.x - lastX) / (at - lastAt)) * 16
    speedY = ((point.y - lastY) / (at - lastAt)) * 16
  }
  lastX = point.x
  lastY = point.y
  lastAt = at
}

function hitTest(x: number, y: number): DishVO | null {
  if (!gridIndex) return null
  const worldX = (x - tx.value) / scale.value
  const worldY = (y - ty.value) / scale.value
  return hitTestGrid(gridIndex, worldX, worldY)?.dish ?? null
}

function onTouchEnd(event: TouchEvent): void {
  // 仍有手指在屏上（双指抬起其一）：切换为单指平移，不结束手势
  if (event.touches.length >= 1) {
    const point = pointOf(event.touches[0])
    mode = 'pan'
    startX = point.x
    startY = point.y
    startTx = tx.value
    startTy = ty.value
    startAt = now()
    moved = true
    lastX = point.x
    lastY = point.y
    lastAt = startAt
    speedX = 0
    speedY = 0
    return
  }

  const wasPan = mode === 'pan'
  const point = event.changedTouches[0]
  mode = 'none'
  if (wasPan && !moved && now() - startAt <= TAP_DURATION_MS && point) {
    const dish = hitTest(point.clientX, point.clientY)
    if (dish) {
      emit('pick', dish)
      return
    }
  }
  if (wasPan) inertia()
  else settle(200)
}

function onImageError(id: number): void {
  failedIds.add(id)
}

/** 窗口尺寸变化：刷新缓存并重建布局（磁贴尺寸随之重算） */
let resizeTimer: ReturnType<typeof setTimeout> | null = null
function handleResize(): void {
  if (resizeTimer != null) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    Object.assign(viewport, refreshWindowSize())
    rebuild()
  }, 120)
}

onMounted(() => {
  uni.onWindowResize(handleResize)
})

onBeforeUnmount(() => {
  stopAnimation()
  if (resizeTimer != null) clearTimeout(resizeTimer)
  uni.offWindowResize(handleResize)
})
</script>

<template>
  <view
    class="map"
    @touchstart="onTouchStart"
    @touchmove.stop.prevent="onTouchMove"
    @touchend="onTouchEnd"
    @touchcancel="onTouchEnd"
  >
    <view class="map__world" :style="worldStyle">
      <view
        v-for="tile in visibleTiles"
        :key="tile.dish.id"
        class="map__tile"
        :style="tileStyle(tile)"
        hover-class="map__tile--pressed"
        hover-stay-time="80"
      >
        <image
          v-if="tile.dish.imageUrl && !failedIds.has(tile.dish.id)"
          class="map__tile-img"
          :src="thumbUrl(tile.dish.imageUrl)"
          mode="aspectFill"
          lazy-load
          @error="onImageError(tile.dish.id)"
        />
        <ImagePlaceholder v-else bare />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.map {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
  background: var(--bg-page);
  touch-action: none;
}

.map__world {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  will-change: transform;
}

.map__tile {
  position: absolute;
  overflow: hidden;
  border-radius: var(--radius-card);
  background: var(--bg-card);
  box-shadow: var(--shadow-card-stack);
  /* Q 弹：点按缩放反馈（过冲缓动） */
  transition: transform var(--duration-fast) var(--ease-bounce);
}

.map__tile--pressed {
  transform: scale(0.94);
}

.map__tile-img {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: var(--radius-card);
}
</style>
