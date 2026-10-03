<template>
  <view class="page correction-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1`） -->
    <PageWallpaper fixed />
    <Header title="菜品信息纠错" @back="goBack" />

    <!-- 独立页面（与「意见反馈」互不耦合）：**只**承接「某条菜品资料有误」的专项修正。
         进页即按导航参数 `dishId` 锚定该菜品并预填全部字段，**页内不提供切换菜品**；
         表单七段结构（提示横幅 → 只读锚定 → 基础信息 → 动态属性 → 图片 → 说明 → 提交）
         全在包内私有 `CorrectionForm`，本页只做壳与失败态恢复路径。 -->
    <scroll-view class="scroll-wrap" scroll-y :scroll-into-view="scrollIntoView" :scroll-with-animation="true">
      <!-- 菜品不存在（4001，不可重试）/ 预填失败（可重试）：明确文案 + 恢复路径 -->
      <RetryBlock
        v-if="notFound || loadFailed"
        strong
        :title="notFound ? '这道菜已不在了' : '菜品信息暂时打不开'"
        :hint="notFound ? '它可能已被下架或移除' : '可能是网络暂时不可用'"
        :loading="loading"
        :primary-text="!notFound ? '重新加载' : ''"
        secondary-text="返回"
        @retry="retryLoad"
        @secondary="goBack"
      />
      <CorrectionForm
        v-else
        ref="correctionFormRef"
        :model="form"
        :dish-name="dishName"
        :dish-location="dishLocation"
        :detail-loading="loading"
        :errors="fieldErrors"
        :submitting="submitting"
        :can-submit="canSubmit"
        :gate-hint="gateHint"
        :submit-error="submitError"
        @clear="clearError"
        @submit="submit"
        @pick-image="pickSheetOpen = true"
      />
      <!-- 卡片下缘留白（提交按钮随内容滚动，非固定底栏 ⇒ 只需 safe-area 避让） -->
      <view class="bottom-space" />
    </scroll-view>

    <!-- 配图来源弹层（拍照 / 从相册选择）：**必须挂在 scroll-view 之外**（小程序 scroll-view 内
         fixed 层级会被压扁/裁剪）。动作项取共享真源 imagePickSource，与意见反馈页不各写一份。 -->
    <ActionSheet
      :open="pickSheetOpen"
      :items="IMAGE_PICK_ACTIONS"
      @close="pickSheetOpen = false"
      @select="onPickImageSource"
    />
  </view>
</template>

<script setup lang="ts">
/**
 * correction —— 菜品信息纠错页（独立分包 pages/correction/，二级页无 TabBar）
 * - 入口**唯一**：菜品详情页底栏「反馈错误」（携带 dishId）⇒ 进页即拉详情预填；
 * - 页面形态：**锚定只读**（不切换菜品）+ 动态属性 `AttributeGroup`（数据驱动维度）+ 下划线轻量输入，
 *   提交**仅带上改动项**（patch）走 `POST /dishes/{id}/correction`，与 `POST /feedback` 完全分开；
 * - 编排逻辑抽包内私有 `useCorrection.ts`；包内子件：CorrectionForm / AttributeGroup / TagChip。
 */
import { ref } from 'vue'
import ActionSheet from '@/components/ActionSheet.vue'
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import CorrectionForm from './CorrectionForm.vue'
import { useCorrection } from './useCorrection'
import { IMAGE_PICK_ACTIONS, isPickSource, type PickSource } from '@/components/imagePickSource'

const {
  goBack,
  form,
  dishName,
  dishLocation,
  loading,
  notFound,
  loadFailed,
  retryLoad,
  fieldErrors,
  scrollIntoView,
  submitting,
  submitError,
  clearError,
  canSubmit,
  gateHint,
  submit,
} = useCorrection()

/* ===== 配图来源弹层（页面根级，因 scroll-view 内 fixed 层级会被裁剪）===== */
const pickSheetOpen = ref(false)
/** CorrectionForm 暴露的 startPick 中转（→ ImagePicker.startPick） */
const correctionFormRef = ref<{ startPick: (source: PickSource) => void } | null>(null)
/** ActionSheet 回抛 key（string）先收窄为 PickSource，未知 key 忽略，不让脏 key 进上传链路 */
function onPickImageSource(key: string) {
  if (!isPickSource(key)) return
  correctionFormRef.value?.startPick(key)
}
</script>

<style scoped>
/* 页面根不带底色（底色下沉到全局 `page{}`，否则会盖住负层级壁纸层） */
.correction-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }

/* 主滚动区：底部 safe-area 避让（提交区随内容滚动，无固定底栏） */
.scroll-wrap {
  flex: 1;
  min-height: 0;
  /* `scroll-view` 自身不写 overflow-y（外挂 CSS 会在 H5 叠出第二根滚动条） */
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: border-box;
}

/* 卡片下缘留白：表单卡片与屏幕底部之间留出呼吸位（随内容滚动，不遮挡任何内容） */
.bottom-space { height: var(--spacing-lg); }
</style>
