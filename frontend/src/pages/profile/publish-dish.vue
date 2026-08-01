<template>
  <view class="page publish-page">
    <Header :title="isEdit ? '编辑菜品' : '发布菜品'" showBack />

    <scroll-view class="scroll-wrap" scroll-y>
      <CardSection title="基本信息">
        <view class="field">
          <text class="field-label">菜品名称<text class="req">*</text></text>
          <input v-model="form.name" class="field-input" placeholder="请输入菜品名称" maxlength="30" />
        </view>
        <view class="field">
          <text class="field-label">价格（元）<text class="req">*</text></text>
          <input v-model="form.price" class="field-input" type="digit" placeholder="0.00" />
        </view>
      </CardSection>

      <CardSection title="所属食堂 / 档口">
        <picker :range="canteenNames" @change="onCanteenChange">
          <view class="picker-row">
            <text class="picker-label">食堂</text>
            <text class="picker-value">{{ form.canteen || '请选择食堂' }}</text>
            <text class="picker-arrow"><IconSvg name="arrow" :size="28" color="var(--text-tertiary)" /></text>
          </view>
        </picker>
        <picker :range="stallNames" :disabled="!form.canteen" @change="onStallChange">
          <view class="picker-row">
            <text class="picker-label">档口</text>
            <text class="picker-value">{{ form.stallName || '请选择档口' }}</text>
            <text class="picker-arrow"><IconSvg name="arrow" :size="28" color="var(--text-tertiary)" /></text>
          </view>
        </picker>
      </CardSection>

      <CardSection title="口味标签">
        <view class="tag-grid">
          <view
            v-for="tag in tagOptions"
            :key="tag"
            class="tag-chip"
            :class="{ active: form.tags.includes(tag) }"
            @tap="toggleTag(tag)"
          >{{ tag }}</view>
        </view>
      </CardSection>

      <CardSection title="菜品图片（≤9 张）">
        <view class="image-grid">
          <view v-for="(img, idx) in form.images" :key="idx" class="image-cell">
            <image class="image-thumb" :src="getImageUrl(img)" mode="aspectFill" />
            <view class="image-remove" @tap="removeImage(idx)"><IconSvg name="close" :size="24" color="var(--badge-dark-text)" /></view>
          </view>
          <view v-if="form.images.length < 9" class="image-cell image-add" @tap="selectImage">
            <text class="image-add-icon">+</text>
          </view>
        </view>
      </CardSection>

      <CardSection title="描述">
        <textarea v-model="form.description" class="desc-input" placeholder="介绍一下这道菜吧..." maxlength="500" />
        <text class="char-count">{{ form.description.length }}/500</text>
      </CardSection>

      <view class="submit-wrap">
        <AppButton :text="isEdit ? '保存并重新提交' : '发布菜品'" :loading="submitting" @click="handleSubmit" />
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
import IconSvg from '@/components/IconSvg.vue'
import { uploadImage } from '@/api/upload'
import { publishDish, updateMyDish } from '@/api/publish'
import { getCanteensWithStalls } from '@/api/canteen'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const editId = ref<number | null>(null)
const isEdit = computed(() => editId.value != null)
const submitting = ref(false)

const canteenTree = ref<any[]>([])
const canteenNames = computed(() => canteenTree.value.map(c => c.name))
const stallNames = ref<string[]>([])

const tagOptions = ['必吃推荐', '招牌菜', '辣味', '素食', '面食', '清真', '西餐', '甜品']

const form = reactive({
  name: '',
  price: '',
  canteen: '',
  stallName: '',
  tags: [] as string[],
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
  const name = canteenNames.value[e.detail.value]
  form.canteen = name
  form.stallName = ''
  const c = canteenTree.value.find(x => x.name === name)
  stallNames.value = (c?.stalls || []).map((s: any) => s.name)
}

function onStallChange(e: any) {
  form.stallName = stallNames.value[e.detail.value] || ''
}

function toggleTag(tag: string) {
  const i = form.tags.indexOf(tag)
  if (i >= 0) form.tags.splice(i, 1)
  else form.tags.push(tag)
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
  if (!form.name.trim()) { uni.showToast({ title: '请填写菜品名称', icon: 'none' }); return }
  const price = Number(form.price)
  if (!form.price || isNaN(price) || price <= 0) { uni.showToast({ title: '请填写有效价格', icon: 'none' }); return }
  if (!form.canteen || !form.stallName) { uni.showToast({ title: '请选择食堂与档口', icon: 'none' }); return }

  // 解析 stallId
  const c = canteenTree.value.find(x => x.name === form.canteen)
  const stall = (c?.stalls || []).find((s: any) => s.name === form.stallName)
  const stallId = Number(stall?.id || 0)
  if (!stallId) { uni.showToast({ title: '档口信息缺失', icon: 'none' }); return }

  submitting.value = true
  const payload = {
    stallId,
    name: form.name.trim(),
    price,
    description: form.description.trim() || undefined,
    images: form.images,
    tags: form.tags.join(','),
  }
  try {
    if (isEdit.value && editId.value != null) {
      await updateMyDish(editId.value, payload)
      uni.showToast({ title: '已重新提交审核', icon: 'success' })
    } else {
      await publishDish(payload)
      uni.showToast({ title: '发布成功，待审核', icon: 'success' })
    }
    setTimeout(() => uni.navigateBack(), 1200)
  } catch (e: any) {
    uni.showToast({ title: e?.message || '提交失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad((query: any) => {
  if (query?.id) editId.value = Number(query.id)
  loadCanteens()
})
</script>

<style scoped>
.publish-page { display: flex; flex-direction: column; height: 100vh; background: var(--bg-page); }
.scroll-wrap { flex: 1; overflow-y: auto; padding: var(--spacing-md); }
.field { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); }
.field:last-child { border-bottom: none; }
.field-label { font-size: var(--font-body); color: var(--text-primary); font-weight: 500; width: 160rpx; flex-shrink: 0; }
.req { color: var(--color-error); margin-left: 4rpx; }
.field-input { flex: 1; font-size: var(--font-body); color: var(--text-primary); min-width: 0; }
.picker-row { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-md) 0; border-bottom: 2rpx solid var(--border-color); }
.picker-row:last-child { border-bottom: none; }
.picker-label { font-size: var(--font-body); color: var(--text-primary); font-weight: 500; width: 160rpx; flex-shrink: 0; }
.picker-value { flex: 1; font-size: var(--font-body); color: var(--text-secondary); }
.picker-arrow { font-size: 28rpx; line-height: 1; opacity: 0.3; flex-shrink: 0; }
.tag-grid { display: flex; flex-wrap: wrap; gap: var(--spacing-md); padding: var(--spacing-xs) 0; }
.tag-chip { padding: var(--spacing-xs) var(--spacing-md); border-radius: 28rpx; background: var(--bg-page); font-size: var(--font-aux); color: var(--text-secondary); border: 2rpx solid var(--border-color); }
.tag-chip.active { background: var(--color-primary-bg); color: var(--color-primary); border-color: var(--color-primary); font-weight: 600; }
.image-grid { display: flex; flex-wrap: wrap; gap: var(--spacing-md); }
.image-cell { width: 180rpx; height: 180rpx; border-radius: var(--radius-icon); overflow: hidden; position: relative; background: var(--bg-page); }
.image-thumb { width: 100%; height: 100%; }
.image-remove { position: absolute; top: 4rpx; right: 4rpx; width: 36rpx; height: 36rpx; background: var(--badge-dark-bg); color: var(--badge-dark-text); border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: var(--font-tiny); }
.image-add { border: 4rpx dashed var(--border-color); display: flex; align-items: center; justify-content: center; background: var(--bg-page); }
.image-add-icon { font-size: var(--font-h1); color: var(--text-tertiary); }
.desc-input { width: 100%; min-height: 160rpx; font-size: var(--font-body); color: var(--text-primary); padding: var(--spacing-sm); border: 2rpx solid var(--border-color); border-radius: var(--radius-icon); box-sizing: border-box; }
.char-count { display: block; text-align: right; font-size: var(--font-tiny); color: var(--text-tertiary); margin-top: var(--spacing-xs); }
.submit-wrap { padding: var(--spacing-md) 0; }
</style>
