<template>
  <view class="rating" :class="{ interactive: !readonly }">
    <view
      v-for="star in 5"
      :key="star"
      class="star"
      :class="{ active: star <= modelValue }"
      @tap="readonly ? null : $emit('update:modelValue', star)"
    >
      <text class="star-icon-img">{{ star <= modelValue ? EMOJI.starFilled : EMOJI.starEmpty }}</text>
    </view>
    <text v-if="showText && modelValue" class="rating-text">{{ modelValue }}分</text>
  </view>
</template>

<script setup lang="ts">
import { EMOJI } from '@/utils/emoji'

withDefaults(defineProps<{
  modelValue: number
  showText?: boolean
  readonly?: boolean
}>(), {
  modelValue: 0,
  showText: false,
  readonly: true,
})

defineEmits<{
  'update:modelValue': [value: number]
}>()
</script>

<style scoped>
.rating {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
.star-icon-img {
  font-size: 56rpx;
  line-height: 1;
  display: block;
}
.interactive .star {
  padding: var(--spacing-sm);
  transition: transform 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.interactive .star:active {
  transform: scale(var(--press-scale));
}
.rating-text {
  font-size: var(--font-h2);
  color: var(--text-secondary);
  margin-left: var(--spacing-xs);
}
</style>
