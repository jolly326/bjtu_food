<script setup lang="ts">
/**
 * 海报管理（设计稿 docs/ui/web/海报管理.md；业务规则 docs/func/web/A4）。
 * 一档口多张；列表序 = sort 升序（与学生端橱窗同序，所见即学生所见）。
 * 图片必填（POST /admin/images 上传）；sort 留空 = 新建排最后 / 编辑保持原值。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import { canteenApi, posterApi, stallApi, upload, type PosterBody, type PosterOut } from '@/api'

const rows = ref<PosterOut[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

const canteens = ref<{ id: number; name: string }[]>([])
const stalls = ref<{ id: number; name: string; canteenId: number; canteenName: string }[]>([])
const filter = reactive({
  canteenId: undefined as number | undefined,
  stallId: undefined as number | undefined,
  status: '',
})

const dialog = reactive({
  visible: false,
  id: 0,
  stallId: undefined as number | undefined,
  imageUrl: '',
  sortText: '',
  status: 'on',
  uploading: false,
})

const saving = ref(false)

/** 筛选用的档口：随食堂联动，未选食堂时列全部 */
const filterStalls = reactive({ items: [] as typeof stalls.value })

async function loadCanteens() {
  const res = await canteenApi.list(1, 50)
  canteens.value = res.records
}

/** 选择器一次性取全量档口（接口 pageSize 上限 50） */
async function loadStalls(canteenId?: number) {
  const res = await stallApi.list({ canteenId, pageSize: 50 })
  stalls.value = res.records as typeof stalls.value
  filterStalls.items = res.records as typeof stalls.value
}

async function load() {
  loading.value = true
  try {
    const res = await posterApi.list({
      stallId: filter.stallId,
      canteenId: filter.canteenId,
      status: filter.status || undefined,
      page: page.value,
    })
    rows.value = res.records
    total.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function prevPage() {
  page.value -= 1
  await load()
}

async function nextPage() {
  page.value += 1
  await load()
}

function applyFilter() {
  page.value = 1
  load()
}

function resetFilter() {
  filter.canteenId = undefined
  filter.stallId = undefined
  filter.status = ''
  loadStalls()
  page.value = 1
  load()
}

function onCreate() {
  dialog.id = 0
  dialog.stallId = undefined
  dialog.imageUrl = ''
  dialog.sortText = ''
  dialog.status = 'on'
  dialog.visible = true
}

function onEdit(row: PosterOut) {
  dialog.id = row.id
  dialog.stallId = row.stallId
  dialog.imageUrl = row.imageUrl
  dialog.sortText = String(row.sort)
  dialog.status = row.status
  dialog.visible = true
}

async function onPickImage(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  dialog.uploading = true
  try {
    dialog.imageUrl = await upload(file)
    ElMessage.success('海报已上传')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    dialog.uploading = false
  }
}

async function onSave() {
  if (!dialog.stallId) {
    ElMessage.warning('请选择所属档口')
    return
  }
  if (!dialog.imageUrl.trim()) {
    ElMessage.warning('海报图不能为空')
    return
  }
  const raw = dialog.sortText.trim()
  let sort: number | undefined
  if (raw !== '') {
    const n = Number(raw)
    if (!Number.isInteger(n)) {
      ElMessage.warning('排序必须是整数')
      return
    }
    sort = n
  }

  const body: PosterBody = {
    stallId: dialog.stallId,
    imageUrl: dialog.imageUrl,
    sort,
    status: dialog.status,
  }

  saving.value = true
  try {
    if (dialog.id) await posterApi.update(dialog.id, body)
    else await posterApi.create(body)
    ElMessage.success('已保存')
    dialog.visible = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function onRemove(row: PosterOut) {
  try {
    await ElMessageBox.confirm('删除后不可恢复，学生端将不再展示该海报。', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await posterApi.remove(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function onFilterCanteenChange() {
  filter.stallId = undefined
  loadStalls(filter.canteenId)
  page.value = 1
  load()
}

onMounted(async () => {
  await loadCanteens()
  await loadStalls()
  await load()
})
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">海报管理</h2>
      <button class="btn btn--primary" @click="onCreate">
        <AppIcon name="plus" :size="16" />新建
      </button>
    </header>

    <div class="filters">
      <select
        v-model="filter.canteenId"
        class="filters__select"
        aria-label="按食堂筛选"
        @change="onFilterCanteenChange"
      >
        <option :value="undefined">全部食堂</option>
        <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <select
        v-model="filter.stallId"
        class="filters__select"
        aria-label="按档口筛选"
        @change="applyFilter"
      >
        <option :value="undefined">全部档口</option>
        <option v-for="s in filterStalls.items" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
      <select
        v-model="filter.status"
        class="filters__select"
        aria-label="按状态筛选"
        @change="applyFilter"
      >
        <option value="">全部状态</option>
        <option value="on">上橱窗</option>
        <option value="off">不展示</option>
      </select>
      <button class="btn btn--outline" @click="resetFilter">重置</button>
    </div>

    <div class="list-card-wrap">
      <div
        class="list-card"
        v-for="row in rows"
        :key="row.id"
      >
        <div class="list-card__main">
          <img
            v-if="row.imageUrl"
            :src="row.imageUrl"
            class="img--round"
            :style="{ width: '80px', height: '80px' }"
            alt="海报"
          />
          <span class="list-card__muted">—</span>
          <div class="list-card__title">
            <span class="list-card__titleText">{{ row.stallName }}</span>
            <span
              class="tag"
              :class="row.status === 'on' ? 'tag--on' : 'tag--off'"
            >
              {{ row.status === 'on' ? '上橱窗' : '不展示' }}
            </span>
          </div>
        </div>
        <div class="list-card__actions">
          <button class="link" @click="onEdit(row)">编辑</button>
          <button class="link link--danger" @click="onRemove(row)">删除</button>
        </div>
      </div>
      <div class="list-card" v-if="!rows.length && !loading">
        <span class="list-card__empty">暂无数据</span>
      </div>
    </div>

    <footer class="page__foot">
      <span class="page__total">共 {{ total }} 条</span>
      <div class="pager">
        <button class="btn" :disabled="page <= 1" @click="prevPage">上一页</button>
        <span class="pager__cur">{{ page }}</span>
        <button class="btn" :disabled="page * 20 >= total" @click="nextPage">下一页</button>
      </div>
    </footer>

    <div v-if="dialog.visible" class="mask" @click.self="dialog.visible = false">
      <div class="dialog">
        <h3 class="dialog__title">{{ dialog.id ? '编辑海报' : '新建海报' }}</h3>

        <label class="dialog__label" for="poster-stall">所属档口</label>
        <select id="poster-stall" v-model="dialog.stallId" class="dialog__input">
          <option :value="undefined">请选择</option>
          <option v-for="s in stalls" :key="s.id" :value="s.id">
            {{ s.canteenName }} · {{ s.name }}
          </option>
        </select>

        <label class="dialog__label" for="poster-image">
          竖版海报（3:4，需画进档口名称 / 所属食堂 / 楼层窗口）
        </label>
        <div class="poster">
          <img v-if="dialog.imageUrl" :src="dialog.imageUrl" class="poster__thumb" alt="海报" />
          <span v-else class="poster__empty">未上传</span>
          <div class="poster__ops">
            <label class="btn btn--sm">
              {{ dialog.uploading ? '上传中…' : '上传海报' }}
              <input id="poster-image" type="file" accept="image/*" hidden @change="onPickImage" />
            </label>
          </div>
        </div>

        <label class="dialog__label" for="poster-sort">
          排序（值小靠前；留空 = 新建排最后 / 编辑保持原值）
        </label>
        <input
          id="poster-sort"
          v-model="dialog.sortText"
          class="dialog__input"
          type="number"
          step="1"
        />

        <label class="dialog__label" for="poster-status">状态</label>
        <select id="poster-status" v-model="dialog.status" class="dialog__input">
          <option value="on">上橱窗</option>
          <option value="off">不展示</option>
        </select>

        <footer class="dialog__foot">
          <button class="btn btn--outline" @click="dialog.visible = false">取消</button>
          <button class="btn btn--primary" :disabled="saving" @click="onSave">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </footer>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* 与菜品管理页共用同一套「列表 + 弹窗」样式约定 */
.page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page__title {
  margin: 0;
  font-size: 18px;
  color: var(--el-text-color-primary);
}
.page__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page__total {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
.pager {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pager__cur {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.filters {
  display: flex;
  gap: 8px;
}
.filters__select {
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
}

  color: var(--el-text-color-secondary);
}
  text-align: right;
}
  text-align: right;
  white-space: nowrap;
}
  padding: 40px 0;
  text-align: center;
  color: var(--el-text-color-placeholder);
}
/* 海报列缩略图：高 40px、宽 30px（3:4 竖版裁切），见 docs/ui/web/海报管理.md §3 */
  width: 30px;
  height: 40px;
  border-radius: 4px;
  object-fit: cover;
  vertical-align: middle;
}

.tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 12px;
}
.tag--on {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}
.tag--off {
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}

.link {
  border: none;
  background: none;
  color: var(--el-color-primary);
  font-size: 13px;
  cursor: pointer;
  padding: 0 6px;
}
.link--danger {
  color: var(--el-color-danger);
}

/* 遮罩底色由 theme.css 的 #app .mask 单点提供（暖色半透明） */
.dialog__title {
  margin: 0 0 20px;
  font-size: 16px;
  color: var(--el-text-color-primary);
}
.dialog__label {
  display: block;
  margin: 14px 0 6px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.dialog__input {
  width: 100%;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  outline: none;
  box-sizing: border-box;
}
.dialog__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 24px;
}

.poster {
  display: flex;
  align-items: center;
  gap: 12px;
}
.poster__thumb {
  width: 60px;
  height: 80px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  object-fit: cover;
}
.poster__empty {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}
.poster__ops {
  display: flex;
  gap: 8px;
}


/* 弹窗窄屏近全宽，避免在手机上超出屏幕 */
@media (max-width: 640px) {
  .dialog {
    width: min(520px, 92vw);
    margin: 0 4px;
  }
}
</style>
`n