<template>
  <!-- 内容宽度主色胶囊按钮：**内容宽 CTA 的唯一实现**。
       与 `AppButton` 分工见下方 JSDoc（二者不可互相替代）。 -->
  <view
    class="content-btn"
    :class="{ 'is-disabled': disabled || loading, 'is-strong': strong }"
    :aria-label="text"
    :aria-disabled="disabled || loading ? 'true' : 'false'"
    :aria-busy="loading ? 'true' : 'false'"
    role="button"
    :hover-class="disabled || loading ? 'none' : 'pressed'"
    @tap="handleTap"
  >
    <view v-if="loading" class="btn-spinner" aria-hidden="true" />
    <text class="content-btn-text">{{ text }}</text>
  </view>
</template>

<script setup lang="ts">
/**
 * ContentButton —— 内容宽度主操作按钮（**内容宽 CTA 的唯一实现**）
 *
 * <p>**与 `AppButton` 的分工**（勿混用，二者不可互相替代）：
 * <ul>
 *   <li>`AppButton` = **通栏**（`width: 100%`）→ 页面级表单提交（身份认证 / 意见反馈提交…）；</li>
 *   <li>本组件 = **内容宽度**（`inline-flex`）→ 卡片内 / 弹层内的短操作
 *       （空态 CTA、举报弹层提交、加载失败块双 CTA、属性弹层「添加」）。</li>
 * </ul>
 *
 * <p>**触达**：`min-height` 恒为全站基线 `--tap-target-size`（88rpx = 44pt）。
 * 此前各消费方靠 `padding` 撑高，实际只有 40~60rpx（20~30pt），低于触达下限。
 *
 * <p>`loading` 时**忽略点击**并显示转环（防重复提交）；`disabled` 走「灰底 + 灰字」禁用档
 * （**不降透明** —— `opacity` 全站只表「在途」）。
 */
const props = withDefaults(
  defineProps<{
    /** 按钮文案 */
    text: string
    /** 禁用（灰底灰字，不可点） */
    disabled?: boolean
    /** 在途（转环 + 忽略点击，防重复提交） */
    loading?: boolean
    /** 强强调：附加上浮投影（失败块主 CTA 等需视觉分量的场景） */
    strong?: boolean
  }>(),
  { disabled: false, loading: false, strong: false },
)

// 自定义事件名不用原生 `tap`/`click`：否则 uni-app 编译 mp-weixin 时父组件监听会被当作
// 原生 bindxxx，emit 参数丢失（同 AppButton 的坑，见其文件内注释）。
const emit = defineEmits<{ press: [] }>()

function handleTap(): void {
  if (props.disabled || props.loading) return
  emit('press')
}
</script>

<style scoped>
.content-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--tap-target-size);
  padding: 0 var(--spacing-xl);
  border-radius: var(--radius-btn);
  background: var(--color-primary);
  -webkit-tap-highlight-color: transparent;
}
.content-btn.is-strong {
  box-shadow: var(--shadow-float);
}
/* 禁用档：灰底 + 灰字，**不降透明**（`opacity` 全站只表「在途 busy」） */
.content-btn.is-disabled {
  background: var(--bg-input);
  box-shadow: none;
}
.content-btn.pressed {
  opacity: 0.85;
}
.content-btn-text {
  font-size: var(--font-small);
  font-weight: var(--weight-semibold);
  color: var(--color-on-primary);
}
.content-btn.is-disabled .content-btn-text {
  color: var(--text-tertiary);
}

/* 在途转环：纯 CSS 环，零图标依赖；外圈 = 主色实底上的半透白描边档 */
.btn-spinner {
  width: var(--spacing-md);
  height: var(--spacing-md);
  margin-right: var(--spacing-2xs);
  border: 3rpx solid var(--text-white-edge);
  border-top-color: var(--text-white);
  border-radius: var(--radius-circle);
  animation: content-btn-spin 0.8s linear infinite;
}
@keyframes content-btn-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .btn-spinner {
    animation: none;
  }
}
</style>
