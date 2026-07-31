<template>
  <view class="page moment-detail-page">
    <Header title="动态详情" showBack />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <view v-if="loading && !moment" class="skeleton">
        <view class="sk-block skeleton" v-for="s in 3" :key="s" />
      </view>

      <template v-else-if="moment">
        <!-- 发布者 -->
        <view class="m-head">
          <image v-if="moment.userAvatar" class="m-avatar" :src="moment.userAvatar" mode="aspectFill" />
          <view v-else class="m-avatar m-avatar-empty"><text class="m-avatar-fallback">{{ EMOJI.dishPlaceholder }}</text></view>
          <view class="m-head-right">
            <text class="m-nickname">{{ moment.userNickname || '匿名用户' }}</text>
            <text class="m-time">{{ relativeTime(moment.createdAt) }}</text>
          </view>
          <!-- 审核态（作者本人可见） -->
          <view v-if="isAuthor && moment.auditStatus && moment.auditStatus !== 'approved'" class="m-audit" :class="auditClass">
            <text class="m-audit-text">{{ auditLabel }}</text>
          </view>
        </view>

        <!-- 正文全文 -->
        <text class="m-content">{{ moment.content }}</text>

        <!-- 九宫格大图 -->
        <view v-if="moment.images.length > 0" class="m-images">
          <view v-for="(img, idx) in moment.images" :key="idx" class="m-image-wrap" @tap="previewImage(idx)">
            <image class="m-image" :src="img" mode="aspectFill" />
          </view>
        </view>

        <!-- 关联对象卡 -->
        <view v-if="moment.relatedType && moment.relatedType !== 'none' && moment.relatedName" class="related-card" @tap="goRelated">
          <text class="related-icon">{{ EMOJI.location }}</text>
          <view class="related-body">
            <text class="related-type">{{ relatedTypeLabel }}</text>
            <text class="related-name">{{ moment.relatedName }}</text>
          </view>
          <text class="related-arrow">{{ EMOJI.arrowRight }}</text>
        </view>

        <!-- 退回原因 + 编辑重提 -->
        <view v-if="isAuthor && moment.auditStatus === 'rejected' && moment.rejectReason" class="reject-box">
          <text class="reject-title">已退回</text>
          <text class="reject-reason">{{ moment.rejectReason }}</text>
          <view class="reject-edit" @tap="goEdit"><text class="reject-edit-text">{{ EMOJI.edit }} 编辑重提</text></view>
        </view>

        <!-- 互动区 -->
        <view class="interact-bar">
          <view class="interact-btn" :class="{ active: usefulActive }" @tap="onUseful">
            <text class="interact-icon">{{ EMOJI.useful }}</text>
            <text class="interact-count">{{ moment.usefulCount > 0 ? moment.usefulCount : '有用' }}</text>
          </view>
          <view class="interact-btn" @tap="focusComment">
            <text class="interact-icon">{{ EMOJI.review }}</text>
            <text class="interact-count">{{ moment.commentCount > 0 ? moment.commentCount : '评论' }}</text>
          </view>
          <view class="interact-btn report" @tap="openReport">
            <text class="interact-icon">{{ EMOJI.report }}</text>
            <text class="interact-count">举报</text>
          </view>
        </view>

        <!-- 评论区 -->
        <view class="comment-section">
          <text class="comment-title">评论 ({{ moment.commentCount }})</text>
          <EmptyState v-if="comments.length === 0" text="还没有评论，来说两句" icon="💬" />
          <view v-else class="comment-list">
            <view
              v-for="c in visibleComments"
              :key="c.id"
              class="comment-item"
              :class="{ pressed: pressedId === c.id }"
              @touchstart="pressedId = c.id"
              @touchend="pressedId = 0"
              @touchcancel="pressedId = 0"
              @mousedown="pressedId = c.id"
              @mouseup="pressedId = 0"
              @mouseleave="pressedId = 0"
              @longpress="onCommentLongPress(c)"
            >
              <image v-if="c.userAvatar" class="c-avatar" :src="c.userAvatar" mode="aspectFill" />
              <view v-else class="c-avatar c-avatar-empty"><text class="c-avatar-fallback">{{ EMOJI.dishPlaceholder }}</text></view>
              <view class="c-body">
                <view class="c-head">
                  <text class="c-nickname">{{ c.userNickname }}</text>
                  <text
                    v-if="c.replyToNickname"
                    class="c-reply"
                    @tap.stop="replyToNamed(c.replyToNickname!)"
                  >回复 @{{ c.replyToNickname }}</text>
                </view>
                <text class="c-content">{{ c.content }}</text>
                <view class="c-footer">
                  <text class="c-time">{{ relativeTime(c.createdAt) }}</text>
                  <view class="c-actions">
                    <text class="c-reply-btn" @tap.stop="replyTo(c)">{{ EMOJI.review }} 回复</text>
                    <view
                      class="c-useful"
                      :class="{ active: c.useful }"
                      @tap.stop="toggleCommentUseful(c)"
                    >
                      <text class="c-useful-icon">{{ EMOJI.useful }}</text>
                      <text class="c-useful-count">{{ c.usefulCount && c.usefulCount > 0 ? c.usefulCount : '有用' }}</text>
                    </view>
                  </view>
                </view>
              </view>
            </view>

            <view v-if="comments.length > collapseThreshold" class="comment-expand" @tap="commentExpanded = !commentExpanded">
              <text class="comment-expand-text">{{ commentExpanded ? '收起' : `共 ${comments.length} 条，点击展开` }}</text>
            </view>
          </view>
        </view>
      </template>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 底部评论输入栏 -->
    <view class="comment-bar" v-if="moment">
      <input
        class="comment-input"
        v-model="commentText"
        :placeholder="replyTarget ? `回复 @${replyTarget.userNickname}` : '说点什么…'"
        confirm-type="send"
        @confirm="submitComment"
      />
      <view class="comment-send" @tap="submitComment">
        <text class="comment-send-text">{{ EMOJI.review }}</text>
      </view>
    </view>

    <!-- 举报弹窗（task-12.7） -->
    <view v-if="reportOpen" class="modal-mask" @tap="reportOpen = false" />
    <view v-if="reportOpen" class="report-modal">
      <text class="report-title">举报动态</text>
      <textarea class="report-input" v-model="reportReason" placeholder="请描述举报原因…" maxlength="500" :auto-height="true" />
      <view class="report-actions">
        <view class="report-btn report-cancel" @tap="reportOpen = false">取消</view>
        <view class="report-btn report-confirm" :class="{ disabled: reportSubmitting }" @tap="submitReport">提交举报</view>
      </view>
    </view>

    <CustomTabBar v-if="false" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import EmptyState from '@/components/EmptyState.vue'
import { EMOJI } from '@/utils/emoji'
import { relativeTime } from '@/utils/time'
import { getImageUrl } from '@/utils/image'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import { submitFeedback } from '@/api/feedback'
import type { Moment, MomentComment } from '@/types/moment'

const userStore = useUserStore()
const moment = ref<Moment | null>(null)
const comments = ref<MomentComment[]>([])
const loading = ref(false)
const refresherTriggered = ref(false)
const commentText = ref('')
const replyTarget = ref<MomentComment | null>(null)
const usefulActive = ref(false)
const pressedId = ref(0)
const commentExpanded = ref(false)
const collapseThreshold = 5

/** 楼中楼「共 N 条」展开/收起（B站式扁平化，task-12.4） */
const visibleComments = computed(() => {
  if (commentExpanded.value || comments.value.length <= collapseThreshold) return comments.value
  return comments.value.slice(0, collapseThreshold)
})

let currentId = 0

const isAuthor = computed(() => !!moment.value && !!userStore.userInfo && moment.value.userId === userStore.userInfo.id)
const relatedTypeLabel = computed(() => {
  if (!moment.value) return ''
  return moment.value.relatedType === 'dish' ? '关联菜品' : moment.value.relatedType === 'stall' ? '关联档口' : ''
})
const auditLabel = computed(() => moment.value?.auditStatus === 'pending' ? '审核中' : '已退回')
const auditClass = computed(() => `audit-${moment.value?.auditStatus}`)

async function loadData() {
  if (!currentId) return
  loading.value = true
  try {
    const [m, c] = await Promise.all([
      momentApi.getMomentDetail(currentId),
      momentApi.getMomentComments(currentId, 1, 50),
    ])
    moment.value = m
    comments.value = c.list
    // 作者本人默认以详情返回为准；非作者不展示审核态
    usefulActive.value = false
  } catch (e: any) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
    moment.value = null
  } finally {
    loading.value = false
  }
}

function previewImage(idx: number) {
  if (!moment.value) return
  uni.previewImage({ urls: moment.value.images.map(getImageUrl), current: moment.value.images.map(getImageUrl)[idx] })
}

function goRelated() {
  if (!moment.value) return
  if (moment.value.relatedType === 'dish' && moment.value.relatedId) {
    uni.navigateTo({ url: `/pages/pages-detail/dish?id=${moment.value.relatedId}` })
  } else if (moment.value.relatedType === 'stall' && moment.value.relatedId) {
    uni.navigateTo({ url: `/pages/pages-detail/stall?id=${moment.value.relatedId}` })
  }
}

function goEdit() {
  if (!moment.value) return
  uni.navigateTo({ url: `/pages/publish-moment/index?id=${moment.value.id}` })
}

async function onUseful() {
  if (!userStore.requireAuth()) return
  if (!moment.value) return
  const prev = usefulActive.value
  const prevCount = moment.value.usefulCount || 0
  usefulActive.value = !prev
  moment.value.usefulCount = prev ? Math.max(0, prevCount - 1) : prevCount + 1
  try {
    const res = await momentApi.toggleUseful(moment.value.id)
    usefulActive.value = res.useful
    moment.value.usefulCount = res.usefulCount
  } catch {
    usefulActive.value = prev
    moment.value.usefulCount = prevCount
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function focusComment() {
  // 仅聚焦输入，小程序 input 无 focus 方法，这里通过提示引导
  uni.showToast({ title: '在底部输入框发表评论', icon: 'none' })
}

/** 举报动态（task-12.7，POST /feedback type=report，relatedType='moment'） */
const reportOpen = ref(false)
const reportReason = ref('')
const reportSubmitting = ref(false)

function openReport() {
  if (!userStore.requireAuth()) return
  reportReason.value = ''
  reportOpen.value = true
}

async function submitReport() {
  if (!moment.value) return
  const text = reportReason.value.trim()
  if (!text) {
    uni.showToast({ title: '请填写举报原因', icon: 'none' })
    return
  }
  reportSubmitting.value = true
  try {
    await submitFeedback({
      type: 'report',
      content: text,
      relatedType: 'moment',
      relatedId: moment.value.id,
    })
    uni.showToast({ title: '举报已提交', icon: 'success' })
    reportOpen.value = false
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    reportSubmitting.value = false
  }
}

function replyTo(c: MomentComment) {
  replyTarget.value = c
}

/** 点击「回复 @昵称」直接以该昵称为回复目标（task-12.4） */
function replyToNamed(nickname: string) {
  const target = comments.value.find(c => c.userNickname === nickname)
  replyTarget.value = target || null
}

/** 评论 👍 有用幂等切换（task-12.4，emoji 语义唯一：👍=有用，不混 ❤️） */
async function toggleCommentUseful(c: MomentComment) {
  if (!userStore.requireAuth()) return
  const prev = !!c.useful
  const prevCount = c.usefulCount || 0
  // 乐观更新
  c.useful = !prev
  c.usefulCount = prev ? Math.max(0, prevCount - 1) : prevCount + 1
  try {
    const res = await momentApi.toggleCommentUseful(moment.value!.id, c.id)
    c.useful = res.useful
    c.usefulCount = res.usefulCount
  } catch {
    c.useful = prev
    c.usefulCount = prevCount
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

async function submitComment() {
  if (!userStore.requireAuth()) return
  if (!moment.value) return
  const content = commentText.value.trim()
  if (!content) {
    uni.showToast({ title: '评论内容不能为空', icon: 'none' })
    return
  }
  const parentId = replyTarget.value && replyTarget.value.parentId ? replyTarget.value.parentId : (replyTarget.value ? replyTarget.value.id : null)
  try {
    await momentApi.commentMoment(moment.value.id, { content, parentId })
    commentText.value = ''
    replyTarget.value = null
    await loadData()
    uni.showToast({ title: '评论成功', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '评论失败', icon: 'none' })
  }
}

function onCommentLongPress(c: MomentComment) {
  if (!userStore.userInfo) return
  if (c.userId !== userStore.userInfo.id) return
  uni.showModal({
    title: '删除评论',
    content: '确定删除这条评论吗？',
    success: async (res) => {
      if (res.confirm && moment.value) {
        try {
          await momentApi.deleteMomentComment(moment.value.id, c.id)
          await loadData()
          uni.showToast({ title: '已删除', icon: 'none' })
        } catch (e: any) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    },
  })
}

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => { refresherTriggered.value = false })
}

onLoad((query) => {
  if (query?.id) {
    currentId = Number(query.id)
    loadData()
  }
})
</script>

<style scoped>
.moment-detail-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }
.skeleton { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.sk-block { width: 100%; height: 160rpx; }
.m-head { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-md); background: var(--bg-card); }
.m-avatar { width: 72rpx; height: 72rpx; border-radius: 50%; background: var(--bg-page); flex-shrink: 0; }
.m-avatar-empty { display: flex; align-items: center; justify-content: center; }
.m-avatar-fallback { font-size: 36rpx; line-height: 1; }
.m-head-right { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.m-nickname { font-size: var(--font-caption); font-weight: 600; color: var(--text-primary); }
.m-time { font-size: var(--font-aux); color: var(--text-tertiary); margin-top: 2rpx; }
.m-audit { padding: 4rpx 12rpx; border-radius: var(--radius-tag); }
.m-audit-text { font-size: 20rpx; font-weight: 700; }
.audit-pending { background: var(--color-warning-soft); }
.audit-pending .m-audit-text { color: var(--color-warning); }
.audit-rejected { background: var(--color-error-soft); }
.audit-rejected .m-audit-text { color: var(--color-error); }
.m-content { display: block; padding: var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; background: var(--bg-card); margin-top: 2rpx; word-break: break-word; }
.m-images { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); padding: var(--spacing-md); background: var(--bg-card); margin-top: 2rpx; }
.m-image-wrap { width: 220rpx; height: 220rpx; border-radius: var(--radius-tag); overflow: hidden; background: var(--bg-page); flex-shrink: 0; }
.m-image { width: 100%; height: 100%; }
.related-card { display: flex; align-items: center; gap: var(--spacing-sm); margin: var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.related-card:active { transform: scale(0.97); }
.related-icon { font-size: 32rpx; line-height: 1; }
.related-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.related-type { font-size: var(--font-aux); color: var(--text-tertiary); }
.related-name { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.related-arrow { font-size: 28rpx; color: var(--text-tertiary); }
.reject-box { margin: 0 var(--spacing-md); padding: var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-card); }
.reject-title { display: block; font-size: var(--font-body); font-weight: 700; color: var(--color-error); margin-bottom: var(--spacing-xs); }
.reject-reason { display: block; font-size: var(--font-body); color: var(--color-error); line-height: 1.5; }
.reject-edit { margin-top: var(--spacing-sm); display: inline-flex; align-items: center; padding: var(--spacing-xs) var(--spacing-md); background: var(--color-primary); border-radius: var(--radius-tag); }
.reject-edit-text { font-size: var(--font-aux); color: var(--text-white); font-weight: 600; }
.interact-bar { display: flex; align-items: center; gap: var(--spacing-md); margin: var(--spacing-md) var(--spacing-md) 0; padding: var(--spacing-sm) var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); }
.interact-btn { display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); border-radius: var(--radius-tag); border: 2rpx solid var(--border-light); transition: transform 0.12s ease, background 0.12s ease, border-color 0.12s ease; -webkit-tap-highlight-color: transparent; }
.interact-btn:active { transform: scale(0.97); }
.interact-btn.active { border-color: var(--color-like); background: var(--color-like-soft); }
.interact-icon { font-size: 30rpx; line-height: 1; color: var(--text-secondary); }
.interact-btn.active .interact-icon { color: var(--color-like); }
.interact-count { font-size: 24rpx; font-weight: 600; color: var(--text-secondary); }
.interact-btn.active .interact-count { color: var(--color-like); }
.comment-section { margin: var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); }
.comment-title { display: block; font-size: var(--font-body); font-weight: 700; color: var(--text-primary); margin-bottom: var(--spacing-sm); }
.comment-list { display: flex; flex-direction: column; }
.comment-item { display: flex; gap: var(--spacing-sm); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.comment-item.pressed { transform: scale(0.985); }
.comment-item:last-child { border-bottom: none; }
.c-avatar { width: 60rpx; height: 60rpx; border-radius: 50%; background: var(--bg-page); flex-shrink: 0; }
.c-avatar-empty { display: flex; align-items: center; justify-content: center; }
.c-avatar-fallback { font-size: 30rpx; line-height: 1; }
.c-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.c-head { display: flex; align-items: baseline; flex-wrap: wrap; }
.c-nickname { font-size: var(--font-aux); font-weight: 600; color: var(--text-primary); }
.c-reply { font-size: var(--font-aux); color: var(--color-primary); margin-left: var(--spacing-xs); transition: opacity 0.12s; -webkit-tap-highlight-color: transparent; }
.c-reply:active { opacity: 0.6; }
.c-content { font-size: var(--font-body); color: var(--text-primary); line-height: 1.5; margin-top: 4rpx; }
.c-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 6rpx; }
.c-time { font-size: var(--font-aux); color: var(--text-tertiary); }
.c-actions { display: inline-flex; align-items: center; gap: var(--spacing-md); }
.c-reply-btn { font-size: var(--font-aux); color: var(--color-primary); align-self: center; transition: opacity 0.12s; -webkit-tap-highlight-color: transparent; }
.c-reply-btn:active { opacity: 0.6; }
.c-useful { display: inline-flex; align-items: center; gap: 4rpx; padding: 2rpx 10rpx; border-radius: var(--radius-tag); border: 2rpx solid var(--border-light); transition: transform 0.12s ease, background 0.12s ease, border-color 0.12s ease; -webkit-tap-highlight-color: transparent; }
.c-useful:active { transform: scale(0.95); }
.c-useful.active { border-color: var(--color-like); background: var(--color-like-soft); }
.c-useful-icon { font-size: 26rpx; line-height: 1; color: var(--text-secondary); }
.c-useful.active .c-useful-icon { color: var(--color-like); }
.c-useful-count { font-size: 20rpx; font-weight: 600; color: var(--text-secondary); }
.c-useful.active .c-useful-count { color: var(--color-like); }
.comment-expand { padding: var(--spacing-sm) 0; text-align: center; }
.comment-expand-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: 600; }
.comment-bar { position: fixed; left: 0; right: 0; bottom: 0; display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); z-index: 50; }
.comment-input { flex: 1; height: 72rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: 0 var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); }
.comment-send { width: 88rpx; height: 72rpx; display: flex; align-items: center; justify-content: center; background: var(--color-primary); border-radius: var(--radius-btn); }
.comment-send-text { font-size: 32rpx; line-height: 1; color: var(--text-white); }

/* 互动栏举报按钮（task-12.7） */
.interact-btn.report { margin-left: auto; }
.interact-btn.report .interact-icon { color: var(--text-tertiary); }
.interact-btn.report .interact-count { color: var(--text-tertiary); }

/* 举报弹窗（task-12.7） */
.modal-mask { position: fixed; inset: 0; background: var(--overlay-scrim); display: flex; align-items: center; justify-content: center; z-index: 100; }
.report-modal { position: fixed; left: 50%; top: 50%; transform: translate(-50%, -50%); width: 600rpx; max-width: 86vw; background: var(--bg-card); border-radius: var(--radius-modal); padding: var(--spacing-xl); box-shadow: var(--shadow-modal); z-index: 101; }
.report-title { display: block; font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); text-align: center; margin-bottom: var(--spacing-lg); }
.report-input { width: 100%; min-height: 180rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; box-sizing: border-box; }
.report-actions { display: flex; gap: var(--spacing-sm); margin-top: var(--spacing-lg); }
.report-btn { flex: 1; height: 80rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); font-size: var(--font-body); font-weight: 600; }
.report-cancel { background: var(--bg-page); color: var(--text-secondary); }
.report-confirm { background: var(--color-error); color: var(--text-white); }
.report-confirm.disabled { opacity: 0.58; }
</style>
