<template>
  <view class="page profile-edit-page">
    <Header title="个人信息" showBack />

    <scroll-view class="scroll-wrap" scroll-y>
      <view class="info-card">
        <!-- 头像 -->
        <view class="info-row info-tappable" @tap="changeAvatar">
          <text class="info-label">头像</text>
          <view class="avatar-wrap">
            <image v-if="avatar" :src="getImageUrl(avatar)" class="avatar" :class="{ uploading: avatarUploading }" />
            <view v-else class="avatar avatar-empty" :class="{ uploading: avatarUploading }">
              <IconSvg name="user" :size="52" color="var(--text-tertiary)" />
            </view>
            <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" class="row-arrow" />
          </view>
        </view>

        <!-- 昵称 -->
        <view class="info-row">
          <text class="info-label">昵称</text>
          <input v-model="nickname" class="nickname-input" placeholder="请输入昵称" maxlength="16" placeholder-class="input-placeholder" />
        </view>

        <!-- 用户 ID -->
        <view class="info-row">
          <text class="info-label">用户 ID</text>
          <text class="info-value">ID {{ userInfo.id }}</text>
        </view>

        <!-- 认证状态（无独立认证字段，由 role 派生：admin=官方认证 / student=未认证） -->
        <view class="info-row">
          <text class="info-label">认证状态</text>
          <view class="verify-tag" :class="{ ok: isVerified }">
            <text class="verify-text">{{ isVerified ? '已认证' : '未认证' }}</text>
          </view>
        </view>
      </view>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>

    <!-- 保存（固定底部，与其他表单页一致） -->
    <view class="submit-bar">
      <AppButton text="保存" type="primary" :loading="saving" @click="save" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import IconSvg from '@/components/IconSvg.vue'
import { useUserStore } from '@/stores/user'
import { getImageUrl } from '@/utils/image'
import { uploadImage } from '@/api/upload'

const userStore = useUserStore()
const userInfo = computed(() => userStore.userInfo)

const avatar = ref(userInfo.value.avatar || '')
const nickname = ref(userInfo.value.nickname || '')
const saving = ref(false)
/** 头像上传中：禁用重复选择 + 头像半透明反馈 */
const avatarUploading = ref(false)

const isVerified = computed(() => userInfo.value.role === 'admin')

function changeAvatar() {
  if (avatarUploading.value) return
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      avatarUploading.value = true
      try {
        const url = await uploadImage(res.tempFilePaths[0])
        avatar.value = url
        uni.showToast({ title: '头像已更新', icon: 'success' })
      } catch {
        uni.showToast({ title: '上传失败', icon: 'none' })
      } finally {
        avatarUploading.value = false
      }
    },
  })
}

async function save() {
  const name = nickname.value.trim()
  if (!name) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await userStore.updateProfile({ nickname: name, avatar: avatar.value })
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch {
    uni.showToast({ title: '保存失败', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.profile-edit-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0 calc(var(--action-bar-height) + env(safe-area-inset-bottom) + var(--spacing-lg)); }
/* 信息卡：inset 分组卡（Apple 列表分组风格：更大圆角 + 柔和阴影） */
.info-card {
  margin: 0 var(--spacing-md);
  background: var(--bg-card);
  border-radius: var(--radius-modal);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}
.info-row {
  display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md);
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1rpx solid var(--border-color);
  transition: background-color 120ms var(--ease-out);
  -webkit-tap-highlight-color: transparent;
}
.info-row:last-child { border-bottom: none; }
/* 可点行（头像）按压反馈 */
.info-row.info-tappable:active { background-color: var(--bg-soft); }
.info-label { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); flex-shrink: 0; }
.avatar-wrap { display: flex; align-items: center; gap: var(--spacing-sm); }
/* 大头像（104rpx）圆角正方形：与「我的」页 hero 头像（112rpx/24rpx）一致。
   可点行按压时头像轻微缩放（Apple 图像 press 反馈，锚定左上避免跳动） */
.avatar { width: 104rpx; height: 104rpx; border-radius: 24rpx; background: var(--bg-page); transition: transform 120ms var(--ease-out), opacity 120ms var(--ease-out); transform-origin: top left; }
.avatar.uploading { opacity: 0.55; }
.avatar-empty { display: flex; align-items: center; justify-content: center; background: var(--bg-soft); }
.info-row.info-tappable:active .avatar { transform: scale(var(--press-scale)); }
.row-arrow { flex-shrink: 0; }
/* 输入框：右侧留白，光标不贴右缘 */
.nickname-input { flex: 1; min-width: 0; text-align: right; padding-right: var(--spacing-xs); font-size: var(--font-body); color: var(--text-primary); }
.input-placeholder { color: var(--text-tertiary); }
.info-value { font-size: var(--font-body); color: var(--text-secondary); }
.verify-tag {
  display: flex; align-items: center;
  padding: var(--spacing-xs) var(--spacing-md);
  border-radius: var(--radius-tag);
  background: var(--bg-soft);
}
.verify-tag.ok { background: var(--color-primary-soft); }
.verify-text { font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-tertiary); }
.verify-tag.ok .verify-text { color: var(--color-primary); }
/* 保存按钮：固定底部（与其他表单页一致） */
.submit-bar {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 20;
  padding: var(--spacing-md);
  padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom));
  background: var(--bg-card);
  border-top: 2rpx solid var(--border-color);
  box-shadow: var(--shadow-bar-soft);
}
</style>
