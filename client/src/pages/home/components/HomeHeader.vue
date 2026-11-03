<script setup lang="ts">
/**
 * 菜品首页页头（docs/ui/client/菜品首页.md §1）。
 *
 * 轻量 brand strip：安全区 + 一行内容区（wordmark + 日期/提示），
 * 无操作按钮、无阴影；内容区高 = 微信胶囊行高，品牌块与胶囊垂直居中对齐。
 */
import { computed } from 'vue'

defineOptions({ options: { virtualHost: true } })

defineProps<{ topInset: number; headerHeight: number }>()

const todayLabel = computed(() => {
  const now = new Date()
  return `${now.getMonth() + 1} 月 ${now.getDate()} 日 今天吃什么`
})
</script>

<template>
  <view class="header" :style="{ paddingTop: topInset + 'px' }">
    <view class="header__inner" :style="{ height: headerHeight + 'px' }">
      <text class="header__wordmark">知行食记</text>
      <text class="header__subtitle">{{ todayLabel }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.header {
  position: relative;
  z-index: 2;
  flex-shrink: 0;
  background: var(--bg-page);
}

.header__inner {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: var(--spacing-2xs);
  box-sizing: border-box;
  height: var(--header-height);
  padding: 0 var(--page-gutter);
}

.header__wordmark {
  font-size: var(--font-display);
  /* 行高收紧到 1.1：两行文案总高才能落进胶囊行，视觉中心与胶囊中心重合 */
  line-height: 1.1;
  color: var(--text-title);
  letter-spacing: 0.02em;
}

.header__subtitle {
  font-size: var(--font-caption);
  line-height: 1.1;
  color: var(--text-body);
}
</style>
