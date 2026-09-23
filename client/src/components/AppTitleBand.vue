<template>
  <!-- 固定标题带（跨页统一，UI 文档 §1.0 / 搜索页 §2）
       · `position: fixed`：永久固定在页面左上角，不随页面滚动、不随 Banner 滚出；
       · 与微信右上角**原生胶囊同一条水平线**（行高 = navBarHeight、垂直中心对齐），右侧按胶囊避让；
       · 两种内容形态：`title`（主 Tab 页页面标题）/ `back`（二级页返回 icon）——位置 / 高度 / 对齐 / 配色跨页一致；
       · 默认**透明**（页面底或 Banner 透出）；`veilOpacity` 传入时叠一层「与页面底同源的纱」（首页柔化 Banner 用，§1.3）。 -->
  <view class="title-band" :style="bandStyle">
    <view
      v-if="veilOpacity !== undefined"
      class="title-veil"
      :style="{ opacity: veilOpacity }"
    ></view>

    <!-- 二级页：返回 icon。容器左缘与页面级 gutter 同轴、命中区 88×88rpx（≥44px）。
         ⚠️ size=88（rpx 单位，= 44px 画布）：`arrow-left` 路径在 24 网格中纵向仅占 12/24，
         故其**绘制高 = 44rpx = `--font-title`（标题字号）** —— 光学尺寸与标题相仿。
         旧值 size=22（22rpx 画布 → 绘制高仅 11rpx/5.5px）不足标题 1/4，用户走查判「过小」
         （docs/ui/client-搜索.md §2 / §7.39 第 3 条）。 -->
    <view
      v-if="back"
      class="title-back"
      role="button"
      aria-label="返回"
      hover-class="title-back-pressed"
      @tap="emit('back')"
    >
      <IconSvg name="arrow-left" :size="88" :color="COLOR_MAP['text-primary']" class="title-back-icon" />
    </view>
    <!-- 主 Tab 页：页面标题（黑色粗体大号；不做白字 / 不描边 / 不加遮罩） -->
    <text v-else class="title-text">{{ title }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import IconSvg from './IconSvg.vue'
import { COLOR_MAP } from '@/theme/tokens'
import { useNavMetrics } from '@/utils/useNavMetrics'

withDefaults(defineProps<{
  /** 页面标题（主 Tab 页用；`back` 为真时忽略） */
  title?: string
  /** 二级页返回 icon 模式（替代标题位） */
  back?: boolean
  /**
   * 纱层不透明度（0..1）。**不传 = 不渲染纱层**；
   * 首页传 `titleVeilAlpha`（按 scrollTop 在 `[H_b − VEIL_PX, H_b]` 由 0 → 1，见 UI 文档 §1.3）。
   */
  veilOpacity?: number
}>(), {
  title: '',
  back: false,
})

const emit = defineEmits<{ (e: 'back'): void }>()

const { statusBarPx, navPadRight, titleBandPx } = useNavMetrics()

/** 带高 = 状态栏 + 导航行高；内边距由度量真源下发（页面不得自算状态栏 / 胶囊避让） */
const bandStyle = computed(() => ({
  height: `${titleBandPx.value}px`,
  paddingTop: 'max(' + statusBarPx.value + 'px, env(safe-area-inset-top))',
  paddingRight: navPadRight.value,
}))
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
  padding-left: var(--spacing-md);
  /* 纯展示层：不拦截下层（Banner / 内容）点击；可点件（返回 icon）单独抬回 */
  pointer-events: none;
}
/* 纱：与页面底**同源**的渐变切片，透明度由调用方按 scrollTop 下发（UI 文档 §1.3）。
   基准 = 页面坐标 0（标题带恒贴屏幕顶）→ 固定不动即可与页面底逐像素对齐。 */
.title-veil {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background-image: var(--home-page-grad);
  background-repeat: no-repeat;
  background-size: 100% 720rpx;
  background-position-y: 0px;
  pointer-events: none;
  /* 抬升合成层：纱的 opacity 每帧变化，只做合成、不触发重绘 */
  transform: translateZ(0);
}
/* 标题：黑色粗体大号；⚠️ 必须在纱之上（z-index 抬升）——纱只柔化 Banner，绝不淡化标题 */
.title-text {
  position: relative;
  z-index: 1;
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  line-height: 1.1;
  letter-spacing: var(--tracking-h2);
  color: var(--text-primary);
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 返回 icon：命中区 88rpx（44px）；容器左缘与页面级 gutter 同轴 → 图标光学左缘 ≈ gutter */
.title-back {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-circle);
  /* 抬回可点（父层为 pointer-events: none） */
  pointer-events: auto;
  -webkit-tap-highlight-color: transparent;
}
.title-back-pressed { opacity: 0.55; }
.title-back-icon { line-height: 1; }
</style>
