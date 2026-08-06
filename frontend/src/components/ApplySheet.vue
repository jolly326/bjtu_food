<template>
  <view class="apply-root">
    <view
      v-if="open"
      class="sheet-mask"
      :class="{ show: maskShow }"
      @tap="requestClose"
      @touchmove.stop.prevent="noop"
    />
    <view
      class="bottom-sheet"
      :class="{ open: sheetOpen }"
      :style="sheetStyle"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
      @touchcancel="onTouchEnd"
    >
      <view class="sheet-grabber" />
      <view class="sheet-head">
        <text class="sheet-title">信息反馈</text>
        <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" @tap="requestClose" />
      </view>

      <scroll-view class="sheet-body" scroll-y>
        <!-- 信息类型（与反馈中心写反馈弹窗统一：bug 反馈 / 信息有误 / 建议 / 其他） -->
        <view class="form-block">
          <text class="form-label">信息类型</text>
          <view class="seg-row">
            <view
              v-for="t in infoTypes"
              :key="t.value"
              class="seg"
              :class="{ on: innerInfoType === t.value }"
              @tap="innerInfoType = t.value"
            >{{ t.label }}</view>
          </view>
        </view>

        <!-- 反馈对象（详情页由 entityType 预置锁定；自由申请可切换） -->
        <view class="form-block">
          <text class="form-label">反馈对象</text>
          <view class="seg-row">
            <view
              v-for="t in entityTypes"
              :key="t.value"
              class="seg"
              :class="{ on: innerEntityType === t.value }"
              @tap="onEntityTypeTap(t.value)"
            >{{ t.label }}</view>
          </view>
        </view>

        <!-- 实体 ID（profile 自由申请可编辑；详情页预置隐藏） -->
        <view v-if="!entityId" class="form-block">
          <text class="form-label">实体 ID</text>
          <input class="form-input" v-model="innerEntityId" type="number" placeholder="填写要申请的对象 ID" />
        </view>

        <!-- 说明 -->
        <view class="form-block">
          <text class="form-label">说明（选填）</text>
          <textarea class="form-textarea" v-model="innerReason" placeholder="请描述问题情况…" maxlength="500" :auto-height="true" />
        </view>
      </scroll-view>

      <view class="sheet-submit">
        <view
          class="sheet-submit-btn"
          :class="{ disabled: submitting }"
          @tap="submit"
        >
          <text class="sheet-submit-text">{{ submitting ? '提交中…' : '提交申请' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, computed, nextTick } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import { submitApply } from '@/api/apply'

export type ApplyEntityType = 'DISH' | 'STALL' | 'CANTEEN'
export type ApplyAction = 'NEW' | 'CLOSE' | 'CHANGE'

const props = defineProps<{
  open: boolean
  /** 预置实体类型（详情页传入）；不传则用户在 profile 自由选择 */
  entityType?: ApplyEntityType
  /** 预置实体 ID（详情页传入）；不传则用户填写 */
  entityId?: number
}>()

const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'submitted'): void
}>()

const entityTypes: { value: ApplyEntityType; label: string }[] = [
  { value: 'DISH', label: '菜品' },
  { value: 'STALL', label: '档口' },
  { value: 'CANTEEN', label: '食堂' },
]
const infoTypes: { value: string; label: string }[] = [
  { value: 'error', label: 'bug 反馈' },
  { value: 'wrong', label: '信息有误' },
  { value: 'suggestion', label: '建议' },
  { value: 'other', label: '其他' },
]

const innerEntityType = ref<ApplyEntityType>('DISH')
const innerInfoType = ref('wrong')
const innerEntityId = ref('')
const innerReason = ref('')
const submitting = ref(false)

const sheetOpen = ref(false)
const maskShow = ref(false)
const dragOffset = ref(0)

/** 空处理器：mask touchmove.stop 防背景滚动穿透（小程序 catchtouchmove） */
function noop() {}
const dragging = ref(false)

const sheetStyle = computed(() => ({
  transform: `translateY(calc(${sheetOpen.value ? 0 : 100}% + ${dragging.value ? dragOffset.value : 0}px))`,
  transition: dragging.value ? 'none' : 'transform 0.3s cubic-bezier(0.32, 0.72, 0, 1)',
}))

watch(() => props.open, (v) => {
  if (v) {
    // 预置实体类型 / ID（详情页锁定）
    if (props.entityType) innerEntityType.value = props.entityType
    if (props.entityId) innerEntityId.value = String(props.entityId)
    innerInfoType.value = 'wrong'
    innerReason.value = ''
    nextTick(() => {
      maskShow.value = true
      sheetOpen.value = true
    })
  } else {
    maskShow.value = false
    sheetOpen.value = false
    dragOffset.value = 0
  }
})

function onEntityTypeTap(v: ApplyEntityType) {
  // 详情页预置类型不可改
  if (props.entityType) return
  innerEntityType.value = v
}

function requestClose() {
  emit('update:open', false)
}

async function submit() {
  if (submitting.value) return
  const entityId = props.entityId ?? Number(innerEntityId.value)
  if (!entityId) {
    uni.showToast({ title: '请填写实体 ID', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await submitApply({
      entityType: innerEntityType.value,
      // 信息反馈统一走纠错/变更（CHANGE）申请
      applyType: 'CHANGE',
      entityId,
      payload: { reason: innerReason.value.trim(), infoType: innerInfoType.value },
    })
    uni.showToast({ title: '反馈已提交', icon: 'success' })
    emit('update:open', false)
    emit('submitted')
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

let startY = 0
let lastY = 0
let lastTime = 0
let velocity = 0
function onTouchStart(e: any) {
  startY = e.touches?.[0]?.clientY ?? 0
  lastY = startY
  lastTime = Date.now()
  velocity = 0
  dragging.value = true
}
function onTouchMove(e: any) {
  if (!dragging.value) return
  const y = e.touches?.[0]?.clientY ?? 0
  const now = Date.now()
  // 记录瞬时速度（与 DishDetailSheet 一致，apple-design §5 velocity handoff）
  const dt = Math.max(now - lastTime, 1)
  velocity = ((y - lastY) / dt) * 1000 // px/s
  lastY = y
  lastTime = now
  const delta = y - startY
  dragOffset.value = delta > 0 ? delta : 0
}
function onTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  // 松手速度 > 480px/s 视为向下甩动直接关闭，或位移 > 120 关闭（与 DishDetailSheet 手感一致）
  if (velocity > 480 || dragOffset.value > 120) requestClose()
  dragOffset.value = 0
}
</script>

<style scoped>
.apply-root { z-index: 100; }
.sheet-mask {
  position: fixed; inset: 0; background: var(--overlay-scrim);
  opacity: 0; transition: opacity 0.3s ease; z-index: 90;
}
.sheet-mask.show { opacity: 1; }

.bottom-sheet {
  position: fixed; left: 0; right: 0; bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 100;
  transform: translateY(100%);
  display: flex;
  flex-direction: column;
  max-height: 80vh;
  padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom));
  will-change: transform;
}
.bottom-sheet.open { transform: translateY(0); }

/* 半透明深色 grabber（Apple 弹层材质：与兄弟弹层统一用 --overlay-dark-soft token） */
.sheet-grabber { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--overlay-dark-soft); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-title { font-size: var(--font-h3); font-weight: var(--weight-bold); color: var(--text-primary); }
.sheet-close { padding: 0 var(--spacing-xs); }

.sheet-body { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0; }
/* 表单块：与反馈中心弹窗 fb-block 一致（padding md lg + 底部分隔线） */
.form-block { padding: var(--spacing-md) var(--spacing-lg); border-bottom: 2rpx solid var(--border-color); }
.form-block:last-child { border-bottom: none; }
.form-label { display: block; font-size: var(--font-aux); font-weight: var(--weight-bold); color: var(--text-secondary); margin-bottom: var(--spacing-sm); }
.seg-row { display: flex; gap: var(--spacing-sm); flex-wrap: wrap; }
/* 类型选择 chips：与反馈中心弹窗 type-chip 同款（选中主色软底 + 边框 + 主色字） */
.seg { padding: var(--spacing-xs) var(--spacing-md); border-radius: var(--radius-tag); background: var(--bg-soft); border: 2rpx solid transparent; font-size: var(--font-aux); color: var(--text-secondary); font-weight: var(--weight-semibold); transition: background 0.15s ease, border-color 0.15s ease, transform 0.12s; -webkit-tap-highlight-color: transparent; }
.seg:active { transform: scale(var(--press-scale)); }
.seg.on { background: var(--color-primary-soft); border-color: var(--color-primary); color: var(--color-primary); }
/* 输入框 / 描述框：与反馈中心弹窗同款（bg-page 浅底 + radius-card + 无边框） */
.form-input { width: 100%; height: 88rpx; background: var(--bg-page); border-radius: var(--radius-card); border: none; padding: 0 var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); box-sizing: border-box; }
.form-textarea { width: 100%; min-height: 200rpx; background: var(--bg-page); border-radius: var(--radius-card); border: none; padding: var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); line-height: 1.5; box-sizing: border-box; }
.sheet-submit { padding: var(--spacing-md) var(--spacing-lg); border-top: 2rpx solid var(--border-color); }
.sheet-submit-btn { height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--color-primary); box-shadow: var(--shadow-bar-primary); transition: transform 0.12s ease, opacity 0.12s ease; -webkit-tap-highlight-color: transparent; }
.sheet-submit-btn:active { transform: scale(var(--press-scale)); }
.sheet-submit-btn.disabled { opacity: 0.58; }
.sheet-submit-text { font-size: var(--font-card); font-weight: var(--weight-bold); color: var(--color-on-primary); }

@media (prefers-reduced-motion: reduce) {
  .sheet-mask { transition: opacity 0.2s ease; }
  .bottom-sheet { transition: opacity 0.2s ease; transform: none !important; }
}
</style>
