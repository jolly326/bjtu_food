<script setup lang="ts">
/**
 * 删除确认卡（弹窗内整卡切换）：与编辑表单共用同一张 `.dialog` 卡片，
 * 点击「删除」后由父组件把卡片内容整体切换为本卡片（标题 / 说明 / 按钮全部替换），
 * 避免在编辑弹窗之上再叠一层确认浮层。
 */
import { ref } from 'vue'

withDefaults(defineProps<{ message?: string }>(), {
  message: '删除后不可恢复。',
})

const emit = defineEmits<{
  cancel: []
  confirm: []
}>()

const clicking = ref(false)

function onConfirm() {
  if (clicking.value) return
  clicking.value = true
  emit('confirm')
  // 短暂防抖，避免快速双击触发两次删除
  setTimeout(() => {
    clicking.value = false
  }, 800)
}
</script>

<template>
  <div class="confirm-delete">
    <h3 class="dialog__title">确认删除</h3>
    <p class="dialog__hint">{{ message }}</p>
    <footer class="dialog__foot">
      <span class="dialog__spacer" />
      <button class="btn btn--outline" @click="emit('cancel')">取消</button>
      <button class="btn btn--danger" @click="onConfirm">删除</button>
    </footer>
  </div>
</template>
