<template>
  <view class="page correction-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1`） -->
    <PageWallpaper fixed />
    <Header title="菜品信息纠错" @back="goBack" />

    <!-- 独立页面（与「意见反馈」互不耦合）：**只**承接「某条菜品资料有误」的专项修正。
         结构：一张白卡（圆角 16rpx）+ 菜品纠错字段区 + 提交区（随内容滚动）。
         进页即按导航参数 `dishId` 预绑定该菜品，表单内**不允许切换菜品**。 -->
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
      <view v-else class="q-card">
        <!-- 纠错字段区：预绑定菜品（只读）→ 预填全部字段 → 用户只改错的地方 -->
        <UpdateForm
          :model="form"
          :dish-name="dishName"
          :dish-location="dishLocation"
          :detail-loading="loading"
          :errors="fieldErrors"
          :submitting="submitting"
          @clear="clearError"
        />
      </view>

      <!-- 提交区（表单最下方，随内容滚动）：
           外层热区承接「置灰态点击」——AppButton 在 disabled 时不 emit press，由这里兜底 toast -->
      <view v-if="!notFound && !loadFailed" class="submit-area" @tap="onSubmitAreaTap">
        <text class="submit-note">提交后由管理员核实，确认无误后更新菜品信息</text>
        <AppButton
          :text="submitting ? '提交中…' : '提交纠错'"
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
 * correction —— 菜品信息纠错页（独立分包 pages/correction/）
 * - 入口**唯一**：菜品详情页底栏「反馈错误」（携带 dishId）⇒ 进页即拉详情预填；
 * - 提交走 `POST /dishes/{id}/correction`（**局部提交：只传改动项**，落 dish_correction 表），
 *   与 `POST /feedback` 完全分开；
 * - 编排逻辑抽包内私有 `useCorrection.ts`；包内子件：UpdateForm。
 */
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import AppButton from '@/components/AppButton.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import UpdateForm from './UpdateForm.vue'
import { useCorrection } from './useCorrection'

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
  clearError,
  canSubmit,
  onSubmitAreaTap,
  submit,
} = useCorrection()
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

/* 表单白卡：圆角 16rpx（走既有档位 `--radius-btn`）+ 标准卡阴影 */
.q-card {
  margin: var(--spacing-md) var(--spacing-md) 0;
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-btn);
  box-shadow: var(--shadow-card);
}

/* 提交区（随内容滚动，非固定） */
.submit-area {
  padding: var(--spacing-md) var(--spacing-lg) var(--spacing-lg);
}
/* 提交说明（不暗示提交即生效）：三级灰小字，只读 */
.submit-note {
  display: block;
  margin-bottom: var(--spacing-xs);
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
  line-height: 1.5;
  text-align: center;
}
</style>
