<template>
  <!-- 全站页底壁纸层（UI 文档 §11.1）：**壁纸图 + 纱** 两层。
       落在页底之上、内容之下（`z-index: -1`），因此接入任何页面都是「根节点第一行加一个标签」，
       不必调整该页既有层级。
       用法：**页面级（视口锚定、不随页面滚动）→ `<PageWallpaper fixed />`**（各页已接入）。
       ⚠️ 2026-09-26 决议：**不再有「容器切片」用法** —— 标题带 / 吸顶容器 / TabBar 一律不铺表面
       （切片与 `fixed` 页底壁纸像素完全相同，只是多一层不透明拷贝；详见 UI 文档 §11.1）。
       存量 `absolute` 模式仅供未来确有「需要被容器裁切」的场景，当前**无消费方**。

       ⚠️ 壁纸必须走 `<image>`：小程序 WXSS 的 `background-image: url()` **取不到包内本地路径**
       （真机报「本地资源图片无法通过 WXSS 获取」，开发工具却可能正常预览）。纱是纯渐变，不受该限制。 -->
  <view class="wallpaper" :class="{ 'is-fixed': fixed }" :style="{ height: `${resolvedHeightPx}px` }">
    <image class="wallpaper-img" :src="src" mode="aspectFill" />
    <view class="wallpaper-scrim" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

const props = withDefaults(defineProps<{
  /** `true` = 页面级（`position: fixed`，视口锚定、不随页面滚动）；默认 `absolute`（供容器裁切） */
  fixed?: boolean
  /**
   * 视口高（px）：**可选**。传了就用（首页把页面底那一处的值与 `--tabbar-height` 等一起按页面实测值统一下发）；
   * 不传则本组件自测 `wx.getWindowInfo().windowHeight` —— 新页面接入只要 `<PageWallpaper fixed />` 一行。
   */
  heightPx?: number
  /** 壁纸素材（本地）：落 `client/src/static/images/`，引用写 `/static/...`。换壁纸 = 换文件或改这里 */
  src?: string
}>(), {
  fixed: false,
  heightPx: 0,
  src: '/static/images/home-bg.jpg',
})

/** 自测视口高（仅在调用方未下发 `heightPx` 时使用） */
const measuredHeightPx = ref(812)
const resolvedHeightPx = computed(() => (props.heightPx > 0 ? props.heightPx : measuredHeightPx.value))

onMounted(() => {
  if (props.heightPx > 0) return
  // @ts-ignore - 跨端兼容（H5 无 wx，退化为固定值）
  const win = (typeof wx !== 'undefined')
    // @ts-ignore
    ? (wx.getWindowInfo ? wx.getWindowInfo() : (wx.getSystemInfoSync ? wx.getSystemInfoSync() : null))
    : null
  measuredHeightPx.value = (win && win.windowHeight) || 812
})
</script>

<style scoped>
/* 默认 `absolute` + `z-index: -1`：
   · `-1` 只压在**父级背景之上、同层内容之下**（CSS 绘制顺序：父级背景 → 负 z 子层 → 流内内容），
     故接入新页面不会挡住任何内容，也无需给内容加 `z-index`；
   · 默认 `absolute` 是为了**被容器裁切**（吸顶容器 / 标题带 / TabBar 都靠 `overflow: hidden` 裁出一条）——
     若默认 `fixed`，子元素会逃出祖先裁切、直接铺满整屏；
   · 页面级用法显式传 `fixed`（视口锚定、不随页面滚动）。 */
.wallpaper {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  z-index: -1;
  /* 纯展示层：绝不拦截任何点击 */
  pointer-events: none;
}
.wallpaper.is-fixed {
  position: fixed;
}
.wallpaper-img {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  display: block;
}
/* 纱（wash）= 铺满整张壁纸的暖白遮罩（`background-color`，**整张统一、无分段**）：
   ① 降低背景突出度：壁纸仍可见，但不抢内容；
   ② 文字可读性：标题 / 标签 / 卡片间隙都落在同一层纱上 —— 不再有「画面中段一条明暗突变」；
   ③ 与「--bg-page #FFF8EF」同源 → 保留暖米白基调，不会变成「照片上浮 UI」；
   ④ **单点真源** = 全局 token `--page-wash`（定义在 `App.vue` 的 `page{}`）
      → 页面底 / 标题带切片 / TabBar 切片全部取它，改 α 只改那一处、全站生效。 */
.wallpaper-scrim {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  background-color: var(--page-wash);
}
</style>
