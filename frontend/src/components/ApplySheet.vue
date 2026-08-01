<template>
  <view class="apply-root">
    <view
      v-if="open"
      class="sheet-mask"
      :class="{ show: maskShow }"
      @tap="requestClose"
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
        <text class="sheet-title">申请下架 / 纠错</text>
        <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" @click="requestClose" />
      </view>

      <scroll-view class="sheet-body" scroll-y>
        <!-- 实体类型（profile 自由申请可编辑；详情页由 entityType 预置锁定） -->
        <view class="form-block">
          <text class="form-label">实体类型</text>
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

        <!-- 申请动作 -->
        <view class="form-block">
          <text class="form-label">申请动作</text>
          <view class="seg-row">
            <view
              v-for="a in actions"
              :key="a.value"
              class="seg"
              :class="{ on: innerAction === a.value }"
              @tap="innerAction = a.value"
            >{{ a.label }}</view>
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
          <textarea class="form-textarea" v-model="innerReason" placeholder="请描述下架/纠错原因…" maxlength="500" :auto-height="true" />
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
import { ref, watch, computed } from 'vue'
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
const actions: { value: ApplyAction; label: string }[] = [
  { value: 'CLOSE', label: '下架 / 关闭' },
  { value: 'CHANGE', label: '纠错 / 变更' },
]

const innerEntityType = ref<ApplyEntityType>('DISH')
const innerAction = ref<ApplyAction>('CLOSE')
const innerEntityId = ref('')
const innerReason = ref('')
const submitting = ref(false)

const sheetOpen = ref(false)
const maskShow = ref(false)
const dragOffset = ref(0)
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
    innerAction.value = 'CLOSE'
    innerReason.value = ''
    requestAnimationFrame(() => {
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
      applyType: innerAction.value,
      entityId,
      payload: { reason: innerReason.value.trim() },
    })
    uni.showToast({ title: '申请已提交', icon: 'success' })
    emit('update:open', false)
    emit('submitted')
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

let startY = 0
function onTouchStart(e: any) {
  startY = e.touches?.[0]?.clientY ?? 0
  dragging.value = true
}
function onTouchMove(e: any) {
  if (!dragging.value) return
  const y = e.touches?.[0]?.clientY ?? 0
  const delta = y - startY
  dragOffset.value = delta > 0 ? delta : 0
}
function onTouchEnd() {
  if (!dragging.value) return
  dragging.value = false
  if (dragOffset.value > 120) requestClose()
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

.sheet-grabber { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--border-color); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); }
.sheet-title { font-size: var(--font-h3); font-weight: 700; color: var(--text-primary); }
.sheet-close { padding: 0 var(--spacing-xs); }

.sheet-body { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0; }
.form-block { padding: var(--spacing-sm) var(--spacing-lg); border-bottom: 2rpx solid var(--border-color); }
.form-block:last-child { border-bottom: none; }
.form-label { display: block; font-size: var(--font-aux); font-weight: 700; color: var(--text-secondary); margin-bottom: var(--spacing-sm); }
.seg-row { display: flex; gap: var(--spacing-sm); flex-wrap: wrap; }
.seg { padding: var(--spacing-xs) var(--spacing-lg); border-radius: var(--radius-tag); background: var(--bg-soft); font-size: var(--font-aux); color: var(--text-secondary); font-weight: 600; transition: background 0.15s, transform 0.12s; -webkit-tap-highlight-color: transparent; }
.seg:active { transform: scale(0.97); }
.seg.on { background: var(--color-primary); color: var(--text-white); }
.form-input { width: 100%; height: 88rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: 0 var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); box-sizing: border-box; }
.form-textarea { width: 100%; min-height: 160rpx; background: var(--bg-soft); border-radius: var(--radius-btn); padding: var(--spacing-sm) var(--spacing-md); font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; box-sizing: border-box; }
.sheet-submit { padding: var(--spacing-md) var(--spacing-lg); border-top: 2rpx solid var(--border-color); }
.sheet-submit-btn { height: 88rpx; display: flex; align-items: center; justify-content: center; border-radius: var(--radius-btn); background: var(--color-primary); box-shadow: var(--shadow-bar-primary); transition: transform 0.12s ease, opacity 0.12s ease; -webkit-tap-highlight-color: transparent; }
.sheet-submit-btn:active { transform: scale(var(--press-scale)); }
.sheet-submit-btn.disabled { opacity: 0.58; }
.sheet-submit-text { font-size: var(--font-card); font-weight: 700; color: var(--text-white); }

@media (prefers-reduced-motion: reduce) {
  .sheet-mask { transition: opacity 0.2s ease; }
  .bottom-sheet { transition: opacity 0.2s ease; transform: none !important; }
}
</style>
