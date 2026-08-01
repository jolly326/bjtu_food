<template>
  <view class="nickname-root">
    <view
      v-if="open"
      class="sheet-mask"
      :class="{ show: maskShow }"
      @tap="requestClose"
    />
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
        <text class="sheet-title">修改昵称</text>
        <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" @tap="requestClose" />
      </view>

      <view class="sheet-body">
        <input
          v-model="draft"
          class="sheet-input"
          placeholder="输入新昵称"
          maxlength="20"
          confirm-type="done"
          @confirm="confirm"
        />
      </view>

      <view class="sheet-footer">
        <view
          class="sheet-btn sheet-btn-cancel"
          :class="{ pressed: pressed === 'cancel' }"
          @touchstart="pressed = 'cancel'"
          @touchend="pressed = ''"
          @touchcancel="pressed = ''"
          @mousedown="pressed = 'cancel'"
          @mouseup="pressed = ''"
          @mouseleave="pressed = ''"
          @tap="requestClose"
        >
          <text class="sheet-btn-text">取消</text>
        </view>
        <view
          class="sheet-btn sheet-btn-confirm"
          :class="{ pressed: pressed === 'confirm' }"
          @touchstart="pressed = 'confirm'"
          @touchend="pressed = ''"
          @touchcancel="pressed = ''"
          @mousedown="pressed = 'confirm'"
          @mouseup="pressed = ''"
          @mouseleave="pressed = ''"
          @tap="confirm"
        >
          <text class="sheet-btn-text">确认</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import IconSvg from '@/components/IconSvg.vue'

const props = defineProps<{
  open: boolean
  /** 当前昵称（打开时回填） */
  value?: string
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'confirm', name: string): void
}>()

const draft = ref('')
const sheetOpen = ref(false)
const maskShow = ref(false)
const dragOffset = ref(0)
const dragging = ref(false)
const pressed = ref('')

const sheetStyle = computed(() => ({
  transform: `translateY(calc(${sheetOpen.value ? 0 : 100}% + ${dragging.value ? dragOffset.value : 0}px))`,
  transition: dragging.value ? 'none' : 'transform 0.3s cubic-bezier(0.32, 0.72, 0, 1)',
}))

watch(() => props.open, (v) => {
  if (v) {
    draft.value = props.value ?? ''
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

function requestClose() {
  emit('update:open', false)
}

function confirm() {
  emit('confirm', draft.value.trim())
}

// ── 手势中断：下拉关闭 ──
let startY = 0
function onTouchStart(e: any) {
  startY = e.touches?.[0]?.clientY ?? 0
  dragging.value = true
}
function onTouchMove(e: any) {
  if (!dragging.value) return
  const y = e.touches?.[0]?.clientY ?? 0
  const delta = y - startY
  dragOffset.value = delta > 0 ? delta : 0
}
function onTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  if (dragOffset.value > 120) requestClose()
  dragOffset.value = 0
}
</script>

<style scoped>
.nickname-root { z-index: 100; }
.sheet-mask {
  position: fixed; inset: 0; background: var(--overlay-scrim);
  opacity: 0; transition: opacity 0.3s ease; z-index: 90;
}
.sheet-mask.show { opacity: 1; }

.bottom-sheet {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 100;
  transform: translateY(100%);
  padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom));
  will-change: transform;
}
.bottom-sheet.open { transform: translateY(0); }

.sheet-grabber { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--border-color); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); }
.sheet-close { padding: 0 var(--spacing-xs); }

.sheet-body { padding: var(--spacing-lg) var(--spacing-md); }
.sheet-input {
  width: 100%;
  height: 88rpx;
  border: 2rpx solid var(--border-color);
  border-radius: var(--radius-card);
  padding: 0 var(--spacing-md);
  font-size: var(--font-body);
  color: var(--text-primary);
  background: var(--bg-soft);
  box-sizing: border-box;
}

.sheet-footer { display: flex; gap: var(--spacing-sm); padding: 0 var(--spacing-md); }
.sheet-btn {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-btn);
  transition: transform 0.12s ease, background 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}
.sheet-btn.pressed { transform: scale(var(--press-scale)); }
.sheet-btn-cancel { background: var(--bg-soft); }
.sheet-btn-confirm { background: var(--color-primary); }
.sheet-btn-text { font-size: var(--font-body); font-weight: 600; }
.sheet-btn-cancel .sheet-btn-text { color: var(--text-secondary); }
.sheet-btn-confirm .sheet-btn-text { color: var(--text-white); }

@media (prefers-reduced-motion: reduce) {
  .sheet-mask { transition: opacity 0.2s ease; }
  .bottom-sheet { transition: opacity 0.2s ease; transform: none !important; }
}
</style>
