<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listReviews, setReviewHidden, deleteReview } from '@/api/reviews'
import type { ReviewAdminVO, ReviewListParams } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import StateBox from '@/components/StateBox.vue'

const fKeyword = ref('')
const fDishId = ref('')
const fUserId = ref('')
const fHidden = ref<boolean | ''>('')

function params(): ReviewListParams {
  return {
    keyword: fKeyword.value || undefined,
    dishId: fDishId.value ? Number(fDishId.value) : undefined,
    userId: fUserId.value ? Number(fUserId.value) : undefined,
    hidden: fHidden.value === '' ? undefined : fHidden.value,
  }
}

const { items, loading, finished, error, load } = usePagedList<ReviewAdminVO>(
  (page, pageSize) => listReviews({ page, pageSize, ...params() }),
  20,
)

async function toggleHidden(r: ReviewAdminVO): Promise<void> {
  try {
    await setReviewHidden(r.id, { hidden: !r.isHidden })
    ElMessage.success(r.isHidden ? '已取消隐藏' : '已隐藏')
    load(true)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}
async function remove(r: ReviewAdminVO): Promise<void> {
  try {
    await ElMessageBox.confirm('确认删除该评价？删除后不可恢复，并会触发菜品评分重算。', '提示', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteReview(r.id)
    ElMessage.success('已删除')
    load(true)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}
function search(): void {
  load(true)
}
function reset(): void {
  fKeyword.value = ''
  fDishId.value = ''
  fUserId.value = ''
  fHidden.value = ''
  load(true)
}

onMounted(() => load(true))
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>评价管理</h2></div>

    <div class="card filters">
      <input class="form-input" v-model="fKeyword" placeholder="评价内容" @keyup.enter="search" />
      <input class="form-input" v-model="fDishId" placeholder="菜品 ID" @keyup.enter="search" />
      <input class="form-input" v-model="fUserId" placeholder="用户 ID" @keyup.enter="search" />
      <select class="form-input" v-model="fHidden" @change="search">
        <option value="">全部</option>
        <option :value="false">未隐藏</option>
        <option :value="true">已隐藏</option>
      </select>
      <button class="btn-primary" type="button" v-press @click="search">查询</button>
      <button class="btn-ghost" type="button" @click="reset">重置</button>
    </div>

    <StateBox v-if="loading && !items.length" status="loading" />
    <StateBox v-else-if="error" status="error" :message="error" @retry="search" />
    <div v-else class="card table-wrap">
      <table class="table">
        <thead>
          <tr>
            <th>菜品</th>
            <th>用户</th>
            <th>评分</th>
            <th>内容</th>
            <th>配图</th>
            <th>状态</th>
            <th>时间</th>
            <th class="actions">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in items" :key="r.id">
            <td>{{ r.dishName ?? '菜品已删除' }}</td>
            <td>{{ r.userNickname }}</td>
            <td>{{ r.rating }}★</td>
            <td class="ellipsis">{{ r.content || '—' }}</td>
            <td>
              <div class="thumbs">
                <img v-for="(img, i) in r.images" :key="i" :src="img" alt="" />
              </div>
            </td>
            <td>
              <span :class="r.isHidden ? 'tag tag-red' : 'tag tag-green'">
                {{ r.isHidden ? '已隐藏' : '正常' }}
              </span>
            </td>
            <td class="muted">{{ r.createdAt }}</td>
            <td class="actions">
              <button class="link" type="button" @click="toggleHidden(r)">
                {{ r.isHidden ? '取消隐藏' : '隐藏' }}
              </button>
              <button class="link danger" type="button" @click="remove(r)">删除</button>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="8"><StateBox status="empty" /></td>
          </tr>
        </tbody>
      </table>
      <div class="pager">
        <span v-if="loading" class="muted">加载中…</span>
        <button v-else-if="!finished" class="btn-ghost" type="button" @click="load()">加载更多</button>
        <span v-else-if="items.length" class="muted">已全部加载</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  padding: var(--space-4);
  margin-bottom: var(--space-4);
}
.filters .form-input {
  width: 150px;
}
.thumbs {
  display: flex;
  gap: var(--space-1);
}
.thumbs img {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  object-fit: cover;
}
.muted {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
.pager {
  padding: var(--space-4);
  text-align: center;
}
</style>
