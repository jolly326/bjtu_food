<template>
  <view class="rating" :class="{ interactive: !readonly, readonly: readonly }">
    <view
      v-for="star in 5"
      :key="star"
      class="star"
      :class="{ active: star <= modelValue }"
      hover-class="pressed"
      hover-stay-time="80"
      @tap="onSelect(star)"
    >
      <IconSvg :name="star <= modelValue ? 'star-filled' : 'star'" :size="starSize" :color="star <= modelValue ? activeColor : emptyColor" class="star-icon" />
    </view>
    <text v-if="showText && modelValue" class="rating-text">{{ modelValue }}分</text>
  </view>
</template>

<script setup lang="ts">
import IconSvg from '@/components/IconSvg.vue'

const props = withDefaults(defineProps<{
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

const emit = defineEmits<{
  'update:modelValue': [value: number]
}>()

function onSelect(star: number) {
  if (props.readonly) return
  emit('update:modelValue', star)
}
</script>

<style scoped>
.rating {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}
.rating.readonly {
  opacity: 0.92;
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
  transition: transform var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.interactive .star:active {
  transform: scale(var(--press-scale));
}
.readonly .star {
  cursor: default;
}
.rating-text {
  font-size: var(--font-h2);
  color: var(--text-secondary);
  margin-left: var(--spacing-xs);
}
</style>
