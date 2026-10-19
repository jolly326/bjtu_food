<script setup lang="ts">
/**
 * 记忆页 Modal Bottom Sheet + 删除二次确认弹窗（口径见 docs/ui/client/记忆.md）。
 *
 * 菜单（长按唤起）：反馈 / 评价 / 删除记忆 / 取消。
 *  - 反馈 → 纠错表单（同一 Sheet 内容切换，不另起浮层）；
 *  - 评价 → 关闭 Sheet、进入该卡「原位评价态 B」（同一状态机 openEval，互斥由 useMemoryUI 保证）；
 *  - 删除记忆 → 关闭 Sheet、唤起高优先级二次确认弹窗；
 *  - 取消 → 关闭 Sheet。
 * 纠错表单：列出反馈选项，点选提交。
 * 二次确认：独立居中弹窗，优先级高于 Bottom Sheet，阻断底层交互。
 *
 * 与 原位评价面板 永不同时渲染（由 useMemoryUI 互斥保证）。
 */
import BaseSheet from '@/components/base/BaseSheet.vue'
import { useMemoryUI } from './useMemoryUI'
import type { IssueOptionOut, RecordOut } from '@/api'

const props = withDefaults(
  defineProps<{
    record: RecordOut | null
    issueOptions: IssueOptionOut[]
    issueLocked?: boolean
    cancelLocked?: boolean
  }>(),
  { issueLocked: false, cancelLocked: false },
)

const emit = defineEmits<{
  (e: 'submit-issue', optionId: number): void
  (e: 'confirm-delete'): void
}>()

const { sheetOpen, sheetMode, confirmOpen, closeSheet, openIssue, openEval, requestDelete, closeConfirm } =
  useMemoryUI()

function onSheetClose() {
  closeSheet()
}
function onFeedback() {
  openIssue()
}
/** 评价 → 关闭 Sheet 并进入该卡原位评价态（openEval 内部即收敛 Sheet） */
function onEvaluate() {
  const id = props.record?.id
  if (id == null) return
  openEval(id)
}
function onDelete() {
  requestDelete()
}
function onCancel() {
  closeSheet()
}
function onPick(optionId: number) {
  if (props.issueLocked) return
  emit('submit-issue', optionId)
}
function onConfirmDelete() {
  if (props.cancelLocked) return
  emit('confirm-delete')
  closeConfirm()
}
</script>

<template>
  <BaseSheet
    :model-value="sheetOpen"
    :title="sheetMode === 'issue' ? '哪里不对？' : ''"
    @update:model-value="onSheetClose"
    @close="onSheetClose"
  >
    <template v-if="sheetMode === 'menu'">
      <view
        class="mact__opt"
        hover-class="mact__opt--pressed"
        role="button"
        aria-label="反馈"
        @tap="onFeedback"
      >
        <text class="mact__optLabel">反馈</text>
      </view>
      <view
        class="mact__opt"
        hover-class="mact__opt--pressed"
        role="button"
        aria-label="评价"
        @tap="onEvaluate"
      >
        <text class="mact__optLabel">评价</text>
      </view>
      <view
        class="mact__opt"
        :class="{ 'mact__opt--locked': cancelLocked }"
        hover-class="mact__opt--pressed"
        role="button"
        aria-label="删除记忆"
        @tap="onDelete"
      >
        <text class="mact__optLabel">删除记忆</text>
      </view>
      <view
        class="mact__opt"
        hover-class="mact__opt--pressed"
        role="button"
        aria-label="取消"
        @tap="onCancel"
      >
        <text class="mact__optLabel">取消</text>
      </view>
    </template>

    <template v-else>
      <view
        v-for="opt in issueOptions"
        :key="opt.id"
        class="mact__opt"
        :class="{ 'mact__opt--locked': issueLocked }"
        hover-class="mact__opt--pressed"
        role="button"
        :aria-label="opt.name"
        @tap="onPick(opt.id)"
      >
        <text class="mact__optLabel">{{ opt.name }}</text>
      </view>
    </template>
  </BaseSheet>

  <!-- 删除二次确认：独立居中弹窗，优先级高于 Bottom Sheet -->
  <view v-if="confirmOpen" class="mact__mask" @tap="closeConfirm">
    <view class="mact__dialog" @tap.stop>
      <text class="mact__dialogTitle">删除这条记忆？</text>
      <text class="mact__dialogDesc">删除后无法恢复；已评分的记录不可删除。</text>
      <view class="mact__dialogBtns">
        <view
          class="mact__dialogBtn"
          hover-class="mact__dialogBtn--pressed"
          role="button"
          aria-label="取消"
          @tap="closeConfirm"
        >
          <text class="mact__dialogBtnLabel">取消</text>
        </view>
        <view
          class="mact__dialogBtn mact__dialogBtn--primary"
          :class="{ 'is-disabled': cancelLocked }"
          hover-class="mact__dialogBtn--pressed"
          role="button"
          aria-label="确定删除"
          @tap="onConfirmDelete"
        >
          <text class="mact__dialogBtnLabel">确定</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.mact__opt {
  display: flex;
  align-items: center;
  height: var(--tap-target-size);
  border-bottom: 1rpx solid var(--border-color);
}
.mact__opt:last-child {
  border-bottom: none;
}
.mact__opt--pressed {
  opacity: 0.7;
}
.mact__opt--locked {
  opacity: 0.5;
}
.mact__optLabel {
  font-size: var(--font-body);
  color: var(--text-title);
}

/* 二次确认弹窗（优先级高于 Bottom Sheet） */
.mact__mask {
  position: fixed;
  inset: 0;
  z-index: calc(var(--z-sheet) + 1000);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay-scrim);
}
.mact__dialog {
  width: var(--sheet-dialog-width);
  padding: var(--spacing-xl);
  background: var(--overlay-panel-bg);
  border-radius: var(--radius-modal);
  box-shadow: var(--shadow-modal);
}
.mact__dialogTitle {
  display: block;
  font-family: var(--font-serif);
  font-size: var(--font-h3);
  font-weight: var(--weight-regular);
  color: var(--text-title);
}
.mact__dialogDesc {
  display: block;
  margin-top: var(--spacing-sm);
  font-size: var(--font-small);
  line-height: 1.6;
  color: var(--text-tertiary);
}
.mact__dialogBtns {
  display: flex;
  gap: var(--spacing-md);
  margin-top: var(--spacing-xl);
}
.mact__dialogBtn {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  height: var(--tap-target-size);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-btn);
}
.mact__dialogBtn--pressed {
  opacity: 0.6;
}
.mact__dialogBtn--primary {
  color: var(--text-on-primary);
  background: var(--color-primary);
  border-color: var(--color-primary);
}
.mact__dialogBtn.is-disabled {
  opacity: 0.5;
}
.mact__dialogBtnLabel {
  font-size: var(--font-subtitle);
  font-weight: var(--weight-medium);
  color: inherit;
}
</style>
