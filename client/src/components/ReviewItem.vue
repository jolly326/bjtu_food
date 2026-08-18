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
        <text class="review-time">{{ formatDateTime(review.createTime) }}</text>
      </view>
      <!-- 星级：仅顶层评价（有评分）展示，展示可信口碑 -->
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
      <!-- footer 操作组：有用（可选）/ 举报 / 删除（本人）。
           评价扁平化后不再提供楼中楼回复（讨论沉淀到动态评论区），见 project_spec 决策 -->
      <view class="review-footer">
        <view class="review-ops">
          <text v-if="!hideUseful" class="review-op" :class="{ active: usefulActive }" role="button" aria-label="标记有用" @tap.stop="onLike">
            <IconSvg
              name="thumb"
              :size="26"
              :color="usefulActive ? 'var(--color-like)' : 'var(--text-tertiary)'"
            />
            <text v-if="likeCount > 0" class="review-op-count">{{ likeCount }}</text>
          </text>
          <text v-if="!isOwn" class="review-op review-op--report" role="button" aria-label="举报评价" @tap.stop="onReport(review)">举报</text>
          <text v-if="canDelete" class="review-op review-op--delete" role="button" aria-label="删除评价" @tap.stop="onDelete">删除</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { getImageUrl, getThumbUrl, previewImages } from '@/utils/image'
import { formatDateTime } from '@/utils/time'
import { toggleUseful } from '@/api/review'
import { useUserStore } from '@/stores/user'
import type { Review } from '@/types/review'

defineOptions({ name: 'ReviewItem' })

const props = defineProps<{
  review: Review
  /** 当前用户是否已点赞（控制填充态） */
  usefulActive?: boolean
  /** 隐藏点赞（有用）操作：个人管理页按产品决策不设点赞 */
  hideUseful?: boolean
  /** 当前登录用户 ID：用于判定本人评价（本人可删、隐藏举报） */
  currentUserId?: number
  /** 显式允许删除（个人管理页独立开关，不依赖 currentUserId） */
  deletable?: boolean
}>()

const emit = defineEmits<{
  (e: 'like', review: Review): void
  (e: 'report', review: Review): void
  (e: 'delete', review: Review): void
}>()

const pressed = ref(false)
const avatarOk = ref(true)
const userStore = useUserStore()

// 有用计数直接用后端 usefulCount（语义已含当前用户：toggleUseful 切换 ±1 均反映在计数中），
// 若再加 usefulActive 会重复 +1。usefulActive 仅控制填充态显示。
const likeCount = computed(() => props.review.usefulCount || 0)
// 本地「有用」激活态（初始取后端 useful；切换后以后端返回为准）
const usefulActive = ref(!!props.review.useful)
// pending 锁防连点（P0 防重复请求 / 计数漂移）
const pendingUseful = ref(false)

/** 评价「有用」：组件内乐观更新 + 失败回滚 + 连点锁（与 CommentItem / MomentCard 同模式） */
function onLike() {
  if (props.hideUseful) return
  if (!userStore.requireAuth(() => onLike())) return
  if (pendingUseful.value) return
  pendingUseful.value = true
  const prevActive = usefulActive.value
  const prevCount = likeCount.value
  // 乐观更新
  usefulActive.value = !prevActive
  props.review.usefulCount = prevActive ? Math.max(0, prevCount - 1) : prevCount + 1
  toggleUseful(props.review.id)
    .then((res) => {
      usefulActive.value = res.useful
      props.review.usefulCount = res.usefulCount
      props.review.useful = res.useful
    })
    .catch(() => {
      // 回滚
      usefulActive.value = prevActive
      props.review.usefulCount = prevCount
      uni.showToast({ title: '操作失败', icon: 'none' })
    })
    .finally(() => {
      pendingUseful.value = false
    })
}

// 本人评价：当前登录用户 ID 命中即本人
const isOwn = computed(() => props.currentUserId != null && props.review.userId === props.currentUserId)
// 可删除：显式 deletable（个人管理页）或本人评价（详情页）
const canDelete = computed(() => !!props.deletable || isOwn.value)

function onDelete() {
  if (!canDelete.value) return
  emit('delete', props.review)
}
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
</script>

<style scoped>
/* ===== 评价项（口碑卡片，与 MomentCard 动态卡片统一视觉：独立卡片 + 圆角 + 阴影）。
   三处评价区共用（菜品详情 / 全部评价 / 我的评价），打磨一处即统一全部。
   与动态卡片的差异仅：无评论/回复入口（口碑层扁平，讨论沉淀到动态评论区）。
   设计要点：卡片层级、touch 物理反馈、层级对比（昵称黑/正文黑/时间灰/操作灰）、星级展示 */
.review-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
  transition: opacity 120ms var(--ease-out);
}
/* 轻反馈：整卡由 scale 改为 opacity，避免整块塌陷感。
   类名用 review-item-pressed 而非 pressed，避免与 App.vue 全局 .pressed（scale !important）同名冲突。 */
.review-item.review-item-pressed { opacity: 0.6; }

/* 头像：与动态卡片统一 64rpx 圆角正方形 */
.review-avatar {
  width: 64rpx;
  height: 64rpx;
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

/* 头部：昵称 + 时间（时间置右灰字） */
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
.review-time {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  margin-left: auto;
  flex-shrink: 0;
}

/* 星级：小星、与头部拉开 */
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

/* footer：操作组（有用·举报·删除，纯文字链无背景） */
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
.review-op.active { color: var(--color-like); }
.review-op--report { color: var(--text-tertiary); }
.review-op--delete { color: var(--color-error); }
</style>
