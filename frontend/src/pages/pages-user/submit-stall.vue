<template>
  <view class="page submit-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="提交档口·食堂" showBack />

    <scroll-view class="scroll-wrap" scroll-y>
      <view class="type-switch">
        <view class="type-btn" :class="{ active: type === 'stall' }" @tap="type = 'stall'">
          <IconSvg name="stall" :size="28" class="type-btn-icon" />
          <text class="type-btn-text">提交档口</text>
        </view>
        <view class="type-btn" :class="{ active: type === 'canteen' }" @tap="type = 'canteen'">
          <IconSvg name="canteen" :size="28" class="type-btn-icon" />
          <text class="type-btn-text">补充食堂</text>
        </view>
      </view>

      <CardSection title="基础信息">
        <view class="field">
          <text class="field-label">{{ type === 'stall' ? '档口名称' : '食堂名称' }}<text class="req">*</text></text>
          <input v-model="form.name" class="field-input" placeholder="请输入名称" maxlength="30" />
        </view>
        <view class="field" v-if="type === 'stall'">
          <text class="field-label">所属食堂<text class="req">*</text></text>
          <picker :range="canteenNames" @change="onCanteenChange">
            <view class="picker-value">{{ form.canteen || '请选择食堂' }}</view>
          </picker>
        </view>
        <view class="field">
          <text class="field-label">位置</text>
          <input v-model="form.location" class="field-input" placeholder="如：学苑食堂 2 层" maxlength="50" />
        </view>
        <view class="field" v-if="type === 'stall'">
          <text class="field-label">营业时间</text>
          <input v-model="form.openTime" class="field-input" placeholder="如：10:00-20:00" maxlength="30" />
        </view>
        <text class="field-hint">营业时间将并入提交说明中展示</text>
      </CardSection>

      <CardSection :title="`${type === 'stall' ? '档口' : '食堂'}图片（≤9 张）`">
        <view class="stall-image-uploader">
          <ImageUploader v-model="form.images" :max="9" />
        </view>
      </CardSection>

      <CardSection title="描述">
        <textarea v-model="form.description" class="desc-input" placeholder="补充一些介绍..." maxlength="500" />
        <text class="char-count">{{ form.description.length }}/500</text>
      </CardSection>

      <view style="height: var(--spacing-xl)" />
    </scroll-view>

    <!-- 底部提交（固定吸底，与 publish-moment/publish-dish 同款 submit-bar） -->
    <view class="submit-bar">
      <AppButton text="提交审核" :loading="submitting" @click="handleSubmit" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'
const theme = useThemeStore()
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import CardSection from '@/components/CardSection.vue'
import AppButton from '@/components/AppButton.vue'
import IconSvg from '@/components/IconSvg.vue'
import { getCanteensWithStalls } from '@/api/canteen'
import { post } from '@/api/http'
import ImageUploader from '@/components/ImageUploader.vue'

const type = ref<'stall' | 'canteen'>('stall')
const submitting = ref(false)
const canteenTree = ref<any[]>([])
const canteenNames = computed(() => canteenTree.value.map(c => c.name))

const form = reactive({
  name: '',
  canteen: '',
  /** 所属食堂 ID（提交档口时必传，后端契约 canteenId） */
  canteenId: 0,
  location: '',
  openTime: '',
  images: [] as string[],
  description: '',
})

async function loadCanteens() {
  try {
    canteenTree.value = await getCanteensWithStalls()
  } catch {
    canteenTree.value = []
  }
}

function onCanteenChange(e: any) {
  const idx = e.detail.value
  form.canteen = canteenNames.value[idx] || ''
  form.canteenId = Number(canteenTree.value[idx]?.id ?? 0)
}

async function handleSubmit() {
  if (submitting.value) return
  if (!form.name.trim()) { uni.showToast({ title: '请填写名称', icon: 'none' }); return }
  if (type.value === 'stall' && !form.canteen) { uni.showToast({ title: '请选择所属食堂', icon: 'none' }); return }

  // 提交档口：canteenId 必传（后端 StallUgcSubmitReq 契约）；营业时间并入描述
  if (type.value === 'stall' && !form.canteenId) { uni.showToast({ title: '请选择所属食堂', icon: 'none' }); return }

  submitting.value = true
  const descParts: string[] = []
  if (form.description.trim()) descParts.push(form.description.trim())
  if (form.openTime.trim()) descParts.push(`营业时间：${form.openTime.trim()}`)
  const payload = {
    type: type.value,
    name: form.name.trim(),
    canteenId: type.value === 'stall' ? form.canteenId : undefined,
    location: form.location.trim() || undefined,
    description: descParts.join('\n') || undefined,
  }
  try {
    await post('/my/stalls', payload)
    uni.showToast({ title: '提交成功，待审核', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1200)
  } catch (e: any) {
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad(() => { loadCanteens() })
</script>

<style scoped>
.submit-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md) 0; }
.type-switch { display: flex; gap: var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-xs); margin: 0 var(--spacing-md) var(--spacing-md); }
.type-btn { flex: 1; display: flex; align-items: center; justify-content: center; gap: var(--spacing-xs); padding: var(--spacing-sm) 0; border-radius: var(--radius-btn); font-size: var(--font-body); color: var(--text-secondary); font-weight: var(--weight-medium); transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.type-btn:active { transform: scale(var(--press-scale)); }
.type-btn.active { background: var(--color-primary); color: var(--color-on-primary); font-weight: var(--weight-semibold); }
.type-btn-icon { flex-shrink: 0; }
.type-btn-text { line-height: 1; }
.field { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); }
.field:last-child { border-bottom: none; }
.field-label { font-size: var(--font-body); color: var(--text-primary); font-weight: var(--weight-medium); width: 160rpx; flex-shrink: 0; }
.req { color: var(--color-error); margin-left: 4rpx; }
.field-input { flex: 1; font-size: var(--font-body); color: var(--text-primary); min-width: 0; }
.picker-value { flex: 1; font-size: var(--font-body); color: var(--text-secondary); }
/* 表单内辅助说明 */
.field-hint { display: block; font-size: var(--font-aux); color: var(--text-tertiary); padding: var(--spacing-xs) var(--spacing-md); }
.char-count { display: block; text-align: right; font-size: var(--font-tiny); color: var(--text-tertiary); margin-top: var(--spacing-xs); font-variant-numeric: tabular-nums; }
/* 复用 ImageUploader：保持与历史设计一致的 180rpx 单元格尺寸 */
.stall-image-uploader { width: 100%; }
.stall-image-uploader :deep(.img-cell) { width: 180rpx; height: 180rpx; border-radius: var(--radius-icon); }
/* 删除角标继承 ImageUploader 基础 48rpx 命中区，不再缩小 */
/* 描述框：与写评价/反馈弹窗 textarea 同款（bg-page 浅底 + radius-card + 无边框） */
.desc-input { width: 100%; min-height: 160rpx; font-size: var(--font-body); color: var(--text-primary); line-height: 1.6; padding: var(--spacing-sm); background: var(--bg-page); border-radius: var(--radius-card); border: none; box-sizing: border-box; }
/* 底部提交栏：吸底（页面 flex 纵向，scroll-wrap flex:1），与 publish-moment/publish-dish 同款 */
.submit-bar { padding: var(--spacing-md); padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom)); background: var(--bg-card); box-shadow: var(--shadow-bar-soft); border-top: 2rpx solid var(--border-color); }
</style>
