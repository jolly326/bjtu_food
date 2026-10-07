<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useOverlayLayer } from '@/composables/useOverlayLayer'

/**
 * 配图**大图预览**（多图；浮层关闭口径同 [UI 基线 §2.1](../../../docs/ui/web/公共组件与形态基线.md)）。
 *
 * <p><b>存在理由</b>：列表缩略图（56px）与抽屉缩略图（140px）都不足以判断配图是否违规，
 * 审核需要「看得清原图」。本组件以覆盖层按容器自适应呈现原图（**等比缩放，不拉伸变形**、居中），
 * 多图可左右切换（按钮 + 键盘 `←` / `→`），并显示「第 n / N 张」。
 *
 * <p><b>挂载即打开</b>：父级用 `v-if` 控制挂载（本组件无 `open` 态），关闭走唯一出口 `close`
 * —— 右上角 `×` / 点击遮罩 / `ESC`；打开期间**锁背景滚动**。
 */
const props = withDefaults(
  defineProps<{
    /** 图片 URL 数组（绝对 URL） */
    images: string[]
    /** 起始索引（默认 0；越界回落 0） */
    index?: number
  }>(),
  { index: 0 },
)

const emit = defineEmits<{ close: [] }>()

const total = computed(() => props.images.length)
const current = ref(0)
const url = computed(() => props.images[current.value] ?? '')

/** 循环切换（末张 → 首张）：连续看图不必来回折返 */
function go(delta: number): void {
  if (total.value <= 1) return
  current.value = (current.value + delta + total.value) % total.value
}

/** 浮层层级 + 背景滚动锁与抽屉 / 弹窗同源（挂载即开 ⇒ open 传 null，自管 attach / detach） */
const layer = useOverlayLayer('preview', null, () => emit('close'))

function onKeydown(e: KeyboardEvent): void {
  // 捕获阶段拦截：本浮层叠在下层抽屉之上，ESC / 方向键不应再触发下层的关闭
  if (e.key === 'Escape') {
    e.stopPropagation()
    return
  }
  if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
  // 非栈顶时不消费事件（下方浮层的方向键语义与自己无关）
  if (!layer.isTop()) return
  e.preventDefault()
  e.stopPropagation()
  go(e.key === 'ArrowLeft' ? -1 : 1)
}

onMounted(() => {
  current.value =
    Number.isInteger(props.index) && props.index >= 0 && props.index < total.value ? props.index : 0
  layer.attach()
  window.addEventListener('keydown', onKeydown, true)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown, true)
  layer.detach()
})
</script>

<template>
  <div
    class="preview-overlay"
    role="dialog"
    aria-modal="true"
    aria-label="配图预览"
    @click.self="emit('close')"
  >
    <button class="preview-x" type="button" aria-label="关闭" @click="emit('close')">×</button>

    <button
      v-if="total > 1"
      class="preview-nav prev"
      type="button"
      aria-label="上一张"
      @click="go(-1)"
    >
      ‹
    </button>

    <img class="preview-img" :src="url" :alt="`第 ${current + 1} 张配图`" />

    <button
      v-if="total > 1"
      class="preview-nav next"
      type="button"
      aria-label="下一张"
      @click="go(1)"
    >
      ›
    </button>

    <div class="preview-count">第 {{ current + 1 }} / {{ total }} 张</div>
  </div>
</template>

<style scoped>
.preview-overlay {
  position: fixed;
  inset: 0;
  /* 叠在下层抽屉 / 弹窗（z-index 1000）之上 */
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 毛玻璃遮罩：与 BaseDrawer 同材质口径，色值一律由 token 派生（不新造颜色） */
  background: color-mix(in srgb, var(--bg-page) 88%, transparent);
  backdrop-filter: var(--blur-material);
  -webkit-backdrop-filter: var(--blur-material);
  padding: var(--space-8) var(--space-10);
}
.preview-img {
  /* 等比缩放（max-* 约束下自动保持原始比例，不拉伸变形）；min-* 放行收缩以免溢出容器 */
  max-width: 100%;
  max-height: 100%;
  min-width: 0;
  min-height: 0;
  border-radius: var(--radius-md);
  background: var(--bg-card);
  box-shadow: var(--shadow-pop);
}
.preview-x,
.preview-nav {
  position: absolute;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-soft);
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-pill);
  line-height: 1;
  cursor: pointer;
}
.preview-x {
  top: var(--space-4);
  right: var(--space-4);
  width: 36px;
  height: 36px;
  font-size: var(--font-xl);
}
.preview-nav {
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  font-size: var(--font-2xl);
}
.preview-nav.prev {
  left: var(--space-4);
}
.preview-nav.next {
  right: var(--space-4);
}
.preview-count {
  position: absolute;
  left: 50%;
  bottom: var(--space-5);
  transform: translateX(-50%);
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  color: var(--text-primary);
  font-size: var(--font-sm);
  font-variant-numeric: tabular-nums;
}
/* 减弱透明度：半透毛玻璃转纯色（与 BaseDrawer 的降级口径一致） */
@media (prefers-reduced-transparency: reduce) {
  .preview-overlay {
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }
}
</style>
