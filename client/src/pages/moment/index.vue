<template>
  <view class="page moment-detail-page">
    <Header title="动态详情" @back="backToHome" />
    <!-- 内容区滚动 + 局部下拉刷新（moment-detail-publish-ux）：页面壳 flex 纵向，Header 固定顶部不随内容滚动；
         内容区 scroll-view 承担纵向滚动与下拉刷新（refresher），底部评论栏 fixed 不随内容滚动 -->
    <scroll-view
      class="moment-scroll"
      scroll-y
      refresher-enabled
      :refresher-triggered="refresherTriggered"
      @refresherrefresh="onRefresh"
    >
      <view class="moment-scroll-inner">
        <template v-if="moment">
          <!-- 私有组件编排：动态主卡 + 评论区（detail-modular-review-cleanup） -->
          <MomentDetailCard
            :moment="moment"
            :is-author="isAuthor"
            @more="openMomentSheet"
            @go-related="goRelated"
          />
          <MomentCommentSection
            :comments="comments"
            :comment-count="moment.commentCount"
            :moment-id="moment.id"
            :author-id="moment.userId"
            @reply="replyTo"
            @reply-named="replyToNamed"
            @delete="onCommentLongPress"
            @report="openCommentSheet"
            @useful="onCommentUseful"
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
      </view>
    </scroll-view>

    <!-- 底部评论输入栏 -->
    <view class="comment-bar" v-if="moment">
      <view class="comment-input-row">
        <ImageUploader v-model="commentImages" :max="3" compact class="comment-uploader" />
        <view class="comment-input-box" :class="{ focused: commentFocus }">
          <input
            class="comment-input"
            v-model="commentText"
            :focus="commentFocus"
            :placeholder="commentPlaceholder"
            placeholder-class="comment-ph"
            confirm-type="send"
            aria-label="评论输入框"
            @confirm="submitComment"
            @blur="onCommentBlur"
          />
        </view>
        <view class="comment-send" :class="{ disabled: commentSubmitting }" role="button" aria-label="发送评论" @tap="submitComment">
          <IconSvg name="send-simple" :size="30" color="var(--color-on-primary)" class="comment-send-text" />
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

    <!-- 动作面板（ActionSheet）：动态主体卡三点(kind=moment 分享/举报) / 评论长按(kind=comment 仅举报) 共用 -->
    <ActionSheet
      :open="actionOpen"
      :items="momentActionItems"
      @close="actionOpen = false"
      @select="onActionSheetSelect"
    />

    <!-- 认证弹层（未登录点赞/评论/举报 requireAuth 统一在此弹出） -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import { submitFeedback } from '@/api/feedback'
import type { Moment, MomentComment } from '@/types/moment'
import { buildSharePayload, sharedMoment } from '@/utils/share-state'
import { backToHome } from '@/utils/nav'
import Header from '@/components/AppHeader.vue'
import IconSvg from '@/components/IconSvg.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import ReportModal from '@/components/ReportModal.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import ActionSheet from '@/components/ActionSheet.vue'
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
const commentText = ref('')
/** 评论图片（最多 3 张，复用 Moment 图床） */
const commentImages = ref<string[]>([])
/** 评论输入自动聚焦（点互动区「评论」/ 回复时触发） */
const commentFocus = ref(false)

/** 正在回复的评论（moment-detail-publish-ux：回复态以输入框浅灰占位「回复 @昵称」呈现，不再预填文本/提示条/@候选） */
const replyingTo = ref<MomentComment | null>(null)

/** 评论输入占位：回复态显示被回复昵称，否则默认文案（浅灰由 placeholder-class comment-ph 承载） */
const commentPlaceholder = computed(() =>
  replyingTo.value ? `回复 @${replyingTo.value.userNickname}` : '说点什么…',
)

/** 内容区下拉刷新状态（页面级整窗下拉已移除） */
const refresherTriggered = ref(false)

let currentId = 0

const isAuthor = computed(() => !!moment.value && !!userStore.userInfo && moment.value.userId === userStore.userInfo.id)

/** 加载序号：竞态守卫 */
let loadSeq = 0
async function loadData() {
  if (!currentId) return
  const seq = ++loadSeq
  try {
    const [m, c] = await Promise.all([
      momentApi.getMomentDetail(currentId),
      momentApi.getMomentComments(currentId, 1, 50),
    ])
    if (seq !== loadSeq) return
    if (!m) {
      moment.value = null
      return
    }
    moment.value = m
    comments.value = c.list
    if (m) moment.value.commentCount = m.commentCount ?? comments.value.length
  } catch (err) {
    if (seq !== loadSeq) return
    // 静默：请求失败不呈现任何占位，异常仅记录，恢复靠重进页面
    console.error('[moment] 加载动态详情失败', err)
    moment.value = null
  }
}

/** 内容区局部下拉刷新：重新拉取主卡与评论（moment-detail-publish-ux） */
function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => {
    refresherTriggered.value = false
  })
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
  uni.navigateTo({ url: `/pages/publish-moment/index?id=${moment.value.id}` })
}

/* 动态「有用」已由 MomentDetailCard 副本内 useMomentUseful 自管（乐观更新/失败回滚/连点锁/requireAuth 认证），
   页面不再维护 pendingUseful 受控点赞（moment-detail 整卡副本改造） */
/* 评论「有用」切换（moment-comment-thread-view 4.3）：乐观更新 + 失败回滚 + 连点锁 + 未认证先认证 */
const commentUsefulPending = new Set<number>()
async function onCommentUseful(c: MomentComment) {
  const m = moment.value
  if (!m || !c) return
  if (!userStore.requireAuth(() => onCommentUseful(c))) return
  if (commentUsefulPending.has(c.id)) return
  commentUsefulPending.add(c.id)
  const prevActive = !!c.useful
  const prevCount = c.usefulCount || 0
  c.useful = !prevActive
  c.usefulCount = prevActive ? Math.max(0, prevCount - 1) : prevCount + 1
  try {
    const res = await momentApi.toggleMomentCommentUseful(m.id, c.id)
    c.useful = res.useful
    c.usefulCount = res.usefulCount
  } catch (e: any) {
    c.useful = prevActive
    c.usefulCount = prevCount
    uni.showToast({ title: e.message || '操作失败', icon: 'none' })
  } finally {
    commentUsefulPending.delete(c.id)
  }
}

/* 举报动作面板（moment-detail-action-deemphasis）：
   动态主体卡三点(kind=moment) / 评论长按(kind=comment) → 通用 ActionSheet；
   面板内「举报」项再打开 ReportModal（举报免认证，游客可直达） */
const actionOpen = ref(false)
const actionKind = ref<'moment' | 'comment'>('moment')
const actionComment = ref<MomentComment | null>(null)

function openMomentSheet() {
  actionKind.value = 'moment'
  actionComment.value = null
  actionOpen.value = true
}

function openCommentSheet(c: MomentComment) {
  actionKind.value = 'comment'
  actionComment.value = c
  actionOpen.value = true
}

/** 动作项：comment=仅举报；moment=分享 +（非作者时）举报 */
const momentActionItems = computed(() => {
  if (actionKind.value === 'comment') {
    return actionComment.value
      ? [{ key: 'report', label: '举报', icon: 'report', iconColor: 'var(--color-primary)', textColor: 'var(--color-primary)' }]
      : []
  }
  const items: { key: string; label: string; icon: string; iconColor?: string; textColor?: string }[] = []
  if (moment.value) items.push({ key: 'share', label: '分享', icon: 'share' })
  if (!isAuthor.value && moment.value) {
    items.push({ key: 'report', label: '举报', icon: 'report', iconColor: 'var(--color-primary)', textColor: 'var(--color-primary)' })
  }
  return items
})

function onActionSheetSelect(key: string) {
  if (key === 'share' && moment.value) {
    sharedMoment.value = moment.value
  } else if (key === 'report') {
    if (actionKind.value === 'comment') {
      if (actionComment.value) onActionReport(actionComment.value)
    } else if (moment.value) {
      onActionReport(moment.value)
    }
  }
}

function onActionReport(target: Moment | MomentComment) {
  const t = target as MomentComment
  if (t.momentId != null) {
    // 评论举报
    reportTarget.value = { type: 'moment_comment', id: t.id }
  } else if (moment.value) {
    // 动态举报
    reportTarget.value = { type: 'moment', id: moment.value.id }
  }
  reportOpen.value = true
}

/* 举报（动态 or 评论）提交弹窗 */
const reportOpen = ref(false)
const reportSubmitting = ref(false)
const commentSubmitting = ref(false)
const reportTarget = ref<{ type: string; id: number } | null>(null)

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

/* 点击评论本体/「回复」：记录回复对象并聚焦输入框（moment-detail-publish-ux：
   不再预填 @昵称 文本，回复对象以 placeholder 呈现，无提示条与 @候选弹层） */
function replyTo(c: MomentComment) {
  replyingTo.value = c
  commentFocus.value = true
}

function replyToNamed(nickname: string) {
  const target = comments.value.find(c => c.userNickname === nickname)
  if (target) replyTo(target)
}

/* 输入框失焦：关闭聚焦；回复态下输入为空（收起）时退出回复态，占位恢复默认 */
function onCommentBlur() {
  commentFocus.value = false
  if (!commentText.value.trim()) {
    replyingTo.value = null
  }
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
  // 归属直接由回复态对象决定（moment-detail-publish-ux）：正文为纯文本，不再从 ^@ 解析
  const replyTarget = replyingTo.value
  const parentId: number | null = replyTarget ? (replyTarget.parentId ? replyTarget.parentId : replyTarget.id) : null
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
    replyingTo.value = null
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

onLoad((query) => {
  if (query?.id) {
    currentId = Number(query.id)
    loadData()
  }
})
</script>

<style scoped>
/* 页面壳：flex 纵向 + 整窗高度，Header 固定在顶部，页面自身不滚动（moment-detail-publish-ux） */
.moment-detail-page { display: flex; flex-direction: column; height: 100vh; overflow: hidden; background: var(--bg-page); }
/* 内容区：承担纵向滚动与下拉刷新；底部预留固定评论栏高度 + 安全区 */
.moment-scroll { flex: 1; min-height: 0; box-sizing: border-box; }
.moment-scroll-inner { padding-bottom: calc(var(--action-bar-height) + var(--spacing-lg) + env(safe-area-inset-bottom)); }

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
.comment-input { flex: 1; min-width: 0; height: 72rpx; background: transparent; padding: 0; font-size: var(--font-body); color: var(--text-primary); }
.comment-ph { color: var(--text-tertiary); }
/* 发送钮：与输入框同高 72rpx；左侧 ImageUploader compact 的「+」与之同尺寸同圆角（moment-detail-publish-ux） */
.comment-send { width: 72rpx; height: 72rpx; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: var(--color-primary); border-radius: var(--radius-btn); transition: opacity var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out); }
.comment-send:active { opacity: 0.8; }
.comment-send.disabled { opacity: 0.5; pointer-events: none; }
.comment-send-text { line-height: 1; color: var(--color-on-primary); }
</style>
