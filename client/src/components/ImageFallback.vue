<template>
  <view class="image-fallback">
    <image v-if="imgSrc && imgOk" :src="imgSrc" mode="aspectFill" class="fb-img" @error="imgOk = false" />
      <!-- 占位统一走 `ImagePlaceholder`（灰底 + 图标）；**头像**保留人形图标语义
           （「无用户」≠「图片损坏」，故传 `user`，底色仍与全站占位一致） -->
      <ImagePlaceholder v-else name="user" :size="64" aria-label="头像占位" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getThumbImageUrl } from '@/utils/image'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'

const props = withDefaults(defineProps<{
  src?: string
}>(), {
  src: '',
})

// 本组件专用于**头像小图位**（占位恒为 `user` 人形图标）⇒ 走缩略图推导，避免小图位拉原图
const imgSrc = computed(() => getThumbImageUrl(props.src))

/** 图片加载状态：失败回退占位，禁止裂图 */
const imgOk = ref(true)
// ⚠️ 破图态必须随 `src` 变化复位：组件实例复用（同 key 换图）时旧破图态会残留，
// 导致换上新有效图仍永久显示占位。
watch(() => props.src, () => { imgOk.value = true })
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
