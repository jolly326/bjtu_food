<script setup lang="ts">
/**
 * 删除确认视图（弹窗内整卡切换）：与编辑表单共用同一张 `.dialog` 卡片，
 * 点击「删除」后由父组件把卡片内容整体切换为本视图（标题 / 说明 / 按钮全部替换），
 * 不使用独立 ElMessageBox 浮层，避免移动端被编辑弹窗压住。规格见 docs/ui/web/公共组件与形态基线.md。
 */
withDefaults(defineProps<{ message?: string }>(), {
  message: '删除后不可恢复。',
})

const emit = defineEmits<{
  cancel: []
  confirm: []
}>()
</script>

<template>
  <div class="confirm-delete">
    <h3 class="dialog__title">确认删除</h3>
    <p class="dialog__hint">{{ message }}</p>
    <footer class="dialog__foot">
      <span class="dialog__spacer" />
      <button class="btn btn--outline" @click="emit('cancel')">取消</button>
      <button class="btn btn--danger" @click="emit('confirm')">确认删除</button>
    </footer>
  </div>
</template>
