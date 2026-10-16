<script setup lang="ts">
/**
 * 「去品尝」主操作（规格见 docs/ui/client/公共组件与形态基线.md §2.2）。
 * 未标记 = 主色实底；已品尝态 = 不可重复触发（全局 .is-disabled 承担禁点）。
 */
const props = withDefaults(
  defineProps<{
    tasted?: boolean
    loading?: boolean
  }>(),
  { tasted: false, loading: false },
)

const emit = defineEmits<{ (e: 'press'): void }>()

function onTap() {
  if (!props.tasted && !props.loading) {
    emit('press')
  }
}
</script>

<template>
  <view
    class="want"
    :class="{ 'want--tasted': props.tasted, 'is-disabled': props.tasted || props.loading }"
    hover-class="want--pressed"
    @tap="onTap"
  >
    <text class="want__label">{{ props.tasted ? '已品尝' : props.loading ? '提交中…' : '去品尝' }}</text>
  </view>
</template>

<style lang="scss" scoped>
.want {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: var(--tap-target-size);
  background: var(--color-primary);
  border-radius: var(--radius-pill);
}

.want--pressed {
  opacity: 0.85;
}

.want--tasted {
  background: var(--bg-input);
}

.want__label {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-medium);
  color: var(--color-on-primary);
}

.want--tasted .want__label {
  color: var(--text-tertiary);
}
</style>
