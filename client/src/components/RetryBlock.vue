<template>
  <!-- 加载失败重试块（公共组件，P3-03 上提）
       原在 my-reviews / notifications / HomeContent / find 四处完整复制（结构 / 文案 / 按压语言 / 无障碍属性一致），
       按 §2「多包高频复用者上提 components」收敛为唯一实现。
       视觉基线：凹陷面 bg-soft + 大圆角 + 次级文字色 + 居中极简行内块；整块可点（@tap），
       按压反馈走 opacity（小程序端按压语言 = bg-soft / opacity，不用 transform: scale）。
       无障碍：整块 role="button" + aria-label（默认可覆写，find 页文案为「搜索失败，点击重试」）。 -->
  <view
    class="retry-block"
    :class="{ 'has-margin': margin }"
    role="button"
    :aria-label="ariaLabel"
    hover-class="pressed"
    @tap="emit('retry')"
  >
    <IconSvg name="report" :size="44" color="var(--text-tertiary)" />
    <text class="retry-title">{{ title }}</text>
    <text class="retry-hint">网络似乎不太顺畅 · 点击重试</text>
  </view>
</template>

<script setup lang="ts">
/**
 * RetryBlock —— 「加载失败 · 点击重试」失败态块（MP-012 同族视觉）
 *
 * 消费方（4 处，替换前各自复制）：
 * - pages/me/my-reviews/index.vue（我的评价首屏失败）
 * - pages/me/notifications/index.vue（通知首屏失败）
 * - pages/home/HomeContent.vue（首页筛选流失败）
 * - pages/find/index.vue（搜索失败）
 *
 * 仅承载失败态展示与 retry 上抛；重拉路径（含竞态 / 失败态复位）由各消费方自行持有，行为与迁移前一致。
 */
import IconSvg from './IconSvg.vue'

withDefaults(defineProps<{
  /** 主标题（默认「加载失败」；find 页用「搜索加载失败」区分语义） */
  title?: string
  /** 可访问标签（默认「加载失败，点击重试」） */
  ariaLabel?: string
  /** 是否带上方外边距（列表页失败态与上一区块拉开；find 页为整屏居中态，不带外边距） */
  margin?: boolean
}>(), {
  title: '加载失败',
  ariaLabel: '加载失败，点击重试',
  margin: true,
})

const emit = defineEmits<{
  /** 整块点击：消费方走与下拉刷新同一条重拉路径 */
  (e: 'retry'): void
}>()
</script>

<style scoped>
.retry-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
/* margin 变体：列表页失败态置于内容流末尾时的上边距（find 整屏居中态不带） */
.retry-block.has-margin { margin-top: var(--spacing-lg); }
.retry-block.pressed { opacity: 0.7; }
.retry-title {
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--text-secondary);
  text-align: center;
}
.retry-hint {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  text-align: center;
}
</style>
