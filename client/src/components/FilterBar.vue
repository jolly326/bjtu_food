<template>
  <!-- 筛选栏（client-filter-bar-consolidation 合并版）：
       单行 = 左「全部食堂」+「全部价格」两按钮均分剩余空间，最右常驻筛选 icon（无跳转）。
       两表单互斥：由单一 activePanel 驱动，任意时刻最多一个展开、最多一个按钮激活（红）。 -->
  <view
    class="fb-row"
    :style="{ '--capsule-h': capsuleHeight + 'px' }"
  >
    <!-- 左组：食堂 + 价格 两按钮（独占剩余空间、可收缩，长文案以 … 省略） -->
    <view class="fb-chips">
      <!-- 食堂按钮：单击展开、双击关闭；仅展开时红底，箭头随之翻转 -->
      <view
        class="fb-chip"
        :class="{ active: activePanel === 'canteen' }"
        @tap="onChipTap('canteen')"
        role="button"
        :aria-label="canteenLabel"
      >
        <IconSvg class="fb-chip-icon" name="dish" :size="'18px'" :color="activePanel === 'canteen' ? 'var(--color-on-primary)' : 'var(--text-secondary)'" />
        <text class="fb-chip-text">{{ canteenLabel }}</text>
        <IconSvg class="fb-chip-icon" :name="activePanel === 'canteen' ? 'arrow-up' : 'arrow-down'" :size="'16px'" :color="activePanel === 'canteen' ? 'var(--color-on-primary)' : 'var(--text-tertiary)'" />
      </view>

      <!-- 价格按钮：与食堂按钮同款交互；收起后文案回显所选区间（元） -->
      <view
        class="fb-chip"
        :class="{ active: activePanel === 'price' }"
        @tap="onChipTap('price')"
        role="button"
        :aria-label="`价格：${priceLabel}`"
      >
        <IconSvg class="fb-chip-icon" name="price" :size="'18px'" :color="activePanel === 'price' ? 'var(--color-on-primary)' : 'var(--text-secondary)'" />
        <text class="fb-chip-text">{{ priceLabel }}</text>
        <IconSvg class="fb-chip-icon" :name="activePanel === 'price' ? 'arrow-up' : 'arrow-down'" :size="'16px'" :color="activePanel === 'price' ? 'var(--color-on-primary)' : 'var(--text-tertiary)'" />
      </view>
    </view>

    <!-- 筛选 icon：恒在最右（左组 flex:1 吃掉剩余空间）；按产品要求不挂跳转、不参与表单展开 -->
    <view
      class="fb-icon-btn"
      role="button"
      aria-label="详细筛选"
    >
      <IconSvg name="filter" :size="'20px'" color="var(--text-secondary)" />
    </view>

    <!-- ===== 食堂下拉：红色背景面板，与 header 同一红色块；点击面板外遮罩关闭 ===== -->
    <view v-if="activePanel === 'canteen'" class="cf-mask" @tap="closePanel">
      <view class="cf-panel" @tap.stop>
        <view class="cf-title">选择食堂</view>
        <scroll-view scroll-y class="cf-list">
          <view
            class="cf-item press"
            :class="{ active: selectedCanteenId === null }"
            hover-class="pressed"
            @tap="selectCanteen(null)"
          >
            <text class="cf-name">全部</text>
            <IconSvg v-if="selectedCanteenId === null" name="check" :size="32" color="var(--color-primary)" />
          </view>
          <view
            v-for="c in canteens"
            :key="c.id"
            class="cf-item press"
            :class="{ active: selectedCanteenId === c.id }"
            hover-class="pressed"
            @tap="selectCanteen(c.id ?? null)"
          >
            <text class="cf-name">{{ c.name }}</text>
            <IconSvg v-if="selectedCanteenId === c.id" name="check" :size="32" color="var(--color-primary)" />
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- ===== 价格弹层：米色面板，从筛选条向下展开 =====
         必须挂在 scroll-view 之外（小程序 scroll-view 内 absolute 层级会被裁剪）。 -->
    <view v-if="activePanel === 'price'" class="ps-root">
      <!-- 遮罩：自筛选条底部向下铺满，承接面板外点击关闭；下方内容轻微压暗 -->
      <view class="ps-mask" :class="{ show: maskShow }" @tap="closePanel" />
      <!-- 米色面板：紧贴筛选条向下展开（非红非白，与筛选区/页面统一） -->
      <view class="ps-panel" :class="{ open: panelOpen }">
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
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'

import IconSvg from './IconSvg.vue'
import { fenToYuan, yuanToFen } from '@/utils/money'
import type { CanteenInfo } from '@/types/canteen'

const props = withDefaults(defineProps<{
  /** 食堂列表（下拉数据源，同时用于按 id 回显食堂名） */
  canteens: CanteenInfo[]
  /** 当前选中食堂 id（受控；null = 全部） */
  selectedCanteenId: number | null
  /** 当前价格区间（受控，单位：分） */
  priceRange?: { min?: number; max?: number }
  /** 胶囊高度（px），对齐原生胶囊/搜索框高度；缺省回退 36px */
  capsuleHeight?: number
}>(), {
  priceRange: () => ({}),
  capsuleHeight: 36,
})

const emit = defineEmits<{
  (e: 'canteen-select', id: number | null): void
  (e: 'price-select', range: { min?: number; max?: number }): void
}>()


/**
 * 当前展开的表单（单一真相源）。
 * 取代原 showFilter/showPrice 双布尔：单值天然保证「两表单互斥、最多一个展开、最多一个按钮激活」，
 * 切换按钮时「先收起前一个再展开后一个」由状态本身成立，无需写互斥分支。
 */
const activePanel = ref<'canteen' | 'price' | null>(null)

function closePanel() {
  activePanel.value = null
}

/** 双击（double tap）识别阈值：两次 tap 间隔小于该值视为双击 → 关闭表单 */
const DOUBLE_TAP_MS = 300
let lastTapPanel: 'canteen' | 'price' | null = null
let lastTapAt = 0

/**
 * 按钮点击语义：单击展开，双击关闭。
 * - 单击：activePanel 置为该按钮（已展开时保持展开，不 toggle 收起）
 * - 双击：activePanel 置 null（收起）
 * 切换按钮时因 activePanel 为单值，前一个表单自动让位，两表单不重叠覆盖。
 */
function onChipTap(panel: 'canteen' | 'price') {
  const now = Date.now()
  const isDoubleTap = lastTapPanel === panel && now - lastTapAt < DOUBLE_TAP_MS
  lastTapPanel = panel
  lastTapAt = now
  activePanel.value = isDoubleTap ? null : panel
}

// ===== 文案内化计算（页面不再各自算，杜绝「分当元」在两页重现） =====

/** 选中食堂名（按受控 id 从 canteens 查）；未选返回空串由 canteenLabel 兜底为「全部食堂」 */
const selectedCanteenName = computed(
  () => props.canteens.find((c) => c.id === props.selectedCanteenId)?.name || '',
)
const canteenLabel = computed(() => selectedCanteenName.value || '全部食堂')

/** 价格胶囊文案：priceRange 单位为「分」，展示转「元」统一走 fenToYuan（红线：禁止裸算 /100） */
const priceLabel = computed(() => {
  const p = props.priceRange
  if (p.min == null && p.max == null) return '全部价格'
  if (p.min != null && p.max == null) return `${fenToYuan(p.min)} 元以上`
  if (p.min == null && p.max != null) return `${fenToYuan(p.max)} 元以下`
  return `${fenToYuan(p.min)}-${fenToYuan(p.max)} 元`
})

// ===== 食堂下拉 =====

function selectCanteen(id: number | null) {
  closePanel()
  emit('canteen-select', id)
}

// ===== 价格弹层 =====

// 遮罩/面板过渡态：挂载后下一帧置位，使 opacity 过渡生效
const maskShow = ref(false)
const panelOpen = ref(false)
watch(activePanel, (v) => {
  if (v === 'price') {
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
  const v = props.priceRange
  const hit = presets.find((p) => p.min === v.min && p.max === v.max)
  return hit ? hit.key : ''
})

// 自定义草稿（元，字符串态避免输入过程抖动）
const draftMin = ref(vModelMin())
const draftMax = ref(vModelMax())

function vModelMin(): string {
  const m = props.priceRange.min
  if (m === undefined) return ''
  return String(fenToYuan(m))
}
function vModelMax(): string {
  const m = props.priceRange.max
  if (m === undefined) return ''
  return String(fenToYuan(m))
}

function onMinInput(e: any) {
  draftMin.value = e.detail.value
}
function onMaxInput(e: any) {
  draftMax.value = e.detail.value
}

function pickPreset(key: PresetKey) {
  const opt = presets.find((p) => p.key === key)!
  // 选预设时清空自定义草稿，保证单选态一致
  draftMin.value = ''
  draftMax.value = ''
  closePanel()
  emit('price-select', { min: opt.min, max: opt.max })
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
  closePanel()
  emit('price-select', { min, max })
}

function onReset() {
  draftMin.value = ''
  draftMax.value = ''
  closePanel()
  emit('price-select', {})
}
</script>

<style scoped lang="scss">
/* ===== 筛选行 ===== */
/* 筛选行：左侧两颗按钮（食堂/价格），筛选 icon 常驻最右 */
.fb-row {
  display: flex;
  align-items: center;
  /* ⚠️ flex:1 不可移除：本组件被 .filter-bar / .find-filter-row（均为 display:flex）包裹，
     作为 flex item 默认 flex:0 1 auto → 宽度只按内容收缩、不撑满父级，
     此时 .fb-icon-btn 的 margin-left:auto 没有任何剩余空间可分配，
     筛选 icon 会紧贴价格按钮右侧而不是靠右。必须撑满，auto 外边距才生效。 */
  flex: 1;
  min-width: 0;
  padding: 0;
  box-sizing: border-box;
  gap: var(--spacing-sm);
}
/* 左组（食堂 + 价格）：独占全部剩余空间（flex:1），把右侧筛选 icon 顶到最右。
   组内两颗按钮 flex:1 平均分左区空间，超长食堂名仅在其内部省略（min-width:0），
   既均分又不挤出右侧 icon。 */
.fb-chips {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  gap: var(--spacing-sm);
}
.fb-chip {
  /* ⚠️ flex:1 均分左区：两按钮各占一半剩余空间，平均分配左侧区域。
     min-width:0 保证长食堂名只在其内部省略，不会把右侧筛选 icon 挤出屏幕。 */
  flex: 1;
  min-width: 0;
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: var(--capsule-h, 36px);
  padding: 0 var(--spacing-md);
  /* 抬起控制件：白底从米色筛选条中浮起 */
  background: var(--bg-card);
  border-radius: var(--radius-pill);
  -webkit-tap-highlight-color: transparent;
}
/* 无障碍：按钮高度 < 44px 热区，透明 ::after 扩展命中区域至 ≈44px */
.fb-chip::after {
  content: '';
  position: absolute;
  top: -12rpx;
  bottom: -12rpx;
  left: 0;
  right: 0;
}
/* 按钮内图标不参与收缩，让省略号只截文字，避免图标被压扁 */
.fb-chip-icon { flex-shrink: 0; }
/* 展开态：主色底白字（收起后即恢复白底，选中值只由文案回显） */
.fb-chip.active { background: var(--color-primary); }
.fb-chip.active .fb-chip-text { color: var(--color-on-primary); }
.fb-chip-text {
  font-size: var(--font-body);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
/* 右侧筛选 icon 按钮：透明底，圆形热区 */
.fb-icon-btn {
  flex-shrink: 0;
  margin-left: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: var(--radius-circle);
  -webkit-tap-highlight-color: transparent;
}
.fb-icon-btn:active { background: var(--bg-soft); }

/* ===== 食堂下拉 ===== */
/* 遮罩：自 header 底部向下铺满，承接面板外点击关闭；下方内容轻微压暗 */
.cf-mask {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  /* 向下延伸一屏，覆盖首页内容区 */
  height: 100vh;
  background: var(--overlay-scrim);
  z-index: 90;
}
/* 面板：与米色筛选区/页面同色（非红非白），紧贴筛选条无间隙，亮/暗模式均无缝 */
.cf-panel {
  background: var(--bg-page);
  color: var(--text-primary);
  padding: var(--spacing-md) var(--spacing-md) calc(var(--spacing-md) + env(safe-area-inset-bottom));
  box-shadow: var(--shadow-card);
}
.cf-title {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  padding: var(--spacing-xs) var(--spacing-sm) var(--spacing-md);
}
.cf-list {
  max-height: 60vh;
}
.cf-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-sm);
  border-radius: var(--radius-card);
  -webkit-tap-highlight-color: transparent;
}
.cf-item.active {
  background: var(--bg-soft);
}
.cf-name {
  font-size: var(--font-subtitle);
  color: var(--text-primary);
  min-width: 0;
}

/* ===== 价格弹层 ===== */
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
  /* opacity 淡入（无位移/缩放，MVP 静态） */
  opacity: 0;
  transition: opacity var(--duration-base) var(--ease-out);
}
.ps-panel.open { opacity: 1; }
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
