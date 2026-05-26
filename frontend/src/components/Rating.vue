<template>
  <view class="rating" :class="{ interactive: readonly }">
    <view
      v-for="star in 5"
      :key="star"
      class="star"
      :class="{ active: star <= modelValue, half: star - 0.5 === modelValue }"
      @tap="readonly ? null : $emit('update:modelValue', star)"
    >
      <text class="star-icon">{{ star <= modelValue ? '★' : '☆' }}</text>
    </view>
    <text v-if="showText && modelValue" class="rating-text">{{ modelValue }}分</text>
  </view>
</template>

<script setup lang="ts">
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
  gap: 8rpx;
}
.star-icon {
  font-size: 28rpx;
  color: #E0E0E0;
}
.star.active .star-icon {
  color: #F5A623;
}
.interactive .star {
  padding: 4rpx;
}
.rating-text {
  font-size: 24rpx;
  color: var(--text-secondary);
  margin-left: 8rpx;
}
</style>
