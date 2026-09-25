<template>
  <view class="page agreement-page">
    <Header title="用户协议" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y>
      <view class="doc">
        <text class="doc-title">知行食记用户协议</text>
        <text class="doc-meta">生效日期：2026 年 9 月 12 日</text>

        <text class="doc-h">一、服务说明</text>
        <text class="doc-p">本应用是面向北京交通大学的校园美食信息与分享工具，提供菜品浏览、搜索与评价功能。菜品信息由管理员录入并由用户反馈共同维护。</text>

        <text class="doc-h">二、账号与认证</text>
        <text class="doc-p">1. 使用微信打开本应用即自动创建账号（未认证状态），可浏览公开内容并提交意见反馈。</text>
        <text class="doc-p">2. 完成校园邮箱认证后，可发表评价、上传配图等。</text>
        <text class="doc-p">3. 请勿将账号交由他人使用，或使用他人身份进行认证。</text>

        <text class="doc-h">三、内容规范</text>
        <text class="doc-p">1. 不得发布违反法律法规、侮辱诽谤、广告引流或虚假信息。</text>
        <text class="doc-p">2. 评价应基于真实就餐体验；同一道菜每人仅可发表一条评价。</text>
        <text class="doc-p">3. 违规内容管理员有权隐藏或删除，并可视情节限制相关账号功能。</text>

        <text class="doc-h">四、信息准确性</text>
        <text class="doc-p">菜品价格、供应时段等信息由用户反馈与管理员维护，可能存在滞后或偏差，请以食堂现场公示为准。</text>

        <text class="doc-h">五、免责与协议变更</text>
        <text class="doc-p">因不可抗力或第三方服务（如微信）导致的服务中断，本应用不承担责任。协议如有变更，将在应用内公示。</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 用户协议：上架合规承载，随包发布（不依赖外部域名与 web-view）。
 * 入口位于「我的」页「其他」分组列表，与「隐私政策」各自独立页面（见 spec privacy-compliance）。
 */
import Header from '@/components/AppHeader.vue'
import { backToHome } from '@/utils/nav'
</script>

<style scoped>
.agreement-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-page); }

.scroll-wrap { flex: 1; min-height: 0; overflow-y: auto; padding: 0 var(--spacing-md) calc(var(--spacing-md) + var(--spacing-lg)); box-sizing: border-box; }

.doc {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  box-sizing: border-box;
}
.doc-title { font-size: var(--font-title); font-weight: var(--weight-semibold); color: var(--text-primary); }
.doc-meta { font-size: var(--font-tiny); color: var(--text-tertiary); margin-bottom: var(--spacing-xs); }
.doc-h { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); margin-top: var(--spacing-sm); }
.doc-p { font-size: var(--font-small); color: var(--text-secondary); line-height: 1.7; }
</style>
