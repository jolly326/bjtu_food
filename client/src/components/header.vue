<template>
  <!-- 首页：单行星胶囊头部（头像 + 整行搜索框），朱砂红底，右上角留空避让胶囊 -->
  <view v-if="variant === 'home'" class="header-wrap home" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))' }">
    <view class="home-row">
      <view
        class="user-chip"
        @tap="$emit('avatar')"
        role="button"
        aria-label="我的"
      >
        <image v-if="avatarOk && avatar" :src="avatar" class="user-chip-avatar" @error="avatarOk = false" />
        <view v-else class="user-chip-avatar user-chip-avatar-empty">
          <IconSvg name="user" :size="34" color="#FFFFFF" />
        </view>
      </view>
      <view class="home-search" @tap="$emit('search')">
        <IconSvg name="search" :size="30" color="#FFFFFF" class="home-search-icon" />
        <text class="home-search-placeholder">{{ searchPlaceholder }}</text>
      </view>
    </view>
  </view>

  <!-- 通用/二级页：返回箭头 + 居中标题 + 右上角留空 -->
  <view v-else class="header-wrap" :class="{ dark: dark }" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))' }">
    <view class="nav" :class="{ 'nav--with-back': showBack }" :style="{ height: navBarHeight + 'px' }">
      <view
        v-if="showBack"
        class="back-area"
        @tap="handleBack"
        role="button"
        aria-label="返回"
      >
        <IconSvg name="arrow-left" :size="44" color="var(--color-primary)" class="back-arrow" />
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
  /** 是否显示返回箭头；从首页头像 navigateTo 进入二级页时传 true，TabBar 直入时传 false */
  showBack?: boolean
}>(), {
  variant: 'default',
  title: '',
  avatar: '',
  nickname: '',
  searchPlaceholder: '搜索菜品、档口或食堂',
  dark: false,
  showBack: true,
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
/* 有返回箭头时：左侧留白补偿，使标题在「返回区 + 标题 + 右侧留白」间视觉居中 */
.nav--with-back {
  padding-left: calc(80rpx + var(--spacing-sm));
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

/* ===== 首页单行星胶囊头部（头像 + 搜索框，朱砂红底） ===== */
.header-wrap.home {
  background: var(--color-primary);
  border-bottom: none;
}
.home-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-lg);
  /* 右上角避让微信原生胶囊（约 180rpx）；H5 端仅多留白，不影响布局 */
  padding-right: calc(env(safe-area-inset-right, 0px) + 180rpx);
  box-sizing: border-box;
}
.user-chip {
  flex-shrink: 0;
  -webkit-tap-highlight-color: transparent;
}
.user-chip-avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
}
.user-chip-avatar-empty { display: flex; align-items: center; justify-content: center; }
.home-search {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: 72rpx;
  padding: 0 var(--spacing-md);
  background: rgba(255, 255, 255, 0.18);
  border-radius: 36rpx;
  -webkit-tap-highlight-color: transparent;
}
.home-search-icon { flex-shrink: 0; line-height: 1; }
.home-search-placeholder { font-size: var(--font-body); color: rgba(255, 255, 255, 0.85); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
