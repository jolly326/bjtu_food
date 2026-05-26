<template>
  <view class="rating" :class="{ interactive: !readonly }">
    <view
      v-for="star in 5"
      :key="star"
      class="star"
      :class="{ active: star <= modelValue }"
      @tap="readonly ? null : $emit('update:modelValue', star)"
    >
      <image
        :src="star <= modelValue ? '/static/icons/star-active.svg' : '/static/icons/star.svg'"
        class="star-icon-img"
      />
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
  gap: 12rpx;
}
.star-icon-img {
  width: 48rpx;
  height: 48rpx;
  display: block;
}
.interactive .star {
  padding: 6rpx;
}
.rating-text {
  font-size: 26rpx;
  color: var(--text-secondary);
  margin-left: 8rpx;
}
</style>
