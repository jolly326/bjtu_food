<template>
  <!-- 首页横向「菜品大类」标签栏（§7.34 / 方案 B 运营化解耦）：
       横向可滑动 + 单选 + 橙色短下划线高亮。
       ⚠️ 标签集合与文案**完全由后端下发直出**（`GET /dishes/meal-types`）——
       首项固定为后端下发的「为你推荐」（value 为 null，对应不传 mealType 拉取推荐流）；
       端上不再前置硬编码拼接「全部」，彻底实现端上零硬编码。
       字典不可用（未加载 / 失败）时由 store 回退仅「为你推荐」，列表仍展示推荐菜品。 -->
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
          :key="tab.value ?? 'recommend'"
          :id="idOf(tab.value)"
          class="mt-tab"
          :class="{ active: tab.value === activeValue }"
          role="button"
          :aria-label="`筛选大类：${tab.label}`"
          hover-class="mt-tab-pressed"
          @tap="onSelect(tab.value)"
        >
          <text class="mt-label">{{ tab.label }}</text>
          <!-- 选中态橙色短下划线：常驻节点 + opacity 切换（避免显隐引起行高跳动）；
               纯装饰（选中语义已由 .active 字重与 aria-label 表达），对读屏隐藏 -->
          <view class="mt-underline" :class="{ show: tab.value === activeValue }" aria-hidden="true" />
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MealType } from '@/types/dish'

/** 标签项：`value === null` 表示首项「为你推荐」（不传 mealType） */
interface MealTab {
  value: string | null
  label: string
}

const props = defineProps<{
  /** 大类字典（`store.mealTypeList`，后端已包含首项「为你推荐」及在售大类） */
  items: MealType[]
  /** 当前选中大类值（null = 为你推荐） */
  activeValue: string | null
}>()

const emit = defineEmits<{
  (e: 'select', value: string | null): void
}>()

/**
 * 方案 B：渲染项完全直出后端响应（不再在前端前置写入「全部」）。
 * 兜底守卫：若 items 尚未加载完成或异常为空，回退单项「为你推荐」。
 */
const tabs = computed<MealTab[]>(() => {
  if (!props.items || props.items.length === 0) {
    return [{ value: null, label: '为你推荐' }]
  }
  return props.items.map((item) => ({ value: item.value, label: item.label }))
})

/** 选中项滚动入视口（横向标签超过一屏时，切换后仍能看到高亮项） */
const scrollIntoId = computed(() => (props.activeValue ? idOf(props.activeValue) : 'mt-tab-recommend'))

/** 稳定 id：小程序 `scroll-into-view` 要求 id 以字母开头、且不含特殊字符 */
function idOf(value: string | null): string {
  return value ? `mt-tab-${value}` : 'mt-tab-recommend'
}

function onSelect(value: string | null) {
  // 点击已选中项不重复发请求（避免无谓的列表重置与闪烁）
  if (value === props.activeValue) return
  emit('select', value)
}
</script>

<style scoped lang="scss">
/* 标签栏落在页面渐变底色区（与上方白色搜索卡在明度上可区分，见 client-visual-language）。
   自身上下 padding **归零**：与搜索区、与网格的间距各由
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
  /* 文字**行内上偏置**（§5.2 光学间距）：行内偏置 24rpx 即「搜索区 → 标签文字」
     间距的**全部来源**（≈12px，`.mt-bar` 已不再补 padding）；「下划线 → 卡片首行」由吸顶容器
     padding-bottom + 本行行底余量共同构成（≈16px）。两侧都较旧口径（20 / 24）收紧，
     仍保持「下行距 ≥ 上行距」，分组感不丢。
     ⚠️ 下划线不得吸到行底（margin-top:auto）——那会让它离文字 ≈16px、与标签脱开。 */
  justify-content: flex-start;
  /* 命中区：高 88rpx（触达下限，不得压低；= 上偏置 24 + 文字行 ≈34 + 下划线位 14 + 行底余量）；
     宽 = 标签文字 + 左右各 24rpx。最短标签「全部」（2 字 × --font-body 28rpx = 56rpx）+ 48rpx = 104rpx ≈ 52px ≥ 44px ✅。 */
  height: 88rpx;
  /* 上内距 = `--spacing-md`（24rpx，同值）：UI 统一 Loop Round 6，由裸 24rpx 改为 token */
  padding: var(--spacing-md) var(--spacing-md) 0;
  box-sizing: border-box;
  vertical-align: bottom;
  -webkit-tap-highlight-color: transparent;
}
/* 按压反馈：小程序端统一「透明度微降」（§4.9：废止 transform: scale 按压） */
.mt-tab-pressed {
  opacity: 0.6;
}
/* 未选中：正文档深灰棕（#4A3520）常规字 —— 层级低于选中项、仍不抢搜索区（§5.1 / §8） */
.mt-label {
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  color: var(--text-body);
  line-height: 1.2;
}
/* 选中：标题档（#2D1F14）+ 半粗 —— 与未选中拉开层级（区分不靠颜色，靠字重 + 下划线） */
.mt-tab.active .mt-label {
  font-weight: var(--weight-semibold);
  color: var(--text-title);
}
/* 橙色短下划线：**长度贴合文字宽度**（width: 100% = 标签内容宽）、紧随文字 4px、选中显现；
   取「图形档」--color-primary-amber（#F5A623）——纯图形装饰，选中语义另由字重承载（§5.1 / §10.1） */
.mt-underline {
  width: 100%;
  height: 6rpx;
  margin-top: var(--spacing-xs);
  border-radius: var(--radius-pill);
  background: var(--color-primary-amber);
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
