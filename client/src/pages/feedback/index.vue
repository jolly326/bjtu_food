<template>
  <view class="page feedback-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下）。
         本页底色随全局 `.page { background: var(--bg-page) }`（
         不另立页内色值 —— 与其它 10 页同口径） -->
    <PageWallpaper fixed />
    <Header title="意见反馈" @back="goBack" />

    <!-- 页面无页签：两种形态由**进入方式**决定，页面上不暴露切换入口 -->
    <scroll-view class="scroll-wrap" scroll-y :scroll-into-view="scrollIntoView" :scroll-with-animation="true">
      <!-- 单表单（「我的」页宫格进入）：反馈类型 3 选 1 + 具体描述 + 截图（≤3 张）+ 本地草稿；
           submitting 下传：表单内 ImagePicker 提交中禁选。
           极简无卡片：**不设外层大白卡**，三个区块各自一块 `.module-wrap`，提交按钮独立在模块之外。 -->
      <IssueForm
        ref="issueFormRef"
        :model="form"
        @update:model="updateForm"
        :errors="fieldErrors"
        :submitting="submitting"
        :placeholder="typePlaceholder"
        @clear="clearError"
        @pick="onPickType"
        @pick-image="pickSheetOpen = true"
      />

      <!-- 提交反馈（表单最下方，随内容滚动）：
           外层热区承接「置灰态点击」——AppButton 在 disabled 时不 emit press，由这里兜底 toast -->
      <view class="submit-area" @tap="onSubmitAreaTap">
        <!-- 处理承诺：48 小时内处理（不得暗示提交即生效） -->
        <AppButton
          :text="submitButtonText"
          :disabled="!canSubmit"
          :loading="submitting"
          @press="submit"
        />
      </view>
    </scroll-view>

    <!-- 配图来源弹层（拍照 / 从相册选择）：**必须挂在 scroll-view 之外**（小程序 scroll-view 内
         fixed 层级会被压扁/裁剪）。动作项取共享真源 imagePickSource，两处宿主页不各写一份。 -->
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
 * feedback —— 意见反馈页（**面向小程序本身的通用反馈**）
 * - 反馈类型 3 选 1（程序功能Bug / 产品功能建议 / 其他相关问题），竖排单选、选中项左侧橙色勾；
 * - 固定一套字段：具体描述（≤600 字、占位随类型选择、字数常显右上角）+ 截图（选填 ≤3 张）；
 * - 提交 `POST /feedback`（type ∈ bug / suggestion / other）；
 * - 入口：「我的」页宫格（搜索页「没搜到 → 推荐这道菜」同页复用）；
 * - 菜品资料有误 / 已经下架走**独立页面** `pages/correction/`（菜品问题反馈，入口「菜品有问题?」）；
 * - 编排逻辑抽包内私有 `useFeedback.ts`；包内子件仅 `IssueForm`。
 */
import { computed, ref } from 'vue'
import ActionSheet from '@/components/ActionSheet.vue'
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import AppButton from '@/components/AppButton.vue'
import IssueForm from './IssueForm.vue'
import { useFeedback } from './useFeedback'
import { IMAGE_PICK_ACTIONS, isPickSource, type PickSource } from '@/components/imagePickSource'

const {
  goBack,
  form,
  updateForm,
  typePlaceholder,
  onPickType,
  fieldErrors,
  scrollIntoView,
  submitting,
  clearError,
  canSubmit,
  onSubmitAreaTap,
  submit,
} = useFeedback()

/** 提交按钮文案：直白具体，不用模糊统称 */
const submitButtonText = computed(() => (submitting.value ? '提交中…' : '提交反馈'))

/* ===== 配图来源弹层（页面根级，因 scroll-view 内 fixed 层级会被裁剪）===== */
const pickSheetOpen = ref(false)
/** IssueForm 暴露的 startPick 中转（→ ImagePicker.startPick） */
const issueFormRef = ref<{ startPick: (source: PickSource) => void } | null>(null)
/** ActionSheet 回抛 key（string）先收窄为 PickSource，未知 key 忽略，不让脏 key 进上传链路 */
function onPickImageSource(key: string) {
  if (!isPickSource(key)) return
  issueFormRef.value?.startPick(key)
}
</script>

<style scoped>
/* 页面底不再声明私有底色：壁纸层（`<PageWallpaper fixed />`）铺满视口，
   底色回退到全局 `.page { background: var(--bg-page) }` */
.feedback-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; }
.feedback-page { min-height: 0; }
/* `min-height: 0` 必需：全局 `.page` 兜底写了 `min-height: 100vh / 100dvh`，而移动端
   `100vh`（最大视口）通常 **大于** `100dvh`（当前视口）；二者同时存在时 min 胜出
   ⇒ 页根比可视区高出一截 ⇒ **页面本身**多出一段可滚区（内容并未超屏也会滚）。
   自带滚动容器的页根必须把 min-height 归零，把高度交给 `height: 100dvh` + 内部 scroll-view。 */

/* 主滚动区：底部 safe-area 避让（提交区随内容滚动，无固定底栏） */
.scroll-wrap {
  flex: 1;
  min-height: 0;
  /* 滚动由 scroll-view 组件内部实现，外挂 CSS `overflow-y` 只会叠出第二根滚动条 */
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: border-box;
}

/* ===== 表单容器（极简无卡片）=====
   不再有大白卡把三段内容框在一起：区块自身是 `.module-wrap`，区块之间靠页面留白分组
   （`.fb-form` 的上下留白与左右 gutter 见 `IssueForm.vue`；模块间距由本表单容器的 flex gap 承担）。 */

/* ===== 提交反馈（**不放进任何模块容器**，直接位于页面底部） ===== */
.submit-area {
  /* 顶部大留白把按钮与表单模块在视觉上分开；左右 --page-gutter 与模块边线同轴 */
  padding: var(--spacing-xl) var(--page-gutter) var(--spacing-lg);
}
</style>
