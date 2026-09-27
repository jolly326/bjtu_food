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
          v-if="!isUpdateMode"
          :model="form"
          :errors="fieldErrors"
          :submitting="submitting"
          :placeholder="typePlaceholder"
          @clear="clearError"
          @pick="onPickType"
        />
        <UpdateForm
          v-else
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
        <text v-if="submitNote" class="submit-note">{{ submitNote }}</text>
        <AppButton
          :text="submitButtonText"
          :disabled="!canSubmit"
          :loading="submitting"
          @press="submit"
        />
      </view>
    </scroll-view>

    <!-- ===== 底部选择器：菜品（搜索 + 简洁结果列表：名称 + 档口） ===== -->
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
      <!-- 列表区内空态：无关键词引导 / 无结果提示 -->
      <template #empty>
        <!-- 统一空态组件（UI 统一 Loop Round 3）：不再本页手写 `.pick-empty` -->
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
 * feedback —— 意见反馈页
 * - 默认（「我的」页宫格进入）：**单表单** —— 反馈类型 4 选 1（竖排单选）+ 具体描述（占位随类型切换）+ 截图（≤1 张）
 *   + 本地草稿（仅类型与描述）；提交 `POST /feedback`（type ∈ bug/suggestion/error/other）；
 * - `mode=update`（菜品详情**底栏「反馈错误」**带 dishId 跳入）：自动填好这道菜的纠错表单，提交纠错端点；
 * - 页面**无页签、无模式切换入口**（形态由进入方式决定，2026-09-27 改版）；
 * - 编排逻辑抽包内私有 `useFeedback.ts`；包内子件：IssueForm / UpdateForm / ListPickerSheet（一级拆分）；
 * - 本文件仅保留模板贴片组装与子件引用；生命周期 / 提交门禁 / 弹层联动见 useFeedback。
 */
import { computed } from 'vue'
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import AppButton from '@/components/AppButton.vue'
import ListPickerSheet from './ListPickerSheet.vue'
import EmptyState from '@/components/EmptyState.vue'
import IssueForm from './IssueForm.vue'
import UpdateForm from './UpdateForm.vue'
import { useFeedback } from './useFeedback'

const {
  goBack,
  isUpdateMode,
  form,
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

/** 提交按钮文案：直白具体（单表单「提交反馈」/ 纠错形态「提交更新」），不用模糊统称 */
const submitButtonText = computed(() =>
  submitting.value ? '提交中…' : isUpdateMode.value ? '提交更新' : '提交反馈',
)

/**
 * 提交区说明行：**仅纠错形态**保留（不得暗示提交即生效）。
 * 单表单形态的说明已改到表单内小字「提交内容将由项目维护者查看」（2026-09-27 改版）——
 * 原「48 小时内处理 + 站内通知」属对外承诺，随本轮文案降级一并撤下（用户拍板）。
 */
const submitNote = computed(() =>
  isUpdateMode.value ? '提交后由管理员核实，确认无误后更新菜品信息' : '',
)
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
/* 提交区说明行（处理承诺 / 核实说明）：三级灰小字，只读，不参与交互 */
.submit-note {
  display: block;
  margin-bottom: var(--spacing-xs);
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
  line-height: 1.5;
  text-align: center;
}
</style>
