<script setup lang="ts">
/**
 * FilterBar：筛选条（T-1）。
 * - v-model 绑定关键词（与 SearchInput 解耦，结构化筛选下沉此处）。
 * - 插槽：#tabs（分段切换）、#default（放 FilterSelect）、#actions（右侧操作）。
 * - 响应式：窄屏自动换行（gap + flex-wrap）。
 */
const model = defineModel<string>({ default: '' })
</script>

<template>
  <div class="filter-bar">
    <div class="fb-left">
      <div v-if="$slots.tabs" class="fb-tabs">
        <slot name="tabs" />
      </div>
      <div v-if="$slots.default" class="fb-filters">
        <slot />
      </div>
    </div>
    <div class="fb-right">
      <input
        v-model="model"
        class="fb-search"
        type="text"
        placeholder="关键词搜索"
        aria-label="关键词搜索"
      />
      <div v-if="$slots.actions" class="fb-actions">
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
  margin-bottom: var(--space-5);
  padding: var(--space-3) var(--space-4);
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.fb-left { display: flex; align-items: center; gap: var(--space-4); flex-wrap: wrap; min-width: 0; }
.fb-tabs { display: flex; align-items: center; gap: var(--space-2); }
.fb-filters { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
.fb-right { display: flex; align-items: center; gap: var(--space-3); flex-shrink: 0; }
.fb-search {
  width: 200px;
  max-width: 100%;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-size: var(--font-base);
  outline: none;
  background: var(--bg-card);
  box-sizing: border-box;
  transition: border-color 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
}
.fb-search:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent);
}
.fb-actions { display: flex; align-items: center; gap: var(--space-2); }

@media (max-width: 959px) {
  .filter-bar { flex-direction: column; align-items: stretch; }
  .fb-right { justify-content: space-between; }
}
@media (prefers-reduced-motion: reduce) {
  .fb-search { transition: none; }
}
</style>
