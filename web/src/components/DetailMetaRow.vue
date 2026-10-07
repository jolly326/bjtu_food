<script setup lang="ts">
/**
 * 详情抽屉「标签-值」单行（**公共类唯一实现**，`.meta-key` / `.meta-val` 的唯一产出点）。
 *
 * <p><b>存在理由</b>：抽屉元信息表的标签-值对若在页面各写一遍，标签列宽、右对齐、断行规则就会散落N 份
 * `<span class="meta-val">` 成对标签，标签列宽、右对齐、断行规则散落N 份 ⇒ 改排版必漏。
 * 本组件把「标签列轴 + 值列断行 + 两列对齐」收敛到一处（[UI 基线 §1.15](../../../docs/ui/web/公共组件与形态基线.md)）。
 *
 * <p><b>用法</b>：值内容为**纯文本 / 插值**时用本组件；值内含**子元素**（头像格、状态标签、
 * 链接等复合内容）时保留手写 `.meta-row` 结构 —— 那类内容本就不可数据驱动。
 *
 * ```vue
 * <DetailMetaRow k="评价 ID" num>#{{ current?.id }}</DetailMetaRow>
 * <DetailMetaRow k="发表时间">{{ formatDateTime(current?.createdAt) }}</DetailMetaRow>
 * ```
 */
withDefaults(
  defineProps<{
    /** 标签（键）：固定列宽、右对齐，超长就地断行不挤占值列 */
    k: string
    /** 值是否走等宽数字（编号 / 计数 / 价格等需纵向对齐的数值） */
    num?: boolean
  }>(),
  { num: false },
)
</script>

<template>
  <div class="meta-row">
    <span class="meta-key">{{ k }}</span>
    <span class="meta-val" :class="{ num }"><slot /></span>
  </div>
</template>
