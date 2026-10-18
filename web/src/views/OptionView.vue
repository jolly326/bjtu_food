<script setup lang="ts">
/**
 * 纠错选项（设计稿 docs/ui/web/纠错选项.md；业务规则 docs/func/web/B3）。
 * 改名全站生效；被引用（refCount > 0）的选项禁删。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
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
        class="list-card"
        v-for="row in rows"
        :key="row.id"
      >
        <div class="list-card__main">
          <h3 class="list-card__title">
            <span class="list-card__titleText">{{ row.name }}</span>
          </h3>
        </div>
        <div class="list-card__actions">
          <button class="link" @click="onEdit(row)">编辑</button>
          <button
            class="link link--danger is-disabled"
            :title="row.refCount > 0 ? `已被 ${row.refCount} 条纠错提交引用，无法删除` : ''"
            @click="onRemove(row)"
          >
            删除
          </button>
        </div>
      </div>
      <span v-if="!rows.length && !loading" class="list-card__empty">暂无选项</span>
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
  margin-bottom: 6px;
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

.dialog__hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
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
