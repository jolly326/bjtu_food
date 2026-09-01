<template>
  <!-- 价格筛选下拉：米色面板，从筛选条向下展开、与米色页面/筛选区无缝衔接（与 CanteenFilter 同款）。
       必须挂在 scroll-view 之外（小程序 scroll-view 内 absolute 层级会被裁剪）。 -->
  <view v-if="open" class="ps-root">
    <!-- 遮罩：自筛选条底部向下铺满，承接面板外点击关闭；下方内容轻微压暗 -->
    <view class="ps-mask" :class="{ show: maskShow }" @tap="close" />
    <!-- 米色面板：紧贴筛选条向下展开（非红非白，与筛选区/页面统一） -->
    <view class="ps-panel" :class="{ 'theme-dark': theme.isDark, open: panelOpen }">
      <view class="ps-title">价格区间</view>

      <view class="ps-presets">
        <view
          v-for="opt in presets"
          :key="opt.key"
          class="ps-preset press"
          :class="{ active: activeKey === opt.key }"
          hover-class="pressed"
          @tap="pickPreset(opt.key)"
        >
          <text class="ps-name">{{ opt.label }}</text>
          <IconSvg v-if="activeKey === opt.key" name="check" :size="28" color="var(--color-primary)" />
        </view>
      </view>

      <view class="ps-custom">
        <view class="ps-custom-title">自定义（元）</view>
        <view class="ps-inputs">
          <input
            class="ps-input"
            type="digit"
            placeholder="最低"
            placeholder-class="ps-ph"
            :value="draftMin"
            @input="onMinInput"
          />
          <text class="ps-tilde">~</text>
          <input
            class="ps-input"
            type="digit"
            placeholder="最高"
            placeholder-class="ps-ph"
            :value="draftMax"
            @input="onMaxInput"
          />
        </view>
      </view>

      <view class="ps-actions">
        <view class="ps-btn ps-reset press" hover-class="pressed" @tap="onReset">
          <text class="ps-btn-text">重置</text>
        </view>
        <view class="ps-btn ps-confirm press" hover-class="pressed" @tap="onConfirm">
          <text class="ps-btn-text">确定</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useThemeStore } from '@/stores/theme'
import IconSvg from '@/components/IconSvg.vue'
import { fenToYuan, yuanToFen } from '@/utils/money'

const props = withDefaults(defineProps<{
  /** 面板显隐（受控，由父级 v-if 承载实际挂载） */
  open: boolean
  /** 当前生效价格区间（分），用于回显选中态 */
  current?: { min?: number; max?: number }
}>(), {
  current: () => ({}),
})

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'select', range: { min?: number; max?: number }): void
}>()

const theme = useThemeStore()

const maskShow = ref(false)
const panelOpen = ref(false)

watch(() => props.open, (v) => {
  if (v) {
    nextTick(() => {
      maskShow.value = true
      panelOpen.value = true
    })
  } else {
    maskShow.value = false
    panelOpen.value = false
  }
})

// 预设：不限 / 0–10 / 10–20 / 20 元以上（元→分，金额换算仅在组件内、不裸算于页面）
const presets = [
  { key: 'all', label: '不限', min: undefined, max: undefined },
  { key: '0-10', label: '0–10 元', min: 0, max: 1000 },
  { key: '10-20', label: '10–20 元', min: 1000, max: 2000 },
  { key: '20+', label: '20 元以上', min: 2000, max: undefined },
] as const

type PresetKey = (typeof presets)[number]['key']

// 当前选中预设（按区间匹配；自定义不匹配任何预设 → activeKey=''）
const activeKey = computed<PresetKey | ''>(() => {
  const v = props.current
  const hit = presets.find((p) => p.min === v.min && p.max === v.max)
  return hit ? hit.key : ''
})

// 自定义草稿（元，字符串态避免输入过程抖动）
const draftMin = ref(vModelMin())
const draftMax = ref(vModelMax())

function vModelMin(): string {
  const m = props.current.min
  if (m === undefined) return ''
  return String(fenToYuan(m))
}
function vModelMax(): string {
  const m = props.current.max
  if (m === undefined) return ''
  return String(fenToYuan(m))
}

function onMinInput(e: any) {
  draftMin.value = e.detail.value
}
function onMaxInput(e: any) {
  draftMax.value = e.detail.value
}

function close() {
  emit('update:open', false)
}

function pickPreset(key: PresetKey) {
  const opt = presets.find((p) => p.key === key)!
  // 选预设时清空自定义草稿，保证单选态一致
  draftMin.value = ''
  draftMax.value = ''
  close()
  emit('select', { min: opt.min, max: opt.max })
}

/** 输入串（元）→ 分：空串 / 非法值返回 undefined（表示不限）。
 *  ×100 主体仍走 utils/money 的 yuanToFen，此处只负责「输入串 → 数字」的解析与边界兜底。 */
function toFen(v: string): number | undefined {
  if (v === '') return undefined
  const n = Number(v)
  if (!Number.isFinite(n)) return undefined
  return yuanToFen(n)
}

function onConfirm() {
  let min = toFen(draftMin.value)
  let max = toFen(draftMax.value)
  // 边界：min>max 时自动纠正为区间（取较小值为下界）
  if (min !== undefined && max !== undefined && min > max) {
    const t = min
    min = max
    max = t
  }
  close()
  emit('select', { min, max })
}

function onReset() {
  draftMin.value = ''
  draftMax.value = ''
  close()
  emit('select', {})
}
</script>

<style scoped lang="scss">
.ps-root {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 90;
}
/* 遮罩：自筛选条底部向下铺满，承接面板外点击关闭；仅透明度交叉淡入（红线 §4.9） */
.ps-mask {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 100vh;
  background: var(--overlay-scrim);
  opacity: 0;
  transition: opacity var(--duration-base) var(--ease-out);
}
.ps-mask.show { opacity: 1; }
/* 米色面板：紧贴筛选条向下展开，与米色页面/筛选区无缝衔接（非红非白） */
.ps-panel {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  background: var(--bg-page);
  color: var(--text-primary);
  padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + env(safe-area-inset-bottom));
  box-shadow: var(--shadow-card);
  /* opacity 交叉淡入 + ≤8px 轻位移（红线 §4.9：无弹性过冲，引用动效 token） */
  opacity: 0;
  transform: translateY(-8px);
  transition: opacity var(--duration-base) var(--ease-out), transform var(--duration-base) var(--ease-out-soft);
}
.ps-panel.open { opacity: 1; transform: translateY(0); }
.ps-title {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  padding: var(--spacing-xs) var(--spacing-sm) var(--spacing-md);
}
.ps-presets {
  display: flex;
  flex-direction: column;
}
.ps-preset {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-sm);
  border-radius: var(--radius-card);
  -webkit-tap-highlight-color: transparent;
}
.ps-preset.active {
  background: var(--bg-soft);
}
.ps-name {
  font-size: var(--font-subtitle);
  color: var(--text-primary);
  min-width: 0;
}
.ps-custom {
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-md);
  border-top: 1rpx solid var(--border-color);
}
.ps-custom-title {
  font-size: var(--font-body);
  color: var(--text-secondary);
  padding: 0 var(--spacing-sm) var(--spacing-sm);
}
.ps-inputs {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 0 var(--spacing-sm);
}
.ps-input {
  flex: 1;
  min-width: 0;
  height: var(--control-h, 72rpx);
  padding: 0 var(--spacing-md);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
  color: var(--text-primary);
  font-size: var(--font-body);
}
.ps-ph {
  color: var(--text-hint);
}
.ps-tilde {
  color: var(--text-secondary);
  font-size: var(--font-body);
}
.ps-actions {
  display: flex;
  gap: var(--spacing-md);
  margin-top: var(--spacing-lg);
}
.ps-btn {
  flex: 1;
  height: var(--action-h, 80rpx);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-pill);
  -webkit-tap-highlight-color: transparent;
}
.ps-reset {
  background: var(--bg-soft);
}
.ps-reset .ps-btn-text {
  color: var(--text-secondary);
}
.ps-confirm {
  background: var(--color-primary);
}
.ps-confirm .ps-btn-text {
  color: var(--color-on-primary-surface);
}
.ps-btn-text {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
}

@media (prefers-reduced-motion: reduce) {
  .ps-mask { transition: opacity 0.2s ease; }
  .ps-panel { transition: opacity 0.2s ease; transform: none; }
  .ps-preset:active,
  .ps-btn:active { transform: none; }
}
</style>
