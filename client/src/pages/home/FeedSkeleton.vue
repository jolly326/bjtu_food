<template>
  <!--
    首页信息流加载骨架（MP-01）
    首屏 / 切换筛选条件期间以占位块占位，替代此前的「空白内容区」——
    空白时列表下方仍会渲染贡献卡「想吃啥没找到？告诉我们」，把「加载中」误导成「没内容」。
    结构与真实瀑布流一致（双列 + 交替高度），避免加载完成时布局跳动。
    静态渲染、无 shimmer 动画：全站动效已于 client-ui-motion 剥离，可见性不依赖动画。
  -->
  <view class="waterfall-grid" aria-busy="true">
    <view class="waterfall-col">
      <view v-for="(h, i) in leftHeights" :key="`sk-l-${i}`" class="sk-card" :style="{ height: `${h}rpx` }">
        <view class="sk-media" />
        <view class="sk-line sk-line--title" />
        <view class="sk-line sk-line--meta" />
      </view>
    </view>
    <view class="waterfall-col">
      <view v-for="(h, i) in rightHeights" :key="`sk-r-${i}`" class="sk-card" :style="{ height: `${h}rpx` }">
        <view class="sk-media" />
        <view class="sk-line sk-line--title" />
        <view class="sk-line sk-line--meta" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/** 左右两列占位块高度（rpx）：交错取值贴近真实卡片高度分布 */
const leftHeights = [360, 300, 420]
const rightHeights = [300, 420, 340]
</script>

<style scoped lang="scss">
/* 网格与 HomeContent 真实瀑布流同一套类名与尺寸，保证骨架 → 内容无跳动 */
.waterfall-grid {
  width: 100%;
  box-sizing: border-box;
  padding-bottom: var(--spacing-lg);
  display: flex;
  gap: var(--spacing-md);
}
.waterfall-col {
  flex: 1 1 0;
  width: 0;
  min-width: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-md);
}
.sk-card {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}
.sk-media {
  flex: 1;
  min-height: 0;
  border-radius: var(--radius-card);
  background: var(--bg-placeholder);
}
.sk-line {
  flex-shrink: 0;
  height: 24rpx;
  border-radius: var(--radius-pill);
  background: var(--bg-placeholder);
}
.sk-line--title { width: 70%; }
.sk-line--meta { width: 44%; height: 20rpx; }
</style>
