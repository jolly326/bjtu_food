<template>
  <view class="page">
    <Header title="联系开发者" showBack />
    <FeedbackForm />
  </view>
</template>

<script setup lang="ts">
import Header from '@/components/header.vue'
import FeedbackForm from '@/components/FeedbackForm.vue'
</script>

<style scoped>
.page { min-height: 100vh; background: var(--bg-page); }
</style>
