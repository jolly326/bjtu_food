<template>
  <view class="image-fallback">
    <image v-if="imgSrc && imgOk" :src="imgSrc" mode="aspectFill" class="fb-img" @error="imgOk = false" />
      <view v-else class="placeholder">
        <IconSvg name="empty" :size="64" color="var(--text-tertiary)" class="placeholder-icon" />
      </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { getImageUrl } from '@/utils/image'
import IconSvg from './IconSvg.vue'

const props = withDefaults(defineProps<{
  src?: string
}>(), {
  src: '',
})

const imgSrc = computed(() => getImageUrl(props.src))

/** 图片加载状态：失败回退占位，禁止裂图 */
const imgOk = ref(true)
</script>

<style scoped>
.image-fallback {
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.fb-img {
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
  font-size: 64rpx !important;
  line-height: 1 !important;
}
</style>
