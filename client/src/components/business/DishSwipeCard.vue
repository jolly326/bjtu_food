<script setup lang="ts">
/**
 * 推荐扑克卡片（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishSwipeCard）。
 * 自上而下：**吊孔**（纸质吊牌隐喻）→ 左上大序号 + 右上标签（评价）→ **圆形**菜品图
 * → 菜名（衬线）→ 元信息行（价格 · 位置）→ 去品尝按钮。
 *
 * 🔴 菜品图为**圆形**：圆角须**直接落在 `<image>` 上**（微信原生 `<image>` 不被父级
 * `overflow: hidden` 裁切，只给容器加圆角会露方角）。
 * 拖拽手势由 `DishCardStack` 管理（本组件只负责单张内容与点击）。
 */
import { computed } from 'vue'
import ImagePlaceholder from '../base/ImagePlaceholder.vue'
import WantButton from './WantButton.vue'

const props = withDefaults(
  defineProps<{
    dish: {
      id: number
      name: string
      price: number
      imageUrl?: string | null
      avgReaction?: number | null
      wanted: boolean
      canteenName: string
      stallName: string
    }
    /** 在栈中的序号（1 起），展示为补零两位 */
    index: number
    submitting?: boolean
  }>(),
  { index: 1, submitting: false },
)

const emit = defineEmits<{ (e: 'want'): void }>()

/** 补零两位序号（01 / 12）—— 弱化作为背景装饰 */
const ordinal = computed(() => String(props.index).padStart(2, '0'))

/** 右上角标签值：评价分；无评价显示「暂无」 */
const reactionText = computed(() =>
  props.dish.avgReaction == null ? '暂无' : String(props.dish.avgReaction),
)

function yuan(fen: number): string {
  return (fen / 100).toFixed(2)
}
</script>

<template>
  <view class="card">
    <!-- 纸质吊牌的吊孔（轻量拟物） -->
    <view class="card__punch" />

    <view class="card__top">
      <text class="card__ordinal">{{ ordinal }}</text>
      <view class="card__tag">
        <text class="card__tagLabel">评价</text>
        <text class="card__tagValue">{{ reactionText }}</text>
      </view>
    </view>

    <view class="card__photo">
      <image v-if="dish.imageUrl" class="card__img" :src="dish.imageUrl" mode="aspectFill" />
      <ImagePlaceholder v-else />
    </view>

    <text class="card__name wrap-anywhere">{{ dish.name }}</text>

    <view class="card__meta">
      <text class="card__price">¥{{ yuan(dish.price) }}</text>
      <text class="card__dot">·</text>
      <text class="card__place">{{ dish.canteenName }} | {{ dish.stallName }}</text>
    </view>

    <WantButton :tasted="dish.wanted" :loading="submitting" @press="emit('want')" />
  </view>
</template>

<style lang="scss" scoped>
.card {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-lg);
  /* 上内距小于下内距 ⇒ 整块内容（含大图）落在**中间偏上**（约 16rpx 位移） */
  padding: var(--spacing-lg) var(--spacing-xl) var(--spacing-2xl);
  background: var(--bg-card);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
}

/* 顶部吊孔：透出页底色，模拟纸质吊牌 */
.card__punch {
  width: var(--dish-punch-size);
  height: var(--dish-punch-size);
  background: var(--bg-page);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-circle);
}

/* 左上序号 + 右上标签：整卡宽的顶行 */
.card__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  width: 100%;
}

/* 超大弱化数字（背景装饰） */
.card__ordinal {
  font-size: var(--font-display);
  font-weight: var(--weight-heavy);
  line-height: 1;
  color: var(--text-tertiary);
}

.card__tag {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--spacing-3xs);
}

.card__tagLabel {
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
}

.card__tagValue {
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
}

.card__photo {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  width: var(--dish-photo-size);
  height: var(--dish-photo-size);
  background: var(--bg-placeholder);
  /* 圆形裁切 */
  border-radius: var(--radius-circle);
}

/* 圆角必须落在这里（原生 <image> 不吃父级 overflow 裁切） */
.card__img {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-circle);
}

/* 菜名用衬线：柔和优雅，与无衬线正文/按钮形成搭配 */
.card__name {
  font-family: var(--font-serif);
  font-size: var(--font-h2);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
  text-align: center;
}

.card__meta {
  display: flex;
  align-items: baseline;
  gap: var(--spacing-sm);
  font-size: var(--font-body);
}

.card__price {
  color: var(--color-price);
}

.card__dot {
  color: var(--text-tertiary);
}

.card__place {
  color: var(--text-tertiary);
}
</style>
