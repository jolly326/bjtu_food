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
    <!-- 作者头像：**仅公开视角**（`MyReviewVO` 不含 `userAvatar`）——本人视角变体不渲染头像列 -->
    <template v-if="!mine">
      <image
        v-if="avatarOk && authorAvatar"
        class="review-avatar"
        :src="getThumbImageUrl(authorAvatar)"
        mode="aspectFill"
        lazy-load
        role="img"
        :aria-label="`${authorNickname}的头像`"
        @error="avatarOk = false"
      />
      <view v-else class="review-avatar review-avatar-empty" role="img" :aria-label="`${authorNickname}的头像`">
        <!-- 头像占位：保留人形语义（「无用户」≠「图片损坏」），底色与全站占位同源 -->
        <ImagePlaceholder name="user" :size="32" />
      </view>
    </template>
    <view class="review-body">
      <view class="review-head" :class="{ 'review-head--mine': mine }">
        <view v-if="!mine" class="review-head-left">
          <text class="review-nickname">{{ authorNickname }}</text>
        </view>
        <!-- 右上角竖三点：举报（他人）/ 删除（本人）收进 ActionSheet（唯一入口，常驻） -->
        <view class="review-more" role="button" aria-label="更多操作" @tap.stop="onMore">
          <IconSvg name="more-v" :size="28" :color="COLOR_MAP['text-tertiary']" />
        </view>
      </view>
      <!-- 第二行：评分（1-5 黄星 + 分值数字）+ 发布时间，小间隙同行 -->
      <view class="review-meta">
        <view v-if="(review.rating || 0) > 0" class="review-stars" role="img" :aria-label="`评分 ${formatRating(review.rating)} 分`">
          <IconSvg
            v-for="n in starCount"
            :key="n"
            name="star-filled"
            :size="22"
            :color="COLOR_MAP['star']"
            class="review-star"
          />
          <text class="review-rating-num">{{ formatRating(review.rating) }}</text>
        </view>
        <!-- 时间：仅到日（YYYY-MM-DD，UI 统一 Loop Round 24）—— 菜品评价时效性弱，
             第二行要同时容纳「星级 + 分值 + 时间」，去掉时分显著降噪 -->
        <text class="review-time">{{ formatDate(review.createdAt) }}</text>
      </view>
      <!-- 菜名行（可选，本人视角列表用）：辨识是哪道菜的评价 -->
      <text v-if="dishName" class="review-dish">{{ dishName }}</text>
      <text class="review-content">{{ review.content }}</text>
      <!-- 配图行（≤3 张 COS URL）：等比小方图，点击预览大图；破图兜底 empty 中性占位 -->
      <view v-if="reviewImages.length" class="review-images">
        <view v-for="(img, i) in reviewImages" :key="img" class="review-image-cell">
          <view class="review-image-box">
            <image
              v-if="!brokenImages.has(i)"
              class="review-image"
              :src="getThumbImageUrl(img)"
              mode="aspectFill"
              lazy-load
              role="img"
              :aria-label="`评价配图 ${i + 1}`"
              @tap="onPreviewImage(i)"
              @error="onImageError(i)"
            />
            <view v-else class="review-image-fallback" @tap="onPreviewImage(i)">
              <ImagePlaceholder :size="36" aria-label="图片已失效" />
            </view>
          </view>
        </view>
      </view>
      <!-- 评价卡片不展示任何互动按钮（无「有用」按钮与计数）：
           操作仅保留右上角三点菜单（本人删除 / 他人举报）。 -->
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
// 星色须传**实色**：IconSvg 的 color 不解析 var()（走 data-uri，见 IconSvg.vue 的 resolveColor），
// 传 var(...) 会恒落 ICON_FALLBACK_COLOR（近黑）。语义键 'star' = --color-star 同源实色。
import { COLOR_MAP } from '@/theme/tokens'
import { getImageUrl, getThumbImageUrl } from '@/utils/image'
import { useBrokenImages } from '@/composables/useBrokenImages'
import { formatRating } from '@/utils/dish'
import { formatDate } from '@/utils/time'
import type { Review, MyReview } from '@/types/review'
import { ANONYMOUS_AUTHOR } from '@/constants/copy'

defineOptions({ name: 'ReviewItem' })

const props = defineProps<{
  /**
   * 评价行：**两种视角两个类型**（R9）——
   * 公开视角 `Review`（`GET /dishes/{id}/reviews`，含作者标识）｜
   * 本人视角 `MyReview`（`GET /my/reviews`，含 `dishId` / `dishName`、无作者标识）。
   */
  review: Review | MyReview
  /** 扁平模式：嵌套在评价卡片内时去独立卡片样式（bg/shadow/圆角），只保留条目结构 */
  flat?: boolean
  /**
   * **本人视角变体**（「我的评价」页）：`MyReviewVO` 不含 `userId` / `userNickname` / `userAvatar`
   * ⇒ 不渲染头像与昵称（恒为本人，渲染即冗余）。
   */
  mine?: boolean
  /** 菜名行（可选，本人视角列表用）：非空时在 meta 行下展示关联菜品名 */
  dishName?: string
}>()
// `hideReport` / `deletable` 两个 prop 全仓零传入（「我的评价」页复用本组件、经 `dishName` prop 注入菜名），
// 按 PR-05 删除；三点菜单收敛为常驻唯一入口。

/* 事件**只有一个出口**：`more`（右上角竖三点）。
   原 `report` / `delete` 两个事件在组件内**从未被触发**（无任何调用点）
   —— 本人删除 / 他人举报统一由父页 `ActionSheet` 处理 ⇒ 按「零消费即删」移除。 */
const emit = defineEmits<{
  (e: 'more', review: Review | MyReview): void
}>()

const pressed = ref(false)
const avatarOk = ref(true)

/**
 * 作者标识（**仅公开视角下发**；本人视角恒为本人、零信息 ⇒ 不渲染头像 / 昵称）。
 * 用 `in` 收窄并集：公开视角行含 `userId`，本人视角行含 `dishId`。
 */
const authorAvatar = computed(() => ('userAvatar' in props.review ? props.review.userAvatar : ''))
const authorNickname = computed(() => {
  const name = 'userNickname' in props.review ? props.review.userNickname : ''
  return name || ANONYMOUS_AUTHOR
})

/**
 * 星级渲染颗数（1–5，最低 1 颗）：Round 28 —— 原为**模板内表达式**（每次渲染重算），改 `computed` 缓存。
 */
const starCount = computed(() => Math.min(Math.max(Math.round(props.review.rating || 0), 1), 5))

/* ===== 配图展示（≤3 张 COS URL，点击预览大图） ===== */
const reviewImages = computed(() =>
  Array.isArray(props.review.images) ? props.review.images.filter(Boolean) : [],
)
/** 破图下标集合：error 后切 empty 中性占位；images 变化（列表重拉）时重置 */
const { broken: brokenImages, markBroken: onImageError, clear } = useBrokenImages()
watch(
  () => props.review.images,
  () => clear(),
)
// 头像破图态同样随数据变化复位（组件实例复用、同 key 换评价时避免旧破图态残留）
watch(authorAvatar, () => { avatarOk.value = true })
/** 预览大图（仅未破图可进入；current 定位到点击那张） */
function onPreviewImage(i: number) {
  if (brokenImages.value.has(i)) return
  const okIdx = reviewImages.value.map((_, idx) => idx).filter((idx) => !brokenImages.value.has(idx))
  const okUrls = okIdx.map((idx) => getImageUrl(reviewImages.value[idx]))
  if (!okUrls.length) return
  const cur = okIdx.indexOf(i)
  uni.previewImage({ urls: okUrls, current: okUrls[Math.max(cur, 0)] })
}

/** 右上角三点菜单：操作由父页面以 ActionSheet 呈现（举报他人 / 删除本人） */
function onMore() {
  emit('more', props.review)
}
</script>

<style scoped lang="scss">
/* 配图网格语言来自共享 partial（与 ImagePicker 同源） */
@use '../styles/media-grid' as grid;

/* ===== 评价项（口碑卡片：独立卡片 + 圆角 + 阴影）。
   消费方 **2 处**：`DishReviewSection`（传 `flat` ⇒ 嵌在评价卡内的条目）、
   `my-reviews`（默认**非 flat** ⇒ 独立白卡）。两支形态均在实际使用，**均不得删除**。
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
/* 扁平模式：嵌套在评价卡片内（菜品详情），去独立卡样式，只保留条目结构。
   **去掉条目分割线**（原 border-bottom）与上下内边距 —— 条目之间由上层列表容器的
   `--spacing-lg` **纯留白**分隔（用户口径「不加分割线」）。 */
.review-item--flat {
  background: transparent;
  border-radius: var(--radius-none);
  box-shadow: none;
  padding: 0;
}
.review-item--flat.review-item-pressed { opacity: 0.5; }
/* 轻反馈：整卡按压 opacity 微降，避免 scale 按压的整块塌陷感（bg-soft 按压语言，spec §4.9）。
   类名用 review-item-pressed 而非 pressed，避免与 App.vue 全局 .pressed（opacity:0.7）同名冲突。 */
.review-item.review-item-pressed { opacity: 0.6; }

/* 头像：圆形浅灰底（dish-detail-visual-polish 对齐 64rpx）。
   `overflow: hidden` 用于把头像占位（`ImagePlaceholder`）裁到圆形内 —— 缺它时占位方块的直角会露在圆外 */
.review-avatar {
  width: var(--avatar-size-sm);
  height: var(--avatar-size-sm);
  border-radius: var(--radius-circle);
  overflow: hidden;
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

/* 菜名行（本人视角列表）：次级加粗小字，辨识评价所属菜品 */
.review-dish {
  font-size: var(--font-small);
  font-weight: var(--weight-semibold);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 头部：昵称（左）+ 竖三点（右，绝对定位不撑高头行，保证昵称与第二行间距紧凑） */
.review-head {
  position: relative;
  display: flex;
  align-items: center;
}
/* 本人视角变体：无昵称行时头行仍须有高度承载右上「竖三点」（该钮为绝对定位、不参与行高） */
.review-head--mine { min-height: 64rpx; }
/* 头部第一行：昵称，右侧预留三点按钮空间 */
.review-head-left {
  flex: 1;
  min-width: 0;
  /* 64rpx 走 `--spacing-2xl`（同值）—— 为右上「竖三点」留出的避让位 */
  padding-right: var(--spacing-2xl);
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
.review-stars { display: inline-flex; align-items: center; gap: var(--spacing-3xs); flex-shrink: 0; }
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
  width: var(--tap-target-size);
  height: var(--tap-target-size);
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

/* 配图行（≤3 张等比小方图） */
.review-images {
  @include grid.list;
  margin-top: var(--spacing-2xs);
}
.review-image-cell {
  @include grid.cell;
}
.review-image-box {
  @include grid.box;
}
.review-image {
  @include grid.media;
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
