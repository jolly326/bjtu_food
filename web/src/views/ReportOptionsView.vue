<script setup lang="ts">
/**
 * 举报原因管理（`/report-options`，字典契约见 [web/report-options.md](../../../docs/api/web/report-options.md)）。
 *
 * <p>页面体由两族工单字典共用（举报原因 / 反馈类型同形），本页只传入 `kind`。
 */
import OptionDictManager from '@/components/OptionDictManager.vue'
</script>

<template>
  <OptionDictManager kind="report" />
</template>
