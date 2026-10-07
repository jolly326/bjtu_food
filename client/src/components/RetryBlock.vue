<template>
  <!-- 加载失败重试块（公共组件，P3-03 上提；UI 统一 Loop Round 13 扩展）
       四处历史完整复制（结构 / 文案 / 按压语言 / 无障碍属性一致），
       按 §2「多包高频复用者上提 components」收敛为唯一实现。
       视觉基线：**无底色块**的居中极简列 + 次级文字色（空 / 失败是内容状态，不需要灰底色块）。
       两种交互形态（按 props 自动选择，消费方无需分支）：
         · **整块可点**（默认，`primaryText` / `secondaryText` 都不传）：整块 role="button"，点击 emit('retry')
           —— my-reviews / notifications / HomeContent / find 沿用；
         · **双 CTA**（传 `primaryText` / `secondaryText`）：块本身不可点，改为两枚圆角按钮（`--radius-btn`）
           —— 详情页「重新加载 + 返回」场景。

       ⚠️ 加载反馈：`loading` 为真时**用旋转环替换图标**并在副文案位显示
       「正在重新加载…」，同时**忽略点击**（防重复提交）。
       口径说明：在途只给文字行、不给骨架屏（禁的是伪内容与抖动，不是文字）；
       本处转圈是**用户主动点击后的在途反馈**，与页面级首屏在途的文字行是两处不同反馈。
       按压反馈走 opacity（小程序端按压语言 = bg-soft / opacity，不用 transform: scale）。
       无障碍：整块/按钮均为 role="button" + aria-label（默认可覆写）。 -->
  <view
    class="retry-block"
    :class="{ 'has-margin': margin, 'is-strong': strong, 'is-tappable': !primaryText && !secondaryText }"
    :role="!primaryText && !secondaryText ? 'button' : 'alert'"
    aria-live="assertive"
    :aria-label="!primaryText && !secondaryText ? ariaLabel : undefined"
    :hover-class="!primaryText && !secondaryText ? 'pressed' : 'none'"
    @tap="onBlockTap"
  >
    <!-- 图标位：加载中用旋转环替换（纯 CSS 环，零图标依赖） -->
    <view v-if="loading" class="retry-spinner" />
    <AppIcon v-else name="report" :size="strong ? 96 : 44" :color="COLOR_MAP['text-tertiary']" />

    <text class="retry-title">{{ title }}</text>
    <text class="retry-hint">{{ loading ? '正在重新加载…' : hint }}</text>

    <!-- 双 CTA 形态（仅在传了按钮文案时渲染）：主 = 主色实底，次 = 浅底描边档 -->
    <view v-if="primaryText || secondaryText" class="retry-actions">
      <ContentButton
        v-if="primaryText"
        :text="primaryText"
        :loading="loading"
        :disabled="loading"
        strong
        @press.stop="onPrimaryTap"
      />
      <ContentButton v-if="secondaryText" :text="secondaryText" @press.stop="emit('secondary')" />
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * RetryBlock —— 加载失败块（**全站唯一实现**）
 *
 * 消费方：
 * - pages/my-reviews/index.vue、pages/notifications/index.vue、pages/home/HomeContent.vue、pages/find/index.vue（整块可点）
 * - pages/detail/dish/index.vue（双 CTA：重新加载 + 返回；Round 13 由自绘 `.detail-fail` 并入）
 *
 * 仅承载失败态展示与事件上抛；重拉路径（含竞态 / 失败态复位 / 在途标记）由各消费方自行持有。
 */
import AppIcon from './AppIcon.vue'
import ContentButton from '@/components/ContentButton.vue'
import { COLOR_MAP } from '@/theme/tokens'

const props = withDefaults(defineProps<{
  /** 主标题（默认「加载失败」；find 页用「搜索加载失败」区分语义，详情页用「这道菜暂时打不开」） */
  title?: string
  /** 副文案（默认「网络似乎不太顺畅 · 点击重试」；详情页传「可能是网络暂时不可用」） */
  hint?: string
  /** 可访问标签（默认「加载失败，点击重试」，仅整块可点形态使用） */
  ariaLabel?: string
  /** 是否带上方外边距（列表页失败态与上一区块拉开；整屏居中态不带） */
  margin?: boolean
  /** 在途（重新加载中）：旋转环 + 副文案切换 + 忽略点击 */
  loading?: boolean
  /** 强语气：标题用一级文字色、图标 96rpx（整屏居中的主失败态，如详情页） */
  strong?: boolean
  /** 主按钮文案（传了 ⇒ 改为双 CTA 形态，块本身不可点） */
  primaryText?: string
  /** 次按钮文案（如「返回」） */
  secondaryText?: string
}>(), {
  title: '加载失败',
  hint: '网络似乎不太顺畅 · 点击重试',
  ariaLabel: '加载失败，点击重试',
  margin: true,
  loading: false,
  strong: false,
})

const emit = defineEmits<{
  /** 重拉：整块点击（默认形态）或主按钮点击（双 CTA 形态） */
  (e: 'retry'): void
  /** 次按钮点击（双 CTA 形态） */
  (e: 'secondary'): void
}>()

/** 在途期忽略点击（双 CTA 形态下由按钮自行处理，这里只兜整块形态） */
function onBlockTap() {
  if (props.loading) return
  if (props.primaryText || props.secondaryText) return
  emit('retry')
}
function onPrimaryTap() {
  if (props.loading) return
  emit('retry')
}
</script>

<style scoped>
/* 失败态 = **无底色块**的居中极简列（图标 + 标题 + 副文案 + 可选双 CTA）：
   空 / 失败是内容状态而非需要「色块」表达的异常，灰底只会在页面里切出一块与内容无关的灰矩形。 */
.retry-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  padding: var(--spacing-xl) var(--spacing-lg);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
/* margin 变体：列表页失败态置于内容流末尾时的上边距（整屏居中态不带） */
.retry-block.has-margin { margin-top: var(--spacing-lg); }
.retry-block.pressed { opacity: 0.7; }
/* 强语气（整屏居中主失败态，如详情页）：标题升到一级文字色与正文档 */
.retry-block.is-strong { gap: var(--spacing-sm); padding: var(--spacing-2xl) var(--spacing-xl); }
.is-strong .retry-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); }
.is-strong .retry-hint { line-height: 1.5; }

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

/* 在途旋转环（纯 CSS，无图标依赖）：主色顶弧 + 浅灰环底 */
.retry-spinner {
  width: 44rpx;
  height: 44rpx;
  border: 4rpx solid var(--border-color);
  border-top-color: var(--color-primary);
  border-radius: var(--radius-circle);
  animation: retry-spin 0.8s linear infinite;
}
@keyframes retry-spin {
  to { transform: rotate(360deg); }
}
/* 尊重系统「减少动态效果」设置：停止持续旋转（全局基线是保留静态环） */
@media (prefers-reduced-motion: reduce) {
  .retry-spinner { animation: none; }
}

/* ===== 双 CTA 形态 ===== */
.retry-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-sm);
}
/* CTA 底色 / 圆角 / 触达 / 在途各档全部由公共 ContentButton 承担 */
</style>