<script setup lang="ts">
/**
 * DetailMetaRow —— 详情抽屉「标签-值」单行（**公共类唯一实现**，`.meta-key` / `.meta-val` 的唯一产出点）。
 *
 * <p><b>存在理由</b>：抽屉元信息表的标签-值对若在页面各写一遍，标签列宽、右对齐、断行规则就会散落 N 份
 * `<span class="meta-val">` 成对标签，标签列宽、右对齐、断行规则散落 N 份 ⇒ 改排版必漏。
 * 本组件把「标签列轴 + 值列断行 + 两列对齐」收敛到一处（[UI 基线 §1.15](../../../docs/ui/web/公共组件与形态基线.md)）。
 *
 * <p><b>布局能力</b>：抽屉宽 640px，单列排布会让右侧约 500px 闲置。故本组件支持**跨列**与**强调档**，
 * 配合 `detail-section` 的两栏网格，让同一区块内的字段按重要性分栏排布（详见 §1.15）。
 *
 * <p><b>用法</b>：值内容为**纯文本 / 插值**时用本组件；值内含**子元素**（头像格、状态标签、
 * 链接等复合内容）时保留手写 `.meta-row` 结构 —— 那类内容本就不可数据驱动。
 *
 * ```vue
 * <!-- 单列（默认，向下兼容既有全部用法） -->
 * <DetailMetaRow k="评价 ID" num>#{{ current?.id }}</DetailMetaRow>
 * <!-- 两栏网格内占满整行（长文本 / 多值字段用） -->
 * <DetailMetaRow k="描述" span>...</DetailMetaRow>
 * <!-- 强调档：标签-值上下堆叠、数值放大（评分 / 价格等核心指标用） -->
 * <DetailMetaRow k="评分" span emphasis>4.8</DetailMetaRow>
 * ```
 */
withDefaults(
  defineProps<{
    /** 标签（键）：固定列宽、右对齐，超长就地断行不挤占值列 */
    k: string
    /** 值是否走等宽数字（编号 / 计数 / 价格等需纵向对齐的数值） */
    num?: boolean
    /** 在两栏网格中占满整行（长文本 / 多值字段 —— 单栏会被压窄） */
    span?: boolean
    /** 强调档：标签与值上下堆叠、值放大加粗（评分 / 价格 / 状态等核心指标） */
    emphasis?: boolean
  }>(),
  { num: false, span: false, emphasis: false },
)
</script>

<template>
  <div class="meta-row" :class="{ 'meta-row--span': span, 'meta-row--emphasis': emphasis }">
    <span class="meta-key">{{ k }}</span>
    <span class="meta-val" :class="{ num }"><slot /></span>
  </div>
</template>
