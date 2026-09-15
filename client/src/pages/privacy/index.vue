<template>
  <view class="page privacy-page">
    <Header title="隐私政策" @back="backToHome" />

    <!-- 文本切换：隐私政策 / 用户协议（与意见反馈页胶囊标签同语言） -->
    <view class="tabs">
      <view
        v-for="t in tabs"
        :key="t.key"
        class="tab"
        :class="{ active: current === t.key }"
        @tap="current = t.key"
      >
        <text class="tab-text">{{ t.label }}</text>
      </view>
    </view>

    <scroll-view class="scroll-wrap" scroll-y>
      <view class="doc">
        <template v-if="current === 'privacy'">
          <text class="doc-title">知行食记隐私政策</text>
          <text class="doc-meta">生效日期：2026 年 9 月 12 日</text>

          <text class="doc-h">一、我们收集哪些信息</text>
          <text class="doc-p">1. 微信登录信息：昵称、头像与 openid，用于创建并识别你的账号。我们不收集你的手机号。</text>
          <text class="doc-p">2. 校园身份信息：@bjtu.edu.cn 校园邮箱。仅用于发送验证码、核验在校身份并记录认证关系。</text>
          <text class="doc-p">3. 你主动提交的内容：菜品评价、意见反馈与举报内容（均为纯文本），以及你主动设置的头像图片（仅用于头像展示，不做其他用途）。</text>
          <text class="doc-p">4. 使用过程信息：菜品浏览足迹，仅用于菜品浏览量统计与当日重复浏览去重。</text>

          <text class="doc-h">二、我们如何使用这些信息</text>
          <text class="doc-p">用于：账号识别与身份核验、评价与反馈内容展示、菜品信息纠错与程序优化、菜品热度统计。</text>
          <text class="doc-p">我们不会将上述信息用于广告投放，也不会向任何第三方出售或提供你的个人信息。</text>

          <text class="doc-h">三、信息如何存储与保护</text>
          <text class="doc-p">你的信息存储在本应用的服务端数据库中，通过访问控制与传输加密进行保护，仅在实现上述功能所必需的期间内保留。</text>

          <text class="doc-h">四、你的权利</text>
          <text class="doc-p">1. 查看与删除自己的评价：「我的」→「我的评价」，可逐条删除。</text>
          <text class="doc-p">2. 注销账号：可通过下方联系方式申请注销，注销后相关个人信息将按规则清除。</text>

          <text class="doc-h">五、联系我们</text>
          <text class="doc-p">如对本政策有疑问，或需要行使上述权利，请通过应用内「我的」→「意见反馈」提交，我们会尽快处理。</text>
        </template>

        <template v-else>
          <text class="doc-title">知行食记用户协议</text>
          <text class="doc-meta">生效日期：2026 年 9 月 12 日</text>

          <text class="doc-h">一、服务说明</text>
          <text class="doc-p">本应用是面向北京交通大学的校园美食信息与分享工具，提供菜品浏览、搜索与评价功能。菜品信息由管理员录入并由用户反馈共同维护。</text>

          <text class="doc-h">二、账号与认证</text>
          <text class="doc-p">1. 使用微信打开本应用即自动创建账号（未认证状态），可浏览公开内容并提交意见反馈。</text>
          <text class="doc-p">2. 完成校园邮箱认证后，可发表评价、标记「有用」等。</text>
          <text class="doc-p">3. 请勿将账号交由他人使用，或使用他人身份进行认证。</text>

          <text class="doc-h">三、内容规范</text>
          <text class="doc-p">1. 不得发布违反法律法规、侮辱诽谤、广告引流或虚假信息。</text>
          <text class="doc-p">2. 评价应基于真实就餐体验；同一道菜每人仅可发表一条评价。</text>
          <text class="doc-p">3. 违规内容管理员有权隐藏或删除，并可视情节限制相关账号功能。</text>

          <text class="doc-h">四、信息准确性</text>
          <text class="doc-p">菜品价格、供应时段等信息由用户反馈与管理员维护，可能存在滞后或偏差，请以食堂现场公示为准。</text>

          <text class="doc-h">五、免责与协议变更</text>
          <text class="doc-p">因不可抗力或第三方服务（如微信）导致的服务中断，本应用不承担责任。协议如有变更，将在应用内公示。</text>
        </template>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
/**
 * 隐私政策与用户协议：上架合规承载，随包发布（不依赖外部域名与 web-view）。
 * 入口位于「我的」页底部信息区，不占用功能宫格格位（见 spec privacy-compliance）。
 */
import { ref } from 'vue'
import Header from '@/components/AppHeader.vue'
import { backToHome } from '@/utils/nav'

type DocKey = 'privacy' | 'terms'

const tabs: { key: DocKey; label: string }[] = [
  { key: 'privacy', label: '隐私政策' },
  { key: 'terms', label: '用户协议' },
]

const current = ref<DocKey>('privacy')
</script>

<style scoped>
.privacy-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-page); }

.tabs { display: flex; gap: var(--spacing-xs); padding: var(--spacing-sm) var(--spacing-md); }
.tab {
  padding: var(--spacing-xs) var(--spacing-md);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  border: 2rpx solid var(--border-color);
}
.tab.active { background: var(--color-primary-soft); border-color: transparent; }
.tab-text { font-size: var(--font-aux); color: var(--text-secondary); }
.tab.active .tab-text { color: var(--color-primary); font-weight: var(--weight-medium); }

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
