<script setup lang="ts">
/**
 * 底部弹层（口径见 docs/ui/client/公共组件与形态基线.md §2.1.4）。
 * 本项目用法：美食地图点磁贴唤起菜品弹窗（DishSheet），承载详情 / 点赞 / 纠错。
 */
import { computed, getCurrentInstance, nextTick, ref, watch } from 'vue'

/** 宿主节点虚拟化：组件根直接参与页面布局 */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    /** 是否显示变暗蒙版；`false` 时背景透明且不接收事件，下方画布保持可点可拖 */
    mask?: boolean
  }>(),
  { title: '', mask: true },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'close'): void
  (e: 'measured', height: number): void
}>()

/** 下拉关闭：跟手位移超过 120rpx（= 60px）即关闭，未超过回弹 */
const CLOSE_DRAG_PX = 60
/** 推入 / 推出时长，与 --duration-base 对齐 */
const CLOSE_ANIM_MS = 240

const instance = getCurrentInstance()
/** 面板是否挂载：与入参解耦，保证退场动画播完才卸载 */
const visible = ref(false)
const dragging = ref(false)
const closing = ref(false)
const dragOffset = ref(0)
let dragStartY = 0
let closeTimer: ReturnType<typeof setTimeout> | null = null

/** 面板跟手位移；退出时整块滑出屏幕（末段位移交给 CSS transition） */
const panelStyle = computed(() => ({
  transform: closing.value ? 'translateY(100%)' : `translateY(${dragOffset.value}px)`,
}))

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      if (closeTimer) {
        clearTimeout(closeTimer)
        closeTimer = null
      }
      dragging.value = false
      closing.value = false
      dragOffset.value = 0
      visible.value = true
      measureHeight()
      return
    }
    // 入参转 false：同样先播退场，避免直接卸载造成画面跳变
    finishLater()
  },
)

/** 兜底重测定时器：入场动画结束后再量一次，避免首帧尚未完成布局 */
let measureTimer: ReturnType<typeof setTimeout> | null = null

/**
 * 面板落地后上报真实高度：画布据此算出「弹层顶边以上」的可视净空区。
 *
 * 用 `instance.proxy` 查询 —— `virtualHost` 下部分基础库传内部实例查不到节点，
 * 量不到就会退回预估比例，表现为「点边缘卡片时偏离可视区中心」。
 * 另外在入场动画结束后补测一次，防止首帧布局未完成。
 */
function measureHeight(): void {
  const proxy = instance?.proxy
  if (!proxy) return
  const run = () => {
    if (!visible.value) return
    uni
      .createSelectorQuery()
      .in(proxy)
      .select('.sheet__panel')
      .boundingClientRect((rect) => {
        const height = (rect as unknown as { height?: number } | null)?.height
        if (typeof height === 'number' && height > 0) emit('measured', height)
      })
      .exec()
  }
  nextTick(() => {
    run()
    if (measureTimer != null) clearTimeout(measureTimer)
    measureTimer = setTimeout(run, CLOSE_ANIM_MS + 80)
  })
}

/** 向下推出：动画结束后卸载并上报关闭 */
function finishLater(): void {
  if (!visible.value || closing.value) return
  closing.value = true
  dragOffset.value = 0
  if (measureTimer != null) {
    clearTimeout(measureTimer)
    measureTimer = null
  }
  closeTimer = setTimeout(() => {
    closeTimer = null
    visible.value = false
    closing.value = false
    emit('close')
  }, CLOSE_ANIM_MS)
}

function onDragStart(event: TouchEvent) {
  if (closing.value) return
  const touch = event.touches[0]
  if (!touch) return
  dragging.value = true
  dragStartY = touch.clientY
}

/** 仅允许向下拖；向上不展开，回弹到 0 */
function onDragMove(event: TouchEvent) {
  if (!dragging.value) return
  const touch = event.touches[0]
  if (!touch) return
  dragOffset.value = Math.max(0, touch.clientY - dragStartY)
}

function onDragEnd() {
  if (!dragging.value) return
  dragging.value = false
  if (dragOffset.value > CLOSE_DRAG_PX) {
    finishLater()
    emit('update:modelValue', false)
    return
  }
  dragOffset.value = 0
}

/** 对外关闭入口：播放退场动画并同步 v-model */
function dismiss(): void {
  finishLater()
  emit('update:modelValue', false)
}

defineExpose({ dismiss })
</script>

<template>
  <view v-if="visible" class="sheet">
    <view class="sheet__scrim" :class="{ 'sheet__scrim--clear': !mask }" @tap="dismiss" />
    <view class="sheet__panel" :class="{ 'sheet__panel--dragging': dragging }" :style="panelStyle">
      <view
        class="sheet__handle"
        @touchstart="onDragStart"
        @touchmove.stop.prevent="onDragMove"
        @touchend="onDragEnd"
        @touchcancel="onDragEnd"
      >
        <view class="sheet__grabber" />
      </view>
      <view v-if="title" class="sheet__title">{{ title }}</view>
      <view class="sheet__body">
        <slot />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 根节点铺满全屏但不接收事件：否则它会吃掉上半屏的所有点击，
   让下方画布无法点选。只有 scrim 与 panel 各自显式开启命中。 */
.sheet {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: var(--z-sheet);
  pointer-events: none;
}

.sheet__scrim {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: var(--overlay-scrim);
  pointer-events: auto;
}

/* 无蒙版：背景透明（不变暗、不加滤镜），且不接收事件 —— 下方画布保持可点可拖，
   关闭改由「点画布空白」与「下拉把手」承担 */
.sheet__scrim--clear {
  background: transparent;
  pointer-events: none;
}

.sheet__panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  /* 兜底 0px：不支持 env() 的环境下不会整条声明失效 */
  padding: 0 0 env(safe-area-inset-bottom, 0px);
  /* 根节点已禁用命中，面板须显式开启，否则自身也点不动 */
  pointer-events: auto;
  max-height: 92vh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background: var(--overlay-panel-bg);
  border-top: 1rpx solid var(--border-color);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  /* 推入：无过冲，直线滑到终点 */
  animation: sheet-rise var(--duration-base) var(--ease-out);
  transition: transform var(--duration-base) var(--ease-out);
  transform-origin: bottom center;
  will-change: transform;
}

/* 拖拽中禁用过渡，保证面板跟手 */
.sheet__panel--dragging {
  transition: none;
}

/* 顶部压痕：把手区高度 = 顶部 gap，压痕在其中垂直居中 */
.sheet__handle {
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--spacing-lg);
  touch-action: none;
}

.sheet__grabber {
  width: calc(var(--spacing-2xl) + var(--spacing-lg));
  height: var(--spacing-xs);
  background: var(--border-color);
  border-radius: var(--radius-pill);
}

.sheet__title {
  margin-bottom: var(--spacing-md);
  font-family: var(--font-sans);
  font-size: var(--font-subtitle);
  font-weight: var(--weight-regular);
  color: var(--text-title);
}

.sheet__body {
  display: flex;
  flex-direction: column;
}

/* 推入：面板自底边整块滑到终点，不回弹 */
@keyframes sheet-rise {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .sheet__panel {
    animation: none;
  }
}
</style>
