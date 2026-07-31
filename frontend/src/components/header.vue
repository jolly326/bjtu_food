<template>
  <view class="header-wrap glass" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="header">
      <view class="back-area" v-if="showBack" @tap="handleBack">
        <text class="back-arrow">{{ EMOJI.back }}</text>
      </view>
      <text class="title">{{ title }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { EMOJI } from '@/utils/emoji'
const statusBarHeight = ref(0)

onMounted(() => {
  // 不再使用已弃用的 wx.getSystemInfoSync，改用 wx.getWindowInfo().statusBarHeight
  // @ts-ignore - 跨端兼容（H5 无 wx，退化为 20）
  const win = (typeof wx !== 'undefined' && wx.getWindowInfo) ? wx.getWindowInfo() : null
  statusBarHeight.value = (win && win.statusBarHeight) || 20
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
  position: sticky;
  top: 0;
  z-index: 100;
  /* 半透主色材质（.glass 提供 blur + 真机降级），顶部高光边模拟光线 */
  background: var(--color-primary-glass);
  border-bottom: 1rpx solid var(--glass-highlight-soft);
}
.header {
  display: flex;
  padding: 0 var(--spacing-lg) var(--spacing-md);
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
  font-size: 44rpx;
  line-height: 1;
}

.title {
  font-size: var(--font-h2);
  font-weight: 500;
  color: var(--text-white);
  display: block;
}
</style>
