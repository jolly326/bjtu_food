<script setup lang="ts">
import zhaoxiangji from '@/static/icon/zhaoxiangji.svg'

defineProps<{
  imageUrl: string
  imageCount: number
  placeholderEmoji?: string
}>()

const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <div class="entity-image" @click="emit('click')">
    <div v-if="imageUrl" class="image-view">
      <img :src="imageUrl" alt="" />
      <span v-if="imageCount > 1" class="image-badge">+{{ imageCount - 1 }}</span>
    </div>
    <div v-else class="image-empty">
      <img :src="zhaoxiangji" class="empty-icon-img" alt="" />
      <span class="empty-tip">添加图片</span>
    </div>
  </div>
</template>

<style scoped>
.entity-image {
  width: 160px;
  height: 160px;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  flex-shrink: 0;
  border: 1px solid #f0f0f0;
  transition: border-color .2s;
}
.entity-image:hover {
  border-color: var(--color-primary);
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
  bottom: 8px;
  right: 8px;
  background: rgba(0,0,0,.6);
  color: #fff;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 8px;
  font-weight: 500;
}
.image-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: #fafafa;
  color: #bbb;
}
.empty-icon-img {
  width: 32px;
  height: 32px;
  opacity: .5;
}
.empty-tip {
  font-size: 13px;
}
</style>
