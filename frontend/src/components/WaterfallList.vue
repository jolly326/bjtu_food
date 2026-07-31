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
  flex-wrap: nowrap;
  align-items: flex-start;
  gap: 24rpx;
  width: 100%;
  box-sizing: border-box;
  padding-bottom: calc(var(--tabbar-height) + env(safe-area-inset-bottom));
}
.waterfall-col {
  /* 固定等宽双列：显式 flex:0 0 calc(50%-12rpx)（grow0/shrink0/basis），
     禁用 flex:1 避免小程序塌列；width:auto 让 flex-basis 主导，配合 min-width:0
     与 overflow:hidden 兜底，防止左列卡片内不可收缩宽内容把整行撑开挤出右列。
     不使用 CSS 变量，规避 scoped 下 var(--spacing-md) 解析失败导致右列 0 宽。 */
  flex: 0 0 calc(50% - 12rpx);
  width: auto;
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
