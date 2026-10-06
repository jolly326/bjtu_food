<script setup lang="ts">
/**
 * 列表四态守卫（加载 / 会话失效 / 错误 / 空），收敛各视图里逐字重复的 `StateBox` 链。
 *
 * <p>与 `useSimpleList` / `usePagedList` 的产出同构：`firstLoading` / `sessionInvalid` /
 * `error` / `isEmpty` / `hasData`；本组件只负责前四态的渲染，有数据时的内容由外部
 * `<div v-if="hasData">` 承载（保持各视图原有 class 与结构）。
 *
 * <p>`session` 不渲染任何列表态：`401` 由请求层统一「清 token → 跳登录页」，
 * 页面只是在跳走前**不显示**「加载失败 / 重试」（[UI 基线 §1.5 ⑥](../../../docs/ui/web/公共组件与形态基线.md)）。
 */
withDefaults(
  defineProps<{
    loading?: boolean
    sessionInvalid?: boolean
    error?: string | null
    empty?: boolean
    emptyMessage?: string
  }>(),
  { loading: false, sessionInvalid: false, error: null, empty: false, emptyMessage: '暂无数据' },
)

defineEmits<{ retry: [] }>()
</script>

<template>
  <StateBox v-if="loading" status="loading" />
  <StateBox v-else-if="sessionInvalid" status="session" />
  <StateBox v-else-if="error" status="error" :message="error" @retry="$emit('retry')" />
  <StateBox v-else-if="empty" status="empty" :message="emptyMessage" />
</template>
