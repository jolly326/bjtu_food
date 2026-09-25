<template>
  <view class="page privacy-page">
    <Header title="隐私政策" @back="backToHome" />

    <scroll-view class="scroll-wrap" scroll-y>
      <view class="doc">
        <text class="doc-title">知行食记隐私政策</text>
        <text class="doc-meta">生效日期：2026 年 9 月 20 日</text>

        <text class="doc-h">一、我们收集哪些信息</text>
        <text class="doc-p">1. 微信登录信息：昵称、头像与 openid，用于创建并识别你的账号。我们不收集你的手机号。</text>
        <text class="doc-p">2. 校园身份信息：@bjtu.edu.cn 校园邮箱。仅用于发送验证码、核验在校身份并记录认证关系。</text>
        <text class="doc-p">3. 你主动提交的内容：菜品评价、意见反馈与举报内容及其配图（评价与反馈各 ≤3 张，提交前均经内容安全检测，检测通过后存储），以及你主动设置的头像图片（仅用于头像展示，不做其他用途）。</text>
        <text class="doc-p">4. 使用过程信息：仅统计菜品浏览量（不记录个人浏览足迹，不采集行为日志）。</text>

        <text class="doc-h">二、我们如何使用这些信息</text>
        <text class="doc-p">用于：账号识别与身份核验、评价与反馈内容展示、菜品信息纠错与程序优化、菜品热度统计。</text>
        <text class="doc-p">我们不会将上述信息用于广告投放，也不会向任何第三方出售或提供你的个人信息。</text>

        <text class="doc-h">三、信息如何存储与保护</text>
        <text class="doc-p">你的信息存储在本应用的服务端数据库中，通过访问控制与传输加密进行保护，仅在实现上述功能所必需的期间内保留。</text>

        <text class="doc-h">四、你的权利</text>
        <text class="doc-p">1. 查看与删除自己的评价：「我的」→「我的主页」，可逐条删除。</text>
        <text class="doc-p">2. 注销账号：「我的」→「注销账号」可自助完成。注销后账号将匿名化且不可恢复——重新登录会创建全新账号，你已发表的评价与反馈会保留但不再关联你的身份，也不会回到新账号。</text>

        <text class="doc-h">五、联系我们</text>
        <text class="doc-p">如对本政策有疑问，或需要行使上述权利，请通过应用内「我的」→「意见反馈」提交，我们会尽快处理。</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 隐私政策：上架合规承载，随包发布（不依赖外部域名与 web-view）。
 * 入口位于「我的」页「其他」分组列表，与「用户协议」各自独立页面（见 spec privacy-compliance）。
 */
import Header from '@/components/AppHeader.vue'
import { backToHome } from '@/utils/nav'
</script>

<style scoped>
.privacy-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-page); }

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
