<script setup lang="ts">
/**
 * 产品反馈入口（docs/ui/client/菜品首页.md `ProductFeedbackEntry`）。
 *
 * 经 HomeHeader 的 #feedback 插槽落在页头右侧，位于原生胶囊左侧。
 * 只负责入口与开关状态；表单本体直接由 FeedbackForm 渲染，不额外套面板容器。
 */
import AppIcon from '@/components/AppIcon.vue'
import FeedbackForm from './FeedbackForm.vue'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{ open: boolean }>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
}>()
</script>

<template>
  <view class="pfe">
    <view
      class="pfe__entry"
      hover-class="pfe__entry--press"
      aria-role="button"
      aria-label="问题反馈"
      @tap.stop="emit('update:open', !props.open)"
    >
      <AppIcon name="help" :size="24" />
      <text class="pfe__label">问题反馈</text>
    </view>

    <FeedbackForm
      v-if="props.open"
      type="product"
      variant="dropdown"
      @close="emit('update:open', false)"
    />
  </view>
</template>

<style lang="scss" scoped>
/* 不创建定位上下文：表单向上以 .header 为包含块，沿内容区居中 */
.pfe {
  display: inline-flex;
  flex-shrink: 0;
}

/* 页头右侧胶囊入口：白底细描边，视觉尺寸收在页头行内，命中区补到触达下限 */
.pfe__entry {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  min-height: var(--control-height-sm);
  padding: 0 var(--spacing-sm);
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
}

.pfe__entry::after {
  position: absolute;
  top: calc((var(--tap-target-size) - var(--control-height-sm)) / -2);
  right: 0;
  left: 0;
  height: var(--tap-target-size);
  content: '';
}

.pfe__entry--press {
  opacity: 0.6;
}

.pfe__label {
  font-size: var(--font-caption);
  color: var(--text-title);
  white-space: nowrap;
}
</style>
