<script setup lang="ts">
/**
 * 风物 tab（设计稿 docs/ui/client/风物.md；功能规则 docs/func/client/A1）。
 * 4 块垂直分区：Header（通栏矩形：明暗切换 + 标题）→ 问候语（通栏矩形块，四态）
 * → 海报区（左上角「今日食单」+ 横向滑动的海报卡，右邻卡露边提示可滑）→ TabBar 容器。
 * 问候语、Header 与页面骨架是本地内容、不依赖网络；海报区**加载失败 ⇒ hero 下方区域竖直居中
 * 渲染 `RetryBlock` 可重试**（与美味 / 记忆同构）；**无海报 ⇒ 空态文案**。
 */
import { computed, onMounted, ref } from 'vue'
import TabPage from '@/components/base/TabPage.vue'
import PageLoading from '@/components/base/PageLoading.vue'
import RetryBlock from '@/components/base/RetryBlock.vue'
import EmptyBlock from '@/components/base/EmptyBlock.vue'
import PosterSlider from './components/PosterSlider.vue'
import { posterApi } from '@/api'
import type { PosterOut } from '@/api'

const posters = ref<PosterOut[]>([])
const loading = ref(true)
const failed = ref(false)

/** 问候语四态（无姓名；「小红果」为固定群体称谓） */
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好，小红果'
  if (hour < 13) return '中午好，小红果'
  if (hour < 17) return '下午好，小红果'
  return '晚上好，小红果'
})

async function load() {
  loading.value = true
  failed.value = false
  try {
    posters.value = await posterApi.list()
  } catch {
    failed.value = true
    posters.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <TabPage v-if="failed" active="scenery">
    <!-- 区块 2 · 问候语（hero 插槽）：与美味 / 记忆同一 topbar 盒高，块内水平 + 垂直居中 -->
    <template #hero>
      <view class="topbar">
        <text class="topbar__greeting">{{ greeting }}</text>
      </view>
    </template>

    <!-- 区块 3 · 失败态：与美味 / 记忆同构 —— hero 下方区域竖直居中渲染重试块 -->
    <RetryBlock @retry="load" />
  </TabPage>

  <TabPage v-else active="scenery">
    <!-- 区块 2 · 问候语（hero 插槽）：与美味 / 记忆同一 topbar 盒高，块内水平 + 垂直居中 -->
    <template #hero>
      <view class="topbar">
        <text class="topbar__greeting">{{ greeting }}</text>
      </view>
    </template>

    <!-- 区块 3 · 海报区（内容插槽）：topbar 下方整块区域全部交给海报滑动器（右邻卡露边提示可滑）；
         加载中 ⇒ 加载块居中于该区域（不整页覆盖）；无海报 ⇒ 统一空态块（三页同一实现） -->
    <view class="posterArea">
      <PageLoading v-if="loading" />
      <view v-else-if="posters.length" class="posterArea__swiper">
        <PosterSlider :posters="posters" />
      </view>
      <EmptyBlock v-else icon="image" title="暂无风物海报" />
    </view>
  </TabPage>
</template>

<style lang="scss" scoped>
/* 问候语 topbar：与美味 / 记忆的 hero 标题块**同一盒高**（--hero-box-height）——
   三页切 tab 时内容区起点完全一致；文字块内水平 + 垂直居中 */
.topbar {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  min-height: var(--hero-box-height);
  padding: var(--spacing-md) var(--page-gutter);
}

/* 问候语文本：hero 档衬线（与美味 / 记忆页头标题同字号），层级一致 */
.topbar__greeting {
  font-family: var(--font-serif);
  font-size: var(--font-hero);
  font-weight: var(--weight-regular);
  line-height: 1.2;
  color: var(--text-title);
  text-align: center;
}

/* 海报区：占满 topbar 下方全部剩余高度 ——
   加载 / 空态 / 滑动器三者都在这整块区域内居中或铺满 */
.posterArea {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

/* 滑动器宿主：占满海报区剩余高度；海报滑动器绝对定位铺满本容器 */
.posterArea__swiper {
  position: relative;
  flex: 1;
  min-height: 0;
}

</style>
