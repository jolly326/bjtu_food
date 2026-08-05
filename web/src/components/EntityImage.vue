<script setup lang="ts">
import { Picture } from '@element-plus/icons-vue'

defineProps<{
  imageUrl: string
  imageCount: number
}>()

const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <div class="entity-image" v-press role="button" tabindex="0" aria-label="查看/选择图片" @click="emit('click')" @keydown.enter.prevent="emit('click')" @keydown.space.prevent="emit('click')">
    <div v-if="imageUrl" class="image-view">
      <img :src="imageUrl" alt="" />
      <span v-if="imageCount > 1" class="image-badge">+{{ imageCount - 1 }}</span>
    </div>
    <div v-else class="image-empty">
      <el-icon class="empty-icon-img"><Picture /></el-icon>
      <span class="empty-tip">添加图片</span>
    </div>
  </div>
</template>

<style scoped>
.entity-image {
  width: 160px;
  height: 160px;
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  flex-shrink: 0;
  border: 1px solid var(--border-light);
  transition: border-color .2s var(--ease-out), transform 160ms var(--ease-out);
}
.entity-image:hover {
  border-color: var(--color-primary);
}
.entity-image:active {
  transform: scale(var(--press-scale));
}
.entity-image:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}
.image-view {
  position: relative;
  width: 100%;
  height: 100%;
}
.image-view img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.image-badge {
  position: absolute;
  bottom: var(--space-2);
  right: var(--space-2);
  background: color-mix(in srgb, var(--text-primary) 60%, transparent);
  color: var(--color-on-primary);
  font-size: var(--font-xs);
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  font-weight: var(--weight-medium);
}
.empty-icon-img {
  width: 32px;
  height: 32px;
  opacity: .5;
}
.empty-tip {
  font-size: var(--font-sm);
}
.image-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  background: var(--bg-soft);
  color: var(--text-light);
}
</style>
