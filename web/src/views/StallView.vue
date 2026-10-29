<script setup lang="ts">
/**
 * 档口管理（设计稿 docs/ui/web/档口管理.md；业务规则 docs/func/web/A2）。
 * 楼层受控字典（两端常量，与后端校验清单逐字一致）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import ListError from '@/components/ListError.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { PAGE_SIZE, usePagedList } from '@/composables/usePagedList'
import { canteenApi, stallApi, type StallBody, type StallOut } from '@/api'

const FLOORS = ['负一层', '一层', '二层', '三层', '四层']

const canteens = ref<{ id: number; name: string }[]>([])
const filter = reactive({ canteenId: undefined as number | undefined })

const dialog = reactive({
  visible: false,
  id: 0,
  canteenId: undefined as number | undefined,
  name: '',
  activate: true,
  floor: '',
  windowNo: '',
})

const saving = ref(false)

/** 指标行位置文案：楼层 · 窗口号（均可空，全空则整段不显示） */
function locText(row: StallOut): string {
  return [row.floor, row.windowNo ? `窗口 ${row.windowNo}` : ''].filter(Boolean).join(' · ')
}

async function loadCanteens() {
  const res = await canteenApi.list(1, 50)
  canteens.value = res.records
}

/** 分页状态机：翻页 / 加载态 / 末页删空回退 / 筛选回第 1 页单点收口（见基线 §2.3「分页」） */
const { rows, total, page, loading, error, hasNext, load, go, reload } = usePagedList<StallOut>(
  (p) => stallApi.list({ canteenId: filter.canteenId, page: p }),
)

function onCreate() {
  dialog.id = 0
  dialog.canteenId = undefined
  dialog.name = ''
  dialog.activate = true
  dialog.floor = ''
  dialog.windowNo = ''
  dialog.visible = true
}

function onEdit(row: StallOut) {
  dialog.id = row.id
  dialog.canteenId = row.canteenId
  dialog.name = row.name
  dialog.activate = row.activate
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
    activate: dialog.activate,
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

/** 删除入口在编辑界面内（卡面无操作按钮、无卡片浮层） */
async function onRemove() {
  if (!dialog.id) return
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
    await stallApi.remove(dialog.id)
    ElMessage.success('已删除')
    dialog.visible = false
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
        <AppIcon name="add" :size="16" />新建
      </button>
    </header>

    <div class="filters">
      <el-select
        v-model="filter.canteenId"
        class="filters__select"
        placeholder="全部食堂"
        clearable
        @change="reload"
      >
        <el-option v-for="c in canteens" :key="c.id" :value="c.id" :label="c.name" />
      </el-select>
    </div>

    <div class="list-card-wrap">
      <div
        v-for="row in rows"
        :key="row.id"
        class="list-card"
        tabindex="0"
        role="button"
        :aria-label="`编辑 ${row.name}`"
        @click="onEdit(row)"
        @keydown.enter.prevent="onEdit(row)"
        @keydown.space.prevent="onEdit(row)"
      >
        <div class="list-card__main">
          <div class="list-card__title">
            <span class="list-card__titleText">{{ row.name }}</span>
            <span class="tag" :class="row.activate ? 'tag--on' : 'tag--off'">
              {{ row.activate ? '正常' : '已停用' }}
            </span>
          </div>
          <div class="list-card__path">{{ row.canteenName }}</div>
          <div class="list-card__stats">
            <span v-if="locText(row)" class="list-card__stat">{{ locText(row) }}</span>
            <span class="list-card__stat">{{ row.dishCount }} 道菜</span>
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
      <PaginationBar
        :page="page"
        :has-next="hasNext"
        :total-pages="Math.ceil(total / PAGE_SIZE)"
        @prev="go(page - 1)"
        @next="go(page + 1)"
        @go="go"
      />
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

        <label class="dialog__label" for="stall-activate">状态</label>
        <select id="stall-activate" v-model="dialog.activate" class="dialog__input">
          <option :value="true">正常</option>
          <option :value="false">已停用</option>
        </select>

        <footer class="dialog__foot">
          <button v-if="dialog.id" class="btn btn--danger" @click="onRemove">删除</button>
          <span class="dialog__spacer" />
          <button class="btn btn--outline" @click="dialog.visible = false">取消</button>
          <button class="btn btn--primary" :disabled="saving" @click="onSave">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </footer>
      </div>
    </div>
  </section>
</template>
