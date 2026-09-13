<template>
  <view class="feed-wrap">
    <!-- 加载失败重试块（MP-012）：筛选流失败且无数据时替代静默空态/「还没录菜品」误导文案。
         与空态同族视觉（bg-soft 凹陷面 + 次级文字），整块 @tap 上抛 retry 由页面走重拉路径 -->
    <view
      v-if="loadFailed"
      class="feed-retry"
      role="button"
      aria-label="加载失败，点击重试"
      hover-class="pressed"
      @tap="emit('retry')"
    >
      <IconSvg name="report" :size="44" color="var(--text-tertiary)" />
      <text class="feed-retry-title">加载失败</text>
      <text class="feed-retry-hint">网络似乎不太顺畅 · 点击重试</text>
    </view>

    <view class="waterfall-grid">
      <!-- 双列瀑布流：奇偶分列（右列绝不空）；原 WaterfallList 已内联合并到此，减少一层组件嵌套 -->
      <view class="waterfall-col waterfall-col-left">
        <view v-for="entry in splitList.left" :key="entry.key" class="waterfall-item">
          <DishCard :dish="entry.item" @select="goToDetail" />
        </view>
      </view>
      <view class="waterfall-col waterfall-col-right">
        <view v-for="entry in splitList.right" :key="entry.key" class="waterfall-item">
          <DishCard :dish="entry.item" @select="goToDetail" />
        </view>
      </view>
    </view>

    <!-- 贡献卡片：内容流末尾的**独立兄弟节点**（全宽单列，不进双列高度计算，规避瀑布流具名 slot 塌缩与列高断层）。
         卡片常驻于网格整体之下：内容非空时为末尾一项，内容为空时即为内容区唯一元素（空态显式例外，见 spec contribution-entry）。
         文案随筛选上下文切换「同节点换文案」，不做整卡条件重建，避免切换闪烁与位移跳动。 -->
    <view class="contribute-card" role="button" aria-label="推荐菜品" hover-class="pressed" @tap="goContribute">
      <view class="cc-icon">
        <IconSvg :name="scopeEmpty ? 'search' : 'plus'" :size="40" color="var(--color-primary)" />
      </view>
      <view class="cc-copy">
        <text class="cc-title">{{ scopeEmpty ? '这个范围还没录菜品' : '想吃啥没找到？告诉我们' }}</text>
        <text class="cc-desc">
          {{ scopeEmpty ? '把你吃到的菜报给我们，也可以扩大范围再找找' : '补录一道菜，让更多同学找到它' }}
        </text>
      </view>
      <!-- 次级动作：仅在「筛选后无结果」时出现，帮助用户脱困（子元素显隐，不重建整卡） -->
      <text
        v-if="scopeEmpty"
        class="cc-clear"
        role="button"
        aria-label="清除筛选"
        @tap.stop="emit('clear-filter')"
      >清除筛选</text>
      <IconSvg v-else name="arrow" :size="28" color="var(--text-tertiary)" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DishCard from './DishCard.vue'
import IconSvg from '@/components/IconSvg.vue'
import { useDishStore } from '@/stores/dish'
import type { Dish } from '@/types/dish'
import { dishDetailUrl, feedbackEntryUrl } from '@/utils/routes'

const props = defineProps<{
  /** 当前是否处于筛选中（食堂 / 价格任一生效）——由页面下发，驱动卡片上下文文案 */
  filtered: boolean
}>()

const emit = defineEmits<{
  (e: 'clear-filter'): void
  /** 筛选流加载失败后点击重试（MP-012）：上抛页面走与下拉刷新同一条重拉路径 */
  (e: 'retry'): void
}>()

const dishStore = useDishStore()

/** 筛选流最近一次请求失败且当前无数据（MP-012）：渲染错误重试块，失败 ≠ 无数据 */
const loadFailed = computed(() => dishStore.filterError && dishStore.filterList.length === 0)

/** 当前筛选范围内是否无菜品（筛选生效且结果为空且**非失败态** → 说明原因并给「清除筛选」脱困动作）；
    失败态不能宣称「还没录菜品」（数据未知），由上方重试块接管 */
const scopeEmpty = computed(
  () => props.filtered && dishStore.filterList.length === 0 && !dishStore.filterError,
)

/** 瀑布流按图片原始比例排列（不再为错落刻意拉伸图片高度）；列分配保持奇偶分列。
 *  key 仅由稳定业务主键 id 构成（id 唯一），不附加列内序号 idx，
 *  避免加载更多时列内序号重排导致 key 变化、已渲染卡片整列重建（闪烁/掉帧）。 */
const splitList = computed(() => {
  const left: { item: Dish; key: string }[] = []
  const right: { item: Dish; key: string }[] = []
  dishStore.filterList.forEach((item, idx) => {
    const rawKey = item.id
    const key =
      rawKey !== undefined && rawKey !== null
        ? `wf-${rawKey}`
        : `wf-idx-${idx}`
    const entry = { item, key }
    if (idx % 2 === 0) left.push(entry)
    else right.push(entry)
  })
  return { left, right }
})

/** 菜品卡片点击 → 独立详情页（pages/detail/dish） */
function goToDetail(dish: { id: number }) {
  uni.navigateTo({ url: dishDetailUrl(dish.id) })
}

/** 贡献入口（首页卡片）→ 意见反馈页并预选「推荐菜品」空表单（落点由唯一构造函数拼装） */
function goContribute() {
  uni.navigateTo({ url: feedbackEntryUrl({ type: 'add', from: 'home' }) })
}
</script>

<style scoped lang="scss">
.feed-wrap {
  padding: 0 var(--spacing-md);
  box-sizing: border-box;
}

.waterfall-grid {
  width: 100%;
  box-sizing: border-box;
  padding-bottom: var(--spacing-lg);
  display: flex;
  gap: var(--spacing-md);
}
.waterfall-col {
  flex: 1 1 0;
  width: 0;
  min-width: 0;
  box-sizing: border-box;
}
.waterfall-item {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  margin-bottom: var(--spacing-md);
}
.waterfall-item:last-child { margin-bottom: 0; }

/* 加载失败重试块（MP-012）：与空态/列表卡同族视觉（bg-soft 凹陷面 + 大圆角 + 次级文字），
   居中极简行内块；整块可点（@tap），按压仅透明度反馈，不引入 scale */
.feed-retry {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--spacing-xs);
  margin: var(--spacing-sm) 0 var(--spacing-lg);
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.feed-retry.pressed { opacity: 0.7; }
.feed-retry-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-secondary); text-align: center; }
.feed-retry-hint { font-size: var(--font-aux); color: var(--text-tertiary); text-align: center; }

/* 贡献卡片：与列表卡同一表面语言（白底 + 大圆角 + 柔和投影），全宽单列 */
.contribute-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  -webkit-tap-highlight-color: transparent;
  box-sizing: border-box;
}
.contribute-card.pressed { background-color: var(--bg-soft); }
.cc-icon {
  flex-shrink: 0;
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-pill);
  background: var(--color-primary-soft);
}
.cc-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.cc-title { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); }
.cc-desc { font-size: var(--font-aux); color: var(--text-tertiary); line-height: 1.4; }
.cc-clear {
  flex-shrink: 0;
  padding: var(--spacing-2xs) var(--spacing-sm);
  font-size: var(--font-aux);
  color: var(--color-primary);
  border: 1rpx solid var(--color-primary);
  border-radius: var(--radius-pill);
}

@media (prefers-reduced-motion: reduce) {
  .contribute-card { transition: none; }
}
</style>
