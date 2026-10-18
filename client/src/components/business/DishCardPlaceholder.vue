<script setup lang="ts">
/**
 * 食单页栈内骨架卡（加载态；与 DishSwipeCard 同构）。
 * 卡面 / 三层堆叠副本 / 吊孔由 PaperCard 承载；同内距、同圆形图位；无旋转 loading。
 */
import PaperCard from './PaperCard.vue'
import WantButton from './WantButton.vue'
</script>

<template>
  <PaperCard>
    <view class="ph__content">
      <view class="ph__photo" />
      <view class="ph__slot ph__slot--name" />
      <view class="ph__slot ph__slot--meta" />
      <view class="ph__slot ph__slot--rating" />
    </view>

    <view class="ph__btn">
      <WantButton disabled />
    </view>
  </PaperCard>
</template>

<style lang="scss" scoped>
/* 与真实卡一致：内容居中排布 */
.ph__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  margin-top: calc(var(--spacing-2xl) + var(--font-hero));
}

.ph__photo {
  width: var(--dish-photo-size);
  height: var(--dish-photo-size);
  margin-bottom: var(--spacing-lg);
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
  margin-top: var(--spacing-md);
  width: 80%;
  height: var(--font-small);
}

.ph__slot--rating {
  margin-top: var(--spacing-md);
  align-self: flex-end;
  width: 30%;
  height: var(--font-small);
}

.ph__btn {
  position: relative;
  z-index: 1;
  width: 100%;
  margin-top: var(--spacing-xl);
}
</style>
