<script setup lang="ts">
/**
 * A5 首页 Banner 管理（页面规格见 [列表页模板.md 的 A5 差异节](../../../docs/ui/web/列表页模板.md)）。
 *
 * <p>要点：卡片网格、**不分页不筛选**（启用 ≤6）；**拖拽排序**（提交**全量行**）；
 * 新增 / 编辑 = **抽屉**（**含图片上传 ⇒ 按基线 §1.10 判据走抽屉**，不用弹窗）；
 * 启停为**显式传目标状态**（非 toggle）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { fail } from '@/utils/error'
import { useReorder } from '@/composables/useReorder'
import {
  createBanner,
  deleteBanner,
  listBanners,
  sortBanners,
  updateBanner,
  updateBannerStatus,
} from '@/api/banners'
import type { BannerAdminVO, BannerSaveReq } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ImageUpload, { type ImageItem } from '@/components/ImageUpload.vue'
import ListState from '@/components/ListState.vue'
import StatusTag from '@/components/StatusTag.vue'

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<BannerAdminVO>(() => listBanners())

/* ==================== 新增 / 编辑（抽屉） ==================== */
const open = ref(false)
const editing = ref<BannerAdminVO | null>(null)
const saving = ref(false)
/** 组件态 = `{ url }` 对象数组（ImageUpload 契约）；保存时取首图映射回 `imageUrl` */
const bannerImages = ref<ImageItem[]>([])

const title = computed(() => (editing.value ? '编辑 Banner' : '新建 Banner'))

function openCreate(): void {
  editing.value = null
  bannerImages.value = []
  open.value = true
}

function openEdit(row: BannerAdminVO): void {
  editing.value = row
  // 编辑回显：契约出参 `imageUrl` 映射为组件对象数组（单张，即封面）
  bannerImages.value = row.imageUrl ? [{ url: row.imageUrl }] : []
  open.value = true
}

async function save(): Promise<void> {
  const imageUrl = bannerImages.value[0]?.url ?? ''
  if (!imageUrl) {
    ElMessage.warning('请上传 Banner 图片')
    return
  }
  const req: BannerSaveReq = { imageUrl }
  saving.value = true
  try {
    if (editing.value) await updateBanner(editing.value.id, req)
    else await createBanner(req)
    ElMessage.success(editing.value ? '已保存' : '已新建')
    open.value = false
    await load()
  } catch (e) {
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

/* ==================== 启停 / 删除 ==================== */
/** 行内动作并发保护：提交中该行按钮 `:disabled`（列表页模板 §1.3「并发保护」） */
const busyId = ref<number | null>(null)

async function toggle(row: BannerAdminVO): Promise<void> {
  const next = row.status === 'on' ? 'off' : 'on'
  busyId.value = row.id
  try {
    await updateBannerStatus(row.id, next)
    ElMessage.success(next === 'on' ? '已启用' : '已停用')
    await load()
  } catch (e) {
    fail(e)
  } finally {
    busyId.value = null
  }
}

async function remove(row: BannerAdminVO): Promise<void> {
  try {
    await confirmDelete('确认删除该 Banner？删除后首页轮播立即不再展示。', { title: '删除 Banner' })
  } catch {
    return
  }
  busyId.value = row.id
  try {
    await deleteBanner(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    fail(e, '删除失败')
  } finally {
    busyId.value = null
  }
}

/* ==================== 拖拽排序（提交全量行） ==================== */
const { onDragStart, onDrop } = useReorder(items, sortBanners, load)

onMounted(() => load())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>首页 Banner</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建 Banner</button>
    </div>

    <!-- 四态：加载 / 会话失效 / 错误 / 空 / 有数据 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无 Banner"
      @retry="load"
    />
    <div v-if="hasData" class="banner-grid">
      <div
        v-for="(row, index) in items"
        :key="row.id"
        class="card banner-card"
        @dragover.prevent
        @drop="onDrop(index)"
      >
        <img :src="row.imageUrl" alt="" class="banner-img" />
        <div class="banner-meta">
          <div class="banner-row">
            <DragHandle @dragstart="onDragStart(index)" />
            <StatusTag :status="row.status" kind="onoff" />
            <span class="muted">排序 {{ row.order }}</span>
          </div>
          <div class="banner-actions">
            <button class="link" type="button" :disabled="busyId === row.id" @click="openEdit(row)">
              编辑
            </button>
            <button class="link" type="button" :disabled="busyId === row.id" @click="toggle(row)">
              {{ row.status === 'on' ? '停用' : '启用' }}
            </button>
            <button
              class="link danger"
              type="button"
              :disabled="busyId === row.id"
              @click="remove(row)"
            >
              删除
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 含图片 ⇒ 抽屉（基线 §1.10 载体判据） -->
    <BaseDrawer :title="title" :open="open" @close="open = false">
      <div class="field">
        <label>Banner 图片</label>
        <ImageUpload
          v-model="bannerImages"
          :max="1"
          ratio-hint="建议 750×320"
          :aria-label="'Banner 图片'"
        />
        <div class="hint">单张、建议比例 750:320；上传成功后地址由服务端返回</div>
      </div>
      <template #actions>
        <button class="btn-secondary" type="button" @click="open = false">取消</button>
        <button class="btn-primary" type="button" :disabled="saving" v-press @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </BaseDrawer>
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
  cursor: grab;
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
.hint {
  margin-top: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-xs);
}
</style>
