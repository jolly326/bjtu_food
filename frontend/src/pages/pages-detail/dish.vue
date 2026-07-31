<template>
  <view class="page detail-page">
    <Header title="菜品详情" showBack />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <template v-if="dish">
        <ImageSwiper :images="dish.images || [dish.image]" />

        <CardSection>
          <view class="title-row" @longpress="onDishLongPress">
            <text class="dish-name">{{ dish.name }}</text>
            <view class="price-box">
              <block v-if="hasPromo">
                <text class="promo-price">¥{{ dish.promoPrice }}</text>
                <text class="origin-price">¥{{ dish.originalPrice }}</text>
                <text class="promo-tag"><IconSvg name="clock" :size="22" color="#E67E22" /> 限时</text>
              </block>
              <text v-else class="price-text">¥{{ dish.price }}</text>
            </view>
          </view>

          <view class="tag-row" v-if="dishTagList.length > 0">
            <TagLabel v-for="tag in dishTagList" :key="tag" :text="tag" />
          </view>

          <view class="rating-row">
            <text class="star-icon-img">{{ EMOJI.starFilled }}</text>
            <text class="rating-value">{{ dish.rating }}</text>
            <text class="rating-count">{{ dish.ratingCount }}条评价</text>
          </view>
        </CardSection>

        <!-- ===== task-03 合并卡片：位置与营业 / 菜品属性 / 菜品介绍，单卡内分区 ===== -->
        <CardSection title="菜品信息">
          <!-- 分区一：位置与营业 -->
          <view class="info-block">
            <view class="location-chain" @tap="goToCanteen">
              <text class="chain-node">{{ dish.canteen || '未知食堂' }}</text>
              <text class="chain-sep">›</text>
              <text class="chain-node" v-if="dish.floor">{{ dish.floor }}</text>
              <text class="chain-sep" v-if="dish.floor">›</text>
              <text class="chain-node">{{ dish.stallName || '档口' }}</text>
              <text class="chain-sep" v-if="dish.windowNo">›</text>
              <text class="chain-node chain-window" v-if="dish.windowNo">窗口 {{ dish.windowNo }}</text>
            </view>
            <view class="biz-hours" v-if="dish.businessHours" @tap="goToStall">
              <text class="biz-icon">{{ EMOJI.clock }}</text>
              <text class="biz-text">营业时间：{{ dish.businessHours }}</text>
              <text class="biz-arrow">›</text>
            </view>
          </view>

          <!-- 分区二：菜品属性（有则展示，用分隔线与上区隔开） -->
          <view class="info-block info-block-divider" v-if="attrTags.length > 0">
            <view class="attr-row">
              <view class="attr-item" v-if="spiceLabel">
                <text class="attr-icon">{{ EMOJI.chili }}</text>
                <text class="attr-text">{{ spiceLabel }}</text>
              </view>
              <view class="attr-item" v-if="portionLabel">
                <text class="attr-icon">{{ EMOJI.portion }}</text>
                <text class="attr-text">{{ portionLabel }}</text>
              </view>
              <view class="attr-item" v-if="dish.limited">
                <IconSvg name="clock" :size="26" color="var(--text-tertiary)" class="attr-icon" />
                <text class="attr-text">限量供应</text>
              </view>
              <view class="attr-item" v-for="p in servePeriodLabels" :key="p">
                <text class="attr-icon">{{ EMOJI.clock }}</text>
                <text class="attr-text">{{ p }}</text>
              </view>
            </view>
          </view>

          <!-- 分区三：菜品介绍（有则展示） -->
          <view class="info-block info-block-divider" v-if="dish.description">
            <text class="desc-content">{{ dish.description }}</text>
          </view>
        </CardSection>

        <!-- ===== task-03 评价区重做 ===== -->
        <CardSection>
          <SectionTitle title="用户评价" noMargin>
            <view class="review-sort" @tap="toggleSortSheet">
              <text class="review-sort-text">{{ sortLabel }}</text>
              <text class="review-sort-arrow">▾</text>
            </view>
          </SectionTitle>

          <!-- 晒图过滤开关 -->
          <view class="review-filter-row">
            <view class="review-filter-chip" :class="{ active: reviewOnlyImage }" @tap="toggleOnlyImage">
              <text class="review-filter-text">{{ EMOJI.image }} 只看有图</text>
            </view>
          </view>

          <view class="review-list" v-if="reviewList.length > 0">
            <view v-for="rv in reviewList.slice(0, 3)" :key="rv.id" class="review-item">
              <view class="review-header">
                <image v-if="rv.userAvatar" class="review-avatar" :src="getImageUrl(rv.userAvatar)" mode="aspectFill" />
                <view v-else class="review-avatar review-avatar-empty">
                  <text class="review-avatar-fallback">{{ EMOJI.dishPlaceholder }}</text>
                </view>
                <view class="review-header-right">
                  <view class="review-header-top">
                    <text class="review-name">{{ rv.userNickname }}</text>
                    <text class="review-time">{{ relativeTime(rv.createTime) }}</text>
                  </view>
                  <view class="review-stars">
                    <text v-for="i in starCount(rv.rating)" :key="i" class="review-star">{{ EMOJI.starFilled }}</text>
                  </view>
                </view>
              </view>
              <text class="review-content">{{ rv.content }}</text>
              <view v-if="rv.images && rv.images.length" class="review-images">
                <view v-for="(img, idx) in rv.images" :key="idx" class="review-image-wrapper">
                  <image class="review-image" :src="getImageUrl(img)" mode="aspectFill" @tap="previewImage(rv.images!, idx)" />
                </view>
              </view>
              <!-- 有用点赞（task-03 幂等切换） -->
              <view class="review-actions">
                <UsefulButton
                  :count="rv.usefulCount || 0"
                  :active="!!rv.useful"
                  @click="handleUseful(rv)"
                />
              </view>
            </view>
          </view>
          <EmptyState v-else text="暂无评价，来写第一条吧" />

          <view class="review-more-btn" v-if="reviewList.length > 0" @tap="goToReviewList">
            <text class="review-more-text">查看全部评价 ›</text>
          </view>
        </CardSection>

        <!-- 申请下架/纠错：不常用，降级为底部弱化的小文字链接，点击展开 Sheet -->
        <view class="apply-link" @tap="openApply">
          <text class="apply-link-text">反馈 / 申请下架 ›</text>
        </view>

      </template>
      <view style="height: var(--spacing-lg)"></view>
    </scroll-view>

    <!-- 底部操作栏 -->
    <view class="action-bar" v-if="dish">
      <view class="action-bar-btns">
        <AppButton text="写评价" type="outline" width="200rpx" margin="0 16rpx 0 0" @click="goToReview" />
        <AppButton text="去档口" width="240rpx" margin="0" @click="goToStall" />
      </view>
    </view>

    <!-- 排序切换底部 Sheet（spring 0.8/0.3） -->
    <view v-if="sortSheetOpen" class="sheet-mask" @tap="sortSheetOpen = false"></view>
    <view class="sort-sheet" :class="{ open: sortSheetOpen }">
      <view class="sort-sheet-head">
        <text class="sort-sheet-title">评价排序</text>
        <text class="sort-sheet-close" @tap="sortSheetOpen = false">✕</text>
      </view>
      <view
        class="sort-option"
        :class="{ active: reviewSort === 'latest' }"
        @tap="selectSort('latest')"
      >
        <text class="sort-option-text">最新</text>
        <text class="sort-option-check" v-if="reviewSort === 'latest'">✓</text>
      </view>
      <view
        class="sort-option"
        :class="{ active: reviewSort === 'useful' }"
        @tap="selectSort('useful')"
      >
        <text class="sort-option-text">最有用</text>
        <text class="sort-option-check" v-if="reviewSort === 'useful'">✓</text>
      </view>
    </view>
    <!-- 申请下架/纠错 Sheet（task-12.1） -->
    <view v-if="applyOpen" class="sheet-mask" @tap="applyOpen = false"></view>
    <view class="apply-sheet" :class="{ open: applyOpen }">
      <view class="sheet-head">
        <text class="sheet-title">申请下架 / 纠错</text>
        <text class="sheet-close" @tap="applyOpen = false">✕</text>
      </view>
      <view class="form-block">
        <text class="form-label">申请动作</text>
        <view class="seg-row">
          <view class="seg" :class="{ on: applyAction === 'CLOSE' }" @tap="applyAction = 'CLOSE'">下架</view>
          <view class="seg" :class="{ on: applyAction === 'CHANGE' }" @tap="applyAction = 'CHANGE'">纠错 / 变更</view>
        </view>
      </view>
      <view class="form-block">
        <text class="form-label">说明（选填）</text>
        <textarea class="form-textarea" v-model="applyReason" placeholder="请描述下架/纠错原因…" maxlength="500" :auto-height="true"></textarea>
      </view>
      <view class="sheet-submit">
        <AppButton text="提交申请" :loading="applySubmitting" @click="submitDishApply" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import TagLabel from '@/components/TagLabel.vue'
import UsefulButton from '@/components/UsefulButton.vue'
import EmptyState from '@/components/EmptyState.vue'
import AppButton from '@/components/AppButton.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import IconSvg from '@/components/IconSvg.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { deleteDish } from '@/api/dish'
import { submitApply } from '@/api/apply'
import { getImageUrl } from '@/utils/image'
import { EMOJI } from '@/utils/emoji'
import { toggleUseful } from '@/api/review'
import { SPICE_LEVELS, PORTION_LEVELS, SERVE_PERIOD_MAP } from '@/constants/categories'
import type { Review, ReviewSort } from '@/types/review'

const dishStore = useDishStore()
const userStore = useUserStore()

const refresherTriggered = ref(false)
const dish = computed(() => dishStore.currentDish!)
const reviewList = computed(() => dishStore.reviewList)
const reviewTotal = computed(() => dishStore.reviewTotal)
const reviewSort = computed(() => dishStore.reviewSort)
const reviewOnlyImage = computed(() => dishStore.reviewOnlyImage)
const dishId = computed(() => dish.value?.id ?? 0)

/** 折扣价展示：promoPrice 非空即视为有促销（task-12.9） */
const hasPromo = computed(() => !!dish.value?.promoPrice)

const sortSheetOpen = ref(false)

/** 标签列表：新菜 + 后端 tags（统一在标题下方展示） */
const dishTagList = computed(() => {
  const d = dish.value
  if (!d) return []
  const list: string[] = []
  if (d.isNew) list.push('新品')
  for (const t of d.tags) {
    if (!list.includes(t)) list.push(t)
  }
  return list
})

/** 辣度标签 */
const spiceLabel = computed(() => {
  const lv = dish.value?.spiceLevel
  if (lv == null) return ''
  return `辣度·${SPICE_LEVELS[lv] ?? '未知'}`
})
/** 分量标签 */
const portionLabel = computed(() => {
  const lv = dish.value?.portion
  if (lv == null) return ''
  return `分量·${PORTION_LEVELS[lv] ?? '未知'}`
})
/** 供应时段标签 */
const servePeriodLabels = computed(() => {
  const raw = dish.value?.servePeriod || ''
  if (!raw) return []
  return raw.split(',').map(s => s.trim()).filter(Boolean).map(key => SERVE_PERIOD_MAP[key] || key)
})
/** 属性标签聚合（用于 section 显隐） */
const attrTags = computed(() => [
  spiceLabel.value,
  portionLabel.value,
  dish.value?.limited ? 'limited' : '',
  ...servePeriodLabels.value,
].filter(Boolean))

const sortLabel = computed(() => reviewSort.value === 'useful' ? '最有用' : '最新')

function starCount(rating: number): number { return Math.round(rating) }

/** 评价「有用」切换（幂等；未登录引导） */
async function handleUseful(rv: Review) {
  if (!userStore.requireAuth()) return
  const prevUseful = !!rv.useful
  const prevCount = rv.usefulCount || 0
  // 乐观更新
  rv.useful = !prevUseful
  rv.usefulCount = prevUseful ? Math.max(0, prevCount - 1) : prevCount + 1
  try {
    const res = await toggleUseful(rv.id)
    // 以服务端返回为准（幂等、一人一票）
    rv.useful = res.useful
    rv.usefulCount = res.usefulCount
  } catch {
    // 回滚
    rv.useful = prevUseful
    rv.usefulCount = prevCount
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}

function toggleSortSheet() {
  sortSheetOpen.value = true
}

function selectSort(sort: ReviewSort) {
  sortSheetOpen.value = false
  if (dishStore.reviewSort === sort) return
  dishStore.fetchReviews(dishId.value, { sort, isWithImage: reviewOnlyImage.value })
}

function toggleOnlyImage() {
  dishStore.fetchReviews(dishId.value, { sort: reviewSort.value, isWithImage: !reviewOnlyImage.value })
}

function goToReview() {
  if (!userStore.requireAuth()) return
  uni.navigateTo({ url: `/pages/pages-detail/review?dishId=${dishId.value}` })
}

/** 快捷申请下架/纠错（task-12.1，POST /my/apply，CLOSE/CHANGE + entityId=当前菜品） */
const applyOpen = ref(false)
const applyAction = ref<'CLOSE' | 'CHANGE'>('CLOSE')
const applyReason = ref('')
const applySubmitting = ref(false)

function openApply() {
  if (!userStore.requireAuth()) return
  applyAction.value = 'CLOSE'
  applyReason.value = ''
  applyOpen.value = true
}

async function submitDishApply() {
  applySubmitting.value = true
  try {
    await submitApply({
      entityType: 'DISH',
      applyType: applyAction.value,
      entityId: dishId.value,
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

/** 删除本人发布的菜品（task-12.5，仅 created_by 本人） */
function onDishLongPress() {
  const d = dish.value
  if (!d) return
  if (!userStore.userInfo || (d.createdBy != null && d.createdBy !== userStore.userInfo.id)) return
  uni.showModal({
    title: '删除菜品',
    content: '确定删除你发布的这道菜品吗？删除后不可恢复。',
    success: async (res) => {
      if (res.confirm) {
        try {
          await deleteDish(d.id)
          uni.showToast({ title: '已删除', icon: 'none' })
          setTimeout(() => uni.navigateBack(), 500)
        } catch (e: any) {
          uni.showToast({ title: e.message || '删除失败', icon: 'none' })
        }
      }
    },
  })
}

function goToReviewList() {
  uni.navigateTo({ url: `/pages/pages-detail/review-list?dishId=${dishId.value}` })
}

function goToStall() {
  if (dish.value) {
    dishStore.navParams.stallName = dish.value.stallName
    dishStore.navParams.canteen = dish.value.canteen
    uni.navigateTo({ url: '/pages/pages-detail/stall' })
  }
}

function goToCanteen() {
  if (dish.value?.canteen) {
    uni.navigateTo({ url: `/pages/pages-detail/canteen?canteen=${encodeURIComponent(dish.value.canteen)}` })
  }
}

function previewImage(images: string[], current: number) {
  uni.previewImage({ urls: images.map(getImageUrl), current: getImageUrl(images[current]) })
}

function relativeTime(dateStr: string): string {
  if (!dateStr) return ''
  const now = Date.now()
  const then = new Date(dateStr).getTime()
  const diff = Math.floor((now - then) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`
  return dateStr
}

let currentDishId = 0

async function loadDishData() {
  if (!currentDishId) return
  await Promise.all([
    dishStore.fetchDetail(currentDishId),
    dishStore.fetchReviews(currentDishId, { sort: 'latest', isWithImage: false }),
  ])
}

onLoad((query) => {
  if (query?.id) {
    currentDishId = Number(query.id)
    loadDishData()
  }
})

onUnmounted(() => {})

function onRefresh() {
  if (refresherTriggered.value || !currentDishId) return
  refresherTriggered.value = true
  loadDishData().finally(() => { refresherTriggered.value = false })
}
</script>

<style scoped>
.detail-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding-bottom: calc(140rpx + env(safe-area-inset-bottom)); }
.title-row { display: flex; align-items: baseline; justify-content: space-between; gap: var(--spacing-sm); }
.dish-name { font-size: var(--font-h1); font-weight: 700; letter-spacing: -0.02em; line-height: 1.2; color: var(--text-primary); flex: 1; min-width: 0; }
.price-text { font-size: var(--font-h2); font-weight: 700; color: var(--color-price); flex-shrink: 0; }
.price-box { display: flex; align-items: baseline; gap: var(--spacing-xs); flex-shrink: 0; flex-wrap: wrap; justify-content: flex-end; }
.promo-price { font-size: var(--font-h2); font-weight: 800; color: var(--color-error); }
.origin-price { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; }
.promo-tag { font-size: 20rpx; font-weight: 700; color: var(--text-white); background: var(--color-error); padding: 0 var(--spacing-xs); border-radius: var(--radius-icon); }
.tag-row { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); margin-top: var(--spacing-sm); }
.rating-row { display: flex; align-items: center; gap: var(--spacing-xs); margin-top: var(--spacing-md); padding-top: var(--spacing-sm); }
.star-icon-img { font-size: var(--icon-sm); line-height: 1; }
.rating-value { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); }
.rating-count { font-size: var(--font-aux); color: var(--text-tertiary); }

/* 位置链路 */
.location-chain { display: flex; align-items: center; flex-wrap: wrap; gap: var(--spacing-xs); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.location-chain:active { transform: scale(var(--press-scale)); }
.chain-node { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); }
.chain-window { color: var(--color-primary); }
.chain-sep { font-size: var(--font-body); color: var(--text-tertiary); }
.biz-hours { display: flex; align-items: center; gap: var(--spacing-xs); margin-top: var(--spacing-sm); padding-top: var(--spacing-sm); border-top: 2rpx solid var(--border-color); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.biz-hours:active { transform: scale(var(--press-scale)); }
.biz-icon { font-size: var(--icon-sm); line-height: 1; }
.biz-text { font-size: var(--font-aux); color: var(--text-secondary); flex: 1; min-width: 0; }
.biz-arrow { font-size: var(--icon-sm); color: var(--text-tertiary); }

/* 属性标签 */
.attr-row { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.attr-item { display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); background: var(--bg-soft); border-radius: var(--radius-tag); }
.attr-icon { font-size: 24rpx; line-height: 1; }
.attr-text { font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; }

.desc-content { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.6; display: block; }

/* 合并卡片内分区（位置/属性/介绍）：用分隔线区分，不拆多卡 */
.info-block { padding: 0; }
.info-block-divider { margin-top: var(--spacing-md); padding-top: var(--spacing-md); border-top: 2rpx solid var(--border-color); }
.review-title { font-size: var(--font-body); font-weight: 600; color: var(--text-primary); }
.review-more { font-size: var(--font-aux); color: var(--color-primary); }
.review-list { margin-top: var(--spacing-sm); }
.review-item { padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); }
.review-item:last-child { border-bottom: none; }
.review-header { display: flex; gap: var(--spacing-sm); align-items: stretch; margin-bottom: var(--spacing-xs); }
.review-avatar { width: 64rpx; height: 64rpx; border-radius: 50%; flex-shrink: 0; background: var(--bg-page); }
.review-avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--border-color); }
.review-avatar-fallback { font-size: 32rpx; line-height: 1; }
.review-header-right { flex: 1; display: flex; flex-direction: column; justify-content: space-between; min-height: 64rpx; }
.review-header-top { display: flex; align-items: center; justify-content: space-between; }
.review-name { font-size: var(--font-headline); font-weight: 500; color: var(--text-primary); }
.review-time { font-size: var(--font-aux); color: var(--text-tertiary); }
.review-stars { display: flex; align-items: center; gap: var(--spacing-xs); }
.review-star { font-size: var(--font-tiny); line-height: 1; }
.review-content { margin: var(--spacing-sm) 0; font-size: var(--font-body); color: var(--text-secondary); line-height: 1.4; display: block; }
.review-images { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.review-actions { margin-top: var(--spacing-xs); display: flex; justify-content: flex-end; }
.review-image-wrapper { width: 200rpx; height: 200rpx; border-radius: var(--radius-tag); overflow: hidden; background: var(--bg-page); flex-shrink: 0; }
.review-image { width: 100%; height: 100%; display: block; }
/* 底部操作栏（Apple §12 半透材质 + 顶部高光边） */
.action-bar { position: fixed; left: 0; right: 0; bottom: 0; display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--glass-highlight-soft); z-index: 50; }
.fav-btn { display: flex; flex-direction: column; align-items: center; justify-content: center; width: 96rpx; gap: var(--spacing-xs); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.fav-btn:active { transform: scale(var(--press-scale)); }
.fav-icon { font-size: 40rpx; line-height: 1; opacity: 0.35; }
.fav-btn.active .fav-icon { opacity: 1; }
.fav-text { font-size: 20rpx; color: var(--text-tertiary); }
.fav-btn.active .fav-text { color: var(--color-like); }
.fav-btn { display: flex; flex-direction: column; align-items: center; justify-content: center; width: 96rpx; gap: var(--spacing-xs); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.fav-btn:active { transform: scale(var(--press-scale)); }
.fav-icon { font-size: 40rpx; line-height: 1; opacity: 0.35; }
.fav-btn.active .fav-icon { opacity: 1; }
.fav-text { font-size: 20rpx; color: var(--text-tertiary); }
.fav-btn.active .fav-text { color: var(--color-like); }
.action-bar-btns { flex: 1; display: flex; justify-content: flex-end; }

/* 评价区头部（SectionTitle + 排序 extra） */
.review-sort { display: flex; align-items: center; gap: 4rpx; padding: 4rpx 12rpx; border-radius: var(--radius-tag); background: var(--bg-soft); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.review-sort:active { transform: scale(0.97); }
.review-sort-text { font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; }
.review-sort-arrow { font-size: var(--font-aux); color: var(--text-tertiary); }
.review-filter-row { margin-top: var(--spacing-sm); }
.review-filter-chip { display: inline-flex; align-items: center; gap: var(--spacing-xs); padding: var(--spacing-xs) var(--spacing-md); border-radius: var(--radius-tag); background: var(--bg-soft); transition: background 0.15s, transform 0.12s; -webkit-tap-highlight-color: transparent; }
.review-filter-chip.active { background: var(--color-primary-soft); }
.review-filter-chip:active { transform: scale(0.97); }
.review-filter-text { font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; }
.review-filter-chip.active .review-filter-text { color: var(--color-primary); }
.review-more-btn { margin-top: var(--spacing-sm); display: flex; justify-content: center; }
.review-more-text { font-size: var(--font-aux); color: var(--color-primary); font-weight: 600; }

/* 排序 Sheet（spring 0.8/0.3） */
.sheet-mask { position: fixed; inset: 0; background: var(--overlay-scrim); z-index: 90; }
.sort-sheet {
  position: fixed;
  left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 100;
  transform: translateY(100%);
  transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1);
  padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom));
}
.sort-sheet.open { transform: translateY(0); }
.sort-sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sort-sheet-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); }
.sort-sheet-close { font-size: var(--font-body); color: var(--text-tertiary); padding: 0 var(--spacing-xs); }
.sort-option { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); transition: background 0.15s; -webkit-tap-highlight-color: transparent; }
.sort-option:last-child { border-bottom: none; }
.sort-option.active { background: var(--bg-soft); }
.sort-option-text { font-size: var(--font-body); color: var(--text-primary); font-weight: 600; }
.sort-option-check { font-size: var(--font-body); color: var(--color-primary); font-weight: 800; }

/* 申请入口：不常用，降级为底部弱化的小文字链接（点击展开 Sheet） */
.apply-link { display: flex; justify-content: center; padding: var(--spacing-md) 0 var(--spacing-sm); -webkit-tap-highlight-color: transparent; }
.apply-link:active { opacity: 0.6; }
.apply-link-text { font-size: var(--font-aux); color: var(--text-tertiary); }
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
  .sort-sheet, .apply-sheet { transition: opacity 0.2s ease; }
}
</style>
