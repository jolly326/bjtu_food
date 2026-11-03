<script setup lang="ts">
/** 单个菜品图标（docs/ui/client/公共组件与形态基线.md §2.2 `DishTile`）。 */
import type { DishVO } from '@/api'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import { IMAGE } from '@/constants'
import { useImageSource } from '@/hooks'

defineOptions({ options: { virtualHost: true } })

const props = defineProps<{ dish: DishVO }>()

const emit = defineEmits<{
  (e: 'open', dish: DishVO): void
}>()

const { src, onError } = useImageSource(() => props.dish.imageUrl, IMAGE.TILE_THUMB_WIDTH)
</script>

<template>
  <view class="tile" hover-class="tile--press" @tap="emit('open', dish)">
    <view class="tile__cover">
      <image v-if="src" class="tile__img" :src="src" mode="aspectFill" @error="onError" />
      <view v-else class="tile__ph"><ImagePlaceholder /></view>
    </view>
    <text class="tile__name">{{ dish.name }}</text>
  </view>
</template>

<style lang="scss" scoped>
.tile {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  width: 100%;
  transition:
    transform var(--duration-fast) var(--ease-out),
    opacity var(--duration-fast) var(--ease-out);
}

.tile--press {
  opacity: 0.7;
  transform: scale(0.96);
}

.tile__cover {
  position: relative;
  height: 0;
  padding-top: 100%;
  overflow: hidden;
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-card);
  background: var(--bg-placeholder);
}

.tile__img,
.tile__ph {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.tile__name {
  display: block;
  overflow: hidden;
  font-size: var(--font-subtitle);
  color: var(--text-title);
  text-align: center;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
