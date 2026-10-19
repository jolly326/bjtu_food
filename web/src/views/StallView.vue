<script setup lang="ts">
/**
 * 档口管理（设计稿 docs/ui/web/档口管理.md；业务规则 docs/func/web/A2）。
 * 楼层受控字典（两端常量）；海报独立维护见「海报管理」（A4，/posters）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import CardActionSheet from '@/components/CardActionSheet.vue'
import { useCardMenu } from '@/composables/useCardMenu'
import { usePagedList } from '@/composables/usePagedList'
import { canteenApi, stallApi, type StallBody, type StallOut } from '@/api'

const FLOORS = ['负一层', '一层', '二层', '三层', '四层']

const canteens = ref<{ id: number; name: string }[]>([])
const filter = reactive({ canteenId: undefined as number | undefined })

const dialog = reactive({
  visible: false,
  id: 0,
  canteenId: undefined as number | undefined,
  name: '',
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
const { rows, total, page, loading, hasNext, load, go, reload } = usePagedList<StallOut>((p) =>
  stallApi.list({ canteenId: filter.canteenId, page: p }),
)

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

/** 长按浮层（编辑 / 删除）：卡面无操作按钮 */
const cardMenu = useCardMenu<StallOut>({ onEdit, onRemove })
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
        @change="reload"
      >
        <option :value="undefined">全部食堂</option>
        <option v-for="c in canteens" :key="c.id" :value="c.id">{{ c.name }}</option>
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
        <div class="list-card__main">
          <div class="list-card__title">
            <span class="list-card__titleText">{{ row.name }}</span>
          </div>
          <div class="list-card__path">{{ row.canteenName }}</div>
          <div class="list-card__stats">
            <span v-if="locText(row)" class="list-card__stat">{{ locText(row) }}</span>
            <span class="list-card__stat">{{ row.dishCount }} 道菜</span>
            <span class="list-card__stat">{{ row.posterCount }} 张海报</span>
          </div>
        </div>
      </div>
      <div class="list-card" v-if="!rows.length && !loading">
        <span class="list-card__empty">暂无数据</span>
      </div>
    </div>

    <footer class="page__foot">
      <span class="page__total">共 {{ total }} 条</span>
      <div class="pager">
        <button class="btn" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
        <span class="pager__cur">{{ page }}</span>
        <button class="btn" :disabled="!hasNext" @click="go(page + 1)">下一页</button>
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
