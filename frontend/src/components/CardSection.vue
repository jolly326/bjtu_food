<template>
  <view class="card-section">
    <SectionTitle v-if="title" :title="title" noMargin />
    <slot />
  </view>
</template>

<script setup lang="ts">
import SectionTitle from './SectionTitle.vue'

withDefaults(defineProps<{
  title?: string
}>(), {
  title: '',
})
</script>

<style scoped>
.card-section {
  background: var(--bg-card);
  margin: var(--spacing-sm) var(--spacing-md);
  padding: var(--spacing-md);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.card-section:first-of-type {
  margin-top: var(--spacing-md);
}
</style>
