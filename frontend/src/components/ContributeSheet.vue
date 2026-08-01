<template>
  <view class="contribute-root">
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
        <text class="sheet-title">我要贡献</text>
        <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" @tap="requestClose" />
      </view>

      <view class="sheet-options">
        <view
          v-for="opt in options"
          :key="opt.key"
          class="sheet-option"
          :class="{ pressed: pressedKey === opt.key }"
          @touchstart="pressedKey = opt.key"
          @touchend="pressedKey = ''"
          @touchcancel="pressedKey = ''"
          @mousedown="pressedKey = opt.key"
          @mouseup="pressedKey = ''"
          @mouseleave="pressedKey = ''"
          @tap="pick(opt.key)"
        >
          <IconSvg :name="opt.icon" :size="36" color="var(--text-secondary)" class="sheet-option-icon" />
          <view class="sheet-option-body">
            <text class="sheet-option-title">{{ opt.title }}</text>
            <text class="sheet-option-sub">{{ opt.sub }}</text>
          </view>
          <IconSvg name="arrow-left" :size="28" color="var(--text-tertiary)" class="sheet-option-arrow" />
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import IconSvg from '@/components/IconSvg.vue'

export type ContributeOption = 'publishDish' | 'submitStall' | 'submitCanteen' | 'apply'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'pick', key: ContributeOption): void
}>()

const options: { key: ContributeOption; icon: string; title: string; sub: string }[] = [
  { key: 'publishDish', icon: 'dish', title: '发布菜品', sub: '新增一道菜品供大家发现' },
  { key: 'submitStall', icon: 'location', title: '提交档口', sub: '新增你常去的档口' },
  { key: 'submitCanteen', icon: 'home', title: '提交食堂', sub: '新增一个食堂（如新校区）' },
  { key: 'apply', icon: 'edit', title: '申请下架 / 纠错', sub: '对已存在菜品·档口·食堂发起申请' },
]

const sheetOpen = ref(false)
const maskShow = ref(false)
const dragOffset = ref(0)
const dragging = ref(false)
const pressedKey = ref('')

const sheetStyle = computed(() => ({
  transform: `translateY(calc(${sheetOpen.value ? 0 : 100}% + ${dragging.value ? dragOffset.value : 0}px))`,
  transition: dragging.value ? 'none' : 'transform 0.3s cubic-bezier(0.32, 0.72, 0, 1)',
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

function requestClose() {
  emit('update:open', false)
}

function pick(key: ContributeOption) {
  emit('pick', key)
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
.contribute-root { z-index: 100; }
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

.sheet-options { padding: var(--spacing-sm) 0; }
.sheet-option {
  display: flex; align-items: center; gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  transition: background 0.15s ease, transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.sheet-option.pressed { background: var(--bg-soft); transform: scale(0.99); }
.sheet-option-icon { flex-shrink: 0; }
.sheet-option-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.sheet-option-title { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); }
.sheet-option-sub { font-size: var(--font-aux); color: var(--text-tertiary); }
.sheet-option-arrow { flex-shrink: 0; transform: rotate(180deg); }

@media (prefers-reduced-motion: reduce) {
  .sheet-mask { transition: opacity 0.2s ease; }
  .bottom-sheet { transition: opacity 0.2s ease; transform: none !important; }
}
</style>
