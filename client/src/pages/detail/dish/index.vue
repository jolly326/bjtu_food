<template>
  <view class="page dish-page" :style="{ paddingTop: `${titleBandPx}px` }">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下，接入无需改动既有层级） -->
    <PageWallpaper fixed />
    <!-- 顶部标题带（公共 `AppTitleBand`，与首页 / 搜索页**同源**）：
         左区「返回」（文字）+ 居中区菜名（**随滚动淡入**，`titleOpacity` 只作用于文字）。
         本页改为与首页 §11 **同构** ——
         标题带恒透明、其下滚动区 ⇒ **没有内容从带背后经过** ⇒ 零切片 / 零实底切换 / 零承接条
         （承接条已移除）。 -->
    <AppTitleBand back :title="dp.dishName" :title-opacity="dp.navOpacity" @back="dp.backToHome" />

    <!-- 详情拉取失败 / 菜品不存在 / 缺少 ID：明确文案 + 恢复路径，不得只留纯空白页。
         加载期间（!dish && 未失败）保持空白静默，不新增骨架屏 / loading 指示。
         缺 ID 无重试意义，仅给「返回」。 -->
    <view
      v-if="!dp.dish && (dp.detailFailed || dp.detailNotFound || dp.missingDishId)"
      class="detail-fail-host"
    >
      <!-- 统一失败块：**双 CTA 形态**，取代原自绘 `.detail-fail` 按钮组。
           文案分流（R8）：不存在（4001）/ 缺 ID ⇒ 不可重试、只给「返回」；网络故障 ⇒ 「重新加载 + 返回」。
           文案与图标（`name="report"`，唯一近似语义键、非举报语义）由 `RetryBlock` 统一承载。
           在途（点击后）显示旋转环 +「正在重新加载…」并忽略重复点击 —— 属**用户主动重试**的在途反馈，
           不是页面级 loading 指示（口径已按裁决调整）。 -->
      <RetryBlock
        strong
        :title="dp.detailNotFound ? '这道菜已不在了' : '这道菜暂时打不开'"
        :hint="dp.detailNotFound ? '它可能已被下架或移除' : '可能是网络暂时不可用'"
        :loading="detailReloading"
        :primary-text="!dp.missingDishId && !dp.detailNotFound ? '重新加载' : ''"
        secondary-text="返回"
        @retry="onRetryDetailClick"
        @secondary="dp.backToHome"
      />
    </view>

    <!-- ===== 滚动区（与首页 §11 同构）=====
         `scroll-view` + `flex: 1`：顶边 = 标题带下沿（页面 padding-top 让出）、底边 = 底部操作栏上沿；
         内容被裁在滚动区内 ⇒ **不会从标题带背后经过**（零切片 / 零实底切换 / 零承接条）。
         `@scroll` 只驱动菜名淡入；`@scrolltolower` 承接评价分页（页面级 onReachBottom 已移除）。 -->
    <scroll-view
      v-if="dp.dish"
      class="dish-scroll"
      scroll-y
      @scroll="dp.onScroll"
      @scrolltolower="dp.onReviewsReachBottom"
    >
      <!-- hero 卡（滚动区首块）：四周留白 12px + 圆角 + 16:10 —— 与首页 Banner **同语言**；
           随滚动 1:1 上移、在标题带下沿被**裁掉**（"移出屏幕"，与首页 Banner 逐字一致）。
           大图关闭自动轮播（autoplay=false），仅手动滑动、保留指示点。 -->
      <view class="hero-card" :style="{ height: `${dp.heroHeightPx}px` }">
        <ImageSwiper
          :images="dp.heroImages"
          :height="`${dp.heroHeightPx}px`"
          :autoplay="false"
          label="菜品图片"
          :placeholder-size="96"
        />
      </view>

    <template>
      <!-- 私有组件编排：信息卡（含评分行）/ 评价（卡内触底加载）；
           均分 + 人数 + 分布并入信息卡一行（同源同刻），减少一块版面与一次视觉重复。 -->
      <view class="dish-body">
        <DishInfoCard
          :dish="dp.dish"
          :location-text="dp.locationText"
          :rating="dp.dish.rating"
          @correct="dp.onCorrectDishInfo"
        />
        <DishReviewSection
          :reviews="dp.reviewList"
          :count="dp.reviewList.length"
          :load-failed="dp.reviewFailed"
          :pending="dp.reviewPending"
          :scroll-top="dp.scrollTop"
          :rating-filter="dp.reviewRatingFilter"
          @more="dp.onReviewMore"
          @retry="dp.onRetryReviews"
          @write="dp.onOpenReviewComposer"
          @filter="dp.onFilterRating"
        />
      </view>
    </template>
    </scroll-view>

    <!-- 页面底部**无任何常驻操作栏**：
         写评价入口 = 评价标题行右侧按钮；纠错入口 = 信息卡名称行「菜品有问题?」。 -->

    <!-- 写评价底部抽屉（挂 scroll-view 外；BaseSheet 受控显隐，close 回写关闭）。
         恒为**空表单**：不做任何写前判定、不预填旧值（同一用户重复提交由服务端覆盖旧评价）。 -->
    <ReviewComposer
      v-if="dp.dish"
      :visible="dp.composerOpen"
      :dish-id="dp.dishId"
      :dish-name="dp.dish.name"
      @close="dp.composerOpen = false"
      @submitted="dp.onReviewSubmitted"
    />

    <!-- 举报底部弹层：原因单选（字典下发） -->
    <ReportModal
      :open="dp.reportOpen"
      :submitting="dp.reportSubmitting"
      @update:open="dp.reportOpen = $event"
      @submit="dp.submitReport"
    />

    <!-- 评价三点菜单：删除/举报（通用 ActionSheet） -->
    <ActionSheet
      :open="dp.reviewMoreOpen"
      :items="dp.reviewMoreItems"
      @close="dp.reviewMoreOpen = false"
      @select="dp.onReviewMoreSelect"
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
 *   DishInfoCard（含评分行）/ DishReviewSection / useDishPage）；生命周期见 useDishPage。
 * - 编排绑定以单一 `dp` 对象下发（来自 `useDishPage()` 的 `reactive` 返回），降低与
 *   `useDishPage` 的耦合面：新增 / 重命名绑定无需回改本文件的解构清单。
 */
import PageWallpaper from '@/components/PageWallpaper.vue'
import ReportModal from './ReportModal.vue'
import ActionSheet from '@/components/ActionSheet.vue'
import ImageSwiper from './ImageSwiper.vue'
import ReviewComposer from './ReviewComposer.vue'
import DishInfoCard from './DishInfoCard.vue'
import DishReviewSection from './DishReviewSection.vue'
import { useDishPage } from './useDishPage'
import RetryBlock from '@/components/RetryBlock.vue'
import AppTitleBand from '@/components/AppTitleBand.vue'
import { useNavMetrics } from '@/utils/useNavMetrics'
import { ref } from 'vue'

/** 标题带高（px）：页面根 `padding-top` 让出（fixed 标题带不占流内高度）—— 与首页 / 搜索页同源 */
const { titleBandPx } = useNavMetrics()

/** 详情重拉在途：驱动 `RetryBlock` 的旋转环。
 *  属「用户主动点击重试」的在途反馈，**不是**页面级 loading 指示（口径已按裁决调整）。
 *  声明在 `useDishPage()` 解构之前是安全的：函数体在**点击时**才解析 `dp.onRetryDetail`（闭包调用期解析）。 */
const detailReloading = ref(false)
async function onRetryDetailClick() {
  if (detailReloading.value) return
  detailReloading.value = true
  try {
    await dp.onRetryDetail()
  } finally {
    detailReloading.value = false
  }
}

const dp = useDishPage()
</script>

<style scoped>
/* 页面级滚动（fix）：根节点不设固定高度、不放内层 scroll-view——内容未溢出剩余区域时页面不产生滚动区；
   内容溢出时由微信原生页面滚动承接（配合下方 hero 的 position:sticky）。
   底部只预留操作栏高度，防止固定操作栏遮挡最后内容。 */
/* QA-04 修复：底部避让由裸 160rpx 改为 token 组合（与 me/profile 同源写法） */
/* 页面根不带底色：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层 */
.dish-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  /* 补 dvh（移动端 H5 地址栏伸缩时 vh > 真实可视高 ⇒ 页根超高 ⇒ 页面自身多出一段可滚区 / 底部露白） */
  height: 100dvh;
  /* 底部让位**精确等于**固定操作栏高度（token 已按「8 + 44 + 8 = 60px」定档）——
     不再叠加 `--spacing-lg`（那会在滚动区下沿与操作栏之间留出一条可见空档）。
     内容末端的呼吸感由卡片自身 margin 提供，不靠这里补。 */
  /* 底部**无固定操作栏**：不再预留 `--action-bar-height`，
     仅保留常规底部呼吸位；`env(safe-area-inset-bottom)` 由页面滚动末端自然兜底。 */
  padding-bottom: var(--spacing-md);
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
   。
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
</style>
