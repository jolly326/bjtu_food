<template>
  <view class="page stall-detail-page">
    <Header :title="stallDetail?.name || '档口'" showBack />
    <scroll-view class="scroll-wrap" scroll-y refresher-enabled :refresher-triggered="refresherTriggered" :scroll-top="scrollTop" @scroll="onScroll" @refresherrefresh="onRefresh">
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

        <!-- hero 卡（无"档口信息"大标题，2026-08-02 用户裁定）：名称 +「信息有误？」同行 / 位置 / 标徽(tags) / 评分 / 一句话简介 -->
        <CardSection>
          <view class="info-body">
            <view class="info-head">
              <text class="info-name">{{ stallDetail.name }}</text>
              <text class="feedback-link" @tap="openApply">信息有误？</text>
            </view>
            <view class="info-location">
              <IconSvg name="location" :size="24" color="var(--color-primary)" class="info-location-icon" />
              <text class="info-location-text">{{ stallDetail.location }}</text>
            </view>
            <!-- 标徽（档口 tags，如 招牌/清真；StallDetail 暂未返回，后端扩展后自动显示） -->
            <view class="info-badges" v-if="stallTags.length > 0">
              <text v-for="t in stallTags" :key="t" class="info-badge">{{ t }}</text>
            </view>
            <view class="info-rating" v-if="stallDetail.avgRating != null && stallDetail.avgRating > 0">
              <IconSvg name="star-filled" :size="26" color="var(--color-star)" class="info-rating-icon" />
              <text class="info-rating-text">{{ stallDetail.avgRating.toFixed(1) }}</text>
            </view>
            <view class="info-desc" v-if="stallDetail.description">
              <text class="info-desc-text">{{ stallDetail.description }}</text>
            </view>
          </view>
        </CardSection>

        <!-- tabBar：紧跟 hero 卡片下方（文档流，不沉底；菜品 / 评价 / 档口） -->
        <view class="detail-tabs">
          <view
            class="tab"
            :class="{ active: activeTab === 'dishes' }"
            @tap="switchTab('dishes')"
          >
            <text class="tab-label">菜品</text>
            <text class="tab-count">{{ dishList.length }}</text>
          </view>
          <view
            class="tab"
            :class="{ active: activeTab === 'reviews' }"
            @tap="switchTab('reviews')"
          >
            <text class="tab-label">评价</text>
            <text class="tab-count">{{ reviewTotal }}</text>
          </view>
          <view
            class="tab"
            :class="{ active: activeTab === 'intro' }"
            @tap="switchTab('intro')"
          >
            <text class="tab-label">档口</text>
          </view>
        </view>

        <!-- 内容区随 tab 切换（三段 v-if 销毁重建：scroll-view 为原生组件不受 v-show 控制会残留，必须 v-if；且三段 pane 固定相同高度 65vh → 外层 scroll-view 总高度恒定 → 切 tab 不回弹） -->
        <!-- ===== 菜品 tab：左侧一列分类筛选 + 右侧按分类分组的菜品卡片（美团外卖式：点左类→右侧锚点跳转到该组开头） ===== -->
        <view v-if="activeTab === 'dishes'" class="dishes-pane" :class="{ 'dishes-empty': dishList.length === 0 }">
          <scroll-view class="cat-sidebar" scroll-y :show-scrollbar="false">
            <view
              v-for="(cat, ci) in categoryGroups"
              :key="cat.key"
              class="cat-item"
              :class="{ active: activeCategory === cat.key }"
              @tap="scrollToCategory(cat.key, ci)"
            >
              <text class="cat-label">{{ cat.label }}</text>
            </view>
          </scroll-view>
          <scroll-view class="dish-group-scroll" scroll-y :scroll-into-view="scrollAnchor" :scroll-with-animation="true" :show-scrollbar="false">
            <view
              v-for="(group, gi) in categoryGroups"
              :key="group.key"
              :id="`cat-${gi}`"
              class="dish-group"
            >
              <view class="dish-group-title">
                <text class="dish-group-label">{{ group.label }}</text>
                <text class="dish-group-count">{{ group.dishes.length }}</text>
              </view>
              <DishRowCard
                v-for="dish in group.dishes"
                :key="dish.id"
                :dish="dish"
                @card-click="goToDetail"
              />
            </view>
            <EmptyState v-if="dishList.length === 0" text="该档口暂无菜品" />
          </scroll-view>
        </view>

        <!-- ===== 评价 tab：与动态详情评论区同款结构（comment-section + comment-title），三处评论区域视觉完全一致 ===== -->
        <view v-if="activeTab === 'reviews'" class="tab-pane tab-pane-plain">
          <view class="comment-section">
            <text class="comment-title">评价 ({{ reviewTotal }})</text>
            <view class="review-list" v-if="reviewList.length > 0">
              <ReviewItem
                v-for="rv in reviewList"
                :key="rv.id"
                :review="rv"
                :deletable="rv.userId === currentUserId"
                @delete="onDeleteReview"
              />
            </view>
            <EmptyState v-else text="暂无评价，来写第一条吧" icon="comment" />
          </view>
        </view>

        <!-- ===== 档口 tab：去掉内层 scroll-view + 改用 page-local .intro-card（避免双层嵌套 + CardSection 组件样式隔离致 var 失效） -->
        <view v-if="activeTab === 'intro'" class="tab-pane tab-pane-plain">
          <view class="intro-card">
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
          </view>
        </view>

      </template>

      <!-- 加载失败 / 无数据空态 -->
      <EmptyState v-else text="档口信息加载失败" :retry="true" @retry="loadData" />
      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 申请关闭/纠错 Sheet（共享组件） -->
    <ApplySheet
      :open="applyOpen"
      entity-type="STALL"
      :entity-id="stallDetail?.id || 0"
      @update:open="applyOpen = $event"
    />

    <!-- 菜品详情底部弹层（task-10：独立页 → sheet）；topOffset 传入档口页 Header 高度，弹层顶部不越过其底部 -->
    <DishDetailSheet
      :open="dishSheetOpen"
      :dish-id="sheetDishId"
      top-offset="176rpx"
      hide-location
      @update:open="dishSheetOpen = $event"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, onMounted } from 'vue'
import Header from '@/components/header.vue'
import ImageSwiper from '@/components/ImageSwiper.vue'
import CardSection from '@/components/CardSection.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconSvg from '@/components/IconSvg.vue'
import ApplySheet from '@/components/ApplySheet.vue'
import ReviewItem from '@/components/ReviewItem.vue'
import DishDetailSheet from '@/components/DishDetailSheet.vue'
import DishRowCard from '@/components/DishRowCard.vue'
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app'
import { getCanteensWithStalls } from '@/api/canteen'
import { useDishStore } from '@/stores/dish'
import { useUserStore } from '@/stores/user'
import { buildSharePayload } from '@/utils/shareState'
import { getStallDetail } from '@/api/canteen'
import { getReviewsByStall, deleteReview } from '@/api/review'
import { DISH_CATEGORIES } from '@/constants/categories'
import type { StallDetail } from '@/types/canteen'
import type { Dish } from '@/types/dish'
import type { Review } from '@/types/review'

type StallTab = 'dishes' | 'reviews' | 'intro'

const dishStore = useDishStore()
const userStore = useUserStore()
/** 菜品详情底部弹层（task-10：独立页 → sheet） */
const dishSheetOpen = ref(false)
const sheetDishId = ref(0)
function openDishSheet(id: number) {
  if (!id) return
  sheetDishId.value = id
  dishSheetOpen.value = true
}
const stallDetail = ref<StallDetail | null>(null)
const dishList = computed(() => dishStore.stallDishes)
const refresherTriggered = ref(false)
const loading = ref(true)

/** 用户评价区（内联展示全部） */
const reviewList = ref<Review[]>([])
const reviewTotal = ref(0)
const currentStallId = ref(0)
const currentUserId = computed(() => userStore.userInfo?.id)

/** 删除本人评价（仅本人 userId；task-12.5 DELETE /my/reviews/{id}） */
function onDeleteReview(rv: Review) {
  if (!userStore.requireAuth()) return
  if (userStore.userInfo?.id && rv.userId !== userStore.userInfo.id) return
  uni.showModal({
    title: '删除评价',
    content: '确定删除这条评价吗？删除后不可恢复。',
    confirmText: '删除',
    confirmColor: '#e54d42',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteReview(rv.id)
        uni.showToast({ title: '评价已删除', icon: 'none' })
        reviewList.value = reviewList.value.filter(x => x.id !== rv.id)
        reviewTotal.value = Math.max(0, reviewTotal.value - 1)
      } catch (e: any) {
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
  })
}

/** 三段 tab 切换状态（菜品 / 评价 / 档口介绍） */
const activeTab = ref<StallTab>('dishes')

/**
 * 整页 scroll-view 滚动位置恢复（2026-08-03 修复频闪版）。
 * 关键：@scroll 只写非响应式普通变量（不触发 re-render），避免
 * `:scroll-top` 受控 + 回写形成死循环导致上滑飘逸频闪；
 * scrollTop 仅在切 tab（v-if 重建 DOM）时一次性赋值触发恢复。
 */
const scrollTop = ref(0)
let lastScrollTop = 0
function onScroll(e: any) {
  lastScrollTop = e.detail?.scrollTop ?? 0
}
/** 切 tab：记录当前滚动位置 → 切换 → nextTick 一次性恢复（v-if 重建后 scroll-view 为新实例，scroll-top 设置生效） */
function switchTab(tab: StallTab) {
  if (activeTab.value === tab) return
  const saved = lastScrollTop
  activeTab.value = tab
  nextTick(() => {
    scrollTop.value = saved
  })
}

/** 菜品分类筛选：当前选中的分类 key（美团外卖式左侧分类，不再有"全部"） */
const activeCategory = ref<string>('')

/** 锚点：右侧 scroll-view 滚动到的分组 id（`cat-${gi}`） */
const scrollAnchor = ref('')

/**
 * 左侧分类分组：按菜品实际 tags 派生（美团外卖式）。
 * 优先匹配 DISH_CATEGORIES 的 key（noodle/rice/...）获得中文名；
 * 未命中则直接用 tags 原文作分组（如"招牌"）。
 * 一个菜品可出现在多个分组（每个 tag 一个分组）。
 */
interface CategoryGroup {
  key: string
  label: string
  dishes: Dish[]
}
const categoryGroups = computed<CategoryGroup[]>(() => {
  const map = new Map<string, Dish[]>()
  for (const d of dishList.value) {
    const tags = d.tags && d.tags.length > 0 ? d.tags : ['other']
    for (const t of tags) {
      if (!map.has(t)) map.set(t, [])
      map.get(t)!.push(d)
    }
  }
  const catLabel = (key: string) => DISH_CATEGORIES.find(c => c.key === key)?.label || key
  const groups: CategoryGroup[] = []
  for (const [key, dishes] of map.entries()) {
    groups.push({ key, label: catLabel(key), dishes })
  }
  return groups
})

/** 点左侧分类 → 右侧锚点跳转到该分组开头（先清空再赋值，保证连续点击同一分类也能触发） */
function scrollToCategory(key: string, gi: number) {
  activeCategory.value = key
  const anchor = `cat-${gi}`
  scrollAnchor.value = ''
  nextTick(() => {
    scrollAnchor.value = anchor
  })
}



/** 档口评分文案（元数据来自 StallDetail.avgRating） */
const stallRatingText = computed(() => {
  const r = stallDetail.value?.avgRating
  return r != null && r > 0 ? `${r.toFixed(1)} 分` : ''
})

/** 标徽（hero 展示）：优先后端 StallDetail.tags；缺省从菜品 tags 派生高频标徽（去重、最多 3 个） */
const stallTags = computed<string[]>(() => {
  const fromApi = stallDetail.value?.tags
  if (fromApi && fromApi.length > 0) return fromApi.slice(0, 3)
  const count = new Map<string, number>()
  dishList.value.forEach(d => (d.tags || []).forEach(t => count.set(t, (count.get(t) || 0) + 1)))
  return [...count.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, 3)
    .map(([t]) => t)
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
  openDishSheet(dish.id)
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
    // 先取档口详情拿到 stallId，再按 stallId 精确过滤档口菜品（避免 keyword 模糊搜索召回同名菜品）
    const detail = await getStallDetail(canteen, stallName)
    stallDetail.value = detail
    currentStallId.value = detail.id ?? 0
    await dishStore.fetchStallDishes(detail.id ?? 0)
    await loadReviews()
  } catch (e) {
    stallDetail.value = null
    console.error('[stall] 档口详情加载失败', e)
  } finally {
    loading.value = false
  }
}

/**
 * 分享深链：/pages/pages-detail/stall?id=<stallId>（菜品分享、动态关联跳转）
 * 从 /canteens/all 反查该档口所属食堂与名称，写入 navParams 后再加载。
 * 普通入口（已设 navParams）直接走 onMounted。
 */
onLoad(async (options: Record<string, string | undefined>) => {
  const id = Number(options?.id || 0)
  if (id && !dishStore.navParams.stallName) {
    try {
      const list = await getCanteensWithStalls()
      for (const c of list) {
        const match = (c.stalls || []).find((s: any) => Number(s.id) === id)
        if (match) {
          dishStore.navParams = { canteen: c.name, stallName: match.name }
          break
        }
      }
    } catch {
      /* 深链反查失败则保持空 navParams */
    }
  }
  loadData()
})

onShareAppMessage(() => buildSharePayload())

function onRefresh() {
  if (refresherTriggered.value) return
  refresherTriggered.value = true
  loadData().finally(() => { refresherTriggered.value = false })
}
</script>

<style scoped>
.stall-detail-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0 0; padding-bottom: var(--spacing-lg); }

/* hero 信息卡（无"档口信息"大标题：名称 + 信息有误？/ 位置 / 标徽 / 评分 / 简介） */
.info-body { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.info-head {
  display: flex;
  flex-direction: row;
  align-items: baseline;
  gap: var(--spacing-sm);
}
/* Apple Design Typography：档口名 h1 级 800（与菜品详情 hero 一致） */
.info-name {
  font-size: var(--font-h1);
  font-weight: var(--weight-heavy);
  color: var(--text-primary);
  letter-spacing: var(--tracking-h3);
  line-height: 1.2;
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
.info-location-icon { font-size: 24rpx; line-height: 1; flex-shrink: 0; }
.info-location-text { font-size: var(--font-small); font-weight: var(--weight-medium); color: var(--text-secondary); }
/* 标徽（tags 徽章行） */
.info-badges { display: flex; flex-wrap: wrap; gap: 8rpx; }
.info-badge {
  font-size: 20rpx;
  color: var(--color-primary);
  background: var(--color-primary-soft);
  padding: 2rpx 12rpx;
  border-radius: var(--radius-tag);
  font-weight: var(--weight-semibold);
}
/* 评分（独立行） */
.info-rating { display: flex; align-items: center; gap: var(--spacing-xs); flex-shrink: 0; }
.info-rating-icon { width: 26rpx; height: 26rpx; line-height: 1; flex-shrink: 0; }
.info-rating-text { font-size: var(--font-body); font-weight: var(--weight-bold); color: var(--text-primary); }
.info-desc-text {
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  color: var(--text-secondary);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

/* ===== 菜品 tab：左侧一列分类 + 右侧按类分组卡片（美团外卖式） ===== */
.dishes-pane {
  display: flex;
  align-items: stretch;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md) 0;
  background: var(--bg-page);
  /* 与评价/档口 tab-pane 高度一致（65vh），保证切 tab 外层 scroll-view 总高度恒定不回弹 */
  height: 65vh;
  box-sizing: border-box;
}
/* 空档口：无菜品时不撑 65vh 大空白，高度自适应到空态卡片 */
.dishes-pane.dishes-empty {
  height: auto;
  min-height: 40vh;
}
/* scroll-view 必须固定 height 才能正常滚动与锚点定位（微信要求）；
   背景必须硬编码 #F6F4EF（= --bg-page 取值）：scroll-view 默认白底，CSS 变量在小程序内可能解析失效导致露出白线，
   故此二处 scroll-view 保留裸色值作兜底，其余普通容器已改 var(--bg-page) */
.cat-sidebar {
  width: 152rpx;
  flex-shrink: 0;
  height: 65vh;
  background: #F6F4EF;
}
/* 右侧分组滚动容器：与左栏等高，内部按分组滚动（同左栏，scroll-view 背景硬编码 #F6F4EF 兜底白线） */
.dish-group-scroll {
  flex: 1;
  min-width: 0;
  height: 65vh;
  background: #F6F4EF;
}
/* 评价 / 档口 tab：去内层 scroll-view，直接在外层 scroll-wrap 内渲染（双层 scroll-view 嵌套致 CSS 变量 var(--bg-card) 继承断裂，白底卡片不显示）。
   内容自然撑开，外层 scroll-wrap scroll-y 自然滚动；切 tab 不回弹由 switchTab 的 scrollTop 恢复处理。 */
.tab-pane { background: var(--bg-page); box-sizing: border-box; }
/* 统一「评价/档口」tab 内容与上方 tab 条的间距：顶部 sm + 底部 md（卡片自身不再带上下 margin） */
.tab-pane-plain { height: auto; padding: var(--spacing-sm) 0 var(--spacing-md); }
.dish-group { margin-bottom: var(--spacing-md); }
.dish-group-title {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  margin-bottom: var(--spacing-sm);
  padding-left: 4rpx;
}
.dish-group-label { font-size: var(--font-body); font-weight: var(--weight-bold); color: var(--text-primary); }
.dish-group-count { font-size: var(--font-aux); color: var(--text-tertiary); font-variant-numeric: tabular-nums; }
.cat-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-md) var(--spacing-xs);
  margin-bottom: var(--spacing-xs);
  font-size: var(--font-aux);
  font-weight: var(--weight-semibold);
  color: var(--text-secondary);
  background: transparent;
  text-align: center;
  transition: background 0.15s ease, color 0.15s ease;
  -webkit-tap-highlight-color: transparent;
}
/* Apple 侧栏 highlight 按压：背景微变而非缩放 */
.cat-item:active { background: var(--bg-soft); }
/* 选中态：白底 + 主色字 + 左侧主色指示条（美团外卖式，去圆润胶囊，与右侧白卡区域连贯） */
.cat-item.active { background: var(--bg-card); color: var(--color-primary); font-weight: var(--weight-bold); }
.cat-item.active::before {
  content: '';
  position: absolute; left: 0; top: 50%;
  transform: translateY(-50%);
  width: 8rpx; height: 32rpx; border-radius: 4rpx;
  background: var(--color-primary);
}
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

/* ===== 档口介绍 tab：单卡内有序分区 ===== */
.intro-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) 0;
}
.intro-row-icon { width: 28rpx; height: 28rpx; line-height: 1; flex-shrink: 0; }
.intro-row-label { flex-shrink: 0; font-size: var(--font-aux); color: var(--text-tertiary); font-weight: var(--weight-semibold); }
.intro-row-value { flex: 1; min-width: 0; font-size: var(--font-body); color: var(--text-primary); font-weight: var(--weight-medium); text-align: right; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.intro-desc { margin-top: var(--spacing-sm); padding-top: var(--spacing-md); border-top: 2rpx solid var(--border-color); }
.intro-desc-label { display: block; font-size: var(--font-aux); font-weight: var(--weight-bold); color: var(--text-tertiary); margin-bottom: var(--spacing-sm); }
.intro-desc-text { font-size: var(--font-body); color: var(--text-secondary); line-height: 1.6; }

/* 档口介绍卡：与评价卡 comment-section 同款白卡（圆角 24px / 阴影 / 无上下 margin，
   与 tab 条间距由 .tab-pane-plain 的 padding-top 提供，保证两 tab 一致） */
.intro-card { margin: 0 var(--spacing-md); padding: var(--spacing-md); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card-soft); }

/* 评价卡：与动态详情评论区 comment-section 完全同款（结构、类名、样式值一致），
   卡片圆角 24px + 标题 34rpx / weight 800 / letter-spacing -0.02em（Apple Design 16 Typography：大字负 tracking），
   背景用 var(--bg-card)（= #FFFFFF）与阴影与 moment.vue 评论区一致，token 化避免裸 hex */
.comment-section { margin: 0 var(--spacing-md); padding: var(--spacing-md) var(--spacing-md) var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-modal); box-shadow: var(--shadow-card-soft); }
.comment-title { display: block; font-size: var(--font-h3); font-weight: var(--weight-heavy); color: var(--text-primary); letter-spacing: -0.02em; margin-bottom: var(--spacing-md); }
.review-list { margin-top: var(--spacing-xs); }

/* ===== 3 tab menubar：紧跟 hero 卡片下方（文档流，不沉底） ===== */
.detail-tabs {
  display: flex;
  margin: var(--spacing-sm) var(--spacing-md) 0;
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-bar-soft);
  border-top: 2rpx solid var(--glass-highlight-soft);
  overflow: hidden;
}
.detail-tabs .tab { flex: 1; text-align: center; padding: var(--spacing-md) 0; font-size: var(--font-body); color: var(--text-secondary); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; display: flex; align-items: baseline; justify-content: center; gap: var(--spacing-xs); position: relative; }
.detail-tabs .tab-label { line-height: 1.2; }
.detail-tabs .tab-count { font-size: var(--font-aux); color: var(--text-tertiary); font-variant-numeric: tabular-nums; }
.detail-tabs .tab.active { color: var(--color-primary); font-weight: var(--weight-bold); }
.detail-tabs .tab.active .tab-count { color: var(--color-primary); }
.detail-tabs .tab.active::after { content: ''; display: block; width: 48rpx; height: 4rpx; background: var(--color-primary); margin: 8rpx auto 0; border-radius: 2rpx; position: absolute; bottom: 0; left: 50%; transform: translateX(-50%); }
.detail-tabs .tab:active { transform: scale(var(--press-scale)); }

/* 反馈入口：不常用，弱化在标题行右侧的小文字链接（点击展开 Sheet） */
/* 信息有误链接：与菜品详情同款（44px 命中区 + opacity/背景按压反馈，Apple 弱链接） */
.feedback-link {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  flex-shrink: 0;
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-tag);
  transition: opacity 120ms ease, background-color 120ms ease;
  -webkit-tap-highlight-color: transparent;
}
.feedback-link:active { opacity: 0.55; background-color: var(--bg-soft); }

/* 加载骨架屏 */
.stall-skeleton { padding: var(--spacing-md); }
.sk-swiper { width: 100%; height: 400rpx; border-radius: var(--radius-card); }
.sk-info { width: 100%; height: 160rpx; border-radius: var(--radius-card); margin-top: var(--spacing-md); }
.sk-dish { display: flex; gap: var(--spacing-sm); padding: var(--spacing-md) 0; border-bottom: 2rpx solid var(--bg-page); }
.sk-dish-img { width: 140rpx; height: 140rpx; border-radius: var(--radius-card); flex-shrink: 0; }
.sk-dish-body { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: var(--spacing-sm); }
.sk-line { width: 60%; height: 28rpx; border-radius: 6rpx; }
.sk-line-short { width: 40%; height: 24rpx; }
/* 骨架屏：复用全局 shimmer（App.vue 1.4s），统一全站加载节奏 */
.skeleton { background: linear-gradient(90deg, var(--bg-placeholder) 25%, var(--border-color) 50%, var(--bg-placeholder) 75%); background-size: 200% 100%; animation: shimmer 1.4s ease infinite; }
</style>
