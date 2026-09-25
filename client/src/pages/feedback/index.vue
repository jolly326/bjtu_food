<template>
  <view class="page feedback-page">
    <Header title="意见反馈" @back="goBack" />

    <!-- 顶部双模式切换：分段控件，两段等宽 -->
    <view class="seg-wrap">
      <view class="seg" role="tablist" aria-label="反馈模式切换">
        <view
          v-for="m in modes"
          :key="m.value"
          class="seg-item"
          :class="{ active: mode === m.value }"
          hover-class="seg-pressed"
          hover-stay-time="80"
          role="tab"
          :aria-selected="mode === m.value ? 'true' : 'false'"
          :aria-label="m.label"
          @tap="mode = m.value"
        >
          <text class="seg-text">{{ m.label }}</text>
        </view>
      </view>
    </view>

    <!-- 轻量单视图 · 动态表单（两模式各自独立子树，切换互不清空、保留各自草稿） -->
    <scroll-view class="scroll-wrap" scroll-y :scroll-into-view="scrollIntoView" :scroll-with-animation="true">
      <view class="q-card">
        <!-- 两套表单字段区：由包内私有组件承载（一级拆分，就近组织）；
             submitting 下传：表单内 ImagePicker 提交中禁选（评审 m1 口径沿用） -->
        <IssueForm
          v-if="mode === 'issue'"
          :model="issue"
          :errors="fieldErrors"
          :submitting="submitting"
          @clear="clearError"
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
        <text class="submit-note">{{ submitNote }}</text>
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
      row-style="plain"
      @close="closeDishSheet"
      @search="onDishSearchKw"
      @select="onDishPick"
    >
      <!-- 列表区内空态：无关键词引导 / 无结果提示 -->
      <template #empty>
        <view v-if="dishKeyword && dishSearched && !dishPickerOptions.length" class="pick-empty">
          <text class="pick-empty-text">没搜到「{{ dishKeyword }}」，换个关键词试试</text>
        </view>
        <view v-else-if="!dishKeyword" class="pick-empty">
          <text class="pick-empty-text">输入关键词搜索菜品</text>
        </view>
      </template>
    </ListPickerSheet>
  </view>
</template>

<script setup lang="ts">
/**
 * feedback —— 意见反馈页（入口：mine 宫格缺省 issue / 菜品详情「信息有误？」带 update+dishId）
 * - 顶部双模式分段控件（issue 反馈问题 / update 更新信息），两段等宽；
 * - 编排逻辑抽包内私有 `useFeedback.ts`；包内子件：IssueForm / UpdateForm / ListPickerSheet（一级拆分）；
 * - 本文件仅保留模板贴片组装与子件引用；生命周期 / 提交门禁 / 弹层联动见 useFeedback。
 */
import { computed } from 'vue'
import Header from '@/components/AppHeader.vue'
import AppButton from '@/components/AppButton.vue'
import ListPickerSheet from './ListPickerSheet.vue'
import IssueForm from './IssueForm.vue'
import UpdateForm from './UpdateForm.vue'
import { useFeedback } from './useFeedback'

const {
  goBack,
  modes,
  mode,
  issue,
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

/** 提交按钮文案：直白具体（issue 提交反馈 / update 提交更新），不用模糊统称 */
const submitButtonText = computed(() =>
  submitting.value ? '提交中…' : mode.value === 'issue' ? '提交反馈' : '提交更新',
)

/** 处理说明：按模式区分（spec 反馈处理预期口径；update 不得暗示提交即生效） */
const submitNote = computed(() =>
  mode.value === 'issue'
    ? '我们会在 48 小时内处理你的反馈，处理结果将通过站内通知告知'
    : '提交后由管理员核实，确认无误后更新菜品信息',
)
</script>

<style scoped>
/* Q 版暖调：页面底用奶油米白 --bg-warm（沿用 feedback-forms-ux-polish） */
.feedback-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-warm); }

/* 主滚动区：底部 safe-area 避让（提交区随内容滚动，无固定底栏） */
.scroll-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: border-box;
}

/* ===== 顶部双模式分段控件：等宽两段（轨道浅底 + 选中白卡浮起，iOS 分段观感） ===== */
.seg-wrap { padding: var(--spacing-md) var(--spacing-md) var(--spacing-xs); }
.seg {
  display: flex;
  gap: var(--spacing-2xs);
  padding: var(--spacing-2xs);
  background: var(--bg-soft);
  border-radius: var(--radius-pill);
}
.seg-item {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  height: 76rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-pill);
  -webkit-tap-highlight-color: transparent;
  transition: background var(--duration-fast) var(--ease-out);
}
.seg-item.active {
  background: var(--bg-card);
  box-shadow: var(--shadow-warm);
}
/* 按压反馈：统一 opacity（与全站按压语言一致）；选中态按下保持白卡不翻灰 */
.seg-pressed { opacity: 0.7; }
.seg-text { font-size: var(--font-body); font-weight: var(--weight-medium); color: var(--text-secondary); }
.seg-item.active .seg-text { color: var(--color-primary-text); font-weight: var(--weight-semibold); }

/* ===== 表单外层 Q 卡（大圆角 + 暖调柔和阴影，内部模块靠间距分层） ===== */
.q-card {
  margin: var(--spacing-xs) var(--spacing-md) 0;
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-warm);
}

/* ===== ListPickerSheet 列表空态 ===== */
.pick-empty { display: flex; flex-direction: column; align-items: center; gap: var(--spacing-md); }
.pick-empty-text { font-size: var(--font-aux); color: var(--text-tertiary); }

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
