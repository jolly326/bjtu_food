<script setup lang="ts">
import { ref, watch } from 'vue'
import { debounce } from '@/utils/debounce'
import { Search } from '@element-plus/icons-vue'

const props = defineProps<{ placeholder?: string }>()
const emit = defineEmits<{ change: [value: string] }>()

//输入防抖组件，输入停止300ms后触发change事件，传递当前输入值
const raw = ref('')
watch(raw, debounce((v: string) => emit('change', v), 300))
</script>

<template>
  <div class="search-wrap">
    <el-icon class="search-icon"><Search /></el-icon>
    <input v-model="raw" :placeholder="props.placeholder || '搜索...'" class="search-input" />
  </div>
</template>

<style scoped>
.search-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
}
.search-icon {
  position: absolute;
  left: var(--space-3);
  width: 16px;
  height: 16px;
  display: inline-flex;
  pointer-events: none;
  opacity: .5;
}
.search-input {
  flex: 1;
  width: 320px;
  max-width: 400px;
  padding: var(--space-2) var(--space-4) var(--space-2) var(--space-10);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-size: var(--font-base);
  outline: none;
  background: var(--bg-card);
  transition: border-color .2s var(--ease-out), box-shadow .2s var(--ease-out);
}
.search-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent);
}
</style>
