<template>
  <view
    class="comment-item"
    :class="{ 'is-reply': reply }"
    @tap="onItemTap"
    @longpress="onLongPress"
  >
    <image v-if="avatarOk && comment.userAvatar" class="c-avatar" :src="getImageUrl(comment.userAvatar)" mode="aspectFill" @error="avatarOk = false" />
    <view v-else class="c-avatar c-avatar-empty">
      <IconSvg name="user" :size="30" color="var(--text-tertiary)" />
    </view>
    <view class="c-body">
      <!-- 两行列（moment-comment-layout-unify）：第 1 行昵称+附属标签；第 2 行独立浅灰时间 -->
      <view class="c-head">
        <view class="c-name-row">
          <text class="c-nickname">{{ comment.userNickname }}</text>
          <!-- 楼主标识：动态作者自己的主评论/回复昵称旁浅灰小标签（弱视觉权重，不抢正文注意力） -->
          <text v-if="isOwner" class="c-owner">楼主</text>
          <text
            v-if="comment.replyToNickname"
            class="c-reply"
            @tap.stop="replyToNamed(comment.replyToNickname!)"
          >@{{ comment.replyToNickname }}</text>
        </view>
        <text class="c-time">{{ formatDateTime(comment.createdAt) }}</text>
      </view>
      <text class="c-content">{{ comment.content }}</text>
      <!-- 评论图片（紧凑内联网格，detail-modular-review-cleanup：MomentImageGrid 不再独立文件） -->
      <view v-if="comment.images && comment.images.length" class="c-images m-images compact">
        <view
          v-for="(img, idx) in comment.images"
          :key="idx"
          class="m-image-wrap"
          @tap.stop="previewImage(idx)"
        >
          <image
            class="m-image"
            :class="{ loaded: loadedSet.has(idx) }"
            :src="getImageUrl(getThumbUrl(img))"
            mode="aspectFill"
            lazy-load
            @load="loadedSet.add(idx)"
          />
        </view>
      </view>
      <!-- 底部：仅「有用」操作行，UI 与 MomentDetailCard 互动区点赞统一（icon 36rpx、激活主色、计数 caption/500）；
           回复入口=点击评论本体 / 长按面板「回复」；footer stop：空白区点击不冒泡成回复 -->
      <view class="c-footer" @tap.stop>
        <view
          class="c-useful"
          :class="{ active: !!comment.useful }"
          role="button"
          :aria-label="comment.useful ? '取消有用' : '标记有用'"
          @tap="onUseful(comment)"
        >
          <IconSvg
            :name="comment.useful ? 'thumb-filled' : 'thumb'"
            :size="36"
            class="c-useful-icon"
            :color="comment.useful ? 'var(--color-primary)' : 'var(--text-secondary)'"
          />
          <text class="c-useful-count">{{ comment.usefulCount || 0 }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { formatDateTime } from '@/utils/time'
import { getImageUrl, getThumbUrl, previewImages } from '@/utils/image'
import { useUserStore } from '@/stores/user'
import type { MomentComment } from '@/types/moment'

const props = withDefaults(defineProps<{
  comment: MomentComment
  momentId: number
  /** 动态作者ID（= moment.userId）：昵称旁「楼主」标签判定；无（游客态数据缺失）不显示 */
  authorId?: number | null
  /** 是否为回复条目（楼中楼缩进） */
  reply?: boolean
}>(), {
  authorId: null,
  reply: false,
})

const emit = defineEmits<{
  (e: 'reply', comment: MomentComment): void
  (e: 'reply-named', nickname: string): void
  (e: 'delete', comment: MomentComment): void
  /** 长按非作者评论 → 父级呼出动作面板（interaction-polish：面板含回复+举报） */
  (e: 'report', comment: MomentComment): void
  (e: 'useful', comment: MomentComment): void
}>()

const userStore = useUserStore()
const avatarOk = ref(true)

/** 楼主：评论作者 = 动态作者 */
const isOwner = computed(() => props.authorId != null && props.comment.userId === props.authorId)

/** 图片淡入记录（B.5） */
const loadedSet = reactive(new Set<number>())
function previewImage(idx: number) {
  previewImages(props.comment.images || [], idx)
}

function replyTo(c: MomentComment) { emit('reply', c) }
function replyToNamed(nickname: string) { emit('reply-named', nickname) }
function onUseful(c: MomentComment) { emit('useful', c) }

/** 点击评论本体 → 回复（moment-detail-interaction-polish 2.2；图片/@昵称/底部操作行均已 @tap.stop，不冒泡至此） */
function onItemTap() {
  replyTo(props.comment)
}

/** 长按：作者本人维持删除；非作者（含游客）长按 → 动作面板（moment-detail-action-deemphasis） */
function onLongPress() {
  const me = userStore.userInfo
  if (me && props.comment.userId === me.id) {
    emit('delete', props.comment)
  } else {
    emit('report', props.comment)
  }
}
</script>

<style scoped>
/* moment-list-detail-polish：单条纵向间距收紧、文字档位统一（头像 32px=64rpx、昵称 14px=body、正文 1.45）。
   moment-comment-thread-view：去条目硬分割线，改统一纵向间距（间距驱动），卡片干净通透。
   moment-detail-interaction-polish：纵向节奏再紧凑（12rpx/边）；点击/长按均无按压底色反馈（无 hover-class）。 */
/* fix：flex-start（而非整条居中）——头像只对齐「昵称/时间两行头部」，
   不随正文/图片/操作行变长而垂直漂移（与 MomentDetailCard 头部同关系：头像覆盖两行） */
.comment-item { display: flex; align-items: flex-start; gap: var(--spacing-sm); padding: 12rpx 0; -webkit-tap-highlight-color: transparent; }
/* 楼中楼：整体右缩进约一个头像宽；不使用任何层级竖线（moment-comment-layout-unify：去竖线） */
.comment-item.is-reply { padding-left: 72rpx; }
.c-avatar { width: 64rpx; height: 64rpx; border-radius: var(--radius-circle); background: var(--bg-soft); flex-shrink: 0; }
.c-avatar-empty { display: flex; align-items: center; justify-content: center; }
.c-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
/* 两行列头部：第 1 行昵称+楼主/@标签，第 2 行独立浅灰时间；头像在根 flex-start 下贴齐头部两行、
   纵向覆盖昵称/时间（与 MomentDetailCard 头部同关系，不随正文/图片/操作行整条居中漂移） */
.c-head { display: flex; flex-direction: column; row-gap: 2rpx; min-width: 0; }
.c-name-row { display: flex; align-items: center; flex-wrap: nowrap; column-gap: var(--spacing-xs); min-width: 0; }
.c-nickname { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 楼主标签：浅灰底 + 深灰字、小字号弱视觉权重，不抢正文注意力（moment-comment-thread-view 4.2） */
.c-owner { flex-shrink: 0; font-size: var(--font-aux); color: var(--text-secondary); background: var(--bg-soft); padding: 2rpx 12rpx; border-radius: var(--radius-pill); align-self: center; line-height: 1.4; }
.c-reply { flex-shrink: 0; font-size: var(--font-aux); color: var(--color-primary); transition: opacity var(--duration-fast); -webkit-tap-highlight-color: transparent; }
.c-reply:active { opacity: 0.6; }
.c-content { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.45; margin-top: 2rpx; }
.c-footer { display: flex; align-items: center; justify-content: flex-end; padding-right: var(--spacing-sm); margin-top: 2rpx; }
/* 发布时间：独立第二行、小号三级浅灰（moment-comment-layout-unify：不与昵称同行） */
.c-time { font-size: var(--font-small); color: var(--text-tertiary); }
/* 「有用」按钮 UI 与 MomentDetailCard 互动区点赞统一（fix）：icon 36rpx、icon-数字间距 12rpx、
   触控高 64rpx；未标记线性图标+二级灰，激活主色（--color-primary）图标与计数同步；计数 caption/500 */
.c-useful { display: inline-flex; align-items: center; justify-content: center; gap: 12rpx; height: 64rpx; padding: 0 var(--spacing-sm); border-radius: var(--radius-tag); box-sizing: border-box; transition: opacity var(--duration-fast) var(--ease-out); -webkit-tap-highlight-color: transparent; }
.c-useful:active { opacity: 0.8; }
.c-useful-icon { font-size: var(--font-caption); line-height: 1; color: var(--text-secondary); }
.c-useful.active .c-useful-icon { color: var(--color-primary); }
.c-useful-count { font-size: var(--font-caption); font-weight: var(--weight-medium); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
.c-useful.active .c-useful-count { color: var(--color-primary); }
.c-images { margin-top: var(--spacing-xs); }
/* 评论紧凑图网格（原 MomentImageGrid compact 样式内联） */
.m-images { display: flex; flex-wrap: wrap; gap: 8rpx; background: transparent; }
.m-images.compact .m-image-wrap { width: 132rpx; height: 132rpx; }
.m-image-wrap { width: 132rpx; height: 132rpx; border-radius: var(--radius-tag); overflow: hidden; background: var(--bg-page); flex-shrink: 0; -webkit-tap-highlight-color: transparent; }
.m-image { width: 100%; height: 100%; opacity: 0; transition: opacity var(--duration-slow) var(--ease-out); }
.m-image.loaded { opacity: 1; }
</style>
