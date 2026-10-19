<script lang="ts">
export default {
  options: { virtualHost: true },
}
</script>
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

/** 宿主节点虚拟化：组件根直接参与单卡 flex 布局（卡片才能撑满槽位高度） */
defineOptions({ options: { virtualHost: true } })

const props = withDefaults(
  defineProps<{
    /** 卡面阴影（默认全量卡片阴影；堆叠后方层传更弱的两档阴影实现景深） */
    shadow?: string
  }>(),
  { shadow: '' },
)
</script>

<template>
  <view class="paper">
    <!-- 顶部吊孔：上沿中线，完全在卡片边框内 -->
    <view class="paper__hole" />
    <!-- 卡面：承载全部内容 -->
    <view class="paper__inner" :style="props.shadow ? { boxShadow: props.shadow } : undefined">
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
  /* 🔴 min-height: 0 —— 让卡片高度服从宿主槽位（不被内容顶高、不溢出槽底） */
  min-height: 0;
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
  /* 🔴 min-height: 0 + overflow: hidden —— 高度服从卡面，内容超出时裁在卡内（不溢出底边） */
  min-height: 0;
  overflow: hidden;
  box-sizing: border-box;
  width: 100%;
  padding: var(--dish-card-pad-y) var(--dish-card-pad-x) var(--dish-card-pad-y);
  background: var(--bg-card);
  border: 1rpx solid var(--border-color);
  border-radius: var(--dish-card-radius);
  box-shadow: var(--shadow-card-stack);
}

/* 顶部吊孔：上沿中线、居于卡片内部（不溢出上边界）；孔位下移 + 加大（纸片打孔的分量感）
   用**内阴影**表现「打孔下沉」的层级（而不是平整圆片）：底部内影 + 细描边 */
.paper__hole {
  position: absolute;
  top: var(--spacing-lg);
  left: 50%;
  z-index: 2;
  box-sizing: border-box;
  width: var(--spacing-2xl);
  height: var(--spacing-2xl);
  background: var(--bg-page);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-circle);
  box-shadow: var(--shadow-inset);
  transform: translateX(-50%);
  pointer-events: none;
}
</style>
