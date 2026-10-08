<script setup lang="ts">
/**
 * ActiveFilters —— **已生效筛选条件回显**（列表页筛选区的公共件）。
 *
 * <p><b>问题</b>：此前 7 个列表页的筛选区只提供「输入框 + 查询 + 重置」，
 * 管理员无法一眼看出**当前结果是被什么条件筛出来的** —— 尤其在翻页 / 改排序后，
 * 只能靠回忆刚才填过什么。本组件把已生效条件渲染成可点删除的标签。
 *
 * <p><b>用法</b>：放在 .filters 容器**内部**（作为其最后一个子元素）——
 * 样式上由 .filters-active 的上边框与筛选控件区分成两层。
 */
import { computed } from 'vue'
import AppIcon from '@/components/AppIcon.vue'

export interface ActiveFilter {
  /** 字段键（回传给消费方用于清空该条件） */
  key: string
  /** 字段名（标签左侧，如「状态」） */
  label: string
  /** 条件的展示值（已由消费方格式化，如「待处理」） */
  value: string
}

const props = defineProps<{
  /** 已生效条件（消费方过滤掉空值后传入） */
  items: ActiveFilter[]
  /** 「清空全部」的文案（默认「清空筛选」） */
  clearText?: string
}>()

const emit = defineEmits<{
  /** 清空单个条件（消费方把对应字段置空并重查） */
  remove: [key: string]
  /** 清空全部条件 */
  clear: []
}>()

/** 无条件时整块不渲染（避免空占位影响筛选区留白） */
const hasAny = computed(() => props.items.length > 0)

/** 删除钮的无障碍标签（避免模板内嵌套模板字符串的反引号转义） */
function ariaLabelFor(f: ActiveFilter): string {
  return '清除筛选条件 ' + f.label
}
</script>

<template>
  <div v-if="hasAny" class="filters-active">
    <span class="filters-active-label">已筛选</span>
    <span v-for="f in items" :key="f.key" class="filters-chip">
      <span>{{ f.label }}：{{ f.value }}</span>
      <button type="button" :aria-label="ariaLabelFor(f)" @click="emit('remove', f.key)">
        <AppIcon name="close" :size="14" color="var(--text-muted)" />
      </button>
    </span>
    <button v-if="items.length > 1" class="link" type="button" @click="emit('clear')">
      {{ clearText ?? '清空筛选' }}
    </button>
  </div>
</template>
