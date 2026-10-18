<script setup lang="ts">
/**
 * 风物 tab（设计稿 docs/ui/client/风物.md；功能规则 docs/func/client/A1）。
 * 4 块垂直分区：Header（通栏矩形：明暗切换 + 标题）→ 问候语（通栏矩形块，四态）
 * → 卡片海报区（左上角「今日食单」+ 一屏一张竖版海报）→ TabBar 容器。
 * 问候语、Header 与页面骨架是本地内容、不依赖网络；海报区**失败 / 为空时渲染 3 张占位卡**
 * （见 PosterSwiper），页内不提供重试入口。
 */
import { computed, onMounted, ref } from 'vue'
import AppIcon from '@/components/base/AppIcon.vue'
import TabBar from '@/components/base/TabBar.vue'
import PosterSwiper from '@/components/business/PosterSwiper.vue'
import { posterApi } from '@/api'
import type { PosterOut } from '@/api'
import { navMetrics } from '@/utils/safeArea'
import { toggleTheme, useTheme } from '@/utils/theme'

const posters = ref<PosterOut[]>([])
/** Header 度量：状态栏高（含刘海避让）+ 胶囊带高（与右上角原生胶囊对齐） */
const { statusBarHeight, navBarHeight } = navMetrics()
/** 全站主题（明暗切换作用于三个 tab 页） */
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
    <!-- 区块 1 · Header（通栏矩形）：上贴屏顶，下边界在原生胶囊下方预留间距 -->
    <view class="header" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="header__row" :style="{ height: navBarHeight + 'px' }">
        <!-- 左：明暗切换（浅色态显 moon、深色态显 sun，预示点击后的目标模式） -->
        <view class="header__toggle" hover-class="header__toggle--pressed" @tap="toggleTheme">
          <AppIcon v-if="theme === 'dark'" name="sun" :size="44" />
          <AppIcon v-else name="moon" :size="44" />
        </view>
        <!-- 中：标题（水平居中） -->
        <view class="header__title">知行食记</view>
        <!-- 右：为微信原生胶囊避让（不改动原生组件） -->
        <view class="header__capsuleSpace" />
      </view>
    </view>

    <!-- 区块 2 · 问候语（通栏矩形块）：块内水平 + 垂直居中 -->
    <view class="greeting">{{ greeting }}</view>

    <!-- 区块 3 · 卡片海报区：左上角标题 + 横向滑动（默认态第 1 张完整 + 第 2 张左缘一小截） -->
    <view class="posterArea">
      <view class="posterArea__title">今日食单</view>
      <view class="posterArea__swiper">
        <PosterSwiper :posters="posters" />
      </view>
    </view>

    <!-- 区块 4 · TabBar 容器（见基线 §2.1.1） -->
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

/* Header：独立完整通栏矩形，与下方问候区以留白作视觉分隔（不加分割线） */
.header {
  flex: none;
  padding-bottom: var(--spacing-lg);
}

.header__row {
  display: flex;
  align-items: center;
  padding: 0 var(--page-gutter);
}

/* 明暗切换按钮：命中区 ≥ 触达下限；图标左缘与页面基准线（海报卡左缘）对齐 */
.header__toggle {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  width: var(--tap-target-size);
  height: var(--tap-target-size);
}

.header__toggle--pressed {
  opacity: 0.6;
}

/* 标题：衬线 + 轻字重，水平居中（两侧等宽占位 ⇒ 视觉居中） */
.header__title {
  flex: 1;
  font-family: var(--font-serif);
  font-size: var(--font-title);
  font-weight: var(--weight-medium);
  line-height: 1.2;
  color: var(--text-title);
  text-align: center;
}

/* 右侧占位：与左侧切换按钮等宽，保证标题居于屏中 */
.header__capsuleSpace {
  width: var(--tap-target-size);
}

/* 问候语：独立全屏宽矩形容器 —— 文字在容器内水平 + 垂直居中，上下留白均匀；加粗衬线 */
.greeting {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-2xl) var(--page-gutter);
  font-family: var(--font-serif);
  font-size: var(--font-display);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--text-title);
  text-align: center;
}

/* 海报区：占满剩余高度 —— 问候容器 → 空白 → 标题 → 空白 → 横向卡片滑动容器。
   顶部内距与 Header 的有效底距对位（Header 下内距 32rpx + 标题在 88rpx 命中区行内的居中量），
   使问候语与上下两侧标语（知行食记 / 今日食单）的视觉距离相同 */
.posterArea {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding-top: var(--spacing-2xl);
}

/* 板块标题：左对齐 + 加粗；与卡片之间紧凑柔和的留白（不贴死） */
.posterArea__title {
  flex: none;
  padding: 0 var(--page-gutter);
  margin-bottom: var(--spacing-lg);
  font-family: var(--font-serif);
  font-size: var(--font-title);
  font-weight: var(--weight-bold);
  line-height: 1.2;
  color: var(--text-title);
  text-align: left;
}

/* 滑动容器：占满海报区剩余高度（卡片放大）；原生 swiper 绝对定位铺满本容器 */
.posterArea__swiper {
  position: relative;
  flex: 1;
  min-height: 0;
}
</style>
