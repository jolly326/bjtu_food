<script setup lang="ts">
/**
 * 美食页「卡片流末尾」空态卡（规格见 docs/ui/client/推荐菜品.md §1）。
 *
 * - 本批卡片全部划完后出现，占据同一卡位（由 `DishCardStack` 的 `end` 插槽承载）。
 * - 两个等宽线性按钮：**重新浏览**（复用本批、原顺序）/ **随机盲选**（新一批、排除本批）。
 * - 纯展示 + 两个动作事件；流水线（请求 / 批次切换）由页面负责。
 */
import AppIcon from '../base/AppIcon.vue'

withDefaults(
  defineProps<{
    /** 随机盲选在途：按钮禁用（防重复请求） */
    busy?: boolean
  }>(),
  { busy: false },
)

const emit = defineEmits<{
  (e: 'replay'): void
  (e: 'surprise'): void
}>()
</script>

<template>
  <view class="flowEnd">
    <text class="flowEnd__title">今天的推荐就到这里</text>
    <text class="flowEnd__hint">都看完啦，换种方式再来一轮</text>

    <view class="flowEnd__actions">
      <view class="flowEnd__btn" hover-class="flowEnd__btn--pressed" @tap="emit('replay')">
        <AppIcon name="refresh-cw" :size="28" tone="default" />
        <text class="flowEnd__btnText">重新浏览</text>
      </view>
      <view
        class="flowEnd__btn flowEnd__btn--primary"
        :class="{ 'is-disabled': busy }"
        hover-class="flowEnd__btn--pressed"
        @tap="emit('surprise')"
      >
        <AppIcon name="shuffle" :size="28" tone="primary" />
        <text class="flowEnd__btnText">随机盲选</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 与栈卡同卡面：实色奶白 + 卡片栈圆角 + 柔和阴影 */
.flowEnd {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  box-sizing: border-box;
  padding: var(--spacing-xl);
  background: var(--bg-card);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
}

.flowEnd__title {
  font-family: var(--font-serif);
  font-size: var(--font-h2);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
  text-align: center;
}

.flowEnd__hint {
  font-size: var(--font-small);
  color: var(--text-tertiary);
  text-align: center;
}

.flowEnd__actions {
  display: flex;
  gap: var(--spacing-md);
  width: 100%;
  margin-top: var(--spacing-lg);
}

/* 两个等宽线性按钮（同尺寸、小圆角），主 CTA 用暖橙描边 */
.flowEnd__btn {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  height: var(--tap-target-size);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-btn);
}

.flowEnd__btn--primary {
  border-color: var(--color-primary);
}

.flowEnd__btn--pressed {
  opacity: 0.6;
}

.flowEnd__btnText {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-medium);
  color: var(--text-title);
}
</style>
