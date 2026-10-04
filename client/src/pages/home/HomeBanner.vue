<template>
  <!-- 16:10 轮播 Banner（docs/ui/client/首页菜品浏览.md §3）
       · 清单来自 `GET /banners`（服务端已按序、只返回启用项）：端上按返回顺序渲染，不排序、不写死 URL 与张数；
       · 多张自动轮播 + 指示点；仅一张不轮播、不显示指示点；
       · 空数组 / 请求失败 / 单张失败 → 灰底 + 居中**中性 `empty` 占位**（不得用 `dish` 图标冒充）；
       · 块高由父级下发（H_b），加载中 / 失败**不改变块高**，否则吸顶阈值漂移。 -->
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
      <swiper-item v-for="(b, i) in list" :key="b.id">
        <image
          v-if="b.imageUrl && !broken.has(i)"
          class="banner-img"
          :src="b.imageUrl"
          mode="aspectFill"
          @error="markBroken(i)"
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
import { useBrokenImages } from '@/composables/useBrokenImages'
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

/**
 * 上报「是否真的有轮播图」：父级（首页）据此决定**是否保留 Banner 占位块**——
 * 全停用 / 加载失败时整块收起，避免首页顶部长期挂着一块无意义的 16:10 灰块。
 * 初始（加载中）父级默认保留槽位，加载完成且无图时再收起。
 */
const emit = defineEmits<{ (e: 'ready', has: boolean): void }>()

/** 多图自动轮播间隔（ms）；仅一张时不自动轮播 */
const AUTOPLAY_INTERVAL = 4000
/** 指示点色：微信原生 <swiper> 不接受 var()，取 tokens.ts 登记的原生例外常量 */
const INDICATOR_COLOR = SWIPER_INDICATOR_COLOR
const INDICATOR_ACTIVE_COLOR = SWIPER_INDICATOR_ACTIVE_COLOR

const list = ref<Banner[]>([])
/** 单张加载失败的下标（该张退化为空态，其余张不受影响、轮播继续）—— 破图集合走公共 composable */
const { broken, markBroken, clear: clearBroken } = useBrokenImages()

/** 拉取轮播图：失败**不抛出**（Banner 失败不阻塞首屏网格），退化为空数组 → 整块灰底空态 */
async function load() {
  try {
    list.value = await bannerApi.listBanners()
    clearBroken()
  } catch (e) {
    console.error('加载首页轮播图失败', e)
    list.value = []
  }
  // 无论成功失败都上报真实「有无图」状态，父级据此收起占位块
  emit('ready', list.value.length > 0)
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
/* Banner = **四周留白的圆角图片卡**：
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