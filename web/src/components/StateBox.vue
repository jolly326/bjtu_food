<script setup lang="ts">
/**
 * 状态盒（[UI 基线 §2.2](../../../docs/ui/web/公共组件与形态基线.md)）。
 *
 * <p>`status` 四档：`loading` 加载 / `error` 错误（带重试）/ `empty` 空 / **`session` 会话失效**。
 *
 * <p>🔴 **`session` 不渲染任何列表态**（[UI 基线 §1.5 ⑥](../../../docs/ui/web/公共组件与形态基线.md)）：
 * `401`（未带 token / 已过期 / 账号已停用）由**请求层**统一处理 —— 清 token → 跳登录页，
 * 页面只需在跳走之前**不显示**「加载失败 / 重试」这种必然无效的入口。
 */
withDefaults(
  defineProps<{
    status: 'loading' | 'error' | 'empty' | 'session'
    message?: string
  }>(),
  { message: undefined },
)

defineEmits<{ retry: [] }>()
</script>

<template>
  <div class="state-box" v-if="status === 'loading'">
    <span class="spin"></span><span>加载中…</span>
  </div>
  <div class="state-box state-err" v-else-if="status === 'error'">
    <span>{{ message || '加载失败' }}</span>
    <button class="link" type="button" @click="$emit('retry')">重试</button>
  </div>
  <!-- 会话失效：请求层正在「清 token → 跳登录页」，此处**不渲染列表态**，只留一个空占位 -->
  <div v-else-if="status === 'session'" aria-live="polite"></div>
  <div class="state-box" v-else>{{ message || '暂无数据' }}</div>
</template>
