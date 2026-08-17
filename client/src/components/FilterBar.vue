<template>
  <view class="filter-wheel">
    <!-- 左右边缘淡化（模拟滚轮纵深） -->
    <view class="wheel-fade left" />
    <view class="wheel-fade right" />

    <view
      class="wheel"
      :style="{
        transform: `translateX(${visualOffset}px)`,
        transition: animating ? 'transform 0.32s cubic-bezier(0.22, 1, 0.36, 1)' : 'none',
      }"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
      @touchcancel="onTouchEnd"
    >
      <view
        v-for="(tab, i) in loopTabs"
        :key="i"
        class="wheel-item"
        :class="{ active: i === activeIndex }"
        :style="{ width: itemW + 'px' }"
        @tap="onTapItem(i)"
      >
        <text class="wheel-label">{{ tab.label }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, getCurrentInstance } from 'vue'
import type { FilterTab } from './filter-tab'

const props = defineProps<{
  /** 品类/标签等扁平平铺维度（单级滚轮，首尾相接成环） */
  tabs: FilterTab[]
  /** 当前选中项的 key */
  modelValue: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', key: string): void
  (e: 'change', tab: FilterTab): void
}>()

/** 品类项固定宽度（px；短标签 2~4 字，均匀步进保证循环精确吸附） */
const itemW = 84

/** 三份复制实现首尾无缝循环：renderIndex 中心落在 [n, 2n) */
const n = computed(() => props.tabs.length || 1)
const loopTabs = computed(() => [...props.tabs, ...props.tabs, ...props.tabs])

const wrapW = ref(360)
const animating = ref(false)

/** 连续视觉索引（可为浮点）：中心刻度始终对齐 viewIndex 对应 item；循环无缝的关键 */
const viewIndex = ref(0)

/** 逻辑选中索引（归一化到 [0, n)，用于 emit 与 tabs 索引） */
const logicalIndex = computed(() => {
  const idx = Math.round(viewIndex.value)
  return ((idx % n.value) + n.value) % n.value
})

/**
 * 高亮项（模板用）：归一化到中间份 [n, 2n)。
 * 关键修复：loopTabs 是三份复制，屏幕中心可见的始终是中间份（n + logicalIndex），
 * 若用 [0, n) 高亮，红线/大字体全部落在屏幕外第一份，选中项视觉无任何选中态。
 */
const activeIndex = computed(() => logicalIndex.value + n.value)

/** 手势拖动状态 */
const dragging = ref(false)
let startX = 0
let startViewIndex = 0
let moved = false
let justDragged = false

/** 视觉偏移（px）：中心刻度对齐选中项中心 */
const visualOffset = computed(() => {
  const base = wrapW.value / 2 - itemW / 2
  return base - viewIndex.value * itemW
})

/** 归一化到中间份 [n, 2n)：保证 loopTabs 中间一份可见且内容正确 */
function normalizeMiddle(raw: number): number {
  return ((Math.round(raw) % n.value) + n.value) % n.value + n.value
}

function emitCurrent() {
  const idx = logicalIndex.value
  emit('update:modelValue', props.tabs[idx].key)
  emit('change', props.tabs[idx])
}

function snapTo(rawIndex: number, withAnimation = true) {
  const target = normalizeMiddle(rawIndex)
  // 关键修复：若当前不在中间份（拖出边界），回绕时必须禁用动画，
  // 否则会从第一/三份「滚动一整圈」回中间份，表现为弹回原点。
  const sameRange = viewIndex.value >= n.value && viewIndex.value < n.value * 2
  animating.value = withAnimation && sameRange
  viewIndex.value = target
  emitCurrent()
}

function onTouchStart(e: TouchEvent) {
  dragging.value = true
  moved = false
  justDragged = false
  animating.value = false
  startX = (e.touches?.[0]?.clientX ?? (e as any).clientX ?? 0) as number
  startViewIndex = viewIndex.value
}

function onTouchMove(e: TouchEvent) {
  if (!dragging.value) return
  const x = (e.touches?.[0]?.clientX ?? (e as any).clientX ?? 0) as number
  const delta = x - startX
  if (Math.abs(delta) > 4) moved = true
  // 向左拖（delta<0）→ 显示更靠后的 item → viewIndex 增大；clamp 在 [0, 3n-1] 避免拖出空白
  const raw = startViewIndex - delta / itemW
  viewIndex.value = Math.max(0, Math.min(n.value * 3 - 1, raw))
}

function onTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  if (!moved) return
  justDragged = true
  // 吸附最近项（归一化到中间份；越界时无动画回绕，视觉无缝）
  snapTo(viewIndex.value, true)
  setTimeout(() => {
    justDragged = false
  }, 0)
}

function onTapItem(i: number) {
  if (justDragged || dragging) return
  snapTo(i, true)
}

function syncFromModelValue() {
  // modelValue 无效（初始空串/未命中）时默认第一项，确保 viewIndex 落在中间份 [n, 2n)，
  // 屏幕中心项与高亮项（n + logicalIndex）始终一致。
  const idx = props.tabs.findIndex((t) => t.key === props.modelValue)
  const target = idx >= 0 ? idx : 0
  viewIndex.value = n.value + target
  animating.value = false
}

/** 兜底容器宽：屏幕宽 - 两侧水平 padding（.feed-wrap padding 0 var(--spacing-md)，各 24rpx） */
function fallbackWrapW() {
  try {
    const info = uni.getSystemInfoSync()
    wrapW.value = info.windowWidth - uni.upx2px(48)
  } catch (e) {
    // 保持默认，下次测量再校正
  }
}

function measure() {
  // 延迟到布局稳定后再测：v-if 刚渲染 / H5 首帧时 nextTick 可能取不到宽度，
  // wrapW 停留默认 360px 会导致 visualOffset 基准错误、选中项整体偏左。
  setTimeout(() => {
    const instance = getCurrentInstance()
    // .in() 兼容：vue3 script setup 下 proxy 与 instance 均可用，取其一
    const inst = instance?.proxy ?? instance
    const query = uni.createSelectorQuery().in(inst as any)
    query
      .select('.filter-wheel')
      .boundingClientRect((el: any) => {
        if (el?.width) wrapW.value = el.width
        else fallbackWrapW()
      })
      .exec()
  }, 30)
}

watch(
  () => props.modelValue,
  () => {
    if (!dragging.value) syncFromModelValue()
  },
)

watch(
  () => props.tabs,
  () => {
    syncFromModelValue()
    measure()
  },
  { immediate: true },
)

onMounted(measure)
</script>

<style scoped lang="scss">
.filter-wheel {
  position: relative;
  width: 100%;
  height: 88rpx;
  overflow: hidden;
  user-select: none;
  touch-action: pan-y;
  background: transparent;
}

/* 左右边缘淡化 */
.wheel-fade {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 24%;
  pointer-events: none;
  z-index: 2;

  &.left {
    left: 0;
    background: linear-gradient(to right, var(--bg-page) 30%, transparent);
  }

  &.right {
    right: 0;
    background: linear-gradient(to left, var(--bg-page) 30%, transparent);
  }
}

.wheel {
  display: flex;
  align-items: center;
  height: 100%;
  will-change: transform;
}

.wheel-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  flex-shrink: 0;
  /* 底部预留选中红线位（透明 border 占位，避免选中/未选中切换高度抖动） */
  border-bottom: 8rpx solid transparent;

  .wheel-label {
    font-size: 28rpx;
    color: #8a8278;
    font-weight: 500;
    white-space: nowrap;
    transition: color 0.2s ease, font-size 0.2s ease, transform 0.2s ease;
  }

  &.active {
    border-bottom-color: var(--color-primary);
  }

  &.active .wheel-label {
    color: var(--color-primary);
    font-weight: 700;
    font-size: 40rpx;
    transform: scale(1);
  }
}
</style>
