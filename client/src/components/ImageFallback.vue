<template>
  <view class="image-fallback">
    <image v-if="imgSrc && imgOk" :src="imgSrc" mode="aspectFill" class="fb-img" @error="imgOk = false" />
      <!-- 占位统一走 `ImagePlaceholder`（灰底 + 图标）；**头像**保留人形图标语义
           （「无用户」≠「图片损坏」，故传 `user`，底色仍与全站占位一致） -->
      <ImagePlaceholder v-else name="user" :size="64" :aria-label="ariaLabel" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { getImageUrl } from '@/utils/image'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'

const props = withDefaults(defineProps<{
  src?: string
  /** 占位图的读屏标签（头像场景应带上下文，如「张同学的头像」） */
  ariaLabel?: string
}>(), {
  src: '',
  ariaLabel: '头像占位',
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
</style>
