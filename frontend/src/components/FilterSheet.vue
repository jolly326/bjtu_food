<template>
  <view class="filter-sheet-root">
    <!-- 遮罩：点击关闭；spring 0.8/0.3 + 手势中断 -->
    <view
      v-if="open"
      class="sheet-mask"
      :class="{ show: maskShow }"
      @tap="requestClose"
    />

    <view
      class="filter-sheet"
      :class="{ open: sheetOpen }"
      :style="sheetStyle"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
      @touchcancel="onTouchEnd"
    >
      <view class="sheet-grabber" />
      <view class="sheet-head">
        <text class="sheet-title">筛选</text>
        <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" @click="requestClose" />
      </view>

      <scroll-view class="sheet-body" scroll-y>
        <!-- 食堂 -->
        <view class="sheet-section">
          <text class="sheet-label">食堂</text>
          <view class="chip-wrap">
            <view
              v-for="c in canteenList"
              :key="c"
              class="chip"
              :class="{ active: innerCanteen === c }"
              @tap="innerCanteen = innerCanteen === c ? '' : c"
            >{{ c }}</view>
          </view>
        </view>

        <!-- 价格区间 -->
        <view class="sheet-section">
          <text class="sheet-label">价格区间（元）</text>
          <view class="price-row">
            <input class="price-input" type="digit" v-model="innerPriceMin" placeholder="最低" />
            <text class="price-dash">—</text>
            <input class="price-input" type="digit" v-model="innerPriceMax" placeholder="最高" />
          </view>
        </view>

        <!-- 口味 / 品类 -->
        <view class="sheet-section">
          <text class="sheet-label">口味 / 品类</text>
          <view class="chip-wrap">
            <view
              v-for="cat in categoryList"
              :key="cat.key"
              class="chip"
              :class="{ active: innerTag === cat.key }"
              @tap="innerTag = innerTag === cat.key ? '' : cat.key"
            >{{ cat.label }}</view>
          </view>
        </view>

        <!-- 辣度 -->
        <view class="sheet-section">
          <text class="sheet-label">辣度</text>
          <view class="seg-wrap">
            <view
              v-for="opt in spiceOptions"
              :key="opt.value"
              class="seg-item"
              :class="{ active: innerSpiceLevel === opt.value }"
              @tap="innerSpiceLevel = opt.value"
            >{{ opt.label }}</view>
          </view>
        </view>
      </scroll-view>

      <view class="sheet-footer">
        <view class="sheet-reset" @tap="onReset">重置</view>
        <view class="sheet-apply" @tap="onApply">查看结果</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import IconSvg from '@/components/IconSvg.vue'

export interface FilterSheetState {
  canteen: string
  tag: string
  priceMin: string
  priceMax: string
  /** 辣度：-1 表示不限，0=不辣 1=微辣 2=中辣 3=重辣 */
  spiceLevel: number
}

const props = defineProps<{
  open: boolean
  canteenList: string[]
  categoryList: { key: string; label: string }[]
  modelValue: FilterSheetState
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'apply', state: FilterSheetState): void
  (e: 'reset'): void
}>()

// 内部可编辑态（草稿），打开时从 modelValue 同步
const inner = reactive<FilterSheetState>({ canteen: '', tag: '', priceMin: '', priceMax: '', spiceLevel: -1 })
const innerCanteen = computed({
  get: () => inner.canteen,
  set: (v: string) => (inner.canteen = v),
})
const innerTag = computed({
  get: () => inner.tag,
  set: (v: string) => (inner.tag = v),
})
const innerPriceMin = computed({
  get: () => inner.priceMin,
  set: (v: string) => (inner.priceMin = v),
})
const innerPriceMax = computed({
  get: () => inner.priceMax,
  set: (v: string) => (inner.priceMax = v),
})
const innerSpiceLevel = computed({
  get: () => inner.spiceLevel,
  set: (v: number) => (inner.spiceLevel = v),
})

// 辣度选项（文案按后端契约：0 不辣 / 1 微辣 / 2 中辣 / 3 重辣；-1 不限）
const spiceOptions = [
  { value: -1, label: '不限' },
  { value: 0, label: '不辣' },
  { value: 1, label: '微辣' },
  { value: 2, label: '中辣' },
  { value: 3, label: '重辣' },
]

const sheetOpen = ref(false)
const maskShow = ref(false)
const dragOffset = ref(0)
const dragging = ref(false)

const sheetStyle = computed(() => ({
  transform: `translateY(calc(${sheetOpen.value ? 0 : 100}% + ${dragging.value ? dragOffset.value : 0}px))`,
  // 抽屉 / sheet：damping 0.8 / response 0.3（apple-design 表）
  transition: dragging.value
    ? 'none'
    : 'transform 0.3s cubic-bezier(0.32, 0.72, 0, 1)',
}))

watch(() => props.open, (v) => {
  if (v) {
    // 同步草稿
    inner.canteen = props.modelValue.canteen
    inner.tag = props.modelValue.tag
    inner.priceMin = props.modelValue.priceMin
    inner.priceMax = props.modelValue.priceMax
    inner.spiceLevel = props.modelValue.spiceLevel
    requestAnimationFrame(() => {
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

function onReset() {
  inner.canteen = ''
  inner.tag = ''
  inner.priceMin = ''
  inner.priceMax = ''
  inner.spiceLevel = -1
  emit('reset')
}

function onApply() {
  emit('apply', {
    canteen: inner.canteen,
    tag: inner.tag,
    priceMin: inner.priceMin,
    priceMax: inner.priceMax,
    spiceLevel: inner.spiceLevel,
  })
  emit('update:open', false)
}

// ── 手势中断：下拉关闭，可中途反向取消 ──
let startY = 0
function onTouchStart(e: any) {
  startY = e.touches?.[0]?.clientY ?? 0
  dragging.value = true
}
function onTouchMove(e: any) {
  if (!dragging.value) return
  const y = e.touches?.[0]?.clientY ?? 0
  const delta = y - startY
  // 仅允许向下拖拽（向上为负则回弹吸附）
  dragOffset.value = delta > 0 ? delta : 0
}
function onTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  const threshold = 120
  if (dragOffset.value > threshold) {
    requestClose()
  }
  dragOffset.value = 0
}
</script>

<style scoped>
.filter-sheet-root { z-index: 100; }
.sheet-mask {
  position: fixed;
  inset: 0;
  background: var(--overlay-scrim);
  opacity: 0;
  transition: opacity 0.3s ease;
  z-index: 90;
}
.sheet-mask.show { opacity: 1; }

.filter-sheet {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 100;
  transform: translateY(100%);
  display: flex;
  flex-direction: column;
  max-height: 80vh;
  padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom));
  will-change: transform;
}
.filter-sheet.open { transform: translateY(0); }

.sheet-grabber {
  width: 72rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: var(--border-color);
  margin: var(--spacing-sm) auto 0;
  flex-shrink: 0;
}
.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md);
  border-bottom: 2rpx solid var(--border-color);
}
.sheet-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); }
.sheet-close { padding: 0 var(--spacing-xs); }

.sheet-body { flex: 1; overflow-y: auto; padding: var(--spacing-md); }
.sheet-section { margin-bottom: var(--spacing-lg); }
.sheet-label { display: block; font-size: var(--font-body); font-weight: 600; color: var(--text-primary); margin-bottom: var(--spacing-sm); }
.chip-wrap { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.chip {
  padding: var(--spacing-xs) var(--spacing-md);
  border-radius: var(--radius-tag);
  font-size: var(--font-aux);
  background: var(--bg-placeholder);
  color: var(--text-secondary);
  -webkit-tap-highlight-color: transparent;
}
.chip.active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: 700; }
.price-row { display: flex; align-items: center; gap: var(--spacing-sm); }
.price-input {
  flex: 1; height: 72rpx; background: var(--bg-soft); border-radius: var(--radius-btn);
  padding: 0 var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); text-align: center;
}
.price-dash { color: var(--text-tertiary); }
.seg-wrap { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.seg-item {
  padding: var(--spacing-xs) var(--spacing-md);
  border-radius: var(--radius-tag);
  font-size: var(--font-aux);
  background: var(--bg-placeholder);
  color: var(--text-secondary);
  -webkit-tap-highlight-color: transparent;
}
.seg-item.active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: 700; }
.sheet-footer { display: flex; gap: var(--spacing-md); padding: var(--spacing-md); border-top: 2rpx solid var(--border-color); }
.sheet-reset {
  flex: 1; height: 88rpx; display: flex; align-items: center; justify-content: center;
  border-radius: var(--radius-btn); background: var(--bg-soft); color: var(--text-secondary);
  font-weight: 600; -webkit-tap-highlight-color: transparent;
}
.sheet-apply {
  flex: 2; height: 88rpx; display: flex; align-items: center; justify-content: center;
  border-radius: var(--radius-btn); background: var(--color-primary); color: var(--text-white);
  font-weight: 700; -webkit-tap-highlight-color: transparent;
}

@media (prefers-reduced-motion: reduce) {
  .sheet-mask { transition: opacity 0.2s ease; }
  .filter-sheet { transition: opacity 0.2s ease; transform: none !important; }
}
</style>
