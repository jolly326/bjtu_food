<script setup lang="ts">
/**
 * 状态盒（[UI 基线 §2.2](../../../docs/web/ui/公共组件与形态基线.md)）。
 *
 * <p>`status` 四档：`loading` 加载 / `error` 错误（带重试）/ `empty` 空 / **`session` 会话失效**。
 * <p>⚠️ **`session` 不渲染「重试」**：口令不匹配（403）时重试必然再失败，
 * 由构建期重新注入 `ADMIN_TOKEN` 才能解决（见 [UI 基线 §1.5 ⑥](../../../docs/web/ui/公共组件与形态基线.md)）。
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
  <div class="state-box state-err" v-else-if="status === 'session'">
    <span>{{ message || '管理员口令校验失败（403），请检查构建期注入的 ADMIN_TOKEN' }}</span>
  </div>
  <div class="state-box" v-else>{{ message || '暂无数据' }}</div>
</template>
