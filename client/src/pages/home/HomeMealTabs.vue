<template>
  <!-- 首页横向「菜品大类」标签栏（2026-09-21 §7.34 / home-page-presentation）：
       横向可滑动 + 单选 + 橙色短下划线高亮。
       ⚠️ 标签集合与文案**完全**来自字典响应（`GET /dishes/meal-types`）——
       本组件不维护任何大类中文映射；唯一由端上渲染的固定项是第一项「全部」（= 不传 mealType）。
       字典不可用（未加载 / 加载失败 / 返回空数组）时降级为仅「全部」，列表仍展示全部菜品。 -->
  <view class="mt-bar">
    <scroll-view
      class="mt-scroll"
      scroll-x
      :scroll-into-view="scrollIntoId"
      :scroll-with-animation="true"
    >
      <view class="mt-track">
        <view
          v-for="tab in tabs"
          :key="tab.key ?? 'all'"
          :id="idOf(tab.key)"
          class="mt-tab"
          :class="{ active: tab.key === activeKey }"
          role="button"
          :aria-label="`筛选大类：${tab.label}`"
          hover-class="mt-tab-pressed"
          @tap="onSelect(tab.key)"
        >
          <text class="mt-label">{{ tab.label }}</text>
          <!-- 选中态橙色短下划线：常驻节点 + 透明度切换（避免显隐引起行高跳动）；
               纯装饰（选中语义已由 .active 字重与 aria-label 表达），对读屏隐藏 -->
          <view class="mt-underline" :class="{ show: tab.key === activeKey }" aria-hidden="true" />
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MealType } from '@/types/dish'

/** 标签项：`key === null` 表示端上固定的第一项「全部」（不传 mealType） */
interface MealTab {
  key: string | null
  label: string
}

const props = defineProps<{
  /** 大类字典（`store.mealTypeList`，后端已按 order 升序）；空数组合法 = 降级为仅「全部」 */
  items: MealType[]
  /** 当前选中大类键（null = 全部） */
  activeKey: string | null
}>()

const emit = defineEmits<{
  (e: 'select', key: string | null): void
}>()

/**
 * 渲染项 = 端上固定「全部」+ 字典响应**原序**展开（不再排序 / 不再过滤；
 * 空类隐藏由后端完成，端上不做二次判断，保证「一处真源」）。
 */
const tabs = computed<MealTab[]>(() => [
  { key: null, label: '全部' },
  ...props.items.map((item) => ({ key: item.key, label: item.label })),
])

/** 选中项滚动入视口（横向标签超过一屏时，切换后仍能看到高亮项） */
const scrollIntoId = computed(() => (props.activeKey ? idOf(props.activeKey) : 'mt-tab-all'))

/** 稳定 id：小程序 `scroll-into-view` 要求 id 以字母开头、且不含特殊字符 */
function idOf(key: string | null): string {
  return key ? `mt-tab-${key}` : 'mt-tab-all'
}

function onSelect(key: string | null) {
  // 点击已选中项不重复发请求（避免无谓的列表重置与闪烁）
  if (key === props.activeKey) return
  emit('select', key)
}
</script>

<style scoped lang="scss">
/* 标签栏落在页面渐变底色区（与上方白色搜索卡在明度上可区分，见 client-visual-language）。
   自身上下 padding **归零**（2026-09-22 间距收口）：与搜索区、与网格的间距各由
   `.mt-tab` 的行内上偏置 / 吸顶容器 padding 单独承担 —— 两处叠加会把间隙撑到 20px+。 */
.mt-bar {
  width: 100%;
  box-sizing: border-box;
  padding: 0;
}
.mt-scroll {
  width: 100%;
  /* 横向滚动：不换行、不出滚动条（红线：页面不得出现横向滚动条） */
  white-space: nowrap;
}
.mt-scroll ::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}
/* 轨道：block + nowrap，子项走 inline-flex —— 小程序 scroll-x 下最稳的横排写法
   （避免 flex 轨道被 scroll-view 收缩导致标签塌成一列 / 不可滑动）。
   ⚠️ 轨道**不留左右内边距**（padding: 0）：若在轨道上再加 --spacing-md，会与 .mt-tab 自身
   的 24rpx 叠加成 48rpx，首项文字缘就与页面级 gutter（24rpx）不同轴。
   左右 inset 统一由 .mt-tab 的 padding 独立承担 → 首/末项文字缘 = 24rpx = 页面 gutter。 */
.mt-track {
  display: block;
  white-space: nowrap;
  padding: 0;
  box-sizing: border-box;
}
.mt-tab {
  /* inline-flex 让每项留在 nowrap 行内，同时内部可纵向堆叠「文字 + 下划线」 */
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  /* 文字**行内上偏置**（§2 光学间距，2026-09-22 修订）：行内偏置 24rpx 即「搜索区 → 标签文字」
     间距的**全部来源**（≈12px，`.mt-bar` 已不再补 padding）；「下划线 → 卡片首行」由吸顶容器
     padding-bottom + 本行行底余量共同构成（≈16px）。两侧都较旧口径（20 / 24）收紧，
     仍保持「下行距 ≥ 上行距」，分组感不丢。
     ⚠️ 下划线不得吸到行底（margin-top:auto）——那会让它离文字 ≈16px、与标签脱开。 */
  justify-content: flex-start;
  /* 命中区：高 88rpx（触达下限，不得压低；= 上偏置 24 + 文字行 ≈34 + 下划线位 14 + 行底余量）；
     宽 = 标签文字 + 左右各 24rpx。最短标签「全部」（2 字 × --font-body 28rpx = 56rpx）+ 48rpx = 104rpx ≈ 52px ≥ 44px ✅。 */
  height: 88rpx;
  padding: 24rpx var(--spacing-md) 0;
  box-sizing: border-box;
  vertical-align: bottom;
  -webkit-tap-highlight-color: transparent;
}
/* 按压反馈：小程序端统一「透明度微降」（§4.9：废止 transform: scale 按压） */
.mt-tab-pressed {
  opacity: 0.6;
}
.mt-label {
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  /* 未选中 = 黑色常规字 */
  color: var(--text-primary);
  line-height: 1.2;
}
.mt-tab.active .mt-label {
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
/* 橙色短下划线：宽度固定（≈文字宽的短横），选中显现；
   取「图形档亮橙」——用户走查反馈 #C2410C 偏红，#EA580C 为明确橙色（图形级 ≥3:1 达标） */
.mt-underline {
  width: 40rpx;
  height: 6rpx;
  margin-top: var(--spacing-xs);
  border-radius: var(--radius-pill);
  background: var(--color-primary-bright);
  opacity: 0;
  transition: opacity var(--duration-base) var(--ease-out);
}
.mt-underline.show {
  opacity: 1;
}

@media (prefers-reduced-motion: reduce) {
  .mt-underline { transition: none; }
}
</style>
