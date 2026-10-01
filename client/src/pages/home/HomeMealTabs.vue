<template>
  <!-- 首页横向「筛选视图」标签栏（§7.34 运营化解耦）：
       横向可滑动 + 单选 + 橙色短下划线高亮。
       ⚠️ **标签集合与文案 100% 由后端下发直出**（`GET /dishes/views`）——
       含「为你推荐」这类**聚合视图**，端上**零文案、零拼接、零兜底项**：
       改文案 / 加视图（如「折扣」）只在服务端 `DishViewConst` + `listDishViews()` 出口处装配，端上无需发版。
       字典未加载 / 失败 ⇒ 判空**整体不渲染**（不留空栏、不占位）；列表仍按默认视图加载。 -->
  <view v-if="tabs.length > 0" class="mt-bar">
    <scroll-view
      class="mt-scroll"
      scroll-x
      :scroll-into-view="scrollIntoId"
      :scroll-with-animation="true"
    >
      <view class="mt-track">
        <view
          v-for="tab in tabs"
          :key="tab.key"
          :id="idOf(tab.key)"
          class="mt-tab"
          :class="{ active: tab.key === activeKey }"
          role="button"
          :aria-label="`筛选：${tab.label}`"
          hover-class="mt-tab-pressed"
          @tap="onSelect(tab.key)"
        >
          <text class="mt-label">{{ tab.label }}</text>
          <!-- 选中态橙色短下划线：常驻节点 + opacity 切换（避免显隐引起行高跳动）；
               纯装饰（选中语义已由 .active 字重与 aria-label 表达），对读屏隐藏 -->
          <view class="mt-underline" :class="{ show: tab.key === activeKey }" aria-hidden="true" />
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DishView } from '@/types/dish'

/** 标签项：`key` 原样回传（无 null 特例：默认视图「为你推荐」也是普通 key）；文案一律来自服务端 */
interface ViewTab {
  key: string
  label: string
}

const props = defineProps<{
  /** 筛选视图字典（`store.viewList`）——**后端已含「为你推荐」等聚合视图及大类视图**，端上原样渲染 */
  items: DishView[]
  /** 当前选中视图键（`null` = 字典尚未加载） */
  activeKey: string | null
}>()

const emit = defineEmits<{
  (e: 'select', key: string): void
}>()

/**
 * 渲染项**完全直出后端响应**：端上**不前置拼接、不补兜底项**。
 * 字典未到位（未加载 / 失败 / 后端返回空）⇒ 返回空数组 ⇒ 标签栏整体不渲染（不留空栏）。
 * 这样「「为你推荐」文案」「将来新增的视图」全部是服务端资产，端上零文案。
 */
const tabs = computed<ViewTab[]>(() =>
  (props.items ?? []).map((item) => ({ key: item.key, label: item.label })),
)

/** 选中项滚动入视口（横向标签超过一屏时，切换后仍能看到高亮项） */
const scrollIntoId = computed(() => (props.activeKey ? idOf(props.activeKey) : ''))

/** 稳定 id：小程序 `scroll-into-view` 要求 id 以字母开头、且不含特殊字符 */
function idOf(key: string): string {
  return `mt-tab-${key}`
}

function onSelect(key: string) {
  // 点击已选中项不重复发请求（避免无谓的列表重置与闪烁）
  if (key === props.activeKey) return
  emit('select', key)
}
</script>

<style scoped lang="scss">
/* 标签栏落在页面底色区（与上方白色搜索卡在明度上可区分 —— 形态与色彩口径真源见
   docs/client/ui/client-公共组件与形态基线.md；色板取色边界见 docs/client/ui/client-首页菜品浏览.md §10）。
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
/* 隐藏 H5 端横向滚动条：**仅 H5** —— 小程序不支持 `::-webkit-scrollbar`，且该伪元素经编译会落成
   tag/属性选择器，触发「selectors are not allowed in component wxss」警告；weapp 的 scroll-view
   本身不显示滚动条，MP 端无需本规则。uni-app 条件编译在此生效。 */
/* #ifdef H5 */
.mt-scroll ::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}
/* #endif */
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
  height: var(--tap-target-size);
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
  height: var(--strip-height);
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
