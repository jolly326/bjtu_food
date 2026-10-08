<template>
  <view
    class="review-item"
    :class="{
      'review-item--flat': flat,
      'review-item--row-lg': rowPadding === 'lg',
      'review-item--row-edge': rowPadding === 'edge',
      'review-item--divided': divided,
    }"
    hover-class="pressed"
  >
    <!-- 作者头像：两个视角共用同一渲染路径。
         公开视角取评价自带作者；本人视角 `MyReviewVO` 不含作者字段 ⇒ 取当前登录用户资料，
         使「我的评价」与菜品详情评价的**结构完全一致**（同列、同字号、同占位）。 -->
    <image
      v-if="avatarOk && displayAvatar"
      class="review-avatar"
      :src="getThumbImageUrl(displayAvatar)"
      mode="aspectFill"
      lazy-load
      role="img"
      :aria-label="`${displayNickname}的头像`"
      @error="avatarOk = false"
    />
    <view v-else class="review-avatar review-avatar-empty" role="img" :aria-label="`${displayNickname}的头像`">
      <!-- 头像占位：保留人形语义（「无用户」≠「图片损坏」），底色与全站占位同源 -->
      <ImagePlaceholder name="user" :size="32" />
    </view>
    <view class="review-body">
      <view class="review-head">
        <view class="review-head-left">
          <text class="review-nickname">{{ displayNickname }}</text>
        </view>
        <!-- 右上角竖三点：举报（他人）/ 删除（本人）收进 ActionSheet（唯一入口，常驻） -->
        <view class="review-more" role="button" aria-label="更多操作" hover-class="pressed" @tap.stop="onMore">
          <AppIcon name="more-v" :size="28" :color="COLOR_MAP['text-tertiary']" />
        </view>
      </view>
      <!-- 第二行：评分（1-5 黄星 + 分值数字）+ 发布时间，小间隙同行 -->
      <view class="review-meta">
        <view v-if="(review.rating || 0) > 0" class="review-stars" role="img" :aria-label="`评分 ${formatRating(review.rating)} 分`">
          <AppIcon
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
          <!-- 按压反馈：`<image>` 不支持 `hover-class` ⇒ 由外层等比盒承载（视觉与热区不变） -->
          <view class="review-image-box" hover-class="pressed">
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
import AppIcon from '@/components/AppIcon.vue'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
// 星色须传**实色**：AppIcon 的 color 不解析 var()（走 data-uri，见 AppIcon.vue 的 resolveColor），
// 传 var(...) 会恒落 ICON_FALLBACK_COLOR（近黑）。语义键 'star' = --color-star 同源实色。
import { COLOR_MAP } from '@/theme/tokens'
import { getImageUrl, getThumbImageUrl } from '@/utils/image'
import { useBrokenImages } from '@/composables/useBrokenImages'
import { formatRating } from '@/utils/dish'
import { useUserStore } from '@/stores/user'
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
  /**
   * 行内距档（`flat` 列表内的行距，**由组件自持**，消费方无需 `:deep` 穿透打补丁）：
   * `'none'` = 零内距（默认，详情页评价卡自行排版）｜
   * `'lg'` = `--spacing-lg`（通栏壳内由壳统一裁切、行自带内距时用）｜
   * `'edge'` = `--spacing-md` + 负 margin 抵消，使分隔线**整条通宽**（菜品详情评价卡内）。
   */
  rowPadding?: 'none' | 'lg' | 'edge'
  /** 条目间是否渲染 `1rpx` 分隔线（`flat` 列表内；首末由消费方裁切 responsibility） */
  divided?: boolean
}>()

/* 事件**只有一个出口**：`more`（右上角竖三点）；本人删除 / 他人举报统一由父页 `ActionSheet` 处理。 */
const emit = defineEmits<{
  (e: 'more', review: Review | MyReview): void
}>()

const avatarOk = ref(true)

/**
 * 本人视角的身份补齐：`MyReviewVO` 不含作者字段（「我的评价」页恒为本人、渲染即冗余），
 * 但**头像与昵称仍要展示** —— 取当前登录用户资料，使本页与菜品详情评价的版式完全一致。
 * 未登录 / 资料未回填时回落空串 ⇒ 走 `ImagePlaceholder` 头像占位 + 「用户」昵称。
 */
const userStore = useUserStore()
const displayAvatar = computed(() => (props.mine ? userStore.userInfo?.avatar || '' : authorAvatar.value))
const displayNickname = computed(() => (props.mine ? userStore.userInfo?.nickname || ANONYMOUS_AUTHOR : authorNickname.value))

/**
 * 作者标识（**仅公开视角下发**；本人视角恒为本人、零信息 ⇒ 不渲染头像 / 昵称）。
 * 用 `in` 收窄并集：公开视角行含 `userId`，本人视角行含 `dishId`。
 */
const authorAvatar = computed(() => ('userAvatar' in props.review ? props.review.userAvatar : ''))
const authorNickname = computed(() => {
  const name = 'userNickname' in props.review ? props.review.userNickname : ''
  return name || ANONYMOUS_AUTHOR
})

/** 星级渲染颗数（1–5，最低 1 颗） */
const starCount = computed(() => Math.min(Math.max(Math.round(props.review.rating || 0), 1), 5))

/* ===== 配图展示（≤3 张 COS URL，点击预览大图） ===== */
// `images` 在类型上是可选字段，故此处保留判空兜底（可能为 undefined）
const reviewImages = computed(() => props.review.images?.filter(Boolean) ?? [])
/** 破图下标集合：error 后切 empty 中性占位；images 变化（列表重拉）时重置 */
const { broken: brokenImages, markBroken: onImageError, clear, previewAt } = useBrokenImages()
// 配图与头像的破图态都随本行数据变化复位（组件实例复用、同 key 换评价时避免旧破图态残留）
watch([() => props.review.images, authorAvatar], () => {
  clear()
  avatarOk.value = true
})
/** 预览大图（仅未破图可进入，current 定位到点击那张；URL 需绝对化） */
function onPreviewImage(i: number) {
  previewAt(reviewImages.value, i, getImageUrl)
}

/** 右上角三点菜单：操作由父页面以 ActionSheet 呈现（举报他人 / 删除本人） */
function onMore() {
  emit('more', props.review)
}
</script>

<style scoped lang="scss">
/* 配图网格语言来自共享 partial（与 ImagePicker 同源） */
@use '../styles/media-grid' as grid;

/* ===== 评价项（口碑条目：独立模块 + 圆角 + 阴影）。
   消费方 **2 处**：`DishReviewSection`（传 `flat` ⇒ 嵌在评价卡内的条目）、
   `my-reviews`（默认**非 flat** ⇒ 独立模块）。两支形态均在实际使用，**均不得删除**。
   口碑层扁平：不设评论/回复/点赞入口，互动仅右上角三点菜单（删除 / 举报）。
   设计要点：卡片层级、touch 物理反馈、层级对比（昵称黑/正文黑/时间灰/操作灰）、星级展示 */
.review-item {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  background: var(--module-bg);
  border-radius: var(--radius-card);
  box-shadow: var(--module-shadow);
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
  transition: opacity var(--duration-fast) var(--ease-out);
}
/* 扁平模式：嵌套在评价卡片内（菜品详情），去独立卡样式，只保留条目结构。
   **去掉条目分割线**与上下内边距 —— 条目之间由上层列表容器的
   `--spacing-lg` **纯留白**分隔（用户口径「不加分割线」）。 */
.review-item--flat {
  background: transparent;
  border-radius: var(--radius-none);
  box-shadow: none;
  padding: 0;
}
/* 行内距 `lg` 档（`flat` 列表内、行自带内距时）：行距由**组件自持**，
   消费方无需 `:deep` 穿透打补丁（改这里即可同时生效详情页与我的评价页）。 */
.review-item--row-lg {
  padding: var(--spacing-lg);
}
/* 条目间分隔线（`divided`）：行内距由上档承担，此处只补 1rpx 结构线 */
.review-item--divided {
  border-top: 1rpx solid var(--border-color);
}
/* 行内距 `edge` 档：负 margin 抵消内距 ⇒ 分隔线整条通宽（菜品详情评价卡内） */
.review-item--row-edge {
  padding: var(--spacing-md);
  margin: 0 calc(-1 * var(--spacing-md));
}
/* 整块按压反馈统一走全局 `.pressed`（App.vue 兜底档，opacity 0.7）——本组件不自持按压覆盖。 */

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
  transition: opacity var(--duration-fast) var(--ease-out);
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
/* 按压反馈：竖三点属「小件」档（行内图标钮）⇒ 0.6（覆盖全局 `.pressed` 整块档 0.7） */
.review-more.pressed { opacity: 0.6; }

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
/* 破图兜底：empty 中性占位（浅底居中），可点击但不进预览 */
.review-image-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>