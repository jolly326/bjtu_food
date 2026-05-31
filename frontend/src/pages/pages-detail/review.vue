<template>
  <view class="page review-page">
    <Header title="发表评价" showBack />

    <!-- 评分 -->
    <CardSection>
      <text class="section-label">评分</text>
      <view class="rating-panel">
        <Rating v-model="form.rating" :readonly="false" :show-text="true" />
      </view>
    </CardSection>

    <!-- 评论 -->
    <CardSection>
      <text class="section-label">评价内容</text>
      <textarea
        v-model="form.content"
        class="content-input"
        placeholder="分享你的用餐体验..."
        :maxlength="MAX_CONTENT_LENGTH"
      />
      <text class="char-count">{{ form.content.length }}/{{ MAX_CONTENT_LENGTH }}</text>
    </CardSection>

    <!-- 图片上传 -->
    <CardSection>
      <text class="section-label">图片（最多3张）</text>
      <view class="image-list">
        <view v-for="(img, idx) in form.images" :key="idx" class="image-item">
          <image :src="img" mode="aspectFill" class="preview-img" />
          <text class="remove-btn" @tap="removeImage(idx)">✕</text>
        </view>
        <view v-if="form.images.length < MAX_IMAGES" class="image-upload" @tap="selectImage">
          <text class="upload-icon">+</text>
        </view>
      </view>
    </CardSection>

    <!-- 提交按钮 -->
    <view style="padding: var(--spacing-lg);">
      <AppButton text="提交评价" type="gradient" :disabled="!canSubmit" @click="handleSubmit" />
    </view>
  </view>
</template>

<script setup lang="ts">
const MAX_CONTENT_LENGTH = 500
const MAX_IMAGES = 3

import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Header from '@/components/header.vue'
import CardSection from '@/components/CardSection.vue'
import AppButton from '@/components/AppButton.vue'
import Rating from '@/components/Rating.vue'
import { useDishStore } from '@/stores/dish'
import { uploadImage as uploadImageApi } from '@/api/upload'

const dishStore = useDishStore()
const dishId = ref(0)
const uploading = ref(false)
const form = reactive({
  rating: 5,
  content: '',
  images: [] as string[],
})

const canSubmit = computed(() => form.rating > 0 && form.content.trim().length > 0)

function removeImage(idx: number) {
  form.images.splice(idx, 1)
}

function selectImage() {
  uni.chooseImage({
    count: 3 - form.images.length,
    success: (res) => {
      form.images.push(...res.tempFilePaths.slice(0, 3 - form.images.length))
    },
  })
}

async function handleSubmit() {
  if (!canSubmit.value || uploading.value) return
  uploading.value = true

  try {
    // 将本地图片上传到服务器，获取可访问的 URL
    const uploadedUrls: string[] = []
    for (const localPath of form.images) {
      try {
        const url = await uploadImageApi(localPath)
        uploadedUrls.push(url)
      } catch {
        // 单张上传失败不影响整体提交
        console.warn('图片上传失败，跳过:', localPath)
      }
    }

    await dishStore.submitReview({
      dishId: dishId.value,
      rating: form.rating,
      content: form.content,
      images: uploadedUrls,
    })
    uni.showToast({ title: '评价成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1500)
  } catch (e: any) {
    uni.showToast({ title: e.message || '提交失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

onLoad((query) => {
  if (query?.dishId) dishId.value = Number(query.dishId)
})
</script>

<style scoped>
.review-page {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: var(--spacing-xl);
}
.section-label {
  font-size: var(--font-body);
  color: var(--text-primary);
  display: block;
  margin-bottom: var(--spacing-sm);
  font-weight: bold;
}
.rating-panel {
  display: flex;
  justify-content: center;
  padding: var(--spacing-md) 0;
}
.content-input {
  width: 100%;
  height: 200rpx;
  font-size: var(--font-body);
  line-height: 1.6;
  border: none;
  outline: none;
  background: var(--bg-page);
  border-radius: var(--radius-icon);
  padding: var(--spacing-sm);
  box-sizing: border-box;
}
.char-count {
  display: block;
  text-align: right;
  font-size: var(--font-tiny);
  color: var(--text-tertiary);
  margin-top: var(--spacing-xs);
}
.image-list {
  display: flex;
  gap: var(--spacing-sm);
  flex-wrap: wrap;
}
.image-item {
  position: relative;
  width: 180rpx;
  height: 180rpx;
  border-radius: var(--radius-icon);
  overflow: hidden;
}
.preview-img {
  width: 100%;
  height: 100%;
}
.remove-btn {
  position: absolute;
  top: 4rpx;
  right: 4rpx;
  width: 36rpx;
  height: 36rpx;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-tiny);
}
.image-upload {
  width: 180rpx;
  height: 180rpx;
  border: 4rpx dashed var(--border-color);
  border-radius: var(--radius-icon);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-page);
}
.upload-icon {
  font-size: var(--font-h1);
  color: var(--text-tertiary);
}

</style>
