<script setup lang="ts">
/**
 * B1 评价管理（页面规格见 [评价管理.md](../../../docs/ui/web/评价管理.md)）。
 *
 * <p>要点：分页（页码 + 共 N 条）；状态列**恒用 `StatusTag`（`kind="review"`）**，页面不自写 `.tag-*`；
 * 隐藏为**显式置位**（非 toggle）且支持**可选附注**（≤200 字，随回执下发给作者）；
 * 删除为物理删除（**触发评分重算 + 向作者投递回执**），确认文案写明影响面。
 *
 * <p>审核可用性：配图缩略图（56px）**点击即开 `ImagePreview` 看大图**；行操作「详情」开
 * `BaseDrawer`，抽屉内给**评价全文**（不截断）+ **大尺寸配图**（点击同样看原图）+ 元信息，
 * 底部复用行内的「隐藏 / 恢复显示 / 删除」（提交中沿用 `busyId` 置灰）。
 */
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { formatDateTime } from '@/utils/datetime'
import { fail } from '@/utils/error'
import { deleteReview, listReviews, setReviewHidden } from '@/api/reviews'
import type { ReviewAdminVO, ReviewListParams } from '@/types/common'
import { usePagedList } from '@/composables/usePagedList'
import ListState from '@/components/ListState.vue'
import Pager from '@/components/Pager.vue'
import StatusTag from '@/components/StatusTag.vue'
import { resolveImageUrl } from '@/utils/image'
import DetailMetaRow from '@/components/DetailMetaRow.vue'
import BaseModal from '@/components/BaseModal.vue'
import BaseDrawer from '@/components/BaseDrawer.vue'
import ImagePreview from '@/components/ImagePreview.vue'
import { useRowAction } from '@/composables/useRowAction'

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
/** 行内动作并发保护：提交中该行按钮 `:disabled`（列表页模板 §1.3「并发保护」） */
const { isBusy, runRowAction } = useRowAction()

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
    syncCurrent(row.id)
  } catch (e) {
    fail(e)
  } finally {
    submitting.value = false
  }
}

function unhide(row: ReviewAdminVO): Promise<void> {
  return runRowAction({
    id: row.id,
    action: () => setReviewHidden(row.id, { hidden: false }),
    successMessage: '已恢复显示',
    refresh: reload,
    syncAfterRefresh: syncCurrent,
  })
}

function remove(row: ReviewAdminVO): Promise<void> {
  return runRowAction({
    id: row.id,
    action: async () => {
      await confirmDelete(
        `确认删除这条评价？删除后不可恢复，并会触发「${row.dishName ?? '该菜品'}」的评分重算；作者会收到站内回执。`,
        {
          title: '删除评价',
        },
      )
      await deleteReview(row.id)
    },
    successMessage: '已删除',
    failMessage: '删除失败',
    refresh: reload,
    // 行已不存在：抽屉若正开着这条，一并关闭（不留空壳）
    closeDrawer: () => {
      if (current.value?.id !== row.id) return
      detailOpen.value = false
      current.value = null
    },
  })
}

/* ===== 详情抽屉（评价全文 + 配图大图入口 + 元信息 + 处置） ===== */
const detailOpen = ref(false)
const current = ref<ReviewAdminVO | null>(null)

function openDetail(row: ReviewAdminVO): void {
  current.value = row
  detailOpen.value = true
}

/** 抽屉底部动作：复用行内的隐藏 / 恢复显示 / 删除（确认文案与成功提示同源、逐字一致） */
function detailToggleHide(): void {
  const row = current.value
  if (!row) return
  if (row.hidden) void unhide(row)
  else openHide(row)
}

function detailRemove(): void {
  const row = current.value
  if (!row) return
  void remove(row)
}

/** 处置成功后按 id 回填最新行（列表已 reload），避免抽屉停在旧快照上 */
function syncCurrent(id: number): void {
  if (current.value?.id !== id) return
  current.value = items.value.find((r) => r.id === id) ?? current.value
}

/* ===== 配图大图预览（表格缩略图 / 详情抽屉共用入口） ===== */
const previewOpen = ref(false)
const previewImages = ref<string[]>([])
const previewIndex = ref(0)

function openPreview(images: string[], index: number): void {
  if (!images || images.length === 0) return
  previewImages.value = images
  previewIndex.value = index
  previewOpen.value = true
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
      <input
        class="form-input"
        v-model="fKeyword"
        placeholder="评价内容"
        @keyup.enter="reloadFirstPage"
      />
      <input
        class="form-input"
        v-model="fDishId"
        placeholder="菜品 ID"
        @keyup.enter="reloadFirstPage"
      />
      <input
        class="form-input"
        v-model="fUserId"
        placeholder="用户 ID"
        @keyup.enter="reloadFirstPage"
      />
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
            <th>评价者</th>
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
            <td>
              <!-- 评价者主标识：头像（32×32 圆形）+ 昵称，占位范式与 UsersView 同源 -->
              <div class="user-cell">
                <img v-if="row.userAvatar" :src="row.userAvatar" class="avatar" alt="" />
                <span v-else class="avatar avatar-placeholder" aria-hidden="true">·</span>
                <span>{{ row.userNickname || '游客' }}</span>
              </div>
            </td>
            <!-- 评分格：内联 SVG 星形图标（--color-star）在数字前（SHALL NOT 用文本「★」拼贴；
                 数字沿用 .num 等宽列） -->
            <td class="num">
              <span class="rating-cell">
                <svg class="rating-star" viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"
                  /></svg
                >{{ row.rating }}
              </span>
            </td>
            <td>
              <!-- 内容摘要（配图在右侧图片列直接呈现缩略图，不再重复角标） -->
              <ClampText :text="row.content" />
            </td>
            <td>
              <div class="thumbs">
                <!-- 缩略图即入口：点击直接看大图，审核不必进详情 -->
                <button
                  v-for="(img, i) in row.images"
                  :key="i"
                  class="thumb"
                  type="button"
                  :aria-label="`查看第 ${i + 1} 张配图`"
                  @click="openPreview(row.images, i)"
                >
                  <img :src="resolveImageUrl(img)" alt="" />
                </button>
              </div>
            </td>
            <td><StatusTag :status="row.hidden ? 'hidden' : 'visible'" kind="review" /></td>
            <td class="muted">{{ formatDateTime(row.createdAt) }}</td>
            <td class="actions">
              <button
                class="link"
                type="button"
                :disabled="isBusy(row.id)"
                @click="openDetail(row)"
              >
                详情
              </button>
              <button
                class="link"
                type="button"
                :disabled="isBusy(row.id)"
                @click="row.hidden ? unhide(row) : openHide(row)"
              >
                {{ row.hidden ? '恢复显示' : '隐藏' }}
              </button>
              <button
                class="link danger"
                type="button"
                :disabled="isBusy(row.id)"
                @click="remove(row)"
              >
                删除
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <Pager
        :total="total"
        :page="page"
        :page-count="pageCount"
        @prev="prevPage"
        @next="nextPage"
      />
    </div>

    <!-- 详情抽屉：评价全文（不截断）+ 配图大图入口 + 元信息 + 隐藏 / 恢复 / 删除 -->
    <BaseDrawer title="评价详情" :open="detailOpen" @close="detailOpen = false">
      <div class="ctx">
        <div class="ctx-label">评价内容</div>
        <div class="ctx-body">{{ current?.content || '（无文字）' }}</div>
      </div>

      <div class="field" v-if="current && current.images.length > 0">
        <label id="rv-images-label">配图 · {{ current.images.length }} 张（点击看大图）</label>
        <div class="detail-thumbs" role="group" aria-labelledby="rv-images-label">
          <button
            v-for="(img, i) in current.images"
            :key="i"
            class="thumb"
            type="button"
            :aria-label="`查看第 ${i + 1} 张配图`"
            @click="openPreview(current.images, i)"
          >
            <img :src="resolveImageUrl(img)" alt="" />
          </button>
        </div>
      </div>

      <div class="detail-meta">
        <DetailMetaRow k="评价 ID" num>#{{ current?.id }}</DetailMetaRow>
        <div class="meta-row">
          <span class="meta-key">作者</span>
          <span class="meta-val">
            <!-- 头像与表格同源：无头像走统一占位（灰底 + 人形符） -->
            <span class="user-cell">
              <img v-if="current?.userAvatar" :src="current.userAvatar" class="avatar" alt="" />
              <span v-else class="avatar avatar-placeholder" aria-hidden="true">·</span>
              <span>
                {{ current?.userNickname || '游客'
                }}<span class="muted"> #{{ current?.userId }}</span>
              </span>
            </span>
          </span>
        </div>
        <div class="meta-row">
          <span class="meta-key">菜品</span>
          <span class="meta-val">
            {{ current?.dishName ?? '菜品已删除'
            }}<span class="muted"> #{{ current?.dishId }}</span>
          </span>
        </div>
        <div class="meta-row">
          <span class="meta-key">评分</span>
          <span class="meta-val num">
            <span class="rating-cell">
              <svg class="rating-star" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"
                /></svg
              >{{ current?.rating }}
            </span>
          </span>
        </div>
        <div class="meta-row">
          <span class="meta-key">状态</span>
          <span class="meta-val">
            <StatusTag :status="current?.hidden ? 'hidden' : 'visible'" kind="review" />
          </span>
        </div>
        <DetailMetaRow k="发表时间">{{ formatDateTime(current?.createdAt) }}</DetailMetaRow>
        <div class="meta-row" v-if="current && current.hidden && current.hiddenNote">
          <DetailMetaRow k="隐藏附注">{{ current.hiddenNote }}</DetailMetaRow>
        </div>
      </div>

      <template #actions>
        <button
          class="link"
          type="button"
          :disabled="isBusy(current?.id)"
          @click="detailToggleHide"
        >
          {{ current?.hidden ? '恢复显示' : '隐藏' }}
        </button>
        <button
          class="link danger"
          type="button"
          :disabled="isBusy(current?.id)"
          @click="detailRemove"
        >
          删除
        </button>
      </template>
    </BaseDrawer>

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
        <button
          class="btn-primary"
          type="button"
          :disabled="submitting"
          v-press
          @click="submitHide"
        >
          {{ submitting ? '提交中…' : '确认隐藏' }}
        </button>
      </template>
    </BaseModal>

    <!-- 配图大图预览：表格缩略图 / 详情抽屉共用，挂载即打开 -->
    <ImagePreview
      v-if="previewOpen"
      :images="previewImages"
      :index="previewIndex"
      @close="previewOpen = false"
    />
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
/* 评分展示：内联 SVG 星形图标（--color-star，独立语义色）在数字前，数字保持 .num 等宽
   （表格评分格与抽屉评分位共用同一段展示结构） */
.rating-cell {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}
.rating-star {
  width: 12px;
  height: 12px;
  flex: none;
  fill: var(--color-star);
}
.thumbs img {
  width: 56px;
  height: 56px;
  border-radius: var(--radius-sm);
  object-fit: cover;
}
/* 抽屉内配图与下方元信息之间的间距（公共 `.detail-thumbs` 只给上间距） */
.detail-thumbs {
  margin-bottom: var(--space-4);
}
/* 字数计数右对齐（本页表单习惯；公共 `.hint` 为左对齐） */
.hint {
  text-align: right;
}
</style>
