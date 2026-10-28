<script setup lang="ts">
/**
 * 美食地图（单页全屏）—— 设计稿 docs/ui/client/美食地图.md；功能规则 docs/func/client/A1、B1、B2。
 *
 * 进入即拉取全量在售菜品并随机散布；点磁贴唤起底部弹窗看详情、两态点赞与纠错。
 * 空态不处理（零在售时画布留白），不做下拉刷新。
 */
import { onMounted, ref } from 'vue'

import { dishApi, issueApi } from '@/api'
import type { DishVO, IssueOptionOut } from '@/api'
import PageLoading from '@/components/base/PageLoading.vue'
import RetryBlock from '@/components/base/RetryBlock.vue'
import { uniqueById } from '@/utils/dishBatch'
import { useTheme } from '@/utils/theme'
import DishSheet from './components/DishSheet.vue'
import FoodMap from './components/FoodMap.vue'

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
  } catch {
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
</script>

<template>
  <view class="map-page" :data-theme="theme">
    <PageLoading v-if="loading" />
    <RetryBlock v-else-if="failed" @retry="load" />
    <FoodMap v-else :dishes="dishes" @pick="openSheet" />
    <DishSheet v-model="sheetOpen" :dish="selected" :options="options" @change="onDishChange" />
  </view>
</template>

<style lang="scss" scoped>
.map-page {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--bg-page);
}
</style>
