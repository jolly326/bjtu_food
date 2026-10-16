<script setup lang="ts">
/**
 * 橱窗 tab（设计稿 docs/ui/client/档口海报.md；功能规则 docs/func/client/A1）。
 * 问候语（无姓名，随时间三态）+ 板块标题 + 一屏一张竖版海报，左右滑动。
 * 问候语、板块标题与页面骨架是本地内容、不依赖网络；海报区**失败 / 为空时渲染 3 张占位卡**（见 PosterSwiper），页内不提供重试入口。
 */
import { computed, onMounted, ref } from 'vue'
import TabBar from '@/components/base/TabBar.vue'
import PosterSwiper from '@/components/business/PosterSwiper.vue'
import { posterApi } from '@/api'
import { navMetrics } from '@/utils/safeArea'

type Poster = { id: number; imageUrl: string }

const posters = ref<Poster[]>([])
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

    <view class="section">今天吃什么</view>

    <view class="posterArea">
      <PosterSwiper :posters="posters" />
    </view>

    <TabBar active="poster" />
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

.greeting {
  padding: var(--spacing-xl) var(--page-gutter);
  font-size: var(--font-display);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--text-title);
  text-align: center;
}

/* 板块标题（对齐参考图：加粗、字号 ≈ 问候语的 0.8） */
.section {
  padding: 0 var(--page-gutter);
  margin-bottom: var(--spacing-md);
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--text-title);
}

/* 由 flex 分配剩余高度，并作为 PosterSwiper 的定位祖先（原生 swiper 不参与 flex 拉伸） */
.posterArea {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
