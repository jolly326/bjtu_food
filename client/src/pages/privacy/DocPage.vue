<template>
  <!-- 文档页外壳（UI 统一 Loop Round 4 上提）
       背景：隐私政策 / 用户协议两页**逐字节重复**同一套外壳 —— 壁纸层 + `AppHeader` + 滚动容器 + 文档白卡，
       仅顶栏标题 / 文档标题 / 生效日期 / 正文不同。按 §2「就近组织」收敛为**包内**共享组件
       （与 `pages/find/DishResultCard.vue` 同款做法：仅本包两页消费，不上提 `components/`）。

       ⚠️ 为什么正文用 **`sections` 数据**而不是「默认插槽」：
       槽内容在小程序端由**父页 wxml** 承载，而父页/组件的 `scoped` 作用域类**不会**加到槽节点上
       —— 实测编译产物：槽节点为裸 `<text class="doc-h">`、不带任何 `data-v-*`，组件内的 `:deep(.doc-h)`
       （编译为 `.data-v-64630978 .doc-h`）跨组件边界**不保证生效**，正文会丢排版。
       改为数据驱动后，`.doc-h` / `.doc-p` 由**本组件自己的模板**渲染 ⇒ 组件 `scoped` 样式 100% 命中。 -->
  <view class="page doc-page">
    <!-- 全站壁纸层（`fixed`：视口锚定、`z-index: -1` → 落在页底之上、内容之下） -->
    <PageWallpaper fixed />
    <Header :title="navTitle" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y>
      <view class="doc">
        <text class="doc-title">{{ title }}</text>
        <text class="doc-meta">{{ meta }}</text>

        <!-- 正文：每节 = 可选小节标题 + 若干段落（均由本组件渲染，样式同作用域） -->
        <block v-for="(sec, si) in sections" :key="si">
          <text v-if="sec.h" class="doc-h">{{ sec.h }}</text>
          <text v-for="(p, pi) in sec.ps" :key="pi" class="doc-p">{{ p }}</text>
        </block>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * DocPage —— 合规文档页外壳（隐私政策 / 用户协议共用）
 *
 * 消费方（2 处，同包）：
 * - pages/privacy/index.vue（隐私政策）
 * - pages/privacy/agreement.vue（用户协议）
 *
 * 仅承载外壳、文档排版与正文渲染；返回路径固定为「回首页」（与抽离前两页各自的 `backToHome` 行为一致）。
 */
import Header from '@/components/AppHeader.vue'
import PageWallpaper from '@/components/PageWallpaper.vue'
import { backToHome } from '@/utils/nav'

/** 正文章节：`h` = 小节标题（可省略）；`ps` = 段落文本（按序渲染） */
export interface DocSection {
  h?: string
  ps: string[]
}

defineProps<{
  /** 顶栏标题（短：如「隐私政策」） */
  navTitle: string
  /** 文档大标题（如「知行食记隐私政策」） */
  title: string
  /** 文档副信息（如「生效日期：2026 年 9 月 20 日」） */
  meta: string
  /** 正文分节（按顺序渲染） */
  sections: DocSection[]
}>()
</script>

<style scoped>
/* 页面外壳：纵向铺满视口（100dvh 为移动端可视区口径），页底见壁纸 */
.doc-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  /* 页面根不带底色（UI 统一 Loop Round 11）：底色下沉到全局 `page{}`，否则会盖住负层级壁纸层 */
}
.scroll-wrap {
  flex: 1;
  min-height: 0;
  /* Round 26：去掉 `overflow-y: auto` —— 本容器是 `scroll-view`（滚动由组件实现），
     外挂 CSS 在 H5 会叠出第二根滚动条。 */
  padding: 0 var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg));
  box-sizing: border-box;
}

/* 文档白卡：与列表卡同表面语言（bg-card + radius-card + shadow-card） */
.doc {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  box-sizing: border-box;
}
.doc-title { font-size: var(--font-title); font-weight: var(--weight-semibold); color: var(--text-primary); }
.doc-meta { font-size: var(--font-tiny); color: var(--text-tertiary); margin-bottom: var(--spacing-xs); }
/* 正文排版（小节标题 / 段落）—— 本组件自身节点，scoped 直接命中 */
.doc-h {
  font-size: var(--font-body);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  margin-top: var(--spacing-sm);
}
.doc-p { font-size: var(--font-small); color: var(--text-secondary); line-height: 1.7; }
</style>
