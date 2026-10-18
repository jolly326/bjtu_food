<script setup lang="ts">
/**
 * 美味页 Z 轴堆叠卡片栈中的单卡（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishSwipeCard）。
 *
 * Julienne 纸片卡：卡面 / 吊孔由 `PaperCard` 承载（堆叠层次由 `DishCardStack` 承担）；本组件只排内容。
 * - 顶部信息带：左大号装饰评级字母（可被图片遮挡，作海报背景）/ 右风味评级两行。
 * - 内容：圆形菜品图（420rpx，无外圈）→ 衬线菜名 → 浅灰辅助信息（价格 · 食堂 | 档口）→ 描边按钮。
 * - 按钮无图标、不贴卡片底边（下方预留充足留白）。
 */
import { computed } from 'vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import FlavorRating from './FlavorRating.vue'
import PaperCard from './PaperCard.vue'
import WantButton from './WantButton.vue'

import type { DishCardOut } from '@/api'

/** 宿主节点虚拟化：组件根直接参与卡片栈 flex 布局（宿主不打断 flex 链路，单卡才能撑满槽位） */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    dish: DishCardOut
    submitting?: boolean
    /** 拖拽中：禁用底部按钮，防止误触 */
    dragging?: boolean
    /** 限频退避中：禁用底部按钮直到倒计时结束（docs/secur/S-03） */
    locked?: boolean
  }>(),
  { submitting: false, dragging: false, locked: false },
)

const emit = defineEmits<{ (e: 'want'): void }>()

function yuan(fen: number): string {
  return (fen / 100).toFixed(2)
}

/** 辅助行文案：`¥14.00 · 食堂 | 档口` —— 竖线两侧各留一个空格（无档口时只显示价格） */
const metaText = computed(() => {
  const place = [props.dish.canteenName, props.dish.stallName].filter(Boolean).join(' | ')
  const price = `¥${yuan(props.dish.price)}`
  return place ? `${price} · ${place}` : price
})
</script>

<template>
  <PaperCard>
    <!-- 顶部信息带：左评级字母 / 右风味评级 -->
    <view class="card__top">
      <FlavorRating :level="dish.flavorLevel ?? null" />
    </view>

    <view class="card__content">
      <!-- 圆形菜品图：正圆裁切，🔴 无外圈白盘 -->
      <view class="card__photo">
        <image v-if="dish.imageUrl" class="card__img" :src="dish.imageUrl" mode="aspectFill" />
        <ImagePlaceholder v-else :size="48" bare />
      </view>

      <text class="card__name wrap-anywhere">{{ dish.name }}</text>

      <text class="card__meta">{{ metaText }}</text>
    </view>

    <view class="card__btn">
      <WantButton
        :tasted="dish.wanted"
        :loading="submitting"
        :disabled="dragging || locked"
        @press="emit('want')"
      />
    </view>
  </PaperCard>
</template>

<style lang="scss" scoped>
/* 顶部信息带：字母作卡片背景层水印（z-index 低于内容层）；右上评级右对齐 */
.card__top {
  position: absolute;
  top: var(--spacing-2xl);
  right: var(--spacing-xl);
  left: var(--spacing-xl);
  z-index: 0;
  pointer-events: none;
}

/* 内容层：底对齐 —— 「图 → 菜名 → 辅助行」整组贴齐按钮上方，
   剩余空间留在图片上方 ⇒ **图片中心位于卡片竖直中心稍偏上**，且随卡高自适应；
   最小顶距保证图片不贴吊孔 / 信息带 */
.card__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  /* 🔴 min-height: 0 —— 允许内容区在槽位不足时收缩，避免把卡片顶高溢出槽底 */
  min-height: 0;
  width: 100%;
  padding-top: var(--spacing-2xl);
}

/* 餐盘：外层白色圆盘（直径 --dish-photo-size 420rpx）+ 内层图（--dish-photo-inner-size 360rpx），
   四周自然留出一圈 30rpx 白色留白环（Julienne 餐盘效果）；图未就绪时露 --bg-placeholder 底 */
.card__photo {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: var(--dish-photo-size);
  height: var(--dish-photo-size);
  margin-bottom: var(--spacing-xl);
  overflow: hidden;
  background: var(--bg-plate);
  border-radius: var(--radius-circle);
}

/* 圆角必须落在这里（原生 <image> 不吃父级 overflow 裁切） */
.card__img {
  width: var(--dish-photo-inner-size);
  height: var(--dish-photo-inner-size);
  border-radius: var(--radius-circle);
}

/* 菜名：衬线常规字重 + 放大字号 —— 卡片主标题（层级靠字号与颜色，不加粗） */
.card__name {
  font-family: var(--font-serif);
  font-size: var(--dish-name-size);
  font-weight: var(--weight-regular);
  color: var(--text-title);
  text-align: center;
}

/* 价格 · 食堂|档口：无衬线 + 弱化（副标题行，浅灰 --text-secondary，不抢菜名重心） */
.card__meta {
  margin-top: calc(var(--spacing-sm) + var(--spacing-2xs));
  font-family: var(--font-sans);
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  color: var(--text-secondary);
  text-align: center;
}

/* 按钮：与信息行间距 + 底部留白由 PaperCard 卡面内距承担（不贴底）；高度不参与压缩 */
.card__btn {
  position: relative;
  z-index: 1;
  flex: none;
  width: 100%;
  margin-top: var(--spacing-lg);
}
</style>
