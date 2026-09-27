<template>
  <view class="page feedback-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下）。
         本页底色随全局 `.page { background: var(--bg-page) }`（UI 统一 Loop Round 1：
         原先私有的 `--bg-warm` 是全项目唯一消费点，且已被壁纸层完全覆盖 ⇒ 移除，与其它 10 页一致） -->
    <PageWallpaper fixed />
    <Header title="意见反馈" @back="goBack" />

    <!-- 页面无页签（2026-09-27 改版）：两种形态由**进入方式**决定，页面上不暴露切换入口 -->
    <scroll-view class="scroll-wrap" scroll-y :scroll-into-view="scrollIntoView" :scroll-with-animation="true">
      <view class="q-card">
        <!-- 单表单（默认：「我的」页宫格进入）—— 反馈类型 4 选 1 + 具体描述 + 截图（≤1 张）+ 本地草稿；
             纠错表单（`mode=update`：菜品详情底栏「反馈错误」带 dishId 跳入）—— 自动填好这道菜，只改差异项。
             submitting 下传：表单内 ImagePicker 提交中禁选（评审 m1 口径沿用） -->
        <IssueForm
          :model="form"
          :errors="fieldErrors"
          :submitting="submitting"
          :placeholder="typePlaceholder"
          @clear="clearError"
          @pick="onPickType"
        />

        <!-- 类型 = 「菜品信息纠错」⇒ 字段区换成**预填纠错表单**（用户口径 v2）：
             选菜 → 拉详情预填（名称 / 价格 / 食堂 / 档口 / 口味 / 食材 / 图片）→ 只改错的地方提交 -->
        <UpdateForm
          v-if="isCorrection"
          :model="update"
          :detail-loading="update.detailLoading"
          :errors="fieldErrors"
          :submitting="submitting"
          @clear="clearError"
          @open-dish="openDishSheet"
          @reset-dish="resetDish"
        />
      </view>

      <!-- 提交反馈（表单最下方，随内容滚动）：
           外层热区承接「置灰态点击」——AppButton 在 disabled 时不 emit press，由这里兜底 toast -->
      <view class="submit-area" @tap="onSubmitAreaTap">
        <!-- 处理承诺：issue 沿用 48 小时口径；update 强调管理员核实后更新（不得暗示提交即生效） -->
        <AppButton
          :text="submitButtonText"
          :disabled="!canSubmit"
          :loading="submitting"
          @press="submit"
        />
      </view>
    </scroll-view>

    <!-- 纠错字段区的菜品选择弹层（仅类型 = 菜品信息纠错时可用） -->
    <ListPickerSheet
      :open="dishSheetOpen"
      title="选择菜品"
      searchable
      search-placeholder="搜菜名"
      :search-initial="dishKeyword"
      :options="dishPickerOptions"
      @close="closeDishSheet"
      @search="onDishSearchKw"
      @select="onDishPick"
    >
      <template #empty>
        <EmptyState
          v-if="dishKeyword && dishSearched && !dishPickerOptions.length"
          :title="`没搜到「${dishKeyword}」，换个关键词试试`"
        />
        <EmptyState v-else-if="!dishKeyword" title="输入关键词搜索菜品" />
      </template>
    </ListPickerSheet>
  </view>
</template>

<script setup lang="ts">
/**
 * feedback —— 意见反馈页（**一张表单、无页签**，字段区随所选类型切换，2026-09-27 用户口径 v2）
 * - 反馈类型 4 选 1（竖排单选，选中项左侧橙色勾）；
 * - 类型 ≠ 「菜品信息纠错」⇒ 具体描述（≤600 字、占位随类型切换、字数常显右上角）+ 截图（选填 ≤1 张）；
 *   类型 = 「菜品信息纠错」⇒ **预填纠错表单**（选菜 → 详情预填 → 只改错的地方，提交纠错端点）；
 * - 提交 `POST /feedback`（type ∈ bug/suggestion/error/other）或 `POST /dishes/{id}/correction`；
 * - 进入方式：「我的」页宫格 ⇒ 类型待用户选；菜品详情页「反馈错误」⇒ 类型默认「菜品信息纠错」**并按 dishId 自动预填**；
 * - 编排逻辑抽包内私有 `useFeedback.ts`；包内子件：IssueForm / UpdateForm / ListPickerSheet（一级拆分）。
 */
import { computed } from 'vue'
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import AppButton from '@/components/AppButton.vue'
import IssueForm from './IssueForm.vue'
import UpdateForm from './UpdateForm.vue'
import ListPickerSheet from './ListPickerSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useFeedback } from './useFeedback'

const {
  goBack,
  form,
  isCorrection,
  typePlaceholder,
  onPickType,
  update,
  dishSheetOpen,
  dishKeyword,
  dishPickerOptions,
  dishSearched,
  openDishSheet,
  closeDishSheet,
  onDishSearchKw,
  onDishPick,
  resetDish,
  fieldErrors,
  scrollIntoView,
  submitting,
  clearError,
  canSubmit,
  gateHint,
  onSubmitAreaTap,
  submit,
} = useFeedback()

/** 提交按钮文案：直白具体，不用模糊统称 */
const submitButtonText = computed(() => (submitting.value ? '提交中…' : '提交反馈'))

/* 提交区说明行已删除（2026-09-27 改版）：说明改为表单内小字「提交内容将由项目维护者查看」；
   原「48 小时内处理 + 站内通知」属对外承诺，随本轮文案降级一并撤下（用户拍板）。 */
</script>

<style scoped>
/* 页面底不再声明私有底色：壁纸层（`<PageWallpaper fixed />`）铺满视口，
   底色回退到全局 `.page { background: var(--bg-page) }`（UI 统一 Loop Round 1） */
.feedback-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }

/* 主滚动区：底部 safe-area 避让（提交区随内容滚动，无固定底栏） */
.scroll-wrap {
  flex: 1;
  min-height: 0;
  /* Round 26：去掉 `overflow-y: auto` —— 本容器是 `scroll-view`，滚动由组件内部实现，
     外挂 CSS 只会在 H5 叠出第二根滚动条（"多余滚动机制"）。 */
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: border-box;
}

/* 顶部双模式分段控件样式已随「无页签」改版退役（2026-09-27）——
   现「反馈类型 4 选 1」的竖排单选视觉在 IssueForm 内（.type-row / .type-check）。 */

/* ===== 表单外层 Q 卡（大圆角 + 标准卡阴影，内部模块靠间距分层）=====
   UI 统一 Loop Round 13（裁决 5A）：卡片阴影一律 `shadow-card`；
   `shadow-warm` 仅保留给**选中 / 强调**态（段控件选中、未读通知卡）。 */
.q-card {
  margin: var(--spacing-md) var(--spacing-md) 0;
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
}

/* 列表空态已上提为公共组件 components/EmptyState.vue（UI 统一 Loop Round 3），此处不再保留副本 */

/* ===== 提交反馈（表单最下方，随内容滚动，非固定） ===== */
.submit-area {
  padding: var(--spacing-md) var(--spacing-lg) var(--spacing-lg);
}
/* 提交区说明行样式已随该行删除退役（2026-09-27）—— 说明改为表单内小字 `.form-note`（IssueForm 内）。 */
</style>
