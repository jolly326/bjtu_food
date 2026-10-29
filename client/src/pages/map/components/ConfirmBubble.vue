<script setup lang="ts">
/**
 * 轻确认气泡（DishSheet 私有组件）。
 * 选中纠错选项后的二次确认：奶米底、细棕描边、奶色遮罩。
 * `leaving` 驱动淡出动画（组合器的状态机控制时序）。
 */
import SheetPill from './SheetPill.vue'

withDefaults(
  defineProps<{
    visible: boolean
    /** 淡出中（阻止重复交互，动画结束后由父级卸载） */
    leaving?: boolean
    busy?: boolean
  }>(),
  { visible: false, leaving: false, busy: false },
)

const emit = defineEmits<{
  /** 🔴 不得用 `confirm`：与小程序原生事件同名会让一次点击触发两次（基线 §1.6） */
  (e: 'submit'): void
  (e: 'cancel'): void
}>()
</script>

<template>
  <view
    v-if="visible"
    class="confirm"
    :class="{ 'confirm--leaving': leaving }"
    aria-role="dialog"
    aria-label="提交反馈确认"
    @tap="emit('cancel')"
  >
    <view class="confirm__card" @tap.stop>
      <text class="confirm__title">要提交这条反馈吗？</text>
      <view class="confirm__actions">
        <SheetPill :disabled="busy || leaving" accent aria-label="确认提交" @press="emit('submit')">
          确认提交
        </SheetPill>
        <SheetPill :disabled="busy || leaving" aria-label="取消" @press="emit('cancel')">
          取消
        </SheetPill>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.confirm {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: calc(var(--z-sheet) + 100);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay-sheet-scrim);
  animation: confirm-fade-in 160ms var(--ease-out);
}

.confirm--leaving {
  animation: confirm-fade-out 160ms var(--ease-out) forwards;
}

.confirm__card {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-lg);
  width: 78%;
  padding: var(--spacing-lg);
  border: 1rpx solid var(--sheet-pill-border);
  border-radius: var(--radius-modal);
  background: var(--overlay-panel-bg);
  animation: confirm-card-in 180ms var(--ease-bounce);
}

.confirm--leaving .confirm__card {
  animation: confirm-card-out 160ms var(--ease-out) forwards;
}

@keyframes confirm-fade-in {
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
}

@keyframes confirm-fade-out {
  from {
    opacity: 1;
  }

  to {
    opacity: 0;
  }
}

@keyframes confirm-card-in {
  from {
    opacity: 0;
    transform: scale(0.96);
  }

  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes confirm-card-out {
  from {
    opacity: 1;
    transform: scale(1);
  }

  to {
    opacity: 0;
    transform: scale(0.98);
  }
}

.confirm__title {
  color: var(--text-title);
  font-family: var(--font-sans);
  font-size: var(--font-h3);
  line-height: 1.4;
  text-align: center;
}

.confirm__actions {
  display: flex;
  gap: var(--spacing-sm);
}
</style>
