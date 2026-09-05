<template>
  <view class="page feedback-page">
    <Header title="意见反馈" @back="goBack" />

    <!-- 轻量单视图 · 动态表单（三类型等宽卡片 + 结构化字段） -->
    <scroll-view class="scroll-wrap" scroll-y :scroll-into-view="scrollIntoView" :scroll-with-animation="true">
      <!-- 类型图标卡片（三列等宽：左侧 icon + 右侧一行标题） -->
      <view class="type-row">
        <view
          v-for="t in types"
          :key="t.value"
          class="type-card"
          :class="{ active: type === t.value }"
          hover-class="pressed"
          hover-stay-time="80"
          role="radio"
          :aria-checked="type === t.value"
          :aria-label="t.label"
          @tap="type = t.value"
        >
          <view class="type-icon">
            <IconSvg :name="t.icon" :size="36" :color="type === t.value ? 'var(--color-primary)' : 'var(--text-tertiary)'" />
          </view>
          <view class="type-copy">
            <text class="type-line">{{ t.label }}</text>
            <text v-if="type === t.value" class="type-desc">{{ t.desc }}</text>
          </view>
        </view>
      </view>

      <!-- 动态字段区 -->
      <CardSection>
        <!-- 提个想法（提建议 / 报问题，均文本 + 图片） -->
        <template v-if="type === 'suggestion'">
          <view class="sub-row" role="radiogroup" aria-label="细分类型">
            <view
              v-for="s in suggestionSubs"
              :key="s.value"
              class="sub-chip"
              :class="{ active: form.suggestion.sub === s.value }"
              hover-class="pressed"
              hover-stay-time="80"
              role="radio"
              :aria-checked="form.suggestion.sub === s.value"
              :aria-label="s.label"
              @tap="form.suggestion.sub = s.value"
            >
              <text class="sub-text">{{ s.label }}</text>
            </view>
          </view>

          <view class="field">
            <text class="field-label">想说啥<text class="req">*</text></text>
            <textarea
              id="f-sug-text"
              v-model="form.suggestion.text"
              class="content-input"
              :class="{ 'input-error': fieldErrors['suggestion.text'] }"
              :placeholder="form.suggestion.sub === 'idea' ? '你的想法，比如：希望加几个素食窗口' : '发生啥了？描述一下'"
              maxlength="1000"
              :auto-height="true"
              :cursor-spacing="20"
              :adjust-position="true"
              @input="clearError('suggestion.text')"
            />
            <text v-if="form.suggestion.text.length > 800" class="counter">{{ form.suggestion.text.length }}/1000</text>
            <text v-if="fieldErrors['suggestion.text']" class="field-error">{{ fieldErrors['suggestion.text'] }}</text>
          </view>

          <view class="field">
            <text class="field-label">图片</text>
            <ImageUploader v-model="form.suggestion.images" :max="3" show-counter />
          </view>
        </template>

        <!-- 推荐菜品（分组：基本信息 / 位置 / 图片与描述） -->
        <template v-else-if="type === 'add'">
          <view class="form-group">
            <view class="row-fields">
              <view class="col">
                <text class="field-label">菜名<text class="req">*</text></text>
                <input
                  id="f-add-name"
                  v-model="form.add.name"
                  class="field-input"
                  :class="{ 'input-error': fieldErrors['add.name'] }"
                  placeholder="必填"
                  maxlength="50"
                  :cursor-spacing="20"
                  :adjust-position="true"
                  @input="clearError('add.name')"
                />
                <text v-if="fieldErrors['add.name']" class="field-error">{{ fieldErrors['add.name'] }}</text>
              </view>
              <view class="col">
                <text class="field-label">价格（元）</text>
                <input
                  id="f-add-price"
                  v-model="form.add.price"
                  class="field-input"
                  :class="{ 'input-error': fieldErrors['add.price'] }"
                  type="digit"
                  placeholder="0.00"
                  maxlength="7"
                  :cursor-spacing="20"
                  :adjust-position="true"
                  @input="clearError('add.price')"
                />
                <text v-if="fieldErrors['add.price']" class="field-error">{{ fieldErrors['add.price'] }}</text>
              </view>
            </view>
          </view>

          <view class="form-group">
            <!-- 食堂 + 档口（一行） -->
            <view class="row-fields">
              <view class="col">
                <text class="field-label">食堂</text>
                <view
                  class="picker-row"
                  hover-class="pressed"
                  hover-stay-time="80"
                  role="button"
                  aria-label="选择食堂"
                  @tap="openLocationSheet('canteen')"
                >
                  <text class="picker-value" :class="{ placeholder: !displayCanteen }">{{ displayCanteen || '选择食堂' }}</text>
                  <IconSvg name="arrow" :size="26" color="var(--text-tertiary)" />
                </view>
              </view>
              <view class="col">
                <text class="field-label">档口</text>
                <!-- A1：未选食堂时禁用 + 引导 -->
                <view
                  class="picker-row"
                  :class="{ disabled: !displayCanteen }"
                  :hover-class="displayCanteen ? 'pressed' : 'none'"
                  hover-stay-time="80"
                  role="button"
                  :aria-label="displayCanteen ? '选择档口' : '请先选择食堂'"
                  @tap="onStallRowTap"
                >
                  <text class="picker-value" :class="{ placeholder: !displayStall }">{{ displayStall || (displayCanteen ? '选择档口' : '先选食堂') }}</text>
                  <IconSvg name="arrow" :size="26" color="var(--text-tertiary)" />
                </view>
              </view>
            </view>

            <view class="field-gap" />

            <!-- 楼层（单独一行，1/2/3 选择） -->
            <text class="field-label">楼层<text class="req">*</text></text>
            <view
              id="f-add-floor"
              class="picker-row"
              hover-class="pressed"
              hover-stay-time="80"
              role="button"
              aria-label="选择楼层"
              @tap="openFloorSheet"
            >
              <text class="picker-value" :class="{ placeholder: !form.add.floor }">{{ form.add.floor ? `${form.add.floor} 楼` : '选择' }}</text>
              <IconSvg name="arrow" :size="26" color="var(--text-tertiary)" />
            </view>
            <text v-if="fieldErrors['add.floor']" class="field-error">{{ fieldErrors['add.floor'] }}</text>
          </view>

          <view class="form-group">
            <view class="field">
              <text class="field-label">菜品图片</text>
              <ImageUploader v-model="form.add.images" :max="3" show-counter />
            </view>

            <view class="field">
              <text class="field-label">一句话描述</text>
              <textarea
                v-model="form.add.description"
                class="content-input content-input-sm"
                placeholder="口味 / 特色"
                maxlength="200"
                :auto-height="true"
                :cursor-spacing="20"
                :adjust-position="true"
              />
            </view>
          </view>
        </template>

        <!-- 信息不对（关联菜品 + 多选哪里不对 + 正确信息 + 作证） -->
        <template v-else-if="type === 'error'">
          <view class="field" id="f-dish">
            <text class="field-label">关联菜品<text class="req">*</text></text>

            <!-- 已选中：菜品摘要卡 -->
            <view v-if="form.error.dish" class="dish-linked">
              <image class="dish-thumb" :src="form.error.dish.image || ''" mode="aspectFill" />
              <view class="dish-info">
                <text class="dish-name">{{ form.error.dish.name }}</text>
                <text class="dish-meta">{{ formatDishMeta(form.error.dish) }}</text>
              </view>
              <view
                class="dish-change"
                hover-class="pressed"
                hover-stay-time="80"
                role="button"
                aria-label="换一个"
                @tap="resetDish"
              ><text class="dish-change-text">换一个</text></view>
            </view>

            <!-- 未选中：点击打开底部搜索弹窗 -->
            <view
              v-else
              class="picker-row dish-picker-row"
              hover-class="pressed"
              hover-stay-time="80"
              role="button"
              aria-label="搜索选择菜品"
              @tap="openDishSheet"
            >
              <IconSvg name="search" :size="30" color="var(--text-tertiary)" />
              <text class="picker-value placeholder">搜索选择菜品</text>
              <IconSvg name="arrow" :size="26" color="var(--text-tertiary)" />
            </view>

            <text v-if="fieldErrors['error.dish']" class="field-error">{{ fieldErrors['error.dish'] }}</text>
          </view>

          <!-- 哪里不对（每项一行：左侧选项 + 右侧编辑区，两独立组件不嵌套） -->
          <view class="field" id="f-point">
            <text class="field-label">哪里不对？<text class="req">*</text></text>
            <view class="point-list">
              <view
                v-for="c in correctionPoints"
                :key="c.key"
                class="point-row"
                :class="{ focused: focusKey === c.key }"
              >
                <!-- 左侧：选项（独立组件，点击选中/取消） -->
                <view
                  class="point-option"
                  :class="{ active: form.error.points.includes(c.key) }"
                  hover-class="pressed"
                  hover-stay-time="80"
                  role="checkbox"
                  :aria-checked="form.error.points.includes(c.key)"
                  :aria-label="c.label"
                  @tap="togglePoint(c.key)"
                >
                  <view class="point-option-icon">
                    <IconSvg :name="c.icon" :size="28" :color="form.error.points.includes(c.key) ? 'var(--color-primary)' : 'var(--text-tertiary)'" />
                  </view>
                  <text class="point-option-text">{{ c.label }}</text>
                  <IconSvg v-if="form.error.points.includes(c.key)" name="check" :size="24" color="var(--color-primary)" />
                </view>

                <!-- 右侧：编辑区（独立组件，选中后出现；已下架/图片属性也可输入文本） -->
                <view
                  v-if="form.error.points.includes(c.key)"
                  class="point-edit"
                  @tap.stop
                >
                  <input
                    v-model="form.error.correctValues[c.key]"
                    class="edit-input"
                    :class="{ 'input-error': fieldErrors[`error.correct.${c.key}`] }"
                    :placeholder="`${c.editPlaceholder}`"
                    maxlength="200"
                    :cursor-spacing="20"
                    :adjust-position="true"
                    @input="clearError(`error.correct.${c.key}`)"
                    @focus="focusKey = c.key"
                    @blur="focusKey = ''"
                  />
                  <text v-if="fieldErrors[`error.correct.${c.key}`]" class="field-error">{{ fieldErrors[`error.correct.${c.key}`] }}</text>
                </view>
              </view>
            </view>
            <text v-if="fieldErrors['error.points']" class="field-error">{{ fieldErrors['error.points'] }}</text>
          </view>

          <!-- 作证（选填）：图片 + 文本，仅选中问题后显示 -->
          <view v-if="form.error.points.length" class="evidence-box">
            <text class="evidence-title">作证</text>
            <view class="field">
              <text class="field-label">图片</text>
              <ImageUploader v-model="form.error.evidenceImages" :max="3" show-counter />
            </view>
            <view class="field">
              <text class="field-label">文本</text>
              <textarea
                v-model="form.error.evidenceText"
                class="content-input content-input-sm"
                placeholder="补充说明，比如照片里能看到啥"
                maxlength="500"
                :auto-height="true"
                :cursor-spacing="20"
                :adjust-position="true"
              />
            </view>
          </view>
        </template>
      </CardSection>

      <!-- 提交反馈（表单最下方，随内容滚动） -->
      <view class="submit-area">
        <AppButton :text="submitting ? '提交中…' : '提交反馈'" :loading="submitting" @press="submit" />
      </view>
    </scroll-view>

    <!-- ===== 底部选择器：位置（食堂 → 档口 两级联动，ListPickerSheet 单实例 locStep 切换） ===== -->
    <ListPickerSheet
      :open="locSheetOpen"
      :title="locStep === 'canteen' ? '选择食堂' : '选择档口'"
      :backable="locStep === 'stall'"
      :options="locOptions"
      row-style="plain"
      :selected-key="locSelectedKey"
      @close="closeLocationSheet"
      @back="locStep = 'canteen'"
      @select="onLocSelect"
    >
      <!-- 尾部「其他」自定义输入（仅选中「其他」时由默认槽承载） -->
      <view v-if="locCustomShown" class="pick-custom">
        <input
          :value="locCustomValue"
          class="pick-custom-input"
          :placeholder="locStep === 'canteen' ? '写一下食堂名' : '写一下档口名'"
          maxlength="50"
          :cursor-spacing="20"
          :adjust-position="true"
          @input="onLocCustomInput"
        />
      </view>
    </ListPickerSheet>

    <!-- ===== 底部选择器：楼层（1/2/3） ===== -->
    <ListPickerSheet
      :open="floorSheetOpen"
      title="选择楼层"
      :options="floorPickerOptions"
      row-style="plain"
      :selected-key="form.add.floor || null"
      @close="closeFloorSheet"
      @select="onFloorSelect"
    />

    <!-- ===== 底部选择器：菜品（搜索 + 列表 + 空态去补录） ===== -->
    <ListPickerSheet
      :open="dishSheetOpen"
      title="选择菜品"
      searchable
      search-placeholder="搜菜名 / 食堂"
      :search-initial="dishKeyword"
      :options="dishPickerOptions"
      row-style="plain"
      @close="closeDishSheet"
      @search="onDishSearchKw"
      @select="onDishPick"
    >
      <!-- 列表区内空态：无关键词引导 / 无结果「去补录一道」CTA -->
      <template #empty>
        <view v-if="dishSearched && !dishPickerOptions.length" class="pick-empty">
          <text class="pick-empty-text">没搜到「{{ dishKeyword }}」</text>
          <view
            class="pick-goto-add"
            hover-class="pressed"
            hover-stay-time="80"
            role="button"
            aria-label="去推荐菜品补录"
            @tap="gotoAdd"
          ><text class="pick-goto-add-text">去补录一道</text></view>
        </view>
        <view v-else-if="!dishKeyword" class="pick-empty">
          <text class="pick-empty-text">输入关键词搜索菜品</text>
        </view>
        <!-- 搜索进行中（防抖未回）/ 已有关键词但无结果外：留空 -->
      </template>
    </ListPickerSheet>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { submitFeedback } from '@/api/feedback'
import type { FeedbackSubmit } from '@/types/feedback'
import { searchDishes, getDishDetail } from '@/api/dish'
import type { Dish } from '@/types/dish'
import { getCanteensWithStalls } from '@/api/canteen'
import { backToHome } from '@/utils/nav'
import Header from '@/components/AppHeader.vue'
import AppButton from '@/components/AppButton.vue'
import CardSection from '@/components/CardSection.vue'
import IconSvg from '@/components/IconSvg.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import ListPickerSheet from '@/components/ListPickerSheet.vue'

// 返回：有返回栈时 navigateBack；无返回栈（redirectTo 直达）才 reLaunch 首页
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else backToHome()
}

// ---- ① 类型（3 类等宽卡片：左侧 icon + 右侧标题） ----
const types: { value: FeedbackSubmit['type']; label: string; desc: string; icon: string }[] = [
  { value: 'suggestion', label: '提个想法', desc: '建议 / 问题', icon: 'lightbulb' },
  { value: 'add', label: '推荐菜品', desc: '补录一道', icon: 'dish' },
  { value: 'error', label: '信息不对', desc: '纠错 / 下架', icon: 'report' },
]
const type = ref<FeedbackSubmit['type']>('suggestion')

// ---- ② 动态字段（各类型独立状态，切换保留，提交清空） ----
const form = reactive({
  suggestion: {
    sub: 'idea' as 'idea' | 'problem',
    text: '',
    images: [] as string[],
  },
  add: {
    name: '',
    price: '',
    canteen: '',
    canteenCustom: '',
    stallName: '',
    stallCustom: '',
    floor: '',
    images: [] as string[],
    description: '',
  },
  error: {
    dish: null as Dish | null,
    points: [] as string[],
    correctValues: {} as Record<string, string>,
    evidenceImages: [] as string[],
    evidenceText: '',
  },
})

// 「提个想法」细分
const suggestionSubs = [
  { value: 'idea' as const, label: '提建议' },
  { value: 'problem' as const, label: '报问题' },
]

// ---- ③ 信息不对：关联菜品搜索（底部弹窗） ----
const dishSheetOpen = ref(false)
const dishKeyword = ref('')
const dishCandidates = ref<Dish[]>([])
const dishSearched = ref(false)
/** 成功态自动返回定时器（⑨ scheduleAutoBack） */
let backTimer: ReturnType<typeof setTimeout> | null = null
/** 页面级一次性定时器注册表（markErrors 的 scrollIntoView 定位延迟）：onUnload 统一清理（P0 防越界访问） */
let pageTimers: ReturnType<typeof setTimeout>[] = []
onUnload(() => {
  if (backTimer) clearTimeout(backTimer)
  pageTimers.forEach((t) => clearTimeout(t))
  pageTimers = []
})

function openDishSheet() {
  dishSheetOpen.value = true
}

function closeDishSheet() {
  dishSheetOpen.value = false
}

/** 空态「去补录一道」：关闭弹窗并切到推荐菜品类型 */
function gotoAdd() {
  dishSheetOpen.value = false
  type.value = 'add'
}

/** ListPickerSheet 候选行（image/icon/文字）映射 */
const dishPickerOptions = computed(() =>
  dishCandidates.value.map((d) => ({
    key: String(d.id),
    label: d.name,
    sub: [d.canteen, d.stallName].filter(Boolean).join(' · '),
    image: d.image || '',
  })),
)

/** 搜索请求序号：快速输入/连续触发时丢弃过期响应，避免旧请求晚到覆盖新候选（竞态守卫，对齐 review.vue） */
let searchSeq = 0

/** 由 ListPickerSheet 内部防抖 emit('search', kw) 驱动；keyword 语义与迁移前一致 */
function onDishSearchKw(kw: string) {
  cancelAutoBack()
  dishKeyword.value = kw
  if (!kw.trim()) {
    dishSearched.value = false
    dishCandidates.value = []
    searchSeq++
    return
  }
  dishSearched.value = false
  const seq = ++searchSeq
  dishSearched.value = true
  searchDishes({ keyword: kw.trim(), page: 1, pageSize: 6 })
    .then((list) => {
      if (seq !== searchSeq) return // 已有更新的搜索发出，丢弃本次过期结果
      dishCandidates.value = list
    })
    .catch((err) => {
      if (seq !== searchSeq) return
      // 静默：请求失败不呈现任何占位，异常仅记录
      console.error('[feedback] 搜索菜品失败', err)
      dishCandidates.value = []
    })
}

function onDishPick(opt: { key: string }) {
  const d = dishCandidates.value.find((x) => String(x.id) === opt.key)
  if (d) selectDish(d)
}

function selectDish(d: Dish) {
  cancelAutoBack()
  form.error.dish = d
  dishKeyword.value = ''
  dishCandidates.value = []
  dishSearched.value = false
  dishSheetOpen.value = false
  clearError('error.dish')
}

function resetDish() {
  form.error.dish = null
  dishKeyword.value = ''
  dishCandidates.value = []
  dishSearched.value = false
}

function formatDishMeta(d: Dish) {
  const parts = [d.canteen, d.stallName].filter(Boolean)
  if (d.price > 0) parts.push(`¥${d.price}`)
  return parts.join(' · ') || '菜品'
}

// ---- ④ 信息不对：哪里不对（多选）+ 正确信息 ----
const correctionPoints = [
  { key: 'price', label: '价格不对', short: '价格', icon: 'price', editPlaceholder: '正确价格，如 0.00' },
  { key: 'name', label: '名字写错', short: '名字', icon: 'edit', editPlaceholder: '正确名字' },
  { key: 'location', label: '位置变了', short: '位置', icon: 'location', editPlaceholder: '正确位置，如：一食堂 · 面食窗口' },
  { key: 'attr', label: '图片 / 属性不对', short: '图片属性', icon: 'image', editPlaceholder: '哪里不对，如：辣度标错了' },
  { key: 'removed', label: '已下架', short: '已下架', icon: 'report', editPlaceholder: '补充下架说明（可选）' },
  { key: 'other', label: '其他', short: '其他', icon: 'comment', editPlaceholder: '还有啥问题' },
]

function togglePoint(key: string) {
  // 用户交互 = 取消自动返回
  cancelAutoBack()
  // 「已下架」与其他所有选项互斥：选中已下架清空其他；选中其他时取消已下架
  if (key === 'removed') {
    if (form.error.points.includes('removed')) {
      form.error.points = []
    } else {
      form.error.points = ['removed']
    }
  } else {
    const i = form.error.points.indexOf(key)
    if (i >= 0) {
      form.error.points.splice(i, 1)
    } else {
      form.error.points = form.error.points.filter(k => k !== 'removed')
      form.error.points.push(key)
      // 选中时若编辑区尚无内容，预填该菜品当前字段值，用户在此基础上改
      if (!(form.error.correctValues[key] || '').trim()) {
        form.error.correctValues[key] = dishPrevValues.value[key] || ''
      }
    }
  }
  clearError('error.points')
}

/** 该菜品当前字段值（编辑区未选中的只读展示，computed 预计算避免模板内函数调用） */
const dishPrevValues = computed<Record<string, string>>(() => {
  const d = form.error.dish
  const out: Record<string, string> = {}
  if (!d) return out
  out.price = d.price > 0 ? `¥${d.price}` : ''
  out.name = d.name || ''
  out.location = [d.canteen, d.stallName].filter(Boolean).join(' · ')
  const n = d.images?.length || 0
  out.attr = n > 0 ? `${n} 张图片` : ''
  return out
})

// ---- ⑤ 位置选择：ListPickerSheet 单实例两级联动（食堂 → 档口，含「其他」自定义） ----
const canteenTree = ref<any[]>([])
const locSheetOpen = ref(false)
const locStep = ref<'canteen' | 'stall'>('canteen')

/** 食堂显示名：选「其他」且有自定义名时显示自定义名 */
const displayCanteen = computed(() =>
  form.add.canteen === '其他' ? form.add.canteenCustom.trim() || '其他' : form.add.canteen,
)
/** 档口显示名：选「其他」且有自定义名时显示自定义名 */
const displayStall = computed(() =>
  form.add.stallName === '其他' ? form.add.stallCustom.trim() || '其他' : form.add.stallName,
)

/** 当前食堂下的档口原始列表 */
const currentStalls = computed<string[]>(() => {
  const c = canteenTree.value.find((x: any) => x.name === form.add.canteen)
  return (c?.stalls || []).map((s: any) => s.name as string)
})

/** ListPickerSheet 位置选项：食堂级(canteen icon) / 档口级(stall icon)，末尾追加「其他」(add icon) */
const locOptions = computed<{ key: string; label: string; icon: string }[]>(() => {
  const base =
    locStep.value === 'canteen'
      ? canteenTree.value.map((c: any) => ({ key: c.name as string, label: c.name as string, icon: 'canteen' }))
      : currentStalls.value.map((name) => ({ key: name, label: name, icon: 'stall' }))
  return [...base, { key: '其他', label: '其他', icon: 'add' }]
})

/** 当前高亮项 key：食堂/档口原始选中值（'' → 无高亮；'其他' 命中末尾项） */
const locSelectedKey = computed(() => (locStep.value === 'canteen' ? form.add.canteen || null : form.add.stallName || null))

/** 尾部「其他」自定义输入可见性：当前级已选中「其他」 */
const locCustomShown = computed(() =>
  locStep.value === 'canteen' ? form.add.canteen === '其他' : form.add.stallName === '其他',
)
const locCustomValue = computed(() => (locStep.value === 'canteen' ? form.add.canteenCustom : form.add.stallCustom))
function onLocCustomInput(e: any) {
  const v = e?.detail?.value ?? ''
  if (locStep.value === 'canteen') form.add.canteenCustom = v
  else form.add.stallCustom = v
}

/** 位置选项点击 → 转发到 pickCanteen/pickStall（保留 toggle 与档口联动清空语义） */
function onLocSelect(opt: { key: string; label: string }) {
  if (locStep.value === 'canteen') pickCanteen(opt.key)
  else pickStall(opt.key)
}

async function loadCanteens() {
  try {
    canteenTree.value = await getCanteensWithStalls()
  } catch {
    canteenTree.value = []
  }
}

function openLocationSheet(step: 'canteen' | 'stall') {
  locStep.value = step
  locSheetOpen.value = true
}

function closeLocationSheet() {
  locSheetOpen.value = false
}

function pickCanteen(name: string) {
  // 再次点击已选项：取消选择（含档口联动清空）
  if (form.add.canteen === name) {
    form.add.canteen = ''
    form.add.canteenCustom = ''
    form.add.stallName = ''
    form.add.stallCustom = ''
    return
  }
  form.add.canteen = name
  form.add.canteenCustom = ''
  // 切换食堂时清空旧档口
  form.add.stallName = ''
  form.add.stallCustom = ''
  if (name === '其他') {
    // A2：其他食堂 → 档口也直接进「其他」自定义输入，保持联动完整
    form.add.stallName = '其他'
    form.add.stallCustom = ''
    locStep.value = 'canteen'
    return
  }
  locStep.value = 'stall'
}

function pickStall(name: string) {
  if (form.add.stallName === name) {
    form.add.stallName = ''
    form.add.stallCustom = ''
    return
  }
  form.add.stallName = name
  form.add.stallCustom = ''
}

/** A1：档口行点击 —— 未选食堂时提示先选食堂 */
function onStallRowTap() {
  if (!displayCanteen.value) {
    uni.showToast({ title: '先选择食堂', icon: 'none' })
    return
  }
  openLocationSheet('stall')
}

// ---- ⑤.5 楼层选择：ListPickerSheet（1/2/3，icon 沿用原观感） ----
const floorSheetOpen = ref(false)
const floorList = ['1', '2', '3']

/** ListPickerSheet 楼层选项（label 显示「N 楼」，key 存原值用于高亮/回传） */
const floorPickerOptions = computed<{ key: string; label: string; icon: string }[]>(() =>
  floorList.map((f) => ({ key: f, label: `${f} 楼`, icon: 'canteen' })),
)

function openFloorSheet() {
  floorSheetOpen.value = true
}

function closeFloorSheet() {
  floorSheetOpen.value = false
}

function pickFloor(f: string) {
  // 再次点击已选项取消
  form.add.floor = form.add.floor === f ? '' : f
  clearError('add.floor')
  closeFloorSheet()
}

function onFloorSelect(opt: { key: string }) {
  pickFloor(opt.key)
}

// ---- ⑥ 字段级错误定位 ----
const fieldErrors = reactive<Record<string, string>>({})
const scrollIntoView = ref('')
/** 当前聚焦的纠错行 key（focus 时左侧选项加主色左边条） */
const focusKey = ref('')
/** 提交中状态（防连点 + AppButton loading 绑定） */
const submitting = ref(false)

function clearError(key: string) {
  delete fieldErrors[key]
  // 用户重新输入 = 取消自动返回
  cancelAutoBack()
}

function markErrors(errs: Record<string, string>) {
  Object.keys(fieldErrors).forEach(k => delete fieldErrors[k])
  const keys = Object.keys(errs)
  if (!keys.length) return
  keys.forEach(k => { fieldErrors[k] = errs[k] })
  const first = keys[0]
  const idMap: Record<string, string> = {
    'suggestion.text': 'f-sug-text',
    'add.name': 'f-add-name',
    'add.price': 'f-add-price',
    'add.floor': 'f-add-floor',
    'error.dish': 'f-dish',
    'error.points': 'f-point',
  }
  const target = idMap[first] || (first.startsWith('error.correct.') ? 'f-point' : '')
  scrollIntoView.value = ''
  const t = setTimeout(() => { scrollIntoView.value = target }, 50)
  pageTimers.push(t)
}

// ---- ⑧ 提交组装 ----
function resetForm() {
  form.suggestion.sub = 'idea'
  form.suggestion.text = ''
  form.suggestion.images = []
  form.add.name = ''
  form.add.price = ''
  form.add.canteen = ''
  form.add.canteenCustom = ''
  form.add.stallName = ''
  form.add.stallCustom = ''
  form.add.floor = ''
  form.add.images = []
  form.add.description = ''
  form.error.dish = null
  form.error.points = []
  form.error.correctValues = {}
  form.error.evidenceImages = []
  form.error.evidenceText = ''
  dishKeyword.value = ''
  dishCandidates.value = []
  dishSearched.value = false
  locSheetOpen.value = false
  floorSheetOpen.value = false
  dishSheetOpen.value = false
  focusKey.value = ''
}

async function submit() {
  if (submitting.value) return

  const t = type.value
  const errs: Record<string, string> = {}

  // 按类型动态必填校验（收集全部错误）
  if (t === 'suggestion') {
    if (!form.suggestion.text.trim()) errs['suggestion.text'] = '先写两句呗'
  } else if (t === 'add') {
    if (!form.add.name.trim()) errs['add.name'] = '菜名叫啥？填一下'
    if (!form.add.floor.trim()) errs['add.floor'] = '楼层必填'
    // A4：价格格式校验（填了就必须是合法数字）
    const priceStr = form.add.price.trim()
    if (priceStr) {
      const priceNum = Number(priceStr)
      if (Number.isNaN(priceNum) || priceNum <= 0 || priceNum > 9999) {
        errs['add.price'] = '价格要像 12.5 这样'
      }
    }
  } else if (t === 'error') {
    if (!form.error.dish) errs['error.dish'] = '先选一道菜'
    if (!form.error.points.length) {
      errs['error.points'] = '至少选一项'
    } else {
      for (const key of form.error.points) {
        // attr/removed 文本为选填；其余需填正确信息
        if (key !== 'removed' && key !== 'attr' && !(form.error.correctValues[key] || '').trim()) {
          errs[`error.correct.${key}`] = '填一下正确信息'
        }
      }
    }
  }

  if (Object.keys(errs).length) {
    markErrors(errs)
    uni.showToast({ title: `还有 ${Object.keys(errs).length} 项没填`, icon: 'none' })
    return
  }

  // 组装 content（结构化文本）
  let content = ''
  const images: string[] = []
  let relatedType: string | undefined
  // #6 修复：relatedId 与 relatedType 成对赋值，仅在 error 分支设置。
  // 原实现 unconditionally 取 form.error.dish?.id，切到 suggestion/add 类型提交时残留
  // error 的 relatedId 而 relatedType 为 undefined，造成契约不一致。
  let relatedId: number | undefined

  if (t === 'suggestion') {
    content = form.suggestion.text.trim()
    images.push(...form.suggestion.images)
  } else if (t === 'add') {
    const parts = [`【新增菜品】${form.add.name.trim()}`]
    if (form.add.price.trim()) parts.push(`价格：${form.add.price.trim()}元`)
    // 「其他」时取自定义值
    const canteen = form.add.canteen === '其他' ? form.add.canteenCustom.trim() : form.add.canteen
    const stall = form.add.stallName === '其他' ? form.add.stallCustom.trim() : form.add.stallName
    const loc = [canteen, stall].filter(Boolean).join('·')
    if (loc) parts.push(`位置：${loc}`)
    if (form.add.floor.trim()) parts.push(`楼层：${form.add.floor.trim()}`)
    if (form.add.description.trim()) parts.push(`描述：${form.add.description.trim()}`)
    content = parts.join('\n')
    images.push(...form.add.images)
    // add 为新增菜品，无关联已有对象，不传 relatedType
  } else if (t === 'error') {
    const parts: string[] = []
    for (const key of form.error.points) {
      const c = correctionPoints.find(x => x.key === key)
      if (!c) continue
      const text = (form.error.correctValues[key] || '').trim()
      if (key === 'removed') {
        parts.push(text ? `【已下架】\n说明：${text}` : '【已下架】')
        continue
      }
      parts.push(text ? `【${c.label}】\n说明：${text}` : `【${c.label}】`)
    }
    content = parts.join('\n')
    if (form.error.evidenceText.trim()) content += `\n作证：${form.error.evidenceText.trim()}`
    images.push(...form.error.evidenceImages)
    relatedType = 'dish'
    relatedId = form.error.dish?.id
  }

  if (content.length > 1000) { uni.showToast({ title: '内容不能超过1000字', icon: 'none' }); return }

  submitting.value = true
  try {
    await submitFeedback({
      type: t,
      content,
      images: images.length ? images : undefined,
      relatedType,
      relatedId,
    })
    uni.showToast({ title: '收到！谢谢你', icon: 'success' })
    resetForm()
    // 成功态双态：2 秒后无输入则自动返回来源页
    scheduleAutoBack()
  } catch (e: any) {
    uni.showToast({ title: e.message || '没发出去，再试一次', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

// ---- ⑨ 成功态自动返回（用户 2 秒内无输入则 navigateBack） ----
function scheduleAutoBack() {
  if (backTimer) clearTimeout(backTimer)
  backTimer = setTimeout(() => {
    if (getCurrentPages().length > 1) uni.navigateBack()
    else backToHome()
  }, 2000)
}

function cancelAutoBack() {
  if (backTimer) {
    clearTimeout(backTimer)
    backTimer = null
  }
}

// ---- 预选菜品（首页「反馈菜品」入口带 object/name/id） ----
onLoad(async (opts?: Record<string, string>) => {
  loadCanteens()
  const hasDishRef = opts?.object === 'dish' || opts?.name || opts?.id
  // 「反馈菜品」入口：预置「信息不对」+ 关联菜品；类型仍可自由切换
  if (hasDishRef) type.value = 'error'
  if (opts?.id) {
    const id = Number(opts.id)
    if (!Number.isNaN(id)) {
      try {
        const d = await getDishDetail(id)
        if (d) form.error.dish = d
      } catch { /* 忽略：详情拉取失败则进入手动搜索 */ }
    }
  } else if (opts?.name) {
    // 仅有菜名：预填搜索框并自动检索，用户点选确认
    onDishSearchKw(opts.name)
  }
})
</script>

<style scoped>
.feedback-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; background: var(--bg-page); }

/* 主滚动区：底部预留固定底栏高度 + safe-area（防遮挡） */
.scroll-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  /* 底部安全区避让：滚动到底时不遮挡提交区 */
  padding-bottom: env(safe-area-inset-bottom);
  box-sizing: border-box;
}

/* ===== 类型图标卡片（三列等宽：左侧 icon + 右侧两行两字） ===== */
.type-row {
  display: flex;
  gap: var(--spacing-sm);
  padding: var(--spacing-md) var(--spacing-lg) var(--spacing-xs);
}
.type-card {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  background: var(--bg-card);
  border: 2rpx solid var(--border-color);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-card);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.type-card.active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
}
.type-icon {
  width: 64rpx;
  height: 64rpx;
  flex-shrink: 0;
  border-radius: var(--radius-icon);
  background: var(--bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.type-card.active .type-icon { background: var(--bg-card); }
.type-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.type-line { font-size: var(--font-small); font-weight: var(--weight-semibold); color: var(--text-primary); line-height: 1.3; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.type-desc { font-size: var(--font-tiny); color: var(--color-primary); line-height: 1.3; }

/* ===== 推荐菜品字段分组（仅靠间距分区） ===== */
.form-group { margin-bottom: var(--spacing-lg); }
.form-group:last-child { margin-bottom: 0; }

/* ===== 字段通用 ===== */
.field { margin-bottom: var(--spacing-md); }
.field:last-child { margin-bottom: 0; }
.field-label { display: block; font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-secondary); margin-bottom: var(--spacing-xs); }
.req { color: var(--color-error); margin-left: var(--spacing-2xs); }
.field-gap { height: var(--spacing-sm); }

/* 同一行双字段（菜名+价格 / 食堂+楼层） */
.row-fields { display: flex; gap: var(--spacing-sm); }
.row-fields .col { flex: 1; min-width: 0; }

.content-input {
  width: 100%;
  min-height: 220rpx;
  font-size: var(--font-body);
  color: var(--text-primary);
  line-height: 1.6;
  padding: var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-btn);
  box-sizing: border-box;
  border: 2rpx solid transparent;
}
.content-input-sm { min-height: 120rpx; }
.field-input {
  width: 100%;
  height: 88rpx;
  background: var(--bg-input);
  border-radius: var(--radius-btn);
  padding: 0 var(--spacing-md);
  font-size: var(--font-body);
  color: var(--text-primary);
  box-sizing: border-box;
  border: 2rpx solid transparent;
}
/* 字段级错误：红描边 + 错误文案 */
.input-error { border-color: var(--color-error); }
.field-error {
  display: block;
  margin-top: var(--spacing-xs);
  font-size: var(--font-tiny);
  color: var(--color-error);
}
.counter { display: block; text-align: right; font-size: var(--font-aux); color: var(--text-tertiary); margin-top: var(--spacing-xs); font-variant-numeric: tabular-nums; }

/* 细分 chips（提建议/报问题） */
.sub-row { display: flex; gap: var(--spacing-sm); margin-bottom: var(--spacing-md); }
.sub-chip {
  flex: 1;
  min-height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-sm) var(--spacing-lg);
  border-radius: var(--radius-btn);
  background: var(--bg-input);
  border: 2rpx solid var(--border-color);
  -webkit-tap-highlight-color: transparent;
}
.sub-chip.active { background: var(--color-primary-soft); border-color: var(--color-primary); }
.sub-text { font-size: var(--font-body); color: var(--text-secondary); font-weight: var(--weight-medium); }
.sub-chip.active .sub-text { color: var(--color-primary); font-weight: var(--weight-semibold); }

/* 食堂/档口 picker 行（并排紧凑） */
.picker-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-xs);
  height: 88rpx;
  padding: 0 var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-btn);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
/* A1：未选食堂时档口行禁用态 */
.picker-row.disabled { opacity: 0.5; }
.picker-value { font-size: var(--font-body); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.picker-value.placeholder { color: var(--text-tertiary); }

/* B2：作证区（仅选中问题后显示） */
.evidence-box {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-md);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
}
.evidence-title {
  display: block;
  font-size: var(--font-tiny);
  font-weight: var(--weight-regular);
  color: var(--text-tertiary);
  margin-bottom: var(--spacing-sm);
}

/* ===== 关联菜品：摘要卡 + 搜索选择器 ===== */
.dish-linked {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
}
.dish-thumb {
  width: 96rpx;
  height: 96rpx;
  border-radius: var(--radius-icon);
  background: var(--bg-placeholder);
  flex-shrink: 0;
}
.dish-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.dish-name { font-size: var(--font-body); font-weight: var(--weight-semibold); color: var(--text-primary); }
.dish-meta { font-size: var(--font-tiny); color: var(--text-tertiary); }
.dish-change {
  flex-shrink: 0;
  padding: var(--spacing-xs) var(--spacing-sm);
  background: var(--bg-card);
  border-radius: var(--radius-tag);
  -webkit-tap-highlight-color: transparent;
}
.dish-change-text { font-size: var(--font-aux); color: var(--text-secondary); }

/* ===== ListPickerSheet 尾部「其他」自定义输入（默认槽承载） ===== */
.pick-custom { padding: var(--spacing-sm) var(--spacing-md) var(--spacing-md); box-sizing: border-box; }
.pick-custom-input {
  width: 100%;
  height: 76rpx;
  background: var(--bg-input);
  border-radius: var(--radius-btn);
  padding: 0 var(--spacing-md);
  font-size: var(--font-body);
  color: var(--text-primary);
  box-sizing: border-box;
  border: 2rpx solid var(--color-primary);
}

/* ===== ListPickerSheet 列表空态（菜品搜索引导 / 无结果去补录 CTA） ===== */
.pick-empty { display: flex; flex-direction: column; align-items: center; gap: var(--spacing-md); }
.pick-empty-text { font-size: var(--font-aux); color: var(--text-tertiary); }
.pick-goto-add {
  min-width: 200rpx;
  height: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--spacing-lg);
  background: var(--color-primary);
  border-radius: var(--radius-btn);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.pick-goto-add-text { font-size: var(--font-small); color: var(--bg-card); font-weight: var(--weight-semibold); }

/* ===== 哪里不对：每项一行（左侧选项 + 右侧编辑区，不嵌套） ===== */
.point-list { display: flex; flex-direction: column; gap: var(--spacing-sm); }
.point-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding-left: var(--spacing-xs);
  border-left: 6rpx solid transparent;
  border-radius: var(--radius-tag);
  box-sizing: border-box;
}
.point-row.focused {
  border-left-color: var(--color-primary);
  background: var(--bg-card);
}
/* 左侧：选项 */
.point-option {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  min-height: 76rpx;
  padding: 0 var(--spacing-md);
  background: var(--bg-input);
  border: 2rpx solid var(--border-color);
  border-radius: var(--radius-btn);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.point-option.active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
}
.point-option-icon {
  width: 48rpx;
  height: 48rpx;
  flex-shrink: 0;
  border-radius: var(--radius-icon);
  background: var(--bg-card);
  display: flex;
  align-items: center;
  justify-content: center;
}
.point-option-text { font-size: var(--font-small); color: var(--text-tertiary); font-weight: var(--weight-regular); white-space: nowrap; }
.point-option.active .point-option-text { font-size: var(--font-body); color: var(--color-primary); font-weight: var(--weight-semibold); }
/* 右侧：编辑区（独立） */
.point-edit { flex: 1; min-width: 0; }
.edit-input {
  width: 100%;
  height: 68rpx;
  background: var(--bg-input);
  border-radius: var(--radius-btn);
  padding: 0 var(--spacing-md);
  font-size: var(--font-small);
  color: var(--text-primary);
  box-sizing: border-box;
  border: 2rpx solid var(--color-primary);
}
.edit-input.input-error { border-color: var(--color-error); }



/* ===== 提交反馈（表单最下方，随内容滚动，非固定） ===== */
.submit-area {
  padding: var(--spacing-md) var(--spacing-lg) var(--spacing-lg);
}
/* 滚动区底部留白（配合固定底栏） */


</style>
