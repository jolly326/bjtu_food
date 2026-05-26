<template>
  <view class="header-wrap" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="header">
      <view class="back-area" v-if="showBack" @tap="handleBack">
        <image src="/static/icons/back.svg" class="back-arrow" />
      </view>
      <text class="title">{{ title }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
const statusBarHeight = ref(0)

onMounted(() => {
  const info = uni.getSystemInfoSync()
  statusBarHeight.value = info.statusBarHeight || 20
})

withDefaults(defineProps<{
  title?: string
  showBack?: boolean
}>(), {
  title: '食在交大',
  showBack: false,
})

function handleBack() {
  uni.navigateBack()
}
</script>

<style scoped>
.header-wrap {
  background: var(--color-primary);
}
.header {
  display: flex;
  padding: 0 var(--spacing-lg) 20rpx;
  justify-content: center;
  align-items: center;
  position: relative;
}
.back-area {
  position: absolute;
  left: var(--spacing-sm);
  width: 80rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.back-arrow {
  width: 48rpx;
  height: 48rpx;
}

.title {
  font-size: var(--font-h2);
  font-weight: 500;
  color: var(--text-white);
  display: block;
}
</style>
