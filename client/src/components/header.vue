<template>
  <!-- 首页：单行星胶囊头部（头像 + 整行搜索框），朱砂红底，右上角留空避让胶囊 -->
  <view v-if="variant === 'home'" class="header-wrap home" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))', '--nav-h': navBarHeight + 'px' }">
    <view class="home-row" :style="{ height: navBarHeight + 'px', paddingRight: 'calc(env(safe-area-inset-right, 0px) + ' + rightPad + ')' }">
      <view
        class="user-chip"
        @tap="$emit('avatar')"
        role="button"
        aria-label="我的"
      >
        <image v-if="avatarOk && avatar" :src="avatar" class="user-chip-avatar" @error="avatarOk = false" />
        <view v-else class="user-chip-avatar user-chip-avatar-empty">
          <IconSvg name="user" :size="'22px'" color="#B8B0A8" />
        </view>
      </view>
      <view class="home-search" @tap="$emit('search')" role="search" :aria-label="searchPlaceholder">
        <IconSvg name="search" :size="'18px'" color="var(--text-tertiary)" class="home-search-icon" />
        <text class="home-search-placeholder">{{ searchPlaceholder }}</text>
      </view>
    </view>
  </view>

  <!-- 通用/二级页：返回箭头 + 居中标题 + 右上角留空 -->
  <view v-else class="header-wrap" :class="{ dark: dark }" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))', '--nav-h': navBarHeight + 'px' }">
    <view class="nav" :class="{ 'nav--with-back': showBack }" :style="{ height: navBarHeight + 'px' }">
      <view
        v-if="showBack"
        class="back-area"
        @tap="handleBack"
        role="button"
        aria-label="返回"
      >
        <IconSvg name="arrow-left" :size="'22px'" color="#FFFFFF" class="back-arrow" />
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
const navBarHeight = ref(48)
const avatarOk = ref(true)
// 是否微信小程序环境（决定右上角是否避让原生胶囊）；非微信端（H5）右侧留白收窄
const isWeChat = ref(false)
const rightPad = ref('180rpx')
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
  // 仅在微信小程序环境避让右上角胶囊；H5/其余端收窄右侧留白，避免搜索框右侧大片空白
  isWeChat.value = typeof wx !== 'undefined'
  rightPad.value = isWeChat.value ? '180rpx' : '0rpx'
  // @ts-ignore - 微信胶囊按钮位置（右上角原生组件），用于对齐返回行高度
  const mb = (typeof wx !== 'undefined' && wx.getMenuButtonBoundingClientRect) ? wx.getMenuButtonBoundingClientRect() : null
  if (mb && mb.height) {
    navBarHeight.value = Math.max((mb.top - sb) * 2 + mb.height, 46)
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
  background: var(--color-primary);
  border-bottom: none;
  position: sticky;
  top: 0;
  z-index: 100;
}

/* ===== 通用/二级页：返回 + 居中标题 ===== */
.nav {
  display: flex;
  align-items: center;
  position: relative;
  box-sizing: border-box;
}
.back-area {
  position: absolute;
  left: var(--spacing-sm);
  top: 0;
  bottom: 0;
  width: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.back-area:active { transform: scale(var(--press-scale)); }
.back-arrow { line-height: 1; }
/* 标题绝对居中：无论有无返回箭头，始终相对导航行真正水平居中（不再因左侧补偿而偏右） */
.title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
  font-size: var(--font-h2);
  font-weight: var(--weight-bold);
  color: #FFFFFF;
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
  padding: 0 var(--spacing-lg);
  box-sizing: border-box;
}
.user-chip {
  flex-shrink: 0;
  -webkit-tap-highlight-color: transparent;
}
.user-chip-avatar {
  width: calc(var(--nav-h) - 14px);
  height: calc(var(--nav-h) - 14px);
  border-radius: 16rpx;
  background: #FFFFFF;
}
.user-chip-avatar-empty { display: flex; align-items: center; justify-content: center; }
.home-search {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: calc(var(--nav-h) - 12px);
  padding: 0 var(--spacing-md);
  /* 白色实底（浅色模式），深色模式自动切换为卡片底色；可见性优于透明底 */
  background: var(--bg-card);
  border-radius: 36rpx;
  -webkit-tap-highlight-color: transparent;
}
.home-search-icon { flex-shrink: 0; line-height: 1; }
.home-search-placeholder { font-size: var(--font-body); color: var(--text-tertiary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
