<template>
  <view v-if="open" class="modal-mask" :class="{ show: maskShow }" @tap="requestClose" @touchmove.stop.prevent="noop">
    <!-- QA-02：居中弹窗无障碍语义与 BaseSheet 对齐（role/aria-modal/aria-label/tabindex） -->
    <view
      class="report-modal"
      :class="{ open: modalShow }"
      role="dialog"
      :aria-modal="true"
      :aria-label="title"
      tabindex="-1"
      @tap.stop
    >
      <text class="report-title">{{ title }}</text>
      <!-- 处理承诺（spec §7.8 第 4 条）：举报/反馈 48 小时内处理，纯人工，无自动动作 -->
      <text class="report-note">举报将在 48 小时内处理</text>
      <textarea
        class="report-input"
        v-model="reason"
        :placeholder="placeholder"
        :aria-label="title"
        maxlength="500"
        :auto-height="true"
      />
      <view class="report-actions">
        <view class="report-btn report-cancel" role="button" aria-label="取消" @tap="requestClose">取消</view>
        <view
          class="report-btn report-confirm"
          :class="{ disabled: submitting }"
          role="button"
          :aria-label="confirmText"
          :aria-disabled="!!submitting"
          :aria-busy="!!submitting"
          @tap="submit"
        >{{ confirmText }}</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useSheetFocus } from '@/composables/useSheetFocus'

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

const { captureTrigger, restoreFocus } = useSheetFocus()

const reason = ref('')
const maskShow = ref(false)
const modalShow = ref(false)

/** 空处理器：mask touchmove.stop 防背景滚动穿透（小程序 catchtouchmove） */
function noop() {}
watch(() => props.open, (v) => {
  if (v) {
    /** UI-OPT-04：打开时捕获触发焦点，关闭后由 useSheetFocus 还原（H5/桌面可达） */
    captureTrigger()
    reason.value = ''
    nextTick(() => {
      maskShow.value = true
      modalShow.value = true
    })
  } else {
    maskShow.value = false
    modalShow.value = false
    restoreFocus()
  }
})

/** UI-OPT-04：ESC 关闭（H5/桌面可达；小程序无键盘事件，编译期剔除，本端 no-op） */
function onKeydown(e: KeyboardEvent) {
  // #ifdef H5
  if (props.open && e.key === 'Escape') {
    e.preventDefault()
    requestClose()
  }
  // #endif
}
onMounted(() => {
  // #ifdef H5
  if (typeof document !== 'undefined') {
    document.addEventListener('keydown', onKeydown)
  }
  // #endif
})
onBeforeUnmount(() => {
  // #ifdef H5
  if (typeof document !== 'undefined') {
    document.removeEventListener('keydown', onKeydown)
  }
  // #endif
})

function requestClose() { emit('update:open', false) }

function submit() {
  emit('submit', reason.value.trim())
}
</script>

<style scoped>
.modal-mask { position: fixed; inset: 0; background: var(--overlay-scrim); display: flex; align-items: center; justify-content: center; z-index: var(--z-modal); opacity: 0; backdrop-filter: blur(2px); -webkit-backdrop-filter: blur(2px); }
.modal-mask.show { opacity: 1; }
.report-modal { position: fixed; left: 50%; top: 50%; width: 600rpx; max-width: 86vw; background: var(--bg-card); border-radius: var(--radius-modal); padding: var(--spacing-xl); padding-bottom: calc(var(--spacing-xl) + env(safe-area-inset-bottom)); box-shadow: var(--shadow-modal); z-index: calc(var(--z-modal) + 1); opacity: 0; transform: translate(-50%, -46%); }
.report-modal.open { transform: translate(-50%, -50%); opacity: 1; }
.report-title { display: block; font-size: var(--font-h3); font-weight: var(--weight-bold); color: var(--text-primary); text-align: center; margin-bottom: var(--spacing-lg); }
/* 处理承诺说明：三级灰小字，置于标题与输入框之间（不改变既有布局结构，仅补一行文案） */
.report-note { display: block; font-size: var(--font-tiny); color: var(--text-tertiary); text-align: center; line-height: 1.5; margin: calc(-1 * var(--spacing-sm)) 0 var(--spacing-sm); }
.report-input { width: 100%; min-height: 180rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; box-sizing: border-box; }
.report-actions { display: flex; gap: var(--spacing-sm); margin-top: var(--spacing-lg); }
.report-btn { flex: 1; height: 80rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); font-size: var(--font-body); font-weight: var(--weight-semibold); transition: opacity var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.report-cancel { background: var(--bg-page); color: var(--text-secondary); }
.report-confirm { background: var(--color-error); color: var(--text-white); }
.report-confirm.disabled { opacity: 0.58; }

@media (prefers-reduced-motion: reduce) {
  .modal-mask { opacity: 1; }
  .report-modal { transform: translate(-50%, -50%) !important; }
}
</style>
