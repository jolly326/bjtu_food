<script setup lang="ts">
/**
 * 菜品卡占位（规格见 docs/ui/client/公共组件与形态基线.md §2.2 DishCardPlaceholder）。
 * 卡区加载中 / 加载失败时的占位：**与 DishSwipeCard 完全同构** —— 同表面（描边 + 阴影 + 圆角）、
 * 同吊孔、同顶行（序号 / 标签）位置、同**圆形**图位、同内距与部件间距、**同位置保留按钮**，
 * 使数据到位时布局不跳动。
 *
 * 🔴 文本区（序号 / 标签 / 菜名 / 元信息）**不占位、不画横条** —— 只留高度（透明），留空即可。
 * 🔴 按钮**复用 `WantButton`**（`disabled` 态）：文案与真实卡同源，图片能否加载出都照样显示。
 */
import ImagePlaceholder from '../base/ImagePlaceholder.vue'
import WantButton from './WantButton.vue'
</script>

<template>
  <view class="ph">
    <view class="ph__punch" />
    <view class="ph__top">
      <view class="ph__slot ph__slot--ordinal" />
      <view class="ph__slot ph__slot--tag" />
    </view>
    <view class="ph__photo">
      <ImagePlaceholder />
    </view>
    <view class="ph__slot ph__slot--name" />
    <view class="ph__slot ph__slot--meta" />
    <WantButton disabled />
  </view>
</template>

<style lang="scss" scoped>
.ph {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-lg);
  /* 与 DishSwipeCard 同内距（上小下大 ⇒ 图位同样偏上） */
  padding: var(--spacing-lg) var(--spacing-xl) var(--spacing-2xl);
  background: var(--bg-card);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
}

.ph__punch {
  width: var(--dish-punch-size);
  height: var(--dish-punch-size);
  background: var(--bg-page);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-circle);
}

/* 顶行：与真实卡的「序号 / 标签」占位对齐（不着色） */
.ph__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  width: 100%;
}

.ph__photo {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  width: var(--dish-photo-size);
  height: var(--dish-photo-size);
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
}

/* 文本区：只占高度、不着色（不画横条） */
.ph__slot--ordinal {
  height: var(--font-display);
}

.ph__slot--tag {
  height: var(--font-h2);
}

.ph__slot--name {
  height: var(--font-h2);
}

.ph__slot--meta {
  height: var(--font-body);
}
</style>
