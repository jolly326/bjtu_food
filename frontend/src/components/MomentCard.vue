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
      <image v-if="moment.userAvatar" class="m-avatar" :src="moment.userAvatar" mode="aspectFill" lazy-load />
      <view v-else class="m-avatar m-avatar-empty">
        <IconSvg name="user" :size="40" color="var(--text-tertiary)" class="m-avatar-fallback" />
      </view>
      <view class="m-head-right">
        <text class="m-nickname">{{ moment.userNickname || '匿名用户' }}</text>
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
        <image class="m-image" :src="img" mode="aspectFill" lazy-load />
      </view>
    </view>

    <!-- 退回原因（作者本人可见） -->
    <view v-if="moment.auditStatus === 'rejected' && moment.rejectReason" class="m-reject">
      <text class="m-reject-text">已退回：{{ moment.rejectReason }}</text>
    </view>

    <!-- 关联对象 chip + 互动栏（同一行，互动靠右） -->
    <view class="m-foot">
      <view v-if="moment.relatedType && moment.relatedType !== 'none' && moment.relatedName" class="m-related" @tap.stop="goRelated">
        <IconSvg name="location" :size="22" color="var(--color-primary)" class="m-related-icon" />
        <text class="m-related-text">{{ relatedLabel }}</text>
      </view>
      <view class="m-actions">
        <view class="m-action" :class="{ active: usefulActive }" @tap.stop="onUseful">
          <IconSvg name="thumb" :size="30" class="m-action-icon" :color="usefulActive ? 'var(--color-like)' : 'var(--text-secondary)'" />
          <text class="m-action-count">{{ moment.usefulCount > 0 ? moment.usefulCount : 0 }}</text>
        </view>
        <view class="m-action" @tap.stop="goDetail">
          <IconSvg name="comment" :size="30" color="var(--text-secondary)" class="m-action-icon" />
          <text class="m-action-count">{{ moment.commentCount > 0 ? moment.commentCount : 0 }}</text>
        </view>
        <!-- 分享：微信原生分享组件（open-type=share → 页面 onShareAppMessage） -->
        <button class="m-action m-action-share" open-type="share" @tap="onShareTap">
          <IconSvg name="share" :size="30" color="var(--text-secondary)" class="m-action-icon" />
          <text class="m-action-count">分享</text>
        </button>
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
import { sharedMoment } from '@/utils/shareState'

const props = withDefaults(defineProps<{
  moment: Moment
  /** 是否展示审核态徽标（我的动态页） */
  showAudit?: boolean
}>(), {
  showAudit: false,
})

// 注意：自定义事件不能用原生事件名（tap/click），否则 uni-app 编译到微信小程序时
// 父组件的 @tap 会被编译为原生 bindtap，emit 参数丢失，点击跳转 id 变 undefined。
// 故进详情用 select 作为自定义事件名。
const emit = defineEmits<{
  (e: 'useful', moment: Moment): void
  (e: 'select', moment: Moment): void
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
  emit('select', props.moment)
}

function goRelated() {
  emit('go-related', props.moment)
}

function onShareTap() {
  // 记录待分享动态，页面 onShareAppMessage 据此生成分享卡片（微信原生分享）
  sharedMoment.value = props.moment
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
  /* Apple highlight 按压：背景微变而非整卡缩放（与 find 混合卡一致） */
  transition: background-color 0.12s ease;
  -webkit-tap-highlight-color: transparent;
}
.moment-card.pressed { background-color: var(--bg-soft); }
.m-head { display: flex; align-items: center; gap: var(--spacing-sm); }
/* 圆角正方形头像：用明确 rpx（16rpx），不用 var(--radius-card)=16px（在 64rpx 头像上接近圆形） */
.m-avatar { width: 64rpx; height: 64rpx; border-radius: 16rpx; background: var(--bg-page); flex-shrink: 0; }
.m-avatar-empty { display: flex; align-items: center; justify-content: center; }
.m-avatar-fallback { font-size: 32rpx; line-height: 1; }
.m-head-right { flex: 1; min-width: 0; display: flex; flex-direction: column; }
/* Apple Design Typography：昵称 body-bold（与动态详情页昵称一致） */
.m-nickname { font-size: var(--font-body); font-weight: var(--weight-bold); color: var(--text-primary); letter-spacing: -0.01em; }
.m-time { font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); }
.m-audit { padding: 4rpx 12rpx; border-radius: var(--radius-tag); flex-shrink: 0; }
.m-audit-text { font-size: 20rpx; font-weight: var(--weight-bold); }
.audit-pending { background: var(--color-warning-soft); }
.audit-pending .m-audit-text { color: var(--color-warning); }
.audit-rejected { background: var(--color-error-soft); }
.audit-rejected .m-audit-text { color: var(--color-error); }
.m-content { display: block; margin-top: var(--spacing-sm); font-size: var(--font-body); color: var(--text-secondary); line-height: 1.5; word-break: break-word; }
.m-content.clamped { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 4; overflow: hidden; }
.m-expand { margin-top: var(--spacing-xs); font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); align-self: flex-start; }
.m-images { display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
/* 缩略图：圆角正方形（16rpx，与全站缩略图/头像统一） */
.m-image-wrap { aspect-ratio: 1 / 1; width: 100%; border-radius: 16rpx; overflow: hidden; background: var(--bg-page); }
.m-image { width: 100%; height: 100%; transition: transform 0.25s ease; }
.m-image-wrap:active .m-image { transform: scale(var(--press-scale)); }
.m-related { display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); background: var(--color-primary-soft); border-radius: var(--radius-tag); flex-shrink: 0; transition: opacity 0.12s ease; }
.m-related:active { opacity: 0.7; }
.m-related-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); }
.m-reject { margin-top: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-tag); }
.m-reject-text { font-size: var(--font-aux); color: var(--color-error); line-height: 1.5; }
/* 关联 chip + 互动栏同一行（m-foot），互动靠右 */
.m-foot { display: flex; align-items: center; gap: var(--spacing-sm); margin-top: var(--spacing-md); }
.m-actions { display: flex; align-items: center; gap: var(--spacing-xs); margin-left: auto; flex-shrink: 0; }
.m-action { display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-tag); border: 2rpx solid transparent; background: var(--bg-soft); transition: transform 0.18s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.12s ease, border-color 0.12s ease; -webkit-tap-highlight-color: transparent; }
.m-action:active { transform: scale(var(--press-scale)); }
.m-action.m-action-share:active { background: var(--color-primary-soft); }
/* button 重置（微信原生分享按钮） */
.m-action.m-action-share { margin: 0; padding: var(--spacing-xs) var(--spacing-sm); line-height: 1.2; font-size: 24rpx; font-weight: var(--weight-semibold); box-sizing: border-box; }
.m-action.m-action-share::after { border: none; }
.m-action.active { border-color: var(--color-like); background: var(--color-like-soft); }
.m-action-icon { font-size: 28rpx; line-height: 1; color: var(--text-secondary); }
.m-action.active .m-action-icon { color: var(--color-like); }
.m-action-count { font-size: 24rpx; font-weight: var(--weight-semibold); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
.m-action.active .m-action-count { color: var(--color-like); }
</style>
