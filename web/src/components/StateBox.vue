<script setup lang="ts">
defineProps<{ status: 'loading' | 'error' | 'empty'; message?: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="state-box" v-if="status === 'loading'">
    <span class="spin"></span><span>加载中…</span>
  </div>
  <div class="state-box state-err" v-else-if="status === 'error'">
    <span>{{ message || '加载失败' }}</span>
    <button class="link" type="button" @click="$emit('retry')">重试</button>
  </div>
  <div class="state-box" v-else>{{ message || '暂无数据' }}</div>
</template>
