<template>
  <!-- N08 修复：v-show 保持 AuthSheet 常驻挂载，关闭弹层不再卸载 AuthForm，发码倒计时不被清空（前端不辅助绕过 60s 冷却） -->
  <view v-show="visible" class="auth-root">
    <!-- 半透明遮罩（点击关闭；touchmove.stop 防背景滚动穿透，与 ApplySheet 一致） -->
    <view
      class="sheet-mask"
      :class="{ show: maskShow }"
      @tap="hide"
      @touchmove.stop.prevent="noop"
    />

    <!-- 底部弹层：复用 ApplySheet 抽屉范式（圆角/grabber/遮罩/下拉关闭/spring 0.3s） -->
    <view
      class="bottom-sheet"
      :class="{ open: sheetOpen }"
      :style="sheetStyle"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
      @touchcancel="onTouchEnd"
    >
      <view class="sheet-grabber" />
      <view class="sheet-head">
        <text class="sheet-title">登录认证</text>
        <view class="sheet-close" @tap="hide" aria-label="关闭">
          <IconSvg name="close" :size="36" color="var(--text-tertiary)" />
        </view>
      </view>

      <scroll-view class="sheet-body" scroll-y>
        <AuthForm :codeCountdown="codeCooldown" @cooldown-change="onCooldownChange" />
      </scroll-view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from 'vue'
import { storeToRefs } from 'pinia'
import IconSvg from '@/components/IconSvg.vue'
import AuthForm from '@/components/AuthForm.vue'
import { useAuthSheetStore } from '@/stores/authSheet'
import { useUserStore } from '@/stores/user'

const authSheetStore = useAuthSheetStore()
const userStore = useUserStore()

// 必须用 storeToRefs 保持响应性（直接解构会丢失更新，弹层永不显示）
const { visible } = storeToRefs(authSheetStore)

function noop() {}

// N08 修复：在 AuthSheet 层持有发码冷却状态（与 AuthForm 同步）。
// 弹层用 v-if 关闭会卸载 AuthForm，故把冷却值提升到本层，重开时回填，前端不辅助绕过 60s 冷却。
const codeCooldown = ref(0)
function onCooldownChange(v: number) {
  codeCooldown.value = v
}

/** 抽屉开合状态（遮罩淡入 + sheet 上滑，与 ApplySheet 动画范式一致） */
const sheetOpen = ref(false)
const maskShow = ref(false)
const dragOffset = ref(0)
const dragging = ref(false)

const sheetStyle = computed(() => ({
  transform: `translateY(calc(${sheetOpen.value ? 0 : 100}% + ${dragging.value ? dragOffset.value : 0}px))`,
  transition: dragging.value ? 'none' : 'transform 0.3s cubic-bezier(0.32, 0.72, 0, 1)',
}))

function hide() {
  authSheetStore.hide()
}

// 认证成功（登录态建立）后关闭弹层并执行认证前记录的待办（跳转到目标功能）
watch(
  () => userStore.isLoggedIn(),
  (v) => {
    if (v) authSheetStore.runPending()
  },
)

// 弹层显隐 → 开合动画
watch(visible, (v) => {
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

/** 下拉关闭手势（与 ApplySheet 手感一致：1:1 跟随 + 速度投影，松手速度 >480 或位移 >120 关闭） */
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
  if (velocity > 480 || dragOffset.value > 120) hide()
  dragOffset.value = 0
}
</script>

<style scoped>
.auth-root { z-index: 300; }
/* 遮罩：与 ApplySheet 一致（--overlay-scrim 半透明，opacity 过渡） */
.sheet-mask {
  position: fixed; inset: 0; background: var(--overlay-scrim);
  opacity: 0; transition: opacity 0.3s ease; z-index: 290;
}
.sheet-mask.show { opacity: 1; }

/* 底部弹层：统一底部抽屉规范（radius-modal 顶部圆角 + shadow-modal + translateY 抽屉） */
.bottom-sheet {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 300;
  transform: translateY(100%);
  display: flex;
  flex-direction: column;
  max-height: 88vh;
  overflow: hidden;
  padding-bottom: env(safe-area-inset-bottom);
  will-change: transform;
}
.bottom-sheet.open { transform: translateY(0); }

/* 顶部小横条：与 ApplySheet 同款（72×8、999rpx、--overlay-dark-soft 半透明深色） */
.sheet-grabber { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--overlay-dark-soft); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }

/* 头部：标题 + 关闭，底部分隔线（与 ApplySheet sheet-head 一致） */
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); flex-shrink: 0; }
.sheet-title { font-size: var(--font-h3); font-weight: var(--weight-bold); color: var(--text-primary); }
.sheet-close { padding: 0 var(--spacing-xs); }

/* 滚动内容区：表单内部布局由 AuthForm 承担，此处只负责滚动与底部安全区 */
.sheet-body { flex: 1; overflow-y: auto; padding: var(--spacing-md) var(--spacing-lg) calc(var(--spacing-lg) + env(safe-area-inset-bottom)); box-sizing: border-box; }

@media (prefers-reduced-motion: reduce) {
  .sheet-mask { transition: opacity 0.2s ease; }
  .bottom-sheet { transition: opacity 0.2s ease; transform: none !important; }
}
</style>
