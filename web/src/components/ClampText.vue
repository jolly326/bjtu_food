<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

/**
 * 长文本展示件（[UI 基线 §1.4 / §2.7](../../../docs/ui/web/公共组件与形态基线.md)）。
 *
 * <p>**存在理由**：管理端的「内容 / 回复列」必须能**读全文**才能判断处置（隐藏 / 删除 / 采纳），
 * 而 `.ellipsis`（`max-width: 200px` + 单行截断）只给一行、信息不足。
 * 本组件提供**多行折叠 + 就地展开**：折叠时占 `lines` 行，溢出才给「展开 / 收起」，
 * 展开后完整呈现（`pre-wrap` + `overflow-wrap: anywhere`，超长无空格串也不会撑破列宽）。
 */
const props = withDefaults(
  defineProps<{
    /** 待展示文本（空值渲染 `placeholder`） */
    text?: string | null
    /** 折叠时展示行数，默认 2 */
    lines?: number
    /** 空文本占位，默认 `—` */
    placeholder?: string
  }>(),
  { text: '', lines: 2, placeholder: '—' },
)

const expanded = ref(false)
const overflowing = ref(false)
const bodyRef = ref<HTMLElement | null>(null)
let observer: ResizeObserver | null = null

/** 溢出判定：折叠态下 `scrollHeight` 超过可视高 ⇒ 需要「展开」入口 */
function measure(): void {
  const node = bodyRef.value
  if (!node || expanded.value) return
  overflowing.value = node.scrollHeight > node.clientHeight + 1
}

function toggle(): void {
  expanded.value = !expanded.value
  if (!expanded.value) void nextTick(measure)
}

watch(
  () => props.text,
  () => {
    expanded.value = false
    void nextTick(measure)
  },
)

onMounted(() => {
  measure()
  const node = bodyRef.value
  if (typeof ResizeObserver !== 'undefined' && node) {
    // 列宽变化（窗口缩放 / 侧栏折叠 / 翻页换列）后重判是否仍溢出
    observer = new ResizeObserver(measure)
    observer.observe(node)
  } else {
    window.addEventListener('resize', measure)
  }
})

onBeforeUnmount(() => {
  observer?.disconnect()
  window.removeEventListener('resize', measure)
})
</script>

<template>
  <div class="clamp-text">
    <div
      ref="bodyRef"
      class="clamp-body"
      :class="{ 'is-clamped': !expanded }"
      :style="{ '--clamp-lines': String(lines) }"
    >
      {{ text || placeholder }}
    </div>
    <button
      v-if="overflowing"
      class="link clamp-toggle"
      type="button"
      :aria-expanded="expanded"
      @click="toggle"
    >
      {{ expanded ? '收起' : '展开' }}
    </button>
  </div>
</template>

<style scoped>
.clamp-text {
  min-width: 0;
}
.clamp-body {
  /* 保留原换行 + 超长串可断行（URL / 无空格长词） */
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.clamp-body.is-clamped {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
  -webkit-line-clamp: var(--clamp-lines, 2);
}
.clamp-toggle {
  margin-top: var(--space-1);
}
</style>
