<template>
  <view class="image-fallback">
    <image v-if="imgSrc" :src="imgSrc" mode="aspectFill" />
    <view v-else class="placeholder">
      <image class="placeholder-icon" src="/static/icons/food.svg" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { getImageUrl } from '@/utils/image'

const props = withDefaults(defineProps<{
  src?: string
}>(), {
  src: '',
})

const imgSrc = computed(() => getImageUrl(props.src))
</script>

<style scoped>
.image-fallback {
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.image-fallback image {
  width: 100%;
  height: 100%;
}
.placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
}
.placeholder-icon {
  width: 64rpx !important;
  height: 64rpx !important;
}
</style>
