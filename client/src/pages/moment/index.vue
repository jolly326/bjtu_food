<template>
  <view class="page moment-detail-page">
    <Header title="动态详情" @back="backToHome" />
    <scroll-view class="scroll-wrap" scroll-y :scroll-into-view="commentIntoView" refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <!-- 加载/失败/空态统一由 StateView 一次承载 -->
      <StateView
        v-if="!moment"
        :loading="loading"
        :failed="!deleted"
        :empty="deleted"
        error-text="动态加载失败，请稍后重试"
        empty-text="该动态不存在或已删除"
        :empty-retry="true"
        @retry="loadData"
      />

      <template v-else>
        <!-- 私有组件编排：动态主卡 + 评论区（detail-modular-review-cleanup） -->
        <MomentDetailCard
          :moment="moment"
          :is-author="isAuthor"
          :useful-pending="pendingUseful"
          @useful="onUseful"
          @comment="focusComment"
          @report="openReport"
          @related="goRelated"
        />
        <MomentCommentSection
          :comments="comments"
          :comment-count="moment.commentCount"
          :moment-id="moment.id"
          @reply="replyTo"
          @reply-named="replyToNamed"
          @delete="onCommentLongPress"
          @report="onCommentReport"
        />

        <!-- 退回原因 + 编辑重提 -->
        <view v-if="isAuthor && moment.auditStatus === 'rejected' && moment.rejectReason" class="reject-box">
          <text class="reject-title">已退回</text>
          <text class="reject-reason">{{ moment.rejectReason }}</text>
          <view class="reject-edit" @tap="goEdit">
            <IconSvg name="edit" :size="26" color="var(--color-on-primary)" />
            <text class="reject-edit-text">编辑重提</text>
          </view>
        </view>
      </template>

    </scroll-view>

    <!-- 底部评论输入栏 -->
    <view class="comment-bar" v-if="moment">
      <transition name="mention">
      <view v-if="mentionOpen" class="mention-pop">
        <view
          v-for="name in filteredMentions"
          :key="name"
          class="mention-item"
          @tap="selectMention(name)"
        >
          <text class="mention-at">@</text>
          <text class="mention-name">{{ name }}</text>
        </view>
        <view v-if="!filteredMentions.length" class="mention-empty">暂无匹配评论者</view>
      </view>
      </transition>
      <view class="comment-input-row">
        <ImageUploader v-model="commentImages" :max="3" compact class="comment-uploader" />
        <view class="comment-input-box" :class="{ focused: commentFocus }">
          <input
          class="comment-input"
          v-model="commentText"
          :focus="commentFocus"
          placeholder="说点什么…"
          placeholder-class="comment-ph"
          confirm-type="send"
          aria-label="评论输入框"
          @confirm="submitComment"
          @blur="onCommentBlur"
          />
        </view>
        <view class="comment-send" :class="{ disabled: commentSubmitting }" role="button" aria-label="发送评论" @tap="submitComment">
          <IconSvg name="send" :size="32" color="var(--color-on-primary)" class="comment-send-text" />
        </view>
      </view>
    </view>

    <!-- 举报弹窗（共享组件） -->
    <ReportModal
      :open="reportOpen"
      :title="reportTarget && reportTarget.type === 'moment_comment' ? '举报评论' : '举报动态'"
      placeholder="请描述举报原因…"
      confirm-text="提交举报"
      :submitting="reportSubmitting"
      @update:open="reportOpen = $event"
      @submit="submitReport"
    />

    <!-- 认证弹层（未登录点赞/评论/举报 requireAuth 统一在此弹出） -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onLoad, onUnload, onShareAppMessage } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import { submitFeedback } from '@/api/feedback'
import type { Moment, MomentComment } from '@/types/moment'
import { buildSharePayload } from '@/utils/share-state'
import { backToHome } from '@/utils/nav'
import Header from '@/components/AppHeader.vue'
import StateView from '@/components/StateView.vue'
import IconSvg from '@/components/IconSvg.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import ReportModal from '@/components/ReportModal.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import MomentDetailCard from './MomentDetailCard.vue'
import MomentCommentSection from './MomentCommentSection.vue'

const userStore = useUserStore()
const moment = ref<Moment | null>(null)
onShareAppMessage(() => buildSharePayload(undefined, moment.value))
function openDishDetail(id: number) {
  if (!id) return
  uni.navigateTo({ url: `/pages/dish/index?id=${id}` })
}
const comments = ref<MomentComment[]>([])
const loading = ref(false)
const deleted = ref(false)
const refresherTriggered = ref(false)
const commentText = ref('')
/** 评论图片（最多 3 张，复用 Moment 图床） */
const commentImages = ref<string[]>([])
/** 评论输入自动聚焦（点互动区「评论」/ 回复时触发） */
const commentFocus = ref(false)
/** @提及弹层状态 */
const mentionOpen = ref(false)
const mentionQuery = ref('')
const atPos = ref(-1)

let currentId = 0

/** 页面级定时器注册表：onUnload 统一清理 */
let pageTimers: ReturnType<typeof setTimeout>[] = []
onUnload(() => {
  pageTimers.forEach((t) => clearTimeout(t))
  pageTimers = []
})

const isAuthor = computed(() => !!moment.value && !!userStore.userInfo && moment.value.userId === userStore.userInfo.id)

/** 加载序号：竞态守卫 */
let loadSeq = 0
async function loadData() {
  if (!currentId) return
  const seq = ++loadSeq
  loading.value = true
  deleted.value = false
  try {
    const [m, c] = await Promise.all([
      momentApi.getMomentDetail(currentId),
      momentApi.getMomentComments(currentId, 1, 50),
    ])
    if (seq !== loadSeq) return
    if (!m) {
      deleted.value = true
      moment.value = null
      return
    }
    moment.value = m
    comments.value = c.list
    if (m) moment.value.commentCount = m.commentCount ?? comments.value.length
  } catch (e: any) {
    if (seq !== loadSeq) return
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
    moment.value = null
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function goRelated() {
  if (!moment.value) return
  if (moment.value.relatedType === 'dish' && moment.value.relatedId) {
    openDishDetail(moment.value.relatedId)
  }
  // 档口详情页已下线：相关档口不再展示跳转入口
}

function goEdit() {
  if (!moment.value) return
  uni.navigateTo({ url: `/pages/publish-content/index?id=${moment.value.id}` })
}

/** scroll-into-view 目标：点「评论」定位到评论区（id 位于 MomentCommentSection 内） */
const commentIntoView = ref('')
function focusComment() {
  commentIntoView.value = ''
  const t = setTimeout(() => {
    commentIntoView.value = 'comment-section'
    commentFocus.value = true
  }, 30)
  pageTimers.push(t)
}

/** 动态「有用」乐观更新（pendingUseful 锁防连点） */
const pendingUseful = ref(false)
function onUseful() {
  const m = moment.value
  if (!m) return
  if (!userStore.requireAuth(() => onUseful())) return
  if (pendingUseful.value) return
  pendingUseful.value = true
  const prevActive = !!m.useful
  const prevCount = m.usefulCount || 0
  m.useful = !prevActive
  m.usefulCount = prevActive ? Math.max(0, prevCount - 1) : prevCount + 1
  momentApi
    .toggleUseful(m.id)
    .then((res) => {
      m.useful = res.useful
      m.usefulCount = res.usefulCount
    })
    .catch(() => {
      m.useful = prevActive
      m.usefulCount = prevCount
      uni.showToast({ title: '操作失败', icon: 'none' })
    })
    .finally(() => {
      pendingUseful.value = false
    })
}

/* 举报（动态 or 评论） */
const reportOpen = ref(false)
const reportSubmitting = ref(false)
const commentSubmitting = ref(false)
const reportTarget = ref<{ type: string; id: number } | null>(null)

function openReport() {
  if (!userStore.requireAuth(() => openReport())) return
  reportTarget.value = moment.value ? { type: 'moment', id: moment.value.id } : null
  reportOpen.value = true
}

function onCommentReport(c: MomentComment) {
  if (!userStore.requireAuth(() => onCommentReport(c))) return
  reportTarget.value = { type: 'moment_comment', id: c.id }
  reportOpen.value = true
}

async function submitReport(text: string) {
  if (!reportTarget.value) return
  if (!text) {
    uni.showToast({ title: '请填写举报原因', icon: 'none' })
    return
  }
  reportSubmitting.value = true
  try {
    await submitFeedback({
      type: 'report',
      content: text,
      relatedType: reportTarget.value.type,
      relatedId: reportTarget.value.id,
    })
    uni.showToast({ title: '举报已提交', icon: 'success' })
    reportOpen.value = false
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    reportSubmitting.value = false
  }
}

/* 点击「回复 @昵称」等价于在输入框写入 @昵称 */
function replyTo(c: MomentComment) {
  commentText.value = '@' + c.userNickname + ' '
  commentFocus.value = true
  mentionOpen.value = false
}

function replyToNamed(nickname: string) {
  const target = comments.value.find(c => c.userNickname === nickname)
  if (target) replyTo(target)
}

/** 候选评论者昵称（作者 + 评论者，去重） */
const mentionList = computed(() => {
  const set = new Set<string>()
  if (moment.value?.userNickname) set.add(moment.value.userNickname)
  comments.value.forEach(c => { if (c.userNickname) set.add(c.userNickname) })
  return [...set]
})

const filteredMentions = computed(() => {
  const q = mentionQuery.value.trim().toLowerCase()
  if (!q) return mentionList.value
  return mentionList.value.filter(n => n.toLowerCase().includes(q))
})

watch(commentText, (val, old) => {
  if (mentionOpen.value) {
    if (val[atPos.value] !== '@') {
      mentionOpen.value = false
      return
    }
    mentionQuery.value = val.slice(atPos.value + 1)
    return
  }
  if (val.length === old.length + 1 && val.endsWith('@')) {
    atPos.value = val.length - 1
    mentionQuery.value = ''
    mentionOpen.value = true
  }
})

function selectMention(name: string) {
  const before = commentText.value.slice(0, atPos.value + 1) // 含 @
  const after = commentText.value.slice(atPos.value + 1 + mentionQuery.value.length)
  commentText.value = before + name + ' ' + after
  mentionOpen.value = false
  commentFocus.value = true
}

function onCommentBlur() {
  commentFocus.value = false
  const t = setTimeout(() => { mentionOpen.value = false }, 150)
  pageTimers.push(t)
}

async function submitComment() {
  if (!userStore.requireAuth(() => submitComment())) return
  if (!moment.value) return
  const content = commentText.value.trim()
  const hasImages = commentImages.value.length > 0
  if (!content && !hasImages) {
    uni.showToast({ title: '说点什么或加张图吧', icon: 'none' })
    return
  }
  if (commentSubmitting.value) return
  let parentId: number | null = null
  const m = content.match(/^@(\S+)\s/)
  if (m) {
    const target = comments.value.find(c => c.userNickname === m[1])
    if (target) parentId = target.parentId ? target.parentId : target.id
  }
  const images = commentImages.value.length ? [...commentImages.value] : null
  commentSubmitting.value = true
  try {
    await momentApi.commentMoment(moment.value.id, { content, parentId, images })
    const me = userStore.userInfo
    comments.value.push({
      id: -Date.now(),
      momentId: moment.value.id,
      userId: me?.id ?? 0,
      userNickname: me?.nickname || '我',
      userAvatar: me?.avatar || '',
      parentId: parentId ?? null,
      content,
      images: images || null,
      usefulCount: 0,
      useful: false,
      createdAt: new Date().toISOString(),
    })
    moment.value.commentCount += 1
    commentText.value = ''
    commentImages.value = []
    mentionOpen.value = false
    uni.showToast({ title: '评论成功', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e.message || '评论失败', icon: 'none' })
  } finally {
    commentSubmitting.value = false
  }
}

async function onCommentLongPress(c: MomentComment) {
  if (!userStore.userInfo) return
  if (c.userId !== userStore.userInfo.id) return
  uni.showModal({
    title: '删除评论',
    content: '确定删除这条评论吗？',
    success: async (res) => {
      if (res.confirm && moment.value) {
        try {
          await momentApi.deleteMomentComment(moment.value.id, c.id)
          comments.value = comments.value.filter((x) => x.id !== c.id)
          moment.value.commentCount = Math.max(0, moment.value.commentCount - 1)
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
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--action-bar-height) + var(--spacing-lg) + env(safe-area-inset-bottom)); }

/* 退回原因 + 编辑重提 */
.reject-box { margin: 0 var(--spacing-md); padding: var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-card); }
.reject-title { display: block; font-size: var(--font-body); font-weight: var(--weight-bold); color: var(--color-error); margin-bottom: var(--spacing-xs); }
.reject-reason { display: block; font-size: var(--font-body); color: var(--color-error); line-height: 1.5; }
.reject-edit { margin-top: var(--spacing-sm); display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); background: var(--color-primary); border-radius: var(--radius-tag); }
.reject-edit-text { font-size: var(--font-aux); color: var(--color-on-primary); font-weight: var(--weight-semibold); }

/* 底部评论输入栏 */
.comment-bar { position: fixed; left: 0; right: 0; bottom: 0; display: flex; flex-direction: column; padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); z-index: 50; }
.comment-input-row { display: flex; align-items: center; gap: var(--spacing-sm); }
.comment-uploader { flex-shrink: 0; }
.comment-input-box { flex: 1; display: flex; align-items: center; min-width: 0; height: 72rpx; background: var(--bg-input); border-radius: var(--radius-btn); padding: 0 var(--spacing-md); border: 2rpx solid var(--border-color); transition: border-color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out); }
.comment-input-box.focused { border-color: var(--color-primary); }
.comment-input { flex: 1; min-width: 0; height: 72rpx; background: transparent; padding: 0; font-size: var(--font-subtitle); color: var(--text-primary); }
.comment-ph { color: var(--text-tertiary); }
.comment-send { width: 88rpx; height: 72rpx; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: var(--color-primary); border-radius: var(--radius-btn); transition: opacity var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out); }
.comment-send:active { opacity: 0.8; }
.comment-send.disabled { opacity: 0.5; pointer-events: none; }
.comment-send-text { font-size: var(--font-subtitle); line-height: 1; color: var(--color-on-primary); }
.mention-pop { position: absolute; left: var(--spacing-md); right: var(--spacing-md); bottom: calc(100% + 8rpx); background: var(--bg-card); border: 2rpx solid var(--border-color); border-radius: var(--radius-card); box-shadow: var(--shadow-bar-soft); max-height: 360rpx; overflow-y: auto; padding: var(--spacing-xs) 0; z-index: 60; }
.mention-item { display: flex; align-items: center; padding: var(--spacing-sm) var(--spacing-md); }
.mention-item:active { background: var(--bg-soft); }
.mention-at { color: var(--color-primary); font-weight: var(--weight-semibold); margin-right: 4rpx; font-size: var(--font-caption); }
.mention-name { font-size: var(--font-caption); color: var(--text-primary); }
.mention-empty { padding: var(--spacing-sm) var(--spacing-md); font-size: var(--font-aux); color: var(--text-tertiary); }
</style>
