<template>
  <view
    class="comment-item"
    :class="{ pressed: pressed }"
    @touchstart="pressed = true"
    @touchend="pressed = false"
    @touchcancel="pressed = false"
    @mousedown="pressed = true"
    @mouseup="pressed = false"
    @mouseleave="pressed = false"
    @longpress="onLongPress"
  >
    <image v-if="avatarOk && comment.userAvatar" class="c-avatar" :src="getImageUrl(comment.userAvatar)" mode="aspectFill" @error="avatarOk = false" />
    <view v-else class="c-avatar c-avatar-empty">
      <IconSvg name="user" :size="30" color="var(--text-tertiary)" />
    </view>
    <view class="c-body">
      <view class="c-head">
        <text class="c-nickname">{{ comment.userNickname }}</text>
        <text
          v-if="comment.replyToNickname"
          class="c-reply"
          @tap.stop="replyToNamed(comment.replyToNickname!)"
        >@{{ comment.replyToNickname }}</text>
      </view>
      <text class="c-content">{{ comment.content }}</text>
      <MomentImageGrid v-if="comment.images && comment.images.length" :images="comment.images" compact class="c-images" />
      <view class="c-footer">
        <text class="c-time">{{ relativeTime(comment.createdAt) }}</text>
        <view class="c-actions">
          <text class="c-reply-btn" role="button" aria-label="回复评论" @tap.stop="replyTo(comment)">
            <IconSvg name="comment" :size="26" color="var(--text-tertiary)" /> 回复
          </text>
          <text
            class="c-useful-count"
            :class="{ 'useful-active': usefulActive }"
            role="button"
            aria-label="标记有用"
            @tap.stop="onUseful"
          >
            <IconSvg name="thumb" :size="26" :color="usefulActive ? 'var(--color-primary)' : 'var(--text-tertiary)'" class="c-useful-icon" />
            {{ usefulCount }}
          </text>
          <text class="c-report-btn" role="button" aria-label="举报评论" @tap.stop="onReport(comment)">举报</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import MomentImageGrid from '@/components/MomentImageGrid.vue'
import { relativeTime } from '@/utils/time'
import { getImageUrl } from '@/utils/image'
import { useUserStore } from '@/stores/user'
import { toggleCommentUseful } from '@/api/moment'
import type { MomentComment } from '@/types/moment'

const props = defineProps<{
  comment: MomentComment
  momentId: number
}>()

const emit = defineEmits<{
  (e: 'reply', comment: MomentComment): void
  (e: 'reply-named', nickname: string): void
  (e: 'delete', comment: MomentComment): void
  (e: 'report', comment: MomentComment): void
}>()

const userStore = useUserStore()
const pressed = ref(false)
const avatarOk = ref(true)

function replyTo(c: MomentComment) { emit('reply', c) }
function replyToNamed(nickname: string) { emit('reply-named', nickname) }
function onReport(c: MomentComment) { emit('report', c) }

/** 评论「有用」本地乐观态（task-12.4）：初始取后端 useful，切换后以后端返回为准 */
const usefulActive = ref(!!props.comment.useful)
/** 展示计数：后端 usefulCount（语义已含当前用户，避免重复 +1） */
const usefulCount = computed(() => props.comment.usefulCount || 0)

async function onUseful() {
  if (!userStore.requireAuth(() => onUseful())) return
  const prevActive = usefulActive.value
  const prevCount = usefulCount.value
  usefulActive.value = !prevActive
  props.comment.usefulCount = prevActive ? Math.max(0, prevCount - 1) : prevCount + 1
  try {
    const res = await toggleCommentUseful(props.momentId, props.comment.id)
    usefulActive.value = res.useful
    props.comment.usefulCount = res.usefulCount
    props.comment.useful = res.useful
  } catch {
    // 回滚乐观更新
    usefulActive.value = prevActive
    props.comment.usefulCount = prevCount
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function onLongPress() {
  if (!userStore.userInfo) return
  if (props.comment.userId !== userStore.userInfo.id) return
  emit('delete', props.comment)
}
</script>

<style scoped>
.comment-item { display: flex; gap: var(--spacing-sm); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.comment-item.pressed { transform: scale(var(--press-scale)); }
.comment-item:last-child { border-bottom: none; }
.c-avatar { width: 60rpx; height: 60rpx; border-radius: 16rpx; background: var(--bg-page); flex-shrink: 0; }
.c-avatar-empty { display: flex; align-items: center; justify-content: center; }
.c-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.c-head { display: flex; align-items: baseline; flex-wrap: wrap; }
.c-nickname { font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-primary); }
.c-reply { font-size: var(--font-aux); color: var(--color-primary); margin-left: var(--spacing-xs); transition: opacity 0.12s; -webkit-tap-highlight-color: transparent; }
.c-reply:active { opacity: 0.6; }
.c-content { font-size: var(--font-body); color: var(--text-primary); line-height: 1.5; margin-top: 4rpx; }
.c-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 6rpx; }
.c-time { font-size: var(--font-aux); color: var(--text-tertiary); }
.c-actions { display: inline-flex; align-items: center; gap: var(--spacing-md); }
.c-reply-btn { font-size: var(--font-aux); color: var(--color-primary); align-self: center; padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-card); transition: opacity 0.12s; -webkit-tap-highlight-color: transparent; }
.c-reply-btn:active { opacity: 0.6; }
.c-useful-count { display: inline-flex; align-items: center; gap: 4rpx; font-size: var(--font-tiny); font-weight: var(--weight-semibold); color: var(--text-secondary); padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-card); transition: opacity 0.12s; -webkit-tap-highlight-color: transparent; }
.c-useful-count:active { opacity: 0.6; }
.c-useful-count.useful-active { color: var(--color-primary); }
.c-useful-icon { font-size: var(--font-aux); line-height: 1; color: var(--text-tertiary); }
.c-useful-count.useful-active .c-useful-icon { color: var(--color-primary); }
.c-report-btn { font-size: var(--font-aux); color: var(--text-tertiary); align-self: center; padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-card); transition: opacity 0.12s; -webkit-tap-highlight-color: transparent; }
.c-report-btn:active { opacity: 0.6; }
.c-images { margin-top: var(--spacing-xs); }
</style>
