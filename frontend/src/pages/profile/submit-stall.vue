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
        <view class="image-grid">
          <view v-for="(img, idx) in form.images" :key="idx" class="image-cell">
            <image class="image-thumb" :src="getImageUrl(img)" mode="aspectFill" />
            <text class="image-remove" @tap="removeImage(idx)">✕</text>
          </view>
          <view v-if="form.images.length < 9" class="image-cell image-add" @tap="selectImage">
            <text class="image-add-icon">+</text>
          </view>
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
import { getImageUrl } from '@/utils/image'
import { uploadImage } from '@/api/upload'
import { getCanteensWithStalls } from '@/api/canteen'
import { post } from '@/api/http'

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

function removeImage(idx: number) {
  form.images.splice(idx, 1)
}

function selectImage() {
  uni.chooseImage({
    count: 9 - form.images.length,
    success: async (res) => {
      for (const p of res.tempFilePaths.slice(0, 9 - form.images.length)) {
        try {
          const url = await uploadImage(p)
          form.images.push(url)
        } catch {
          uni.showToast({ title: '图片上传失败', icon: 'none' })
        }
      }
    },
  })
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
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md); }
.type-switch { display: flex; gap: var(--spacing-sm); background: var(--bg-card); border-radius: var(--radius-card); padding: var(--spacing-xs); margin-bottom: var(--spacing-md); }
.type-btn { flex: 1; text-align: center; padding: var(--spacing-sm) 0; border-radius: var(--radius-icon); font-size: var(--font-body); color: var(--text-secondary); font-weight: 500; transition: transform 120ms var(--ease-out); -webkit-tap-highlight-color: transparent; }
.type-btn:active { transform: scale(var(--press-scale)); }
.type-btn.active { background: var(--color-primary); color: var(--text-white); font-weight: 600; }
.field { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); }
.field:last-child { border-bottom: none; }
.field-label { font-size: var(--font-body); color: var(--text-primary); font-weight: 500; width: 160rpx; flex-shrink: 0; }
.req { color: var(--color-error); margin-left: 4rpx; }
.field-input { flex: 1; font-size: var(--font-body); color: var(--text-primary); min-width: 0; }
.picker-value { flex: 1; font-size: var(--font-body); color: var(--text-secondary); }
.image-grid { display: flex; flex-wrap: wrap; gap: var(--spacing-md); }
.image-cell { width: 180rpx; height: 180rpx; border-radius: var(--radius-icon); overflow: hidden; position: relative; background: var(--bg-page); }
.image-thumb { width: 100%; height: 100%; }
.image-remove { position: absolute; top: 4rpx; right: 4rpx; width: 36rpx; height: 36rpx; background: var(--badge-dark-bg); color: var(--badge-dark-text); border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: var(--font-tiny); }
.image-add { border: 4rpx dashed var(--border-color); display: flex; align-items: center; justify-content: center; background: var(--bg-page); }
.image-add-icon { font-size: var(--font-h1); color: var(--text-tertiary); }
.desc-input { width: 100%; min-height: 160rpx; font-size: var(--font-body); color: var(--text-primary); padding: var(--spacing-sm); border: 2rpx solid var(--border-color); border-radius: var(--radius-icon); box-sizing: border-box; }
.submit-wrap { padding: var(--spacing-md) 0; }
</style>
