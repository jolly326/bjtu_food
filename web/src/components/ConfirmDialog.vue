<script setup lang="ts">
/**
 * ConfirmDialog：二次确认弹窗（§4.2 自封装组件，破坏性操作必用）。
 * 复用全局 confirmStore，承载按钮即时反馈与弹层 spring 动效。
 *
 * UI-02（2026-09-15）：改为基于 Modal 实现——Modal 为唯一弹层基座，
 * 本组件不再自带 Teleport 与 overlay/box 样式副本，仅保留 confirmStore 绑定。
 * 弹层取值（overlay 毛玻璃 / 盒圆角 / 顶部品牌条 / 220ms 过渡豁免）由 Modal 统一承载；
 * confirm 语义（role=alertdialog、隐藏右上角 X、默认聚焦「取消」、ESC 关闭）由 Modal variant="confirm" 提供。
 */
import Modal from './Modal.vue'
import { useConfirmStore } from '@/stores/confirmStore'

const confirm = useConfirmStore()

function onCancel() {
  confirm.cancel()
}
function onOk() {
  confirm.ok()
}
</script>

<template>
  <Modal
    :show="confirm.visible"
    variant="confirm"
    danger
    :width="400"
    confirm-text="确定"
    cancel-text="取消"
    @close="onCancel"
    @confirm="onOk"
  >
    <p class="confirm-msg">{{ confirm.message }}</p>
  </Modal>
</template>

<style scoped>
.confirm-msg {
  margin: 0;
  font-size: var(--font-md);
  color: var(--text-primary);
  line-height: var(--leading-base);
}
/* 按钮（btn-cancel/btn-danger）走 shared.css 全局基线，此处不重复覆盖 */
</style>
