<template>
  <!-- 评论单独卡片（id 供「评论」按钮 scroll-into-view 定位） -->
  <view id="comment-section" class="comment-section">
    <!-- 评论标题（interaction-polish 增量：计数浅灰小字弱化、标题与首条间距收紧） -->
    <view class="comment-head">
      <text class="comment-head-text">评论</text>
      <text v-if="commentCount > 0" class="comment-head-count">({{ commentCount }})</text>
    </view>
    <view v-if="comments.length === 0" class="comment-empty">
      <text class="comment-empty-text">还没有评论，快来抢沙发吧</text>
    </view>
    <view v-else class="comment-list">
      <!-- 组渲染（moment-comment-layout-unify）：父评论与其楼中楼回复同一组；
           细浅灰横线只出现在两个独立一级评论组之间（.comment-group + .comment-group border-top），
           组内无线、首条评论上方无线 -->
      <view v-for="g in groups" :key="g.main.id" class="comment-group">
        <CommentItem
          :comment="g.main"
          :moment-id="momentId"
          :author-id="authorId"
          @reply="emit('reply', $event)"
          @reply-named="emit('reply-named', $event)"
          @delete="emit('delete', $event)"
          @report="emit('report', $event)"
          @useful="emit('useful', $event)"
        />
        <CommentItem
          v-for="reply in g.replies"
          :key="reply.id"
          :comment="reply"
          :moment-id="momentId"
          :author-id="authorId"
          :reply="true"
          @reply="emit('reply', $event)"
          @reply-named="emit('reply-named', $event)"
          @delete="emit('delete', $event)"
          @report="emit('report', $event)"
          @useful="emit('useful', $event)"
        />
      </view>
      <view v-if="topComments.length > collapseThreshold" class="comment-expand" @tap="commentExpanded = !commentExpanded">
        <text class="comment-expand-text">{{ commentExpanded ? '收起' : `共 ${comments.length} 条，点击展开` }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import CommentItem from './CommentItem.vue'
import type { MomentComment } from '@/types/moment'

const props = defineProps<{
  comments: MomentComment[]
  commentCount: number
  momentId: number
  /** 动态作者ID（moment.userId）：透传给 CommentItem 做「楼主」标签 */
  authorId?: number | null
}>()

const emit = defineEmits<{
  (e: 'reply', comment: MomentComment): void
  (e: 'reply-named', nickname: string): void
  (e: 'delete', comment: MomentComment): void
  (e: 'report', comment: MomentComment): void
  (e: 'useful', comment: MomentComment): void
}>()

const commentExpanded = ref(false)
const collapseThreshold = 5

/** 主评论（parentId 为空） */
const topComments = computed(() => props.comments.filter((c) => c.parentId == null))

/** 回复按父评论分组（同一层回复按后端 created_at asc 已有序） */
const replyMap = computed(() => {
  const map = new Map<number, MomentComment[]>()
  for (const c of props.comments) {
    if (c.parentId != null) {
      const arr = map.get(c.parentId) || []
      arr.push(c)
      map.set(c.parentId, arr)
    }
  }
  return map
})

/** 收起阈值按「主评论数」驱动（moment-comment-thread-view D1） */
const visibleTop = computed(() => {
  if (commentExpanded.value || topComments.value.length <= collapseThreshold) return topComments.value
  return topComments.value.slice(0, collapseThreshold)
})

/** 组渲染顺序：每个独立一级评论与其楼中楼回复为一组（moment-comment-layout-unify） */
interface CommentGroup { main: MomentComment; replies: MomentComment[] }
const groups = computed<CommentGroup[]>(() =>
  visibleTop.value.map((main) => ({ main, replies: replyMap.value.get(main.id) || [] })),
)
</script>

<style scoped>
/* moment-list-detail-polish：评论卡与列表/全局卡片表面统一（radius-card + shadow-card） */
.comment-section { margin: 0 var(--spacing-md) var(--spacing-md); padding: var(--spacing-md) var(--spacing-md) var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); }
/* 评论标题（增量：标题与首条评论间距收紧、计数浅灰小字弱化） */
.comment-head { display: flex; align-items: baseline; gap: var(--spacing-xs); margin-bottom: var(--spacing-xs); }
.comment-head-text { font-size: var(--font-h2); font-weight: var(--weight-heavy); color: var(--text-primary); letter-spacing: var(--tracking-h2); }
.comment-head-count { font-size: var(--font-body); color: var(--text-tertiary); }
.comment-empty { padding: var(--spacing-lg) 0; display: flex; align-items: center; justify-content: center; }
.comment-empty-text { font-size: var(--font-small); color: var(--text-tertiary); }
.comment-list { display: flex; flex-direction: column; }
/* 组间细浅灰横线：只出现在两个独立一级评论组之间；首组上方无线、组内无线 */
.comment-group + .comment-group { border-top: 2rpx solid var(--border-color); }
.comment-expand { padding: var(--spacing-sm) 0; text-align: center; }
.comment-expand-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); }
</style>
