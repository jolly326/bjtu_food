<script setup lang="ts">
/**
 * UserActivityModal：用户行为聚合（任务：用户管理 → 查看某学生的全部行为）。
 * 打开时并行拉取该用户的动态 / 评价 / 反馈，分 tab 展示，方便管理员判断用户是否有违规内容。
 */
import { ref, watch } from 'vue'
import Modal from '@/components/Modal.vue'
import StatusTag from '@/components/StatusTag.vue'
import { listMoments, type MomentManageVO } from '@/api/moment'
import { listFeedbacks, type FeedbackAdminVO } from '@/api/feedback'
import { getAll } from '@/api/review'
import type { Review } from '@/types'

const props = defineProps<{ show: boolean; user: any }>()
const emit = defineEmits<{ close: [] }>()

const section = ref<'moment' | 'review' | 'feedback'>('moment')
const loading = ref(false)
const moments = ref<MomentManageVO[]>([])
const reviews = ref<Review[]>([])
const feedbacks = ref<FeedbackAdminVO[]>([])

function fmtTime(v: string | Date | undefined | null): string {
  if (!v) return ''
  const d = v instanceof Date ? v : new Date(v)
  if (isNaN(d.getTime())) return String(v).slice(0, 10)
  return d.toLocaleString('zh-CN')
}

function fmtContent(s: string | undefined, max = 60): string {
  const t = (s || '').replace(/\s+/g, ' ').trim()
  return t.length > max ? t.slice(0, max) + '…' : (t || '（无内容）')
}

watch(() => props.show, async (v) => {
  if (v && props.user) {
    section.value = 'moment'
    loading.value = true
    try {
      const uid = Number(props.user.id)
      const [m, r, f] = await Promise.all([
        listMoments({ userId: uid, pageSize: 30 }),
        getAll(uid),
        listFeedbacks({ userId: uid, pageSize: 30 }),
      ])
      moments.value = m.list
      reviews.value = r
      feedbacks.value = f.list
    } catch {
      moments.value = []
      reviews.value = []
      feedbacks.value = []
    } finally {
      loading.value = false
    }
  }
})

const sections = [
  { key: 'moment' as const, label: '动态' },
  { key: 'review' as const, label: '评价' },
  { key: 'feedback' as const, label: '反馈' },
]
function countOf(key: string): number {
  if (key === 'moment') return moments.value.length
  if (key === 'review') return reviews.value.length
  return feedbacks.value.length
}
</script>

<template>
  <Modal :show="show" title="用户行为" :width="640" @close="emit('close')">
    <!-- 用户信息 -->
    <div class="ua-user">
      <span class="ua-avatar">{{ (user?.nickname || user?.username || '?')[0] }}</span>
      <div class="ua-info">
        <div class="ua-name">
          {{ user?.nickname || user?.username }}
          <StatusTag
            :type="user?.status === 'active' ? 'success' : 'danger'"
            :text="user?.status === 'active' ? '正常' : '已禁用'"
          />
        </div>
        <div class="ua-meta">@{{ user?.username }} · {{ fmtTime(user?.created_at) }} 注册</div>
      </div>
    </div>

    <!-- 行为分段 -->
    <div class="ua-tabs">
      <button
        v-for="s in sections"
        :key="s.key"
        class="ua-tab"
        :class="{ on: section === s.key }"
        v-press
        type="button"
        @click="section = s.key"
      >{{ s.label }}（{{ countOf(s.key) }}）</button>
    </div>

    <!-- 内容列表 -->
    <div v-if="loading" class="ua-empty">加载中…</div>

    <template v-else>
      <!-- 动态 -->
      <div v-if="section === 'moment'" class="ua-list">
        <div v-for="m in moments" :key="m.id" class="ua-item">
          <div class="ua-item-main">{{ fmtContent(m.content) }}</div>
          <div class="ua-item-meta">
            <StatusTag
              :type="m.auditStatus === 'approved' ? 'success' : m.auditStatus === 'rejected' ? 'danger' : 'warning'"
              :text="m.auditStatus === 'approved' ? (m.status === 1 ? '已下架' : '已通过') : m.auditStatus === 'rejected' ? '已退回' : '待审核'"
            />
            <span class="ua-time">{{ fmtTime(m.createdAt) }}</span>
          </div>
        </div>
        <div v-if="!moments.length" class="ua-empty">该用户暂无动态</div>
      </div>

      <!-- 评价 -->
      <div v-else-if="section === 'review'" class="ua-list">
        <div v-for="r in reviews" :key="Number(r.id)" class="ua-item">
          <div class="ua-item-main">
            <span class="ua-stars">{{ '★'.repeat(r.rating) }}</span>
            {{ fmtContent(r.content) }}
          </div>
          <div class="ua-item-meta">
            <StatusTag :type="r.is_hidden === 1 ? 'danger' : 'success'" :text="r.is_hidden === 1 ? '已隐藏' : '显示中'" />
            <span class="ua-time">{{ fmtTime(r.created_at) }}</span>
          </div>
        </div>
        <div v-if="!reviews.length" class="ua-empty">该用户暂无评价</div>
      </div>

      <!-- 反馈 -->
      <div v-else class="ua-list">
        <div v-for="f in feedbacks" :key="f.id" class="ua-item">
          <div class="ua-item-main">{{ fmtContent(f.content) }}</div>
          <div class="ua-item-meta">
            <StatusTag :type="f.status === 'handled' ? 'success' : 'warning'" :text="f.status === 'handled' ? '已处理' : '待处理'" />
            <span class="ua-time">{{ fmtTime(f.createdAt) }}</span>
          </div>
        </div>
        <div v-if="!feedbacks.length" class="ua-empty">该用户暂无反馈</div>
      </div>
    </template>
  </Modal>
</template>

<style scoped>
.ua-user {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-1) 0 var(--space-4);
  border-bottom: 1px solid var(--border-light);
}
.ua-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--color-primary);
  color: var(--color-on-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: var(--weight-semibold);
  flex-shrink: 0;
}
.ua-info { min-width: 0; }
.ua-name {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-lg);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.ua-meta { margin-top: 2px; font-size: var(--font-xs); color: var(--text-muted); }

.ua-tabs { display: inline-flex; gap: 2px; padding: 3px; background: var(--agg-tabs-bg); border-radius: var(--radius-pill); margin: var(--space-4) 0; }
.ua-tab { padding: var(--space-2) var(--space-4); border: none; background: transparent; border-radius: var(--radius-pill); font-size: var(--font-base); color: var(--text-secondary); cursor: pointer; transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out), transform 160ms var(--ease-out); }
.ua-tab:hover { color: var(--text-primary); }
.ua-tab.on { background: var(--agg-tab-active-bg); color: var(--color-primary); font-weight: var(--weight-semibold); box-shadow: var(--agg-tab-active-shadow); }
.ua-tab:active { transform: scale(var(--press-scale)); }

.ua-list { display: flex; flex-direction: column; }
.ua-item {
  padding: var(--space-3) var(--space-1);
  border-bottom: 1px solid var(--border-soft);
}
.ua-item:last-child { border-bottom: none; }
.ua-item-main { font-size: var(--font-base); color: var(--text-primary); line-height: 1.5; }
.ua-stars { color: var(--color-warning); margin-right: var(--space-2); }
.ua-item-meta {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-2);
}
.ua-time { font-size: var(--font-xs); color: var(--text-light); }
.ua-empty {
  text-align: center;
  color: var(--text-light);
  padding: var(--space-8) 0;
  font-size: var(--font-base);
  /* 加载/空态高度稳定，弹窗内不塌陷 */
  min-height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}
</style>
