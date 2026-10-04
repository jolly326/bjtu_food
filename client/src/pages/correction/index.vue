<template>
  <view class="page correction-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1`） -->
    <PageWallpaper fixed />
    <Header title="菜品问题反馈" @back="goBack" />

    <!-- 独立页面（与「意见反馈」互不耦合）：承接「某条菜品」的**问题反馈**。
         进页即按导航参数 `dishId` 锚定该菜品并**先选问题类型**（唯一入口，页内不提供切换菜品）；
         ① `field`（信息有误）→ 拉详情预填，走 `CorrectionForm` 七段结构（局部提交）；
         ② `gone`（已经下架）→ **不拉详情**，走 `GoneForm` 一键提交（提交恒可用）。 -->
    <scroll-view class="scroll-wrap" scroll-y :scroll-into-view="scrollIntoView" :scroll-with-animation="true">
      <!-- ① 问题类型（进页第一屏 · 单选两项）：未选类型**不渲染任何表单**
           （UI 稿「不选类型不得进入表单」）；选定后常驻表单上方，可随时改选（表单随类型切换）。
           菜品不存在（4001）时无类型可言 ⇒ 隐藏选择器，只留下方恢复路径。 -->
      <TypePicker
        v-if="!notFound"
        :model-value="problemType"
        :disabled="submitting"
        @update:model-value="selectType"
      />

      <!-- ② `field` 预填失败：菜品不存在（4001，不可重试）/ 网络失败（可重试） -->
      <RetryBlock
        v-if="typeChosen && !isGone && (notFound || loadFailed)"
        strong
        :title="notFound ? '这道菜已不在了' : '菜品信息暂时打不开'"
        :hint="notFound ? '它可能已被下架或移除' : '可能是网络暂时不可用'"
        :loading="loading"
        :primary-text="!notFound ? '重新加载' : ''"
        secondary-text="返回"
        @retry="retryLoad"
        @secondary="goBack"
      />
      <!-- ③ `field` 型：字段表单（锚定只读 + 基础信息 + 动态属性 + 图片；局部提交）。
           预填未完成时组件内部只渲染锚定行（不渲染半截表单）。 -->
      <CorrectionForm
        v-else-if="problemType === 'field'"
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
      <!-- ④ `gone` 型：一键提交（提交按钮恒可用；补充说明 / 图片皆选填、默认折叠） -->
      <GoneForm
        v-else-if="isGone"
        ref="goneFormRef"
        v-model:note="goneNote"
        v-model:images="goneImages"
        :dish-name="dishName"
        :dish-location="dishLocation"
        :submitting="submitting"
        :submit-error="submitError"
        @submit="submitGone"
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
 * correction —— 菜品问题反馈页（独立分包 pages/correction/，二级页无 TabBar）
 * - 入口**唯一**：菜品详情页信息卡名称行「菜品有问题?」（携带 `dishId`）；
 * - 页面形态：**先选问题类型**（第一屏单选两项）→ 表单随类型切换：
 *   ① `field`（信息有误）→ 拉详情预填，走 `CorrectionForm`（**锚定只读** + 动态属性 + 下划线轻量输入，
 *      提交**仅带上改动项** patch）；
 *   ② `gone`（已经下架）→ **不拉详情**，走 `GoneForm`（**一键提交**，补充说明 / 图片皆选填、默认折叠）；
 *   两型同走 `POST /dishes/{id}/correction`（携带 `type`），与 `POST /feedback` 完全分开；
 * - 编排逻辑抽包内私有 `useCorrection.ts`；包内子件：TypePicker / CorrectionForm / GoneForm /
 *   AttributeGroup / AttributePickerSheet / FloorPickerSheet / TagChip。
 */
import { ref } from 'vue'
import ActionSheet from '@/components/ActionSheet.vue'
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import CorrectionForm from './CorrectionForm.vue'
import GoneForm from './GoneForm.vue'
import TypePicker from './TypePicker.vue'
import { useCorrection } from './useCorrection'
import { IMAGE_PICK_ACTIONS, isPickSource, type PickSource } from '@/components/imagePickSource'

const {
  goBack,
  problemType,
  typeChosen,
  isGone,
  selectType,
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
  goneNote,
  goneImages,
  submitGone,
} = useCorrection()

/* ===== 配图来源弹层（页面根级，因 scroll-view 内 fixed 层级会被裁剪）===== */
const pickSheetOpen = ref(false)
/** 两型表单各自暴露的 `startPick` 中转（→ `ImagePicker.startPick`）；同一时刻只有一型挂载 */
const correctionFormRef = ref<{ startPick: (source: PickSource) => void } | null>(null)
const goneFormRef = ref<{ startPick: (source: PickSource) => void } | null>(null)
/** ActionSheet 回抛 key（string）先收窄为 PickSource，未知 key 忽略，不让脏 key 进上传链路 */
function onPickImageSource(key: string) {
  if (!isPickSource(key)) return
  // 按当前类型转发：`field` 型与 `gone` 型的 ImagePicker 实例不同（两者取图上限也不同）
  const target = isGone.value ? goneFormRef.value : correctionFormRef.value
  target?.startPick(key)
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
