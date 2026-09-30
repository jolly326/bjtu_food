<script setup lang="ts">
const props = defineProps<{
  status: string
  kind?: 'onoff' | 'user' | 'feedback' | 'correction'
}>()

function cls(s: string, kind?: string): string {
  if (kind === 'user') {
    if (s === 'normal') return 'tag tag-green'
    if (s === 'disabled') return 'tag tag-gray'
    return 'tag tag-red' // deleted
  }
  if (kind === 'feedback' || kind === 'correction') {
    if (s === 'pending') return 'tag tag-gray'
    if (s === 'handled' || s === 'adopted') return 'tag tag-green'
    if (s === 'rejected') return 'tag tag-red'
    return 'tag tag-gray'
  }
  // onoff（含 Banner / 菜品 / 档口 / 食堂）
  return s === 'on' ? 'tag tag-green' : 'tag tag-gray'
}

function label(s: string): string {
  const map: Record<string, string> = {
    on: '启用',
    off: '停用',
    normal: '正常',
    disabled: '已禁用',
    deleted: '已注销',
    pending: '待处理',
    handled: '已处理',
    adopted: '已采纳',
    rejected: '已拒绝',
  }
  return map[s] ?? s
}
</script>

<template>
  <span :class="cls(props.status, props.kind)">{{ label(props.status) }}</span>
</template>
