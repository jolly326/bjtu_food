<template>
  <!-- 首页：两行头部（头像行 + 整行搜索框），无返回键，右上角留空避让胶囊 -->
  <view v-if="variant === 'home'" class="header-wrap home" :style="{ paddingTop: statusBarHeight + 'px' }">
    <view class="home-row home-top-row">
      <view
        class="user-chip"
        @tap="$emit('avatar')"
        role="button"
        aria-label="我的"
      >
        <image v-if="avatarOk && avatar" :src="avatar" class="user-chip-avatar" @error="avatarOk = false" />
        <view v-else class="user-chip-avatar user-chip-avatar-empty">
          <IconSvg name="user" :size="34" color="var(--text-secondary)" />
        </view>
        <text class="user-chip-name">{{ nickname }}</text>
      </view>
    </view>
    <view class="home-row home-search-row" @tap="$emit('search')">
      <IconSvg name="search" :size="30" color="var(--text-tertiary)" class="home-search-icon" />
      <text class="home-search-placeholder">{{ searchPlaceholder }}</text>
    </view>
  </view>

  <!-- 通用/二级页：返回箭头 + 居中标题 + 右上角留空 -->
  <view v-else class="header-wrap" :class="{ dark: dark }">
    <view class="nav" :style="{ height: navBarHeight + 'px', paddingTop: statusBarHeight + 'px' }">
      <view
        class="back-area"
        @tap="handleBack"
        role="button"
        aria-label="返回"
      >
        <IconSvg name="arrow-left" :size="44" color="var(--text-primary)" class="back-arrow" />
      </view>
      <text class="title">{{ title }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import IconSvg from './IconSvg.vue'

const props = withDefaults(defineProps<{
  /** home=首页两行头部；默认=二级页返回箭头+标题 */
  variant?: 'home' | 'default'
  title?: string
  /** 首页头像（可选） */
  avatar?: string
  /** 首页昵称 */
  nickname?: string
  /** 首页搜索框占位 */
  searchPlaceholder?: string
  /** 深色模式（仅影响无背景变量时的兜底） */
  dark?: boolean
}>(), {
  variant: 'default',
  title: '',
  avatar: '',
  nickname: '',
  searchPlaceholder: '搜索菜品、档口或食堂',
  dark: false,
})

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'avatar'): void
  (e: 'search'): void
}>()

const statusBarHeight = ref(20)
const navBarHeight = ref(44)
const avatarOk = ref(true)
// 头像地址变化（如切换账号）时重置失效标记，避免沿用上一张的 error 状态
watch(() => props.avatar, () => { avatarOk.value = true })

onMounted(() => {
  // 兼容老基础库：getWindowInfo 不存在时回退 getSystemInfoSync（避免拿不到 statusBarHeight 导致刘海遮挡）
  // @ts-ignore - 跨端兼容（H5 无 wx，退化为固定值）
  const win = (typeof wx !== 'undefined')
    // @ts-ignore
    ? (wx.getWindowInfo ? wx.getWindowInfo() : (wx.getSystemInfoSync ? wx.getSystemInfoSync() : null))
    : null
  const sb = (win && win.statusBarHeight) || 20
  statusBarHeight.value = sb
  // @ts-ignore - 微信胶囊按钮位置（右上角原生组件），用于对齐返回行高度
  const mb = (typeof wx !== 'undefined' && wx.getMenuButtonBoundingClientRect) ? wx.getMenuButtonBoundingClientRect() : null
  if (mb && mb.height) {
    navBarHeight.value = (mb.top - sb) * 2 + mb.height
  }
})

function handleBack() {
  emit('back')
}
</script>

<style scoped>
.header-wrap {
  width: 100%;
  box-sizing: border-box;
  background: var(--bg-page);
  border-bottom: 1rpx solid var(--border-color);
  position: sticky;
  top: 0;
  z-index: 100;
}

/* ===== 通用/二级页：返回 + 居中标题 ===== */
.nav {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  box-sizing: border-box;
}
.back-area {
  position: absolute;
  left: var(--spacing-sm);
  top: 0;
  bottom: 0;
  width: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.back-area:active { transform: scale(var(--press-scale)); }
.back-arrow { font-size: 44rpx; line-height: 1; }
.title {
  font-size: var(--font-h2);
  font-weight: var(--weight-bold);
  color: var(--text-primary);
  max-width: 60%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 首页两行头部 ===== */
.header-wrap.home { border-bottom: none; }
.home-row {
  display: flex;
  align-items: center;
  padding: 0 var(--spacing-lg);
  box-sizing: border-box;
}
.home-top-row { padding-top: var(--spacing-sm); padding-bottom: var(--spacing-xs); justify-content: flex-start; }
.home-search-row {
  margin: 0 var(--spacing-lg) var(--spacing-md);
  padding: 0 var(--spacing-md);
  height: 80rpx;
  background: var(--bg-card);
  border-radius: var(--radius-pill, 999rpx);
  box-shadow: var(--shadow-card);
  justify-content: flex-start;
  gap: var(--spacing-xs);
  -webkit-tap-highlight-color: transparent;
}
.home-search-icon { flex-shrink: 0; line-height: 1; }
.home-search-placeholder { font-size: var(--font-body); color: var(--text-tertiary); }
.user-chip {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-xs) var(--spacing-sm);
  border-radius: var(--radius-tag);
  -webkit-tap-highlight-color: transparent;
}
.user-chip-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: var(--bg-soft);
}
.user-chip-avatar-empty { display: flex; align-items: center; justify-content: center; }
.user-chip-name { font-size: var(--font-subtitle); font-weight: var(--weight-semibold); color: var(--text-primary); max-width: 280rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
