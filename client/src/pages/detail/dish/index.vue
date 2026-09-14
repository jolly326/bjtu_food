<template>
  <view class="page dish-page">
    <!-- dish-detail-visual-polish：页面内覆盖导航（顶部透明 → 滚动渐显菜名与底白），大图全幅出血 -->
    <view
      class="dish-nav"
      :class="{ solid: navSolid }"
      :style="{ paddingTop: topPad, '--nav-h': navBarHeight + 'px', '--nav-pad-right': navPadRight }"
    >
      <view class="dish-nav-row">
        <view class="dish-nav-back" role="button" aria-label="返回" @tap="backToHome">
          <!-- 返回钮＝显式 chip + 黑箭头（微信原生同款配色）：不再用 ::before 伪元素画底，
               避免伪元素层级盖住箭头导致“看不到 icon” -->
          <view class="dish-back-chip">
            <IconSvg
              name="arrow-left"
              :size="'22px'"
              color="var(--nav-back-icon)"
              class="dish-back-icon"
            />
          </view>
        </view>
        <text class="dish-nav-title" :style="{ opacity: dish ? navOpacity : 1 }">{{ dishName }}</text>
      </view>
    </view>

    <!-- 空态/加载/不存在承接条：dish 缺失时固定实底承接返回钮/标题（z 介于滚动内容与覆盖导航之间）；
         有图态无需此条——大图自身持续盖住承接区，定格后由内容流内 .hero-carry 平滑承接 -->
    <view v-if="!dish" class="no-dish-bar" :style="{ height: `${pinLine}px` }" />

    <!-- 大图（.hero-slot）：内容流首块，页面级滚动 + CSS position:sticky 原生实现"两阶段定格"。
         当页面滚动量达到 pinStart 后，浏览器/微信把大图钉在 top:-(heroBase-pinLine)（其底边恰落承接线 pinLine），
         不再逐帧改写 transform——消除"实时计算"造成的偶发闪帧；此后仅下方卡片继续上滑。
         内容未溢出剩余区域时页面本身不滚动，也就没有多余滚动区。 -->
    <view
      v-if="dish"
      class="hero-slot"
      :style="{ height: `${heroBase}px`, top: `-${pinStart}px` }"
    >
      <ImageSwiper
        :images="heroImages"
        :height="`${heroBase}px`"
        :placeholder-size="96"
        placeholder-background="var(--bg-card)"
      />
      <view class="hero-carry" :style="{ height: `${pinLine}px`, opacity: carryOpacity }" />
    </view>

    <template v-if="dish">
      <!-- 私有组件编排：基本信息 / 综合评分（只读）/ 评价（卡内触底加载）。
           内容块最小高度（dishBodyMin）保证：即使内容不足一屏，页面也可滚动 ≥ pinStart，
           「无论如何」都能把顶部大图滑到 header 定格位 -->
      <view class="dish-body" :style="{ minHeight: dishBodyMin + 'px' }">
        <DishInfoCard
          :dish="dish"
          :location-text="locationText"
          :dist-text="distText"
          :dist-active="dishDistance != null"
          @dist-tap="onDistTap"
        />
        <DishSummaryCard
          :rating="dish.rating || 0"
          :rating-count="dish.ratingCount || 0"
          :distribution="ratingDistribution"
        />
        <DishReviewSection
          :reviews="reviewList"
          :total="reviewTotal"
          :current-user-id="currentUserId"
          @delete="onDeleteReview"
          @report="onReviewReport"
          @more="onReviewMore"
        />
      </view>
    </template>

    <!-- 底部固定操作栏：左「写评价」（打开提交弹层）右「去分享」（open-type=share），等宽双按钮 -->
    <view class="action-bar" v-if="dish">
      <button class="bar-btn bar-btn--write" aria-label="写评价" @tap="onOpenReviewComposer">
        <text class="bar-btn-text">写评价</text>
      </button>
      <button class="bar-btn bar-btn--share" open-type="share" aria-label="去分享">
        <text class="bar-btn-text">去分享</text>
      </button>
    </view>

    <!-- 写评价底部抽屉（挂 scroll-view 外；BaseSheet 受控显隐，close 回写关闭；提交成功后重拉评价 + 综合评分） -->
    <ReviewComposer
      v-if="dish"
      :visible="composerOpen"
      :dish-id="dishId"
      :dish-name="dish.name"
      @close="composerOpen = false"
      @submitted="onReviewSubmitted"
    />

    <!-- 举报弹窗（共享组件） -->
    <ReportModal
      :open="reportOpen"
      title="举报评价"
      placeholder="请描述举报原因…"
      confirm-text="提交举报"
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

    <!-- 认证弹层：点赞等需认证入口统一底部弹出 -->
    <AuthSheet />
  </view>
</template>

<script setup lang="ts">
/**
 * dish —— 菜品详情页（入口：首页/搜索/通知等卡片点击）
 * - 编排逻辑抽包内私有 `useDishPage.ts`（数据流 / 顶部大图滚动几何 / 评价分页与删除 /
 *   写评价弹层 / 三点菜单 / 举报 / 距你距离与定位补齐 / 分享）。
 * - 本文件仅保留模板贴片组装与包内子件引用（ImageSwiper / ReviewComposer /
 *   DishInfoCard / DishSummaryCard / DishReviewSection / useDishPage）；生命周期见 useDishPage。
 */
import IconSvg from '@/components/IconSvg.vue'
import ReportModal from './ReportModal.vue'
import ActionSheet from './ActionSheet.vue'
import AuthSheet from '@/components/AuthSheet.vue'
import ImageSwiper from './ImageSwiper.vue'
import ReviewComposer from './ReviewComposer.vue'
import DishInfoCard from './DishInfoCard.vue'
import DishSummaryCard from './DishSummaryCard.vue'
import DishReviewSection from './DishReviewSection.vue'
import { useDishPage } from './useDishPage'

const {
  dish,
  dishId,
  dishName,
  dishBodyMin,
  heroImages,
  heroBase,
  pinLine,
  pinStart,
  carryOpacity,
  navOpacity,
  navSolid,
  topPad,
  navPadRight,
  navBarHeight,
  locationText,
  distText,
  dishDistance,
  ratingDistribution,
  reviewList,
  reviewTotal,
  currentUserId,
  composerOpen,
  reportOpen,
  reportSubmitting,
  reviewMoreOpen,
  reviewMoreItems,
  backToHome,
  onDistTap,
  onDeleteReview,
  onReviewReport,
  onReviewMore,
  onReviewMoreSelect,
  onOpenReviewComposer,
  onReviewSubmitted,
  submitReport,
} = useDishPage()
</script>

<style scoped>
/* 页面级滚动（fix）：根节点不设固定高度、不放内层 scroll-view——内容未溢出剩余区域时页面不产生滚动区；
   内容溢出时由微信原生页面滚动承接（配合下方 hero 的 position:sticky）。
   底部只预留操作栏高度，防止固定操作栏遮挡最后内容。 */
/* QA-04 修复：底部避让由裸 160rpx 改为 token 组合（与 me/profile 同源写法） */
.dish-page { min-height: 100vh; background: var(--bg-page); padding-bottom: calc(var(--action-bar-height) + var(--spacing-lg) + env(safe-area-inset-bottom)); }

/* ===== dish-detail-visual-polish：覆盖导航 ===== */
/* 覆盖导航层全程透明、不自持实底/描边/阴影——导航区背景：有图态由大图本身覆盖承接区（无空窗），
   定格后由内容流内 .hero-carry 实底承接；dish 缺失态由 .no-dish-bar 固定实底承接。
   返回钮使用恒定高对比圆形浮层（见 .dish-nav-back::before），不随背景切换而“隐身”。 */
.dish-nav {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  z-index: var(--z-detail-nav);
}
/* 顶部渐变 scrim 已移除（方案 C→常驻固定 hero header 替代） */
.dish-nav-row {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  height: var(--nav-h);
}
.dish-nav-back {
  position: absolute;
  left: var(--spacing-sm);
  top: 0;
  bottom: 0;
  width: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-tap-highlight-color: transparent;
}
/* 返回钮：底色对齐微信右上角自带胶囊（中性浅灰透底 + #1A1A1A 黑箭头、细边），
   icon 依赖显式 import 的 IconSvg 渲染（此前未 import 导致页面全部图标不可见） */
.dish-back-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: var(--bg-nav-back-chip);
  box-shadow: inset 0 0 0 1rpx var(--border-nav-back-chip);
}
.dish-back-icon { line-height: 1; }
.dish-nav-title {
  position: absolute;
  /* 渐渐显现的标题：以视口水平居中（原生导航标题一致），非在 [返回钮, 胶囊] 之间偏移居中；
     长菜名由 max-width + 省略号收口，避免伸入返回钮 / 右侧胶囊区 */
  left: 50%;
  transform: translateX(-50%);
  max-width: 60%;
  font-size: var(--font-h3);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: opacity var(--duration-base) var(--ease-out);
}

/* 空态/加载/不存在承接条：固定于屏幕顶端、高 pinLine（内联）、实底 --bg-card，保证返回钮可读可用；
   pointer-events:none 不拦手势；底部圆角与卡片一致（16px）。 */
.no-dish-bar {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: var(--z-detail-bar);
  width: 100%;
  background: var(--bg-card);
  border-bottom-left-radius: 16px;
  border-bottom-right-radius: 16px;
  pointer-events: none;
}
/* 大图容器：内容流首块 + position:sticky（top 由内联 -pinStart 动态给定）。
   滚动越过 pinStart 后由滚动引擎把大图钉在 top:-pinStart（其底边恰落承接线 pinLine），
   原生接管“定格”，不逐帧改写 transform → 消除实时计算导致的偶发闪帧；
   此后仅下方卡片继续上滑并被 z-index:2 的大图盖于其上。
   底部圆角全程恒定由 overflow 裁切，任何态不丢圆角。 */
.hero-slot {
  position: sticky;
  z-index: 2;
  width: 100%;
  overflow: hidden;
  line-height: 0;
  border-bottom-left-radius: 16px;
  border-bottom-right-radius: 16px;
}
/* 承接条：锚于大图容器底部 pinLine 高，定格后按 carryOpacity 连续淡入 --bg-card 实底（无空窗/阶跃）；
   opacity 由滚动量驱动，非 CSS transition */
.hero-carry {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--bg-card);
  opacity: 0;
  pointer-events: none;
}

/* 底部固定操作栏：左写评价（主色实底）+ 右分享给同学（白底主色描边次按钮），等宽双按钮，与全局主按钮同高/圆角/字重 */
.action-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: var(--z-action-bar); display: flex; align-items: center; gap: var(--spacing-md); padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }
/* 按钮基线（圆角 12px=24rpx 就近落地、600 字重、88rpx 高；图标 + 文字同行居中） */
.bar-btn { flex: 1; min-width: 0; height: 88rpx; display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); border-radius: 24rpx; border: none; padding: 0; line-height: 1; -webkit-tap-highlight-color: transparent; }
.bar-btn::after { border: none; }
.bar-btn-icon { flex-shrink: 0; }
.bar-btn-text { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
/* 写评价 = 主操作（主色实底 + 白字 + 极淡下投影） */
.bar-btn--write { background: var(--color-primary); box-shadow: var(--shadow-float); }
.bar-btn--write .bar-btn-text { color: var(--color-on-primary); }
/* 分享 = 次操作（白底 + 主色细边/文字，弱于实底主钮） */
.bar-btn--share { background: var(--bg-card); border: 2rpx solid var(--color-primary); }
.bar-btn--share .bar-btn-text { color: var(--color-primary); }
</style>
