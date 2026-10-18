<script setup lang="ts">
/**
 * 纸片卡外壳（规格见 docs/ui/client/公共组件与形态基线.md §2.2 PaperCard）。
 *
 * 承载 Julienne 纸片卡的卡面元素（美味页卡片族共用）：
 * - 单层卡面：与页面同色的无感填充 + 细浅灰描边 + 大圆角 + **低透明柔和浅阴影**（悬浮感）。
 * - 顶部吊孔：上沿中线小圆镂空，**完全约束在卡片边框内部**（纸片本身的打孔装饰）。
 * 🔴 三层纸片堆叠由 `DishCardStack` 的卡片栈承担（顶层卡 + 后方两张收窄上移卡），
 * **不在每张卡上自带多层纹路**（口径见基线 §2.2 DishCardStack）。
 * 内容经默认插槽注入（顶层信息带 / 圆形图 / 文本 / 按钮等由宿主排版）。
 */
</script>

<template>
  <view class="paper">
    <!-- 顶部吊孔：上沿中线，完全在卡片边框内 -->
    <view class="paper__hole" />
    <!-- 卡面：承载全部内容 -->
    <view class="paper__inner">
      <slot />
    </view>
  </view>
</template>

<style lang="scss" scoped>
.paper {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  box-sizing: border-box;
  width: 100%;
  /* 减少 webview 变换重绘抖动 */
  will-change: transform;
  backface-visibility: hidden;
}

/* 卡面（单层）：与页面同色的无感填充 ⇒ 视觉上融于页面、无白卡感；
   同时保证不透明，挡住所下层卡片预览穿帮。取 --bg-page 为「组件不声明页面底色」的登记例外。
   阴影取 --shadow-card-stack：低透明度、柔和弥散的浅阴影（悬浮轻盈感；禁高对比深色阴影）。 */
.paper__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  box-sizing: border-box;
  width: 100%;
  padding: var(--spacing-2xl) var(--spacing-xl) var(--spacing-2xl);
  background: var(--bg-page);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card-stack);
}

/* 顶部吊孔：上沿中线、居于卡片内部（不溢出上边界）；圆环 + 环内填页面同色（镂空感） */
.paper__hole {
  position: absolute;
  top: var(--spacing-sm);
  left: 50%;
  z-index: 2;
  box-sizing: border-box;
  width: var(--spacing-xl);
  height: var(--spacing-xl);
  background: var(--bg-page);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-circle);
  transform: translateX(-50%);
  pointer-events: none;
}
</style>
