<script setup lang="ts">
/**
 * PageContainer：页面统一容器（T-1）。
 * - 默认 max-width 1280px 居中；≥960 生效，<960 自动铺满（响应式在 style 内处理）。
 * - 插槽：#header（页面头部）、#default（主体）、#actions（头部右侧主操作）。
 * - 入场 spring 1.0/0.3，scale(0.95)+opacity:0，禁止 scale(0)。
 */
withDefaults(
  defineProps<{
    title?: string
    breadcrumb?: string[]
    maxWidth?: '1280' | 'none'
    padding?: boolean
  }>(),
  { maxWidth: '1280', padding: true },
)
</script>

<template>
  <div class="page-container" :class="[`mw-${maxWidth}`, { 'no-pad': !padding }]">
    <header v-if="$slots.header || title || $slots.actions" class="pc-header">
      <div class="pc-head-main">
        <slot name="header">
          <div class="pc-titles">
            <nav v-if="breadcrumb && breadcrumb.length" class="pc-crumb">
              <span v-for="(b, i) in breadcrumb" :key="i" class="pc-crumb-item">
                <span v-if="i > 0" class="pc-crumb-sep">/</span>{{ b }}
              </span>
            </nav>
            <h1 v-if="title" class="pc-title">{{ title }}</h1>
          </div>
        </slot>
      </div>
      <div v-if="$slots.actions" class="pc-head-actions">
        <slot name="actions" />
      </div>
    </header>
    <main class="pc-body">
      <slot />
    </main>
  </div>
</template>

<style scoped>
.page-container {
  width: 100%;
  margin: 0 auto;
  box-sizing: border-box;
}
.page-container.mw-1280 { max-width: 1280px; }
.page-container.mw-none { max-width: none; }
.page-container:not(.no-pad) {
  padding: var(--space-4) var(--space-5);
}
.pc-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  margin-bottom: var(--space-4);
}
.pc-head-main { min-width: 0; }
.pc-titles { display: flex; flex-direction: column; gap: var(--space-1); }
.pc-crumb { display: flex; flex-wrap: wrap; align-items: center; font-size: var(--font-sm); color: var(--text-muted); }
.pc-crumb-item { display: inline-flex; align-items: center; }
.pc-crumb-sep { margin: 0 var(--space-2); color: var(--text-light); }
.pc-title {
  margin: 0;
  font-size: var(--font-2xl);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  letter-spacing: var(--tracking-tight);
  line-height: var(--leading-tight);
}
.pc-head-actions { display: flex; align-items: center; gap: var(--space-3); flex-shrink: 0; }
.pc-body { display: block; }

/* 入场：scale(0.95)+opacity:0 → 1，spring 1.0/0.3 */
.page-container {
  animation: pc-enter 0.3s var(--ease-out) both;
}
@keyframes pc-enter {
  from { opacity: 0; transform: scale(0.95); }
  to { opacity: 1; transform: scale(1); }
}

@media (prefers-reduced-motion: reduce) {
  .page-container { animation: none; }
}

/* ≥1280 居中（max-width 已控制）；960–1279 铺满交由 AdminLayout 内容区；
   <960 铺满并收紧内边距，防横向滚动 */
@media (max-width: 959px) {
  .page-container:not(.no-pad) { padding: var(--space-4) var(--space-4); }
}
@media (max-width: 1279px) {
  .page-container.mw-1280 { max-width: none; }
}
</style>
