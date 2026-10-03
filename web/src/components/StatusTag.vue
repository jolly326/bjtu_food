<script setup lang="ts">
/**
 * 状态标签（[UI 基线 §2.3](../../../docs/web/ui/公共组件与形态基线.md)）。
 *
 * <p><b>文案单一真源</b> = [设计变量.md 的「状态文案总表」](../../../docs/web/ui/设计变量.md) ——
 * 页面**只传 `status` + `kind`**，不自定义文案、不自写 `.tag-*`。
 *
 * <p>`kind` 决定「用哪组文案」（不改变文案本身）：
 * - `onoff`：启用 / 停用（Banner / 食堂 / 档口 / 视图 / 举报原因）
 * - **`dish`：在售 / 已下架**（菜品本体，刻意与 `onoff` 区分）
 * - `user`：正常 / 已禁用 / 已注销
 * - `feedback`：待处理 / 已处理
 * - `correction`：待处理 / 已采纳 / 已拒绝
 * - `review`：显示中 / 已隐藏（传 `visible` / `hidden`）
 */
export type StatusKind = 'onoff' | 'dish' | 'user' | 'feedback' | 'correction' | 'review'

const props = defineProps<{
  status: string
  kind?: StatusKind
}>()

/** 文案总表（唯一真源；新增 kind 必须同步文档） */
const LABELS: Record<string, string> = {
  on: '启用',
  off: '停用',
  // dish
  // （与 onoff 同值、不同文案 —— 通过 kind 分流）
  active: '正常',
  disabled: '已禁用',
  deleted: '已注销',
  pending: '待处理',
  handled: '已处理',
  adopted: '已采纳',
  rejected: '已拒绝',
  visible: '显示中',
  hidden: '已隐藏',
}

function label(): string {
  if (props.kind === 'dish') return props.status === 'on' ? '在售' : '已下架'
  return LABELS[props.status] ?? props.status
}

function cls(): string {
  const s = props.status
  if (props.kind === 'review') return s === 'hidden' ? 'tag tag-red' : 'tag tag-green'
  if (props.kind === 'user') {
    if (s === 'active') return 'tag tag-green'
    if (s === 'disabled') return 'tag tag-gray'
    return 'tag tag-red'
  }
  if (props.kind === 'feedback' || props.kind === 'correction') {
    if (s === 'pending') return 'tag tag-gray'
    if (s === 'handled' || s === 'adopted') return 'tag tag-green'
    return 'tag tag-red'
  }
  // onoff / dish：on 绿、off 中性灰（"下架"不是错误，不引入红色）
  return s === 'on' ? 'tag tag-green' : 'tag tag-gray'
}
</script>

<template>
  <span :class="cls()">{{ label() }}</span>
</template>
