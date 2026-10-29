<script setup lang="ts">
/**
 * 公共分页条（口径见 docs/ui/web/公共组件与形态基线.md §2.3「分页」）。
 * 视图只传当前页 / 是否有下一页 / 总页数（可选，供跳页），翻页事件回抛给 `usePagedList.go`。
 */
import { ref, watch } from 'vue'

const props = defineProps<{ page: number; hasNext: boolean; totalPages?: number }>()

const emit = defineEmits<{
  (e: 'prev'): void
  (e: 'next'): void
  (e: 'go', page: number): void
}>()

const jump = ref('')

watch(
  () => props.page,
  () => {
    jump.value = ''
  },
)

/** 跳页：越界自动收敛到 [1, totalPages]；非法输入忽略 */
function submitJump() {
  const target = Number.parseInt(jump.value, 10)
  if (!Number.isFinite(target)) return
  const max = props.totalPages ?? target
  const clamped = Math.min(Math.max(1, target), Math.max(1, max))
  if (clamped === props.page) return
  emit('go', clamped)
  jump.value = ''
}
</script>

<template>
  <div class="pager">
    <button class="btn" :disabled="page <= 1" @click="emit('prev')">上一页</button>
    <span class="pager__cur">{{ page }}</span>
    <button class="btn" :disabled="!hasNext" @click="emit('next')">下一页</button>
    <span v-if="totalPages && totalPages > 1" class="pager__jump">
      <input
        v-model="jump"
        class="pager__jumpInput"
        type="number"
        min="1"
        :max="totalPages"
        aria-label="跳至页码"
        @keydown.enter="submitJump"
      />
      <button class="btn btn--sm" @click="submitJump">跳转</button>
      <span class="pager__total">/ {{ totalPages }} 页</span>
    </span>
  </div>
</template>
