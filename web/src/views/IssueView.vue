<script setup lang="ts">
/**
 * 纠错处理（设计稿 docs/ui/web/纠错处理.md；业务规则 docs/func/web/B1）。
 *
 * 页内两段：① **纠错待办** —— 默认只查未处理；「去改菜品」在本页就地打开编辑弹窗（不跨菜单跳转）。
 * ② **纠错选项** —— 错误类型字典（新建 / 改名 / 删除，被引用禁删）。
 *
 * 🔴 卡面无操作按钮：去改菜品 / 标记已处理 / 撤销 一律走点击后的操作浮层（与其他页同构）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import CardActionSheet from '@/components/CardActionSheet.vue'
import DishEditDialog from '@/components/DishEditDialog.vue'
import ListError from '@/components/ListError.vue'
import PaginationBar from '@/components/PaginationBar.vue'
import { fetchAllPages } from '@/composables/useOptions'
import { PAGE_SIZE, usePagedList } from '@/composables/usePagedList'
import { useCardActions, type CardAction } from '@/composables/useCardMenu'
import {
  dishApi,
  issueApi,
  optionApi,
  stallApi,
  type DishOut,
  type IssueOut,
  type OptionOut,
} from '@/api'
import type { StallOut } from '@/api/types'
import { formatDateTime } from '@/utils/datetime'

/** 页内分段：待办 / 选项字典 */
const tab = ref<'issues' | 'options'>('issues')

const dishes = ref<DishOut[]>([])
const stalls = ref<StallOut[]>([])
const filter = reactive({
  handled: false as boolean | undefined,
  dishId: undefined as number | undefined,
})

const totalPages = computed(() => Math.ceil(total.value / PAGE_SIZE))

async function loadDishes() {
  dishes.value = await fetchAllPages((page, pageSize) => dishApi.list({ page, pageSize }))
}

async function loadStalls() {
  stalls.value = (await fetchAllPages((page, pageSize) =>
    stallApi.list({ page, pageSize }),
  )) as StallOut[]
}

/** 分页状态机：翻页 / 加载态 / 末页删空回退 / 筛选回第 1 页单点收口（见基线 §2.3「分页」） */
const { rows, total, page, loading, error, hasNext, load, go, reload } = usePagedList<IssueOut>(
  (p) =>
    issueApi.list({
      handled: filter.handled,
      dishId: filter.dishId,
      page: p,
    }),
)

/* ===== 就地改菜（不跨菜单跳转）===== */

const editing = ref<DishOut | null>(null)
const editVisible = ref(false)

/** 一键直达：在本页打开该菜的编辑弹窗（改完保存后仍需手动「标记已处理」） */
function onFix(row: IssueOut) {
  editing.value = dishes.value.find((d) => d.id === row.dishId) ?? null
  editVisible.value = true
}

async function onHandle(row: IssueOut) {
  try {
    await issueApi.setHandled(row.id, true)
    ElMessage.success('已标记')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function onUndo(row: IssueOut) {
  try {
    await issueApi.setHandled(row.id, false)
    ElMessage.success('已撤销')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

/** 该行的处置动作：未处理 ⇒ 去改菜品 / 标记已处理；已处理 ⇒ 撤销 */
function actionsOf(row: IssueOut): CardAction[] {
  return row.handledAt == null
    ? [
        { key: 'fix', label: '去改菜品' },
        { key: 'handle', label: '标记已处理' },
      ]
    : [{ key: 'undo', label: '撤销处理' }]
}

/** 点击浮层：动作按 key 分发到既有处理器 */
const cardMenu = useCardActions<IssueOut>((row, key) => {
  if (key === 'fix') onFix(row)
  else if (key === 'handle') void onHandle(row)
  else if (key === 'undo') void onUndo(row)
})

/* ===== 纠错选项（错误类型字典）===== */

const options = ref<OptionOut[]>([])
const optionsLoading = ref(false)
const optionDialog = reactive({ visible: false, id: 0, name: '', refCount: 0 })
const optionSaving = ref(false)

async function loadOptions() {
  optionsLoading.value = true
  try {
    options.value = await optionApi.list()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    optionsLoading.value = false
  }
}

function onOptionCreate() {
  optionDialog.id = 0
  optionDialog.name = ''
  optionDialog.refCount = 0
  optionDialog.visible = true
}

function onOptionEdit(row: OptionOut) {
  optionDialog.id = row.id
  optionDialog.name = row.name
  optionDialog.refCount = row.refCount
  optionDialog.visible = true
}

async function onOptionSave() {
  if (!optionDialog.name.trim()) {
    ElMessage.warning('选项文案不能为空')
    return
  }
  optionSaving.value = true
  try {
    if (optionDialog.id) await optionApi.update(optionDialog.id, optionDialog.name.trim())
    else await optionApi.create(optionDialog.name.trim())
    ElMessage.success('已保存')
    optionDialog.visible = false
    await loadOptions()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    optionSaving.value = false
  }
}

/** 删除入口在编辑界面内（被引用 refCount > 0 时禁删） */
async function onOptionRemove() {
  if (!optionDialog.id || optionDialog.refCount > 0) return
  try {
    await ElMessageBox.confirm(`删除后学生端将不再出现「${optionDialog.name}」。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await optionApi.remove(optionDialog.id)
    ElMessage.success('已删除')
    optionDialog.visible = false
    await loadOptions()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(async () => {
  await Promise.all([loadDishes(), loadStalls(), load(), loadOptions()])
})
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">纠错处理</h2>
      <div class="page__actions">
        <div class="seg" role="tablist" aria-label="纠错处理分段">
          <button
            class="seg__item"
            :class="{ 'seg__item--on': tab === 'issues' }"
            role="tab"
            :aria-selected="tab === 'issues'"
            @click="tab = 'issues'"
          >
            纠错待办
          </button>
          <button
            class="seg__item"
            :class="{ 'seg__item--on': tab === 'options' }"
            role="tab"
            :aria-selected="tab === 'options'"
            @click="tab = 'options'"
          >
            纠错选项
          </button>
        </div>
        <button v-if="tab === 'options'" class="btn btn--primary" @click="onOptionCreate">
          <AppIcon name="add" :size="16" />新建选项
        </button>
      </div>
    </header>

    <!-- ① 纠错待办 -->
    <template v-if="tab === 'issues'">
      <div class="filters">
        <el-select
          v-model="filter.handled"
          class="filters__select"
          placeholder="未处理"
          @change="reload"
        >
          <el-option label="未处理" :value="false" />
          <el-option label="已处理" :value="true" />
          <el-option label="全部" :value="undefined" />
        </el-select>
        <el-select
          v-model="filter.dishId"
          class="filters__select"
          placeholder="全部菜品"
          filterable
          clearable
          @change="reload"
        >
          <el-option v-for="d in dishes" :key="d.id" :value="d.id" :label="d.name" />
        </el-select>
      </div>

      <div class="list-card-wrap">
        <div
          class="list-card"
          :class="{ 'list-card--dim': row.handledAt != null }"
          v-for="row in rows"
          :key="row.id"
          tabindex="0"
          aria-haspopup="menu"
          v-bind="cardMenu.openAttrs(row, { title: row.dishName, actions: actionsOf(row) })"
        >
          <div class="list-card__main">
            <div class="list-card__title">
              <span class="list-card__titleText">{{ row.dishName }}</span>
              <span class="tag tag--off">{{ row.optionName }}</span>
              <span v-if="row.dishFeedbackCount > 1" class="tag tag--off">
                待办 {{ row.dishFeedbackCount }} 条
              </span>
            </div>
            <div class="list-card__path">{{ row.canteenName }} · {{ row.stallName }}</div>
            <div class="list-card__stats">
              <span class="list-card__stat">提交于 {{ formatDateTime(row.createdAt) }}</span>
              <span v-if="row.handledAt" class="list-card__stat">
                处理于 {{ formatDateTime(row.handledAt) }}
              </span>
            </div>
          </div>
        </div>
        <div class="list-card" v-if="!rows.length && !loading">
          <span class="list-card__empty">暂无待处理的纠错</span>
        </div>
      </div>

      <ListError v-if="error" :message="error" @retry="load" />

      <footer class="page__foot">
        <span class="page__total">共 {{ total }} 条</span>
        <PaginationBar
          :page="page"
          :has-next="hasNext"
          :total-pages="totalPages"
          @prev="go(page - 1)"
          @next="go(page + 1)"
          @go="go"
        />
      </footer>
    </template>

    <!-- ② 纠错选项（错误类型字典） -->
    <template v-else>
      <div class="list-card-wrap">
        <div
          v-for="row in options"
          :key="row.id"
          class="list-card"
          tabindex="0"
          role="button"
          :aria-label="`编辑 ${row.name}`"
          @click="onOptionEdit(row)"
          @keydown.enter.prevent="onOptionEdit(row)"
          @keydown.space.prevent="onOptionEdit(row)"
        >
          <div class="list-card__main">
            <h3 class="list-card__title">
              <span class="list-card__titleText">{{ row.name }}</span>
            </h3>
            <div class="list-card__stats">
              <span class="list-card__stat">被 {{ row.refCount }} 条纠错引用</span>
            </div>
          </div>
        </div>
        <div class="list-card" v-if="!options.length && !optionsLoading">
          <span class="list-card__empty">暂无选项</span>
        </div>
      </div>
    </template>

    <!-- 选项编辑弹窗 -->
    <div v-if="optionDialog.visible" class="mask" @click.self="optionDialog.visible = false">
      <div class="dialog">
        <h3 class="dialog__title">{{ optionDialog.id ? '编辑选项' : '新建选项' }}</h3>
        <label class="dialog__label" for="option-name">选项文案</label>
        <input id="option-name" v-model="optionDialog.name" class="dialog__input" type="text" />
        <p class="dialog__hint">改名后全站生效（含历史记录的中文翻译）。</p>
        <p v-if="optionDialog.id && optionDialog.refCount > 0" class="dialog__hint">
          已被 {{ optionDialog.refCount }} 条纠错引用，无法删除。
        </p>
        <footer class="dialog__foot">
          <button
            v-if="optionDialog.id"
            class="btn btn--danger"
            :disabled="optionDialog.refCount > 0"
            @click="onOptionRemove"
          >
            删除
          </button>
          <span class="dialog__spacer" />
          <button class="btn btn--outline" @click="optionDialog.visible = false">取消</button>
          <button class="btn btn--primary" :disabled="optionSaving" @click="onOptionSave">
            {{ optionSaving ? '保存中…' : '保存' }}
          </button>
        </footer>
      </div>
    </div>

    <!-- 卡片点击浮层：去改菜品 / 标记已处理 / 撤销（与主数据页同一组件） -->
    <CardActionSheet
      v-model="cardMenu.open"
      :title="cardMenu.title"
      :actions="cardMenu.actions"
      @pick="cardMenu.pick"
    />

    <!-- 就地改菜：与菜品管理共用编辑弹窗（保存后仍需手动标记已处理） -->
    <DishEditDialog
      v-model="editVisible"
      :dish="editing"
      :stalls="stalls"
      @saved="
        () => {
          void load()
          void loadDishes()
        }
      "
    />
  </section>
</template>

<!-- 🔴 视图不得重定义基础块（.tag）：形态全站收口在 theme.css，见 docs/ui/web/公共组件与形态基线.md -->
