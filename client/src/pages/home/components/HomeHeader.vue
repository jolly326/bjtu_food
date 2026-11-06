<script setup lang="ts">
/**
 * 菜品首页页头（docs/ui/client/菜品首页.md）。
 *
 * 轻量 brand strip：安全区 + 一行内容区（wordmark + 主题切换 + #feedback 插槽），两端对齐。
 * 高度按微信胶囊行运行时测量值注入；右侧内距为原生胶囊预留，避免反馈入口被遮挡。
 */
import ThemeToggle from './ThemeToggle.vue'

defineOptions({ options: { virtualHost: true } })

defineProps<{
  topInset: number
  headerHeight: number
  /** 右侧为微信胶囊预留的宽度（px） */
  rightReserve: number
}>()
</script>

<template>
  <view class="header" :style="{ paddingTop: topInset + 'px' }">
    <view
      class="header__inner"
      :style="{ height: headerHeight + 'px', paddingRight: rightReserve + 'px' }"
    >
      <view class="header__brand">
        <text class="header__wordmark">知行食记</text>
        <ThemeToggle />
      </view>
      <slot name="feedback" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.header {
  position: relative;
  z-index: var(--z-header);
  flex-shrink: 0;
  background: var(--bg-page);
}

.header__inner {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
  padding: 0 var(--page-gutter);
}

/* 品牌组：wordmark 与主题切换同排，切换入口紧贴标题右缘 */
.header__brand {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  gap: var(--spacing-xs);
}

/* 页头与卡片信息区共用字号档：wordmark 与内容主标题同级 */
.header__wordmark {
  flex: 0 1 auto;
  min-width: 0;
  overflow: hidden;
  font-size: var(--font-title);
  line-height: 1.1;
  color: var(--text-title);
  letter-spacing: 0.02em;
  white-space: nowrap;
  text-overflow: ellipsis;
}
</style>
