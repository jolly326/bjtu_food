<template>
  <view class="filter-result">
    <!-- 结果态滚动容器：find-page-layout-restructure —— 滚动随结果内容区（FindResults）走，
         不再由 find/index 页根层包裹两态共用滚动。结果为空或请求失败时静默（无占位）。
         容器为普通滚动容器；结果态的恢复路径 = 失败重试块 @tap / 重新提交搜索（宿主页持有）。 -->
    <scroll-view
      class="results-scroll"
      scroll-y
    >
      <!-- 结果列表：一行一个菜品卡（UI 统一 Loop Round 21 抽出为页内私有 `DishResultRow`，
           卡片结构 / 状态 / 命中高亮全部内聚在该组件，本组件只负责列表编排与单结果居中） -->
      <view class="mixed-list" :class="{ single: items.length === 1 }">
        <DishResultRow
          v-for="item in items"
          :key="`${item.type}-${item.id}`"
          :item="item"
          :keyword="keyword"
          @select="selectRow(item)"
        />
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import DishResultRow from './DishResultRow.vue'
import type { MixedResultItem } from '@/types/dish'

/* 结果项类型来自公共 `@/types/dish.MixedResultItem`（UI 统一 Loop Round 17：
   原组件内定义与 find 页 `MixedResult` 逐字段重复，已合并为单一来源）。 */

/* 对外接口：入参仅 `items` / `keyword`，事件仅 `select`。 */
defineProps<{
  items: MixedResultItem[]
  keyword?: string
}>()

const emit = defineEmits<{
  (e: 'select', id: number): void
}>()

/** 行组件已做 id 判空；此处只负责把选中项上抛给宿主页（跳转菜品详情） */
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
/* 搜索结果列表（仅菜品，一行一个卡片）。
   顶部间距的**唯一来源 = 宿主页搜索行的下 padding**（UI 文档 §2：块间 `--spacing-lg`），
   本组件不再叠加任何 margin-top（筛选条不提供，间距口径以宿主页搜索行下 padding 为准）。 */
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
</style>
