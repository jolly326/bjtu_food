<template>
  <view class="page moment-detail-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="动态详情" @back="backToHome" />
    <scroll-view class="scroll-wrap" scroll-y :scroll-into-view="commentIntoView" refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <!-- 骨架屏：贴合真实首屏（主卡：发布者行 + 正文 + 三图 + 互动按钮；评论卡：标题 + 两条评论），
           子元素统一挂全局 .skeleton 闪烁类，避免加载完成跳变 -->
      <view v-if="loading && !moment" class="detail-skeleton">
        <view class="sk-card">
          <view class="sk-head">
            <view class="sk-avatar skeleton" />
            <view class="sk-lines">
              <view class="sk-line w-40 skeleton" />
              <view class="sk-line w-25 skeleton" />
            </view>
          </view>
          <view class="sk-line w-90 skeleton" />
          <view class="sk-line w-75 skeleton" />
          <view class="sk-grid">
            <view v-for="i in 3" :key="'g' + i" class="sk-img skeleton" />
          </view>
          <view class="sk-actions">
            <view class="sk-action skeleton" />
            <view class="sk-action skeleton" />
          </view>
        </view>
        <view class="sk-card">
          <view class="sk-line w-30 skeleton" />
          <view v-for="i in 2" :key="'c' + i" class="sk-comment">
            <view class="sk-avatar skeleton" />
            <view class="sk-lines">
              <view class="sk-line w-55 skeleton" />
              <view class="sk-line w-85 skeleton" />
            </view>
          </view>
        </view>
      </view>

      <!-- 动态已删除/审核下架：接口返回空，显示兜底提示而非空白 -->
      <EmptyState
        v-else-if="!moment && deleted"
        text="该动态不存在或已删除"
        :retry="true"
        @retry="loadData"
      />

      <EmptyState
        v-else-if="!moment"
        text="动态加载失败，请稍后重试"
        :retry="true"
        @retry="loadData"
      />

      <template v-else>
        <!-- 动态主卡（合并卡）：发布者 + 正文 + 九宫格 + 关联对象 + 点赞评论举报 + 用户评价 全部一张卡 -->
        <view class="m-card">
          <view class="m-head">
            <image v-if="moment.userAvatar" class="m-avatar" :src="getImageUrl(moment.userAvatar)" mode="aspectFill" />
            <view v-else class="m-avatar m-avatar-empty">
              <IconSvg name="user" :size="36" color="var(--text-tertiary)" />
            </view>
            <view class="m-head-right">
              <text class="m-nickname">{{ moment.userNickname || '匿名用户' }}</text>
              <text class="m-time">{{ formatDateTime(moment.createdAt) }}</text>
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

          <!-- 关联对象（与下方互动栏用细线分隔）：左侧圆角正方形菜品缩略图（有图显图、无图显占位图标） -->
          <view v-if="moment.relatedType && moment.relatedType !== 'none' && moment.relatedName" class="related-card" @tap="goRelated">
            <image v-if="moment.relatedImage" class="related-thumb" :src="getImageUrl(moment.relatedImage)" mode="aspectFill" lazy-load />
            <view v-else class="related-thumb related-thumb--empty">
              <IconSvg :name="relatedIconName" :size="28" color="var(--text-tertiary)" class="related-icon" />
            </view>
            <view class="related-body">
              <text class="related-type">{{ relatedTypeLabel }}</text>
              <view class="related-name-row">
                <text class="related-name">{{ moment.relatedName }}</text>
                <!-- 关联菜品评分（1 星 + 数字，与评价卡统一单星形态） -->
                <view v-if="moment.relatedType === 'dish' && (moment.relatedRating || 0) > 0" class="related-rating">
                  <IconSvg name="star-filled" :size="22" color="var(--color-star)" />
                  <text class="related-rating-num">{{ (moment.relatedRating || 0).toFixed(1) }}</text>
                </view>
              </view>
            </view>
            <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="related-arrow" />
          </view>

          <!-- 互动区（有用 / 评论 / 举报） -->
          <InteractBar
            :comment-count="moment.commentCount"
            :useful-count="moment.usefulCount"
            :useful-active="!!moment.useful"
            :useful-pending="pendingUseful"
            @useful="onUseful"
            @comment="focusComment"
            @report="openReport"
          />
        </view>

        <!-- 评论单独卡片（id 供「评论」按钮 scroll-into-view 定位） -->
        <view id="comment-section" class="comment-section">
          <!-- 评论区标题：不加左侧装饰竖线（简洁分区；评论内容本身无竖线装饰） -->
          <SectionTitle :title="`评论 (${moment.commentCount})`" :bar="false" />
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
              @report="onCommentReport"
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
            <IconSvg name="edit" :size="26" color="var(--color-on-primary)" />
            <text class="reject-edit-text">编辑重提</text>
          </view>
        </view>
      </template>

      <view style="height: var(--spacing-lg)" />
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
            confirm-type="send"
            aria-label="评论输入框"
            @confirm="submitComment"
            @blur="onCommentBlur"
          />
        </view>
        <view class="comment-send" :class="{ disabled: commentSubmitting }" role="button" aria-label="发送评论" @tap="submitComment">
          <IconSvg name="send" :size="32" color="var(--color-on-primary)" class="comment-send-text" :class="{ spin: commentSubmitting }" />
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
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'
import * as momentApi from '@/api/moment'
import { submitFeedback } from '@/api/feedback'
import { formatDateTime } from '@/utils/time'
import { getImageUrl } from '@/utils/image'
import type { Moment, MomentComment } from '@/types/moment'
import { buildSharePayload } from '@/utils/share-state'
import { backToHome } from '@/utils/nav'
import Header from '@/components/AppHeader.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconSvg from '@/components/IconSvg.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import MomentImageGrid from '@/components/MomentImageGrid.vue'
import InteractBar from '@/components/InteractBar.vue'
import CommentItem from '@/components/CommentItem.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import ReportModal from '@/components/ReportModal.vue'
import AuthSheet from '@/components/AuthSheet.vue'

const theme = useThemeStore()
const userStore = useUserStore()
const moment = ref<Moment | null>(null)
onShareAppMessage(() => buildSharePayload(undefined, moment.value))
function openDishDetail(id: number) {
  if (!id) return
  uni.navigateTo({ url: `/pages/detail/dish?id=${id}` })
}
const comments = ref<MomentComment[]>([])
const loading = ref(false)
const deleted = ref(false)
const refresherTriggered = ref(false)
const commentText = ref('')
/** 评论图片（最多 3 张，复用 Moment 图床） */
const commentImages = ref<string[]>([])
/** 评论输入自动聚焦（点 InteractBar「评论」/ 楼中楼回复时触发，2026-08-03 打磨） */
const commentFocus = ref(false)
const commentExpanded = ref(false)
/** @提及弹层状态（手动输入 @ 唤起选人，微信式回复，2026-08-16） */
const mentionOpen = ref(false)
const mentionQuery = ref('')
const atPos = ref(-1)
const collapseThreshold = 5

/** 楼中楼「共 N 条」展开/收起 */
const visibleComments = computed(() => {
  if (commentExpanded.value || comments.value.length <= collapseThreshold) return comments.value
  return comments.value.slice(0, collapseThreshold)
})

let currentId = 0

/** 页面级定时器注册表：onUnload 统一清理，避免页面销毁后 setTimeout 仍触发（P0 防多退一层/越界访问） */
let pageTimers: ReturnType<typeof setTimeout>[] = []
onUnload(() => {
  pageTimers.forEach((t) => clearTimeout(t))
  pageTimers = []
})

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

/** 加载序号：onLoad/onRefresh 快速连续触发时，旧请求结果不得覆盖新请求（竞态守卫） */
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
    // 接口返回空：动态已删除 / 审核下架 / 不存在
    if (!m) {
      deleted.value = true
      moment.value = null
      return
    }
    moment.value = m
    comments.value = c.list
    // 以服务端权威值回写评论数，避免乐观插入（commentCount += 1）与刷新覆盖后不一致；
    // 评论超 50 条时本地插入项已在刷新中丢失，此处用服务端真实总数校正展示
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
  // 档口详情页已下线（2026-08-09）：相关档口不再展示跳转入口
}

function goEdit() {
  if (!moment.value) return
  uni.navigateTo({ url: `/pages/user/publish-content/index?id=${moment.value.id}` })
}

/** scroll-into-view 目标：点「评论」定位到评论卡（scroll-view 内滚动，uni.pageScrollTo 对 scroll-view 无效） */
const commentIntoView = ref('')
function focusComment() {
  // 先清空再延迟设置目标值，保证重复点击每次都能触发 scroll-into-view 定位
  commentIntoView.value = ''
  const t = setTimeout(() => {
    commentIntoView.value = 'comment-section'
    commentFocus.value = true
  }, 30)
  pageTimers.push(t)
}

/** 动态「有用」乐观更新：与 MomentCard / CommentItem 同模式（后端计数已含当前用户），失败回滚。
 *  pendingUseful 锁防连点（P0 防重复请求 / 计数漂移）。 */
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

/** 举报（动态 or 评论，共享 ReportModal，2026-08-16 扩展评论举报） */
const reportOpen = ref(false)
const reportSubmitting = ref(false)
/** 评论提交中：防连点 */
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

/** 点击「回复 @昵称」：等价于在输入框写入 @昵称 （微信式提及回复，2026-08-16） */
function replyTo(c: MomentComment) {
  commentText.value = '@' + c.userNickname + ' '
  commentFocus.value = true
  mentionOpen.value = false
}

/** 点击「回复 @昵称」直接以该昵称为回复目标 */
function replyToNamed(nickname: string) {
  const target = comments.value.find(c => c.userNickname === nickname)
  if (target) replyTo(target)
}

/** 候选评论者昵称（动态作者 + 已有评论者，去重） */
const mentionList = computed(() => {
  const set = new Set<string>()
  if (moment.value?.userNickname) set.add(moment.value.userNickname)
  comments.value.forEach(c => { if (c.userNickname) set.add(c.userNickname) })
  return [...set]
})

/** 按已输入的 @ 查询词过滤 */
const filteredMentions = computed(() => {
  const q = mentionQuery.value.trim().toLowerCase()
  if (!q) return mentionList.value
  return mentionList.value.filter(n => n.toLowerCase().includes(q))
})

/** 监听输入框：检测手动输入 @ 唤起选人；@ 被删则关闭弹层 */
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

/** 选中提及：在 @ 位置插入 @昵称 ，并定位回复目标 */
function selectMention(name: string) {
  const before = commentText.value.slice(0, atPos.value + 1) // 含 @
  const after = commentText.value.slice(atPos.value + 1 + mentionQuery.value.length)
  commentText.value = before + name + ' ' + after
  mentionOpen.value = false
  commentFocus.value = true
}

/** 输入框失焦：收起提及弹层（延迟以允许点击选项先触发） */
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
  // 以开头的 @昵称 识别回复目标（微信式），@提及本身保留在内容中
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
    // 本地插入评论（避免整页 loadData 重拉，评论列表为扁平按时间升序，新评论追加末尾）
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
          // 本地过滤删除（避免整页 loadData 重拉）
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
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom)); }
/* 骨架屏（贴合真实首屏：主卡 + 评论卡；子元素挂全局 .skeleton 闪烁类） */
.detail-skeleton { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.sk-card { padding: var(--spacing-md); border-radius: var(--radius-modal); background: var(--bg-card); box-shadow: var(--shadow-card); display: flex; flex-direction: column; gap: var(--spacing-sm); }
.sk-head { display: flex; align-items: center; gap: var(--spacing-sm); }
.sk-avatar { width: 72rpx; height: 72rpx; border-radius: 16rpx; flex-shrink: 0; }
.sk-lines { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.sk-line { height: 28rpx; border-radius: var(--radius-tag); }
.w-90 { width: 90%; }
.w-85 { width: 85%; }
.w-75 { width: 75%; }
.w-55 { width: 55%; }
.w-40 { width: 40%; }
.w-30 { width: 30%; }
.w-25 { width: 25%; }
.sk-grid { display: flex; gap: var(--spacing-xs); margin-top: var(--spacing-xs); }
.sk-img { width: 220rpx; height: 220rpx; border-radius: var(--radius-tag); }
.sk-actions { display: flex; gap: var(--spacing-md); margin-top: var(--spacing-xs); }
.sk-action { width: 120rpx; height: 56rpx; border-radius: var(--radius-tag); }
.sk-comment { display: flex; align-items: center; gap: var(--spacing-sm); }
.sk-comment .sk-avatar { width: 60rpx; height: 60rpx; }
/* 动态主卡（合并卡）：发布者 + 正文 + 九宫格 + 关联对象 + 点赞评论举报 + 用户评价 全部一张卡；评论区单独一张卡 */
.m-card { margin: var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card); }
.m-head { display: flex; align-items: center; gap: var(--spacing-sm); }
.m-avatar { width: 72rpx; height: 72rpx; border-radius: 16rpx; background: var(--bg-page); flex-shrink: 0; }
.m-avatar-empty { display: flex; align-items: center; justify-content: center; }
.m-head-right { flex: 1; min-width: 0; display: flex; flex-direction: column; }
/* 作者行：详情页字号略大于列表卡（caption 30rpx），字重与列表统一 bold，突出作者层级 */
.m-nickname { font-size: var(--font-caption); font-weight: var(--weight-bold); color: var(--text-primary); letter-spacing: var(--tracking-h3); }
.m-time { font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); font-variant-numeric: tabular-nums; }
.m-audit { padding: 4rpx 12rpx; border-radius: var(--radius-tag); }
.m-audit-text { font-size: 20rpx; font-weight: var(--weight-bold); }
.audit-pending { background: var(--color-warning-soft); }
.audit-pending .m-audit-text { color: var(--color-warning); }
.audit-rejected { background: var(--color-error-soft); }
.audit-rejected .m-audit-text { color: var(--color-error); }
.m-content { display: block; margin-top: var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; word-break: break-word; }
.m-images { margin-top: var(--spacing-sm); }
.related-card { display: flex; align-items: center; gap: var(--spacing-sm); margin: var(--spacing-md) 0 0; padding: var(--spacing-sm) var(--spacing-xs) var(--spacing-md); background: transparent; border-radius: 0; box-shadow: none; border-bottom: 2rpx solid var(--border-color); transition: transform var(--duration-fast) var(--ease-out); -webkit-tap-highlight-color: transparent; }
.related-card:active { transform: scale(var(--press-scale)); }
/* 互动栏顶部留白在 InteractBar 组件内实现（mp-weixin 样式隔离，:deep 不生效） */
.related-icon { font-size: 32rpx; line-height: 1; }
/* 圆角正方形菜品缩略图（与动态卡片 m-related-thumb 统一） */
.related-thumb { width: 64rpx; height: 64rpx; border-radius: 14rpx; background: var(--bg-page); flex-shrink: 0; }
.related-thumb--empty { display: flex; align-items: center; justify-content: center; }
.related-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.related-type { font-size: var(--font-aux); color: var(--text-tertiary); }
.related-name-row { display: flex; align-items: center; gap: var(--spacing-sm); min-width: 0; }
.related-name { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 关联菜品星级：与动态卡片一致（--color-star 金黄） */
.related-rating { display: inline-flex; align-items: center; gap: 2rpx; flex-shrink: 0; }
.related-rating-num { font-size: var(--font-aux); color: var(--text-tertiary); margin-left: var(--spacing-xs); font-variant-numeric: tabular-nums; }
.related-arrow { font-size: 28rpx; color: var(--text-tertiary); }
.reject-box { margin: 0 var(--spacing-md); padding: var(--spacing-md); background: var(--color-error-soft); border-radius: var(--radius-card); }
.reject-title { display: block; font-size: var(--font-body); font-weight: var(--weight-bold); color: var(--color-error); margin-bottom: var(--spacing-xs); }
.reject-reason { display: block; font-size: var(--font-body); color: var(--color-error); line-height: 1.5; }
.reject-edit { margin-top: var(--spacing-sm); display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); background: var(--color-primary); border-radius: var(--radius-tag); }
.reject-edit-text { font-size: var(--font-aux); color: var(--color-on-primary); font-weight: var(--weight-semibold); }
/* 评论卡（Apple Design Typography：大字负 tracking + 设计系统 card 规范）。
   与菜品详情评价区、档口详情评价 tab 的 comment-section 完全同款（升级版：圆角 24px、标题 34rpx weight 800、阴影更深更柔）。
   分区标题统一 SectionTitle（§4.9 红线） */
.comment-section { margin: 0 var(--spacing-md) var(--spacing-md); padding: var(--spacing-md) var(--spacing-md) var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card-soft); }
.comment-list { display: flex; flex-direction: column; }
.comment-expand { padding: var(--spacing-sm) 0; text-align: center; }
.comment-expand-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); }
.comment-bar { position: fixed; left: 0; right: 0; bottom: 0; display: flex; flex-direction: column; padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); z-index: 50; }
.comment-input-row { display: flex; align-items: center; gap: var(--spacing-sm); }
.comment-uploader { flex-shrink: 0; }
.comment-input-box { flex: 1; display: flex; align-items: center; min-width: 0; height: 72rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: 0 var(--spacing-md); border: 2rpx solid transparent; transition: border-color var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out); }
/* 聚焦态：仅强调主色边框，背景保持 --bg-soft 灰底不变白。
   原实现把背景换成 --bg-card（白）——输入 @ 触发聚焦后输入框瞬间变白，且与上方 mention-pop 白卡片、
   底部栏白底融为一体，视觉层次丢失（Apple 输入框聚焦保持背景一致） */
.comment-input-box.focused { border-color: var(--color-primary); }
.comment-input { flex: 1; min-width: 0; height: 72rpx; background: transparent; padding: 0; font-size: 32rpx; color: var(--text-primary); }
.comment-send { width: 88rpx; height: 72rpx; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: var(--color-primary); border-radius: var(--radius-btn); transition: opacity var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-out), background var(--duration-fast) var(--ease-out); }
.comment-send:active { opacity: 0.8; transform: scale(var(--press-scale)); }
.comment-send.disabled { opacity: 0.5; pointer-events: none; }
.comment-send-text { font-size: 32rpx; line-height: 1; color: var(--color-on-primary); }
.comment-send-text.spin { animation: comment-spin 0.8s linear infinite; }
@keyframes comment-spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.mention-pop { position: absolute; left: var(--spacing-md); right: var(--spacing-md); bottom: calc(100% + 8rpx); background: var(--bg-card); border: 2rpx solid var(--border-color); border-radius: var(--radius-card); box-shadow: var(--shadow-bar-soft); max-height: 360rpx; overflow-y: auto; padding: var(--spacing-xs) 0; z-index: 60; }
.mention-item { display: flex; align-items: center; padding: var(--spacing-sm) var(--spacing-md); }
.mention-item:active { background: var(--bg-soft); }
.mention-at { color: var(--color-primary); font-weight: var(--weight-semibold); margin-right: 4rpx; font-size: 30rpx; }
.mention-name { font-size: 30rpx; color: var(--text-primary); }
.mention-empty { padding: var(--spacing-sm) var(--spacing-md); font-size: var(--font-aux); color: var(--text-tertiary); }
.mention-enter-active, .mention-leave-active { transition: opacity var(--duration-fast) var(--ease-out), transform var(--duration-fast) var(--ease-drawer); }
.mention-enter-from, .mention-leave-to { opacity: 0; transform: translateY(12rpx) scale(0.96); }
.mention-enter-to, .mention-leave-from { opacity: 1; transform: translateY(0) scale(1); }

/* reduced-motion 降级：去位移/弹性过冲，保留透明度交叉淡入 */
@media (prefers-reduced-motion: reduce) {
  .mention-enter-active, .mention-leave-active { transition: opacity var(--duration-fast) ease; }
  .mention-enter-from, .mention-leave-to { transform: none; }
}
</style>
