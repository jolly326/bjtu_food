<template>
  <!-- 首页「筛选」面板（2026-09-21 home-filter delta「食堂筛选面板」）：
       由搜索框右侧「筛选」按钮展开，**锚定按钮正下方**（右缘与按钮对齐）、**中性白底**（--bg-card），
       内含「全部」+ 各食堂（食堂维度）与价格区间（价格维度）——两个维度与大类标签栏**可叠加**、
       互不清除；点击面板以外区域（遮罩）关闭且不改变当前筛选。
       弹层开合**瞬开瞬关**（§4.9：无进出 transition），故不写任何出入场动效。 -->
  <view class="hf-root">
    <!-- 遮罩：承接「点击外部关闭」（不改变筛选，仅关闭） -->
    <view class="hf-mask" aria-hidden="true" @tap="emit('close')" />

    <view class="hf-panel" role="dialog" aria-label="筛选" @tap.stop>
      <text class="hf-section">食堂</text>
      <!-- 列表外层只为承载底部渐隐（D9）：食堂多于可视条数（≈6）时，末项会被 max-height 半截裁断，
           用极淡的白→透明遮罩提示「还可继续滚动」，避免读成「就这些了」 -->
      <view class="hf-canteens-wrap">
        <scroll-view class="hf-canteens" scroll-y>
          <view
            class="hf-opt"
            :class="{ active: selectedCanteenId === null }"
            role="button"
            aria-label="不限食堂"
            hover-class="hf-pressed"
            @tap="pickCanteen(null)"
          >
            <text class="hf-opt-name">全部</text>
            <IconSvg v-if="selectedCanteenId === null" name="check" :size="28" :color="COLOR_MAP['on-primary']" />
          </view>
          <view
            v-for="c in canteens"
            :key="c.id ?? c.name"
            class="hf-opt"
            :class="{ active: selectedCanteenId === c.id }"
            role="button"
            :aria-label="`筛选食堂：${c.name}`"
            hover-class="hf-pressed"
            @tap="pickCanteen(c.id ?? null)"
          >
            <!-- 长食堂名完整显示（不省略、可换行）——省略号只作用于「筛选」按钮文案 -->
            <text class="hf-opt-name">{{ c.name }}</text>
            <IconSvg v-if="selectedCanteenId === c.id" name="check" :size="28" :color="COLOR_MAP['on-primary']" />
          </view>
        </scroll-view>
        <!-- 底部渐隐：仅在列表超出可视高度（≈6 条）时出现；纯装饰，对读屏隐藏 -->
        <view
          v-if="canteens.length > CANTEEN_FADE_THRESHOLD"
          class="hf-canteens-fade"
          aria-hidden="true"
        />
      </view>

      <text class="hf-section hf-section--price">价格</text>
      <view class="hf-prices">
        <view
          v-for="opt in PRICE_PRESETS"
          :key="opt.key"
          class="hf-price"
          :class="{ active: activePriceKey === opt.key }"
          role="button"
          :aria-label="`价格：${opt.label}`"
          hover-class="hf-pressed"
          @tap="pickPrice(opt.key)"
        >
          <text class="hf-price-text">{{ opt.label }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import type { CanteenInfo } from '@/types/canteen'
import { COLOR_MAP } from '@/theme/tokens'
import {
  PRICE_PRESETS,
  matchPresetKey,
  type PricePresetKey,
  type PriceRange,
} from '@/utils/price-filter'

const props = defineProps<{
  /** 食堂字典（面板数据源；未加载时面板只显示「全部」，由页面在展开前补拉） */
  canteens: CanteenInfo[]
  /** 当前选中食堂 id（受控；null = 全部） */
  selectedCanteenId: number | null
  /** 当前价格区间（受控，单位：元；透传 emit，禁止二次换算） */
  priceRange: PriceRange
}>()

const emit = defineEmits<{
  (e: 'canteen-select', id: number | null): void
  (e: 'price-select', range: PriceRange): void
  (e: 'close'): void
}>()

/**
 * 底部渐隐显隐阈值（条数）：`.hf-canteens` 的 max-height 46vh 在常见机型上约容纳 6 条食堂选项，
 * 超出时末条会被半截裁断，此时才需要「可继续滚动」的提示（D9）。短列表不显示渐隐，
 * 避免遮住最后一条的正常文字。
 */
const CANTEEN_FADE_THRESHOLD = 6

/** 当前价格预设命中键（自定义区间不匹配任何预设 → 无预选） */
const activePriceKey = computed<PricePresetKey | ''>(() => matchPresetKey(props.priceRange))

function pickCanteen(id: number | null) {
  // 先关面板再加筛选，与「选择某食堂 → 面板关闭」的行为一致
  emit('close')
  emit('canteen-select', id)
}

function pickPrice(key: PricePresetKey) {
  const opt = PRICE_PRESETS.find((p) => p.key === key)
  if (!opt) return
  emit('close')
  emit('price-select', { min: opt.min, max: opt.max })
}
</script>

<style scoped lang="scss">
/* 定位根：挂在首页固定头部（.home-top，position:relative）之下，top:100% = 搜索框正下方 */
.hf-root {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: var(--z-filter-dropdown);
}
/* 遮罩：全屏承接外部点击（fixed 以覆盖固定头部自身区域） */
.hf-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 100vh;
  background: var(--overlay-scrim);
}
/* 面板：中性白底 + 圆角 + 投影；右缘与搜索框内「筛选」按钮右缘对齐
   （搜索行左右内边距 + 搜索框自身右内边距，**两处现均为 --spacing-md** = 24 + 24 = 48rpx）。
   ⚠️ 三项同源：本 right ← `.home-search-row` padding ← `.home-search` padding。
   改动其中任一（如把搜索行 gutter 改回 --spacing-lg）必须同步改本行，否则面板右缘错位。 */
.hf-panel {
  position: absolute;
  top: var(--spacing-xs);
  right: calc(var(--spacing-md) + var(--spacing-md));
  width: 560rpx;
  max-width: 78%;
  max-height: 70vh;
  box-sizing: border-box;
  padding: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-modal);
}
.hf-section {
  display: block;
  padding: var(--spacing-xs) var(--spacing-sm) var(--spacing-xs);
  font-size: var(--font-aux);
  font-weight: var(--weight-medium);
  color: var(--text-tertiary);
}
.hf-section--price { padding-top: var(--spacing-md); }
/* 列表外层：仅作为底部渐隐的定位包含块（不参与滚动） */
.hf-canteens-wrap {
  position: relative;
}
.hf-canteens {
  max-height: 46vh;
}
/* 底部渐隐（D9）：白→透明，提示「下面还有」；不拦截手势（pointer-events: none） */
.hf-canteens-fade {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 32rpx;
  background: linear-gradient(180deg, var(--grad-fade-white), var(--bg-card));
  pointer-events: none;
}
.hf-canteens ::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}
.hf-opt {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-sm);
  border-radius: var(--radius-tag);
  -webkit-tap-highlight-color: transparent;
}
/* 选中态：主色填充 + 反白文字（--color-primary 即填充档，白字 5.18:1 ✅） */
.hf-opt.active { background: var(--color-primary); }
.hf-opt.active .hf-opt-name { color: var(--color-on-primary); }
.hf-opt-name {
  flex: 1;
  min-width: 0;
  font-size: var(--font-body);
  color: var(--text-primary);
  /* 完整显示（长食堂名换行而非省略） */
  word-break: break-all;
}
/* 按压反馈：小程序端统一「透明度微降 / 浅底」（§4.9：废止 transform: scale 按压） */
.hf-pressed { opacity: 0.7; }
.hf-prices {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) var(--spacing-sm) var(--spacing-xs);
}
.hf-price {
  /* 两颗一行：flex 均分（禁 grid）。
     基准宽用固定百分比（不用 calc(var(...))）：列宽解析在小程序 scoped 样式下曾出现
     var() 失效导致塌列的情况，固定值最稳；换行由 flex-wrap 承担，窄屏自动单列。 */
  flex: 1 1 44%;
  min-width: 200rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 72rpx;
  padding: 0 var(--spacing-sm);
  box-sizing: border-box;
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  -webkit-tap-highlight-color: transparent;
}
.hf-price.active { background: var(--color-primary); }
.hf-price-text {
  font-size: var(--font-small);
  color: var(--text-secondary);
}
.hf-price.active .hf-price-text {
  color: var(--color-on-primary);
  font-weight: var(--weight-medium);
}
</style>
