<script setup lang="ts">
/**
 * PageSection：页面分区卡（T-1）。
 * - props: title / collapsible? / defaultOpen?
 * - 插槽：#header-extra（标题右侧）、#default（内容）。
 * - 半透轻面 + 上下文阴影；不叠两层轻透面。
 */
import { ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

const props = withDefaults(
  defineProps<{
    title?: string
    collapsible?: boolean
    defaultOpen?: boolean
  }>(),
  { title: '', collapsible: false, defaultOpen: true },
)

const open = ref(props.defaultOpen)
function toggle() {
  if (props.collapsible) open.value = !open.value
}
</script>

<template>
  <section class="page-section" :class="{ collapsed: collapsible && !open }">
    <div class="ps-head" :class="{ clickable: collapsible }" @click="toggle">
      <div class="ps-head-left">
        <h2 v-if="title" class="ps-title">{{ title }}</h2>
        <slot name="header-extra" />
      </div>
      <el-icon v-if="collapsible" class="ps-chevron" :class="{ rotated: !open }"><ArrowDown /></el-icon>
    </div>
    <div v-show="open" class="ps-body">
      <slot />
    </div>
  </section>
</template>

<style scoped>
.page-section {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: var(--space-5) var(--space-6);
  margin-bottom: var(--space-5);
}
.ps-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}
.ps-head.clickable { cursor: pointer; user-select: none; }
.ps-head-left { display: flex; align-items: center; gap: var(--space-3); min-width: 0; }
/* 菜单牌标记：分区标题朱砂红短竖条 */
.ps-title {
  margin: 0;
  font-size: var(--font-xl);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  line-height: var(--leading-snug);
  padding-left: var(--space-3);
  position: relative;
}
.ps-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4px;
  bottom: 4px;
  width: 3px;
  border-radius: 2px;
  background: var(--color-primary);
}
.ps-chevron {
  width: 18px;
  height: 18px;
  color: var(--text-muted);
  transition: transform 0.2s var(--ease-out);
  flex-shrink: 0;
}
.ps-chevron.rotated { transform: rotate(-90deg); }
.ps-body { display: block; }

@media (prefers-reduced-motion: reduce) {
  .ps-chevron { transition: none; }
}
</style>
