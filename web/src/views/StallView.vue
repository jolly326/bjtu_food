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

const saving = ref(false)

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

  saving.value = true
  try {
    if (dialog.id) await stallApi.update(dialog.id, body)
    else await stallApi.create(body)
    ElMessage.success('已保存')
    dialog.visible = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
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
      <select
        v-model="filter.canteenId"
        class="filters__select"
        aria-label="按食堂筛选"
        @change="applyFilter"
      >
        <option :value="undefined">全部食堂</option>
        <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
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
          <div class="list-card__title">
            <span class="list-card__titleText">{{ row.name }}</span>
          </div>
          <div class="list-card__meta">
            <span class="list-card__metaText">所属食堂：{{ row.canteenName }}</span>
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
        <h3 class="dialog__title">{{ dialog.id ? '编辑档口' : '新建档口' }}</h3>

        <label class="dialog__label" for="stall-canteen">所属食堂</label>
        <select id="stall-canteen" v-model="dialog.canteenId" class="dialog__input">
          <option :value="undefined">请选择</option>
          <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>

        <label class="dialog__label" for="stall-name">名称</label>
        <input id="stall-name" v-model="dialog.name" class="dialog__input" type="text" />

        <label class="dialog__label" for="stall-floor">楼层</label>
        <select id="stall-floor" v-model="dialog.floor" class="dialog__input">
          <option value="">不填</option>
          <option v-for="f in FLOORS" :key="f" :value="f">{{ f }}</option>
        </select>

        <label class="dialog__label" for="stall-window">窗口号</label>
        <input id="stall-window" v-model="dialog.windowNo" class="dialog__input" type="text" />

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

  color: var(--el-text-color-secondary);
}
  text-align: right;
  white-space: nowrap;
}
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


/* 弹窗窄屏近全宽，避免在手机上超出屏幕 */
@media (max-width: 640px) {
  .dialog {
    width: min(520px, 92vw);
    margin: 0 4px;
  }
}
</style>
`n