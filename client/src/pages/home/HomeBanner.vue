<template>
  <!-- 16:10 轮播 Banner（docs/client/ui/client-首页菜品浏览.md §3）
       · 图片清单来自 `GET /banners`（服务端已按 sort_order 升序、只返回启用项）——
         端上按返回顺序渲染、不排序、不写死任何 URL 与张数；
       · 多张：自动轮播（AUTOPLAY_INTERVAL）+ 循环 + 底部居中指示点；仅一张：不轮播、不显示指示点；
       · 空数组 / 请求失败 / 单张失败 → 灰底（--bg-soft）+ 居中「中性 empty 图标」空态
         （图标键取中性 `empty`——Banner 是运营位轮播，容器语义 ≠ 菜品，依 project_spec.md §4.9
          不得用 `dish` 冒充中性占位）；不渲染任何文字说明、不加白卡 / 投影 / 渐变；
       · 块高由父级下发（恒定按 BANNER_ASPECT 推导）——加载中 / 失败不改变块高，
         否则吸顶阈值与切换点漂移（§11.2 常量表 H_b 同源）。 -->
  <view class="home-banner" :style="{ height: `${heightPx}px` }">
    <swiper
      v-if="list.length > 0"
      class="banner-swiper"
      :autoplay="list.length > 1"
      :interval="AUTOPLAY_INTERVAL"
      circular
      :indicator-dots="list.length > 1"
      :indicator-color="INDICATOR_COLOR"
      :indicator-active-color="INDICATOR_ACTIVE_COLOR"
    >
      <swiper-item v-for="b in list" :key="b.id">
        <image
          v-if="b.imageUrl && !failedIds.includes(b.id)"
          class="banner-img"
          :src="b.imageUrl"
          mode="aspectFill"
          @error="onError(b.id)"
        />
        <view v-else class="banner-ph">
          <ImagePlaceholder :size="120" />
        </view>
      </swiper-item>
    </swiper>
    <view v-else class="banner-ph">
      <ImagePlaceholder :size="120" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as bannerApi from '@/api/banner'
import type { Banner } from '@/types/banner'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
// 微信原生 <swiper> 的指示点色不接受 var()（同 ImageSwiper 的原生属性限制例外），必须用真实色值
import {
  SWIPER_INDICATOR_ACTIVE_COLOR,
  SWIPER_INDICATOR_COLOR,
} from '@/theme/tokens'

defineProps<{
  /** Banner 总高（px）：由页面按 `max((min(屏宽, 720) − 左右各 12px) × 10/16, 最小高度兜底)` 推导下发
   *  （`720` = 与 `App.vue` 宽屏限宽**同源**，§3.3），与吸顶阈值 / 纱区间同源——本组件只消费，不自算（§11.2 常量 H_b）。 */
  heightPx: number
}>()

/** 多图自动轮播间隔（ms）；仅一张时不自动轮播 */
const AUTOPLAY_INTERVAL = 4000
/** 指示点色：微信原生 <swiper> 不接受 var()，取 tokens.ts 登记的原生例外常量 */
const INDICATOR_COLOR = SWIPER_INDICATOR_COLOR
const INDICATOR_ACTIVE_COLOR = SWIPER_INDICATOR_ACTIVE_COLOR

const list = ref<Banner[]>([])
/** 单张加载失败的 banner id（该张退化为空态，其余张不受影响、轮播继续） */
const failedIds = ref<number[]>([])

function onError(id: number) {
  if (!failedIds.value.includes(id)) failedIds.value = [...failedIds.value, id]
}

/** 拉取轮播图：失败**不抛出**（Banner 失败不阻塞首屏网格），退化为空数组 → 整块灰底空态 */
async function load() {
  try {
    list.value = await bannerApi.getBanners()
  } catch (e) {
    console.error('加载首页轮播图失败', e)
    list.value = []
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
/* Banner = **四周留白的圆角图片卡**（UI 文档 §3.1）：
   左右各 12px 页面级边距（--spacing-md）+ 四角 --radius-card；图片 aspectFill 铺满整卡。
   上间距（与固定标题带之间）由页面滚动内容的顶部占位承担；下方与搜索区相隔 16px（吸顶容器 padding-top）。 */
.home-banner {
  position: relative;
  margin: 0 var(--spacing-md);
  overflow: hidden;
  background: var(--bg-soft);
  border-radius: var(--radius-card);
}
.banner-swiper {
  width: 100%;
  height: 100%;
}
.banner-img {
  width: 100%;
  height: 100%;
  display: block;
}
.banner-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
