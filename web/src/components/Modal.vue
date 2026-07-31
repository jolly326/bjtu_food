<script setup lang="ts">
/**
 * Modal：基础弹窗（§4.5 材质 + §4.4 弹层动效）。
 * - 遮罩 backdrop-filter: blur(20px) saturate(180%)（强制）
 * - 弹层 spring 动效、transform-origin 锚定触发源
 * - 尊重 reduced-transparency / prefers-reduced-motion 降级为纯色 + 交叉淡入
 */
import { ref, watch, onBeforeUnmount } from 'vue'
import { icon } from '@/utils/icon'

const props = defineProps<{
  show: boolean
  title?: string
  width?: number
  /** 锚定触发源的横坐标，用于 transform-origin（§4.4） */
  originX?: number
  originY?: number
}>()
const emit = defineEmits<{ close: [] }>()

const overlay = ref<HTMLElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const mounted = ref(false)
const visible = ref(false)

watch(
  () => props.show,
  (v) => {
    if (v) {
      mounted.value = true
      requestAnimationFrame(() => {
        visible.value = true
        enterAnim()
      })
    } else if (mounted.value) {
      visible.value = false
      window.setTimeout(() => (mounted.value = false), 240)
    }
  },
  { immediate: true },
)

function enterAnim() {
  if (!overlay.value || !panel.value) return
  overlay.value.style.opacity = '1'
  panel.value.style.opacity = '1'
  panel.value.style.transform = 'scale(1) translateY(0)'
}

onBeforeUnmount(() => {
  mounted.value = false
})

function onClose() {
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="mounted"
      ref="overlay"
      class="overlay"
      :class="{ show: visible }"
      @click.self="onClose"
    >
      <div
        ref="panel"
        class="modal"
        :class="{ show: visible }"
        :style="[
          width ? { width: width + 'px' } : undefined,
          (originX !== undefined || originY !== undefined)
            ? { transformOrigin: `${originX ?? 50}% ${originY ?? 50}%` }
            : undefined,
        ]"
        role="dialog"
        aria-modal="true"
      >
        <button class="modal-close" v-press aria-label="关闭" @click="onClose">
          <img :src="icon.close" class="icon-close" alt="" />
        </button>
        <h3 v-if="title">{{ title }}</h3>
        <slot />
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--el-mask-color, rgba(0, 0, 0, 0.45));
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  opacity: 0;
  transition: opacity 0.22s var(--ease-drawer);
  /* §4.5 强制半透明材质 */
  backdrop-filter: var(--blur-material);
  -webkit-backdrop-filter: var(--blur-material);
}
.overlay.show {
  opacity: 1;
}
.modal {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  padding: var(--space-8) var(--space-8);
  width: 480px;
  max-width: 90vw;
  max-height: 84vh;
  overflow-y: auto;
  box-shadow: var(--shadow-sheet);
  position: relative;
  opacity: 0;
  transform: scale(0.96) translateY(8px);
  transform-origin: center;
  transition:
    opacity 0.24s var(--ease-drawer),
    transform 0.24s var(--ease-drawer);
}
.modal.show {
  opacity: 1;
  transform: scale(1) translateY(0);
}
.modal h3 {
  margin: 0 0 var(--space-5);
  font-size: var(--font-xl);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.modal-close {
  position: absolute;
  top: var(--space-4);
  right: var(--space-5);
  background: none;
  border: none;
  cursor: pointer;
  padding: var(--space-1);
  line-height: 1;
  transition: transform 160ms var(--ease-out);
}
.modal-close:hover {
  opacity: 0.8;
}
.modal-close:active {
  transform: scale(var(--press-scale));
}
.icon-close {
  width: 16px;
  height: 16px;
  display: block;
  opacity: 0.5;
  transition: opacity 0.2s var(--ease-out);
}

/* §4.7 降级：去模糊 + 纯色遮罩 */
@media (prefers-reduced-transparency: reduce) {
  .overlay {
    background: rgba(0, 0, 0, 0.55);
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .overlay,
  .modal {
    transition: opacity 0.18s ease;
  }
  .modal {
    transform: none;
  }
}
</style>
