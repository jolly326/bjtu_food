<template>
  <!-- 筛选栏（client-filter-bar-consolidation 合并版）：
       单行 = 左「全部食堂」+「全部价格」两按钮均分剩余空间，最右常驻筛选 icon（无跳转）。
       两表单互斥：由单一 activePanel 驱动，任意时刻最多一个展开、最多一个按钮激活（红）。 -->
  <view
    class="fb-row"
    :style="{ '--capsule-h': capsuleH + 'px' }"
  >
    <!-- 左组：食堂 + 价格 两按钮（独占剩余空间、可收缩，长文案以 … 省略） -->
    <view class="fb-chips">
      <!-- 食堂按钮：单击切换（展开 / 再次单击收起）；仅展开时红底，箭头随之翻转 -->
      <view
        class="fb-chip"
        :class="{ active: activePanel === 'canteen' }"
        @tap="onChipTap('canteen')"
        role="button"
        :aria-label="canteenLabel"
      >
        <IconSvg class="fb-chip-icon" name="canteen" :size="'18px'" :color="activePanel === 'canteen' ? 'var(--color-on-primary)' : 'var(--text-secondary)'" />
        <text class="fb-chip-text">{{ canteenLabel }}</text>
        <IconSvg class="fb-chip-icon" :name="activePanel === 'canteen' ? 'arrow-up' : 'arrow-down'" :size="'16px'" :color="activePanel === 'canteen' ? 'var(--color-on-primary)' : 'var(--text-secondary)'" />
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
        <IconSvg class="fb-chip-icon" :name="activePanel === 'price' ? 'arrow-up' : 'arrow-down'" :size="'16px'" :color="activePanel === 'price' ? 'var(--color-on-primary)' : 'var(--text-secondary)'" />
      </view>
    </view>

    <!-- 筛选控件：与食堂/价格同款白底圆角胶囊（「筛选」文字 + 线性图标）；恒在最右。
         仍不挂跳转、不参与面板展开（tab-pages-visual-refine-2） -->
    <view
      class="fb-chip fb-chip--more"
      role="button"
      aria-label="详细筛选"
    >
      <IconSvg class="fb-chip-icon" name="filter" :size="'18px'" color="var(--text-secondary)" />
      <text class="fb-chip-text">筛选</text>
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
            <IconSvg v-if="selectedCanteenId === null" name="check" :size="32" color="var(--color-on-primary)" />
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
            <IconSvg v-if="selectedCanteenId === c.id" name="check" :size="32" color="var(--color-on-primary)" />
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
            <IconSvg v-if="activeKey === opt.key" name="check" :size="28" color="var(--color-on-primary)" />
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
import type { CanteenInfo } from '@/types/canteen'
import { getCapsuleHeight, type MenuButtonRect } from '@/utils/navMetrics'

const props = withDefaults(defineProps<{
  /** 食堂列表（下拉数据源，同时用于按 id 回显食堂名） */
  canteens: CanteenInfo[]
  /** 当前选中食堂 id（受控；null = 全部） */
  selectedCanteenId: number | null
  /** 当前价格区间（受控，单位：元）；emit 与透传同为元，禁止二次换算 */
  priceRange?: { min?: number; max?: number }
  /** 胶囊高度（px）显式覆盖口；不传则组件内按 navMetrics.getCapsuleHeight 自取（与 AppHeader 同一真源，MP-017 修正此前硬编码 36 与该口径矛盾） */
  capsuleHeight?: number
}>(), {
  priceRange: () => ({}),
})

/**
 * 胶囊高度（px）：与 AppHeader 的搜索框同一真源 navMetrics.getCapsuleHeight——
 * 微信端读真实原生胶囊高度，H5 / 非微信端回退 32px。运行期恒定，setup 一次性解析即可。
 */
function resolveCapsuleHeight(): number {
  // @ts-ignore - 跨端兼容（H5 无 wx，回退默认胶囊高度）
  const mb: MenuButtonRect | null = (typeof wx !== 'undefined' && wx.getMenuButtonBoundingClientRect) ? wx.getMenuButtonBoundingClientRect() : null
  return getCapsuleHeight(mb)
}
const capsuleH = ref<number>(props.capsuleHeight ?? resolveCapsuleHeight())

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

/**
 * 按钮点击语义：单击切换（toggle）。
 * - 收起态（activePanel === null）：单击展开目标表单
 * - 已展开且为同一按钮：再次单击收起
 * - 已展开且为另一按钮：先置 null 收起当前表单，下一帧再展开目标表单
 *   （单值 activePanel 已天然保证互斥、最多一个表单展开；把两次状态变更拆到不同帧，
 *     才能让「先收起、再展开」成为用户可观察的先后次序，两表单不重叠覆盖）
 * 不依赖任何 double-tap 手势识别。
 */
function onChipTap(panel: 'canteen' | 'price') {
  if (activePanel.value === null) {
    activePanel.value = panel
  } else if (activePanel.value === panel) {
    activePanel.value = null
  } else {
    // 跨表单切换：先收起当前表单，下一帧再展开目标表单（先收后展）
    activePanel.value = null
    nextTick(() => {
      activePanel.value = panel
    })
  }
}

// ===== 文案内化计算（页面不再各自算，杜绝「分当元」在两页重现） =====

/** 选中食堂名（按受控 id 从 canteens 查）；未选返回空串由 canteenLabel 兜底为「全部食堂」 */
const selectedCanteenName = computed(
  () => props.canteens.find((c) => c.id === props.selectedCanteenId)?.name || '',
)
const canteenLabel = computed(() => selectedCanteenName.value || '全部食堂')

/** 金额展示归一：最多两位小数、去掉整数的多余尾零（10 显示「10」，4.5 显示「4.5」）；空值兜底空串 */
function formatYuan(v: number | null | undefined): string {
  if (v == null) return ''
  return String(Math.round(v * 100) / 100)
}

/** 价格胶囊文案：priceRange 单位为「元」，直接以元回显（红线：禁止裸算 /100、禁止二次换算） */
const priceLabel = computed(() => {
  const min = props.priceRange.min
  const max = props.priceRange.max
  if (min == null && max == null) return '全部价格'
  if (min != null && max == null) return `${formatYuan(min)} 元以上`
  if (min == null && max != null) return `${formatYuan(max)} 元以下`
  return `${formatYuan(min)}-${formatYuan(max)} 元`
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

// 预设：不限 / 0–10 / 10–20 / 20 元以上（单位：元，与 priceRange / emit 同为元口径，禁止换算）
const presets = [
  { key: 'all', label: '不限', min: undefined, max: undefined },
  { key: '0-10', label: '0–10 元', min: 0, max: 10 },
  { key: '10-20', label: '10–20 元', min: 10, max: 20 },
  { key: '20+', label: '20 元以上', min: 20, max: undefined },
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
  return String(m)
}
function vModelMax(): string {
  const m = props.priceRange.max
  if (m === undefined) return ''
  return String(m)
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

/** 输入串（元）→ 元数值：空串 / 非法值返回 undefined（表示不限）。单位即元，禁止任何 ×/÷ 换算。 */
function toYuan(v: string): number | undefined {
  if (v === '') return undefined
  const n = Number(v)
  if (!Number.isFinite(n)) return undefined
  return n
}

function onConfirm() {
  let min = toYuan(draftMin.value)
  let max = toYuan(draftMax.value)
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
/* 筛选行：左侧两颗按钮（食堂/价格），筛选 icon 常驻最右（两端对齐） */
.fb-row {
  display: flex;
  align-items: center;
  /* ⚠️ 本组件在小程序中是一个真实节点（<filter-bar>），其父 .filter-bar / .find-filter-row 为 flex 容器时，
     flex item 是宿主节点而非本行；宿主的撑满由**父级**的 .fb-host { flex:1; min-width:0 } 负责
     （见 home/index.vue 与 find/index.vue 的 .fb-host 规则），组件自身无法越权控制宿主。
     在此之上，flex:1 覆盖宿主为 flex 容器的情形、width:100% 覆盖宿主为 block 的情形，二者共同保证本行撑满宿主宽度——
     行若不撑满，则没有剩余空间可分配，space-between 与 auto 外边距都会失效，icon 会紧贴两颗按钮而非靠右。 */
  flex: 1;
  width: 100%;
  min-width: 0;
  /* 两端对齐：左组（.fb-chips）与右侧筛选 icon 分列两端，icon 恒定贴行最右 */
  justify-content: space-between;
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
  height: var(--capsule-h, 32px);
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
/* 最右「筛选」胶囊：复用 .fb-chip 白底圆角表面，但固定宽度不参与收缩/展开 */
.fb-chip--more {
  flex: 0 0 auto;
  min-width: auto;
  -webkit-tap-highlight-color: transparent;
}
.fb-chip--more:active { opacity: 0.7; }
.fb-chip--more .fb-chip-text { color: var(--text-secondary); }

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
  z-index: var(--z-filter-dropdown);
}
/* 面板：紧贴筛选条向下展开的下拉片，**不是浮空的孤立卡片**。
   - 满宽（无左右外边距）+ 顶边方角：与 .filter-bar / .find-filter-row 底边无缝衔接，
     视觉上从筛选条底部「长出来」，而不是一张漂在页面上的 card。
   - 底色与筛选条同面（--bg-page）、接缝处无任何 border：表单与 FilterBar 连成一体。
   - 底边圆角 + 极淡柔阴影：表达「向下展开的下拉片」，层级主要由下方 scrim 压暗提供。
   ⚠️ 刻意不做「左右留边 + 四角圆角 + 异色面」的悬浮卡：那会让面板看起来没有根、与筛选条脱开。
   ⚠️ 左右内边距 = 筛选行内容边（--spacing-lg）：与 .cf-item 的 --spacing-md 叠加后
      选项文字落在 56rpx，与胶囊内文字（32+24）落在同一条内容轴上。 */
.cf-panel {
  background: var(--bg-page);
  color: var(--text-primary);
  border-radius: 0 0 var(--radius-card) var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: var(--spacing-md) var(--spacing-lg) calc(var(--spacing-md) + env(safe-area-inset-bottom));
}
.cf-title {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  /* 左右与 .cf-item 同为 --spacing-md：标题与选项共用一条内容轴 */
  padding: var(--spacing-xs) var(--spacing-md) var(--spacing-md);
}
.cf-list {
  max-height: 60vh;
}
.cf-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  /* 左右 --spacing-md = 胶囊内边距：选项文字与胶囊文字同内容轴（56rpx） */
  padding: var(--spacing-md);
  border-radius: var(--radius-card);
  -webkit-tap-highlight-color: transparent;
}
/* 选中项：纯红底 + 反白文字/对勾，与顶部胶囊选中态 100% 同语言 */
.cf-item.active {
  background: var(--color-primary);
}
.cf-item.active .cf-name {
  color: var(--color-on-primary);
}
/* 未选中项点击反馈：极浅灰，中性不碰红系 */
.cf-item:active {
  background: var(--bg-soft);
}
/* 选中项点击反馈：保持纯红，仅轻微降透明度（特异性高于 .cf-item:active，不会回退成灰） */
.cf-item.active:active {
  background: var(--color-primary);
  opacity: 0.85;
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
  z-index: var(--z-filter-dropdown);
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
/* 面板：与食堂下拉同款——紧贴筛选条向下展开、与筛选条同面（--bg-page）、接缝无 border 的下拉片。
   满宽无左右边距、顶边方角与筛选条无缝衔接、底边圆角 + 柔阴影表达展开层级。
   左右内边距同取 --spacing-lg：与 .ps-preset 的 --spacing-md 叠加后，
   预设文字落在 56rpx，与胶囊文字、食堂选项文字共用同一条内容轴。
   （.ps-panel 的包含块是 .ps-root，其 left/right:0 已是满宽，故无需外边距。） */
.ps-panel {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  background: var(--bg-page);
  border-radius: 0 0 var(--radius-card) var(--radius-card);
  box-shadow: var(--shadow-card);
  color: var(--text-primary);
  padding: var(--spacing-md) var(--spacing-lg) calc(var(--spacing-md) + env(safe-area-inset-bottom));
  /* opacity 淡入（无位移/缩放，MVP 静态） */
  opacity: 0;
  transition: opacity var(--duration-base) var(--ease-out);
}
.ps-panel.open { opacity: 1; }
.ps-title {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  /* 左右与 .ps-preset 同为 --spacing-md：标题与预设共用一条内容轴 */
  padding: var(--spacing-xs) var(--spacing-md) var(--spacing-md);
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
  /* 左右 --spacing-md = 胶囊内边距：预设文字与胶囊文字同内容轴（56rpx） */
  padding: var(--spacing-md);
  border-radius: var(--radius-card);
  -webkit-tap-highlight-color: transparent;
}
/* 选中预设：纯红底 + 反白文字/对勾，与食堂面板、顶部胶囊完全同一选中语言 */
.ps-preset.active {
  background: var(--color-primary);
}
.ps-preset.active .ps-name {
  color: var(--color-on-primary);
}
/* 未选中项点击反馈：极浅灰，中性不碰红系 */
.ps-preset:active {
  background: var(--bg-soft);
}
/* 选中项点击反馈：保持纯红，仅轻微降透明度 */
.ps-preset.active:active {
  background: var(--color-primary);
  opacity: 0.85;
}
.ps-name {
  font-size: var(--font-subtitle);
  color: var(--text-primary);
  min-width: 0;
}
/* 自定义区：与上方预设之间**仅靠间距分隔**，刻意不挂 border-top——
   横线会把表单切成互不相干的两块，破坏「与筛选条连成一体」的整体感。
   （上方预设项自带 --spacing-md 下内边距，叠加此处 margin-top 已有 48rpx 呼吸。） */
.ps-custom {
  margin-top: var(--spacing-md);
  padding-top: 0;
}
.ps-custom-title {
  font-size: var(--font-body);
  color: var(--text-secondary);
  /* 左右 --spacing-md：与预设、标题、胶囊同内容轴 */
  padding: 0 var(--spacing-md) var(--spacing-sm);
}
.ps-inputs {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  /* 输入框左右与内容轴对齐（面板 --spacing-lg + 此处 --spacing-md = 56rpx） */
  padding: 0 var(--spacing-md);
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
  color: var(--color-on-primary);
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
