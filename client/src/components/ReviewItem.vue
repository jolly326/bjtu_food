<template>
  <view
    class="review-item"
    :class="{ 'review-item-pressed': pressed, 'review-item--flat': flat }"
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
        <view class="review-head-left">
          <text class="review-nickname">{{ review.userNickname || '匿名用户' }}</text>
        </view>
        <!-- 右上角竖三点：举报（他人）/ 删除（本人）收进 ActionSheet -->
        <view v-if="!hideReport || canDelete" class="review-more" role="button" aria-label="更多操作" @tap.stop="onMore">
          <IconSvg name="more-v" :size="36" color="var(--text-tertiary)" />
        </view>
      </view>
      <!-- 第二行：评分（1-5 黄星 + 分值数字）+ 发布时间，小间隙同行 -->
      <view class="review-meta">
        <view v-if="(review.rating || 0) > 0" class="review-stars" role="img" :aria-label="`评分 ${(review.rating || 0).toFixed(1)} 分`">
          <IconSvg
            v-for="n in Math.min(Math.max(Math.round(review.rating || 0), 1), 5)"
            :key="n"
            name="star-filled"
            :size="22"
            color="var(--color-star)"
            class="review-star"
          />
          <text class="review-rating-num">{{ (review.rating || 0).toFixed(1) }}</text>
        </view>
        <text class="review-time">{{ formatDateTime(review.createTime) }}</text>
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
      <!-- footer 操作组：仅有用（举报/删除已上移右上角） -->
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
  /** 隐藏举报（右上角）：我的评价页无需举报自己的评价 */
  hideReport?: boolean
  /** 当前登录用户 ID：用于判定本人评价（本人可删、隐藏举报） */
  currentUserId?: number
  /** 显式允许删除（个人管理页独立开关，不依赖 currentUserId） */
  deletable?: boolean
  /** 扁平模式：嵌套在评价卡片内时去独立卡片样式（bg/shadow/圆角），只保留条目结构 */
  flat?: boolean
}>()

const emit = defineEmits<{
  (e: 'like', review: Review): void
  (e: 'report', review: Review): void
  (e: 'delete', review: Review): void
  (e: 'more', review: Review): void
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
/** 右上角三点菜单：操作由父页面以 ActionSheet 呈现（举报他人 / 删除本人） */
function onMore() {
  emit('more', props.review)
}

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
  transition: opacity var(--duration-fast) var(--ease-out);
}
/* 扁平模式：嵌套在评价卡片内（菜品详情），去独立卡样式，保留条目结构 + 分隔线 */
.review-item--flat {
  background: transparent;
  border-radius: 0;
  box-shadow: none;
  padding: var(--spacing-md) 0;
  border-bottom: 2rpx solid var(--border-color);
}
.review-item--flat:last-child { border-bottom: none; }
.review-item--flat.review-item-pressed { opacity: 0.5; }
/* 轻反馈：整卡由 scale 改为 opacity，避免整块塌陷感。
   类名用 review-item-pressed 而非 pressed，避免与 App.vue 全局 .pressed（scale !important）同名冲突。 */
.review-item.review-item-pressed { opacity: 0.6; }

/* 头像：与动态卡（moment.vue m-head）统一 72rpx 圆角正方形 */
.review-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 16rpx;
  background: var(--bg-page);
  flex-shrink: 0;
}
.review-avatar-empty { display: flex; align-items: center; justify-content: center; }

/* 右侧内容：行距与动态卡 m-head-right 一致（2xs），昵称与第二行不再因叠加间距拉开 */
.review-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2xs);
}

/* 头部：昵称（左）+ 竖三点（右，绝对定位不撑高头行，保证昵称与第二行间距紧凑） */
.review-head {
  position: relative;
  display: flex;
  align-items: center;
}
/* 头部第一行：昵称，右侧预留三点按钮空间 */
.review-head-left {
  flex: 1;
  min-width: 0;
  padding-right: 64rpx;
  display: flex;
  align-items: center;
}
.review-nickname {
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--font-caption);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 第二行：评分（星星+数字）与发布时间小间隙同行（不推右）；间距由 review-body gap 提供，不叠加 margin */
.review-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}
.review-time {
  flex-shrink: 0;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

/* 评分：黄色实星（1-5 颗，最低 1 颗）+ 右侧分值数字 */
.review-stars { display: inline-flex; align-items: center; gap: 2rpx; flex-shrink: 0; }
.review-star { display: inline-block; }
.review-rating-num { font-size: var(--font-aux); color: var(--text-secondary); margin-left: var(--spacing-xs); font-variant-numeric: tabular-nums; }

/* 右上角竖三点：绝对定位于头行右上，不参与行高（否则 64rpx 会撑开昵称与第二行的间距） */
.review-more {
  position: absolute;
  top: 50%;
  right: 0;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  flex-shrink: 0;
  transition: opacity var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.review-more:active { opacity: 0.5; }

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
  transition: transform var(--duration-fast) var(--ease-out);
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
  transition: opacity var(--duration-fast) ease;
  -webkit-tap-highlight-color: transparent;
}
.review-op:active { opacity: 0.6; }
.review-op.active { color: var(--color-like); }
.review-op--report { color: var(--text-tertiary); }
.review-op--delete { color: var(--color-error); }
</style>
