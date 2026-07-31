<script setup lang="ts">
/**
 * PageHeader：页面头部（T-1）。
 * - props: title / count? / subtitle? / back?
 * - 插槽：#actions（主操作按钮）、#extra（详情缩略图等）。
 * - back 为 true 时左侧渲染返回按钮（@back 事件）。
 */
import { ArrowLeft } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    title?: string
    count?: number | string
    subtitle?: string
    back?: boolean
  }>(),
  { title: '', count: undefined, subtitle: '', back: false },
)
const emit = defineEmits<{ back: [] }>()
</script>

<template>
  <div class="page-header">
    <div class="ph-left">
      <button v-if="back" class="ph-back" v-press type="button" aria-label="返回" @click="emit('back')">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <div class="ph-titles">
        <div class="ph-title-row">
          <h1 v-if="title" class="ph-title">{{ title }}</h1>
          <span v-if="count !== undefined" class="ph-count">{{ count }}</span>
        </div>
        <p v-if="subtitle" class="ph-subtitle">{{ subtitle }}</p>
      </div>
    </div>
    <div class="ph-right">
      <slot name="extra" />
      <div v-if="$slots.actions" class="ph-actions">
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-6);
}
.ph-left { display: flex; align-items: center; gap: var(--space-3); min-width: 0; }
.ph-back {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
  flex-shrink: 0;
  transition: transform 160ms var(--ease-out), background 0.2s var(--ease-out), color 0.2s var(--ease-out), border-color 0.2s var(--ease-out);
}
@media (hover: hover) {
  .ph-back:hover { color: var(--color-primary); border-color: var(--color-primary); }
}
.ph-back:active { transform: scale(var(--press-scale)); }
.ph-titles { display: flex; flex-direction: column; gap: var(--space-1); min-width: 0; }
.ph-title-row { display: flex; align-items: baseline; gap: var(--space-3); }
.ph-title {
  margin: 0;
  font-size: var(--font-3xl);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  letter-spacing: var(--tracking-tight);
  line-height: var(--leading-tight);
}
.ph-count {
  display: inline-flex;
  align-items: center;
  padding: 1px var(--space-3);
  border-radius: var(--radius-pill);
  background: var(--color-primary-bg);
  color: var(--color-primary);
  font-size: var(--font-sm);
  font-weight: var(--weight-medium);
}
.ph-subtitle { margin: 0; font-size: var(--font-sm); color: var(--text-muted); }
.ph-right { display: flex; align-items: center; gap: var(--space-4); flex-shrink: 0; }
.ph-actions { display: flex; align-items: center; gap: var(--space-3); }
</style>
