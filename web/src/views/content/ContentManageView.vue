<script setup lang="ts">
/**
 * ContentManageView：信息管理页（内容域唯一入口）。
 * 单一视图：**菜品**（列表 + 新增/编辑弹窗 + 详情 + 批量操作），不再有卡导航。
 *
 * 2026-09-14 拍板（project_spec §7.15）：食堂与档口是「菜品」的附属维度，随菜品表单一起维护
 * → 已移除「食堂（含档口）」独立入口（详见 project_spec §7.15）。
 * 2026-09-15：首页配置入口已随本轮字典域清理一并移除，信息管理收敛为只有「菜品」。
 *
 * 深链兼容：历史 `?tab=home` / `?tab=dish`（旧书签、各页返回链接）访问本页均直接渲染菜品列表，
 * 不再做 tab 降级改写——本页已无多视图切换需求。
 */
import PageContainer from '@/components/layout/PageContainer.vue'
import DishManageView from '@/views/content/DishManageView.vue'
</script>

<template>
  <PageContainer>
    <DishManageView />
  </PageContainer>
</template>
