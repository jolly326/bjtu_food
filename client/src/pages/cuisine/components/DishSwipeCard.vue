<script setup lang="ts">
/**
 * 美味页 Z 轴堆叠卡片栈中的单卡（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishSwipeCard）。
 *
 * Julienne 纸片卡：卡面 / 吊孔由 `PaperCard` 承载（堆叠层次由 `DishCardStack` 承担）；本组件只排内容。
 * - 顶部信息带：左菜品名首字装饰大字（衬线浅灰，可被菜品图遮挡）／右群体风味评级两行（标签 + 档位词）。
 * - 内容：圆形菜品图（居中，封顶 480rpx）→ 衬线菜名 → 辅助行（图标 + 位置 + 价格）→ 描边按钮。
 * - 按钮无图标、不贴卡片底边（下方预留充足留白）。
 */
import { computed } from 'vue'
import AppIcon from '@/components/base/AppIcon.vue'
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

/** 价格文案：`¥14.00`（与位置同行、紧随位置文本右侧，字号与位置文本一致） */
const priceText = computed(() => `¥${fenToYuan(props.dish.price)}`)

/** 位置文案：`食堂 档口` —— 空格分隔（缺失项跳过；两者皆缺时为空，位置文本不渲染） */
const placeText = computed(() =>
  [props.dish.canteenName, props.dish.stallName].filter(Boolean).join(' '),
)

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
    <!-- 顶部信息带：左菜品名首字装饰大字 / 右群体风味评级（标签 + 档位词） -->
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
        <image v-if="dish.imageUrl" class="card__img" :src="dish.imageUrl" mode="aspectFill" lazy-load />
        <ImagePlaceholder v-else :size="48" />
      </view>

      <text class="card__name wrap-anywhere">{{ dish.name }}</text>

      <!-- 辅助行：`map-pin` + 位置文本 + 价格（价格在位置文本右侧，与位置文本同字号） -->
      <view class="card__meta">
        <AppIcon name="map-pin" :size="32" tone="muted" />
        <text v-if="placeText" class="card__metaText">{{ placeText }}</text>
        <text class="card__metaPrice">{{ priceText }}</text>
      </view>
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

/* 左上：菜品名首字装饰大字（可被菜品图遮挡）—— 与右上评级顶端对齐、不加纵向偏移；
   衬线 + 大字号 + 极浅暖灰直接显色（不叠不透明度），作背景层氛围装饰、不抢主体 */
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

/* 第一行：纯标签（低对比浅灰小字）—— 让位于下方档位词 */
.card__ratingCap {
  font-family: var(--font-sans);
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
  letter-spacing: 0.04em;
}

/* 第二行档位词：无衬线 + 中号字重，浅棕灰弱化 —— 卡内评级的信息本体，明显大于上方标签 */
.card__ratingLabel {
  font-family: var(--font-sans);
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--text-secondary);
  letter-spacing: 0.02em;
}

/* 内容层：**整组竖直居中** ——「图 → 菜名与价格 → 位置行」居中于按钮上方，
   圆形图落在组内视觉中心，剩余空间均分上下；最小顶距保证图不贴吊孔 / 信息带 */
.card__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  /* 🔴 min-height: 0 —— 允许内容区在槽位不足时收缩，避免把卡片顶高溢出槽底 */
  min-height: 0;
  width: 100%;
  padding-top: var(--spacing-2xl);
}

/* 圆形菜品图：单层正圆，图铺满整个圆位（🔴 无外圈白盘）；
   **尺寸封顶 + 水平居中**：宽度取 --dish-photo-size、卡内更窄时由 100% 让位，
   1:1 派生高度 ⇒ 图不被纵向拉伸变形；底色仅图未就绪时透出（--bg-placeholder） */
.card__photo {
  flex: none;
  align-self: center;
  width: min(var(--dish-photo-size), 100%);
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

/* 菜名：衬线大字号 —— 卡片视觉绝对主体，🔴 卡内最重（medium 衬线），颜色深棕，层级最高 */
.card__name {
  font-family: var(--font-serif);
  font-size: var(--dish-name-size);
  font-weight: var(--weight-medium);
  color: var(--text-title);
  text-align: center;
}

/* 辅助行：`map-pin` 图标 + 位置文本 + 价格（价格紧随位置右侧）—— 一行居中，
   与菜名块间距 --spacing-sm + --spacing-2xs */
.card__meta {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  margin-top: calc(var(--spacing-sm) + var(--spacing-2xs));
}

/* 位置文本：无衬线弱化行（meta，保持轻，不抢主体） */
.card__metaText {
  font-family: var(--font-sans);
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
}

/* 价格：**核心数据**，🔴 比位置 meta 重一档（medium 字重 + 深炭灰），与菜名 / 评级同属卡内信息本体 */
.card__metaPrice {
  font-family: var(--font-sans);
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--text-body);
}

/* 按钮：内容撑起、卡内水平居中；底部留白由 PaperCard 卡面内距承担（不贴底），高度不参与压缩 */
.card__btn {
  position: relative;
  z-index: 1;
  flex: none;
  align-self: center;
  margin-top: var(--spacing-lg);
}
</style>
