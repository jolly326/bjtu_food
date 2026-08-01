<template>
  <view class="menu-group">
    <SectionTitle v-if="title" :title="title" noMargin />
    <view class="menu-card">
      <slot />
    </view>
  </view>
</template>

<script setup lang="ts">
import SectionTitle from '@/components/SectionTitle.vue'

defineProps<{
  title?: string
}>()
</script>

<style scoped>
.menu-group { margin: 0 var(--spacing-md) var(--spacing-lg); }
.menu-card { background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); overflow: hidden; }
</style>
