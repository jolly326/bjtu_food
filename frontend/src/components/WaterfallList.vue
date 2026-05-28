<template>
  <view class="waterfall-grid">
    <view v-for="(item, index) in props.list" :key="item[props.itemKey]" class="waterfall-item">
      <slot name="card" :item="item" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from "vue";
const props = withDefaults(defineProps<{
  list: any[]
  itemKey?: string
}>(), {
  list: () => [],
  itemKey: 'id',
})

const keyName = computed(() => props.itemKey)
</script>

<style scoped>
.waterfall-grid {
  columns: 2;
  column-gap: 20rpx;
  padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
}
.waterfall-item {
  break-inside: avoid;
  margin-bottom: 20rpx;
}
</style>
