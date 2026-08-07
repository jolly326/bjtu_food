<template>
  <view class="header-wrap" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="header">
      <view class="back-area" v-if="showBack" @tap="handleBack" :class="{ 'back-area-custom': customBack }">
        <IconSvg name="arrow-left" :size="44" color="var(--color-on-primary-surface)" class="back-arrow" />
      </view>
      <text class="title">{{ title }}</text>
      <view class="action-area" v-if="$slots.action">
        <slot name="action" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import IconSvg from './IconSvg.vue'
const statusBarHeight = ref(0)

onMounted(() => {
  // 不再使用已弃用的 wx.getSystemInfoSync，改用 wx.getWindowInfo().statusBarHeight
  // @ts-ignore - 跨端兼容（H5 无 wx，退化为 20）
  const win = (typeof wx !== 'undefined' && wx.getWindowInfo) ? wx.getWindowInfo() : null
  statusBarHeight.value = (win && win.statusBarHeight) || 20
})

const props = withDefaults(defineProps<{
  title?: string
  showBack?: boolean
  /** 自定义返回：为 true 时不调用 uni.navigateBack，仅 emit('back') 由页面自行处理（如退出筛选态） */
  customBack?: boolean
}>(), {
  title: '知行食记',
  showBack: false,
  customBack: false,
})

const emit = defineEmits<{ (e: 'back'): void }>()

function handleBack() {
  if (props.customBack) {
    emit('back')
  } else {
    uni.navigateBack()
  }
}
</script>

<style scoped>
.header-wrap {
  position: sticky;
  top: 0;
  z-index: 100;
  /* 主色表面（大面积）：浅色=品牌红，深色=暗陶土红（见 --color-primary-surface） */
  background: var(--color-primary-surface);
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
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.back-area:active { transform: scale(var(--press-scale)); }
.back-arrow {
  font-size: 44rpx;
  line-height: 1;
}
.action-area {
  position: absolute;
  right: var(--spacing-sm);
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.title {
  font-size: var(--font-h2);
  font-weight: var(--weight-medium);
  color: var(--color-on-primary-surface);
  display: block;
}
</style>
