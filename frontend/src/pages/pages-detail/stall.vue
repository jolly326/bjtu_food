<template>
  <view class="page stall-detail-page">
    <Header :title="stallDetail?.name || '档口'" showBack />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <template v-if="stallDetail">
        <ImageSwiper :images="stallDetail.images" />
        <view class="info-section">
          <text class="info-name">{{ stallDetail.name }}</text>
          <view class="info-location">
            <text class="info-location-icon">{{ EMOJI.location }}</text>
            <text class="info-location-text">{{ stallDetail.location }}</text>
          </view>
          <view class="info-desc">
            <text class="info-desc-text">{{ stallDetail.description }}</text>
          </view>
        </view>
        <CardSection>
          <view class="apply-row" @tap="openApply">
            <text class="apply-icon">{{ EMOJI.edit }}</text>
            <view class="apply-body">
              <text class="apply-title">申请关闭 / 纠错</text>
              <text class="apply-sub">档口信息有误或需关闭？向管理员发起申请</text>
            </view>
            <text class="apply-arrow">{{ EMOJI.arrowRight }}</text>
          </view>
        </CardSection>
        <view class="dish-section">
          <text class="dish-section-title">全部菜品（{{ dishList.length }}）</text>
          <view v-if="dishList.length > 0" class="dish-list">
            <view v-for="dish in dishList" :key="dish.id" class="dish-row" @click="goToDetail(dish)">
              <view class="dish-row-img">
                <ImageFallback :src="dish.image" />
              </view>
              <view class="dish-row-info">
                <text class="dish-row-name">{{ dish.name }}</text>
                <view v-if="dish.tags?.length" class="dish-row-tags">
                  <TagLabel v-for="tag in dish.tags" :key="tag" :text="tag" />
                </view>
                <view class="dish-row-meta">
                  <text class="dish-row-star">{{ EMOJI.starFilled }}</text>
                  <text class="dish-row-rating">{{ dish.rating }}</text>
                </view>
              </view>
              <text class="dish-row-price">¥{{ dish.price }}</text>
            </view>
          </view>
        </view>

        <!-- ===== task-12.6 关联动态聚合（GET /moments?stallId=） ===== -->
        <CardSection title="关联动态">
          <EmptyState v-if="relatedMoments.length === 0" text="暂无关联动态" icon="💬" />
          <view v-else class="moment-list">
            <view
              v-for="m in relatedMoments"
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
          </view>
        </CardSection>
      </template>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 申请关闭/纠错 Sheet（task-12.1） -->
    <view v-if="applyOpen" class="sheet-mask" @tap="applyOpen = false" />
    <view class="apply-sheet" :class="{ open: applyOpen }">
      <view class="sheet-head">
        <text class="sheet-title">申请关闭 / 纠错</text>
        <text class="sheet-close" @tap="applyOpen = false">✕</text>
      </view>
      <view class="form-block">
        <text class="form-label">申请动作</text>
        <view class="seg-row">
          <view class="seg" :class="{ on: applyAction === 'CLOSE' }" @tap="applyAction = 'CLOSE'">关闭</view>
          <view class="seg" :class="{ on: applyAction === 'CHANGE' }" @tap="applyAction = 'CHANGE'">纠错 / 变更</view>
        </view>
      </view>
      <view class="form-block">
        <text class="form-label">说明（选填）</text>
        <textarea class="form-textarea" v-model="applyReason" placeholder="请描述关闭/纠错原因…" maxlength="500" :auto-height="true" />
      </view>
      <view class="sheet-submit">
        <AppButton text="提交申请" :loading="applySubmitting" @click="submitStallApply" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Header from '@/components/header.vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import ImageFallback from '@/components/ImageFallback.vue'
import TagLabel from '@/components/TagLabel.vue'
import CardSection from '@/components/CardSection.vue'
import AppButton from '@/components/AppButton.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { EMOJI } from '@/utils/emoji'
import { getStallDetail } from '@/api/canteen'
import { submitApply } from '@/api/apply'
import * as momentApi from '@/api/moment'
import type { StallDetail } from '@/types/canteen'
import type { Dish } from '@/types/dish'
import type { Moment } from '@/types/moment'

const dishStore = useDishStore()
const userStore = useUserStore()
const stallDetail = ref<StallDetail | null>(null)
const dishList = computed(() => dishStore.stallDishes)
const refresherTriggered = ref(false)
const relatedMoments = ref<Moment[]>([])
const momentPressedId = ref(0)

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

function goMoment(id: number) {
  uni.navigateTo({ url: `/pages/pages-detail/moment?id=${id}` })
}

/** 关联动态（task-12.6，GET /moments?stallId=） */
async function loadRelatedMoments() {
  if (!stallDetail.value?.id) { relatedMoments.value = []; return }
  try {
    const res = await momentApi.getMoments({ stallId: stallDetail.value.id, pageSize: 10 })
    relatedMoments.value = res.list
  } catch {
    relatedMoments.value = []
  }
}

/** 快捷申请关闭/纠错（task-12.1，POST /my/apply，CLOSE/CHANGE + entityId=当前档口） */
const applyOpen = ref(false)
const applyAction = ref<'CLOSE' | 'CHANGE'>('CLOSE')
const applyReason = ref('')
const applySubmitting = ref(false)

function openApply() {
  if (!userStore.requireAuth()) return
  if (!stallDetail.value?.id) {
    uni.showToast({ title: '档口信息缺失，无法申请', icon: 'none' })
    return
  }
  applyAction.value = 'CLOSE'
  applyReason.value = ''
  applyOpen.value = true
}

async function submitStallApply() {
  if (!stallDetail.value?.id) return
  applySubmitting.value = true
  try {
    await submitApply({
      entityType: 'STALL',
      applyType: applyAction.value,
      entityId: stallDetail.value.id,
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

async function loadData() {
  const { stallName, canteen } = dishStore.navParams
  if (stallName && canteen) {
    const [detail] = await Promise.all([
      getStallDetail(canteen, stallName),
      dishStore.fetchStallDishes(canteen, stallName),
    ])
    stallDetail.value = detail
    await loadRelatedMoments()
  }
}

onMounted(() => { loadData() })

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => { refresherTriggered.value = false })
}
</script>

<style scoped>
.stall-detail-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.info-section { margin: var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-card); }
.info-name { font-size: var(--font-subtitle); font-weight: 700; color: var(--text-primary); display: block; margin-bottom: var(--spacing-sm); }
.info-location { display: flex; align-items: center; gap: var(--spacing-xs); margin-bottom: var(--spacing-sm); padding-bottom: var(--spacing-sm); border-bottom: 2rpx solid var(--bg-page); }
.info-location-icon { font-size: 28rpx; line-height: 1; flex-shrink: 0; }
.info-location-text { font-size: var(--font-small); color: var(--text-secondary); }
.info-desc-text { font-size: var(--font-small); color: var(--text-secondary); line-height: 1.6; display: block; }
.dish-section { margin: 0 var(--spacing-md); }
.dish-section-title { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); display: block; margin-bottom: var(--spacing-sm); padding-left: var(--spacing-xs); }
.dish-list { background: var(--bg-card); border-radius: var(--radius-card); overflow: hidden; }
.dish-row { display: flex; align-items: flex-start; gap: var(--spacing-sm); padding: var(--spacing-md) var(--spacing-sm); border-bottom: 2rpx solid var(--bg-page); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.dish-row:active { transform: scale(var(--press-scale)); }
.dish-row:last-child { border-bottom: none; }
.dish-row-img { width: 140rpx; height: 140rpx; border-radius: var(--radius-tag); overflow: hidden; flex-shrink: 0; background: var(--bg-page); }
.dish-row-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); }
.dish-row-name { font-size: var(--font-caption); font-weight: 500; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dish-row-tags { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); }
.dish-row-meta { display: flex; align-items: center; gap: var(--spacing-xs); }
.dish-row-star { font-size: 28rpx; line-height: 1; flex-shrink: 0; }
.dish-row-rating { font-size: var(--font-card); color: var(--color-star); }
.dish-row-price { font-size: var(--font-card); font-weight: 700; color: var(--color-price); flex-shrink: 0; margin-left: var(--spacing-xs); }

/* 关联动态 */
.moment-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.moment-item { padding: var(--spacing-sm) var(--spacing-md); background: var(--bg-soft); border-radius: var(--radius-card); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.moment-item.pressed { transform: scale(0.985); }
.moment-text { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.5; }
.moment-meta { display: flex; align-items: center; justify-content: space-between; margin-top: 6rpx; }
.moment-author { font-size: var(--font-aux); color: var(--text-tertiary); }
.moment-count { font-size: var(--font-aux); color: var(--text-tertiary); }

/* 申请入口行 + Sheet（task-12.1） */
.apply-row { display: flex; align-items: center; gap: var(--spacing-sm); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.apply-row:active { transform: scale(var(--press-scale)); }
.apply-icon { font-size: 36rpx; line-height: 1; opacity: 0.6; flex-shrink: 0; }
.apply-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4rpx; }
.apply-title { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); }
.apply-sub { font-size: var(--font-aux); color: var(--text-tertiary); }
.apply-arrow { font-size: 28rpx; color: var(--text-tertiary); flex-shrink: 0; }
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
