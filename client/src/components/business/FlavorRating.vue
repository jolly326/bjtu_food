<script setup lang="ts">
/**
 * 群体风味评级（规格见 docs/ui/client/公共组件与形态基线.md §2.2 FlavorRating）。
 *
 * - 左：评级字母 `S` / `A` / `B` / `C` / `D`；**待评（样本 < 3 条）不渲染**。
 * - 右：两行「风味评级 / 档位词」；档位词映射只在本组件内维护。
 * - 纯展示、无交互；落位由宿主决定（推荐卡绝对定位在顶部信息位；记忆卡置于卡内首行）。
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

/* 左上：大号淡衬线字母 */
.flavor__level {
  font-family: var(--font-serif);
  font-size: var(--font-title);
  line-height: 1;
  color: var(--text-tertiary);
  opacity: 0.45;
}

/* 右上：两行（风味评级 / 档位词） */
.flavor__tag {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--spacing-3xs);
  margin-left: auto;
}

.flavor__cap {
  font-size: var(--font-small);
  color: var(--text-tertiary);
  letter-spacing: 0.08em;
}

.flavor__label {
  font-family: var(--font-serif);
  font-size: var(--font-h3);
  font-weight: var(--weight-semibold);
  color: var(--text-title);
}
</style>
