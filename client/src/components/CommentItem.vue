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
      <!-- 底部：时间 + 仅「回复 / 举报」两个操作（评论点赞已移除，评论区不再支持点赞） -->
      <view class="c-footer">
        <text class="c-time">{{ formatDateTime(comment.createdAt) }}</text>
        <view class="c-actions">
          <text class="c-reply-btn" role="button" aria-label="回复评论" @tap.stop="replyTo(comment)">
            <IconSvg name="comment" :size="26" color="var(--text-tertiary)" /> 回复
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
import { formatDateTime } from '@/utils/time'
import { getImageUrl } from '@/utils/image'
import { useUserStore } from '@/stores/user'
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

function onLongPress() {
  if (!userStore.userInfo) return
  if (props.comment.userId !== userStore.userInfo.id) return
  emit('delete', props.comment)
}
</script>

<style scoped>
.comment-item { display: flex; gap: var(--spacing-sm); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); transition: transform var(--duration-fast) ease; -webkit-tap-highlight-color: transparent; }
.comment-item.pressed { transform: scale(var(--press-scale)); }
.comment-item:last-child { border-bottom: none; }
.c-avatar { width: 60rpx; height: 60rpx; border-radius: var(--radius-xs); background: var(--bg-page); flex-shrink: 0; }
.c-avatar-empty { display: flex; align-items: center; justify-content: center; }
.c-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.c-head { display: flex; align-items: baseline; flex-wrap: wrap; }
.c-nickname { font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-primary); }
.c-reply { font-size: var(--font-aux); color: var(--color-primary); margin-left: var(--spacing-xs); transition: opacity var(--duration-fast); -webkit-tap-highlight-color: transparent; }
.c-reply:active { opacity: 0.6; }
.c-content { font-size: var(--font-body); color: var(--text-primary); line-height: 1.5; margin-top: 4rpx; }
.c-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 6rpx; }
.c-time { font-size: var(--font-aux); color: var(--text-tertiary); }
.c-actions { display: inline-flex; align-items: center; gap: var(--spacing-md); }
.c-reply-btn { font-size: var(--font-aux); color: var(--color-primary); align-self: center; padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-card); transition: opacity var(--duration-fast); -webkit-tap-highlight-color: transparent; }
.c-reply-btn:active { opacity: 0.6; }
.c-report-btn { font-size: var(--font-aux); color: var(--text-tertiary); align-self: center; padding: var(--spacing-xs) var(--spacing-sm); border-radius: var(--radius-card); transition: opacity var(--duration-fast); -webkit-tap-highlight-color: transparent; }
.c-report-btn:active { opacity: 0.6; }
.c-images { margin-top: var(--spacing-xs); }
</style>
