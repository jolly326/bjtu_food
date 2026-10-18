<script setup lang="ts">
/**
 * 美味页栈内骨架卡（加载态；与 DishSwipeCard 同构）。
 * 卡面 / 吊孔由 PaperCard 承载；同内距、同 420rpx 圆形图位、同纵向节奏；无旋转 loading。
 * 🔴 不画堆叠副本 —— 一叠纸片的层次由 `DishCardStack` 承担。
 */
import PaperCard from './PaperCard.vue'
import WantButton from './WantButton.vue'

/** 宿主节点虚拟化：组件根直接参与卡片栈布局 */
defineOptions({ options: { virtualHost: true } })
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
/* 与真实卡一致：内容居中排布，图片上方留白同值（信息带 + 大号字母 + 呼吸位） */
.ph__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 0;
  width: 100%;
  margin-top: calc(var(--spacing-2xl) * 2 + var(--font-hero));
}

.ph__photo {
  flex: none;
  width: var(--dish-photo-size);
  height: var(--dish-photo-size);
  margin-bottom: var(--spacing-xl);
  background: var(--bg-placeholder);
  border-radius: var(--radius-circle);
}

.ph__slot {
  background: var(--bg-placeholder);
  border-radius: var(--radius-xs);
}

.ph__slot--name {
  width: 60%;
  height: var(--font-hero);
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
