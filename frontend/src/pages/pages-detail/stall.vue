<template>
  <view class="page stall-detail-page">
    <Header :title="stallDetail?.name || '档口'" showBack />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" @refresherrefresh="onRefresh">
      <!-- 加载骨架 -->
      <view v-if="loading" class="stall-skeleton">
        <view class="sk-swiper skeleton" />
        <view class="sk-info skeleton" />
        <view class="sk-dish" v-for="s in 3" :key="s">
          <view class="sk-dish-img skeleton" />
          <view class="sk-dish-body">
            <view class="sk-line skeleton" />
            <view class="sk-line sk-line-short skeleton" />
          </view>
        </view>
      </view>

      <template v-else-if="stallDetail">
        <!-- 横幅：档口图集 -->
        <ImageSwiper :images="stallDetail.images" />

        <!-- hero 卡：档口名 / 位置 / 星级 / 简介（合并单卡，含弱化反馈入口） -->
        <CardSection>
          <SectionTitle title="档口信息" noMargin>
            <template #extra>
              <text class="feedback-link" @tap="openApply">反馈信息有误</text>
            </template>
          </SectionTitle>
          <view class="info-body">
            <view class="info-head">
              <text class="info-name">{{ stallDetail.name }}</text>
              <view class="info-rating" v-if="stallDetail.avgRating != null && stallDetail.avgRating > 0">
                <IconSvg name="star-filled" :size="26" color="var(--color-star)" class="info-rating-icon" />
                <text class="info-rating-text">{{ stallDetail.avgRating.toFixed(1) }}</text>
              </view>
            </view>
            <view class="info-location">
              <IconSvg name="location" :size="26" color="var(--color-primary)" class="info-location-icon" />
              <text class="info-location-text">{{ stallDetail.location }}</text>
            </view>
            <view class="info-desc" v-if="stallDetail.description">
              <text class="info-desc-text">{{ stallDetail.description }}</text>
            </view>
          </view>
        </CardSection>

        <!-- 内容区随 tab 切换（三段，互斥，不构成 v-if 链） -->
        <!-- ===== 菜品 tab：左侧分类侧栏 + 右侧菜品瀑布流（美团外卖式） ===== -->
        <view v-show="activeTab === 'dishes'" class="dishes-pane">
          <scroll-view class="cat-sidebar" scroll-y :show-scrollbar="false">
            <view
              v-for="cat in categories"
              :key="cat.key"
              class="cat-item"
              :class="{ active: activeCategory === cat.key }"
              @tap="activeCategory = cat.key"
            >
              <text class="cat-label">{{ cat.label }}</text>
              <view class="cat-dot" v-if="activeCategory === cat.key" />
            </view>
          </scroll-view>
          <view class="dishes-waterfall">
            <WaterfallList
              v-if="filteredDishes.length > 0"
              :list="filteredDishes"
              @card-click="goToDetail"
            />
            <EmptyState v-else text="该分类暂无菜品" />
          </view>
        </view>

        <!-- ===== 评价 tab：内联评价列表 ===== -->
        <CardSection v-show="activeTab === 'reviews'" title="">
          <SectionTitle :title="`用户评价 (${reviewTotal})`" noMargin />
          <view class="review-list" v-if="reviewList.length > 0">
            <ReviewItem
              v-for="rv in reviewList"
              :key="rv.id"
              :review="rv"
            />
          </view>
          <EmptyState v-else text="暂无评价，来写第一条吧" />
        </CardSection>

        <!-- ===== 档口介绍 tab：完整介绍（单卡内有序分区，不再拆多卡） ===== -->
        <CardSection v-show="activeTab === 'intro'" title="">
          <SectionTitle title="档口介绍" noMargin />
          <view class="intro-row">
            <IconSvg name="location" :size="28" color="var(--text-tertiary)" class="intro-row-icon" />
            <text class="intro-row-label">所在位置</text>
            <text class="intro-row-value">{{ stallDetail.location }}</text>
          </view>
          <view class="intro-row" v-if="stallRatingText">
            <IconSvg name="star-filled" :size="28" color="var(--color-star)" class="intro-row-icon" />
            <text class="intro-row-label">档口评分</text>
            <text class="intro-row-value">{{ stallRatingText }}</text>
          </view>
          <view class="intro-row" v-if="stallBusinessHours">
            <IconSvg name="clock" :size="28" color="var(--text-tertiary)" class="intro-row-icon" />
            <text class="intro-row-label">营业时间</text>
            <text class="intro-row-value">{{ stallBusinessHours }}</text>
          </view>
          <view class="intro-desc" v-if="stallDetail.description">
            <text class="intro-desc-label">档口简介</text>
            <text class="intro-desc-text">{{ stallDetail.description }}</text>
          </view>
        </CardSection>
      </template>

      <!-- 加载失败 / 无数据空态 -->
      <EmptyState v-else text="档口信息加载失败" :retry="true" @retry="loadData" />
      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 底部固定 tabBar（菜品 / 评价 / 档口介绍，安全区避让） -->
    <view class="detail-tabs" v-if="stallDetail">
      <view
        class="tab"
        :class="{ active: activeTab === 'dishes' }"
        @tap="activeTab = 'dishes'"
      >
        <text>菜品（{{ dishList.length }}）</text>
      </view>
      <view
        class="tab"
        :class="{ active: activeTab === 'reviews' }"
        @tap="activeTab = 'reviews'"
      >
        <text>评价（{{ reviewTotal }}）</text>
      </view>
      <view
        class="tab"
        :class="{ active: activeTab === 'intro' }"
        @tap="activeTab = 'intro'"
      >
        <text>档口介绍</text>
      </view>
    </view>

    <!-- 申请关闭/纠错 Sheet（共享组件） -->
    <ApplySheet
      :open="applyOpen"
      entity-type="STALL"
      :entity-id="stallDetail?.id || 0"
      @update:open="applyOpen = $event"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Header from '@/components/header.vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconSvg from '@/components/IconSvg.vue'
import ApplySheet from '@/components/ApplySheet.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import WaterfallList from '@/components/WaterfallList.vue'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { getStallDetail } from '@/api/canteen'
import { getReviewsByStall } from '@/api/review'
import { DISH_CATEGORIES } from '@/constants/categories'
import type { StallDetail } from '@/types/canteen'
import type { Dish } from '@/types/dish'
import type { Review } from '@/types/review'

type StallTab = 'dishes' | 'reviews' | 'intro'

const dishStore = useDishStore()
const userStore = useUserStore()
const stallDetail = ref<StallDetail | null>(null)
const dishList = computed(() => dishStore.stallDishes)
const refresherTriggered = ref(false)
const loading = ref(true)

/** 用户评价区（内联展示全部） */
const reviewList = ref<Review[]>([])
const reviewTotal = ref(0)
const currentStallId = ref(0)

/** 三段 tab 切换状态（菜品 / 评价 / 档口介绍） */
const activeTab = ref<StallTab>('dishes')

/** 菜品分类筛选：'all' 或 DISH_CATEGORIES 命中的 key（美团外卖式左侧筛选） */
const activeCategory = ref<string>('all')

/** 左侧分类列表：全部 + 该档口实际命中的品类 */
const categories = computed(() => {
  const present = DISH_CATEGORIES.filter(cat =>
    dishList.value.some(d => d.tags.includes(cat.key)),
  )
  return [{ key: 'all', label: '全部' }, ...present]
})

/** 按当前分类过滤的菜品（'all' 显示全部；命中分类的归该类，tags 为空/命不中归全部） */
const filteredDishes = computed(() => {
  if (activeCategory.value === 'all') return dishList.value
  return dishList.value.filter(d => d.tags.includes(activeCategory.value))
})

/** 档口评分文案（元数据来自 StallDetail.avgRating） */
const stallRatingText = computed(() => {
  const r = stallDetail.value?.avgRating
  return r != null && r > 0 ? `${r.toFixed(1)} 分` : ''
})

/** 档口营业时间：从菜品联表的 businessHours 派生（StallDetail 无此字段） */
const stallBusinessHours = computed(() => {
  return dishList.value.find(d => d.businessHours)?.businessHours || ''
})

async function loadReviews() {
  if (!currentStallId.value) return
  try {
    const res = await getReviewsByStall(currentStallId.value, { sort: 'latest', isWithImage: false })
    reviewList.value = res.list
    reviewTotal.value = res.total
  } catch {
    reviewList.value = []
    reviewTotal.value = 0
  }
}

function goToDetail(dish: Dish) {
  uni.navigateTo({ url: `/pages/pages-detail/dish?id=${dish.id}` })
}

/** 快捷申请关闭/纠错 Sheet */
const applyOpen = ref(false)
function openApply() {
  if (!userStore.requireAuth()) return
  if (!stallDetail.value?.id) {
    uni.showToast({ title: '档口信息缺失，无法申请', icon: 'none' })
    return
  }
  applyOpen.value = true
}

async function loadData() {
  const { stallName, canteen } = dishStore.navParams
  if (!stallName || !canteen) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const [detail] = await Promise.all([
      getStallDetail(canteen, stallName),
      dishStore.fetchStallDishes(canteen, stallName),
    ])
    stallDetail.value = detail
    currentStallId.value = detail.id ?? 0
    await loadReviews()
  } catch (e) {
    stallDetail.value = null
    console.error('[stall] 档口详情加载失败', e)
  } finally {
    loading.value = false
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
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0 0; padding-bottom: calc(var(--action-bar-height) + env(safe-area-inset-bottom)); }

/* hero 信息卡 */
.info-body { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.info-head {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  gap: var(--spacing-sm);
}
.info-name {
  font-size: var(--font-h3);
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.3;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.info-location {
  display: flex;
  align-items: center;
  gap: 4rpx;
}
.info-location-icon { font-size: 28rpx; line-height: 1; flex-shrink: 0; }
.info-location-text { font-size: var(--font-caption); font-weight: 600; color: var(--text-secondary); }
.info-rating { display: flex; align-items: center; gap: var(--spacing-xs); flex-shrink: 0; }
.info-rating-icon { width: 26rpx; height: 26rpx; line-height: 1; flex-shrink: 0; }
.info-rating-text { font-size: var(--font-body); font-weight: 700; color: var(--text-primary); }
.info-desc-text {
  font-size: var(--font-caption);
  font-weight: 400;
  color: var(--text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

/* ===== 菜品 tab：左侧分类侧栏 + 右侧瀑布流（美团外卖式） ===== */
.dishes-pane { display: flex; align-items: stretch; gap: var(--spacing-sm); padding: 0 var(--spacing-md); }
.cat-sidebar { width: 152rpx; flex-shrink: 0; max-height: 60vh; }
.cat-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-md) var(--spacing-xs);
  border-radius: var(--radius-tag);
  margin-bottom: var(--spacing-xs);
  font-size: var(--font-aux);
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--bg-soft);
  text-align: center;
  transition: transform 0.12s ease, background 0.15s ease, color 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}
.cat-item:active { transform: scale(var(--press-scale)); }
.cat-item.active { background: var(--color-primary); color: var(--text-white); }
.cat-label { line-height: 1.2; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cat-dot {
  position: absolute;
  right: 8rpx;
  top: 50%;
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: var(--color-primary);
  transform: translateY(-50%);
}
.cat-item.active .cat-dot { background: var(--text-white); }

.dishes-waterfall { flex: 1; min-width: 0; }
/* WaterfallList 内部为 .waterfall-col { flex:1 1 0; width:0 }，父级需真实宽度（flex:1 已满足），
   此处确保瀑布流容器不横向受限，避免微信下塌成单列 */

/* ===== 档口介绍 tab：单卡内有序分区 ===== */
.intro-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) 0;
}
.intro-row-icon { width: 28rpx; height: 28rpx; line-height: 1; flex-shrink: 0; }
.intro-row-label { flex-shrink: 0; font-size: var(--font-aux); color: var(--text-tertiary); font-weight: 600; }
.intro-row-value { flex: 1; min-width: 0; font-size: var(--font-body); color: var(--text-primary); font-weight: 500; text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.intro-desc { margin-top: var(--spacing-sm); padding-top: var(--spacing-md); border-top: 2rpx solid var(--border-color); }
.intro-desc-label { display: block; font-size: var(--font-aux); font-weight: 700; color: var(--text-tertiary); margin-bottom: var(--spacing-sm); }
.intro-desc-text { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.6; }

.review-list { margin-top: var(--spacing-sm); }

/* ===== 底部固定 3 tab menubar（复用 .detail-tabs 样式，扩到 3 个 tab） ===== */
.detail-tabs { display: flex; background: var(--bg-card); position: fixed; left: 0; right: 0; bottom: 0; z-index: 50; height: var(--action-bar-height); padding-bottom: env(safe-area-inset-bottom); box-sizing: content-box; box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--glass-highlight-soft); }
.detail-tabs .tab { flex: 1; text-align: center; padding: var(--spacing-md) 0; font-size: var(--font-body); color: var(--text-secondary); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; display: flex; align-items: center; justify-content: center; }
.detail-tabs .tab.active { color: var(--color-primary); font-weight: 700; }
.detail-tabs .tab.active::after { content: ''; display: block; width: 48rpx; height: 4rpx; background: var(--color-primary); margin: 8rpx auto 0; border-radius: 2rpx; }
.detail-tabs .tab:active { transform: scale(var(--press-scale)); }

/* 反馈入口：不常用，弱化在标题行右侧的小文字链接（点击展开 Sheet） */
.feedback-link {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  flex-shrink: 0;
}

/* 加载骨架屏 */
.stall-skeleton { padding: var(--spacing-md); }
.sk-swiper { width: 100%; height: 400rpx; border-radius: var(--radius-card); }
.sk-info { width: 100%; height: 160rpx; border-radius: var(--radius-card); margin-top: var(--spacing-md); }
.sk-dish { display: flex; gap: var(--spacing-sm); padding: var(--spacing-md) 0; border-bottom: 2rpx solid var(--bg-page); }
.sk-dish-img { width: 140rpx; height: 140rpx; border-radius: var(--radius-card); flex-shrink: 0; }
.sk-dish-body { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: var(--spacing-sm); }
.sk-line { width: 60%; height: 28rpx; border-radius: 6rpx; }
.sk-line-short { width: 40%; height: 24rpx; }
.skeleton { background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%); background-size: 200% 100%; animation: shimmer 1.5s infinite; }
@keyframes shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
</style>
