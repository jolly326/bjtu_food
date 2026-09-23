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
    <image
      v-if="avatarOk && review.userAvatar"
      class="review-avatar"
      :src="getImageUrl(review.userAvatar)"
      mode="aspectFill"
      role="img"
      :aria-label="`${review.userNickname || '匿名用户'}的头像`"
      @error="avatarOk = false"
    />
    <view v-else class="review-avatar review-avatar-empty" role="img" :aria-label="`${review.userNickname || '匿名用户'}的头像`">
      <IconSvg name="user" :size="32" :color="COLOR_MAP['text-tertiary']" />
    </view>
    <view class="review-body">
      <view class="review-head">
        <view class="review-head-left">
          <text class="review-nickname">{{ review.userNickname || '匿名用户' }}</text>
        </view>
        <!-- 右上角竖三点：举报（他人）/ 删除（本人）收进 ActionSheet（唯一入口，常驻） -->
        <view class="review-more" role="button" aria-label="更多操作" @tap.stop="onMore">
          <IconSvg name="more-v" :size="28" :color="COLOR_MAP['text-tertiary']" />
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
            :color="COLOR_MAP['star']"
            class="review-star"
          />
          <text class="review-rating-num">{{ (review.rating || 0).toFixed(1) }}</text>
        </view>
        <text class="review-time">{{ formatDateTime(review.createdAt) }}</text>
      </view>
      <text class="review-content">{{ review.content }}</text>
      <!-- 配图行（≤3 张 COS URL）：等比小方图，点击预览大图；破图兜底 empty 中性占位 -->
      <view v-if="reviewImages.length" class="review-images">
        <view v-for="(img, i) in reviewImages" :key="img" class="review-image-cell">
          <view class="review-image-box">
            <image
              v-if="!brokenImages.has(i)"
              class="review-image"
              :src="getImageUrl(img)"
              mode="aspectFill"
              role="img"
              :aria-label="`评价配图 ${i + 1}`"
              @tap="onPreviewImage(i)"
              @error="onImageError(i)"
            />
            <view v-else class="review-image-fallback" @tap="onPreviewImage(i)">
              <IconSvg name="empty" :size="36" :color="COLOR_MAP['text-tertiary']" />
            </view>
          </view>
        </view>
      </view>
      <!-- 评价卡片不展示任何互动按钮（原「有用」按钮与计数已全链下线，2026-09-20）：
           操作仅保留右上角三点菜单（本人删除 / 他人举报）。 -->
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
// 星色须传**实色**：IconSvg 的 color 不解析 var()（走 data-uri，见 IconSvg.vue 的 resolveColor），
// 传 var(...) 会恒落 ICON_FALLBACK_COLOR（近黑）。语义键 'star' = --color-star 同源实色。
import { COLOR_MAP } from '@/theme/tokens'
import { getImageUrl } from '@/utils/image'
import { formatDateTime } from '@/utils/time'
import type { Review } from '@/types/review'

defineOptions({ name: 'ReviewItem' })

const props = defineProps<{
  review: Review
  /** 当前登录用户 ID：用于判定本人评价（本人可删、他人可举报） */
  currentUserId?: number
  /** 扁平模式：嵌套在评价卡片内时去独立卡片样式（bg/shadow/圆角），只保留条目结构 */
  flat?: boolean
}>()
// 2026-09-20（任务 6.3 零消费扫描）：原 `hideReport` / `deletable` 两个 prop 全仓零传入
// （「我的评价」页自持卡片、不再复用本组件），按 PR-05 删除；三点菜单收敛为常驻唯一入口。

const emit = defineEmits<{
  (e: 'report', review: Review): void
  (e: 'delete', review: Review): void
  (e: 'more', review: Review): void
}>()

const pressed = ref(false)
const avatarOk = ref(true)

// 本人评价：当前登录用户 ID 命中即本人（本人可删、他人可举报）
const isOwn = computed(() => props.currentUserId != null && props.review.userId === props.currentUserId)
const canDelete = computed(() => isOwn.value)

/* ===== 配图展示（≤3 张 COS URL，点击预览大图） ===== */
const reviewImages = computed(() =>
  Array.isArray(props.review.images) ? props.review.images.filter(Boolean) : [],
)
/** 破图下标集合：error 后切 empty 中性占位；images 变化（列表重拉）时重置 */
const brokenImages = ref<Set<number>>(new Set())
watch(
  () => props.review.images,
  () => { brokenImages.value = new Set() },
)
function onImageError(i: number) {
  const next = new Set(brokenImages.value)
  next.add(i)
  brokenImages.value = next
}
/** 预览大图（仅未破图可进入；current 定位到点击那张） */
function onPreviewImage(i: number) {
  if (brokenImages.value.has(i)) return
  const okIdx = reviewImages.value.map((_, idx) => idx).filter((idx) => !brokenImages.value.has(idx))
  const okUrls = okIdx.map((idx) => getImageUrl(reviewImages.value[idx]))
  if (!okUrls.length) return
  const cur = okIdx.indexOf(i)
  uni.previewImage({ urls: okUrls, current: okUrls[Math.max(cur, 0)] })
}

function onDelete() {
  if (!canDelete.value) return
  emit('delete', props.review)
}
function onReport(r: Review) { emit('report', r) }
/** 右上角三点菜单：操作由父页面以 ActionSheet 呈现（举报他人 / 删除本人） */
function onMore() {
  emit('more', props.review)
}
</script>

<style scoped>
/* ===== 评价项（口碑卡片：独立卡片 + 圆角 + 阴影）。
   当前唯一消费方 = 菜品详情评价区（DishReviewSection）；「我的评价」页自持卡片。
   口碑层扁平：不设评论/回复/点赞入口，互动仅右上角三点菜单（删除 / 举报）。
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
  border-radius: var(--radius-none);
  box-shadow: none;
  padding: var(--spacing-md) 0;
  border-bottom: 2rpx solid var(--border-color);
}
.review-item--flat:last-child { border-bottom: none; }
.review-item--flat.review-item-pressed { opacity: 0.5; }
/* 轻反馈：整卡按压 opacity 微降，避免 scale 按压的整块塌陷感（bg-soft 按压语言，spec §4.9）。
   类名用 review-item-pressed 而非 pressed，避免与 App.vue 全局 .pressed（opacity:0.7）同名冲突。 */
.review-item.review-item-pressed { opacity: 0.6; }

/* 头像：圆形浅灰底（dish-detail-visual-polish 对齐 64rpx） */
.review-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: var(--radius-circle);
  background: var(--bg-soft);
  flex-shrink: 0;
}
.review-avatar-empty { display: flex; align-items: center; justify-content: center; }

/* 右侧内容：行距 2xs，昵称与第二行不再因叠加间距拉开 */
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
  font-weight: var(--weight-semibold);
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
  font-size: var(--font-small);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}

/* 评分：黄色实星（1-5 颗，最低 1 颗）+ 右侧分值数字 */
.review-stars { display: inline-flex; align-items: center; gap: 2rpx; flex-shrink: 0; }
.review-star { display: inline-block; }
.review-rating-num { font-size: var(--font-aux); color: var(--text-secondary); margin-left: var(--spacing-xs); font-variant-numeric: tabular-nums; }

/* 右上角竖三点：绝对定位于头行右上，不参与行高（否则会撑开昵称与第二行的间距）。
   a11y：视觉 64rpx，命中区经 ::after 透明覆盖扩至 ≥88rpx（Apple 44pt 触达下限）。 */
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
.review-more::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 88rpx;
  height: 88rpx;
  transform: translate(-50%, -50%);
}
.review-more:active { opacity: 0.5; }

/* 正文：二级灰、行高 1.5（content-flow-visual-polish 5.2） */
.review-content {
  font-size: var(--font-body);
  color: var(--text-secondary);
  line-height: 1.5;
  word-break: break-word;
  white-space: pre-wrap;
}

/* 配图行（≤3 张等比小方图）：与 ImagePicker 同一网格语言（3 等分 + 16rpx gap） */
.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: var(--spacing-2xs);
}
.review-image-cell {
  width: calc((100% - 32rpx) / 3);
}
/* 正方形容器：padding-bottom 等比盒（与 ImagePicker 同法，小程序 aspect-ratio 支持不稳） */
.review-image-box {
  position: relative;
  width: 100%;
  height: 0;
  padding-bottom: 100%;
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--bg-placeholder);
  -webkit-tap-highlight-color: transparent;
}
.review-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  transition: opacity var(--duration-fast) var(--ease-out);
}
.review-image:active { opacity: 0.6; }
/* 破图兜底：empty 中性占位（浅底居中），可点击但不进预览 */
.review-image-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
