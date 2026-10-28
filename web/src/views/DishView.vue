<script setup lang="ts">
/**
 * 菜品管理（设计稿 docs/ui/web/菜品管理.md；业务规则 docs/func/web/A3）。
 * likeCount 是只读派生列（点赞数 = COUNT(dish_rating.liked = 1)）。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import CardActionSheet from '@/components/CardActionSheet.vue'
import ListError from '@/components/ListError.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { useCardMenu } from '@/composables/useCardMenu'
import { fetchAllPages } from '@/composables/useOptions'
import { usePagedList } from '@/composables/usePagedList'
import { canteenApi, dishApi, stallApi, upload, type DishBody, type DishOut } from '@/api'

const route = useRoute()

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
  imageUrl: '',
  status: 'on',
  uploading: false,
})

const saving = ref(false)

/** 指标行点赞数文案（派生缓存列，只读） */
function likeText(row: DishOut): string {
  return `${row.likeCount} 人赞`
}

/** 筛选用的档口：随食堂联动，未选食堂时列全部 */
const filterStalls = reactive({ items: [] as typeof stalls.value })

async function loadCanteens() {
  canteens.value = await fetchAllPages((page, pageSize) => canteenApi.list(page, pageSize))
}

/** 选择器取全量档口：逐页拉取，避免 pageSize=50 冒充全量导致漏项 */
async function loadStalls(canteenId?: number) {
  const list = await fetchAllPages((page, pageSize) => stallApi.list({ canteenId, page, pageSize }))
  stalls.value = list as typeof stalls.value
  filterStalls.items = list as typeof stalls.value
}

/** 分页状态机：翻页 / 加载态 / 末页删空回退 / 筛选回第 1 页单点收口（见基线 §2.3「分页」） */
const { rows, total, page, loading, error, hasNext, load, go, reload } = usePagedList<DishOut>(
  (p) =>
    dishApi.list({
      stallId: filter.stallId,
      canteenId: filter.canteenId,
      status: filter.status || undefined,
      page: p,
    }),
)

function onCreate() {
  dialog.id = 0
  dialog.stallId = undefined
  dialog.name = ''
  dialog.imageUrl = ''
  dialog.status = 'on'
  dialog.visible = true
}

function onEdit(row: DishOut) {
  dialog.id = row.id
  dialog.stallId = row.stallId
  dialog.name = row.name
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
  const body: DishBody = {
    stallId: dialog.stallId,
    name: dialog.name.trim(),
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
    await ElMessageBox.confirm(
      '删除后不可恢复。若该菜已有学生纠错记录，删除会被拒绝（请先改用下架）。',
      '确认删除',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
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

/** 长按浮层（编辑 / 删除）：卡面无操作按钮 */
const cardMenu = useCardMenu<DishOut>({ onEdit, onRemove })

function onFilterCanteenChange() {
  filter.stallId = undefined
  loadStalls(filter.canteenId)
  reload()
}

onMounted(async () => {
  await Promise.all([loadCanteens(), loadStalls(), load()])

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
        @change="reload"
      >
        <option :value="undefined">全部档口</option>
        <option v-for="s in filterStalls.items" :key="s.id" :value="s.id">{{ s.name }}</option>
      </select>
      <select
        v-model="filter.status"
        class="filters__select"
        aria-label="按状态筛选"
        @change="reload"
      >
        <option value="">全部状态</option>
        <option value="on">在售</option>
        <option value="off">已下架</option>
      </select>
    </div>

    <div class="list-card-wrap">
      <div
        v-for="row in rows"
        :key="row.id"
        class="list-card"
        tabindex="0"
        aria-haspopup="menu"
        v-bind="cardMenu.pressAttrs(row, { title: row.name })"
      >
        <img
          v-if="row.imageUrl"
          v-zoomimg
          :src="row.imageUrl"
          class="list-card__figure"
          :alt="row.name"
          loading="lazy"
        />
        <span v-else class="list-card__figure list-card__figure--ph" aria-hidden="true">
          <AppIcon name="image-broken" :size="20" />
        </span>
        <div class="list-card__main">
          <div class="list-card__title">
            <span class="list-card__titleText">{{ row.name }}</span>
            <span class="tag" :class="row.status === 'on' ? 'tag--on' : 'tag--off'">
              {{ row.status === 'on' ? '在售' : '已下架' }}
            </span>
          </div>
          <div class="list-card__path">{{ row.canteenName }} · {{ row.stallName }}</div>
          <div class="list-card__stats">
            <span class="list-card__like">
              <AppIcon name="heart" :size="12" />
              {{ likeText(row) }}
            </span>
          </div>
        </div>
      </div>
      <div class="list-card" v-if="!rows.length && !loading">
        <span class="list-card__empty">暂无数据</span>
      </div>
    </div>

    <ListError v-if="error" :message="error" @retry="load" />

    <footer class="page__foot">
      <span class="page__total">共 {{ total }} 条</span>
      <PaginationBar :page="page" :has-next="hasNext" @prev="go(page - 1)" @next="go(page + 1)" />
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

        <label class="dialog__label" for="dish-image">菜品大图</label>
        <div class="photo">
          <img
            v-if="dialog.imageUrl"
            v-zoomimg
            :src="dialog.imageUrl"
            class="photo__thumb img--round"
            alt="菜品图"
          />
          <span v-else class="photo__empty">未上传</span>
          <div class="photo__ops">
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

    <CardActionSheet
      v-model="cardMenu.open"
      :title="cardMenu.title"
      :remove-disabled="cardMenu.removeDisabled"
      :remove-hint="cardMenu.removeHint"
      @edit="cardMenu.pickEdit"
      @remove="cardMenu.pickRemove"
    />
  </section>
</template>

<style scoped>
.photo {
  display: flex;
  align-items: center;
  gap: 12px;
}
/* 圆形裁切由 .img--round 提供（见 theme.css） */
.photo__thumb {
  width: 80px;
  height: 80px;
  border: 1px solid var(--el-border-color-lighter);
}
.photo__empty {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}
.photo__ops {
  display: flex;
  gap: 8px;
}
</style>
