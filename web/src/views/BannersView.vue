<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listBanners,
  createBanner,
  updateBanner,
  toggleBannerStatus,
  deleteBanner,
} from '@/api/banners'
import type { BannerAdminVO, BannerSaveReq } from '@/types/common'
import StatusTag from '@/components/StatusTag.vue'
import BaseModal from '@/components/BaseModal.vue'
import ImageUpload from '@/components/ImageUpload.vue'
import StateBox from '@/components/StateBox.vue'

const list = ref<BannerAdminVO[]>([])
const loading = ref(true)
const error = ref<string | null>(null)
const open = ref(false)
const editing = ref<BannerAdminVO | null>(null)
const form = ref<BannerSaveReq>({ imageUrl: '', sortOrder: 0 })
const bannerImages = ref<string[]>([])
const saving = ref(false)

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    list.value = await listBanners()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}
function openCreate(): void {
  editing.value = null
  bannerImages.value = []
  form.value = { imageUrl: '', sortOrder: 0 }
  open.value = true
}
function openEdit(r: BannerAdminVO): void {
  editing.value = r
  bannerImages.value = r.imageUrl ? [r.imageUrl] : []
  form.value = { imageUrl: r.imageUrl, sortOrder: r.sortOrder }
  open.value = true
}
async function save(): Promise<void> {
  const imageUrl = bannerImages.value[0] || ''
  if (!imageUrl) {
    ElMessage.warning('请上传 Banner 图片')
    return
  }
  const req: BannerSaveReq = { imageUrl, sortOrder: form.value.sortOrder }
  saving.value = true
  try {
    if (editing.value) await updateBanner(editing.value.id, req)
    else await createBanner(req)
    ElMessage.success('已保存')
    open.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}
async function toggle(r: BannerAdminVO): Promise<void> {
  try {
    await toggleBannerStatus(r.id)
    ElMessage.success('已更新')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}
async function remove(r: BannerAdminVO): Promise<void> {
  try {
    await ElMessageBox.confirm('确认删除该 Banner？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteBanner(r.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>首页 Banner 管理</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建 Banner</button>
    </div>
    <StateBox v-if="loading" status="loading" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="load" />
    <div v-else class="banner-grid">
      <div v-for="r in list" :key="r.id" class="card banner-card">
        <img :src="r.imageUrl" alt="" class="banner-img" />
        <div class="banner-meta">
          <div class="banner-row">
            <StatusTag :status="r.status" kind="onoff" />
            <span class="muted">排序 {{ r.sortOrder }}</span>
          </div>
          <div class="banner-actions">
            <button class="link" type="button" @click="openEdit(r)">编辑</button>
            <button class="link" type="button" @click="toggle(r)">
              {{ r.status === 'on' ? '停用' : '启用' }}
            </button>
            <button class="link danger" type="button" @click="remove(r)">删除</button>
          </div>
        </div>
      </div>
      <div v-if="!list.length" class="card empty-card"><StateBox status="empty" /></div>
    </div>

    <BaseModal :title="editing ? '编辑 Banner' : '新建 Banner'" :open="open" @close="open = false">
      <div class="field">
        <label>配图（建议 750×320）</label>
        <ImageUpload v-model="bannerImages" :max="1" ratio-hint="建议 750×320" />
      </div>
      <div class="field">
        <label>排序</label>
        <input class="form-input" type="number" v-model.number="form.sortOrder" />
      </div>
      <template #actions>
        <button class="btn-ghost" type="button" @click="open = false">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" v-press @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.banner-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-4);
}
.banner-card {
  overflow: hidden;
  padding: 0;
}
.banner-img {
  width: 100%;
  aspect-ratio: 750 / 320;
  object-fit: cover;
  display: block;
}
.banner-meta {
  padding: var(--space-3) var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}
.banner-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.banner-actions {
  display: flex;
  gap: var(--space-3);
}
.muted {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
.empty-card {
  padding: var(--space-8);
}
</style>
