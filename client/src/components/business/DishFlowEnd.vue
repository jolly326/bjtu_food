<script setup lang="ts">
/**
 * 美味页「卡片流末尾」结束态（规格见 docs/ui/client/美味.md §1；对标 Julienne 结束页参考图）。
 *
 * - 本批卡片全部划完后出现，占据同一卡位（由 `DishCardStack` 的 `end` 插槽承载，位于卡片区域、
 *   Tab 栏之上的页面居中区），**不是悬浮层**。
 * 🔴 **它不是卡片**：无描边 / 无卡面圆角 / 无吊孔 / 无阴影 —— 直接呈现在**页面背景**上，
 *   语义是「上层卡片已全部划干净」；留白充足，是温和的结束提示而非错误页。
 * - 文案层级：衬线加粗主标题 + 浅灰副标题（紧凑不拥挤）。
 * - 两个动作：**重新浏览**（`refresh-cw` 循环箭头）/ **随机首选**（`star` 星芒，低饱和暖橙点缀）——
 *   **图标框在上、文字在下**的圆角方框（细描边、无阴影），两列等宽居中、间距适中。
 * - 纯展示 + 两个动作事件；流水线（请求 / 批次切换）由页面负责。
 */
import AppIcon from '../base/AppIcon.vue'

withDefaults(
  defineProps<{
    /** 随机首选在途：按钮禁用（防重复请求） */
    busy?: boolean
  }>(),
  { busy: false },
)

const emit = defineEmits<{
  (e: 'replay'): void
  (e: 'surprise'): void
}>()
</script>

<template>
  <view class="flowEnd">
    <!-- 文字层级：衬线加粗主标题 + 浅灰副标题 -->
    <text class="flowEnd__title">今天的推荐就到这里</text>
    <text class="flowEnd__hint">明天再来看看，新的美味马上更新</text>

    <!-- 动作区：图标框在上、文字在下；两列等宽居中、间距适中 -->
    <view class="flowEnd__actions">
      <view class="flowEnd__act" hover-class="flowEnd__act--pressed" @tap="emit('replay')">
        <view class="flowEnd__actBox">
          <AppIcon name="refresh-cw" :size="44" tone="default" />
        </view>
        <text class="flowEnd__actText">重新浏览</text>
      </view>

      <view
        class="flowEnd__act flowEnd__act--accent"
        :class="{ 'is-disabled': busy }"
        hover-class="flowEnd__act--pressed"
        @tap="emit('surprise')"
      >
        <view class="flowEnd__actBox">
          <AppIcon name="star" :size="44" tone="primary" />
        </view>
        <text class="flowEnd__actText">随机首选</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
/* 结束态：直接落在页面背景上（无卡面），内容居中纵向排布 */
.flowEnd {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 100%;
}

/* 主标题：衬线加粗、卡片标题级别、深棕居中 */
.flowEnd__title {
  font-family: var(--font-serif);
  font-size: var(--font-title);
  font-weight: var(--weight-semibold);
  line-height: 1.3;
  color: var(--text-title);
  text-align: center;
}

/* 副标题：无衬线细体浅灰、小于主标题、居中 */
.flowEnd__hint {
  margin-top: var(--spacing-lg);
  font-size: var(--font-small);
  line-height: 1.5;
  color: var(--text-tertiary);
  text-align: center;
}

/* 动作区：两列等宽居中、间距适中；与副标题留出舒适呼吸位 */
.flowEnd__actions {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: calc(var(--spacing-xl) + var(--spacing-md));
  margin-top: calc(var(--spacing-2xl) + var(--spacing-lg));
}

/* 单个动作：图标框在上、文字在下，整列可点 */
.flowEnd__act {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-2xl);
}

/* 图标框：小圆角细描边方框、**无底色无阴影**（直接显页面背景），图标居中 */
.flowEnd__actBox {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: var(--flowend-icon-box);
  height: var(--flowend-icon-box);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-btn);
}

/* 随机首选：低饱和暖橙点缀（仅描边 + 图标色调，文字仍为深棕，不做高亮主色按钮） */
.flowEnd__act--accent .flowEnd__actBox {
  border-color: var(--color-primary);
}

/* 文字：深棕居中、字号小于图标，与图标框保持舒适间距 */
.flowEnd__actText {
  font-size: var(--font-small);
  line-height: 1;
  color: var(--text-title);
  white-space: nowrap;
}

.flowEnd__act--pressed {
  opacity: 0.6;
}
</style>