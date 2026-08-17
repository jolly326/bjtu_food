<template>
  <view
    class="review-item"
    :class="{ 'review-item-pressed': pressed }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
  >
    <image v-if="avatarOk && review.userAvatar" class="review-avatar" :src="getImageUrl(review.userAvatar)" mode="aspectFill" @error="avatarOk = false" />
    <view v-else class="review-avatar review-avatar-empty">
      <IconSvg name="user" :size="32" color="var(--text-tertiary)" />
    </view>
    <view class="review-body">
      <view class="review-head">
        <text class="review-nickname">{{ review.userNickname || '匿名用户' }}</text>
        <text
          v-if="review.replyToNickname"
          class="review-reply-to"
          role="button"
          aria-label="回复对象"
          @tap.stop="replyToNamed(review.replyToNickname!)"
        >@{{ review.replyToNickname }}</text>
        <text class="review-time">{{ relativeTime(review.createTime) }}</text>
      </view>
      <!-- 星级降权：仅顶层评价（有评分）展示，回复不计分不展示 -->
      <view class="review-rating" v-if="(review.rating || 0) > 0">
        <IconSvg
          v-for="i in 5"
          :key="i"
          name="star-filled"
          :size="20"
          :color="i <= (review.rating || 0) ? 'var(--color-star)' : 'var(--border-color)'"
          class="review-star"
        />
      </view>
      <text class="review-content">{{ review.content }}</text>
      <view class="review-foot" v-if="review.images && review.images.length">
        <image
          v-for="(img, idx) in review.images.slice(0, 3)"
          :key="idx"
          class="review-thumb"
          :src="thumbSrc(img, idx)"
          mode="aspectFill"
          lazy-load
          @error="onThumbError(idx)"
          @tap="previewImage(idx)"
        />
      </view>
      <!-- footer 两栏：左时间已上移到头部，右操作组（回复 / 有用 / 举报） -->
      <view class="review-footer">
        <view class="review-ops">
          <text class="review-op" role="button" aria-label="回复评价" @tap.stop="replyTo(review)">
            <IconSvg name="comment" :size="26" color="var(--text-tertiary)" /> 回复
          </text>
          <text v-if="!hideUseful" class="review-op" :class="{ active: usefulActive }" role="button" aria-label="标记有用" @tap.stop="$emit('like', review)">
            <IconSvg
              name="thumb"
              :size="26"
              :color="usefulActive ? 'var(--color-primary)' : 'var(--text-tertiary)'"
            />
            <text v-if="likeCount > 0" class="review-op-count">{{ likeCount }}</text>
          </text>
          <text v-if="!isOwn" class="review-op review-op--report" role="button" aria-label="举报评价" @tap.stop="onReport(review)">举报</text>
          <text v-if="canDelete" class="review-op review-op--delete" role="button" aria-label="删除评价" @tap.stop="onDelete">删除</text>
        </view>
      </view>
      <!-- 楼中楼子回复：缩进轻量渲染，复用同一组件；depth 封顶 3 层防失控 -->
      <view class="review-replies" v-if="review.replies && review.replies.length && (depth ?? 0) < 3">
        <ReviewItem
          v-for="child in review.replies"
          :key="child.id"
          :review="child"
          :useful-active="child.useful"
          :hide-useful="hideUseful"
          :current-user-id="currentUserId"
          :depth="(depth ?? 0) + 1"
          @like="$emit('like', $event)"
          @reply="$emit('reply', $event)"
          @reply-named="$emit('reply-named', $event)"
          @report="$emit('report', $event)"
          @delete="$emit('delete', $event)"
        />
        <text
          v-if="review.repliesHasMore && !repliesLoading"
          class="review-replies-more"
          role="button"
          :aria-label="repliesExpanded ? '加载更多回复' : '查看全部回复'"
          @tap.stop="onLoadMoreReplies"
        >{{ repliesExpanded ? '加载更多' : `查看全部 ${(review.replies || []).length}+ 条回复` }}</text>
        <text v-else-if="repliesLoading" class="review-replies-more loading">加载中…</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { getImageUrl, getThumbUrl, previewImages } from '@/utils/image'
import { relativeTime } from '@/utils/time'
import { getParentReplies } from '@/api/review'
import type { Review } from '@/types/review'
// 递归自引用：uni-app 跨端（尤其微信小程序）必须显式 import 自身，
// 否则编译期无法解析模板中的 <ReviewItem>，仅 defineOptions name 不足。
import ReviewItem from '@/components/ReviewItem.vue'

// 递归组件需 name（H5 端递归解析用）
defineOptions({ name: 'ReviewItem' })

const props = defineProps<{
  review: Review
  /** 当前用户是否已点赞（控制填充态） */
  usefulActive?: boolean
  /** 隐藏点赞（喜欢）操作：档口详情页评价区按产品决策不设点赞 */
  hideUseful?: boolean
  /** 当前登录用户 ID：用于判定本人评价（本人可删、隐藏举报），任意层级均生效 */
  currentUserId?: number
  /** 嵌套层级（楼中楼缩进控制，顶层=0） */
  depth?: number
}>()

const emit = defineEmits<{
  (e: 'like', review: Review): void
  (e: 'reply', review: Review): void
  (e: 'reply-named', nickname: string): void
  (e: 'report', review: Review): void
  (e: 'delete', review: Review): void
}>()

const pressed = ref(false)
const avatarOk = ref(true)

// 有用计数直接用后端 usefulCount（语义已含当前用户：toggleUseful 切换 ±1 均反映在计数中），
// 若再加 usefulActive 会重复 +1。usefulActive 仅控制填充态显示。
const likeCount = computed(() => props.review.usefulCount || 0)

// 本人评价（任意层级，子回复也可删）：当前登录用户 ID 命中即本人
const isOwn = computed(() => props.currentUserId != null && props.review.userId === props.currentUserId)
// 本人评价可删除（子回复开放删除入口）
const canDelete = computed(() => isOwn.value)

function onDelete() {
  if (!canDelete.value) return
  emit('delete', props.review)
}
function replyTo(r: Review) { emit('reply', r) }
/** 点 @昵称 直接开启回复弹层并指向该评价（含子回复，parentId=当前 review.id），无需先点「回复」 */
function replyToNamed(nickname: string) { emit('reply', props.review) }
function onReport(r: Review) { emit('report', r) }

function previewImage(idx: number) {
  previewImages(props.review.images, idx)
}

/* ===== 评价图缩略图（#5）：优先缩略图减流量，加载失败回退原图 ===== */
const thumbFailed = ref<boolean[]>([])
function thumbSrc(img: string, idx: number): string {
  // 该图缩略图加载失败过 → 回退原图
  if (thumbFailed.value[idx]) return getImageUrl(img)
  return getImageUrl(getThumbUrl(img))
}
function onThumbError(idx: number) {
  if (!thumbFailed.value[idx]) {
    thumbFailed.value[idx] = true
  }
}

/* ===== 楼中楼「查看全部回复」展开（#4）：分页拉取后续 push 进 review.replies ===== */
const REPLY_PAGE_SIZE = 20
const repliesPage = ref(1)
const repliesLoading = ref(false)
/** 是否已点过「查看全部」（控制按钮文案：查看全部 / 加载更多） */
const repliesExpanded = ref(false)

async function onLoadMoreReplies() {
  if (repliesLoading.value || !props.review.id) return
  repliesLoading.value = true
  try {
    const { list } = await getParentReplies(props.review.id, {
      page: repliesPage.value,
      pageSize: REPLY_PAGE_SIZE,
    })
    // 与已有 replies 按 id 去重合并（首屏窗口 5 条可能被重复返回）
    const existing = new Set((props.review.replies || []).map((r) => r.id))
    const fresh = list.filter((r) => !existing.has(r.id))
    if (!props.review.replies) props.review.replies = []
    props.review.replies.push(...fresh)
    // 返回条数不足一页说明已到底，隐藏按钮
    props.review.repliesHasMore = list.length >= REPLY_PAGE_SIZE
    repliesPage.value += 1
    repliesExpanded.value = true
  } finally {
    repliesLoading.value = false
  }
}
</script>

<style scoped>
/* ===== 评价项（对标动态评论卡片 CommentItem + Apple HIG 克制风格）。
   三处评论区共用（动态详情 / 菜品详情 / 档口详情），打磨一处即统一全部。
   设计要点：hairline 分隔、touch 物理反馈、层级对比（昵称黑/正文黑/时间灰/操作灰）、星级降权、楼中楼缩进 */
.review-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) 0;
  border-bottom: 1rpx solid var(--border-color);
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
  transition: opacity 120ms var(--ease-out);
}
.review-item:last-child { border-bottom: none; }
/* 轻反馈：整卡由 scale 改为 opacity，避免整块（含楼中楼）塌陷感。
   类名用 review-item-pressed 而非 pressed，避免与 App.vue 全局 .pressed（scale !important）同名冲突。 */
.review-item.review-item-pressed { opacity: 0.6; }

/* 头像：与 CommentItem 对齐，统一 60rpx 圆角正方形 */
.review-avatar {
  width: 60rpx;
  height: 60rpx;
  border-radius: 16rpx;
  background: var(--bg-page);
  flex-shrink: 0;
}
.review-avatar-empty { display: flex; align-items: center; justify-content: center; }

/* 右侧内容 */
.review-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

/* 头部：昵称 + @被回复人 + 时间（时间置右灰字） */
.review-head {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: var(--spacing-xs);
}
.review-nickname {
  font-size: var(--font-card);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h3);
  flex-shrink: 0;
}
.review-reply-to {
  font-size: var(--font-aux);
  font-weight: var(--weight-semibold);
  color: var(--color-primary);
  transition: opacity 120ms ease;
  -webkit-tap-highlight-color: transparent;
}
.review-reply-to:active { opacity: 0.6; }
.review-time {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  margin-left: auto;
  flex-shrink: 0;
}

/* 星级降权：小星、与头部拉开，仅顶层评价展示 */
.review-rating { display: inline-flex; align-items: center; gap: 2rpx; }
.review-star { display: inline-block; }

/* 正文：转主色（提升阅读重心），与昵称拉开层级 */
.review-content {
  font-size: var(--font-body);
  color: var(--text-primary);
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

/* 缩略图 */
.review-foot { display: flex; gap: var(--spacing-xs); margin-top: var(--spacing-xs); }
.review-thumb {
  width: 144rpx;
  height: 144rpx;
  border-radius: var(--radius-card);
  background: var(--bg-page);
  transition: transform 120ms var(--ease-out);
}
.review-thumb:active { transform: scale(var(--press-scale)); }

/* footer 两栏：左留空（时间上移头部）/ 右操作组（回复·有用·举报，纯文字链无背景） */
.review-footer { margin-top: var(--spacing-xs); }
.review-ops { display: inline-flex; align-items: center; gap: var(--spacing-lg); }
.review-op {
  display: inline-flex;
  align-items: center;
  gap: 4rpx;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  font-weight: var(--weight-medium);
  padding: var(--spacing-2xs) var(--spacing-xs);
  border-radius: var(--radius-card);
  transition: opacity 120ms ease;
  -webkit-tap-highlight-color: transparent;
}
.review-op:active { opacity: 0.6; }
.review-op.active { color: var(--color-primary); }
.review-op--report { color: var(--text-tertiary); }
.review-op--delete { color: var(--color-error); }

/* 楼中楼子回复：左侧细线缩进，轻量层级 */
.review-replies {
  margin-top: var(--spacing-sm);
  padding-left: var(--spacing-md);
  border-left: 2rpx solid var(--border-color);
  display: flex;
  flex-direction: column;
}
/* 子回复「查看全部/加载更多」：可点击主色链接，loading 态降为灰字 */
.review-replies-more {
  align-self: flex-start;
  margin-top: var(--spacing-xs);
  padding: var(--spacing-2xs) var(--spacing-xs);
  font-size: var(--font-aux);
  font-weight: var(--weight-semibold);
  color: var(--color-primary);
  -webkit-tap-highlight-color: transparent;
  transition: opacity 120ms var(--ease-out);
}
.review-replies-more:active { opacity: 0.6; }
.review-replies-more.loading { color: var(--text-tertiary); font-weight: var(--weight-medium); }
</style>