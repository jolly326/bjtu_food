<script setup lang="ts">
/**
 * 美食地图画布（设计稿 docs/ui/client/美食地图.md；功能规则 docs/func/client/A1-美食地图.md）。
 *
 * 场景逻辑（布局 / 手势 / 相机 / 动画）下沉到 `useMapScene`，本组件只做模板绑定与 emit 转发，
 * 自身不含任何手势 / 几何状态 —— 职责单一、可独立于 DOM 单测。
 */
import type { DishVO } from '@/api'
import ImagePlaceholder from '@/components/ImagePlaceholder.vue'
import { toggleTheme } from '@/theme'
import { useMapScene, type SceneTile } from '@/hooks/useMapScene'
import ThemeTotem from './ThemeTotem.vue'

const props = defineProps<{
  dishes: DishVO[]
  /** 可视净空区高度（px）：底部弹层顶边以上；缺省按整屏计算 */
  netHeight?: number
}>()

const emit = defineEmits<{
  (e: 'pick', dish: DishVO): void
  (e: 'blank'): void
}>()

const { visibleTiles, worldStyle, failedIds, onTouchStart, onTouchMove, onTouchEnd, onImageError } =
  useMapScene(props, {
    onPick: (dish) => emit('pick', dish),
    onBlank: () => emit('blank'),
    onToggleTheme: () => toggleTheme(),
  })

function tileKey(tile: SceneTile): string | number {
  return tile.kind === 'dish' ? tile.dish.id : tile.kind
}
</script>

<template>
  <view
    class="map"
    @touchstart="onTouchStart"
    @touchmove.stop.prevent="onTouchMove"
    @touchend="onTouchEnd"
    @touchcancel="onTouchEnd"
  >
    <view class="map__world" :style="worldStyle">
      <view
        v-for="tile in visibleTiles"
        :key="tileKey(tile)"
        :class="['map__tile', tile.kind === 'dish' ? '' : 'map__tile--functional']"
        :style="tile.style"
        hover-class="map__tile--pressed"
        hover-stay-time="80"
      >
        <template v-if="tile.kind === 'dish'">
          <image
            v-if="tile.src && !failedIds.has(tile.dish.id)"
            class="map__tile-img"
            :src="tile.src"
            mode="aspectFill"
            lazy-load
            @error="onImageError(tile.dish.id)"
          />
          <ImagePlaceholder v-else bare />
        </template>
        <ThemeTotem v-else />
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
.map {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
  touch-action: none;
}

.map__world {
  position: absolute;
  top: 0;
  left: 0;
  transform-origin: 0 0;
  will-change: transform;
}

.map__tile {
  position: absolute;
  box-sizing: border-box;
  overflow: hidden;
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--radius-card);
  background: var(--bg-card);
  /* 极弱弥散阴影：卡片轻微悬浮（层级靠底色 / 描边 / 圆角 + 极弱阴影） */
  box-shadow: var(--shadow-soft);
  /* Q 弹：点按缩放反馈（过冲缓动） */
  transition: transform var(--duration-fast) var(--ease-bounce);
}

.map__tile--pressed {
  transform: scale(0.96);
}

/* 功能网格按钮：图标直接落在背景上，无菜品磁贴的圆角矩形框 / 描边 / 底色 */
.map__tile--functional {
  border: none;
  background: transparent;
  border-radius: 0;
  overflow: visible;
}

.map__tile-img {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: var(--radius-card);
}
</style>
