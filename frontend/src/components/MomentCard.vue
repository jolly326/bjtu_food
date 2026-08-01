<template>
  <view
    class="moment-card"
    :class="{ pressed }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
    @tap="goDetail"
  >
    <!-- 发布者 -->
    <view class="m-head">
      <image v-if="moment.userAvatar" class="m-avatar" :src="moment.userAvatar" mode="aspectFill" />
      <view v-else class="m-avatar m-avatar-empty">
        <IconSvg name="user" :size="40" color="var(--text-tertiary)" class="m-avatar-fallback" />
      </view>
      <view class="m-head-right">
        <text class="m-nickname">@{{ moment.userNickname || '匿名用户' }}</text>
        <text class="m-time">{{ relativeTime(moment.createdAt) }}</text>
      </view>
      <!-- 审核态徽标（仅作者本人可见，我的动态页） -->
      <view v-if="showAudit && moment.auditStatus && moment.auditStatus !== 'approved'" class="m-audit" :class="auditClass">
        <text class="m-audit-text">{{ auditLabel }}</text>
      </view>
    </view>

    <!-- 正文 -->
    <text class="m-content" :class="{ clamped: !expanded }">{{ moment.content }}</text>
    <text v-if="needClamp" class="m-expand" @tap.stop="expanded = !expanded">{{ expanded ? '收起' : '展开' }}</text>

    <!-- 图片九宫格 -->
    <view v-if="moment.images.length > 0" class="m-images" :class="`img-${Math.min(moment.images.length, 9)}`">
      <view
        v-for="(img, idx) in moment.images.slice(0, 9)"
        :key="idx"
        class="m-image-wrap"
        @tap.stop="previewImage(idx)"
      >
        <image class="m-image" :src="img" mode="aspectFill" />
      </view>
    </view>

    <!-- 关联对象 chip -->
    <view v-if="moment.relatedType && moment.relatedType !== 'none' && moment.relatedName" class="m-related" @tap.stop="goRelated">
      <IconSvg name="location" :size="24" color="var(--color-primary)" class="m-related-icon" />
      <text class="m-related-text">{{ relatedLabel }}</text>
    </view>

    <!-- 退回原因（作者本人可见） -->
    <view v-if="moment.auditStatus === 'rejected' && moment.rejectReason" class="m-reject">
      <text class="m-reject-text">已退回：{{ moment.rejectReason }}</text>
    </view>

    <!-- 互动栏 -->
    <view class="m-actions">
      <view class="m-action" :class="{ active: usefulActive }" @tap.stop="onUseful">
        <IconSvg name="thumb" :size="30" class="m-action-icon" :color="usefulActive ? 'var(--color-like)' : 'var(--text-secondary)'" />
        <text class="m-action-count">{{ moment.usefulCount > 0 ? moment.usefulCount : 0 }}</text>
      </view>
      <view class="m-action" @tap.stop="goDetail">
        <IconSvg name="comment" :size="30" color="var(--text-secondary)" class="m-action-icon" />
        <text class="m-action-count">{{ moment.commentCount > 0 ? moment.commentCount : 0 }}</text>
      </view>
      <view class="m-action m-action-share" @tap.stop="onShare">
        <IconSvg name="share" :size="30" color="var(--text-secondary)" class="m-action-icon" />
        <text class="m-action-count">分享</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import IconSvg from './IconSvg.vue'
import { relativeTime } from '@/utils/time'
import { getImageUrl } from '@/utils/image'
import type { Moment } from '@/types/moment'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'

const props = withDefaults(defineProps<{
  moment: Moment
  /** 是否展示审核态徽标（我的动态页） */
  showAudit?: boolean
}>(), {
  showAudit: false,
})

const emit = defineEmits<{
  (e: 'useful', moment: Moment): void
  (e: 'tap', moment: Moment): void
  (e: 'go-related', moment: Moment): void
}>()

const userStore = useUserStore()
const pressed = ref(false)

// 正文展开态（点1：超长折叠，粗判长度显示展开入口）
const expanded = ref(false)
const needClamp = computed(() => (props.moment.content?.length || 0) > 80)

const relatedLabel = computed(() => {
  const prefix = props.moment.relatedType === 'dish' ? '菜品' : props.moment.relatedType === 'stall' ? '档口' : ''
  return `${prefix}·${props.moment.relatedName || ''}`
})

const auditLabel = computed(() => {
  if (props.moment.auditStatus === 'pending') return '审核中'
  if (props.moment.auditStatus === 'rejected') return '已退回'
  return ''
})
const auditClass = computed(() => `audit-${props.moment.auditStatus}`)

// 有用 toggle 本地状态（详情页传入有用态；此处仅做乐观 UI）
const usefulActive = ref(false)

function goDetail() {
  emit('tap', props.moment)
}

function goRelated() {
  emit('go-related', props.moment)
}

function onShare() {
  // 分享：复制卡片链接/文案到剪贴板（纯前端，不调后端）
  const text = `@${props.moment.userNickname || '匿名用户'}：${props.moment.content}`
  uni.setClipboardData({
    data: text,
    success: () => uni.showToast({ title: '已复制，去分享吧', icon: 'none' }),
  })
}

function previewImage(idx: number) {
  uni.previewImage({ urls: props.moment.images.map(getImageUrl), current: props.moment.images.map(getImageUrl)[idx] })
}

async function onUseful() {
  if (!userStore.requireAuth()) return
  const prevActive = usefulActive.value
  const prevCount = props.moment.usefulCount || 0
  usefulActive.value = !prevActive
  props.moment.usefulCount = prevActive ? Math.max(0, prevCount - 1) : prevCount + 1
  try {
    const res = await momentApi.toggleUseful(props.moment.id)
    usefulActive.value = res.useful
    props.moment.usefulCount = res.usefulCount
    emit('useful', props.moment)
  } catch {
    usefulActive.value = prevActive
    props.moment.usefulCount = prevCount
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}
</script>

<style scoped>
.moment-card {
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: var(--spacing-md);
  transition: transform 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.moment-card.pressed { transform: scale(0.97); }
.m-head { display: flex; align-items: center; gap: var(--spacing-sm); }
.m-avatar { width: 64rpx; height: 64rpx; border-radius: 50%; background: var(--bg-page); flex-shrink: 0; }
.m-avatar-empty { display: flex; align-items: center; justify-content: center; }
.m-avatar-fallback { font-size: 32rpx; line-height: 1; }
.m-head-right { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.m-nickname { font-size: var(--font-caption); font-weight: 600; color: var(--text-primary); }
.m-time { font-size: var(--font-aux); color: var(--text-tertiary); margin-top: 6rpx; }
.m-audit { padding: 4rpx 12rpx; border-radius: var(--radius-tag); flex-shrink: 0; }
.m-audit-text { font-size: 20rpx; font-weight: 700; }
.audit-pending { background: var(--color-warning-soft); }
.audit-pending .m-audit-text { color: var(--color-warning); }
.audit-rejected { background: var(--color-error-soft); }
.audit-rejected .m-audit-text { color: var(--color-error); }
.m-content { display: block; margin-top: var(--spacing-sm); font-size: var(--font-body); color: var(--text-secondary); line-height: 1.5; word-break: break-word; }
.m-content.clamped { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 4; overflow: hidden; }
.m-expand { margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--color-primary); font-weight: 600; align-self: flex-start; }
.m-images { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
.m-image-wrap { aspect-ratio: 1 / 1; width: 100%; border-radius: var(--radius-tag); overflow: hidden; background: var(--bg-page); }
.m-image { width: 100%; height: 100%; transition: transform 0.25s ease; }
.m-image-wrap:active .m-image { transform: scale(1.04); }
.m-related { display: inline-flex; align-items: center; gap: var(--spacing-xs); margin-top: var(--spacing-sm); padding: var(--spacing-xs) var(--spacing-md); background: var(--color-primary-soft); border-radius: var(--radius-tag); align-self: flex-start; transition: opacity 0.12s ease; }
.m-related:active { opacity: 0.7; }
.m-related-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: 600; }
.m-reject { margin-top: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-tag); }
.m-reject-text { font-size: var(--font-aux); color: var(--color-error); line-height: 1.5; }
.m-actions { display: flex; align-items: center; justify-content: flex-start; gap: var(--spacing-md); margin-top: var(--spacing-md); padding-top: var(--spacing-sm); border-top: 2rpx solid var(--border-color); }
.m-action { display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); border-radius: var(--radius-tag); border: 2rpx solid transparent; background: var(--bg-soft); transition: transform 0.18s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.12s ease, border-color 0.12s ease; -webkit-tap-highlight-color: transparent; }
.m-action:active { transform: scale(0.9); }
.m-action.m-action-share:active { background: var(--color-primary-soft); }
.m-action.active { border-color: var(--color-like); background: var(--color-like-soft); }
.m-action-icon { font-size: 28rpx; line-height: 1; color: var(--text-secondary); }
.m-action.active .m-action-icon { color: var(--color-like); }
.m-action-count { font-size: 24rpx; font-weight: 600; color: var(--text-secondary); }
.m-action.active .m-action-count { color: var(--color-like); }
</style>
