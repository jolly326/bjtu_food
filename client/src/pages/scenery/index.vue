<script setup lang="ts">
/**
 * 风物 tab（设计稿 docs/ui/client/风物.md；功能规则 docs/func/client/A1）。
 * 4 块垂直分区：Header（通栏矩形：明暗切换 + 标题）→ 问候语（通栏矩形块，四态）
 * → 卡片海报区（左上角「今日食单」+ 同槽位 Z 轴堆叠的海报卡）→ TabBar 容器。
 * 问候语、Header 与页面骨架是本地内容、不依赖网络；海报区**失败 / 为空时渲染 3 张占位卡**
 * （见 PosterSwiper），页内不提供重试入口。
 */
import { computed, onMounted, ref } from 'vue'
import AppHeader from '@/components/base/AppHeader.vue'
import TabBar from '@/components/base/TabBar.vue'
import PosterSwiper from './components/PosterSwiper.vue'
import { posterApi } from '@/api'
import type { PosterOut } from '@/api'
import { useTheme } from '@/utils/theme'

const posters = ref<PosterOut[]>([])
/** 全站主题（明暗切换已上提到全局 AppHeader） */
const theme = useTheme()

/** 问候语四态（无姓名；「小红果」为固定群体称谓） */
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好，小红果'
  if (hour < 13) return '中午好，小红果'
  if (hour < 17) return '下午好，小红果'
  return '晚上好，小红果'
})

async function load() {
  try {
    posters.value = await posterApi.list()
  } catch {
    // 失败不提示、不给重试入口：海报区转 3 张占位卡（口径见 docs/ui/client/风物.md §2 / §4）
    posters.value = []
  }
}

onMounted(load)
</script>

<template>
  <view class="page" :data-theme="theme">
    <!-- 区块 1 · 全局页头（品牌标题 + 明暗切换；与 TabBar 同级的公共组件） -->
    <AppHeader />

    <!-- 区块 2 · 问候语（通栏矩形块）：块内水平 + 垂直居中 -->
    <view class="greeting">{{ greeting }}</view>

    <!-- 区块 3 · 卡片海报区：左上角次级标题 + 同槽位堆叠的海报卡（顶层可划走，下层上浮接替） -->
    <view class="posterArea">
      <view class="posterArea__title">今日食单</view>
      <view class="posterArea__swiper">
        <PosterSwiper :posters="posters" />
      </view>
    </view>

    <!-- 区块 4 · TabBar 容器（见基线 §2.1.1） -->
    <TabBar active="scenery" />
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

/* 问候语：独立全屏宽矩形容器 —— 文字在容器内水平 + 垂直居中，上下留白均匀；hero 档衬线
   （页头主信息：字号最大、色最重；次级标题再弱化一档） */
.greeting {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-md) var(--page-gutter);
  font-family: var(--font-serif);
  font-size: var(--font-hero);
  font-weight: var(--weight-regular);
  line-height: 1.2;
  color: var(--text-title);
  text-align: center;
}

/* 海报区：占满剩余高度 —— 问候容器 → 大留白 → 次级标题 → 空白 → 卡片堆叠槽位。
   顶部内距 = 问候语与次级标题之间的纵向留白（两级标题拉开层次）；
   底部内距 = 海报与 TabBar 容器的呼吸位（不贴 TabBar） */
.posterArea {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding-top: calc(var(--spacing-2xl) * 2);
  padding-bottom: var(--spacing-md);
}

/* 次级标题：左对齐，字号小于问候语、色降一档（--text-secondary）；与卡片之间紧凑柔和的留白 */
.posterArea__title {
  flex: none;
  padding: 0 var(--page-gutter);
  margin-bottom: var(--spacing-lg);
  font-family: var(--font-serif);
  font-size: var(--font-title);
  font-weight: var(--weight-regular);
  line-height: 1.2;
  color: var(--text-secondary);
  text-align: left;
}

/* 堆叠槽位宿主：占满海报区剩余高度（卡片放大）；卡片栈绝对定位铺满本容器 */
.posterArea__swiper {
  position: relative;
  flex: 1;
  min-height: 0;
}
</style>
