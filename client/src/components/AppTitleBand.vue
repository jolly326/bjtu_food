<template>
  <!-- 固定标题带（跨页统一，UI 文档 §1 / 搜索页 §2）
       · `position: fixed`：永久固定在页面左上角，不随页面滚动、不随 Banner 滚出；
       · 与微信右上角**原生胶囊同一条水平线**（行高 = navBarHeight、垂直中心对齐），右侧按胶囊避让；
       · 两种内容形态：`title`（主 Tab 页页面标题）/ `back`（二级页返回 icon）——位置 / 高度 / 对齐 / 配色跨页一致；
       · **恒透明、不铺任何表面**（2026-09-27 结构性决议）：调用方的滚动区已从「标题带 + 常驻工具栏」之下开始
         （首页见 §11；搜索页同样把根层 `padding-top` 让给标题带）⇒ **没有内容从带背后经过**，
         带背后直接露出 `fixed` 页底壁纸即可，不需要切片 / 纯色底 / 材质。
         ⚠️ 组件**不提供**任何「纱 / 渐变 / 透明度」能力（原 `veilOpacity` 已按「零消费即删」移除）。 -->
       <view class="title-band" :style="bandStyle">
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
  /* 无底色：滚动区已从本带之下开始（结构性决议）⇒ 带背后即页底壁纸，天然连续 */
  /* 纯展示层：不拦截下层（Banner / 内容）点击；可点件（返回 icon）单独抬回 */
  pointer-events: none;
}
/* 标题：黑色粗体大号；压在页底壁纸之上（`z-index` 与返回 icon 同层排列） */
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
