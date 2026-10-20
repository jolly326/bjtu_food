<script setup lang="ts">
/**
 * 空态块（**三页唯一实现**）：线性图标 + 衬线标题 + 浅灰引导 + 可选描边按钮。
 * 规格见 docs/ui/client/公共组件与形态基线.md §2.1.2。
 */
import AppIcon from './AppIcon.vue'

defineOptions({ options: { virtualHost: true } })

withDefaults(
  defineProps<{
    /** 线性图标键（64rpx、muted、低透明度） */
    icon: string
    /** 衬线主文案 */
    title: string
    /** 浅灰引导文案（可选） */
    desc?: string
    /** 动作按钮文案（可选；不传则不渲染按钮） */
    actionText?: string
  }>(),
  { desc: '', actionText: '' },
)

const emit = defineEmits<{ (e: 'action'): void }>()
</script>

<template>
  <view class="empty">
    <AppIcon class="empty__icon" :name="icon" :size="64" tone="muted" />
    <text class="empty__title">{{ title }}</text>
    <text v-if="desc" class="empty__desc">{{ desc }}</text>
    <view
      v-if="actionText"
      class="empty__btn"
      hover-class="empty__btn--pressed"
      @tap="emit('action')"
    >
      {{ actionText }}
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 占满可用高度并竖直居中：与失败态 RetryBlock 同一居中基准 */
.empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  min-height: 0;
  gap: var(--spacing-sm);
  padding: var(--spacing-xl) var(--page-gutter);
}

.empty__icon {
  margin-bottom: var(--spacing-xs);
  opacity: 0.55;
}

.empty__title {
  font-family: var(--font-serif);
  font-size: var(--font-h2);
  font-weight: var(--weight-regular);
  color: var(--text-title);
  text-align: center;
}

.empty__desc {
  padding: 0 var(--spacing-xl);
  font-size: var(--font-small);
  line-height: 1.6;
  color: var(--text-tertiary);
  text-align: center;
}

/* 动作按钮：暖橙细描边 + 无底色（与全站次级按钮同语言） */
.empty__btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: var(--tap-target-size);
  margin-top: var(--spacing-md);
  padding: 0 var(--spacing-xl);
  border: 1rpx solid var(--color-primary);
  border-radius: var(--radius-btn);
  color: var(--text-title);
  font-size: var(--font-subtitle);
  font-weight: var(--weight-medium);
}

.empty__btn--pressed {
  opacity: 0.6;
}
</style>
