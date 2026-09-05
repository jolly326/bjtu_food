<template>
  <!-- N08 修复：v-show 保持 AuthSheet 常驻挂载（由 BaseSheet 根节点 v-show 承接），
       关闭弹层不再卸载 AuthForm，发码倒计时不被清空（前端不辅助绕过 60s 冷却）。
       骨架（遮罩/grabber/下拉关闭/安全区/焦点还原）统一复用 BaseSheet，本层只承载认证语义。 -->
  <BaseSheet
    :visible="visible"
    z-token="--z-auth"
    scroll-body
    @close="hide"
  >
    <AuthForm :codeCountdown="codeCooldown" @cooldown-change="onCooldownChange" />
  </BaseSheet>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import BaseSheet from '@/components/BaseSheet.vue'
import AuthForm from '@/components/AuthForm.vue'
import { useAuthSheetStore } from '@/stores/auth-sheet'
import { useUserStore } from '@/stores/user'

const authSheetStore = useAuthSheetStore()
const userStore = useUserStore()

// 必须用 storeToRefs 保持响应性（直接解构会丢失更新，弹层永不显示）
const { visible } = storeToRefs(authSheetStore)

// N08 修复：在 AuthSheet 层持有发码冷却状态（与 AuthForm 同步）。
// 弹层关闭不清空（v-show 常驻），重开时回填，前端不辅助绕过 60s 冷却。
const codeCooldown = ref(0)
function onCooldownChange(v: number) {
  codeCooldown.value = v
}

// 用户主动关闭（点击遮罩 / 下滑 / 关闭钮）未完成认证：清除待办，避免过期动作在后续认证成功后误执行
function hide() {
  authSheetStore.clearPending()
  authSheetStore.hide()
}

// 认证成功（verified=true）后关闭弹层并执行认证前记录的待办（跳转到目标功能，§5.y）
watch(
  () => userStore.isVerified(),
  (v) => {
    if (v) authSheetStore.runPending()
  },
)
</script>

<style scoped>
/* 认证表单自身留有上下留白；BaseSheet scroll-body 已提供 AuthForm 原口径的水平/底部安全区 padding */
</style>
