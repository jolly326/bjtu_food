<template>
  <view class="page stall-page">
    <Header :title="canteenName" showBack />
    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 关联动态上移：作为食堂社交氛围入口，紧跟 Header，不再沉底 -->
      <CardSection title="关联动态">
        <EmptyState v-if="relatedMoments.length === 0" text="暂无关联动态" icon="💬" />
        <view v-else class="moment-list">
          <view
            v-for="m in relatedMoments.slice(0, 3)"
            :key="m.id"
            class="moment-item"
            :class="{ pressed: momentPressedId === m.id }"
            @touchstart="momentPressedId = m.id"
            @touchend="momentPressedId = 0"
            @touchcancel="momentPressedId = 0"
            @mousedown="momentPressedId = m.id"
            @mouseup="momentPressedId = 0"
            @mouseleave="momentPressedId = 0"
            @tap="goMoment(m.id)"
          >
            <text class="moment-text">{{ m.content }}</text>
            <view class="moment-meta">
              <text class="moment-author">{{ m.userNickname }}</text>
              <text class="moment-count">{{ EMOJI.useful }} {{ m.usefulCount }} · 💬 {{ m.commentCount }}</text>
            </view>
          </view>
          <view v-if="relatedMoments.length > 3" class="moment-more" @tap="goCommunity">查看全部关联动态 ›</view>
        </view>
      </CardSection>

      <!-- 档口筛选条：点选档口筛选菜品，不点则展示全部；档口与菜品合并为单流 -->
      <view class="stall-filter" v-if="stallList.length > 0">
        <scroll-view class="stall-filter-scroll" scroll-x>
          <view
            class="stall-chip"
            :class="{ on: activeStall === '' }"
            @tap="activeStall = ''"
          >全部</view>
          <view
            v-for="stall in stallList"
            :key="stall.id"
            class="stall-chip"
            :class="{ on: activeStall === stall.name }"
            @tap="activeStall = stall.name"
            @longpress="goToStall(stall)"
          >{{ stall.name }}</view>
        </scroll-view>
      </view>

      <!-- 同一菜品瀑布流：按档口筛选，统一浏览，不再与档口列表分开 -->
      <view class="dish-section" v-if="filteredDishes.length > 0">
        <WaterfallList :list="filteredDishes">
          <template #card="{ item: dish }">
            <DishCard :dish="dish" @click="goToDetail" />
          </template>
        </WaterfallList>
      </view>
      <EmptyState v-else text="该食堂暂无菜品" />

      <!-- 申请调整/下架：不常用，降级为底部弱化的小文字链接，不再横卡置顶 -->
      <view class="apply-link" @tap="openApply">
        <text class="apply-link-text">食堂信息有误？申请调整 / 下架 ›</text>
      </view>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 申请调整/下架 Sheet（task-12.1） -->
    <view v-if="applyOpen" class="sheet-mask" @tap="applyOpen = false" />
    <view class="apply-sheet" :class="{ open: applyOpen }">
      <view class="sheet-head">
        <text class="sheet-title">申请调整 / 下架</text>
        <text class="sheet-close" @tap="applyOpen = false">✕</text>
      </view>
      <view class="form-block">
        <text class="form-label">申请动作</text>
        <view class="seg-row">
          <view class="seg" :class="{ on: applyAction === 'CHANGE' }" @tap="applyAction = 'CHANGE'">调整 / 变更</view>
          <view class="seg" :class="{ on: applyAction === 'CLOSE' }" @tap="applyAction = 'CLOSE'">下架</view>
        </view>
      </view>
      <view class="form-block">
        <text class="form-label">说明（选填）</text>
        <textarea class="form-textarea" v-model="applyReason" placeholder="请描述调整/下架原因…" maxlength="500" :auto-height="true" />
      </view>
      <view class="sheet-submit">
        <AppButton text="提交申请" :loading="applySubmitting" @click="submitCanteenApply" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import DishCard from '@/components/DishCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import CardSection from '@/components/CardSection.vue'
import AppButton from '@/components/AppButton.vue'
import type { StallInfo, DishPreview } from '@/components/StallCard.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import type { Dish } from '@/types/dish'
import { getCanteensWithStalls } from '@/api/canteen'
import { getStallDishes, searchDishes } from '@/api/dish'
import { submitApply } from '@/api/apply'
import * as momentApi from '@/api/moment'
import type { Moment } from '@/types/moment'
import { EMOJI } from '@/utils/emoji'

const dishStore = useDishStore()
const userStore = useUserStore()
const canteenName = ref('')
const canteenId = ref(0)
const stallList = ref<StallInfo[]>([])
const canteenDishes = ref<Dish[]>([])
const relatedMoments = ref<Moment[]>([])
const momentPressedId = ref(0)
/** 档口筛选：'' = 全部；其余为 stall.name */
const activeStall = ref('')

/** 按档口筛选后的菜品（合并浏览流，档口与菜品不再分两个区块） */
const filteredDishes = computed(() => {
  if (!activeStall.value) return canteenDishes.value
  return canteenDishes.value.filter((d) => d.stallName === activeStall.value)
})

function goMoment(id: number) {
  uni.navigateTo({ url: `/pages/pages-detail/moment?id=${id}` })
}

function goCommunity() {
  uni.navigateTo({ url: '/pages/community/index' })
}

/** 关联动态（task-12.6，GET /moments?canteenId= 聚合） */
async function loadRelatedMoments() {
  if (!canteenId.value) { relatedMoments.value = []; return }
  try {
    const res = await momentApi.getMoments({ canteenId: canteenId.value, pageSize: 10 })
    relatedMoments.value = res.list
  } catch {
    relatedMoments.value = []
  }
}

function firstImage(value: unknown): string {
  return Array.isArray(value) ? (value.find(item => typeof item === 'string') || '') : ''
}

async function loadStalls() {
  if (!canteenName.value) return
  const canteens = await getCanteensWithStalls()
  const current = canteens.find((item: any) => item.name === canteenName.value)
  canteenId.value = Number(current?.id || 0)
  const stalls = current?.stalls || []
  stallList.value = await Promise.all(stalls.map(async (stall: any) => {
    let dishes: Dish[] = []
    try {
      dishes = await getStallDishes(canteenName.value, stall.name)
    } catch {
      dishes = []
    }
    return {
      id: Number(stall.id || 0),
      name: stall.name || '',
      location: stall.location || current?.location || canteenName.value,
      dishCount: dishes.length,
      image: firstImage(stall.images),
      rating: stall.avgRating ?? stall.rating ?? 0,
      ratingCount: dishes.reduce((sum, d) => sum + (d.ratingCount || 0), 0),
      dishes: dishes.slice(0, 10).map(d => ({
        id: d.id,
        name: d.name,
        price: d.price,
        image: d.image,
      })),
    }
  }))
  // 食堂全部菜品（按食堂名搜索）
  try {
    canteenDishes.value = await searchDishes({ canteen: canteenName.value })
  } catch {
    canteenDishes.value = []
  }
  await loadRelatedMoments()
}

function goToStall(stall: StallInfo) {
  dishStore.navParams.stallName = stall.name
  dishStore.navParams.canteen = canteenName.value
  uni.navigateTo({ url: '/pages/pages-detail/stall' })
}

function goToDetail(dish: DishPreview) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

/** 快捷申请调整/下架（task-12.1，POST /my/apply，CLOSE/CHANGE + entityId=当前食堂） */
const applyOpen = ref(false)
const applyAction = ref<'CLOSE' | 'CHANGE'>('CHANGE')
const applyReason = ref('')
const applySubmitting = ref(false)

function openApply() {
  if (!userStore.requireAuth()) return
  if (!canteenId.value) {
    uni.showToast({ title: '食堂信息缺失，无法申请', icon: 'none' })
    return
  }
  applyAction.value = 'CHANGE'
  applyReason.value = ''
  applyOpen.value = true
}

async function submitCanteenApply() {
  if (!canteenId.value) return
  applySubmitting.value = true
  try {
    await submitApply({
      entityType: 'CANTEEN',
      applyType: applyAction.value,
      entityId: canteenId.value,
      payload: { reason: applyReason.value.trim() },
    })
    uni.showToast({ title: '申请已提交', icon: 'success' })
    applyOpen.value = false
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    applySubmitting.value = false
  }
}

onLoad(async (query) => {
  if (query?.canteen) {
    canteenName.value = decodeURIComponent(query.canteen as string)
  }
  await loadStalls()
})
</script>

<style scoped>
.stall-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-page);
}
.scroll-wrap {
  flex: 1;
  overflow-y: auto;
}

/* 菜品区：补齐横向 padding，避免左列贴屏幕边 */
.dish-section {
  padding: 0 var(--spacing-md);
  box-sizing: border-box;
}

/* 关联动态 */
.moment-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.moment-item { padding: var(--spacing-sm) var(--spacing-md); background: var(--bg-soft); border-radius: var(--radius-card); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.moment-item.pressed { transform: scale(0.985); }
.moment-text { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.5; }
.moment-meta { display: flex; align-items: center; justify-content: space-between; margin-top: 6rpx; }
.moment-author { font-size: var(--font-aux); color: var(--text-tertiary); }
.moment-count { font-size: var(--font-aux); color: var(--text-tertiary); }

/* 档口筛选条：横向滚动胶囊，点选筛选菜品（档口与菜品合并浏览） */
.stall-filter { padding: var(--spacing-sm) var(--spacing-md) 0; box-sizing: border-box; }
.stall-filter-scroll { white-space: nowrap; }
.stall-chip {
  display: inline-block;
  padding: var(--spacing-xs) var(--spacing-lg);
  margin-right: var(--spacing-sm);
  border-radius: var(--radius-tag);
  background: var(--bg-soft);
  font-size: var(--font-aux);
  color: var(--text-secondary);
  font-weight: 600;
  transition: background 0.15s, transform 0.12s, color 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.stall-chip:active { transform: scale(0.97); }
.stall-chip.on { background: var(--color-primary); color: var(--text-white); }

/* 关联动态 - 查看全部 */
.moment-more {
  margin-top: var(--spacing-xs);
  text-align: right;
  font-size: var(--font-aux);
  color: var(--color-primary);
  padding: var(--spacing-xs) var(--spacing-md);
}

/* 申请入口：不常用，降级为底部弱化的小文字链接（不再横卡置顶） */
.apply-link {
  display: flex;
  justify-content: center;
  padding: var(--spacing-md) 0 var(--spacing-sm);
  -webkit-tap-highlight-color: transparent;
}
.apply-link:active { opacity: 0.6; }
.apply-link-text { font-size: var(--font-aux); color: var(--text-tertiary); }

/* 申请 Sheet（task-12.1，保留底部弹层表单） */
.apply-sheet { position: fixed; left: 0; right: 0; bottom: 0; background: var(--bg-card); border-radius: var(--radius-modal) var(--radius-modal) 0 0; box-shadow: var(--shadow-modal); z-index: 100; transform: translateY(100%); transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1); padding-bottom: calc(var(--spacing-lg) + env(safe-area-inset-bottom)); }
.apply-sheet.open { transform: translateY(0); }
.form-block { padding: var(--spacing-md) var(--spacing-lg); border-bottom: 2rpx solid var(--border-color); }
.form-label { display: block; font-size: var(--font-aux); font-weight: 700; color: var(--text-secondary); margin-bottom: var(--spacing-sm); }
.seg-row { display: flex; gap: var(--spacing-sm); }
.seg { padding: var(--spacing-xs) var(--spacing-lg); border-radius: var(--radius-tag); background: var(--bg-soft); font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; transition: background 0.15s, transform 0.12s; -webkit-tap-highlight-color: transparent; }
.seg:active { transform: scale(0.97); }
.seg.on { background: var(--color-primary); color: var(--text-white); }
.form-textarea { width: 100%; min-height: 160rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: var(--spacing-sm) var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; box-sizing: border-box; }
.sheet-submit { padding: var(--spacing-md) var(--spacing-lg); }

@media (prefers-reduced-motion: reduce) {
  .apply-sheet { transition: opacity 0.2s ease; }
}

</style>
