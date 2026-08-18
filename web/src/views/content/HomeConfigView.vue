<script setup lang="ts">
/**
 * HomeConfigView：首页配置（轮播 / 广播 / 分类 / 活动）。
 * 仅几个轻量分段，避免与顶层分类卡重复。
 */
import { ref } from 'vue'
import BannerManageView from '@/views/banner/BannerManageView.vue'
import BroadcastManage from '@/views/content/BroadcastManage.vue'
import CategoryManage from '@/views/content/CategoryManage.vue'
import ActivityManage from '@/views/content/ActivityManage.vue'

const active = ref<'banner' | 'broadcast' | 'category' | 'activity'>('banner')
const segments = [
  { key: 'banner', label: '轮播' },
  { key: 'broadcast', label: '广播' },
  { key: 'category', label: '分类' },
  { key: 'activity', label: '活动' },
] as const
</script>

<template>
  <div>
    <div class="seg-tabs">
      <button
        v-for="s in segments"
        :key="s.key"
        class="seg-tab"
        :class="{ on: active === s.key }"
        v-press
        type="button"
        @click="active = s.key"
      >{{ s.label }}</button>
    </div>
    <BannerManageView v-if="active === 'banner'" />
    <BroadcastManage v-else-if="active === 'broadcast'" />
    <CategoryManage v-else-if="active === 'category'" />
    <ActivityManage v-else />
  </div>
</template>

<style scoped>
/* 轻量分段（仅 2 项，顶部小字分类） */
.seg-tabs { display: inline-flex; gap: var(--space-2); margin-bottom: var(--space-4); }
.seg-tab {
  padding: var(--space-2) var(--space-4);
  border: none;
  background: none;
  border-radius: var(--radius);
  font-size: var(--font-base);
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out);
}
.seg-tab:hover { background: var(--bg-soft); color: var(--text-primary); }
.seg-tab.on { background: var(--color-primary-bg); color: var(--color-primary); font-weight: var(--weight-semibold); }
</style>
