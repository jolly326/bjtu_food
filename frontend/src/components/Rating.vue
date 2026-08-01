<template>
  <view class="rating" :class="{ interactive: !readonly }">
    <view
      v-for="star in 5"
      :key="star"
      class="star"
      :class="{ active: star <= modelValue }"
      @tap="readonly ? null : $emit('update:modelValue', star)"
    >
      <IconSvg name="star" :size="starSize" :color="star <= modelValue ? activeColor : emptyColor" class="star-icon" />
    </view>
    <text v-if="showText && modelValue" class="rating-text">{{ modelValue }}分</text>
  </view>
</template>

<script setup lang="ts">
import IconSvg from '@/components/IconSvg.vue'

withDefaults(defineProps<{
  modelValue: number
  showText?: boolean
  readonly?: boolean
  /** 实心星颜色（如评分色 / 主色） */
  activeColor?: string
  /** 空心星颜色（浅灰描边语义） */
  emptyColor?: string
  /** 单星尺寸（rpx，默认 32） */
  starSize?: number
}>(), {
  modelValue: 0,
  showText: false,
  readonly: true,
  activeColor: 'var(--color-star)',
  emptyColor: 'var(--color-star-empty)',
  starSize: 32,
})

defineEmits<{
  'update:modelValue': [value: number]
}>()
</script>

<style scoped>
.rating {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}
.star {
  line-height: 1;
}
.star-icon {
  display: block;
  line-height: 1;
}
.interactive .star {
  padding: var(--spacing-xs);
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
