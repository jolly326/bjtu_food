<script setup lang="ts">
/**
 * A2 档口管理（页面规格见 [列表页模板.md 的 A2 差异节](../../../docs/ui/web/列表页模板.md)）。
 *
 * <p>要点：**不分页**；可选 `canteenId` 筛选；列表按「食堂 → 档口名」；
 * 表格**只放基础检索列**（档口名 / 所属食堂 / 菜品数 / 更新时间）—— 楼层、窗口号、图片、地点、描述、
 * 排序位与**全部操作**（编辑 / 删除）都在**详情抽屉**内（同一抽屉两态：查看 ⇄ 编辑）；
 * 楼层为**下拉（楼层字典，值即汉字）**；删除受阻（其下仍有菜品 → `400` 原文透出）。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { formatDateTime } from '@/utils/datetime'
import { resolveImageUrl } from '@/utils/image'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import { fail } from '@/utils/error'
import { createStall, deleteStall, listStalls, updateStall } from '@/api/stalls'
import { listCanteens } from '@/api/canteens'
import type { CanteenAdminVO, StallAdminVO, StallSaveReq } from '@/types/common'
import { useSimpleList } from '@/composables/useSimpleList'
import { FLOOR_OPTIONS } from '@/utils/floorDict'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import ImageUpload, { type ImageItem } from '@/components/ImageUpload.vue'
import ListState from '@/components/ListState.vue'
import { useRowAction } from '@/composables/useRowAction'

/** 筛选：所属食堂（不传 = 全部） */
const filterCanteenId = ref<number | ''>('')
const canteens = ref<CanteenAdminVO[]>([])

const { items, firstLoading, isEmpty, hasData, error, sessionInvalid, load } =
  useSimpleList<StallAdminVO>(() =>
    listStalls(filterCanteenId.value === '' ? undefined : Number(filterCanteenId.value)),
  )

/** 抽屉两态：`view` = 详情（只读 + 操作）/ `edit` = 新建或编辑表单 */
type DrawerMode = 'view' | 'edit'

const drawerOpen = ref(false)
const mode = ref<DrawerMode>('view')
/** 查看态的当前行（新建 / 编辑态为 null = 新建） */
const current = ref<StallAdminVO | null>(null)
const saving = ref(false)

/** 表单态（`sortOrder` 用字符串承接空输入 = 不下发该字段 ⇒ 保持原值） */
const form = ref({
  canteenId: 0,
  name: '',
  floor: '',
  windowNo: '',
  location: '',
  description: '',
  sortOrder: '',
  images: [] as ImageItem[],
})
/** 进入编辑态时的快照：用于「未保存修改」二次确认（BaseDrawer 的 dirty） */
const snapshot = ref('')

const drawerTitle = computed(() => {
  if (mode.value === 'view') return '档口详情'
  return current.value ? '编辑档口' : '新建档口'
})
const dirty = computed(() => mode.value === 'edit' && JSON.stringify(form.value) !== snapshot.value)

function fillForm(row: StallAdminVO | null): void {
  form.value = {
    canteenId: row?.canteenId ?? canteens.value[0]?.id ?? 0,
    name: row?.name ?? '',
    floor: row?.floor ?? '',
    windowNo: row?.windowNo ?? '',
    location: row?.location ?? '',
    description: row?.description ?? '',
    sortOrder: row == null ? '' : String(row.sortOrder),
    images: (row?.images ?? []).map((url) => ({ url })),
  }
  snapshot.value = JSON.stringify(form.value)
}

function openDetail(row: StallAdminVO): void {
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
  if (!form.value.canteenId) {
    ElMessage.warning('请选择所属食堂')
    return
  }
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入档口名称')
    return
  }
  // 「楼层」未填 ⇒ 不下发该字段（契约：`floor` 空白串 → 400；缺省 = 保持原值 / 新建不设置）
  // 「窗口号」保留 `''` 语义（契约：PUT 传 `''` = 清空）
  const req: StallSaveReq = {
    canteenId: form.value.canteenId,
    name: form.value.name.trim(),
    windowNo: form.value.windowNo,
    location: form.value.location,
    description: form.value.description,
    images: form.value.images.map((img) => img.url),
  }
  if (form.value.floor) req.floor = form.value.floor
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
      await updateStall(current.value.id, req)
      ElMessage.success('已保存')
      await load()
      const refreshed = items.value.find((r) => r.id === current.value?.id)
      if (refreshed) {
        current.value = refreshed
        fillForm(refreshed)
        mode.value = 'view'
      }
    } else {
      await createStall(req)
      ElMessage.success('已新建')
      await load()
      drawerOpen.value = false
    }
  } catch (e) {
    // 同食堂下重名 / 楼层不在字典 / 食堂不存在 / 图片超限 → 后端原文（400）
    fail(e, '保存失败')
  } finally {
    saving.value = false
  }
}

/** 行内 / 抽屉动作并发保护：提交中该行（或抽屉当前行）动作置灰（列表页模板 §1.3「并发保护」） */
const { isBusy, runRowAction } = useRowAction()

function remove(row: StallAdminVO): Promise<void> {
  const dishHint = row.dishCount > 0 ? `该档口下有 ${row.dishCount} 个菜品，删除前请先处理。` : ''
  return runRowAction({
    id: row.id,
    action: async () => {
      await confirmDelete(`确认删除档口「${row.name}」？${dishHint}`, { title: '删除档口' })
      await deleteStall(row.id)
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

onMounted(async () => {
  // 依赖数据（食堂下拉）与列表并行：依赖失败不阻塞列表
  listCanteens()
    .then((rows) => (canteens.value = rows))
    .catch(() => undefined)
  await load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <h2>档口管理</h2>
      <button class="btn-primary" type="button" v-press @click="openCreate">新建档口</button>
    </div>

    <div class="card filters">
      <select class="form-input" v-model="filterCanteenId" @change="load">
        <option value="">全部食堂</option>
        <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <button class="btn-primary" type="button" v-press @click="load">查询</button>
      <button
        class="btn-secondary"
        type="button"
        @click="
          () => {
            filterCanteenId = ''
            load()
          }
        "
      >
        重置
      </button>
    </div>

    <!-- 六态：① 加载 ⑥ 会话失效 ② 错误 ③ 空 ④ 有数据 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无档口"
      @retry="load"
    />
    <div v-if="hasData" class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>档口</th>
            <th>所属食堂</th>
            <th>菜品数</th>
            <th>更新时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.name }}</td>
            <td>{{ row.canteenName || '—' }}</td>
            <td class="num">{{ row.dishCount }}</td>
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
        「档口名 + 所属食堂」是快速检索列；楼层、窗口号、图片、地点、描述、排序位与编辑 /
        删除入口都在行内「详情」内。
      </p>
    </div>

    <!-- 详情 / 编辑抽屉（同一抽屉两态） -->
    <BaseDrawer :title="drawerTitle" :open="drawerOpen" :dirty="dirty" @close="drawerOpen = false">
      <!-- ① 查看态 -->
      <template v-if="mode === 'view'">
        <div class="field">
          <label id="st-images-label">档口图片 · {{ current?.images.length ?? 0 }} 张</label>
          <div
            v-if="current?.images.length"
            class="detail-thumbs"
            role="group"
            aria-labelledby="st-images-label"
          >
            <button
              v-for="(img, i) in current.images"
              :key="i"
              class="thumb"
              type="button"
              :aria-label="`查看第 ${i + 1} 张档口图片`"
              @click="openPreview(current.images, i)"
            >
              <img :src="resolveImageUrl(img)" alt="" />
            </button>
          </div>
          <div v-else class="muted">暂无图片（点「编辑」上传）</div>
        </div>

        <div class="detail-meta">
          <DetailMetaRow k="档口 ID" num>#{{ current?.id }}</DetailMetaRow>
          <DetailMetaRow k="档口名">{{ current?.name }}</DetailMetaRow>
          <div class="meta-row">
            <span class="meta-key">所属食堂</span>
            <span class="meta-val">
              {{ current?.canteenName || '—' }}
              <span class="muted">#{{ current?.canteenId }}</span>
            </span>
          </div>
          <div class="meta-row">
            <span class="meta-key">楼层</span>
            <span class="meta-val">
              {{ current?.floor || '—' }}
              <span class="muted">（改动影响该档口下所有菜品的位置展示）</span>
            </span>
          </div>
          <DetailMetaRow k="窗口号">{{ current?.windowNo || '—' }}</DetailMetaRow>
          <DetailMetaRow k="地点">{{ current?.location || '—' }}</DetailMetaRow>
          <DetailMetaRow k="描述">{{ current?.description || '—' }}</DetailMetaRow>
          <div class="meta-row">
            <span class="meta-key">排序位</span>
            <span class="meta-val num">
              {{ current?.sortOrder }}
              <span class="muted">（纠错候选档口列表按它升序）</span>
            </span>
          </div>
          <DetailMetaRow k="菜品数" num>{{ current?.dishCount }}</DetailMetaRow>
          <DetailMetaRow k="平均评分" num>{{
            current?.avgRating?.toFixed(2) ?? '0.00'
          }}</DetailMetaRow>
          <DetailMetaRow k="更新时间">{{ formatDateTime(current?.updatedAt) }}</DetailMetaRow>
        </div>
      </template>

      <!-- ② 编辑态（新建 / 编辑共用） -->
      <template v-else>
        <div class="field">
          <label for="stall-canteen">所属食堂</label>
          <select id="stall-canteen" class="form-input" v-model.number="form.canteenId">
            <option :value="0" disabled>请选择</option>
            <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </div>
        <div class="field">
          <label for="stall-name">档口名称</label>
          <input id="stall-name" class="form-input" v-model="form.name" placeholder="如 嘉园奶茶" />
        </div>
        <div class="field">
          <label for="stall-floor">楼层</label>
          <select id="stall-floor" class="form-input" v-model="form.floor">
            <option value="">未填写</option>
            <option v-for="f in FLOOR_OPTIONS" :key="f" :value="f">{{ f }}</option>
          </select>
          <div class="hint">楼层归属档口，改动会影响该档口下所有菜品的位置展示</div>
        </div>
        <div class="field">
          <label for="stall-window">窗口号</label>
          <input
            id="stall-window"
            class="form-input"
            v-model="form.windowNo"
            maxlength="32"
            placeholder="如 3号窗口"
          />
        </div>
        <div class="field">
          <label for="stall-location">地点</label>
          <input
            id="stall-location"
            class="form-input"
            v-model="form.location"
            maxlength="128"
            placeholder="如 二层东侧"
          />
        </div>
        <div class="field">
          <label for="stall-description">描述</label>
          <textarea
            id="stall-description"
            class="form-textarea"
            v-model="form.description"
            rows="3"
            maxlength="512"
          />
        </div>
        <div class="field">
          <label for="stall-sort">排序位</label>
          <input
            id="stall-sort"
            class="form-input"
            type="number"
            step="1"
            v-model="form.sortOrder"
            placeholder="升序，越小越靠前"
          />
          <div class="hint">
            留空 = 保持原值；纠错候选档口列表按它升序（本列表按「食堂 → 档口名」）
          </div>
        </div>
        <div class="field">
          <label>档口图片（有序，首图作封面，≤5 张）</label>
          <ImageUpload
            v-model="form.images"
            :max="5"
            ratio-hint="建议 4:3"
            :aria-label="'档口图片'"
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

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-4);
  margin-bottom: var(--space-4);
}
.filters .form-input {
  width: 160px;
}
</style>
