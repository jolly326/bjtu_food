<template>
  <view class="page dish-page" :style="{ paddingTop: `${titleBandPx}px` }">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下，接入无需改动既有层级） -->
    <PageWallpaper fixed />
    <!-- 顶部标题带（公共 `AppTitleBand`，与首页 / 搜索页**同源**）：
         左区「返回」（文字）+ 居中区菜名（**随滚动淡入**，`titleOpacity` 只作用于文字）。
         UI 统一 Loop Round 16（2026-09-27 裁决 c）：本页改为与首页 §11 **同构** ——
         标题带恒透明、其下滚动区 ⇒ **没有内容从带背后经过** ⇒ 零切片 / 零实底切换 / 零承接条
         （原自绘 `.dish-nav` 的"透明→实底/渐显"与 `.no-dish-bar` 承接条已退役）。 -->
    <AppTitleBand back :title="dishName" :title-opacity="navOpacity" @back="backToHome" />

    <!-- 详情拉取失败 / 菜品不存在 / 缺少 ID：明确文案 + 恢复路径，不得只留纯空白页。
         加载期间（!dish && 未失败）保持空白静默，不新增骨架屏 / loading 指示。
         缺 ID 无重试意义，仅给「返回」。 -->
    <view
      v-if="!dish && (detailFailed || detailNotFound || missingDishId)"
      class="detail-fail-host"
    >
      <!-- 统一失败块（UI 统一 Loop Round 13 裁决 9B）：**双 CTA 形态**，取代原自绘 `.detail-fail` 按钮组。
           文案分流（R8）：不存在（4001）/ 缺 ID ⇒ 不可重试、只给「返回」；网络故障 ⇒ 「重新加载 + 返回」。
           文案与图标（`name="report"`，唯一近似语义键、非举报语义）由 `RetryBlock` 统一承载。
           在途（点击后）显示旋转环 +「正在重新加载…」并忽略重复点击 —— 属**用户主动重试**的在途反馈，
           不是页面级 loading 指示（§4.8 口径已按裁决调整）。 -->
      <RetryBlock
        strong
        :title="detailNotFound ? '这道菜已不在了' : '这道菜暂时打不开'"
        :hint="detailNotFound ? '它可能已被下架或移除' : '可能是网络暂时不可用'"
        :loading="detailReloading"
        :primary-text="!missingDishId && !detailNotFound ? '重新加载' : ''"
        secondary-text="返回"
        @retry="onRetryDetailClick"
        @secondary="backToHome"
      />
    </view>

    <!-- ===== 滚动区（与首页 §11 同构）=====
         `scroll-view` + `flex: 1`：顶边 = 标题带下沿（页面 padding-top 让出）、底边 = 底部操作栏上沿；
         内容被裁在滚动区内 ⇒ **不会从标题带背后经过**（零切片 / 零实底切换 / 零承接条）。
         `@scroll` 只驱动菜名淡入；`@scrolltolower` 承接评价分页（原页面级 onReachBottom 退役）。 -->
    <scroll-view
      v-if="dish"
      class="dish-scroll"
      scroll-y
      @scroll="onScroll"
      @scrolltolower="onReviewsReachBottom"
    >
      <!-- hero 卡（滚动区首块）：四周留白 12px + 圆角 + 16:10 —— 与首页 Banner **同语言**；
           随滚动 1:1 上移、在标题带下沿被**裁掉**（"移出屏幕"，与首页 Banner 逐字一致）。
           大图关闭自动轮播（autoplay=false），仅手动滑动、保留指示点。 -->
      <view class="hero-card" :style="{ height: `${heroHeightPx}px` }">
        <ImageSwiper
          :images="heroImages"
          :height="`${heroHeightPx}px`"
          :autoplay="false"
          label="菜品图片"
          :placeholder-size="96"
          placeholder-background="var(--bg-card)"
        />
      </view>

    <template v-if="dish">
      <!-- 私有组件编排：信息卡（五段）/ 综合评分（只读）/ 评价（卡内触底加载）。
           ⚠️ 原 `dishBodyMin`（保证页面可滚动 ≥ pinStart，好让大图定格）已随定格方案退役（R16 口径 c） -->
      <view class="dish-body">
        <DishInfoCard :dish="dish" :location-text="locationText" />
        <DishSummaryCard
          :rating="dish.rating || 0"
          :rating-count="dish.ratingCount || 0"
          :distribution="ratingDistribution"
        />
        <DishReviewSection
          :reviews="reviewList"
          :total="reviewTotal"
          :current-user-id="currentUserId"
          :load-failed="reviewFailed"
          :image-only="imageOnly"
          :pending="reviewPending"
          @delete="onDeleteReview"
          @report="onReviewReport"
          @more="onReviewMore"
          @retry="onRetryReviews"
          @write="onOpenReviewComposer"
          @toggle-image-only="onToggleImageOnly"
        />
      </view>
    </template>
    </scroll-view>

    <!-- 底部固定操作栏：左「写评价」（会话内判定为已评价或提交成功后本地切「重新评价」）右「去分享」（open-type=share），等宽双按钮 -->
    <view class="action-bar" v-if="dish">
      <button class="bar-btn bar-btn--write" :aria-label="reviewButtonText" hover-class="pressed" @tap="onOpenReviewComposer">
        <text class="bar-btn-text">{{ reviewButtonText }}</text>
      </button>
      <button class="bar-btn bar-btn--share" open-type="share" aria-label="去分享" hover-class="pressed">
        <text class="bar-btn-text">去分享</text>
      </button>
    </view>

    <!-- 写评价 / 重新评价底部抽屉（挂 scroll-view 外；BaseSheet 受控显隐，close 回写关闭；
         已评价时传入 reviewId + 预填旧值，走覆盖式重评 PUT /reviews/{id}） -->
    <ReviewComposer
      v-if="dish"
      :visible="composerOpen"
      :dish-id="dishId"
      :dish-name="dish.name"
      :review-id="composerReviewId"
      :prefill="composerPrefill"
      @close="composerOpen = false"
      @submitted="onReviewSubmitted"
    />

    <!-- 举报底部弹层：原因单选（字典下发） -->
    <ReportModal
      :open="reportOpen"
      :submitting="reportSubmitting"
      @update:open="reportOpen = $event"
      @submit="submitReport"
    />

    <!-- 评价三点菜单：删除/举报（通用 ActionSheet） -->
    <ActionSheet
      :open="reviewMoreOpen"
      :items="reviewMoreItems"
      @close="reviewMoreOpen = false"
      @select="onReviewMoreSelect"
    />

    <!-- 认证弹层：评价等需认证入口统一底部弹出 -->
  </view>
</template>

<script setup lang="ts">
/**
 * dish —— 菜品详情页（入口：首页/搜索/通知等卡片点击）
 * - 编排逻辑抽包内私有 `useDishPage.ts`（数据流 / 顶部大图滚动几何 / 评价分页与删除 /
 *   写评价 / 重新评价弹层 / 三点菜单 / 举报 / 分享）。
 * - 本文件仅保留模板贴片组装与包内子件引用（ImageSwiper / ReviewComposer /
 *   DishInfoCard / DishSummaryCard / DishReviewSection / useDishPage）；生命周期见 useDishPage。
 */
import IconSvg from '@/components/IconSvg.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import ReportModal from './ReportModal.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import ImageSwiper from './ImageSwiper.vue'
import ReviewComposer from './ReviewComposer.vue'
import DishInfoCard from './DishInfoCard.vue'
import DishSummaryCard from './DishSummaryCard.vue'
import DishReviewSection from './DishReviewSection.vue'
import { useDishPage } from './useDishPage'
// 图标色须传**实色**（IconSvg 的 color 不解析 var()，data-uri 内为字面量，传 var(...) 恒落近黑）
import { COLOR_MAP } from '@/theme/tokens'
import RetryBlock from '@/components/RetryBlock.vue'
import AppTitleBand from '@/components/AppTitleBand.vue'
import { useNavMetrics } from '@/utils/useNavMetrics'
import { ref } from 'vue'

/** 标题带高（px）：页面根 `padding-top` 让出（fixed 标题带不占流内高度）—— 与首页 / 搜索页同源 */
const { titleBandPx } = useNavMetrics()

/** 详情重拉在途（UI 统一 Loop Round 13 裁决 9B）：驱动 `RetryBlock` 的旋转环。
 *  属「用户主动点击重试」的在途反馈，**不是**页面级 loading 指示（§4.8 口径已按裁决调整）。
 *  声明在 `useDishPage()` 解构之前是安全的：函数体在**点击时**才解析 `onRetryDetail`（闭包调用期解析）。 */
const detailReloading = ref(false)
async function onRetryDetailClick() {
  if (detailReloading.value) return
  detailReloading.value = true
  try {
    await onRetryDetail()
  } finally {
    detailReloading.value = false
  }
}

const {
  dish,
  dishId,
  dishName,
  heroImages,
  heroHeightPx,
  navOpacity,
  onScroll,
  locationText,
  ratingDistribution,
  reviewList,
  reviewTotal,
  reviewFailed,
  reviewPending,
  detailFailed,
  detailNotFound,
  missingDishId,
  imageOnly,
  currentUserId,
  reviewButtonText,
  composerPrefill,
  composerReviewId,
  composerOpen,
  reportOpen,
  reportSubmitting,
  reviewMoreOpen,
  reviewMoreItems,
  backToHome,
  onRetryDetail,
  onToggleImageOnly,
  onDeleteReview,
  onReviewReport,
  onReviewMore,
  onReviewMoreSelect,
  onOpenReviewComposer,
  onReviewSubmitted,
  onRetryReviews,
  onReviewsReachBottom,
  submitReport,
} = useDishPage()
</script>

<style scoped>
/* 页面级滚动（fix）：根节点不设固定高度、不放内层 scroll-view——内容未溢出剩余区域时页面不产生滚动区；
   内容溢出时由微信原生页面滚动承接（配合下方 hero 的 position:sticky）。
   底部只预留操作栏高度，防止固定操作栏遮挡最后内容。 */
/* QA-04 修复：底部避让由裸 160rpx 改为 token 组合（与 me/profile 同源写法） */
/* 页面根不带底色（UI 统一 Loop Round 11）：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层 */
.dish-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding-bottom: calc(var(--action-bar-height) + var(--spacing-lg) + env(safe-area-inset-bottom));
}

/* ===== 滚动区（与首页 §11 同构）=====
   `flex: 1` ⇒ 顶边 = 标题带下沿（页面 `padding-top` 让出）、底边 = 底部操作栏上沿。
   内容被裁在滚动区内 ⇒ **不会从标题带背后经过**（零切片 / 零实底切换 / 零承接条）。 */
.dish-scroll {
  flex: 1;
  min-height: 0;
}

/* ===== hero 卡（滚动区首块）=====
   与首页 Banner **同语言**：四周留白 `--spacing-md`(12px) + `--radius-card` 圆角 + 16:10 定高（高由内联下发）；
   随滚动 1:1 上移、在标题带下沿被**裁掉**（"移出屏幕"）。 */
.hero-card {
  margin: var(--spacing-md);
  border-radius: var(--radius-card);
  overflow: hidden;
  line-height: 0;
}

/* ===== 详情失败 / 不存在态：**宿主只做剩余区域内居中**，视觉全部由公共 `RetryBlock` 承担
   （UI 统一 Loop Round 13 裁决 9B 并入；原自绘图标 / 文案 / 按钮样式已删）。
   `flex: 1` ⇒ 在「标题带下沿 ↔ 底部操作栏上沿」之间垂直居中（不再用 min-height: 100vh 硬撑）。 ===== */
.detail-fail-host {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding-left: var(--spacing-xl);
  padding-right: var(--spacing-xl);
  padding-bottom: var(--spacing-2xl);
  box-sizing: border-box;
}

/* 原 `.hero-slot`（sticky 两阶段定格）与 `.hero-carry`（承接条）已随口径 c 退役：
   hero 现为滚动区首块 `.hero-card`（见上），随滚动 1:1 移出、在标题带下沿被裁。 */

/* 底部固定操作栏：左写评价 / 重新评价（主色实底）+ 右分享给同学（白底主色描边次按钮），等宽双按钮，与全局主按钮同高/圆角/字重 */
.action-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: var(--z-action-bar); display: flex; align-items: center; gap: var(--spacing-md); padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }
/* 按钮基线（圆角统一到全局主按钮档位 token --radius-btn、600 字重、88rpx 高；图标 + 文字同行居中）。
   按压反馈显式 :active（不依赖平台默认 hover），与全站 bg-soft/opacity 按压语言一致。 */
.bar-btn { flex: 1; min-width: 0; height: 88rpx; display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); border-radius: var(--radius-btn); border: none; padding: 0; line-height: 1; -webkit-tap-highlight-color: transparent; }
.bar-btn::after { border: none; }
.bar-btn:active { opacity: 0.85; }
.bar-btn-icon { flex-shrink: 0; }
.bar-btn-text { font-size: var(--font-subtitle); font-weight: var(--weight-medium); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 写评价 / 重新评价 = 主操作（主色实底 + 白字 + 极淡下投影） */
.bar-btn--write { background: var(--color-primary); box-shadow: var(--shadow-float); }
.bar-btn--write .bar-btn-text { color: var(--color-on-primary); }
/* 分享 = 次操作（白底 + 主色细边/文字，弱于实底主钮） */
.bar-btn--share { background: var(--bg-card); border: 2rpx solid var(--color-primary); }
.bar-btn--share .bar-btn-text { color: var(--color-primary); }
</style>
