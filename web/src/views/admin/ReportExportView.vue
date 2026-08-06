<script setup lang="ts">
/**
 * ReportExportView：报表导出（task-12 W3）。
 * 四类 CSV 报表（菜品/评价/用户/动态），可选时间范围，下载 CSV（UTF-8 BOM，Excel 可直接打开）。
 * 调用 reportApi.downloadReport（fetch + blob + Bearer token）。
 */
import { ref, reactive } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import {
  REPORT_LABELS,
  REPORT_DESCS,
  downloadReport,
  type ReportType,
} from '@/api/report'
import { Dish, ChatDotRound, User, ChatLineSquare, Download } from '@element-plus/icons-vue'

// 报表导出（用户与系统聚合页"报表"tab 内容组件）

const toast = useToastStore()

const TYPES: { type: ReportType; icon: any; desc: string }[] = [
  { type: 'dishes', icon: Dish, desc: REPORT_DESCS.dishes },
  { type: 'reviews', icon: ChatDotRound, desc: REPORT_DESCS.reviews },
  { type: 'users', icon: User, desc: REPORT_DESCS.users },
  { type: 'moments', icon: ChatLineSquare, desc: REPORT_DESCS.moments },
]

// 每个类型的日期范围与下载状态
const ranges = reactive<Record<ReportType, { startAt: string; endAt: string }>>({
  dishes: { startAt: '', endAt: '' },
  reviews: { startAt: '', endAt: '' },
  users: { startAt: '', endAt: '' },
  moments: { startAt: '', endAt: '' },
})
const downloading = ref<ReportType | null>(null)

// ===== 快捷日期范围（视觉引导，替代"留空不限制"说明文字） =====
const QUICK_RANGES = [
  { label: '全部', days: 0 },
  { label: '近7天', days: 7 },
  { label: '近30天', days: 30 },
] as const

function isoDaysAgo(days: number): string {
  if (days === 0) return ''
  const d = new Date()
  d.setDate(d.getDate() - days)
  return d.toISOString().slice(0, 10)
}
function applyQuick(type: ReportType, q: { label: string; days: number }) {
  if (q.days === 0) {
    ranges[type] = { startAt: '', endAt: '' }
    return
  }
  ranges[type] = { startAt: isoDaysAgo(q.days), endAt: isoDaysAgo(0) }
}
function isQuick(type: ReportType, q: { label: string; days: number }): boolean {
  const r = ranges[type]
  if (q.days === 0) return !r.startAt && !r.endAt
  return r.startAt === isoDaysAgo(q.days) && r.endAt === isoDaysAgo(0)
}

async function handleDownload(type: ReportType) {
  const { startAt, endAt } = ranges[type]
  if (startAt && endAt && startAt > endAt) {
    toast.error('开始日期不能晚于结束日期')
    return
  }
  downloading.value = type
  try {
    await downloadReport(type, { startAt: startAt || undefined, endAt: endAt || undefined })
    toast.success(`${REPORT_LABELS[type]}已导出`)
  } catch (e: any) {
    toast.error(e.message || '导出失败')
  } finally {
    downloading.value = null
  }
}
</script>

<template>
    <div class="report-grid">
      <article v-for="t in TYPES" :key="t.type" class="report-card">
        <div class="report-head">
          <span class="report-icon"><el-icon><component :is="t.icon" /></el-icon></span>
          <div class="report-title-box">
            <h3 class="report-title">{{ REPORT_LABELS[t.type] }}</h3>
            <p class="report-desc">{{ t.desc }}</p>
          </div>
        </div>

        <div class="report-fields">
          <label class="field">
            <span class="field-label">开始日期</span>
            <input v-model="ranges[t.type].startAt" type="date" class="date-input" :placeholder="''" />
          </label>
          <label class="field">
            <span class="field-label">结束日期</span>
            <input v-model="ranges[t.type].endAt" type="date" class="date-input" :placeholder="''" />
          </label>
        </div>
        <!-- 快捷范围：点选即视觉表达"不限"，无需文字解释 -->
        <!-- 快捷范围：点选即视觉表达"不限"，无需文字解释 -->
        <div class="quick-chips">
          <button
            v-for="q in QUICK_RANGES"
            :key="q.label"
            class="chip"
            :class="{ on: isQuick(t.type, q) }"
            v-press
            type="button"
            @click="applyQuick(t.type, q)"
          >{{ q.label }}</button>
        </div>

        <button
          class="download-btn"
          v-press
          type="button"
          :disabled="downloading !== null"
          @click="handleDownload(t.type)"
        >
          <el-icon class="dl-ico"><Download /></el-icon>
          {{ downloading === t.type ? '导出中…' : '导出 CSV' }}
        </button>
      </article>
    </div>
</template>

<style scoped>
.report-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--space-4);
}
.report-card {
  background: var(--bg-card);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  padding: var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  transition: transform 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out), border-color 0.2s var(--ease-out);
}
@media (prefers-reduced-motion: reduce) {
  .report-card { transition: none; }
}
@media (hover: hover) {
  .report-card:hover {
    box-shadow: var(--shadow-hover);
    border-color: color-mix(in srgb, var(--color-primary) 30%, transparent);
  }
}

/* 快捷范围 chips */
.quick-chips { display: flex; gap: var(--space-2); margin-bottom: var(--space-4); }
.chip {
  padding: var(--space-1) var(--space-3);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: var(--font-sm);
  cursor: pointer;
  transition: background 0.2s var(--ease-out), color 0.2s var(--ease-out), border-color 0.2s var(--ease-out), transform 160ms var(--ease-out);
}
.chip:hover { border-color: var(--border-strong); color: var(--text-primary); }
.chip.on { background: var(--color-primary); color: var(--color-on-primary); border-color: var(--color-primary); }
.chip:active { transform: scale(var(--press-scale)); }

.report-head {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
}
.report-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  flex-shrink: 0;
  color: var(--color-primary);
  background: var(--color-primary-bg);
}
.report-icon .el-icon { font-size: 22px; }
.report-title-box { min-width: 0; }
.report-title {
  margin: 0 0 var(--space-1);
  font-size: var(--font-lg);
  font-weight: var(--weight-semibold);
  color: var(--text-primary);
}
.report-desc {
  margin: 0;
  font-size: var(--font-xs);
  color: var(--text-muted);
  line-height: var(--leading-base);
}

.report-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-3);
}
.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.field-label {
  font-size: var(--font-xs);
  color: var(--text-secondary);
}
.date-input {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--border-strong);
  border-radius: var(--radius);
  font-size: var(--font-sm);
  background: var(--bg-card);
  color: var(--text-primary);
  outline: none;
  transition: border-color 0.2s var(--ease-out), box-shadow 0.2s var(--ease-out);
}
.date-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 15%, transparent);
}
.field-tip {
  margin: -var(--space-2) 0 0;
  font-size: var(--font-xs);
  color: var(--text-light);
}

.download-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-5);
  border: none;
  border-radius: var(--radius);
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: var(--font-base);
  font-weight: var(--weight-medium);
  cursor: pointer;
  transition: background 0.2s var(--ease-out), transform 160ms var(--ease-out);
  margin-top: auto;
}
.download-btn:active:not(:disabled) { transform: scale(var(--press-scale)); }
.download-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.dl-ico { font-size: 16px; }
</style>
