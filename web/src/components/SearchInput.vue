<script setup lang="ts">
import { ref, watch } from 'vue'
import { debounce } from '@/utils/debounce'
import searchIcon from '@/static/icon/search.svg'

const props = defineProps<{ placeholder?: string }>()
const emit = defineEmits<{ change: [value: string] }>()

//输入防抖组件，输入停止300ms后触发change事件，传递当前输入值
const raw = ref('')
watch(raw, debounce((v: string) => emit('change', v), 300))
</script>

<template>
  <div class="search-wrap">
    <img :src="searchIcon" class="search-icon" alt="" />
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
  left: 12px;
  width: 16px;
  height: 16px;
  pointer-events: none;
  opacity: .5;
}
.search-input {
  flex: 1;
  width: 320px;
  max-width: 400px;
  padding: 10px 16px 10px 36px;
  border: 1px solid var(--border, #d9d9d9);
  border-radius: var(--radius-md, 8px);
  font-size: 14px;
  outline: none;
  transition: border-color .2s;
}
.search-input:focus {
  border-color: var(--primary, #1890ff);
  box-shadow: 0 0 0 2px rgba(24,144,255,.1);
}
</style>
