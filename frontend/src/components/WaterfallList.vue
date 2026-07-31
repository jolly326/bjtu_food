<template>
  <view class="waterfall-grid">
    <!--
      双列瀑布流：在组件内直接两列 v-for 渲染，避免「自定义组件(WaterfallItem)
      嵌套 wx:for + 跨组件 scoped slot」在微信小程序编译器下的塌列坑（右列 slot
      内容丢失、整列空白）。同组件内 v-for + 父级 #card 插槽在 mp-weixin 下稳定。
    -->
    <view class="waterfall-col">
      <view
        v-for="(entry, i) in splitList.left"
        :key="entry.key"
        class="waterfall-item enter-up"
        :style="{ '--enter-i': i }"
      >
        <slot name="card" :item="entry.item" />
      </view>
    </view>
    <view class="waterfall-col">
      <view
        v-for="(entry, i) in splitList.right"
        :key="entry.key"
        class="waterfall-item enter-up"
        :style="{ '--enter-i': i }"
      >
        <slot name="card" :item="entry.item" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  list: any[]
  itemKey?: string
}>(), {
  list: () => [],
  itemKey: 'id',
})

/**
 * 瀑布流分列：按奇偶索引切成左右两列，每列各自 v-for 渲染。
 *
 * 微信小程序塌列根因修复（方案 B）：
 * 1. :key 使用「itemKey + 全局索引」生成全局唯一键。原 genKey 兜底键左右列
 *    会撞成 wf-0/wf-2...，导致 setData 后整列塌空。idx 是 list 全局索引，
 *    左右列 idx 必然不同（左 0,2,4… / 右 1,3,5…），key 全局唯一。
 * 2. 列宽固定 calc(50% - 12rpx)，不再依赖 var(--spacing-md)。原列宽在 scoped
 *    样式下 CSS 变量解析失败会算出 NaN 宽度，从而塌成单列（右列 0 宽）。
 * 3. splitList 为 computed，list 变化（含分页追加）即重算，无需 watch。
 */
const splitList = computed(() => {
  const left: { item: any; key: string }[] = []
  const right: { item: any; key: string }[] = []
  props.list.forEach((item, idx) => {
    const rawKey = item?.[props.itemKey]
    const key = (rawKey !== undefined && rawKey !== null && rawKey !== '')
      ? `wf-${rawKey}-${idx}`
      : `wf-idx-${idx}`
    const entry = { item, key }
    if (idx % 2 === 0) left.push(entry)
    else right.push(entry)
  })
  return { left, right }
})
</script>

<style scoped>
.waterfall-grid {
  display: flex;
  /* 关键：不给 flex-wrap，强制两列横向排列，父级必须是 100% 可解析宽度 */
  flex-wrap: nowrap;
  align-items: flex-start;
  /* 列间距用固定 rpx（不依赖 CSS 变量，规避 scoped 解析失败） */
  gap: 24rpx;
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding-bottom: 40rpx;
}
.waterfall-col {
  /* 微信真机最稳的等分双列写法：
     用 flex:1 1 0 + width:0 强制两列均分父级宽度，父级宽度由 .waterfall-grid
     的 width:100% 决定（scroll-view 内部 block 默认 750rpx 视口宽）。
     width:0 让 flex-basis:0 主导、shrink/grow 均摊；配合 min-width:0 +
     overflow:hidden 兜住卡片内不可收缩内容，避免被撑破挤出右列。
     额外加 max-width:50% 兜底，杜绝任何环境下右列被压成 0 宽而塌列。 */
  flex: 1 1 0;
  width: 0;
  max-width: 50%;
  min-width: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.waterfall-item {
  width: 100%;
  box-sizing: border-box;
}
</style>
