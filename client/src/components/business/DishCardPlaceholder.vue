<script setup lang="ts">
/**
 * 美食页栈内骨架卡（加载态；与 DishSwipeCard 同构）。
 * 同内距（96/48/24）、同圆形图位、同内容居中、同底部按钮位；无旋转 loading。
 */
import WantButton from './WantButton.vue'
</script>

<template>
  <view class="ph">
    <view class="ph__inner">
      <view class="ph__content">
        <view class="ph__photo" />
        <view class="ph__slot ph__slot--name" />
        <view class="ph__slot ph__slot--meta" />
        <view class="ph__slot ph__slot--rating" />
      </view>

      <view class="ph__btn">
        <WantButton disabled />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 与真实卡同构：外层描边悬浮层 + 内层白卡 */
.ph {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  box-sizing: border-box;
  width: 100%;
  padding: var(--spacing-xs);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
}

.ph__inner {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  box-sizing: border-box;
  width: 100%;
  padding: calc(var(--spacing-2xl) * 2) var(--spacing-xl) var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card-stack);
}

/* 与真实卡一致：内容居中偏上 */
.ph__content {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-md);
  width: 100%;
  margin-top: var(--spacing-md);
}

.ph__photo {
  width: var(--dish-photo-size);
  height: var(--dish-photo-size);
  margin-bottom: var(--spacing-2xl);
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
}

.ph__slot {
  background: var(--bg-placeholder);
  border-radius: var(--radius-xs);
}

.ph__slot--name {
  width: 60%;
  height: var(--font-display);
}

.ph__slot--meta {
  width: 80%;
  height: var(--font-small);
}

.ph__slot--rating {
  align-self: flex-end;
  width: 30%;
  height: var(--font-small);
}

.ph__btn {
  width: 100%;
  margin-top: var(--spacing-md);
}
</style>
