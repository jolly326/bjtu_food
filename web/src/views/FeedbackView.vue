<script setup lang="ts">
/**
 * 反馈数据（设计稿 docs/ui/web/反馈数据.md；业务规则 docs/func/web/B1）。
 *
 * 只读总览：菜品点赞数来自 `dish.likeCount`（派生缓存列）。
 * 🔴 不修改 / 不删除任何数据，且**不展示任何用户标识**（学生全匿名）；
 * 纠错处置在「纠错处理」页完成，本页只对「有未处理纠错」的菜品做标记。
 */
import { onMounted, ref } from 'vue'
import AppIcon from '@/components/AppIcon.vue'
import { usePagedList } from '@/composables/usePagedList'
import { dishApi, issueApi, type DishOut } from '@/api'

/**
 * 未处理纠错的扫描上限：只用于「有未处理纠错」的标记，
 * 超出的部分不在本页体现（精确处置走「纠错处理」页）。
 */
const ISSUE_SCAN_SIZE = 50

/** 有未处理纠错的菜品 id */
const flagged = ref<Set<number>>(new Set())

/** 分页状态机：翻页 / 加载态 / 末页空回退单点收口（见基线 §2.3「分页」） */
const { rows, total, page, loading, hasNext, load, go } = usePagedList<DishOut>((p) =>
  dishApi.list({ page: p }),
)

async function loadFlags(): Promise<void> {
  try {
    const res = await issueApi.list({ handled: false, pageSize: ISSUE_SCAN_SIZE })
    flagged.value = new Set(res.records.map((item) => item.dishId))
  } catch {
    // 标记仅作提示：拉取失败不影响点赞数总览
  }
}

onMounted(async () => {
  await load()
  await loadFlags()
})
</script>

<template>
  <section class="page">
    <header class="page__bar">
      <h2 class="page__title">反馈数据</h2>
    </header>

    <div class="list-card-wrap">
      <div v-for="row in rows" :key="row.id" class="list-card" aria-label="菜品反馈">
        <div class="list-card__main">
          <h3 class="list-card__title">
            <span class="list-card__titleText">{{ row.name }}</span>
          </h3>
          <div class="list-card__path">{{ row.canteenName }} · {{ row.stallName }}</div>
          <div class="list-card__stats">
            <span class="list-card__like">
              <AppIcon name="heart" :size="12" />{{ row.likeCount }} 人赞
            </span>
            <span v-if="flagged.has(row.id)" class="list-card__stat">有未处理纠错</span>
          </div>
        </div>
      </div>
      <div class="list-card" v-if="!rows.length && !loading">
        <span class="list-card__empty">暂无数据</span>
      </div>
    </div>

    <footer class="page__foot">
      <span class="page__total">共 {{ total }} 条</span>
      <div class="pager">
        <button class="btn" :disabled="page <= 1" @click="go(page - 1)">上一页</button>
        <span class="pager__cur">{{ page }}</span>
        <button class="btn" :disabled="!hasNext" @click="go(page + 1)">下一页</button>
      </div>
    </footer>
  </section>
</template>
