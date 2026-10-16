<script setup lang="ts">
/**
 * 菜品卡占位（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishCardPlaceholder）。
 * 卡区加载中 / 加载失败时的占位：与 DishSwipeCard 同表面、同图位；无文案、无动画、不可点。
 */
import ImagePlaceholder from '../base/ImagePlaceholder.vue'
</script>

<template>
  <view class="ph">
    <view class="ph__photo">
      <ImagePlaceholder />
    </view>
    <view class="ph__bar ph__bar--name" />
    <view class="ph__bar ph__bar--meta" />
    <view class="ph__bar ph__bar--cta" />
  </view>
</template>

<style lang="scss" scoped>
.ph {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  background: var(--module-bg);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
}

.ph__photo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: var(--dish-photo-size);
  height: var(--dish-photo-size);
  overflow: hidden;
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
}

.ph__bar {
  background: var(--bg-placeholder);
  border-radius: var(--radius-pill);
}

.ph__bar--name {
  width: 60%;
  height: var(--font-h3);
}

.ph__bar--meta {
  width: 80%;
  height: var(--font-body);
}

.ph__bar--cta {
  width: 100%;
  height: var(--tap-target-size);
}
</style>
