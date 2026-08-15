<template>
  <view class="page feedback-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="意见反馈" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 我的反馈记录（登录后展示；未登录引导登录） -->
      <CardSection title="我的反馈">
        <view v-if="!loggedIn" class="login-hint" @tap="goLogin">
          <text class="login-hint-text">登录后可查看你的反馈记录</text>
          <text class="login-hint-action">去登录 ›</text>
        </view>
        <template v-else-if="recordsLoading && records.length === 0">
          <view class="sk-list">
            <view v-for="s in 3" :key="s" class="sk-item" />
          </view>
        </template>
        <template v-else-if="records.length > 0">
          <view v-for="f in records" :key="f.id" class="rec-item" @tap="viewDetail(f)">
            <view class="rec-main">
              <view class="rec-title-row">
                <text class="rec-title">{{ feedbackTypeLabel(f.type) }}</text>
                <StatusBadge :status="f.status === 'handled' ? 'approved' : 'pending'" />
              </view>
              <text class="rec-content">{{ f.content }}</text>
              <text class="rec-time">{{ formatTime(f.createdAt) }}</text>
            </view>
            <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="rec-arrow" />
          </view>
        </template>
        <EmptyState v-else-if="recordsLoadFailed" text="加载失败，请重试" icon="report" :retry="true" @retry="loadRecords" />
        <EmptyState v-else text="还没有反馈记录" icon="report" />
      </CardSection>

      <!-- 反馈类型 -->
      <CardSection title="反馈类型">
        <view class="type-row">
          <view
            v-for="t in types"
            :key="t.value"
            class="type-chip"
            :class="{ active: type === t.value }"
            @tap="type = t.value"
          >
            <text class="type-text">{{ t.label }}</text>
          </view>
        </view>
      </CardSection>

      <!-- 反馈对象（选填） -->
      <CardSection title="反馈对象（选填）">
        <view class="type-row">
          <view
            v-for="e in entityOptions"
            :key="e.key"
            class="type-chip"
            :class="{ active: selectedEntity === e.key }"
            @tap="selectedEntity = e.key"
          >
            <text class="type-text">{{ e.label }}</text>
          </view>
        </view>
      </CardSection>

      <!-- 内容 -->
      <CardSection title="反馈内容">
        <textarea
          class="content-input"
          v-model="content"
          placeholder="请描述你遇到的问题或建议…"
          maxlength="1000"
          :auto-height="true"
        />
        <text class="counter">{{ content.length }}/1000</text>
      </CardSection>

      <!-- 联系方式 -->
      <CardSection title="联系方式（选填）">
        <input class="contact-input" v-model="contact" placeholder="邮箱 / 微信，方便我们回复你" />
      </CardSection>

      <view style="height: var(--spacing-xl)" />
    </scroll-view>

    <view class="submit-bar">
      <AppButton text="提交反馈" :loading="submitting" @click="submit" />
    </view>

    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import { submitFeedback, getMyFeedback, type FeedbackMyItem } from '@/api/feedback'
import type { FeedbackSubmit } from '@/types/feedback'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import CardSection from '@/components/CardSection.vue'
import IconSvg from '@/components/IconSvg.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import EmptyState from '@/components/EmptyState.vue'
import AuthSheet from '@/components/AuthSheet.vue'

const theme = useThemeStore()
const userStore = useUserStore()
const loggedIn = computed(() => userStore.isLoggedIn())

// ---- 提交表单 ----
const types: { value: FeedbackSubmit['type']; label: string }[] = [
  { value: 'suggestion', label: '功能建议' },
  { value: 'error', label: '问题报告' },
  { value: 'other', label: '其他' },
]
const type = ref<FeedbackSubmit['type']>('suggestion')
const entityOptions = [
  { key: 'dish', label: '菜品' },
  { key: 'stall', label: '档口' },
  { key: 'canteen', label: '食堂' },
  { key: 'none', label: '其他' },
]
const selectedEntity = ref('none')
const content = ref('')
const contact = ref('')
const submitting = ref(false)

async function submit() {
  // 反馈不登录也可用（后端 POST /feedback 公开，游客 userId=null）；仅做内容校验
  const text = content.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写反馈内容', icon: 'none' })
    return
  }
  if (text.length > 1000) {
    uni.showToast({ title: '内容不能超过1000字', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await submitFeedback({
      type: type.value,
      relatedType: selectedEntity.value === 'none' ? undefined : selectedEntity.value,
      content: text,
      contact: contact.value.trim() || undefined,
    })
    uni.showToast({ title: '提交成功，感谢反馈', icon: 'success' })
    content.value = ''
    contact.value = ''
    selectedEntity.value = 'none'
    type.value = 'suggestion'
    setTimeout(() => { uni.navigateBack() }, 600)
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

// ---- 我的反馈记录（登录后加载） ----
const records = ref<FeedbackMyItem[]>([])
const recordsLoading = ref(false)
const recordsLoadFailed = ref(false)

async function loadRecords(silent = false) {
  if (!loggedIn.value) return
  if (!silent) recordsLoading.value = true
  recordsLoadFailed.value = false
  try {
    records.value = await getMyFeedback()
  } catch {
    recordsLoadFailed.value = true
    /* toast 由 http 层统一处理 */
  } finally {
    recordsLoading.value = false
  }
}

function goLogin() {
  // 未登录：引导认证；登录成功后 watch 自动重新加载
  userStore.requireAuth(() => loadRecords(false))
}

function viewDetail(f: FeedbackMyItem) {
  uni.showModal({
    title: feedbackTypeLabel(f.type),
    content: `内容：${f.content}\n\n状态：${f.status === 'handled' ? '已处理' : '待处理'}${f.reply ? `\n回复：${f.reply}` : ''}`,
    showCancel: false,
  })
}

function feedbackTypeLabel(t: string): string {
  const map: Record<string, string> = { error: '问题报告', suggestion: '功能建议', other: '其他', report: '举报', wrong: '信息有误' }
  return map[t] ?? '反馈'
}
function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

let initialized = false
// 登录成功后自动加载记录
watch(
  () => userStore.isLoggedIn(),
  (v) => {
    if (v) {
      loadRecords(initialized)
      initialized = true
    }
  },
)
onShow(() => {
  if (loggedIn.value) {
    loadRecords(initialized)
    initialized = true
  }
})
</script>

<style scoped>
.feedback-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-top: var(--spacing-md); padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom)); }

/* 登录引导 */
.login-hint { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-sm) 0; }
.login-hint:active { transform: scale(var(--press-scale)); }
.login-hint-text { font-size: var(--font-aux); color: var(--text-secondary); }
.login-hint-action { font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); }

/* 记录骨架 */
.sk-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.sk-item { height: 120rpx; border-radius: var(--radius-card); background: var(--bg-soft); }

/* 记录项 */
.rec-item { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-md) 0; background: var(--bg-page); border-radius: var(--radius-card); }
.rec-item:active { transform: scale(var(--press-scale)); }
.rec-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.rec-title-row { display: flex; align-items: center; gap: var(--spacing-sm); }
.rec-title { font-size: var(--font-caption); font-weight: var(--weight-semibold); color: var(--text-primary); }
.rec-content { font-size: var(--font-aux); color: var(--text-secondary); display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; line-clamp: 2; overflow: hidden; }
.rec-time { font-size: var(--font-tiny); color: var(--text-tertiary); }
.rec-arrow { flex-shrink: 0; }

/* 类型选择 */
.type-row { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.type-chip { padding: var(--spacing-sm) var(--spacing-lg); border-radius: var(--radius-tag); background: var(--bg-soft); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.type-chip:active { transform: scale(var(--press-scale)); }
.type-chip.active { background: var(--color-primary-soft); }
.type-text { font-size: var(--font-aux); color: var(--text-secondary); font-weight: var(--weight-semibold); }
.type-chip.active .type-text { color: var(--color-primary); }

/* 内容输入：与发布菜品/评价 textarea 同款 */
.content-input { width: 100%; min-height: 220rpx; font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; padding: var(--spacing-sm); background: var(--bg-page); border-radius: var(--radius-card); box-sizing: border-box; }
.counter { display: block; text-align: right; font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); font-variant-numeric: tabular-nums; }
.contact-input { width: 100%; height: 88rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: 0 var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); box-sizing: border-box; }

/* 提交栏：固定底部，避让安全区 */
.submit-bar { padding: var(--spacing-md); padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }
</style>
