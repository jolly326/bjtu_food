<script setup lang="ts">
/**
 * 群体风味评级（规格见 docs/ui/client/公共组件与形态基线.md §2.2 FlavorRating）。
 *
 * - 左：评级字母 `S` / `A` / `B` / `C` / `D` —— 大号加粗浅灰水印，作卡片背景层装饰
 *   （对标 Julienne 左上大号淡数字）；**待评（样本 < 3 条）不渲染**。
 * - 右：两行「风味评级 / 档位词」，整体右对齐、全衬线（对标 Julienne 右上 Skill Level）；
 *   档位词映射只在本组件内维护。
 * - 纯展示、无交互；落位由宿主决定（美味卡绝对定位在顶部信息带）。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 群体评级字母；`null` = 待评 */
    level?: string | null
  }>(),
  { level: null },
)

/** 档位词映射（唯一处）：只用中性口碑词，不出现劝退 / 负面表述 */
const LABELS: Record<string, string> = {
  S: '臻荐',
  A: '极荐',
  B: '推荐',
  C: '尚可',
  D: '平实',
}

const label = computed(() => (props.level ? (LABELS[props.level] ?? '待评') : '待评'))
</script>

<template>
  <view class="flavor">
    <text v-if="level" class="flavor__level">{{ level }}</text>
    <view class="flavor__tag">
      <text class="flavor__cap">风味评级</text>
      <text class="flavor__label">{{ label }}</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.flavor {
  display: flex;
  align-items: flex-start;
  width: 100%;
  pointer-events: none;
}

/* 左上：大号加粗浅灰水印字母（卡片背景层装饰，不遮挡上层内容） */
.flavor__level {
  font-family: var(--font-serif);
  font-size: calc(var(--font-hero) + var(--font-display) + var(--spacing-md));
  font-weight: var(--weight-bold);
  line-height: 1;
  color: var(--text-tertiary);
  opacity: 0.45;
}

/* 右上：两行（风味评级 / 档位词）—— 整体右对齐 */
.flavor__tag {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--spacing-3xs);
  margin-left: auto;
}

/* 第一行小字：衬线 + 字距，弱化为标签 */
.flavor__cap {
  font-family: var(--font-serif);
  font-size: var(--font-small);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
  letter-spacing: 0.08em;
}

/* 第二行档位词：大号粗体衬线（标签小字在上、等级值大字在下） */
.flavor__label {
  font-family: var(--font-serif);
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  color: var(--text-title);
}
</style>
