<script setup lang="ts">
/**
 * 纠错选项（设计稿 docs/ui/web/纠错选项.md；业务规则 docs/func/web/B3）。
 * 改名全站生效；被引用（refCount > 0）的选项禁删。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import CardActionSheet from '@/components/CardActionSheet.vue'
import { useCardMenu } from '@/composables/useCardMenu'
import { optionApi, type OptionOut } from '@/api'

const rows = ref<OptionOut[]>([])
const loading = ref(false)
const saving = ref(false)
const dialog = reactive({ visible: false, id: 0, name: '' })

async function load() {
  loading.value = true
  try {
    rows.value = await optionApi.list()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function onCreate() {
  dialog.id = 0
  dialog.name = ''
  dialog.visible = true
}

function onEdit(row: OptionOut) {
  dialog.id = row.id
  dialog.name = row.name
  dialog.visible = true
}

async function onSave() {
  if (!dialog.name.trim()) {
    ElMessage.warning('选项文案不能为空')
    return
  }
  saving.value = true
  try {
    if (dialog.id) await optionApi.update(dialog.id, dialog.name.trim())
    else await optionApi.create(dialog.name.trim())
    ElMessage.success('已保存')
    dialog.visible = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function onRemove(row: OptionOut) {
  if (row.refCount > 0) return
  try {
    await ElMessageBox.confirm(`删除后学生端将不再出现「${row.name}」。`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await optionApi.remove(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

/** 长按浮层（编辑 / 删除）：被引用（refCount > 0）时浮层内删除置灰并注明原因 */
const cardMenu = useCardMenu<OptionOut>({ onEdit, onRemove })

onMounted(load)
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">纠错选项</h2>
      <button class="btn btn--primary" @click="onCreate">
        <AppIcon name="plus" :size="16" />新建选项
      </button>
    </header>

    <div class="list-card-wrap">
      <div
        v-for="row in rows"
        :key="row.id"
        class="list-card"
        tabindex="0"
        aria-haspopup="menu"
        v-bind="
          cardMenu.pressAttrs(row, {
            title: row.name,
            removeDisabled: row.refCount > 0,
            removeHint: row.refCount > 0 ? `已被 ${row.refCount} 条纠错引用，无法删除` : undefined,
          })
        "
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
      <div class="list-card" v-if="!rows.length && !loading">
        <span class="list-card__empty">暂无选项</span>
      </div>
    </div>

    <div v-if="dialog.visible" class="mask" @click.self="dialog.visible = false">
      <div class="dialog">
        <h3 class="dialog__title">{{ dialog.id ? '编辑选项' : '新建选项' }}</h3>
        <label class="dialog__label" for="option-name">选项文案</label>
        <input id="option-name" v-model="dialog.name" class="dialog__input" type="text" />
        <p class="dialog__hint">改名后全站生效（含历史记录的中文翻译）。</p>
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
