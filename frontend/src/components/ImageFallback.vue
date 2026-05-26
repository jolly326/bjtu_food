<template>
  <view class="image-fallback">
    <image v-if="src" :src="src" mode="aspectFill" />
    <view v-else class="placeholder">
      <text class="placeholder-icon">{{ placeholder }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  src?: string
  placeholder?: string
}>(), {
  src: '',
  placeholder: '🍽️',
})
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
  font-size: 64rpx;
}
</style>
