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

    <table class="table">
      <thead>
        <tr>
          <th>选项文案</th>
          <th class="table__muted table__num">引用次数</th>
          <th class="table__muted">更新时间</th>
          <th class="table__ops">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.name }}</td>
          <td class="table__muted table__num">{{ row.refCount }}</td>
          <td class="table__muted">{{ row.updatedAt }}</td>
          <td class="table__ops">
            <button class="link" @click="onEdit(row)">编辑</button>
            <button
              class="link link--danger"
              :class="{ 'is-disabled': row.refCount > 0 }"
              :title="row.refCount > 0 ? `已被 ${row.refCount} 条纠错提交引用，无法删除` : ''"
              @click="onRemove(row)"
            >
              删除
            </button>
          </td>
        </tr>
        <tr v-if="!rows.length && !loading">
          <td colspan="4" class="table__empty">暂无选项</td>
        </tr>
      </tbody>
    </table>

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
.is-disabled {
  pointer-events: none;
  opacity: 0.4;
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
</style>
