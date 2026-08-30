<template>
  <!-- 动态卡片三点菜单：底部 ActionSheet（页面根级挂载，fixed 遮罩才能正确覆盖全屏）。
       与 AuthSheet/ApplySheet 同一底部抽屉范式：grabber 横条 + 右上角关闭 + 无取消按钮 +
       transform 过渡动画 + 下拉关闭手势（2026-08-19 统一）。
       注意：组件必须放在 scroll-view 之外（微信小程序 scroll-view 内 fixed 层级会被压扁/裁剪） -->
  <view v-if="open" class="more-root">
    <view class="more-mask" :class="{ show: maskShow }" @tap="close" @touchmove.stop.prevent="noop" />
    <view
      class="more-sheet"
      :class="{ open: sheetOpen }"
      :style="sheetStyle"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
      @touchcancel="onTouchEnd"
    >
      <view class="sheet-grabber" />
      <!-- 头部：与 ApplySheet/AuthSheet 一致 —— 仅右上角关闭按钮 + 底部分隔线（无标题、无取消） -->
      <view class="sheet-head">
        <view class="sheet-close" role="button" aria-label="关闭" @tap.stop="close">
          <IconSvg name="close" :size="36" color="var(--text-tertiary)" />
        </view>
      </view>
      <!-- 分享：微信原生分享（open-type=share → 页面 onShareAppMessage） -->
      <button class="more-item" open-type="share" @tap="onShareTap">
        <IconSvg name="share" :size="34" color="var(--text-primary)" class="more-item-icon" />
        <text class="more-item-text">分享</text>
      </button>
      <!-- 举报：emit 给父页面弹 ReportModal。选项不设按钮背景，与其他弹层一致 -->
      <view class="more-item more-item--danger" role="button" aria-label="举报动态" @tap="onReport">
        <IconSvg name="report" :size="34" color="var(--color-error)" class="more-item-icon" />
        <text class="more-item-text">举报</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import IconSvg from './IconSvg.vue'
import { sharedMoment } from '@/utils/share-state'
import type { Moment } from '@/types/moment'

const props = defineProps<{
  open: boolean
  moment?: Moment | null
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'report', moment: Moment): void
}>()

/** 空处理器：mask touchmove.stop 防背景滚动穿透（小程序 catchtouchmove） */
function noop() {}

// 开合动画状态（与 ApplySheet/AuthSheet 一致：遮罩淡入 + sheet 上滑）
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

/** 下拉关闭手势（与 ApplySheet/AuthSheet 手感一致：1:1 跟随 + 速度投影，松手速度 >480 或位移 >120 关闭） */
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
  const dt = Math.max(now - lastTime, 1)
  velocity = ((y - lastY) / dt) * 1000
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

function onShareTap() {
  // 记录待分享动态，页面 onShareAppMessage 据此生成分享卡片（微信原生分享）
  if (props.moment) sharedMoment.value = props.moment
  close()
}

function onReport() {
  close()
  if (props.moment) emit('report', props.moment)
}
</script>

<style scoped>
/* 底部 ActionSheet：与 AuthSheet/ApplySheet 统一抽屉范式（grabber + 圆角卡片 + shadow-modal + transform 过渡）。
   选项不设置按钮背景（去背景块），与登录/认证等弹层的选项视觉一致 */
.more-root { z-index: var(--z-actionsheet); }
/* 遮罩：与 ApplySheet 一致（--overlay-scrim 半透明，opacity 过渡） */
.more-mask {
  position: fixed; inset: 0; background: var(--overlay-scrim);
  opacity: 0; transition: opacity var(--duration-slow) var(--ease-out); z-index: calc(var(--z-actionsheet) - 10);
}
.more-mask.show { opacity: 1; }

/* 底部弹层：统一底部抽屉规范（radius-modal 顶部圆角 + shadow-modal + translateY 抽屉） */
.more-sheet {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: var(--z-actionsheet);
  transform: translateY(100%);
  padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-md) + env(safe-area-inset-bottom));
  will-change: transform;
}
.more-sheet.open { transform: translateY(0); }

/* 顶部小横条：与 ApplySheet 同款（72×8、999rpx、--overlay-dark-soft 半透明深色） */
.sheet-grabber { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--overlay-dark-soft); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }

/* 头部：与 ApplySheet/AuthSheet 一致 —— 仅右上角关闭按钮（无标题、无取消），底部分隔线 */
.sheet-head { display: flex; align-items: center; justify-content: flex-end; padding: var(--spacing-sm) var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-close { padding: 0 var(--spacing-xs); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.sheet-close:active { opacity: 0.5; }
/* 选项：无背景色块，仅图标 + 文字 + 分隔线（iOS ActionSheet 风格，与其他弹层一致） */
.more-item {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  width: 100%;
  height: 104rpx;
  font-size: var(--font-body);
  color: var(--text-primary);
  border-bottom: 2rpx solid var(--border-color);
  background: transparent;
  /* 原生 button 默认样式重置 */
  margin: 0;
  padding: 0;
  line-height: normal;
  border-radius: 0;
}
.more-item::after { border: none; }
.more-item:active { opacity: 0.7; }
.more-item-icon { flex-shrink: 0; }
.more-item-text { font-weight: var(--weight-medium); }
.more-item--danger .more-item-text { color: var(--color-error); }

@media (prefers-reduced-motion: reduce) {
  .more-mask { transition: opacity 0.2s ease; }
  .more-sheet { transition: opacity 0.2s ease; transform: none !important; }
}
</style>
