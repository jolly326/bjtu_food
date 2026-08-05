<template>
  <view class="page my-moments-page">
    <Header title="我的动态" showBack />

    <!-- 直接展示一列我的动态（无分类 tab；被退回的会通过消息中心提醒） -->
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <view v-if="loading && moments.length === 0" class="skeleton-list">
        <view v-for="s in 3" :key="s" class="sk-card skeleton" />
      </view>

      <!-- 加载失败：与空数据语义区分，提供重试 -->
      <EmptyState v-else-if="loadFailed" text="加载失败，请重试" icon="report" :retry="true" @retry="loadData" />

      <EmptyState
        v-else-if="moments.length === 0"
        text="你还没有发布动态"
        icon="comment"
        @retry="loadData"
      />

      <view v-else class="moment-list">
        <!-- enter-up + --enter-i：列表 stagger 入场（全局 enterFade 0.2s + 40ms 间隔） -->
        <MomentCard
          v-for="(m, i) in moments"
          :key="m.id"
          class="enter-up"
          :style="{ '--enter-i': Math.min(i, 8) }"
          :moment="m"
          :show-audit="true"
          @select="goDetail"
          @go-related="goRelated"
        />
      </view>

      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 菜品详情底部弹层（task-10：独立页 → sheet） -->
    <DishDetailSheet
      :open="dishSheetOpen"
      :dish-id="sheetDishId"
      top-offset="176rpx"
      @update:open="dishSheetOpen = $event"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onShareAppMessage } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import MomentCard from '@/components/MomentCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import DishDetailSheet from '@/components/DishDetailSheet.vue'
import { useUserStore } from '@/stores/user'
import { useDishStore } from '@/stores/dish'
import * as momentApi from '@/api/moment'
import type { Moment } from '@/types/moment'
import { buildSharePayload } from '@/utils/shareState'

const userStore = useUserStore()
const dishStore = useDishStore()
const moments = ref<Moment[]>([])
/** 菜品详情底部弹层（task-10：独立页 → sheet） */
const dishSheetOpen = ref(false)
const sheetDishId = ref(0)
function openDishSheet(id: number) {
  if (!id) return
  sheetDishId.value = id
  dishSheetOpen.value = true
}
const loading = ref(false)
const loadFailed = ref(false)
const refresherTriggered = ref(false)

async function loadData() {
  if (!userStore.requireAuth()) return
  loading.value = true
  loadFailed.value = false
  try {
    moments.value = await momentApi.getMyMoments()
  } catch (e: any) {
    loadFailed.value = true
    uni.showToast({ title: e.message || '加载失败', icon: 'none' })
    moments.value = []
  } finally {
    loading.value = false
  }
}

function goDetail(m: Moment) {
  // 已退回可直达编辑；其他态进详情
  if (m.auditStatus === 'rejected') {
    uni.navigateTo({ url: `/pages/pages-user/publish-moment/index?id=${m.id}` })
  } else {
    uni.navigateTo({ url: `/pages/pages-detail/moment?id=${m.id}` })
  }
}

function goRelated(m: Moment) {
  if (m.relatedType === 'dish' && m.relatedId) {
    openDishSheet(m.relatedId)
  } else if (m.relatedType === 'stall' && m.relatedName && m.relatedCanteen) {
    // 档口详情靠 navParams（stallName + canteen）加载，不能用 ?id=（stall 页不支持）
    dishStore.navParams = { stallName: m.relatedName, canteen: m.relatedCanteen }
    uni.navigateTo({ url: '/pages/pages-detail/stall' })
  }
}

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => { refresherTriggered.value = false })
}

onMounted(() => { loadData() })
onShareAppMessage(() => buildSharePayload())
</script>

<style scoped>
.my-moments-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: 0; }
.moment-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.skeleton-list { padding: var(--spacing-md); display: flex; flex-direction: column; gap: var(--spacing-md); }
.sk-card { width: 100%; height: 280rpx; }
</style>
