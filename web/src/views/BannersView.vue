<script setup lang="ts">
/**
 * A5 首页 Banner 管理（页面规格见 [列表页模板.md 的 A5 差异节](../../../docs/ui/web/列表页模板.md)）。
 *
 * <p><b>本页管理的是一个轮播组合</b>：库里就是**多条** Banner，每条可**单独启停**、可**拖拽排序**，
 * 因此「换组合」= 启停 + 调序，不需要也不存在「多套轮播」的概念（页内说明同此口径）。
 *
 * <p>要点：卡片网格 + **不分页不筛选**（启用 ≤6）；**拖拽排序**（提交**全量行**）；
 * 卡片只放基础信息（图 + 状态 + 顺序），**全部操作**（编辑 / 启停 / 删除）在**详情抽屉**内；
 * 启停为**显式传目标状态**（非 toggle）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { formatDateTime } from '@/utils/datetime'
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
import type { BannerAdminVO, BannerSaveReq, OnOffStatus } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import ImageUpload, { type ImageItem } from '@/components/ImageUpload.vue'
import ListState from '@/components/ListState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { resolveImageUrl } from '@/utils/image'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import { useRowAction } from '@/composables/useRowAction'

/** 启用上限（服务端硬校验；页头计数徽标 = 该上限的自证） */
const ENABLE_MAX = 6

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<BannerAdminVO>(() => listBanners())

const enabledCount = computed(() => items.value.filter((r) => r.status === 'on').length)

/* ==================== 详情 / 编辑抽屉 ==================== */
/** 抽屉两态：`view` = 详情（只读 + 操作）/ `edit` = 新建或编辑表单 */
type DrawerMode = 'view' | 'edit'

const drawerOpen = ref(false)
const mode = ref<DrawerMode>('view')
/** 查看态 / 编辑态的当前行（新建为 null） */
const current = ref<BannerAdminVO | null>(null)
const saving = ref(false)
/** 组件态 = `{ url }` 对象数组（ImageUpload 契约）；保存时取首图映射回 `imageUrl` */
const bannerImages = ref<ImageItem[]>([])
/** 进入编辑态时的快照：用于「未保存修改」二次确认（BaseDrawer 的 dirty） */
const snapshot = ref('')

const drawerTitle = computed(() => {
  if (mode.value === 'view') return 'Banner 详情'
  return current.value ? '编辑 Banner' : '新建 Banner'
})
const dirty = computed(
  () => mode.value === 'edit' && JSON.stringify(bannerImages.value) !== snapshot.value,
)

function fillImages(row: BannerAdminVO | null): void {
  // 编辑回显：契约出参 `imageUrl` 映射为组件对象数组（单张 = 本行的 Banner 图）
  bannerImages.value = row?.imageUrl ? [{ url: row.imageUrl }] : []
  snapshot.value = JSON.stringify(bannerImages.value)
}

function openDetail(row: BannerAdminVO): void {
  current.value = row
  fillImages(row)
  mode.value = 'view'
  drawerOpen.value = true
}

function openCreate(): void {
  current.value = null
  fillImages(null)
  mode.value = 'edit'
  drawerOpen.value = true
}

function startEdit(): void {
  fillImages(current.value)
  mode.value = 'edit'
}

/** 编辑态「取消」：有历史行则退回查看态，新建则关抽屉 */
function cancelEdit(): void {
  if (current.value) {
    fillImages(current.value)
    mode.value = 'view'
  } else {
    drawerOpen.value = false
  }
}

async function save(): Promise<void> {
  const imageUrl = bannerImages.value[0]?.url ?? ''
  if (!imageUrl) {
    ElMessage.warning('请上传 Banner 图')
    return
  }
  const req: BannerSaveReq = { imageUrl }
  saving.value = true
  try {
    if (current.value) {
      await updateBanner(current.value.id, req)
      ElMessage.success('已保存')
      await load()
      const refreshed = items.value.find((r) => r.id === current.value?.id)
      if (refreshed) {
        current.value = refreshed
        fillImages(refreshed)
        mode.value = 'view'
      }
    } else {
      await createBanner(req)
      ElMessage.success('已新建')
      await load()
      drawerOpen.value = false
    }
  } catch (e) {
    // 启用已达上限 6 张 → 后端原文（400）
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

/* ==================== 启停 / 删除 ==================== */
/** 行内 / 抽屉动作并发保护：提交中该行（或抽屉当前行）动作置灰（列表页模板 §1.3「并发保护」） */
const { isBusy, runRowAction } = useRowAction()

/** 刷新后按 id 回填抽屉数据（抽屉展示的不是该行时才不动，避免串数据） */
function syncCurrent(id: number): void {
  if (current.value?.id !== id) return
  current.value = items.value.find((r) => r.id === id) ?? current.value
}

function toggle(row: BannerAdminVO): Promise<void> {
  const next: OnOffStatus = row.status === 'on' ? 'off' : 'on'
  return runRowAction({
    id: row.id,
    action: () => updateBannerStatus(row.id, next),
    successMessage: next === 'on' ? '已启用' : '已停用',
    refresh: load,
    syncAfterRefresh: syncCurrent,
  })
}

function remove(row: BannerAdminVO): Promise<void> {
  return runRowAction({
    id: row.id,
    // 二次确认放进闭包：用户取消时 `confirmDelete` reject ⇒ 直接抛出、不提示、不刷新
    action: async () => {
      await confirmDelete('确认删除该 Banner？删除后首页轮播立即不再展示。', {
        title: '删除 Banner',
      })
      await deleteBanner(row.id)
    },
    successMessage: '已删除',
    failMessage: '删除失败',
    refresh: load,
    closeDrawer: () => {
      if (current.value?.id !== row.id) return
      drawerOpen.value = false
      current.value = null
    },
  })
}

/* ==================== 拖拽排序（提交全量行） ==================== */
const { onDragStart, onDrop } = useReorder(items, sortBanners, load)

/* ==================== 图片大图预览 ==================== */
const previewOpen = ref(false)

onMounted(() => load())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>首页 Banner</h2>
      <span class="tag">启用 {{ enabledCount }} / {{ ENABLE_MAX }}</span>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建 Banner</button>
    </div>

    <p class="page-note">
      本页管理的是「轮播组合」：库里就是多条 Banner，每条可单独启停、可拖拽排序 —— 换组合即调序 /
      启停，不需要也不存在「多套轮播」。启用中的条数上限为 {{ ENABLE_MAX }} 张。
    </p>

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
        <img :src="resolveImageUrl(row.imageUrl)" alt="" class="banner-img" />
        <div class="banner-meta">
          <div class="banner-row">
            <DragHandle @dragstart="onDragStart(index)" />
            <StatusTag :status="row.status" kind="onoff" />
            <span class="muted">顺序 {{ row.order }}</span>
          </div>
          <div class="banner-actions">
            <button class="link" type="button" :disabled="isBusy(row.id)" @click="openDetail(row)">
              详情
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情 / 编辑抽屉（同一抽屉两态） -->
    <BaseDrawer :title="drawerTitle" :open="drawerOpen" :dirty="dirty" @close="drawerOpen = false">
      <!-- ① 查看态 -->
      <template v-if="mode === 'view'">
        <div class="field">
          <label id="bn-image-label">Banner 图（点击看大图）</label>
          <div
            v-if="current?.imageUrl"
            class="detail-thumbs"
            role="group"
            aria-labelledby="bn-image-label"
          >
            <button
              class="thumb"
              type="button"
              aria-label="查看 Banner 大图"
              @click="previewOpen = true"
            >
              <img :src="resolveImageUrl(current.imageUrl)" alt="" />
            </button>
          </div>
          <div v-else class="muted">暂无图片（点「编辑」上传）</div>
        </div>

        <div class="detail-meta">
          <DetailMetaRow k="Banner ID" num>#{{ current?.id }}</DetailMetaRow>
          <div class="meta-row">
            <span class="meta-key">顺序</span>
            <span class="meta-val num">
              {{ current?.order }}
              <span class="muted">（升序；在卡片列表上拖拽调整）</span>
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-key">状态</span>
            <span class="meta-val"
              ><StatusTag :status="current?.status ?? 'off'" kind="onoff"
            /></span>
          </div>
          <DetailMetaRow k="创建时间">{{ formatDateTime(current?.createdAt) }}</DetailMetaRow>
          <DetailMetaRow k="更新时间">{{ formatDateTime(current?.updatedAt) }}</DetailMetaRow>
        </div>
      </template>

      <!-- ② 编辑态（新建 / 编辑共用） -->
      <template v-else>
        <div class="field">
          <label>Banner 图</label>
          <ImageUpload
            v-model="bannerImages"
            :max="1"
            :cover="false"
            ratio-hint="建议 16:10"
            :aria-label="'Banner 图'"
          />
          <div class="hint">单张、建议比例 16:10（避免轮播切换时块高抖动）；地址由服务端返回</div>
        </div>
      </template>

      <template #actions>
        <template v-if="mode === 'view'">
          <button class="link" type="button" :disabled="isBusy(current?.id)" @click="startEdit">
            编辑
          </button>
          <button
            class="link"
            type="button"
            :disabled="isBusy(current?.id)"
            @click="current && toggle(current)"
          >
            {{ current?.status === 'on' ? '停用' : '启用' }}
          </button>
          <button
            class="link danger"
            type="button"
            :disabled="isBusy(current?.id)"
            @click="current && remove(current)"
          >
            删除
          </button>
        </template>
        <template v-else>
          <button class="btn-secondary" type="button" @click="cancelEdit">取消</button>
          <button class="btn-primary" type="button" :disabled="saving" v-press @click="save">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </template>
      </template>
    </BaseDrawer>

    <!-- Banner 大图预览：挂载即打开 -->
    <ImagePreview
      v-if="previewOpen && current?.imageUrl"
      :images="[current.imageUrl]"
      :index="0"
      @close="previewOpen = false"
    />
  </div>
</template>

<style scoped>
.page-note {
  margin: 0 0 var(--space-4);
  color: var(--text-secondary);
  font-size: var(--font-sm);
}
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
  aspect-ratio: 16 / 10;
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
</style>
