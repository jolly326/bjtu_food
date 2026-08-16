<template>
  <view v-if="open" class="modal-mask" :class="{ show: maskShow }" @tap="requestClose" @touchmove.stop.prevent="noop">
    <view class="report-modal" :class="{ open: modalShow }" @tap.stop>
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
import { ref, watch, nextTick } from 'vue'

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
const maskShow = ref(false)
const modalShow = ref(false)

/** 空处理器：mask touchmove.stop 防背景滚动穿透（小程序 catchtouchmove） */
function noop() {}
watch(() => props.open, (v) => {
  if (v) {
    reason.value = ''
    nextTick(() => {
      maskShow.value = true
      modalShow.value = true
    })
  } else {
    maskShow.value = false
    modalShow.value = false
  }
})

function requestClose() { emit('update:open', false) }

function submit() {
  emit('submit', reason.value.trim())
}
</script>

<style scoped>
.modal-mask { position: fixed; inset: 0; background: var(--overlay-scrim); display: flex; align-items: center; justify-content: center; z-index: 100; opacity: 0; transition: opacity var(--duration-slow) var(--ease-out); backdrop-filter: blur(2px); -webkit-backdrop-filter: blur(2px); }
.modal-mask.show { opacity: 1; }
.report-modal { position: fixed; left: 50%; top: 50%; width: 600rpx; max-width: 86vw; background: var(--bg-card); border-radius: var(--radius-modal); padding: var(--spacing-xl); padding-bottom: calc(var(--spacing-xl) + env(safe-area-inset-bottom)); box-shadow: var(--shadow-modal); z-index: 101; opacity: 0; transform: translate(-50%, -46%) scale(0.92); transition: opacity var(--duration-slow) var(--ease-out), transform var(--duration-slow) var(--ease-drawer); will-change: opacity, transform; }
.report-modal.open { transform: translate(-50%, -50%) scale(1); opacity: 1; }
.report-title { display: block; font-size: var(--font-h3); font-weight: var(--weight-bold); color: var(--text-primary); text-align: center; margin-bottom: var(--spacing-lg); }
.report-input { width: 100%; min-height: 180rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; box-sizing: border-box; }
.report-actions { display: flex; gap: var(--spacing-sm); margin-top: var(--spacing-lg); }
.report-btn { flex: 1; height: 80rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); font-size: var(--font-body); font-weight: var(--weight-semibold); transition: transform 0.12s ease, opacity 0.12s ease; -webkit-tap-highlight-color: transparent; }
.report-btn:active { transform: scale(var(--press-scale)); }
.report-cancel { background: var(--bg-page); color: var(--text-secondary); }
.report-confirm { background: var(--color-error); color: var(--text-white); }
.report-confirm.disabled { opacity: 0.58; }

@media (prefers-reduced-motion: reduce) {
  .modal-mask { transition: opacity 0.2s ease; }
  .report-modal { transition: opacity 0.2s ease; transform: translate(-50%, -50%) !important; }
}
</style>
