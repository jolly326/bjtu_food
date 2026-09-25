<template>
  <!-- 本组件只承载**二级页**「返回箭头 + 居中标题」。
       首页头部在 `pages/home/index.vue` 自持（固定标题带 + 吸顶容器），
       搜索页顶部由「`AppTitleBand`（返回 icon）+ `SearchBar`（首页同款搜索行）」两段式承载。 -->

  <!-- 二级页：返回箭头 + 居中标题 + 右上角留空；搜索页顶部由
       「`AppTitleBand`（返回 icon 占标题位）+ `SearchBar`（与首页同款搜索行）」两段式承载。 -->
  <view class="header-wrap" :style="{ paddingTop: 'max(' + statusBarHeight + 'px, env(safe-area-inset-top))', '--nav-h': navBarHeight + 'px' }">
    <view class="nav" :class="{ 'nav--with-back': showBack }" :style="{ height: navBarHeight + 'px' }">
      <view
        v-if="showBack"
        class="back-area"
        @tap="handleBack"
        role="button"
        aria-label="返回"
      >
        <IconSvg name="arrow-left" :size="'22px'" :color="COLOR_MAP['text-white']" class="back-arrow" />
      </view>
      <text class="title">{{ title }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import IconSvg from './IconSvg.vue'
import { getNavBarHeight } from '@/utils/navMetrics'
import { COLOR_MAP } from '@/theme/tokens'

withDefaults(defineProps<{
  title?: string
  /** 是否显示返回箭头；从首页头像 navigateTo 进入二级页时传 true，TabBar 直入时传 false */
  showBack?: boolean
}>(), {
  title: '',
  showBack: true,
})

const emit = defineEmits<{
  (e: 'back'): void
}>()

const statusBarHeight = ref(20)
const navBarHeight = ref(56)

onMounted(() => {
  // 兼容老基础库：getWindowInfo 不存在时回退 getSystemInfoSync（避免拿不到 statusBarHeight 导致刘海遮挡）
  // @ts-ignore - 跨端兼容（H5 无 wx，退化为固定值）
  const win = (typeof wx !== 'undefined')
    // @ts-ignore
    ? (wx.getWindowInfo ? wx.getWindowInfo() : (wx.getSystemInfoSync ? wx.getSystemInfoSync() : null))
    : null
  const sb = (win && win.statusBarHeight) || 20
  statusBarHeight.value = sb
  // @ts-ignore - 微信胶囊按钮位置（右上角原生组件）：用于「返回 + 居中标题」行高对齐
  const mb = (typeof wx !== 'undefined' && wx.getMenuButtonBoundingClientRect) ? wx.getMenuButtonBoundingClientRect() : null
  if (mb && mb.height) {
    navBarHeight.value = getNavBarHeight(sb, mb)
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
  z-index: var(--z-header);
  /* 底部留白：全站 header 总高以「搜索页(find)」为基准，其 .search-nav 带此留白，
     故此处必须以同一 token（--spacing-sm）复刻，否则搜索页会比其余所有页面高 16rpx。
     ⚠️ 改此值必须同步改 find/index.vue 的 .search-nav —— 两者共用 --spacing-sm，
     只要都引用该 token 就不会漂移；真正要防的是其中一方整条留白被删。
     胶囊居中只由 paddingTop + 行高(--nav-h) 决定，本留白不影响胶囊对齐。 */
  padding-bottom: var(--spacing-sm);
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
  -webkit-tap-highlight-color: transparent;
}
.back-arrow { line-height: 1; }
/* 标题绝对居中：无论有无返回箭头，始终相对导航行真正水平居中（不再因左侧补偿而偏右） */
.title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  text-align: center;
  /* 导航标题档（36rpx / 600）——client-visual-language R3 五档映射，与菜品名/昵称的
     一级标题档（32rpx / 600）区分，避免同一语义出现多套字号 */
  font-size: var(--font-h3);
  font-weight: var(--weight-semibold);
  color: var(--text-white);
  max-width: 60%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 首页头部样式在 `pages/home/index.vue`、搜索页顶部在 `AppTitleBand` + `SearchBar`；本组件仅承载二级页头部。 */
</style>
