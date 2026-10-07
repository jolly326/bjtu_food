<template>
  <!-- 评价卡：整卡一张（卡头 + flat 条目）；三态齐全（在途文字行 / 失败可重试 / 零评价）
       P3-01：卡头改用 SectionTitle（「分区标题一律 SectionTitle」）。
       标题行 = 左「评价 + 条数」（合并为一个标题块，数字同色 / 小半号 / 等宽）
       + 右**「写评价」轻量入口**（主色笔形图标 + 文字，**非按钮形态**）；
       数字口径 = **已加载条数**（分页壳只有 `records`，服务端不回传总数）；在途 / 失败态不渲染数字。
       评价卡无「有用」按钮；排序唯一时间倒序、无切换入口；条目之间 1rpx 浅分隔线（与通知页 / 我的评价页同语言）。 -->
  <view class="review-section" id="review-section">
    <!-- 卡片壳走公共 `CardSection`：
         `flush` = 去掉自身外边距（块间距由外层 `.review-section` 统管），内距 `--spacing-md`。 -->
    <CardSection flush>
      <!-- 标题行：
           左 = 「评价」+ 条数，**合并为一个标题块**（经 SectionTitle 的 `count`）：数字与标题同色、小半号、等宽；
                数字口径 = **已加载条数**；在途 / 失败态不渲染数字（避免 0 值误导，失败 ≠ 零评价）。
           右 = **「写评价」轻量入口**（主色笔形图标 + 文字，**非按钮形态**，文案恒定）。 -->
      <SectionTitle title="评价" :count="(!pending && !loadFailed) ? count : null" no-margin>
        <template #extra>
          <!-- 轻量入口（**非按钮形态**）：主色线性笔形图标 + 主色文字，**无边框 / 无底色 / 无阴影**；
               仅表达「点击这里可以写评价」，不与页面主操作按钮抢视觉权重。 -->
          <view
            class="write-entry"
            role="button"
            aria-label="写评价"
            hover-class="write-entry--pressed"
            hover-stay-time="80"
            @tap="emit('write')"
          >
            <IconSvg name="edit" :size="26" :color="COLOR_MAP['primary-text']" class="write-entry-icon" />
            <text class="write-entry-text">写评价</text>
          </view>
        </template>
      </SectionTitle>

      <!-- ① 重置式在途（pending）只给文字行、不给骨架屏（禁的是伪内容与抖动，不是文字）：
           列表为空 ⇒ 列表位渲染一行 `.list-foot`「加载中…」，不误闪空态；
           列表非空 ⇒ 保留旧列表不清空（切星级筛选 / 提交后刷新期间不出现整块空白卡壳），
           列表末尾追加一行在途文字行。 -->

      <!-- ② 失败态：可重试（PR-03 失败态必备，避免误闪空态误导用户） -->
      <RetryBlock v-if="loadFailed" :margin="false" @retry="emit('retry')" />

      <!-- ③ 在途且列表为空：列表位的在途文字行 -->
      <view v-else-if="pending && reviews.length === 0" class="list-foot">
        <text class="list-foot-text">加载中…</text>
      </view>

      <!-- ④ 有数据（含在途期保留旧列表）/ ⑤ 零评价 -->
      <template v-else>
        <!-- 星级筛选条：`全部` + ⭐5~⭐1 共 6 项**静态枚举**（免服务端字典）。
             🔴 渲染条件 = 「有评价 **或** 已选筛选」（hasAnyReview）——
             筛选后命中 0 条时列表虽空，但筛选条必须保留，否则用户**无法切回「全部」**（死路）。 -->
        <scroll-view v-if="hasAnyReview" class="rating-filter" scroll-x :show-scrollbar="false">
          <view class="rating-filter-row">
            <view
              v-for="opt in RATING_FILTERS"
              :key="String(opt.value ?? 'all')"
              class="filter-chip"
              :class="{ 'filter-chip--active': ratingFilter === opt.value }"
              role="radio"
              :aria-checked="ratingFilter === opt.value"
              :aria-label="opt.label"
              hover-class="filter-chip--pressed"
              hover-stay-time="80"
              @tap="emit('filter', opt.value)"
            >
              <text class="filter-chip-text">{{ opt.label }}</text>
            </view>
          </view>
        </scroll-view>

        <!-- 有数据：评价列表（无「有用」入口；排序唯一时间倒序） -->
        <view v-if="reviews.length > 0" class="review-list">
          <view :style="{ height: topPad + 'px' }" />
          <ReviewItem
            v-for="rv in visible"
            :key="rv.id"
            class="v-item"
            :review="rv"
            flat
            @more="emit('more', $event)"
          />
          <view :style="{ height: bottomPad + 'px' }" />
        </view>

        <!-- 空态二态（语义不同，勿混；在途期不渲染，避免误闪空态）：
             ① **零评价**（ratingFilter == null）—— 信息卡评分位恒显「⭐ 5.0」兜底，
                若此处写「暂无评价」会与之矛盾 ⇒ 改为**冷启动号召**「快来抢首评 ⭐ 5.0」；
             ② **筛选无结果**（ratingFilter != null）—— 纯文案「暂无 X 星评价」，
                🔴 此时上方筛选条仍渲染（hasAnyReview），保证可切回「全部」。 -->
        <EmptyState
          v-else-if="!pending"
          :title="ratingFilter == null ? '快来抢首评 ⭐ 5.0' : `暂无 ${ratingFilter} 星评价`"
        />

        <!-- 重置式在途且列表非空：旧列表保留，列表末尾追加在途行（不整块清空） -->
        <view v-if="pending && reviews.length > 0" class="list-foot">
          <text class="list-foot-text">加载中…</text>
        </view>
      </template>
    </CardSection>
  </view>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance } from 'vue'
import ReviewItem from '@/components/ReviewItem.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import CardSection from '@/components/CardSection.vue'
import RetryBlock from '@/components/RetryBlock.vue'
import EmptyState from '@/components/EmptyState.vue'
import IconSvg from '@/components/IconSvg.vue'
// 图标色须传**实色**（IconSvg 的 color 不解析 var()，data-uri 内为字面量，传 var(...) 恒落近黑）
import { COLOR_MAP } from '@/theme/tokens'
import type { Review, MyReview } from '@/types/review'
import { useVirtualList } from '@/composables/usePagedList'

/** 评价列表项（静态枚举，值域固定 1~5 ⇒ **无服务端字典**；`null` = 全部） */
const RATING_FILTERS: ReadonlyArray<{ value: number | null; label: string }> = [
  { value: null, label: '全部' },
  { value: 5, label: '⭐5' },
  { value: 4, label: '⭐4' },
  { value: 3, label: '⭐3' },
  { value: 2, label: '⭐2' },
  { value: 1, label: '⭐1' },
]

const props = withDefaults(defineProps<{
  reviews: Review[]
  /**
   * 标题行展示的评价条数 = **已加载条数**。
   * 分页壳只有 `records`（服务端不回传总数），故「总数」口径退化为当前已加载量；
   * 触底加载更多时该数字随之上浮，属契约既定形态。
   */
  count: number
  /** 评价首屏/刷新是否失败（失败 ≠ 零评价，渲染可重试失败态） */
  loadFailed?: boolean
  /**
   * 重置式评价请求在途（首屏 / 重试 / 提交后刷新 / 切星级筛选）：
   * 列表为空 ⇒ 列表位渲染一行「加载中…」文字行（避免在途瞬间误闪「暂无评价」）；
   * 列表非空 ⇒ 保留旧列表不清空，列表末尾追加一行在途文字行
   * （切筛选 / 提交后刷新期间不出现整块空白卡壳）。在途只给文字行、不给骨架屏。
   */
  pending: boolean
  /** 页面滚动量（px）：驱动评价列表虚拟窗口（由父级 `scroll-view` 的 `@scroll` 下发） */
  scrollTop?: number
  /** 当前星级筛选（`null` = 全部）：驱动筛选条高亮 + 空态文案二态区分 */
  ratingFilter?: number | null
}>(), {
  loadFailed: false,
  scrollTop: 0,
  ratingFilter: null,
})

/** 虚拟列表：列表嵌在页面 `scroll-view` 内 ⇒ 外部滚动量 + 组件作用域查询 + 动态偏移（见 composable 文档） */
const { visible, topPad, bottomPad } = useVirtualList<Review>({
  items: computed(() => props.reviews),
  estimateHeight: 200,
  offsetSelector: '.review-list',
  scrollClass: 'dish-scroll',
  scrollTopSource: computed(() => props.scrollTop),
  scope: getCurrentInstance()?.proxy,
})

/** 筛选条渲染条件：有评价（列表非空）**或**已选筛选（ratingFilter != null）。
 *  🔴 后半条是**死路修复**：筛选后命中 0 条 ⇒ 列表为空，若此时连筛选条一并隐藏，
 *     用户将无法切回「全部」（唯一出口消失）。零评价且未筛选时仍不渲染（无可筛项）。 */
const hasAnyReview = computed(() => props.reviews.length > 0 || props.ratingFilter !== null)

/* 评价条数口径：**恒为已加载条数**（分页壳只有 `records`，服务端不回传总数）；
   数字经 `SectionTitle` 的 `count` 与标题合并渲染为「评价 12」（同色 / 小半号 / 等宽）。 */
const emit = defineEmits<{
  (e: 'more', review: Review | MyReview): void
  /** 失败态点击重试：页面侧重拉评价列表（与进入页面同路径） */
  (e: 'retry'): void
  /** 写评价（标题行右侧唯一入口；页面侧走 requireAuth → ReviewComposer） */
  (e: 'write'): void
  /** 切换星级筛选（`null` = 全部）：页面侧重置分页并从第 1 页重拉 */
  (e: 'filter', rating: number | null): void
}>()
</script>

<style scoped>
/* 纵向间距：块间距统管在外层（卡壳本身 `flush`，见模板）；同页两卡内距由此统一到 `--spacing-md`
    */
.review-section { margin: var(--spacing-md) var(--page-gutter) 0; }
/* 条目之间 1rpx 浅分隔线（与系统通知页 / 我的评价页同语言）；
   行内距由条目自身承担，最上 / 最下无线；负 margin 抵消 CardSection 内距使分隔线撑满卡宽 */
.review-list { display: flex; flex-direction: column; }
.review-list :deep(.review-item) {
  padding: var(--spacing-md) var(--spacing-md);
  margin: 0 calc(-1 * var(--spacing-md));
}
.review-list :deep(.review-item + .review-item) { border-top: 1rpx solid var(--border-color); }

/* ===== 标题行右位：「写评价」轻量入口（**文字 + 图标，非按钮形态**）=====
   评价数已并入标题块（SectionTitle 的 `count`），右位只放写评价入口。
   **无边框 / 无底色 / 无阴影 / 无胶囊槽** —— 仅主色笔形图标 + 主色文字（视觉权重低于任何按钮）；
   按压反馈 = opacity 微降（**禁 `transform: scale`** —— 全站红线）。 */
.write-entry {
  position: relative;
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-3xs);
  -webkit-tap-highlight-color: transparent;
}
/* 命中区经 ::after **仅纵向**扩至 ≥88rpx（a11y 44pt 下限；视觉尺寸不变） */
.write-entry::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: var(--tap-target-size);
  transform: translateY(-50%);
}
.write-entry-icon {
  flex: none;
  width: 26rpx;
  height: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.write-entry-text {
  font-size: var(--font-small);
  font-weight: var(--weight-medium);
  color: var(--color-primary-text);
  line-height: 1.2;
  white-space: nowrap;
}
.write-entry--pressed { opacity: 0.6; }

/* ===== 星级筛选条（`全部` + ⭐5~⭐1）=====
    横向 scroll-x 单行不换行；chip 为独立可点件（min-height ≥88rpx 触达基线）。
    圆角 = `--radius-pill`（基线 §1.2「标签两档」：可点胶囊 chip → pill；消费方已登记）。
    选中态 = --color-primary-soft 底 + --color-primary 描边（形态基线 §二 TagChip）。
    按压反馈 = opacity 微降（**禁 `transform: scale`** —— 全站红线）。 */
.rating-filter {
  width: 100%;
  white-space: nowrap;
  margin-bottom: var(--spacing-sm);
}
.rating-filter-row { display: inline-flex; align-items: center; gap: var(--spacing-xs); }
.filter-chip {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: var(--tap-target-size);
  padding: 0 var(--spacing-md);
  border: 1rpx solid var(--border-color);
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  -webkit-tap-highlight-color: transparent;
}
.filter-chip--active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
}
.filter-chip--pressed { opacity: 0.6; }
.filter-chip-text {
  font-size: var(--font-body);
  font-weight: var(--weight-regular);
  color: var(--text-body);
  line-height: 1.2;
}
.filter-chip--active .filter-chip-text { color: var(--color-primary-text); font-weight: var(--weight-medium); }

/* ===== 失败态：视觉由公共 RetryBlock 承担，此处仅补卡内上下呼吸 ===== */
.review-section :deep(.retry-block) { margin: var(--spacing-sm) 0; }
</style>
