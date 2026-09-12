<template>
  <view class="page feedback-page">
    <Header title="意见反馈" @back="goBack" />

    <!-- 轻量单视图 · 动态表单（三类型等宽卡片 + 结构化字段） -->
    <scroll-view class="scroll-wrap" scroll-y :scroll-into-view="scrollIntoView" :scroll-with-animation="true">
      <!-- 类型图标卡片（三列等宽：左侧 icon + 右侧一行标题） -->
      <view class="type-row">
        <view
          v-for="t in types"
          :key="t.value"
          class="type-card"
          :class="{ active: type === t.value }"
          hover-class="pressed"
          hover-stay-time="80"
          role="radio"
          :aria-checked="type === t.value"
          :aria-label="t.label"
          @tap="type = t.value"
        >
          <view class="type-icon">
            <IconSvg :name="t.icon" :size="36" :color="type === t.value ? 'var(--color-primary)' : 'var(--text-tertiary)'" />
          </view>
          <view class="type-copy">
            <text class="type-line">{{ t.label }}</text>
            <text v-if="type === t.value" class="type-desc">{{ t.desc }}</text>
          </view>
        </view>
      </view>

      <!-- 动态字段区 -->
      <view class="q-card">
        <!-- 三套表单字段区：由包内私有组件承载（feedback-form-component-split，一级拆分） -->
        <SuggestionForm
          v-if="type === 'suggestion'"
          :model="form.suggestion"
          :errors="fieldErrors"
          @clear="clearError"
        />
        <AddForm
          v-else-if="type === 'add'"
          :model="form.add"
          :errors="fieldErrors"
          @clear="clearError"
          @open-location="openLocationSheet"
          @open-floor="openFloorSheet"
          @stall-tap="onStallRowTap"
        />
        <ErrorForm
          v-else-if="type === 'error'"
          :model="form.error"
          :points="correctionPoints"
          :errors="fieldErrors"
          @clear="clearError"
          @open-dish="openDishSheet"
          @reset-dish="resetDish"
          @toggle="togglePoint"
        />
      </view>

      <!-- 提交反馈（表单最下方，随内容滚动）：
           外层热区承接「置灰态点击」——AppButton 在 disabled 时不 emit press，由这里兜底 toast（仿 publish-moment） -->
      <view class="submit-area" @tap="onSubmitAreaTap">
        <AppButton
          :text="submitting ? '提交中…' : '提交反馈'"
          :disabled="!canSubmit"
          :loading="submitting"
          @press="submit"
        />
      </view>
    </scroll-view>

    <!-- ===== 底部选择器：位置（食堂 → 档口 两级联动，ListPickerSheet 单实例 locStep 切换） ===== -->
    <ListPickerSheet
      :open="locSheetOpen"
      :title="locStep === 'canteen' ? '选择食堂' : '选择档口'"
      :backable="locStep === 'stall'"
      :options="locOptions"
      row-style="plain"
      :selected-key="locSelectedKey"
      @close="closeLocationSheet"
      @back="locStep = 'canteen'"
      @select="onLocSelect"
    >
      <!-- 尾部「其他」自定义输入（仅选中「其他」时由默认槽承载） -->
      <view v-if="locCustomShown" class="pick-custom">
        <input
          :value="locCustomValue"
          class="pick-custom-input"
          :placeholder="locStep === 'canteen' ? '写一下食堂名' : '写一下档口名'"
          maxlength="50"
          :cursor-spacing="40"
          :adjust-position="true"
          @input="onLocCustomInput"
        />
      </view>
    </ListPickerSheet>

    <!-- ===== 底部选择器：楼层（1/2/3） ===== -->
    <ListPickerSheet
      :open="floorSheetOpen"
      title="选择楼层"
      :options="floorPickerOptions"
      row-style="plain"
      :selected-key="form.add.floor || null"
      @close="closeFloorSheet"
      @select="onFloorSelect"
    />

    <!-- ===== 底部选择器：菜品（搜索 + 列表 + 空态去补录） ===== -->
    <ListPickerSheet
      :open="dishSheetOpen"
      title="选择菜品"
      searchable
      search-placeholder="搜菜名 / 食堂"
      :search-initial="dishKeyword"
      :options="dishPickerOptions"
      row-style="plain"
      @close="closeDishSheet"
      @search="onDishSearchKw"
      @select="onDishPick"
    >
      <!-- 列表区内空态：无关键词引导 / 无结果「去补录一道」CTA -->
      <template #empty>
        <view v-if="dishSearched && !dishPickerOptions.length" class="pick-empty">
          <text class="pick-empty-text">没搜到「{{ dishKeyword }}」</text>
          <view
            class="pick-goto-add"
            hover-class="pressed"
            hover-stay-time="80"
            role="button"
            aria-label="去推荐菜品补录"
            @tap="gotoAdd"
          ><text class="pick-goto-add-text">去补录一道</text></view>
        </view>
        <view v-else-if="!dishKeyword" class="pick-empty">
          <text class="pick-empty-text">输入关键词搜索菜品</text>
        </view>
        <!-- 搜索进行中（防抖未回）/ 已有关键词但无结果外：留空 -->
      </template>
    </ListPickerSheet>
  </view>
</template>

<script setup lang="ts">
/**
 * feedback —— 意见反馈页（页面入口：mine 宫格；首页「反馈菜品」入口带 object/name/id 预选）
 * - 三类型（suggestion/add/error）单视图动态切换，编排逻辑抽包内私有 `useFeedback.ts`；
 *   包内子件：SuggestionForm / AddForm / ErrorForm / useFeedback（一级拆分/就近组织）。
 * - 本文件仅保留模板贴片组装与子件引用；生命周期 / 提交门禁 / 弹层联动见 useFeedback。
 */
import Header from '@/components/AppHeader.vue'
import AppButton from '@/components/AppButton.vue'
import IconSvg from '@/components/IconSvg.vue'
import ListPickerSheet from '@/components/ListPickerSheet.vue'
import SuggestionForm from './SuggestionForm.vue'
import AddForm from './AddForm.vue'
import ErrorForm from './ErrorForm.vue'
import { useFeedback } from './useFeedback'

const {
  goBack,
  types,
  type,
  form,
  canSubmit,
  gateHint,
  onSubmitAreaTap,
  dishSheetOpen,
  dishKeyword,
  dishPickerOptions,
  dishSearched,
  openDishSheet,
  closeDishSheet,
  onDishSearchKw,
  onDishPick,
  gotoAdd,
  resetDish,
  correctionPoints,
  togglePoint,
  locSheetOpen,
  locStep,
  locOptions,
  locSelectedKey,
  locCustomShown,
  locCustomValue,
  openLocationSheet,
  closeLocationSheet,
  onLocCustomInput,
  onLocSelect,
  onStallRowTap,
  floorSheetOpen,
  floorPickerOptions,
  openFloorSheet,
  closeFloorSheet,
  onFloorSelect,
  fieldErrors,
  scrollIntoView,
  submitting,
  clearError,
  submit,
} = useFeedback()
</script>

<style scoped>
/* Q 版暖调：页面底用奶油米白 --bg-warm（feedback-forms-ux-polish） */
.feedback-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-warm); }

/* 主滚动区：底部预留固定底栏高度 + safe-area（防遮挡） */
.scroll-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  /* 底部安全区避让：滚动到底时不遮挡提交区 */
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: border-box;
}

/* ===== 顶部类型入口：三枚等宽大胶囊（Q 版满圆；选中浅红底主色，未选中白底浅灰细边） ===== */
.type-row {
  display: flex;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-lg) var(--spacing-xs);
}
.type-card {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2xs);
  padding: var(--spacing-sm) var(--spacing-xs);
  background: var(--bg-card);
  border: 2rpx solid var(--border-color);
  border-radius: var(--radius-pill);
  box-shadow: var(--shadow-warm);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.type-card.active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
}
.type-icon {
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
  border-radius: var(--radius-circle);
  background: var(--bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.type-card.active .type-icon { background: var(--bg-card); }
.type-copy { flex: 0 0 auto; display: flex; flex-direction: column; align-items: center; gap: var(--spacing-2xs); }
.type-line { font-size: var(--font-small); font-weight: var(--weight-semibold); color: var(--text-primary); line-height: 1.3; white-space: nowrap; }
.type-card.active .type-line { color: var(--color-primary); }
.type-desc { font-size: var(--font-tiny); color: var(--color-primary); line-height: 1.3; }

/* ===== 表单外层 Q 卡（替代 CardSection 观感：大圆角 + 暖调柔和阴影，内部模块靠间距分层） ===== */
.q-card {
  margin: var(--spacing-xs) var(--spacing-md) 0;
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-warm);
}

/* ===== ListPickerSheet 尾部「其他」自定义输入（默认槽承载） ===== */
.pick-custom { padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md); box-sizing: border-box; }
.pick-custom-input {
  width: 100%;
  height: 76rpx;
  background: var(--bg-input);
  border-radius: var(--radius-icon);
  padding: 0 var(--spacing-md);
  font-size: var(--font-body);
  color: var(--text-primary);
  box-sizing: border-box;
  border: 2rpx solid var(--color-primary);
}

/* ===== ListPickerSheet 列表空态（菜品搜索引导 / 无结果去补录 CTA） ===== */
.pick-empty { display: flex; flex-direction: column; align-items: center; gap: var(--spacing-md); }
.pick-empty-text { font-size: var(--font-aux); color: var(--text-tertiary); }
.pick-goto-add {
  min-width: 200rpx;
  height: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--spacing-lg);
  background: var(--color-primary);
  border-radius: var(--radius-pill);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.pick-goto-add-text { font-size: var(--font-small); color: var(--bg-card); font-weight: var(--weight-semibold); }

/* ===== 提交反馈（表单最下方，随内容滚动，非固定） ===== */
.submit-area {
  padding: var(--spacing-md) var(--spacing-lg) var(--spacing-lg);
}
/* 滚动区底部留白（配合固定底栏） */


</style>
