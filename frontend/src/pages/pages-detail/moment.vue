<template>
  <view class="page moment-detail-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="动态详情" showBack />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <view v-if="loading && !moment" class="skeleton">
        <view class="sk-block skeleton" v-for="s in 3" :key="s" />
      </view>

      <EmptyState
        v-else-if="!moment"
        text="动态加载失败或不存在"
        :retry="true"
        @retry="loadData"
      />

      <template v-else>
        <!-- 动态主卡（合并卡）：发布者 + 正文 + 九宫格 + 关联对象 + 点赞评论举报 + 用户评价 全部一张卡 -->
        <view class="m-card">
          <view class="m-head">
            <image v-if="moment.userAvatar" class="m-avatar" :src="moment.userAvatar" mode="aspectFill" />
            <view v-else class="m-avatar m-avatar-empty">
              <IconSvg name="user" :size="36" color="var(--text-tertiary)" />
            </view>
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
          <view class="m-images">
            <MomentImageGrid :images="moment.images" />
          </view>

          <!-- 关联对象（与下方互动栏用细线分隔） -->
          <view v-if="moment.relatedType && moment.relatedType !== 'none' && moment.relatedName" class="related-card" @tap="goRelated">
            <IconSvg :name="relatedIconName" :size="28" color="var(--text-tertiary)" class="related-icon" />
            <view class="related-body">
              <text class="related-type">{{ relatedTypeLabel }}</text>
              <text class="related-name">{{ moment.relatedName }}</text>
            </view>
            <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="related-arrow" />
          </view>

          <!-- 互动区（点赞 / 评论 / 举报） -->
          <InteractBar
            :useful-active="usefulActive"
            :useful-count="moment.usefulCount"
            :comment-count="moment.commentCount"
            @useful="onUseful"
            @comment="focusComment"
            @report="openReport"
          />

          <!-- 用户评价（关联菜品时展示前 2 条，上分隔线） -->
          <view v-if="moment.relatedType === 'dish' && dishReviews.length > 0" class="review-section">
            <text class="comment-title">用户评价</text>
            <ReviewItem v-for="rv in dishReviews.slice(0, 2)" :key="rv.id" :review="rv" />
          </view>
        </view>

        <!-- 评论单独卡片（task todo#9） -->
        <view class="comment-section">
          <text class="comment-title">评论 ({{ moment.commentCount }})</text>
          <EmptyState v-if="comments.length === 0" text="还没有评论，来说两句" icon="comment" />
          <view v-else class="comment-list">
            <CommentItem
              v-for="c in visibleComments"
              :key="c.id"
              :comment="c"
              :moment-id="moment.id"
              @reply="replyTo"
              @reply-named="replyToNamed"
              @delete="onCommentLongPress"
            />

            <view v-if="comments.length > collapseThreshold" class="comment-expand" @tap="commentExpanded = !commentExpanded">
              <text class="comment-expand-text">{{ commentExpanded ? '收起' : `共 ${comments.length} 条，点击展开` }}</text>
            </view>
          </view>
        </view>

        <!-- 退回原因 + 编辑重提 -->
        <view v-if="isAuthor && moment.auditStatus === 'rejected' && moment.rejectReason" class="reject-box">
          <text class="reject-title">已退回</text>
          <text class="reject-reason">{{ moment.rejectReason }}</text>
          <view class="reject-edit" @tap="goEdit">
            <IconSvg name="edit" :size="26" color="var(--text-white)" />
            <text class="reject-edit-text">编辑重提</text>
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
        :focus="commentFocus"
        :placeholder="replyTarget ? `回复 @${replyTarget.userNickname}` : '说点什么…'"
        confirm-type="send"
        @confirm="submitComment"
        @blur="commentFocus = false"
      />
      <view class="comment-send" @tap="submitComment">
        <IconSvg name="comment" :size="32" color="var(--text-white)" class="comment-send-text" />
      </view>
    </view>

    <!-- 举报弹窗（共享组件） -->
    <ReportModal
      :open="reportOpen"
      title="举报动态"
      placeholder="请描述举报原因…"
      confirm-text="提交举报"
      :submitting="reportSubmitting"
      @update:open="reportOpen = $event"
      @submit="submitReport"
    />

    <!-- 菜品详情底部弹层（task-10：独立页 → sheet） -->
    <DishDetailSheet
      :open="dishSheetOpen"
      :dish-id="sheetDishId"
      top-offset="176rpx"
      @update:open="dishSheetOpen = $event"
    />
  </view>
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
const theme = useThemeStore()
import { ref, computed, onMounted } from 'vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconSvg from '@/components/IconSvg.vue'
import MomentImageGrid from '@/components/MomentImageGrid.vue'
import InteractBar from '@/components/InteractBar.vue'
import CommentItem from '@/components/CommentItem.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import ReportModal from '@/components/ReportModal.vue'
import DishDetailSheet from '@/components/DishDetailSheet.vue'
import { relativeTime } from '@/utils/time'
import { useUserStore } from '@/stores/user'
import { useDishStore } from '@/stores/dish'
import * as momentApi from '@/api/moment'
import { getReviewsByDish } from '@/api/review'
import { submitFeedback } from '@/api/feedback'
import type { Moment, MomentComment } from '@/types/moment'
import type { Review } from '@/types/review'
import { buildSharePayload } from '@/utils/shareState'

const userStore = useUserStore()
const dishStore = useDishStore()
const moment = ref<Moment | null>(null)
// 微信原生分享：优先菜品（DishDetailSheet 分享按钮），其次当前动态，默认首页
onShareAppMessage(() => buildSharePayload(undefined, moment.value))
/** 菜品详情底部弹层（task-10：独立页 → sheet） */
const dishSheetOpen = ref(false)
const sheetDishId = ref(0)
function openDishSheet(id: number) {
  if (!id) return
  sheetDishId.value = id
  dishSheetOpen.value = true
}
const comments = ref<MomentComment[]>([])
/** 关联菜品的用户评价（task todo#9：并入关联+互动卡） */
const dishReviews = ref<Review[]>([])
const loading = ref(false)
const refresherTriggered = ref(false)
const commentText = ref('')
const replyTarget = ref<MomentComment | null>(null)
/** 评论输入自动聚焦（点 InteractBar「评论」/ 楼中楼回复时触发，2026-08-03 打磨） */
const commentFocus = ref(false)
const usefulActive = ref(false)
const commentExpanded = ref(false)
const collapseThreshold = 5

/** 楼中楼「共 N 条」展开/收起 */
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
const relatedIconName = computed(() => {
  if (!moment.value) return 'dish'
  return moment.value.relatedType === 'stall' ? 'list' : 'dish'
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
    usefulActive.value = false
    // 关联菜品时加载该菜品的用户评价（task todo#9）
    if (m.relatedType === 'dish' && m.relatedId) {
      try {
        dishReviews.value = await getReviewsByDish(m.relatedId)
      } catch {
        dishReviews.value = []
      }
    } else {
      dishReviews.value = []
    }
  } catch (e: any) {
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
    moment.value = null
  } finally {
    loading.value = false
  }
}

function goRelated() {
  if (!moment.value) return
  if (moment.value.relatedType === 'dish' && moment.value.relatedId) {
    openDishSheet(moment.value.relatedId)
  } else if (moment.value.relatedType === 'stall' && moment.value.relatedName && moment.value.relatedCanteen) {
    // 档口详情靠 navParams（stallName + canteen）加载，不能用 ?id=（stall 页不支持）
    dishStore.navParams = { stallName: moment.value.relatedName, canteen: moment.value.relatedCanteen }
    uni.navigateTo({ url: '/pages/pages-detail/stall' })
  }
}

function goEdit() {
  if (!moment.value) return
  uni.navigateTo({ url: `/pages/pages-user/publish-moment/index?id=${moment.value.id}` })
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
  // 先让底部输入栏进入视口（滚动到底部），再触发聚焦弹键盘
  uni.pageScrollTo({ scrollTop: 99999, duration: 200 })
  commentFocus.value = true
}

/** 举报动态 */
const reportOpen = ref(false)
const reportSubmitting = ref(false)
/** 评论提交中：防连点 */
const commentSubmitting = ref(false)

function openReport() {
  if (!userStore.requireAuth()) return
  reportOpen.value = true
}

async function submitReport(text: string) {
  if (!moment.value) return
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

/** 点击「回复 @昵称」直接以该昵称为回复目标 */
function replyToNamed(nickname: string) {
  const target = comments.value.find(c => c.userNickname === nickname)
  replyTarget.value = target || null
}

async function submitComment() {
  if (!userStore.requireAuth()) return
  if (!moment.value) return
  const content = commentText.value.trim()
  if (!content) {
    uni.showToast({ title: '评论内容不能为空', icon: 'none' })
    return
  }
  if (commentSubmitting.value) return
  const parentId = replyTarget.value && replyTarget.value.parentId ? replyTarget.value.parentId : (replyTarget.value ? replyTarget.value.id : null)
  commentSubmitting.value = true
  try {
    await momentApi.commentMoment(moment.value.id, { content, parentId })
    commentText.value = ''
    replyTarget.value = null
    await loadData()
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
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom)); }
.skeleton { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.sk-block { width: 100%; height: 160rpx; }
/* 动态主卡（合并卡）：发布者 + 正文 + 九宫格 + 关联对象 + 点赞评论举报 + 用户评价 全部一张卡；评论区单独一张卡 */
.m-card { margin: var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card); }
.m-head { display: flex; align-items: center; gap: var(--spacing-sm); }
.m-avatar { width: 72rpx; height: 72rpx; border-radius: 16rpx; background: var(--bg-page); flex-shrink: 0; }
.m-avatar-empty { display: flex; align-items: center; justify-content: center; }
.m-head-right { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.m-nickname { font-size: var(--font-caption); font-weight: var(--weight-semibold); color: var(--text-primary); }
.m-time { font-size: var(--font-aux); color: var(--text-tertiary); margin-top: 2rpx; font-variant-numeric: tabular-nums; }
.m-audit { padding: 4rpx 12rpx; border-radius: var(--radius-tag); }
.m-audit-text { font-size: 20rpx; font-weight: var(--weight-bold); }
.audit-pending { background: var(--color-warning-soft); }
.audit-pending .m-audit-text { color: var(--color-warning); }
.audit-rejected { background: var(--color-error-soft); }
.audit-rejected .m-audit-text { color: var(--color-error); }
.m-content { display: block; margin-top: var(--spacing-sm); font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; word-break: break-word; }
.m-images { margin-top: var(--spacing-sm); }
.related-card { display: flex; align-items: center; gap: var(--spacing-sm); margin: var(--spacing-sm) 0 0; padding: var(--spacing-sm) 0 var(--spacing-md); background: transparent; border-radius: 0; box-shadow: none; border-bottom: 2rpx solid var(--border-color); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.related-card:active { transform: scale(var(--press-scale)); }
/* 互动栏顶部留白在 InteractBar 组件内实现（mp-weixin 样式隔离，:deep 不生效） */
.related-icon { font-size: 32rpx; line-height: 1; }
.related-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.related-type { font-size: var(--font-aux); color: var(--text-tertiary); }
.related-name { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.related-arrow { font-size: 28rpx; color: var(--text-tertiary); }
.reject-box { margin: 0 var(--spacing-md); padding: var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-card); }
.reject-title { display: block; font-size: var(--font-body); font-weight: var(--weight-bold); color: var(--color-error); margin-bottom: var(--spacing-xs); }
.reject-reason { display: block; font-size: var(--font-body); color: var(--color-error); line-height: 1.5; }
.reject-edit { margin-top: var(--spacing-sm); display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); background: var(--color-primary); border-radius: var(--radius-tag); }
.reject-edit-text { font-size: var(--font-aux); color: var(--text-white); font-weight: var(--weight-semibold); }
/* 评论卡（Apple Design Typography：大字负 tracking + 设计系统 card 规范）。
   与菜品详情评价区、档口详情评价 tab 的 comment-section 完全同款（升级版：圆角 24px、标题 34rpx weight 800、阴影更深更柔） */
.comment-section { margin: 0 var(--spacing-md) var(--spacing-md); padding: var(--spacing-md) var(--spacing-md) var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card-soft); }
/* 用户评价区（并入关联+互动卡，上分隔线） */
.review-section { margin: var(--spacing-sm) 0 0; padding: var(--spacing-sm) 0 0; border-top: 2rpx solid var(--border-color); }
.comment-title { font-size: var(--font-h3); font-weight: var(--weight-heavy); color: var(--text-primary); letter-spacing: var(--tracking-h3); margin-bottom: var(--spacing-md); }
.comment-list { display: flex; flex-direction: column; }
.comment-expand { padding: var(--spacing-sm) 0; text-align: center; }
.comment-expand-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); }
.comment-bar { position: fixed; left: 0; right: 0; bottom: 0; display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); z-index: 50; }
.comment-input { flex: 1; height: 72rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: 0 var(--spacing-md); font-size: 32rpx; color: var(--text-primary); }
.comment-send { width: 88rpx; height: 72rpx; display: flex; align-items: center; justify-content: center; background: var(--color-primary); border-radius: var(--radius-btn); transition: opacity 120ms var(--ease-out), transform 120ms var(--ease-out); }
.comment-send:active { opacity: 0.8; transform: scale(var(--press-scale)); }
.comment-send-text { font-size: 32rpx; line-height: 1; color: var(--text-white); }
</style>
