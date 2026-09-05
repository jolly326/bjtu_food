<template>
  <!-- 评论单独卡片（id 供「评论」按钮 scroll-into-view 定位） -->
  <view id="comment-section" class="comment-section">
    <SectionTitle :title="`评论 (${commentCount})`" :bar="false" />
    <view v-if="comments.length === 0" class="comment-empty">
      <text class="comment-empty-text">还没有评论，来说两句</text>
    </view>
    <view v-else class="comment-list">
      <CommentItem
        v-for="c in visibleComments"
        :key="c.id"
        :comment="c"
        :moment-id="momentId"
        @reply="emit('reply', $event)"
        @reply-named="emit('reply-named', $event)"
        @delete="emit('delete', $event)"
        @report="emit('report', $event)"
      />
      <view v-if="comments.length > collapseThreshold" class="comment-expand" @tap="commentExpanded = !commentExpanded">
        <text class="comment-expand-text">{{ commentExpanded ? '收起' : `共 ${comments.length} 条，点击展开` }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import SectionTitle from '@/components/SectionTitle.vue'
import CommentItem from './CommentItem.vue'
import type { MomentComment } from '@/types/moment'

const props = defineProps<{
  comments: MomentComment[]
  commentCount: number
  momentId: number
}>()

const emit = defineEmits<{
  (e: 'reply', comment: MomentComment): void
  (e: 'reply-named', nickname: string): void
  (e: 'delete', comment: MomentComment): void
  (e: 'report', comment: MomentComment): void
}>()

const commentExpanded = ref(false)
const collapseThreshold = 5

const visibleComments = computed(() => {
  if (commentExpanded.value || props.comments.length <= collapseThreshold) return props.comments
  return props.comments.slice(0, collapseThreshold)
})
</script>

<style scoped>
/* moment-list-detail-polish：评论卡与列表/全局卡片表面统一（radius-card + shadow-card） */
.comment-section { margin: 0 var(--spacing-md) var(--spacing-md); padding: var(--spacing-md) var(--spacing-md) var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-card); box-shadow: var(--shadow-card); }
.comment-empty { padding: var(--spacing-lg) 0; display: flex; align-items: center; justify-content: center; }
.comment-empty-text { font-size: var(--font-small); color: var(--text-tertiary); }
.comment-list { display: flex; flex-direction: column; }
.comment-expand { padding: var(--spacing-sm) 0; text-align: center; }
.comment-expand-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: var(--weight-semibold); }
</style>
