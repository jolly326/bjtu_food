<template>
  <!-- 底部弹层骨架唯一真源（component-org-sheet-unify）：
       半透明遮罩（点击关闭）/ 顶部 grabber / translateY 上滑开合 / 下拉关闭手势 /
       env(safe-area-inset-bottom) 底部安全区 / 可选头部与内容区滚动。
       挂载策略由父级决定：AuthSheet 用 v-show 常驻传 visible，其余父级 v-if 懒挂载后传 visible。 -->
  <view
    v-show="visible"
    class="bs-root"
    :style="rootStyle"
    role="dialog"
    :aria-modal="true"
    tabindex="-1"
    @touchstart="onTouchStart"
    @touchmove.stop.prevent="onTouchMove"
    @touchend="onTouchEnd"
    @touchcancel="onTouchEnd"
  >
    <view
      class="bs-mask"
      :class="{ show: maskShow }"
      :style="maskStyle"
      @tap="emitClose"
      @touchmove.stop.prevent="noop"
    />
    <view class="bs-sheet" :class="{ open: sheetOpen }" :style="sheetStyle">
      <view class="bs-grabber" />
      <view v-if="closable || title || backable" class="bs-head">
        <view class="bs-head-left">
          <view v-if="backable" class="bs-back" role="button" aria-label="返回" @tap.stop="emit('back')">
            <IconSvg name="arrow" :size="30" color="var(--text-secondary)" />
          </view>
          <text v-if="title" class="bs-title">{{ title }}</text>
        </view>
        <view v-if="closable" class="bs-close" role="button" aria-label="关闭" @tap.stop="emitClose">
          <IconSvg name="close" :size="36" color="var(--text-tertiary)" />
        </view>
      </view>
      <scroll-view v-if="scrollBody" class="bs-body bs-body--scroll" scroll-y>
        <slot />
      </scroll-view>
      <view v-else class="bs-body">
        <slot />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import IconSvg from './IconSvg.vue'
import { useSheetFocus } from '@/composables/useSheetFocus'

/**
 * 受控底部弹层骨架：visible 驱动开合动画；不接管挂载策略（常驻/懒挂载由父级决定，
 * 以保证 AuthSheet 的 v-show 常驻与发码冷却跨关闭续接等既有时序不被破坏）。
 */
const props = withDefaults(defineProps<{
  visible: boolean
  /** 弹层 z 层级 token 名（含 -- 前缀），如 --z-auth / --z-actionsheet / --z-sheet */
  zToken?: string
  /** 右上角关闭钮（按语义显式开启；动态/认证等「去 X」弹层不传） */
  closable?: boolean
  /** 头部标题（可省略） */
  title?: string
  /** 头部标题左侧返回箭头（层级型弹层回退到上级；与 closable 右上关闭钮并存） */
  backable?: boolean
  /** 内容区是否用 scroll-view 包裹（内容超高时可滚动；默认普通 view） */
  scrollBody?: boolean
  /** 是否接管弹层打开/关闭的焦点还原（默认开启，AuthSheet 等既有语义保持不变） */
  manageFocus?: boolean
}>(), {
  zToken: '--z-sheet',
  closable: false,
  title: '',
  backable: false,
  scrollBody: false,
  manageFocus: true,
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'back'): void
}>()

const { captureTrigger, restoreFocus } = useSheetFocus()

/** 空处理器：mask touchmove.stop 防背景滚动穿透（小程序 catchtouchmove） */
function noop() {}

function emitClose() {
  emit('close')
}

// 开合动画状态（遮罩淡入 + sheet 上滑；与既有弹层同一手感）
const maskShow = ref(false)
const sheetOpen = ref(false)
const dragOffset = ref(0)
const dragging = ref(false)

const rootStyle = computed(() => ({
  zIndex: `var(${props.zToken})`,
}))
const maskStyle = computed(() => ({
  zIndex: `calc(var(${props.zToken}) - 10)`,
}))
const sheetStyle = computed(() => ({
  transform: `translateY(calc(${sheetOpen.value ? 0 : 100}% + ${dragging.value ? dragOffset.value : 0}px))`,
  transition: 'none',
  zIndex: `var(${props.zToken})`,
}))

watch(
  () => props.visible,
  (v) => {
    if (v) {
      if (props.manageFocus) captureTrigger()
      nextTick(() => {
        maskShow.value = true
        sheetOpen.value = true
      })
    } else {
      maskShow.value = false
      sheetOpen.value = false
      dragOffset.value = 0
      if (props.manageFocus) restoreFocus()
    }
  },
)

/** 下拉关闭手势（1:1 跟随 + 速度投影，松手速度 >480px/s 或位移 >120rpx 关闭）。
 *  根容器 touchmove 带 stop.prevent（= catchtouchmove）：弹层区域上开始的手势不再穿透滚动背景页面；
 *  内部原生 scroll-view（scrollBody / ListPickerSheet 列表）自行承接滚动，不受影响。 */
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
  if (velocity > 480 || dragOffset.value > 120) emitClose()
  dragOffset.value = 0
}
</script>

<style scoped>
/* 根容器仅占位 z-index 宿主，遮罩/弹层 fixed 覆盖全屏 */
.bs-root { position: relative; }

/* 遮罩：--overlay-scrim 半透明，opacity 过渡（与既有弹层一致）；z-index 由内联 style 按 zToken 折算 */
.bs-mask {
  position: fixed; inset: 0;
  background: var(--overlay-scrim);
  opacity: 0;
  transition: opacity var(--duration-slow) var(--ease-out);
}
.bs-mask.show { opacity: 1; }

/* 底部弹层：统一底部抽屉规范（radius-modal 顶部圆角 + shadow-modal + translateY 抽屉）；z-index 由内联 style 提供 */
.bs-sheet {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  transform: translateY(100%);
  display: flex;
  flex-direction: column;
  max-height: 88vh;
  overflow: hidden;
  padding-bottom: env(safe-area-inset-bottom);
  will-change: transform;
}
.bs-sheet.open { transform: translateY(0); }

/* 顶部小横条：72×8、--overlay-dark-soft 半透明深色 */
.bs-grabber { width: 72rpx; height: 8rpx; border-radius: var(--radius-pill); background: var(--overlay-dark-soft); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }

/* 可选头部：标题左（如有）+ 关闭钮右 */
.bs-head { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md); padding: var(--spacing-sm) var(--spacing-md); border-bottom: 2rpx solid var(--border-color); flex-shrink: 0; }
.bs-head-left { display: flex; align-items: center; gap: var(--spacing-2xs); flex: 1; min-width: 0; }
.bs-back { width: 48rpx; height: 48rpx; display: flex; align-items: center; justify-content: center; transform: scaleX(-1); flex-shrink: 0; -webkit-tap-highlight-color: transparent; }
.bs-back:active { opacity: 0.5; }
.bs-title { flex: 1; min-width: 0; font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.bs-close { padding: 0 var(--spacing-xs); flex-shrink: 0; -webkit-tap-highlight-color: transparent; }
.bs-close:active { opacity: 0.5; }

/* 内容区：普通容器（默认无额外横留白，由各调用方内容决定）或滚动容器（复用 AuthSheet 口径留白） */
.bs-body { flex: 1; min-height: 0; }
.bs-body--scroll { box-sizing: border-box; padding: var(--spacing-md) var(--spacing-lg) calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }

@media (prefers-reduced-motion: reduce) {
  .bs-mask { transition: opacity 0.2s ease; }
  .bs-sheet { transition: opacity 0.2s ease; transform: none !important; }
}
</style>
