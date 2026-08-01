<template>
  <view class="page settings-page">
    <Header title="设置" showBack />

    <scroll-view class="scroll-wrap" scroll-y>
      <!-- 通知（前端占位，本地存储） -->
      <SettingGroup title="通知">
        <view class="cell" @tap="toggleNotify">
          <IconSvg name="bell" :size="36" color="var(--text-secondary)" class="cell-icon" />
          <text class="cell-label">动态与消息通知</text>
          <view class="switch" :class="{ on: notifyOn }">
            <view class="switch-knob" />
          </view>
        </view>
      </SettingGroup>

      <!-- 通用 -->
      <SettingGroup title="通用">
        <SettingCell label="关于食在交大" icon="dish" @tap="goAbout" />
        <SettingCell label="隐私政策" icon="lock" @tap="goPrivacy" />
        <SettingCell label="清除缓存" icon="delete" @tap="clearCache" />
      </SettingGroup>

      <!-- 账号 -->
      <SettingGroup title="账号">
        <SettingCell label="退出登录" icon="profile" @tap="goLogoutConfirm" />
        <SettingCell label="账号注销" icon="delete" danger @tap="goCancelAccount" />
      </SettingGroup>

      <view class="version-row">
        <text class="version-text">食在交大 v1.0.0</text>
      </view>
      <view style="height: var(--spacing-xl)" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import Header from '@/components/header.vue'
import IconSvg from '@/components/IconSvg.vue'
import SettingGroup from '@/components/SettingGroup.vue'
import SettingCell from '@/components/SettingCell.vue'
import { useUserStore } from '@/stores/user'
import { deleteAccount } from '@/api/user'

const userStore = useUserStore()

// 通知开关（前端占位，本地存储，无后端订阅通道，真推送留三期）
const notifyOn = ref(uni.getStorageSync('setting_notify') !== '0')

function toggleNotify() {
  notifyOn.value = !notifyOn.value
  uni.setStorageSync('setting_notify', notifyOn.value ? '1' : '0')
}

function goAbout() {
  uni.showModal({
    title: '关于食在交大',
    content: '食在交大是面向北京交通大学学生的校园美食分享、评价与社交内容平台。发现食堂美食、分享用餐体验。',
    showCancel: false,
  })
}

function goPrivacy() {
  uni.showModal({
    title: '隐私政策',
    content: '我们仅收集必要的账号与登录信息用于提供服务。您的浏览足迹、动态与收藏仅用于优化你的个性化体验，不会向第三方泄露。',
    showCancel: false,
  })
}

function clearCache() {
  uni.showModal({
    title: '清除缓存',
    content: '确定清除本地缓存吗？不会删除你的账号数据。',
    success: (res) => {
      if (res.confirm) {
        uni.clearStorageSync()
        // 保留登录态
        userStore.restoreFromCache()
        uni.showToast({ title: '缓存已清除', icon: 'none' })
      }
    },
  })
}

function goLogoutConfirm() {
  uni.showModal({
    title: '退出登录',
    content: '确定退出当前账号吗？',
    success: (res) => {
      if (res.confirm) {
        userStore.logout()
        uni.reLaunch({ url: '/pages/profile/index' })
      }
    },
  })
}

// 账号注销（task-12.8）：二次确认 + 风险提示 + 清 token 跳登录
function goCancelAccount() {
  uni.showModal({
    title: '账号注销',
    content: '注销后你的菜品、动态、评价等数据将被删除且不可恢复，确定要继续吗？',
    confirmText: '确认注销',
    confirmColor: '#e54d42',
    success: (res) => {
      if (res.confirm) {
        doDeleteAccount()
      }
    },
  })
}

async function doDeleteAccount() {
  try {
    await deleteAccount()
    uni.removeStorageSync('token')
    uni.removeStorageSync('userInfo')
    uni.showToast({ title: '账号已注销', icon: 'none' })
    setTimeout(() => { uni.reLaunch({ url: '/pages/profile/index' }) }, 600)
  } catch (e: any) {
    uni.showToast({ title: e.message || '注销失败', icon: 'none' })
  }
}
</script>

<style scoped>
.settings-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; }
.cell { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-md) var(--spacing-lg); border-bottom: 2rpx solid var(--border-color); transition: transform 0.12s ease; -webkit-tap-highlight-color: transparent; }
.cell:last-child { border-bottom: none; }
.cell:active { transform: scale(0.99); }
.cell-icon { flex-shrink: 0; }
.cell-label { flex: 1; font-size: var(--font-body); color: var(--text-primary); }
/* 开关 */
.switch { width: 88rpx; height: 48rpx; border-radius: 999rpx; background: var(--border-bold); position: relative; transition: background 0.2s ease; flex-shrink: 0; }
.switch.on { background: var(--color-primary); }
.switch-knob { position: absolute; top: 4rpx; left: 4rpx; width: 40rpx; height: 40rpx; border-radius: 50%; background: var(--text-white); transition: transform 0.2s ease; }
.switch.on .switch-knob { transform: translateX(40rpx); }
.version-row { text-align: center; padding: var(--spacing-xl) 0 var(--spacing-md); }
.version-text { font-size: 24rpx; font-weight: 600; color: var(--text-tertiary); }
</style>
