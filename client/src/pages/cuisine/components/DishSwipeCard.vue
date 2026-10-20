<script setup lang="ts">
/**
 * 美味页 Z 轴堆叠卡片栈中的单卡（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishSwipeCard）。
 *
 * Julienne 纸片卡：卡面 / 吊孔由 `PaperCard` 承载（堆叠层次由 `DishCardStack` 承担）；本组件只排内容。
 * - 顶部信息带：左菜品名首字水印（背景层，可被图片遮挡；纯装饰、不表意）／右群体风味评级两行。
 * - 内容：圆形菜品图（280rpx，无外圈）→ 衬线菜名 → 浅灰辅助信息（价格 · 食堂 | 档口）→ 描边按钮。
 * - 按钮无图标、不贴卡片底边（下方预留充足留白）。
 */
import { computed } from 'vue'
import ImagePlaceholder from '@/components/base/ImagePlaceholder.vue'
import PaperCard from './PaperCard.vue'
import WantButton from './WantButton.vue'
import { fenToYuan } from '@/utils/price'

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
    /** 卡面阴影（堆叠后方层传更弱的两档阴影实现景深） */
    paperShadow?: string
  }>(),
  { submitting: false, dragging: false, locked: false, paperShadow: '' },
)

const emit = defineEmits<{ (e: 'want'): void }>()

/** 辅助行文案：`¥14.00 · 食堂 | 档口` —— 竖线两侧各留一个空格（无档口时只显示价格） */
const metaText = computed(() => {
  const place = [props.dish.canteenName, props.dish.stallName].filter(Boolean).join(' | ')
  const price = `¥${fenToYuan(props.dish.price)}`
  return place ? `${price} · ${place}` : price
})

/** 左上装饰大字：菜品名首字（去首尾空白后取第一个字符）—— 纯装饰、不表意 */
const initial = computed(() => props.dish.name.trim().slice(0, 1))

/** 档位词映射（唯一处）：只用中性口碑词，不出现劝退 / 负面表述 */
const LABELS: Record<string, string> = {
  S: '臻荐',
  A: '极荐',
  B: '推荐',
  C: '尚可',
  D: '平实',
}

/** 右上档位词：无评级（投票人数 < 3）⇒ 待评 */
const flavorLabel = computed(() =>
  props.dish.flavorLevel ? (LABELS[props.dish.flavorLevel] ?? '待评') : '待评',
)
</script>

<template>
  <PaperCard :shadow="paperShadow">
    <!-- 顶部信息带：左菜品名首字水印（背景层，可被菜品图遮挡）/ 右群体风味评级 -->
    <view class="card__top">
      <text v-if="initial" class="card__initial">{{ initial }}</text>
      <view class="card__rating">
        <text class="card__ratingCap">风味评级</text>
        <text class="card__ratingLabel">{{ flavorLabel }}</text>
      </view>
    </view>

    <view class="card__content">
      <!-- 圆形菜品图：正圆裁切，🔴 无外圈白盘 -->
      <view class="card__photo">
        <image v-if="dish.imageUrl" class="card__img" :src="dish.imageUrl" mode="aspectFill" />
        <ImagePlaceholder v-else :size="48" />
      </view>

      <text class="card__name wrap-anywhere">{{ dish.name }}</text>

      <text class="card__meta">{{ metaText }}</text>
    </view>

    <view class="card__btn">
      <WantButton
        :loading="submitting"
        :disabled="dragging || locked"
        @press="emit('want')"
      />
    </view>
  </PaperCard>
</template>

<style lang="scss" scoped>
/* 顶部信息带：一行两端对齐，作卡片背景层（z-index 低于内容层，不遮挡图文）。
   🔴 top 须落在吊孔之下（孔：top --spacing-lg + 直径 --spacing-2xl ⇒ 孔底边 80rpx），再留 --spacing-sm 呼吸位 */
.card__top {
  position: absolute;
  top: calc(var(--spacing-lg) + var(--spacing-2xl) + var(--spacing-sm));
  right: var(--spacing-xl);
  left: var(--spacing-xl);
  display: flex;
  align-items: flex-start;
  z-index: 0;
  pointer-events: none;
}

/* 左上：菜品名首字水印（可被菜品图遮挡）。与右上标签靠 flex-start 顶端对齐，🔴 不加纵向偏移；
   装饰暖灰（--text-decor）直接显色（不叠不透明度）—— 白底上靠色值保证可见，只做氛围装饰 */
.card__initial {
  font-family: var(--font-serif);
  font-size: var(--dish-decor-letter);
  font-weight: var(--weight-regular);
  line-height: 1;
  color: var(--text-decor);
}

/* 右上：两行（风味评级 / 档位词）—— 整体右对齐 */
.card__rating {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--spacing-3xs);
  margin-left: auto;
}

/* 第一行小字：衬线 + 字距，弱化为标签；浅灰，权重低但可读。 */
.card__ratingCap {
  font-family: var(--font-serif);
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
}

/* 第二行档位词：中号衬线 + 中号字重，浅棕灰弱化 —— 与第一行标签拉开字号 / 字重级差，
   作为弱附属信息，仍不抢菜名视线 */
.card__ratingLabel {
  font-family: var(--font-serif);
  font-size: var(--font-h3);
  font-weight: var(--weight-medium);
  color: var(--text-secondary);
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

/* 圆形菜品图：单层正圆，图铺满整个圆位（🔴 无外圈白盘）；
   **自适应**：主轴按剩余空间撑满、宽度由 1:1 派生、`--dish-photo-size` 封顶
   ⇒ 槽位偏矮（小屏 / 短机身）时圆图等比收缩，绝不因超排被裁成「挤压」；
   底色仅在图未就绪时透出（--bg-placeholder），不参与正常态观感 */
.card__photo {
  display: flex;
  flex: 1 1 0;
  align-items: center;
  justify-content: center;
  align-self: center;
  min-height: 0;
  max-height: var(--dish-photo-size);
  max-width: 100%;
  margin-bottom: var(--spacing-2xl);
  overflow: hidden;
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
  aspect-ratio: 1;
}

/* 圆角必须落在这里（原生 <image> 不吃父级 overflow 裁切） */
.card__img {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-circle);
}

/* 菜名：衬线中粗字重 + 放大字号 —— 卡片视觉绝对主体，颜色深棕，层级最高 */
.card__name {
  font-family: var(--font-serif);
  font-size: var(--dish-name-size);
  font-weight: var(--weight-medium);
  color: var(--text-title);
  text-align: center;
}

/* 价格 · 食堂|档口：无衬线 + 弱化（副标题行，浅棕灰细体，对比度显著低于菜名，次要信息） */
.card__meta {
  margin-top: calc(var(--spacing-sm) + var(--spacing-2xs));
  font-family: var(--font-sans);
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
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
