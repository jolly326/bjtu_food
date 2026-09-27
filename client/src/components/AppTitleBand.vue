<template>
  <!-- 固定标题带（跨页统一，UI 文档 §1 / 搜索页 §2）
       · `position: fixed`：永久固定在页面左上角，不随页面滚动、不随 Banner 滚出；
       · 与微信右上角**原生胶囊同一条水平线**（行高 = navBarHeight、垂直中心对齐）；
       · **两个区域、两档字号均可由参数控制**（2026-09-27 用户裁决）：
         ── 左区（靠左，与左边缘**留有 `--spacing-md` 内距**）：
              · 无返回（`back=false`）⇒ 显示**页面名称**（默认字档 `--font-title`，粗体大号）；
              · 有返回（`back=true`） ⇒ 显示**文字「返回」**（默认字档 `--font-body`，可点，命中区 ≥88rpx）；
         ── 居中区（相对导航行**绝对居中**）：
              · `back=true` ⇒ 显示**页面名称**（默认字档 `--font-h3`）；`back=false` ⇒ 不渲染；
       · **恒透明、不铺任何表面**（结构性决议）：调用方的滚动区已从「标题带 + 常驻工具栏」之下开始
         （首页见 §11；搜索页同样把根层 `padding-top` 让给标题带）⇒ **没有内容从带背后经过**，
         带背后直接露出 `fixed` 页底壁纸即可，不需要切片 / 纯色底 / 材质。
         ⚠️ 组件**不提供**任何「纱 / 渐变 / 透明度」能力（原 `veilOpacity` 已按「零消费即删」移除）。 -->
  <view class="title-band" :style="bandStyle">
    <!-- ===== 左区 ===== -->
    <view class="band-left">
      <!-- 有返回：文字「返回」（替代原箭头 icon；命中区撑满行高） -->
      <view
        v-if="back"
        class="band-back"
        role="button"
        aria-label="返回"
        hover-class="band-back-pressed"
        @tap="emit('back')"
      >
        <text class="band-back-text" :style="{ fontSize: leftFontSize }">返回</text>
      </view>
      <!-- 无返回：页面名称（左对齐；黑色粗体大号，不做白字 / 不描边 / 不加遮罩） -->
      <text v-else class="band-title-left" :style="{ fontSize: leftFontSize }">{{ title }}</text>
    </view>

    <!-- ===== 居中区（仅「有返回」时显示页面名称） ===== -->
    <text v-if="back" class="band-title-center" :style="{ fontSize: centerFontSize }">{{ title }}</text>
  </view>
</template>

<script setup lang="ts">
/**
 * AppTitleBand —— 固定标题带（透明；左区 + 居中区，两档字号可传参）
 *
 * 消费方：`pages/home/index.vue`（无返回 ⇒ 左区显示「知行食记」）、`pages/find/index.vue`（有返回 ⇒ 左区「返回」+ 居中页面名）。
 *
 * 布局口径：
 * · 本组件**自身不带左右内距**（否则会平移「绝对居中」的基准）——左区负责与左边缘的间距，
 *   居中区以整条带为基准取 `left: 50%` 真正居中；右端由 `max-width` 约束、不侵入微信原生胶囊。
 * · 字号档位由消费方按页覆盖（`left-size` / `center-size`），默认按「有无返回」自动取档。
 */
import { computed } from 'vue'
import { useNavMetrics } from '@/utils/useNavMetrics'

const props = withDefaults(defineProps<{
  /** 页面名称：无返回 ⇒ 显示在左区；有返回 ⇒ 显示在居中区 */
  title?: string
  /** 是否显示返回（文字「返回」，居中区随之显示页面名称） */
  back?: boolean
  /**
   * 左区字号（CSS 长度，如 `'44rpx'` / `'var(--font-title)'`）。
   * 不传 ⇒ 按有无返回自动取档：有返回 `--font-body`（文字「返回」）、无返回 `--font-title`（页面名）。
   */
  leftSize?: string
  /** 居中区字号（CSS 长度）。不传 ⇒ `--font-h3`（导航标题档） */
  centerSize?: string
}>(), {
  title: '',
  back: false,
})

const emit = defineEmits<{ (e: 'back'): void }>()

const { statusBarPx, titleBandPx } = useNavMetrics()

/** 带高 = 状态栏 + 导航行高；顶部安全区由度量真源下发（页面不得自算状态栏 / 胶囊避让） */
const bandStyle = computed(() => ({
  height: `${titleBandPx.value}px`,
  paddingTop: 'max(' + statusBarPx.value + 'px, env(safe-area-inset-top))',
}))

/** 左区字号：显式传参优先；否则按「有返回（返回二字）/ 无返回（页面名）」分档 */
const leftFontSize = computed(() => props.leftSize || (props.back ? 'var(--font-body)' : 'var(--font-title)'))
/** 居中区字号：显式传参优先；默认导航标题档 */
const centerFontSize = computed(() => props.centerSize || 'var(--font-h3)')
</script>

<style scoped>
.title-band {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  z-index: calc(var(--z-header) + 1);
  box-sizing: border-box;
  display: flex;
  align-items: center;
  /* ⚠️ 本层**不设左右内距**：任何内距都会平移 `.band-title-center` 的「绝对居中」基准；
     与左边缘的间距由 `.band-left` 承担（用户裁决：左区须与左边缘留有间距）。 */
  /* 无底色：滚动区已从本带之下开始（结构性决议）⇒ 带背后即页底壁纸，天然连续 */
  /* 纯展示层：不拦截下层（Banner / 内容）点击；可点件（「返回」）单独抬回 */
  pointer-events: none;
}

/* ===== 左区：与左边缘的间距在此（`--spacing-md`，页面级 gutter 同轴） ===== */
.band-left {
  display: flex;
  align-items: center;
  min-width: 0;
  padding-left: var(--spacing-md);
}
/* 左区 · 页面名称（无返回）：粗体大号 —— 与文档 §1「标题档」同源 */
.band-title-left {
  position: relative;
  z-index: 1;
  font-weight: var(--weight-bold);
  line-height: 1.1;
  letter-spacing: var(--tracking-h2);
  color: var(--text-primary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 左区 · 「返回」（有返回）：可点，命中区撑满导航行高（≥88rpx = 44pt） */
.band-back {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  min-height: 88rpx;
  padding-right: var(--spacing-sm);
  /* 抬回可点（父层为 pointer-events: none） */
  pointer-events: auto;
  -webkit-tap-highlight-color: transparent;
}
.band-back-pressed { opacity: 0.55; }
.band-back-text {
  font-weight: var(--weight-medium);
  line-height: 1.1;
  color: var(--text-primary);
}

/* ===== 居中区：相对整条带绝对居中（不因左区宽度 / 右侧留白而偏移） ===== */
.band-title-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1;
  text-align: center;
  font-weight: var(--weight-semibold);
  line-height: 1.1;
  color: var(--text-primary);
  /* 右端不侵入微信原生胶囊（胶囊宽 ≈ 87px，居中标题限宽 56% ≈ 210px 内安全） */
  max-width: 56%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
