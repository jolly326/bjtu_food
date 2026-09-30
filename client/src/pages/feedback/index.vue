<template>
  <view class="page feedback-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下）。
         本页底色随全局 `.page { background: var(--bg-page) }`（
         原先私有的 `--bg-warm` 是全项目唯一消费点，且已被壁纸层完全覆盖 ⇒ 移除，与其它 10 页一致） -->
    <PageWallpaper fixed />
    <Header title="意见反馈" @back="goBack" />

    <!-- 页面无页签：两种形态由**进入方式**决定，页面上不暴露切换入口 -->
    <scroll-view class="scroll-wrap" scroll-y :scroll-into-view="scrollIntoView" :scroll-with-animation="true">
      <view class="q-card">
        <!-- 单表单（默认：「我的」页宫格进入）—— 反馈类型 3 选 1 + 具体描述 + 截图（≤3 张）+ 本地草稿；
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
  </view>
</template>

<script setup lang="ts">
/**
 * feedback —— 意见反馈页（**面向小程序本身的通用反馈**，与菜品纠错解耦）
 * - 反馈类型 3 选 1（程序功能Bug / 产品功能建议 / 其他相关问题），竖排单选、选中项左侧橙色勾；
 * - 固定一套字段：具体描述（≤600 字、占位随类型切换、字数常显右上角）+ 截图（选填 ≤1 张）—— **不再有第二套表单**；
 * - 提交 `POST /feedback`（type ∈ bug / suggestion / other）；
 * - 入口：「我的」页宫格（搜索页「没搜到 → 推荐这道菜」同页复用）；
 * - **菜品纠错已迁出为独立页面** `pages/correction/`（仅菜品详情页底栏「反馈错误」进入）；
 * - 编排逻辑抽包内私有 `useFeedback.ts`；包内子件仅 `IssueForm`。
 */
import { computed } from 'vue'
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import AppButton from '@/components/AppButton.vue'
import IssueForm from './IssueForm.vue'
import { useFeedback } from './useFeedback'

const {
  goBack,
  form,
  typePlaceholder,
  onPickType,
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
</script>

<style scoped>
/* 页面底不再声明私有底色：壁纸层（`<PageWallpaper fixed />`）铺满视口，
   底色回退到全局 `.page { background: var(--bg-page) }` */
.feedback-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }

/* 主滚动区：底部 safe-area 避让（提交区随内容滚动，无固定底栏） */
.scroll-wrap {
  flex: 1;
  min-height: 0;
  /* 滚动由 scroll-view 组件内部实现，外挂 CSS `overflow-y` 只会叠出第二根滚动条 */
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: border-box;
}

/* ===== 表单外层 Q 卡（大圆角 + 标准卡阴影，内部模块靠间距分层）=====
   卡片阴影一律 `shadow-card`；`shadow-warm` 仅保留给**选中 / 强调**态。 */
.q-card {
  margin: var(--spacing-md) var(--spacing-md) 0;
  padding: var(--spacing-lg);
  background: var(--bg-card);
  /* 大卡片圆角 = 16rpx（用户口径 v3；走既有圆角档 `--radius-btn` = 16rpx，不新立 token） */
  border-radius: var(--radius-btn);
  box-shadow: var(--shadow-card);
}

/* ===== 提交反馈（表单最下方，随内容滚动，非固定） ===== */
.submit-area {
  padding: var(--spacing-md) var(--spacing-lg) var(--spacing-lg);
}
</style>
