<script setup lang="ts">
/**
 * 发现 tab（设计稿 docs/ui/client/档口海报.md；功能规则 docs/func/client/A1）。
 * 问候语（无姓名，随时间三态；轻字重衬线）+ 板块标题「为你推荐」+ 一屏一张竖版海报，左右滑动。
 * 问候语、板块标题与页面骨架是本地内容、不依赖网络；海报区**失败 / 为空时渲染 3 张占位卡**（见 PosterSwiper），页内不提供重试入口。
 */
import { computed, onMounted, ref } from 'vue'
import TabBar from '@/components/base/TabBar.vue'
import PosterSwiper from '@/components/business/PosterSwiper.vue'
import { posterApi } from '@/api'
import type { PosterOut } from '@/api'
import { navMetrics } from '@/utils/safeArea'

const posters = ref<PosterOut[]>([])
/** 顶部安全区：避开状态栏 / 刘海与右上角胶囊 */
const topInset = navMetrics().topInset

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好，小红果'
  if (hour < 17) return '中午好，小红果'
  return '晚上好，小红果'
})

async function load() {
  try {
    posters.value = await posterApi.list()
  } catch {
    // 失败不提示、不给重试入口：海报区转 3 张占位卡（口径见 docs/ui/client/档口海报.md §2 / §4）
    posters.value = []
  }
}

onMounted(load)
</script>

<template>
  <view class="page" :style="{ paddingTop: topInset + 'px' }">
    <view class="greeting">{{ greeting }}</view>

    <view class="section">为你推荐</view>

    <view class="posterArea">
      <PosterSwiper :posters="posters" />
    </view>

    <TabBar active="discover" />
  </view>
</template>

<style lang="scss" scoped>
.page {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  height: 100vh;
  overflow: hidden;
  padding-bottom: calc(
    var(--tabbar-height) + var(--tabbar-gap) + var(--spacing-md) + env(safe-area-inset-bottom)
  );
}

/* 问候区：**独立头部板块**（与「为你推荐」分开）—— 顶部留白，紧随页面顶部 */
.greeting {
  padding: var(--spacing-2xl) var(--page-gutter) 0;
  font-family: var(--font-serif);
  font-size: var(--font-display);
  font-weight: var(--weight-regular);
  line-height: 1.2;
  color: var(--text-title);
  text-align: center;
}

/* 板块标题：**左对齐 + 加粗**；与问候区之间的弹性留白由 margin-top:auto 承担，紧贴海报墙顶部 */
.section {
  padding: 0 var(--page-gutter);
  margin-top: auto;
  margin-bottom: var(--spacing-sm);
  font-family: var(--font-serif);
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--text-title);
  text-align: left;
}

/* 轮播区高度 = 3:4 卡高（高度不足时收缩）；卡片贴区域底部、靠近 TabBar */
.posterArea {
  position: relative;
  flex: 0 1 auto;
  height: calc(
    (100vw - var(--page-gutter) - var(--spacing-md) - var(--spacing-2xl)) * 4 / 3
  );
  min-height: 0;
  margin-bottom: var(--spacing-xl);
  overflow: hidden;
}
</style>
