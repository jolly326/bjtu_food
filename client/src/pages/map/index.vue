<script setup lang="ts">
/**
 * 美食地图（单页全屏）—— 设计稿 docs/ui/client/美食地图.md；功能规则 docs/func/client/A1、B1、B2。
 *
 * 进入即拉取全量在售菜品并按错位方格密铺；点磁贴唤起底部弹窗看详情、两态点赞与纠错。
 * 空态不处理（零在售时画布留白），不做下拉刷新。
 */
import { onMounted, onUnmounted, ref } from 'vue'

import { dishApi, issueApi } from '@/api'
import type { DishVO, IssueOptionOut } from '@/api'
import PageLoading from '@/components/PageLoading.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import { topSafeArea } from '@/utils/safeArea'
import { windowSize } from '@/utils/viewport'
import { uniqueById } from '@/utils/dishBatch'
import { abortAllRequests, isAbortedRequest } from '@/utils/request'
import { useTheme } from '@/theme'
import DishSheet from './components/DishSheet.vue'
import FoodMap from './components/FoodMap.vue'

/** 顶部安全区（状态栏 + 胶囊）：画布内容起始避让，依赖窗口宽读一次即可 */
const safeTop = topSafeArea(windowSize().width).total

const theme = useTheme()

const dishes = ref<DishVO[]>([])
const options = ref<IssueOptionOut[]>([])
const loading = ref(true)
const failed = ref(false)
const sheetOpen = ref(false)
const selected = ref<DishVO | null>(null)

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    const [list, issueOptions] = await Promise.all([dishApi.list(), issueApi.options()])
    dishes.value = uniqueById(list)
    options.value = issueOptions
  } catch (error) {
    // 页面卸载主动取消不落错误态
    if (isAbortedRequest(error)) return
    failed.value = true
  } finally {
    loading.value = false
  }
}

function openSheet(dish: DishVO): void {
  selected.value = dish
  sheetOpen.value = true
}

function onDishChange(payload: { id: number; likeCount: number; liked: boolean }): void {
  const dish = dishes.value.find((item) => item.id === payload.id)
  if (!dish) return
  dish.likeCount = payload.likeCount
  dish.liked = payload.liked
}

onMounted(load)
onUnmounted(abortAllRequests)
</script>

<template>
  <view class="map-page" :data-theme="theme" :style="{ paddingTop: `${safeTop}px` }">
    <view class="map-page__body">
      <PageLoading v-if="loading" />
      <RetryBlock v-else-if="failed" @retry="load" />
      <FoodMap v-else :dishes="dishes" @pick="openSheet" />
    </view>
    <DishSheet v-model="sheetOpen" :dish="selected" :options="options" @change="onDishChange" />
  </view>
</template>

<style lang="scss" scoped>
/* 页面底色唯一承载处是 `page{}`（styles/global.scss），此处 SHALL NOT 重复声明（基线 §1.1）；
   顶部 `padding-top` 让画布避开状态栏与右上角胶囊（见 utils/safeArea.ts） */
.map-page {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  box-sizing: border-box;
  flex-direction: column;
  overflow: hidden;
}

/* 画布 / 加载 / 失败块的内容区：撑满剩余高度，并作为 FoodMap 绝对定位的包含块 */
.map-page__body {
  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
}
</style>
