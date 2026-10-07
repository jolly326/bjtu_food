<script setup lang="ts">
/**
 * A1 食堂管理（页面规格见 [列表页模板.md 的 A1 差异节](../../../docs/ui/web/列表页模板.md)）。
 *
 * <p>要点：**不分页**（量级十数条，列表按 `sortOrder` 升序 → `updatedAt` 降序）；
 * 表格**只放基础检索列**（名称 / 档口数 / 更新时间）—— 图片、地点、描述、排序位与
 * **全部操作**（编辑 / 删除）都在**详情抽屉**内（同一抽屉两态：查看 ⇄ 编辑）；
 * 删除受阻（**其下仍有档口 → `400` 原文透出**）。
 *
 * <p>排序位无拖动入口（本页不是 `useReorder` 菜单）：它作为**普通可编辑字段**落在详情表单内，
 * 与 [schema/canteen.md](../../../docs/schema/canteen.md) 的「改序直接维护本列」同口径。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { formatDateTime } from '@/utils/datetime'
import { resolveImageUrl } from '@/utils/image'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import { fail } from '@/utils/error'
import { createCanteen, deleteCanteen, listCanteens, updateCanteen } from '@/api/canteens'
import type { CanteenAdminVO, CanteenSaveReq } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import ImageUpload, { type ImageItem } from '@/components/ImageUpload.vue'
import ListState from '@/components/ListState.vue'
import { useRowAction } from '@/composables/useRowAction'

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<CanteenAdminVO>(() => listCanteens())

/** 抽屉两态：`view` = 详情（只读 + 操作）/ `edit` = 新建或编辑表单 */
type DrawerMode = 'view' | 'edit'

const drawerOpen = ref(false)
const mode = ref<DrawerMode>('view')
/** 查看态的当前行（新建 / 编辑态为 null = 新建） */
const current = ref<CanteenAdminVO | null>(null)
const saving = ref(false)

/** 表单态（`sortOrder` 用字符串承接空输入 = 不下发该字段 ⇒ 保持原值） */
const form = ref({
  name: '',
  location: '',
  description: '',
  sortOrder: '',
  images: [] as ImageItem[],
})
/** 进入编辑态时的快照：用于「未保存修改」二次确认（BaseDrawer 的 dirty） */
const snapshot = ref('')

const drawerTitle = computed(() => {
  if (mode.value === 'view') return '食堂详情'
  return current.value ? '编辑食堂' : '新建食堂'
})
const dirty = computed(() => mode.value === 'edit' && JSON.stringify(form.value) !== snapshot.value)

function fillForm(row: CanteenAdminVO | null): void {
  form.value = {
    name: row?.name ?? '',
    location: row?.location ?? '',
    description: row?.description ?? '',
    sortOrder: row == null ? '' : String(row.sortOrder),
    images: (row?.images ?? []).map((url) => ({ url })),
  }
  snapshot.value = JSON.stringify(form.value)
}

function openDetail(row: CanteenAdminVO): void {
  current.value = row
  fillForm(row)
  mode.value = 'view'
  drawerOpen.value = true
}

function openCreate(): void {
  current.value = null
  fillForm(null)
  mode.value = 'edit'
  drawerOpen.value = true
}

function startEdit(): void {
  fillForm(current.value)
  mode.value = 'edit'
}

/** 编辑态「取消」：有历史行则退回查看态，新建则关抽屉 */
function cancelEdit(): void {
  if (current.value) {
    fillForm(current.value)
    mode.value = 'view'
  } else {
    drawerOpen.value = false
  }
}

async function save(): Promise<void> {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入食堂名称')
    return
  }
  const req: CanteenSaveReq = {
    name: form.value.name.trim(),
    location: form.value.location,
    description: form.value.description,
    images: form.value.images.map((img) => img.url),
  }
  // `sortOrder` 留空 = 不下发 ⇒ 服务端保持原值（新建时落库默认 0）
  if (form.value.sortOrder.trim() !== '') {
    const order = Number(form.value.sortOrder)
    if (!Number.isInteger(order)) {
      ElMessage.warning('排序位须为整数')
      return
    }
    req.sortOrder = order
  }
  saving.value = true
  try {
    if (current.value) {
      await updateCanteen(current.value.id, req)
      ElMessage.success('已保存')
      await load()
      const refreshed = items.value.find((r) => r.id === current.value?.id)
      if (refreshed) {
        current.value = refreshed
        fillForm(refreshed)
        mode.value = 'view'
      }
    } else {
      await createCanteen(req)
      ElMessage.success('已新建')
      await load()
      drawerOpen.value = false
    }
  } catch (e) {
    // 重名 / 空 / 超长 / 图片过长 → 后端原文（400）
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

/** 行内 / 抽屉动作并发保护：提交中该行（或抽屉当前行）动作置灰（列表页模板 §1.3「并发保护」） */
const { isBusy, runRowAction } = useRowAction()

function remove(row: CanteenAdminVO): Promise<void> {
  const stallHint =
    row.stallCount > 0 ? `该食堂下有 ${row.stallCount} 个档口，删除前请先处理。` : ''
  return runRowAction({
    id: row.id,
    action: async () => {
      await confirmDelete(`确认删除食堂「${row.name}」？${stallHint}`, { title: '删除食堂' })
      await deleteCanteen(row.id)
    },
    successMessage: '已删除',
    failMessage: '删除失败',
    refresh: load,
    // 行已不存在：抽屉若正开着这条，一并关闭（不留空壳）
    closeDrawer: () => {
      if (current.value?.id !== row.id) return
      drawerOpen.value = false
      current.value = null
    },
  })
}

/* ===== 图片大图预览（详情抽屉与图片格共用入口） ===== */
const previewOpen = ref(false)
const previewImages = ref<string[]>([])
const previewIndex = ref(0)

function openPreview(images: string[], index: number): void {
  if (!images || images.length === 0) return
  previewImages.value = images
  previewIndex.value = index
  previewOpen.value = true
}

onMounted(() => load())
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>食堂管理</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建食堂</button>
    </div>

    <!-- 六态：① 加载 ⑥ 会话失效 ② 错误 ③ 空 ④ 有数据 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无食堂"
      @retry="load"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>食堂</th>
            <th>档口数</th>
            <th>更新时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.name }}</td>
            <td class="num">{{ row.stallCount }}</td>
            <td class="muted">{{ formatDateTime(row.updatedAt) }}</td>
            <td class="actions">
              <button
                class="link"
                type="button"
                :disabled="isBusy(row.id)"
                @click="openDetail(row)"
              >
                详情
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <p class="foot-note">
        「食堂名称」是快速检索列；图片、地点、描述、排序位与编辑 / 删除入口都在行内「详情」内。
      </p>
    </div>

    <!-- 详情 / 编辑抽屉（同一抽屉两态） -->
    <BaseDrawer :title="drawerTitle" :open="drawerOpen" :dirty="dirty" @close="drawerOpen = false">
      <!-- ① 查看态 -->
      <template v-if="mode === 'view'">
        <div class="field">
          <label id="ct-images-label">食堂图片 · {{ current?.images.length ?? 0 }} 张</label>
          <div
            v-if="current?.images.length"
            class="detail-thumbs"
            role="group"
            aria-labelledby="ct-images-label"
          >
            <button
              v-for="(img, i) in current.images"
              :key="i"
              class="thumb"
              type="button"
              :aria-label="`查看第 ${i + 1} 张食堂图片`"
              @click="openPreview(current.images, i)"
            >
              <img :src="resolveImageUrl(img)" alt="" />
            </button>
          </div>
          <div v-else class="muted">暂无图片（点「编辑」上传）</div>
        </div>

        <div class="detail-meta">
          <DetailMetaRow k="食堂 ID" num>#{{ current?.id }}</DetailMetaRow>
          <DetailMetaRow k="名称">{{ current?.name }}</DetailMetaRow>
          <DetailMetaRow k="地点">{{ current?.location || '—' }}</DetailMetaRow>
          <DetailMetaRow k="描述">{{ current?.description || '—' }}</DetailMetaRow>
          <div class="meta-row">
            <span class="meta-key">排序位</span>
            <span class="meta-val num">
              {{ current?.sortOrder }}
              <span class="muted">（升序，越小越靠前）</span>
            </span>
          </div>
          <DetailMetaRow k="档口数" num>{{ current?.stallCount }}</DetailMetaRow>
          <DetailMetaRow k="更新时间">{{ formatDateTime(current?.updatedAt) }}</DetailMetaRow>
        </div>
      </template>

      <!-- ② 编辑态（新建 / 编辑共用） -->
      <template v-else>
        <div class="field">
          <label for="canteen-name">食堂名称</label>
          <input
            id="canteen-name"
            class="form-input"
            v-model="form.name"
            placeholder="如 学一食堂"
            @keyup.enter="save"
          />
        </div>
        <div class="field">
          <label for="canteen-location">地点</label>
          <input
            id="canteen-location"
            class="form-input"
            v-model="form.location"
            maxlength="128"
            placeholder="如 学苑路 3 号"
          />
        </div>
        <div class="field">
          <label for="canteen-description">描述</label>
          <textarea
            id="canteen-description"
            class="form-textarea"
            v-model="form.description"
            rows="3"
            maxlength="512"
          />
        </div>
        <div class="field">
          <label for="canteen-sort">排序位</label>
          <input
            id="canteen-sort"
            class="form-input"
            type="number"
            step="1"
            v-model="form.sortOrder"
            placeholder="升序，越小越靠前"
          />
          <div class="hint">留空 = 保持原值；列表按「排序位升序 → 更新时间降序」排列</div>
        </div>
        <div class="field">
          <label>食堂图片（有序，首图作封面，≤5 张）</label>
          <ImageUpload
            v-model="form.images"
            :max="5"
            ratio-hint="建议 4:3"
            :aria-label="'食堂图片'"
          />
        </div>
      </template>

      <template #actions>
        <template v-if="mode === 'view'">
          <button class="link" type="button" :disabled="isBusy(current?.id)" @click="startEdit">
            编辑
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

    <!-- 图片大图预览：挂载即打开 -->
    <ImagePreview
      v-if="previewOpen"
      :images="previewImages"
      :index="previewIndex"
      @close="previewOpen = false"
    />
  </div>
</template>
