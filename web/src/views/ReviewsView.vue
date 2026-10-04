<script setup lang="ts">
/**
 * B1 评价管理（页面规格见 [评价管理.md](../../../docs/ui/web/评价管理.md)）。
 *
 * <p>要点：分页（页码 + 共 N 条）；状态列**恒用 `StatusTag`（`kind="review"`）**，页面不自写 `.tag-*`；
 * 隐藏为**显式置位**（非 toggle）且支持**可选附注**（≤200 字，随回执下发给作者）；
 * 删除为物理删除（**触发评分重算 + 向作者投递回执**），确认文案写明影响面。
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { fail } from '@/utils/error'
import { deleteReview, listReviews, setReviewHidden } from '@/api/reviews'
import type { ReviewAdminVO, ReviewListParams } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import BaseModal from '@/components/BaseModal.vue'

const fKeyword = ref('')
const fDishId = ref('')
const fUserId = ref('')
// B1：隐藏筛选是**布尔**（与出参 `hidden` 同口径），不用 0/1 魔法值
const fHidden = ref<boolean | ''>('')

function params(): ReviewListParams {
  return {
    keyword: fKeyword.value || undefined,
    dishId: fDishId.value ? Number(fDishId.value) : undefined,
    userId: fUserId.value ? Number(fUserId.value) : undefined,
    // B1：新增 dishId 筛选
    hidden: fHidden.value === '' ? undefined : fHidden.value,
  }
}

const {
  items,
  total,
  page,
  pageCount,
  firstLoading,
  isEmpty,
  hasData,
  error,
  sessionInvalid,
  reload,
  reloadFirstPage,
  prevPage,
  nextPage,
} = usePagedList<ReviewAdminVO>((pageNo, pageSize) =>
  listReviews({ page: pageNo, pageSize, ...params() }),
)

/* ===== 隐藏 / 恢复显示（显式置位，非 toggle） ===== */
const hideOpen = ref(false)
const hideTarget = ref<ReviewAdminVO | null>(null)
const hideNote = ref('')
const submitting = ref(false)

function openHide(row: ReviewAdminVO): void {
  hideTarget.value = row
  hideNote.value = row.hiddenNote ?? ''
  hideOpen.value = true
}

async function submitHide(): Promise<void> {
  const row = hideTarget.value
  if (!row) return
  if (hideNote.value.length > 200) {
    ElMessage.warning('附注不能超过 200 字')
    return
  }
  submitting.value = true
  try {
    await setReviewHidden(row.id, {
      hidden: true,
      note: hideNote.value.trim() || undefined,
    })
    ElMessage.success('已隐藏')
    hideOpen.value = false
    await reload()
  } catch (e) {
    fail(e)
  } finally {
    submitting.value = false
  }
}

async function unhide(row: ReviewAdminVO): Promise<void> {
  try {
    await setReviewHidden(row.id, { hidden: false })
    ElMessage.success('已恢复显示')
    await reload()
  } catch (e) {
    fail(e)
  }
}

async function remove(row: ReviewAdminVO): Promise<void> {
  try {
    await confirmDelete(`确认删除这条评价？删除后**不可恢复**，并会触发「${row.dishName ?? '该菜品'}」的评分重算；作者会收到站内回执。`, {
      title: '删除评价',
    })
  } catch {
    return
  }
  try {
    await deleteReview(row.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e) {
    fail(e, '删除失败')
  }
}

function reset(): void {
  fKeyword.value = ''
  fDishId.value = ''
  fUserId.value = ''
  fHidden.value = ''
  reloadFirstPage()
}

onMounted(() => reloadFirstPage())
</script>

<template>
  <div class="page">
    <div class="page-header"><h2>评价管理</h2></div>

    <div class="card filters">
      <input class="form-input" v-model="fKeyword" placeholder="评价内容" @keyup.enter="reloadFirstPage" />
      <input class="form-input" v-model="fDishId" placeholder="菜品 ID" @keyup.enter="reloadFirstPage" />
      <input class="form-input" v-model="fUserId" placeholder="用户 ID" @keyup.enter="reloadFirstPage" />
      <select class="form-input" v-model="fHidden" @change="reloadFirstPage">
        <option value="">全部状态</option>
        <option :value="false">显示中</option>
        <option :value="true">已隐藏</option>
      </select>
      <button class="btn-primary" type="button" v-press @click="reloadFirstPage">查询</button>
      <button class="btn-secondary" type="button" @click="reset">重置</button>
    </div>

    <!-- 六态：① 加载 ② 错误 ③ 空 ④ 有数据 ⑤ 分页 ⑥ 会话失效 -->
    <ListState
      :loading="firstLoading"
      :session-invalid="sessionInvalid"
      :error="error"
      :empty="isEmpty"
      empty-message="暂无评价"
      @retry="reload"
    />
    <div v-if="hasData" class="card table-wrap">
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
          <tr v-for="row in items" :key="row.id">
            <td>{{ row.dishName ?? '菜品已删除' }}</td>
            <td>{{ row.userNickname || '游客' }}</td>
            <td class="num">{{ row.rating }}★</td>
            <td class="ellipsis">{{ row.content || '—' }}</td>
            <td>
              <div class="thumbs">
                <img v-for="(img, i) in row.images" :key="i" :src="img" alt="" />
              </div>
            </td>
            <td><StatusTag :status="row.hidden ? 'hidden' : 'visible'" kind="review" /></td>
            <td class="muted">{{ row.createdAt }}</td>
            <td class="actions">
              <button class="link" type="button" @click="row.hidden ? unhide(row) : openHide(row)">
                {{ row.hidden ? '恢复显示' : '隐藏' }}
              </button>
              <button class="link danger" type="button" @click="remove(row)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>

      <Pager :total="total" :page="page" :page-count="pageCount" @prev="prevPage" @next="nextPage" />
    </div>

    <!-- 隐藏：显式置位 + 可选附注（随回执下发） -->
    <BaseModal title="隐藏评价" :open="hideOpen" @close="hideOpen = false">
      <div class="ctx">
        <div class="ctx-label">评价内容</div>
        <div class="ctx-body">{{ hideTarget?.content || '（无文字）' }}</div>
      </div>
      <div class="field">
        <label for="review-note">附注（可选，≤200 字）</label>
        <textarea
          id="review-note"
          class="form-textarea"
          v-model="hideNote"
          rows="3"
          maxlength="200"
          placeholder="将随站内回执下发给评价作者，留空则回执用固定文案"
        />
        <div class="hint">{{ hideNote.length }} / 200</div>
      </div>
      <template #actions>
        <button class="btn-secondary" type="button" @click="hideOpen = false">取消</button>
        <button class="btn-primary" type="button" :disabled="submitting" v-press @click="submitHide">
          {{ submitting ? '提交中…' : '确认隐藏' }}
        </button>
      </template>
    </BaseModal>
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
.num {
  font-variant-numeric: tabular-nums;
}
.muted {
  color: var(--text-muted);
  font-size: var(--font-sm);
}
.hint {
  margin-top: var(--space-1);
  color: var(--text-muted);
  font-size: var(--font-xs);
  text-align: right;
}
.ctx {
  background: var(--bg-soft);
  border-radius: var(--radius);
  padding: var(--space-3);
  margin-bottom: var(--space-4);
}
.ctx-label {
  font-size: var(--font-xs);
  color: var(--text-muted);
  margin-bottom: var(--space-1);
}
.ctx-body {
  white-space: pre-wrap;
  color: var(--text-primary);
}
</style>
