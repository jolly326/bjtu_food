<script setup lang="ts">
/**
 * 结果卡（docs/ui/client/菜品首页.md `DishResultCard`）。
 *
 * 一卡对应一个在售位置：封面 + 菜名 + 位置 + 确定售价 + 常驻描述 + 反馈入口；
 * 描述超长时在卡内展开全文，无售卖点明细、无遮罩、不跳位。
 */
import { computed } from 'vue'

import type { DishLocationVO, DishVO } from '@/api'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import { IMAGE, RESULT } from '@/constants'
import { useImageSource } from '@/hooks'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{
  dish: DishVO
  loc: DishLocationVO
  expanded: boolean
}>()

const emit = defineEmits<{
  (e: 'open', offeringId: number): void
  (e: 'feedback', dishId: number): void
  (e: 'pick', canteenName: string): void
}>()

const { src, onError } = useImageSource(() => props.dish.imageUrl, IMAGE.RESULT_THUMB_WIDTH)

const needFull = computed(
  () => props.dish.description.length > RESULT.DESCRIPTION_PREVIEW_CHARS,
)
const priceText = computed(() =>
  props.loc.price === null ? '价格待定' : `¥${(props.loc.price / 100).toFixed(2)}`,
)
const placeText = computed(
  () => `${props.loc.floor || '暂无'} · ${props.loc.stallName || '暂无'}`,
)
</script>

<template>
  <view class="card" hover-class="card--press">
    <view class="card__cover">
      <image v-if="src" class="card__img" :src="src" mode="aspectFill" @error="onError" />
      <view v-else class="card__ph"><ImagePlaceholder /></view>
    </view>

    <view class="card__info">
      <text class="card__name">{{ dish.name }}</text>

      <view class="card__place">
        <text
          class="card__canteen"
          hover-class="card__canteen--press"
          @tap.stop="emit('pick', loc.canteenName)"
        >{{ loc.canteenName }}</text>
        <text class="card__placeRest"> · {{ placeText }}</text>
      </view>

      <text class="card__price">{{ priceText }}</text>

      <text
        class="card__desc"
        :class="{ 'card__desc--clamp': needFull && !expanded, 'card__desc--full': expanded }"
      >{{ dish.description || '暂无描述' }}</text>

      <view class="card__actions">
        <text
          v-if="needFull"
          class="card__more"
          hover-class="card__more--press"
          @tap.stop="emit('open', loc.offeringId)"
        >{{ expanded ? '收起' : '全文' }}</text>
        <view class="card__btn" hover-class="card__btn--press" @tap.stop="emit('feedback', dish.id)">
          反馈这道菜
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.card {
  box-sizing: border-box;
  width: 100%;
  overflow: hidden;
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-card);
  background: var(--bg-card);
}

.card--press {
  opacity: 0.7;
}

.card__cover {
  position: relative;
  height: 0;
  padding-top: 56.25%;
  overflow: hidden;
  background: var(--bg-placeholder);
}

.card__img,
.card__ph {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.card__info {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  box-sizing: border-box;
  padding: var(--spacing-sm);
}

.card__name {
  overflow: hidden;
  font-size: var(--font-title);
  color: var(--text-title);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.card__place {
  overflow: hidden;
  font-size: var(--font-caption);
  color: var(--text-body);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.card__canteen {
  color: var(--text-title);
}

.card__canteen--press {
  opacity: 0.6;
}

.card__placeRest {
  color: var(--text-body);
}

.card__price {
  font-size: var(--font-body);
  color: var(--brand);
}

.card__desc {
  font-size: var(--font-body);
  color: var(--text-body);
  overflow-wrap: anywhere;
}

.card__desc--clamp {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.card__desc--full {
  animation: desc-in var(--duration-base) var(--ease-out);
}

@keyframes desc-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

.card__actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.card__more {
  font-size: var(--font-caption);
  color: var(--text-title);
}

.card__more--press {
  opacity: 0.6;
}

.card__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  height: var(--control-height-sm);
  padding: 0 var(--spacing-md);
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  font-size: var(--font-caption);
  color: var(--text-title);
}

.card__btn--press {
  opacity: 0.7;
}
</style>
