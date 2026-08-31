<template>
  <!-- 首页排序面板：底部 Sheet（与 MomentActionSheet/AuthSheet 同一抽屉范式：
       grabber 横条 + 右上角关闭 + transform 过渡 + 下拉关闭手势）。
       必须挂在 scroll-view 之外（小程序 scroll-view 内 fixed 层级会被裁剪） -->
  <view v-if="open" class="sort-root">
    <view class="sort-mask" :class="{ show: maskShow }" @tap="close" @touchmove.stop.prevent="noop" />
    <view
      class="sort-sheet"
      :class="{ open: sheetOpen }"
      :style="sheetStyle"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
      @touchcancel="onTouchEnd"
    >
      <view class="sheet-grabber" />
      <view class="sheet-head">
        <view class="sheet-close" role="button" aria-label="关闭" @tap.stop="close">
          <IconSvg name="close" :size="36" color="var(--text-tertiary)" />
        </view>
      </view>

      <view
        v-for="opt in options"
        :key="opt.key"
        class="sort-item"
        :class="{ active: opt.key === current }"
        role="button"
        :aria-label="`按${opt.label}排序`"
        @tap="pick(opt.key)"
      >
        <IconSvg :name="opt.icon" :size="34" :color="opt.key === current ? 'var(--color-primary)' : 'var(--text-primary)'" class="sort-item-icon" />
        <text class="sort-item-text">{{ opt.label }}</text>
        <IconSvg v-if="opt.key === current" name="check" :size="34" color="var(--color-primary)" class="sort-item-check" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import IconSvg from './IconSvg.vue'
import type { HomeSortKey } from '@/stores/dish'

const props = defineProps<{
  open: boolean
  /** 当前选中排序项（受控） */
  current: HomeSortKey
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'select', key: HomeSortKey): void
}>()

/** 排序选项（问题一拍板：最新/距离最近/价格↑/价格↓/热度最高；「综合推荐」不保留） */
const options: { key: HomeSortKey; label: string; icon: string }[] = [
  { key: 'latest', label: '最新', icon: 'clock' },
  { key: 'distance', label: '距离最近', icon: 'location' },
  { key: 'priceAsc', label: '价格从低到高', icon: 'up' },
  { key: 'priceDesc', label: '价格从高到低', icon: 'arrow-down' },
  { key: 'hot', label: '热度最高', icon: 'fire' },
]

function noop() {}

const maskShow = ref(false)
const sheetOpen = ref(false)
const dragOffset = ref(0)
const dragging = ref(false)

const sheetStyle = computed(() => ({
  transform: `translateY(calc(${sheetOpen.value ? 0 : 100}% + ${dragging.value ? dragOffset.value : 0}px))`,
  transition: dragging.value ? 'none' : 'transform var(--duration-slow) var(--ease-drawer)',
}))

watch(() => props.open, (v) => {
  if (v) {
    nextTick(() => {
      maskShow.value = true
      sheetOpen.value = true
    })
  } else {
    maskShow.value = false
    sheetOpen.value = false
    dragOffset.value = 0
  }
})

/** 下拉关闭手势（与 MomentActionSheet 手感一致：松手速度 >480 或位移 >120 关闭） */
let startY = 0
let lastY = 0
let lastTime = 0
let velocity = 0
function onTouchStart(e: any) {
  startY = e.touches?.[0]?.clientY ?? 0
  lastY = startY
  lastTime = Date.now()
  velocity = 0
  dragging.value = true
}
function onTouchMove(e: any) {
  if (!dragging.value) return
  const y = e.touches?.[0]?.clientY ?? 0
  const now = Date.now()
  velocity = ((y - lastY) / Math.max(now - lastTime, 1)) * 1000
  lastY = y
  lastTime = now
  dragOffset.value = Math.max(y - startY, 0)
}
function onTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  if (velocity > 480 || dragOffset.value > 120) close()
  dragOffset.value = 0
}

function close() {
  emit('update:open', false)
}

function pick(key: HomeSortKey) {
  close()
  emit('select', key)
}
</script>

<style scoped>
.sort-root { z-index: var(--z-actionsheet); }
.sort-mask {
  position: fixed; inset: 0; background: var(--overlay-scrim);
  opacity: 0; transition: opacity var(--duration-slow) var(--ease-out); z-index: calc(var(--z-actionsheet) - 10);
}
.sort-mask.show { opacity: 1; }

.sort-sheet {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: var(--z-actionsheet);
  transform: translateY(100%);
  padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-md) + env(safe-area-inset-bottom));
  will-change: transform;
}
.sort-sheet.open { transform: translateY(0); }

.sheet-grabber { width: 72rpx; height: 8rpx; border-radius: var(--radius-pill); background: var(--overlay-dark-soft); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }
.sheet-head { display: flex; align-items: center; justify-content: flex-end; padding: var(--spacing-sm) var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-close { padding: 0 var(--spacing-xs); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.sheet-close:active { opacity: 0.5; }

.sort-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  width: 100%;
  height: 104rpx;
  padding: 0 var(--spacing-md);
  box-sizing: border-box;
  border-bottom: 2rpx solid var(--border-color);
  background: transparent;
  transition: transform var(--duration-fast) var(--ease-out), background-color var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.sort-item:active { transform: scale(var(--press-scale)); }
.sort-item.active .sort-item-text { color: var(--color-primary); font-weight: var(--weight-semibold); }
.sort-item-icon { flex-shrink: 0; }
.sort-item-text { flex: 1; min-width: 0; font-size: var(--font-body); color: var(--text-primary); font-weight: var(--weight-medium); }
.sort-item-check { flex-shrink: 0; line-height: 1; }

@media (prefers-reduced-motion: reduce) {
  .sort-mask { transition: opacity 0.2s ease; }
  .sort-sheet { transition: opacity 0.2s ease; transform: none !important; }
  .sort-item { transition: none; }
  .sort-item:active { transform: none; }
}
</style>
