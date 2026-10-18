<script setup lang="ts">
/**
 * 美味页「卡片流末尾」结束卡（规格见 docs/ui/client/美味.md §1；对标 Julienne 结束页参考图）。
 *
 * - 本批卡片全部划完后出现，占据同一卡位（由 `DishCardStack` 的 `end` 插槽承载，位于卡片区域、
 *   Tab 栏之上的页面居中区），**不是悬浮层**。
 * - 文案层级：衬线加粗主标题 + 浅灰副标题（紧凑不拥挤）。
 * - 两个动作：**重新浏览**（`refresh-cw` 循环箭头）/ **随机首选**（`star` 星芒，低饱和暖橙点缀）——
 *   **图标框在上、文字在下**的圆角方框（细描边、白底、无阴影），间距适中、等宽平衡。
 * - 表面与栈卡同卡面（PaperCard：同色卡面 + 顶部吊孔 + 柔和浅阴影）。
 * - 纯展示 + 两个动作事件；流水线（请求 / 批次切换）由页面负责。
 */
import AppIcon from '../base/AppIcon.vue'
import PaperCard from './PaperCard.vue'

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
  <PaperCard>
    <view class="flowEnd">
      <!-- 文字层级：衬线加粗主标题 + 浅灰副标题 -->
      <text class="flowEnd__title">今天的推荐就到这里</text>
      <text class="flowEnd__hint">明天再来看看，新的美味马上更新</text>

      <!-- 动作区：图标框在上、文字在下；两列等宽、间距适中 -->
      <view class="flowEnd__actions">
        <view class="flowEnd__act" hover-class="flowEnd__act--pressed" @tap="emit('replay')">
          <view class="flowEnd__actBox">
            <AppIcon name="refresh-cw" :size="28" tone="default" />
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
            <AppIcon name="star" :size="28" tone="primary" />
          </view>
          <text class="flowEnd__actText">随机首选</text>
        </view>
      </view>
    </view>
  </PaperCard>
</template>

<style lang="scss" scoped>
/* 与栈卡同卡面：内容居中纵向排布（表面由 PaperCard 承担） */
.flowEnd {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
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

/* 副标题：无衬线细体浅灰、小于主标题、紧凑不拥挤 */
.flowEnd__hint {
  font-size: var(--font-small);
  line-height: 1.5;
  color: var(--text-tertiary);
  text-align: center;
}

/* 动作区：两列等宽居中、间距适中 */
.flowEnd__actions {
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: var(--spacing-xl);
  margin-top: var(--spacing-xl);
}

/* 单个动作：图标框在上、文字在下，整列可点（命中区含框 + 文字） */
.flowEnd__act {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
}

/* 图标框：小圆角细描边方框、白底（透出卡面）、无阴影；视觉边长小于命中区 */
.flowEnd__actBox {
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: calc(var(--tap-target-size) - var(--spacing-md));
  height: calc(var(--tap-target-size) - var(--spacing-md));
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-btn);
}

/* 随机首选：低饱和暖橙点缀（仅描边 + 图标色调，文字仍为深棕，不做高亮主色按钮） */
.flowEnd__act--accent .flowEnd__actBox {
  border-color: var(--color-primary);
}

/* 文字：深棕居中 */
.flowEnd__actText {
  font-size: var(--font-subtitle);
  line-height: 1;
  color: var(--text-title);
  white-space: nowrap;
}

.flowEnd__act--pressed {
  opacity: 0.6;
}
</style>