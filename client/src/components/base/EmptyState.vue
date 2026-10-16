<script setup lang="ts">
/**
 * 空态（规格见 docs/ui/client/公共组件与形态基线.md §2.1.2）。
 * 与 RetryBlock 的分工：本组件 = 空态；RetryBlock = 失败态。
 * 动作按钮（0~2 个）由本组件按规格渲染（方形 + 图标 + 按钮下方文案），点击派发 `action`。
 */
import AppIcon from './AppIcon.vue'

type EmptyAction = { key: string; icon: string; label: string }

withDefaults(
  defineProps<{
    title: string
    subtitle?: string
    actions?: EmptyAction[]
  }>(),
  { actions: () => [] },
)

const emit = defineEmits<{ (e: 'action', key: string): void }>()
</script>

<template>
  <view class="empty">
    <text class="empty__title">{{ title }}</text>
    <text v-if="subtitle" class="empty__subtitle">{{ subtitle }}</text>
    <view v-if="actions.length" class="empty__actions">
      <view
        v-for="item in actions"
        :key="item.key"
        class="empty__action"
        hover-class="empty__action--pressed"
        @tap="emit('action', item.key)"
      >
        <view class="empty__actionBox">
          <AppIcon :name="item.icon" :size="48" tone="default" />
        </view>
        <text class="empty__actionLabel">{{ item.label }}</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.empty {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xl) 0;
}

.empty__title {
  font-size: var(--font-body);
  font-weight: var(--weight-medium);
  color: var(--text-body);
}

.empty__subtitle {
  font-size: var(--font-small);
  color: var(--text-tertiary);
}

.empty__actions {
  display: flex;
  gap: var(--spacing-md);
  margin-top: var(--spacing-md);
}

.empty__action {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
}

.empty__action--pressed {
  opacity: 0.7;
}

.empty__actionBox {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--empty-action-size);
  height: var(--empty-action-size);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
}

.empty__actionLabel {
  font-size: var(--font-small);
  color: var(--text-body);
}
</style>
