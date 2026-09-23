<template>
  <view class="filter-result">
    <!-- 结果态滚动容器：find-page-layout-restructure —— 滚动随结果内容区（FindResults）走，
         不再由 find/index 页根层包裹两态共用滚动。结果为空或请求失败时静默（无占位）。
         页面级手动刷新手势已于 2026-09-22 下线（change `remove-pull-to-refresh`）：容器恢复为
         普通滚动容器；结果态的恢复路径 = 失败重试块 @tap / 重新提交搜索（宿主页持有）。 -->
    <scroll-view
      class="results-scroll"
      scroll-y
    >
      <!-- 搜索结果：一行一个菜品（find-result-card-polish：单卡内联于 FindResults，DishResultRow 已合并） -->
      <view class="mixed-list" :class="{ single: items.length === 1 }">
        <view
          v-for="item in items"
          :key="`${item.type}-${item.id}`"
          class="mixed-item"
          @tap="selectRow(item)"
        >
          <view class="mixed-thumb">
            <image
              v-if="item.image"
              :src="thumbSrc(item.image)"
              mode="aspectFill"
              class="mixed-thumb-img"
              :class="{ loaded: loadedSet.has(thumbSrc(item.image)) }"
              lazy-load
              @load="loadedSet.add(thumbSrc(item.image))"
            />
            <view v-else class="mixed-thumb-ph">
              <IconSvg name="dish" :size="48" color="var(--text-tertiary)" />
            </view>
          </view>
          <view class="mixed-info">
            <!-- 第一行：菜名（命中不再上红，主色仅留给价格）+ 评分 + 价格，基线对齐 -->
            <view class="mixed-title-row">
              <view class="mixed-name-group">
                <text class="mixed-name">
                  <text
                    v-for="(seg, si) in splitHighlight(item.name)"
                    :key="si"
                    :class="{ hit: seg.hit }"
                  >{{ seg.text }}</text>
                </text>
                <view v-if="item.rating != null" class="mixed-rating-group">
                  <!-- 星色 = 独立语义 token `--color-star`（黄 #FBBF24），**不随主色换肤**
                       （project_spec.md §4.2 / §7.39 第 2 条）。旧实现传 `--color-primary` 属缺陷。 -->
                  <IconSvg name="star-filled" :size="26" color="var(--color-star)" class="mixed-rating-star" />
                  <text class="mixed-rating-num">{{ Number(item.rating).toFixed(1) }}</text>
                </view>
              </view>
              <!-- 价格：展示唯一数据源 = price（现价）；originalPrice 有值且大于 price 时并列划线原价。
                   标签行与距离文案已随「菜品标签 / 坐标距离」全链下线删除（design D8/D9）。 -->
              <view v-if="item.price != null" class="mixed-price-group">
                <text class="mixed-price"><text class="mixed-price-sym">¥</text>{{ formatPrice(item.price) }}</text>
                <text v-if="hasDiscount(item)" class="mixed-original">¥{{ formatPrice(item.originalPrice) }}</text>
              </view>
            </view>
            <!-- 底部：位置（食堂 · 档口名，与首页 DishCard 同序），三级浅灰弱化 -->
            <view class="mixed-sub">
              <text class="mixed-sub-text">
                <text
                  v-for="(seg, si) in splitHighlight(item.sub || '')"
                  :key="si"
                  :class="{ hit: seg.hit }"
                >{{ seg.text }}</text>
              </text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { formatPrice } from '@/utils/money'
import { getImageUrl, getThumbUrl } from '@/utils/image'

/** 搜索混合结果项（仅菜品）；与 find 页 MixedResult 结构兼容 */
interface MixedResultItem {
  type: 'dish'
  id?: number
  name: string
  image?: string
  sub?: string
  price?: number
  originalPrice?: number
  rating?: number
}

/* 对外接口（2026-09-22 收敛，change `remove-pull-to-refresh`）：入参仅 `items` / `keyword`，
   事件仅 `select`；原手动刷新手势所需的触发态 prop 与重跑事件已随之下线一并删除。 */
const props = defineProps<{
  items: MixedResultItem[]
  keyword?: string
}>()

const emit = defineEmits<{
  (e: 'select', id: number): void
}>()

/** 图片淡入去重集合（key = 缩略图 url） */
const loadedSet = reactive(new Set<string>())
function thumbSrc(src?: string): string {
  return src ? getImageUrl(getThumbUrl(src)) : ''
}

/** 「有折扣」判据：originalPrice 有值且大于 price（唯一口径，不引入第三个价格字段） */
function hasDiscount(item: MixedResultItem): boolean {
  return item.originalPrice != null && item.price != null && item.originalPrice > item.price
}

/** 关键词拆段：find-result-card-polish 后命中片段不再上主色（红只给价格），保留分段语义以备未来弱化 */
function splitHighlight(text: string): { text: string; hit: boolean }[] {
  const kw = (props.keyword || '').trim()
  if (!text || !kw) return [{ text, hit: false }]
  const segs: { text: string; hit: boolean }[] = []
  const lowerText = text.toLowerCase()
  const lowerKw = kw.toLowerCase()
  let start = 0
  let idx = lowerText.indexOf(lowerKw, start)
  while (idx !== -1) {
    if (idx > start) segs.push({ text: text.slice(start, idx), hit: false })
    segs.push({ text: text.slice(idx, idx + kw.length), hit: true })
    start = idx + kw.length
    idx = lowerText.indexOf(lowerKw, start)
  }
  if (start < text.length) segs.push({ text: text.slice(start), hit: false })
  return segs
}

function selectRow(item: MixedResultItem) {
  if (item.id != null) emit('select', item.id)
}
</script>

<style scoped>
/* 结果内容区占满宿主（find-body/results-host flex 链），滚动由内部 scroll-view 承担 */
.filter-result {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}
.results-scroll {
  flex: 1;
  min-height: 0;
  padding-bottom: var(--spacing-lg);
}
/* 搜索结果列表（仅菜品，一行一个，左图右信息）。
   顶部间距的**唯一来源 = 宿主页搜索行的下 padding**（UI 文档 §2：块间 `--spacing-lg`），
   本组件不再叠加任何 margin-top（旧注释「贴近 FilterBar」所指的筛选条已于 2026-09-22 全量下线，
   该口径一并作废）。 */
.mixed-list { margin: 0 var(--spacing-md) var(--spacing-md); }
/* 单条结果：结果区**垂直居中**，把留白分到卡片上下两侧（UI 文档 §4「少量结果」）。
   旧实现 `margin-top: 12px` → 卡片悬顶、下方约 70% 屏高空白，读作「还没加载完」。
   ⚠️ 依赖 scroll-view 有确定高度（宿主 `.results-scroll` 为 `flex:1; min-height:0`）；
   `min-height:100%` 在 mp-weixin 的表现须随真机走查复核。 */
.mixed-list.single {
  margin-top: 0;
  min-height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  box-sizing: border-box;
}

/* ===== 单卡样式（原 DishResultRow 内联，find-result-card-polish） ===== */
.mixed-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  /* 卡内边距（UI 文档 §2）：上下 `--spacing-md`(24rpx/12px)、左右 `--spacing-lg`(32rpx/16px)。
     旧值 28rpx = 3.5×8 **不在 8 基网格上**（与当时注释自称「对齐 8 基网格」自相矛盾），已收口。 */
  padding: var(--spacing-md) var(--spacing-lg);
  box-shadow: var(--shadow-card);
  transition: background-color var(--duration-fast) var(--ease-out);
  -webkit-tap-highlight-color: transparent;
  touch-action: manipulation;
}
.mixed-item + .mixed-item { margin-top: var(--spacing-sm); }
.mixed-thumb {
  width: 160rpx;
  height: 160rpx;
  flex-shrink: 0;
  border-radius: var(--radius-icon);
  overflow: hidden;
  background: var(--bg-page);
}
.mixed-thumb-img { width: 100%; height: 100%; opacity: 0; transition: opacity 0.32s var(--ease-out); }
.mixed-thumb-img.loaded { opacity: 1; }
.mixed-thumb-ph { width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; }
.mixed-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-xs); min-height: 160rpx; justify-content: center; }
.mixed-title-row { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); }
.mixed-name-group { flex: 1; min-width: 0; display: flex; align-items: center; gap: var(--spacing-sm); }
.mixed-name {
  flex: 0 1 auto;
  min-width: 0;
  font-size: var(--font-title);
  /* 菜名：600 一级深灰第一阅读落点（find-result-card-polish） */
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
  line-height: 1.3;
  letter-spacing: var(--tracking-h3);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
/* 命中片段：仅**字重加深**，不上主色（主色是价格专用强调色 —— UI 文档 §1 第 5 条）。
   修复「搜什么、结果就叫什么」时命中零反馈、无从扫读的问题。 */
.mixed-name .hit { font-weight: var(--weight-heavy); }
.mixed-sub-text .hit { font-weight: var(--weight-heavy); }
/* 划线原价：辅助档三级灰（旧注释「命中片段不再上主色」与本行无关，属错位，已更正） */
.mixed-original { font-size: var(--font-aux); color: var(--text-tertiary); text-decoration: line-through; font-variant-numeric: tabular-nums; }
.mixed-price-group { display: flex; align-items: baseline; gap: var(--spacing-2xs); flex-shrink: 0; }
/* 价格：专用主色 + 600，卡片唯一高饱和强调。
   字号 = `--font-h3`(36rpx)，**低于菜名一档**（菜名 `--font-title` 44rpx）——
   旧口径与菜名同档，两个最高权重元素并列造成焦点竞争，且与 UI 文档 §4「价格作**第二**视觉重心」相悖。 */
.mixed-price { font-size: var(--font-h3); font-weight: var(--weight-semibold); color: var(--color-price); font-variant-numeric: tabular-nums; }
.mixed-price-sym { font-size: var(--font-body); font-weight: var(--weight-medium); }
.mixed-rating-group { display: inline-flex; align-items: center; gap: 2rpx; flex-shrink: 0; }
.mixed-rating-star { flex-shrink: 0; }
.mixed-rating-num { font-size: var(--font-small); font-weight: var(--weight-medium); color: var(--text-secondary); font-variant-numeric: tabular-nums; }
.mixed-sub { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-sm); margin-top: var(--spacing-xs); font-size: var(--font-aux); }
/* 底部位置统一三级浅灰弱化（find-result-card-polish） */
.mixed-sub-text { flex: 1; min-width: 0; color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
