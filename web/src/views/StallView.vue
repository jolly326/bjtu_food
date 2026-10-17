<script setup lang="ts">
/**
 * 档口管理（设计稿 docs/ui/web/档口管理.md；业务规则 docs/func/web/A2）。
 * 楼层受控字典（两端常量）；海报独立维护见「海报管理」（A4，/posters）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import { canteenApi, stallApi, type StallBody, type StallOut } from '@/api'

const FLOORS = ['负一层', '一层', '二层', '三层', '四层']

const rows = ref<StallOut[]>([])
const total = ref(0)
const page = ref(1)
const canteens = ref<{ id: number; name: string }[]>([])
const filter = reactive({ canteenId: undefined as number | undefined })
const loading = ref(false)

const dialog = reactive({
  visible: false,
  id: 0,
  canteenId: undefined as number | undefined,
  name: '',
  floor: '',
  windowNo: '',
})

async function loadCanteens() {
  const res = await canteenApi.list(1, 50)
  canteens.value = res.records
}

async function load() {
  loading.value = true
  try {
    const res = await stallApi.list({ canteenId: filter.canteenId, page: page.value })
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
  page.value = 1
  load()
}

function onCreate() {
  dialog.id = 0
  dialog.canteenId = undefined
  dialog.name = ''
  dialog.floor = ''
  dialog.windowNo = ''
  dialog.visible = true
}

function onEdit(row: StallOut) {
  dialog.id = row.id
  dialog.canteenId = row.canteenId
  dialog.name = row.name
  dialog.floor = row.floor ?? ''
  dialog.windowNo = row.windowNo ?? ''
  dialog.visible = true
}

async function onSave() {
  if (!dialog.canteenId) {
    ElMessage.warning('请选择所属食堂')
    return
  }
  if (!dialog.name.trim()) {
    ElMessage.warning('档口名称不能为空')
    return
  }
  if (dialog.floor && !FLOORS.includes(dialog.floor)) {
    ElMessage.warning('楼层取值不合法')
    return
  }

  const body: StallBody = {
    canteenId: dialog.canteenId,
    name: dialog.name.trim(),
    floor: dialog.floor || null,
    windowNo: dialog.windowNo.trim() || null,
  }

  try {
    if (dialog.id) await stallApi.update(dialog.id, body)
    else await stallApi.create(body)
    ElMessage.success('已保存')
    dialog.visible = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function onRemove(row: StallOut) {
  try {
    await ElMessageBox.confirm('删除后不可恢复。若该档口下仍有菜品，删除会被拒绝。', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await stallApi.remove(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  await loadCanteens()
  await load()
})
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">档口管理</h2>
      <button class="btn btn--primary" @click="onCreate">
        <AppIcon name="plus" :size="16" />新建
      </button>
    </header>

    <div class="filters">
      <select v-model="filter.canteenId" class="filters__select" @change="applyFilter">
        <option :value="undefined">全部食堂</option>
        <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <button class="btn btn--outline" @click="resetFilter">重置</button>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>名称</th>
          <th>所属食堂</th>
          <th class="table__muted">楼层</th>
          <th class="table__muted">窗口号</th>
          <th class="table__muted">海报数</th>
          <th class="table__muted">菜品数</th>
          <th class="table__muted">更新时间</th>
          <th class="table__ops">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.name }}</td>
          <td>{{ row.canteenName }}</td>
          <td class="table__muted">{{ row.floor ?? '—' }}</td>
          <td class="table__muted">{{ row.windowNo ?? '—' }}</td>
          <td class="table__muted">{{ row.posterCount }}</td>
          <td class="table__muted">{{ row.dishCount }}</td>
          <td class="table__muted">{{ row.updatedAt }}</td>
          <td class="table__ops">
            <button class="link" @click="onEdit(row)">编辑</button>
            <button class="link link--danger" @click="onRemove(row)">删除</button>
          </td>
        </tr>
        <tr v-if="!rows.length && !loading">
          <td colspan="8" class="table__empty">暂无数据</td>
        </tr>
      </tbody>
    </table>

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
        <h3 class="dialog__title">{{ dialog.id ? '编辑档口' : '新建档口' }}</h3>

        <label class="dialog__label">所属食堂</label>
        <select v-model="dialog.canteenId" class="dialog__input">
          <option :value="undefined">请选择</option>
          <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>

        <label class="dialog__label">名称</label>
        <input v-model="dialog.name" class="dialog__input" type="text" />

        <label class="dialog__label">楼层</label>
        <select v-model="dialog.floor" class="dialog__input">
          <option value="">不填</option>
          <option v-for="f in FLOORS" :key="f" :value="f">{{ f }}</option>
        </select>

        <label class="dialog__label">窗口号</label>
        <input v-model="dialog.windowNo" class="dialog__input" type="text" />

        <footer class="dialog__foot">
          <button class="btn" @click="dialog.visible = false">取消</button>
          <button class="btn btn--primary" @click="onSave">保存</button>
        </footer>
      </div>
    </div>
  </section>
</template>

<style scoped>
/* 与食堂管理页共用同一套「列表 + 弹窗」样式约定 */
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

.btn {
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font-size: 13px;
  cursor: pointer;
}
.btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.btn--primary {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary);
  color: #fff;
}

.table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.table th,
.table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  text-align: left;
}
.table th {
  color: var(--el-text-color-secondary);
  font-weight: 600;
}
.table__muted {
  color: var(--el-text-color-secondary);
}
.table__ops {
  text-align: right;
  white-space: nowrap;
}
.table__empty {
  padding: 40px 0;
  text-align: center;
  color: var(--el-text-color-placeholder);
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

.mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
}
.dialog {
  width: 520px;
  max-height: 86vh;
  overflow: auto;
  padding: 24px;
  border-radius: var(--el-border-radius-base);
  background: var(--el-bg-color);
}
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
</style>
