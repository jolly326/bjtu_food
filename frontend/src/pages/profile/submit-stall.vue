<template>
  <view class="page submit-page">
    <Header title="提交档口·食堂" showBack />

    <scroll-view class="scroll-wrap" scroll-y>
      <view class="type-switch">
        <view class="type-btn" :class="{ active: type === 'stall' }" @tap="type = 'stall'">提交档口</view>
        <view class="type-btn" :class="{ active: type === 'canteen' }" @tap="type = 'canteen'">补充食堂</view>
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
      </CardSection>

      <CardSection :title="`${type === 'stall' ? '档口' : '食堂'}图片（≤9 张）`">
        <view class="stall-image-uploader">
          <ImageUploader v-model="form.images" :max="9" />
        </view>
      </CardSection>

      <CardSection title="描述">
        <textarea v-model="form.description" class="desc-input" placeholder="补充一些介绍..." maxlength="500" />
      </CardSection>

      <view class="submit-wrap">
        <AppButton text="提交审核" :loading="submitting" @click="handleSubmit" />
      </view>
      <view style="height: var(--spacing-lg)" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import CardSection from '@/components/CardSection.vue'
import AppButton from '@/components/AppButton.vue'
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
  form.canteen = canteenNames.value[e.detail.value] || ''
}

async function handleSubmit() {
  if (submitting.value) return
  if (!form.name.trim()) { uni.showToast({ title: '请填写名称', icon: 'none' }); return }
  if (type.value === 'stall' && !form.canteen) { uni.showToast({ title: '请选择所属食堂', icon: 'none' }); return }

  submitting.value = true
  const payload = {
    type: type.value,
    name: form.name.trim(),
    canteen: form.canteen || undefined,
    location: form.location.trim() || undefined,
    openTime: form.openTime.trim() || undefined,
    images: form.images,
    description: form.description.trim() || undefined,
  }
  try {
    // 学生提交档口·食堂：status=pending 待审核（后端契约待 task-09 落库，路径以 /my/stalls 约定）
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
.type-btn { flex: 1; text-align: center; padding: var(--spacing-sm) 0; border-radius: var(--radius-btn); font-size: var(--font-body); color: var(--text-secondary); font-weight: 500; transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.type-btn:active { transform: scale(var(--press-scale)); }
.type-btn.active { background: var(--color-primary); color: var(--text-white); font-weight: 600; }
.field { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); }
.field:last-child { border-bottom: none; }
.field-label { font-size: var(--font-body); color: var(--text-primary); font-weight: 500; width: 160rpx; flex-shrink: 0; }
.req { color: var(--color-error); margin-left: 4rpx; }
.field-input { flex: 1; font-size: var(--font-body); color: var(--text-primary); min-width: 0; }
.picker-value { flex: 1; font-size: var(--font-body); color: var(--text-secondary); }
/* 复用 ImageUploader：保持与历史设计一致的 180rpx 单元格尺寸 */
.stall-image-uploader { width: 100%; }
.stall-image-uploader :deep(.img-cell) { width: 180rpx; height: 180rpx; border-radius: var(--radius-icon); }
.stall-image-uploader :deep(.img-remove) { width: 36rpx; height: 36rpx; }
.desc-input { width: 100%; min-height: 160rpx; font-size: var(--font-body); color: var(--text-primary); padding: var(--spacing-sm); border: 2rpx solid var(--border-color); border-radius: var(--radius-icon); box-sizing: border-box; }
.submit-wrap { padding: var(--spacing-md); }
</style>
