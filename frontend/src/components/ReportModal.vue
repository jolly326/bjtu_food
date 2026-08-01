<template>
  <view v-if="open" class="modal-mask" @tap="requestClose">
    <view class="report-modal" @tap.stop>
      <text class="report-title">{{ title }}</text>
      <textarea class="report-input" v-model="reason" :placeholder="placeholder" maxlength="500" :auto-height="true" />
      <view class="report-actions">
        <view class="report-btn report-cancel" @tap="requestClose">取消</view>
        <view class="report-btn report-confirm" :class="{ disabled: submitting }" @tap="submit">{{ confirmText }}</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  open: boolean
  title?: string
  placeholder?: string
  confirmText?: string
  submitting?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'submit', reason: string): void
}>()

const reason = ref('')
watch(() => props.open, (v) => { if (v) reason.value = '' })

function requestClose() { emit('update:open', false) }

function submit() {
  emit('submit', reason.value.trim())
}
</script>

<style scoped>
.modal-mask { position: fixed; inset: 0; background: var(--overlay-scrim); display: flex; align-items: center; justify-content: center; z-index: 100; }
.report-modal { position: fixed; left: 50%; top: 50%; transform: translate(-50%, -50%); width: 600rpx; max-width: 86vw; background: var(--bg-card); border-radius: var(--radius-modal); padding: var(--spacing-xl); box-shadow: var(--shadow-modal); z-index: 101; }
.report-title { display: block; font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); text-align: center; margin-bottom: var(--spacing-lg); }
.report-input { width: 100%; min-height: 180rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; box-sizing: border-box; }
.report-actions { display: flex; gap: var(--spacing-sm); margin-top: var(--spacing-lg); }
.report-btn { flex: 1; height: 80rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); font-size: var(--font-body); font-weight: 600; transition: transform 0.12s ease, opacity 0.12s ease; -webkit-tap-highlight-color: transparent; }
.report-btn:active { transform: scale(0.97); }
.report-cancel { background: var(--bg-page); color: var(--text-secondary); }
.report-confirm { background: var(--color-error); color: var(--text-white); }
.report-confirm.disabled { opacity: 0.58; }
</style>
