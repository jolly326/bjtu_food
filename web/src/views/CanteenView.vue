<script setup lang="ts">
/**
 * 食堂管理（设计稿 docs/ui/web/食堂管理.md；业务规则 docs/func/web/A1）。
 * 范式：顶栏 + 表格 + 分页；新建 / 编辑走 520px 弹窗；删除二次确认。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppIcon from '@/components/AppIcon.vue'
import { canteenApi, type CanteenOut } from '@/api'

const rows = ref<CanteenOut[]>([])
const total = ref(0)
const page = ref(1)
const loading = ref(false)

const dialog = reactive({ visible: false, id: 0 as number, name: '' })
const saving = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await canteenApi.list(page.value)
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

function onCreate() {
  dialog.id = 0
  dialog.name = ''
  dialog.visible = true
}

function onEdit(row: CanteenOut) {
  dialog.id = row.id
  dialog.name = row.name
  dialog.visible = true
}

async function onSave() {
  if (!dialog.name.trim()) {
    ElMessage.warning('食堂名称不能为空')
    return
  }
  saving.value = true
  try {
    if (dialog.id) {
      await canteenApi.update(dialog.id, dialog.name.trim())
    } else {
      await canteenApi.create(dialog.name.trim())
    }
    ElMessage.success('已保存')
    dialog.visible = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function onRemove(row: CanteenOut) {
  try {
    await ElMessageBox.confirm('删除后不可恢复。若该食堂下仍有档口，删除会被拒绝。', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await canteenApi.remove(row.id)
    ElMessage.success('已删除')
    if (rows.value.length === 1 && page.value > 1) page.value -= 1
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
      <h2 class="page__title">食堂管理</h2>
      <button class="btn btn--primary" @click="onCreate">
        <AppIcon name="plus" :size="16" />新建
      </button>
    </header>

    <table class="table">
      <thead>
        <tr>
          <th>名称</th>
          <th class="table__muted">档口数</th>
          <th class="table__muted">更新时间</th>
          <th class="table__ops">操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="row.id">
          <td>{{ row.name }}</td>
          <td class="table__muted">{{ row.stallCount }}</td>
          <td class="table__muted">{{ row.updatedAt }}</td>
          <td class="table__ops">
            <button class="link" @click="onEdit(row)">编辑</button>
            <button class="link link--danger" @click="onRemove(row)">删除</button>
          </td>
        </tr>
        <tr v-if="!rows.length && !loading">
          <td colspan="4" class="table__empty">暂无数据</td>
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
        <h3 class="dialog__title">{{ dialog.id ? '编辑食堂' : '新建食堂' }}</h3>
        <label class="dialog__label" for="canteen-name">名称</label>
        <input id="canteen-name" v-model="dialog.name" class="dialog__input" type="text" />
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

.dialog__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 24px;
}
</style>
