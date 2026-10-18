<script setup lang="ts">
/**
 * 菜品管理（设计稿 docs/ui/web/菜品管理.md；业务规则 docs/func/web/A3）。
 * 价格以「分」存取、界面按「元」展示（转换只在 utils/money）。
 * avgReaction / reactionCount 是只读派生列。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import { canteenApi, dishApi, stallApi, upload, type DishBody, type DishOut } from '@/api'
import { fenToYuan, yuanToFen } from '@/utils/money'

const route = useRoute()

const rows = ref<DishOut[]>([])
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
  name: '',
  priceYuan: '',
  imageUrl: '',
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
    const res = await dishApi.list({
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
  dialog.name = ''
  dialog.priceYuan = ''
  dialog.imageUrl = ''
  dialog.status = 'on'
  dialog.visible = true
}

function onEdit(row: DishOut) {
  dialog.id = row.id
  dialog.stallId = row.stallId
  dialog.name = row.name
  dialog.priceYuan = fenToYuan(row.price)
  dialog.imageUrl = row.imageUrl ?? ''
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
    ElMessage.success('图片已上传')
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
  if (!dialog.name.trim()) {
    ElMessage.warning('菜品名称不能为空')
    return
  }
  const price = yuanToFen(dialog.priceYuan)
  if (!Number.isFinite(price)) {
    ElMessage.warning('价格不合法')
    return
  }

  const body: DishBody = {
    stallId: dialog.stallId,
    name: dialog.name.trim(),
    price,
    imageUrl: dialog.imageUrl || null,
    status: dialog.status,
  }

  saving.value = true
  try {
    if (dialog.id) await dishApi.update(dialog.id, body)
    else await dishApi.create(body)
    ElMessage.success('已保存')
    dialog.visible = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function onRemove(row: DishOut) {
  try {
    await ElMessageBox.confirm('删除后不可恢复。该菜的饮食记录与纠错记录将一并删除。', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await dishApi.remove(row.id)
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

  // 从「纠错处理」点「去改菜品」直达：自动打开该菜的编辑弹窗
  const dishId = Number(route.query.dishId)
  if (dishId) {
    const row = rows.value.find((r) => r.id === dishId)
    if (row) onEdit(row)
  }
})
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">菜品管理</h2>
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
        <option value="on">在售</option>
        <option value="off">已下架</option>
      </select>
      <button class="btn btn--outline" @click="resetFilter">重置</button>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>名称</th>
          <th>所属档口</th>
          <th class="table__num">价格</th>
          <th>状态</th>
          <th class="table__muted table__num">评价分</th>
          <th class="table__muted table__num">评分条数</th>
          <th class="table__muted">更新时间</th>
          <th class="table__ops">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.name }}</td>
          <td>{{ row.stallName }}</td>
          <td class="table__num">¥{{ fenToYuan(row.price) }}</td>
          <td>
            <span class="tag" :class="row.status === 'on' ? 'tag--on' : 'tag--off'">
              {{ row.status === 'on' ? '在售' : '已下架' }}
            </span>
          </td>
          <td class="table__muted table__num">{{ row.avgReaction ?? '暂无评分' }}</td>
          <td class="table__muted table__num">{{ row.reactionCount }}</td>
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
        <h3 class="dialog__title">{{ dialog.id ? '编辑菜品' : '新建菜品' }}</h3>

        <label class="dialog__label" for="dish-stall">所属档口</label>
        <select id="dish-stall" v-model="dialog.stallId" class="dialog__input">
          <option :value="undefined">请选择</option>
          <option v-for="s in stalls" :key="s.id" :value="s.id">
            {{ s.canteenName }} · {{ s.name }}
          </option>
        </select>

        <label class="dialog__label" for="dish-name">名称</label>
        <input id="dish-name" v-model="dialog.name" class="dialog__input" type="text" />

        <label class="dialog__label" for="dish-price">价格（元）</label>
        <input
          id="dish-price"
          v-model="dialog.priceYuan"
          class="dialog__input"
          type="text"
          placeholder="12.00"
        />

        <label class="dialog__label" for="dish-image">菜品大图</label>
        <div class="poster">
          <img
            v-if="dialog.imageUrl"
            :src="dialog.imageUrl"
            class="poster__thumb img--round"
            alt="菜品图"
          />
          <span v-else class="poster__empty">未上传</span>
          <div class="poster__ops">
            <label class="btn btn--sm">
              {{ dialog.uploading ? '上传中…' : '上传图片' }}
              <input id="dish-image" type="file" accept="image/*" hidden @change="onPickImage" />
            </label>
            <button v-if="dialog.imageUrl" class="btn btn--sm" @click="dialog.imageUrl = ''">
              移除
            </button>
          </div>
        </div>

        <label class="dialog__label" for="dish-status">状态</label>
        <select id="dish-status" v-model="dialog.status" class="dialog__input">
          <option value="on">在售</option>
          <option value="off">已下架</option>
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

.table__muted {
  color: var(--el-text-color-secondary);
}
.table__num {
  text-align: right;
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
/* 圆形裁切由 .img--round 提供（见 theme.css） */
.poster__thumb {
  width: 80px;
  height: 80px;
  border: 1px solid var(--el-border-color-lighter);
}
.poster__empty {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}
.poster__ops {
  display: flex;
  gap: 8px;
}
</style>
