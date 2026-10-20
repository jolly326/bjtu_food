<script setup lang="ts">
/**
 * 全屏图片预览（Teleport 到 body，z-index 高于所有弹层）。
 * 能力：滚轮缩放(1–4x)、点击切换适应/2x、放大后拖拽平移、Esc / 点遮罩 / 关闭按钮退出；
 * 打开期间锁定 body 滚动。状态见 composables/useImageZoom。
 */
import { onBeforeUnmount, ref, watch } from 'vue'
import {
  closeImageZoom,
  imageZoom,
  toggleImageZoom,
  zoomImageZoom,
} from '@/composables/useImageZoom'

const dragging = ref(false)
let sx = 0
let sy = 0
let stx = 0
let sty = 0

function onWheel(e: WheelEvent) {
  e.preventDefault()
  zoomImageZoom(e.deltaY)
}

function onDown(e: MouseEvent) {
  if (imageZoom.scale <= 1) return
  dragging.value = true
  sx = e.clientX
  sy = e.clientY
  stx = imageZoom.tx
  sty = imageZoom.ty
}

function onMove(e: MouseEvent) {
  if (!dragging.value) return
  imageZoom.tx = stx + (e.clientX - sx)
  imageZoom.ty = sty + (e.clientY - sy)
}

function onUp() {
  dragging.value = false
}

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') closeImageZoom()
}

watch(
  () => imageZoom.open,
  (open) => {
    if (open) {
      window.addEventListener('keydown', onKey)
      document.body.style.overflow = 'hidden'
    } else {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = ''
    }
  },
)

onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <Teleport to="body">
    <div
      v-if="imageZoom.open"
      class="imgzoom"
      @click.self="closeImageZoom"
      @wheel.prevent="onWheel"
    >
      <img
        class="imgzoom__img"
        :class="{ 'is-dragging': dragging }"
        :src="imageZoom.src"
        :alt="imageZoom.alt"
        :style="{
          transform: `translate(${imageZoom.tx}px, ${imageZoom.ty}px) scale(${imageZoom.scale})`,
          cursor: imageZoom.scale > 1 ? (dragging ? 'grabbing' : 'grab') : 'zoom-in',
        }"
        @click.stop="toggleImageZoom"
        @wheel.stop.prevent="onWheel"
        @mousedown.prevent="onDown"
        @mousemove="onMove"
        @mouseup="onUp"
        @mouseleave="onUp"
      />
      <button
        class="imgzoom__close"
        type="button"
        aria-label="关闭预览"
        @click.stop="closeImageZoom"
      >
        ✕
      </button>
    </div>
  </Teleport>
</template>

<style scoped>
.imgzoom {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.85);
  animation: imgzoom-in 0.14s ease-out;
}
@keyframes imgzoom-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}
.imgzoom__img {
  max-width: 92vw;
  max-height: 92vh;
  object-fit: contain;
  user-select: none;
  -webkit-user-drag: none;
  transition: transform 0.12s ease-out;
  will-change: transform;
}
.imgzoom__img.is-dragging {
  transition: none;
}
.imgzoom__close {
  position: absolute;
  top: 16px;
  right: 20px;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  color: #fff;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: background 0.15s ease;
}
.imgzoom__close:hover {
  background: rgba(255, 255, 255, 0.28);
}
</style>
