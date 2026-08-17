<template>
  <view class="page feedback-page" :class="{ 'theme-dark': theme.isDark }">
    <Header title="意见反馈" @back="goBack" />

    <!-- 轻量单视图 · 动态表单（2026-08-17 重设计：三类型等宽卡片 + 结构化字段，操作顺手） -->
    <scroll-view class="scroll-wrap" scroll-y :scroll-into-view="scrollIntoView" :scroll-with-animation="true">
      <!-- 类型图标卡片（三列等宽铺满，选中即切换表单） -->
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
            <IconSvg :name="t.icon" :size="40" :color="type === t.value ? 'var(--color-primary)' : 'var(--text-tertiary)'" />
          </view>
          <view class="type-copy">
            <text class="type-name">{{ t.label }}</text>
            <text class="type-desc">{{ t.desc }}</text>
          </view>
          <view v-if="hasDraft(t.value)" class="type-draft" aria-hidden="true" />
        </view>
      </view>

      <!-- 分区标题 + 草稿提示 -->
      <view class="section-head">
        <SectionTitle :title="currentTypeLabel" noMargin />
        <text v-if="hasDraft(type)" class="draft-tip">已填写，可稍后继续</text>
      </view>

      <!-- 动态字段区（结构化字段补齐，按类型独立状态） -->
      <CardSection>
        <!-- 提个想法（含 App 问题细分） -->
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
            <text class="counter">{{ form.suggestion.text.length }}/1000</text>
            <text v-if="fieldErrors['suggestion.text']" class="field-error">{{ fieldErrors['suggestion.text'] }}</text>
          </view>

          <!-- 报问题：追加操作步骤 + 截图 -->
          <template v-if="form.suggestion.sub === 'problem'">
            <view class="field">
              <text class="field-label">操作步骤<text class="opt">（选填）</text></text>
              <textarea
                v-model="form.suggestion.steps"
                class="content-input content-input-sm"
                placeholder="怎么复现的？"
                maxlength="300"
                :auto-height="true"
                :cursor-spacing="20"
                :adjust-position="true"
              />
            </view>
            <view class="field">
              <text class="field-label">截图<text class="opt">（选填）</text></text>
              <ImageUploader v-model="form.suggestion.images" :max="3" show-counter />
            </view>
          </template>
        </template>

        <!-- 推荐菜品（菜品详情结构化字段，菜名必填其余选填） -->
        <template v-else-if="type === 'add'">
          <view class="form-group">
            <text class="group-title">基本信息</text>
            <view class="field">
              <text class="field-label">菜名叫啥？<text class="req">*</text></text>
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

            <view class="field">
              <text class="field-label">价格（元）<text class="opt">（选填）</text></text>
              <input
                v-model="form.add.price"
                class="field-input"
                type="digit"
                placeholder="0.00"
                :cursor-spacing="20"
                :adjust-position="true"
              />
            </view>
          </view>

          <view class="form-group">
            <text class="group-title">在哪吃</text>
            <view class="field">
              <picker :range="canteenNames" @change="onCanteenChange">
                <view class="picker-row" hover-class="pressed" hover-stay-time="80">
                  <view class="picker-icon"><IconSvg name="canteen" :size="28" color="var(--text-tertiary)" /></view>
                  <text class="picker-value" :class="{ placeholder: !form.add.canteen }">{{ form.add.canteen || '请选择食堂' }}</text>
                  <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" />
                </view>
              </picker>
              <picker :range="stallNames" :disabled="!form.add.canteen" @change="onStallChange">
                <view class="picker-row" hover-class="pressed" hover-stay-time="80">
                  <view class="picker-icon"><IconSvg name="stall" :size="28" color="var(--text-tertiary)" /></view>
                  <text class="picker-value" :class="{ placeholder: !form.add.stallName }">{{ form.add.stallName || '请选择档口' }}</text>
                  <IconSvg name="arrow" :size="28" color="var(--text-tertiary)" />
                </view>
              </picker>
            </view>
          </view>

          <view class="form-group">
            <text class="group-title">口味属性</text>
            <view class="opt-group">
              <text class="opt-caption">辣度</text>
              <view class="chip-row">
                <view
                  v-for="o in spiceOptions"
                  :key="o.value"
                  class="sub-chip sub-chip-sm"
                  :class="{ active: form.add.spiceLevel === o.value }"
                  hover-class="pressed"
                  hover-stay-time="80"
                  role="radio"
                  :aria-checked="form.add.spiceLevel === o.value"
                  @tap="form.add.spiceLevel = o.value"
                >{{ o.label }}</view>
              </view>
            </view>
            <view class="opt-group">
              <text class="opt-caption">分量</text>
              <view class="chip-row">
                <view
                  v-for="o in portionOptions"
                  :key="o.value"
                  class="sub-chip sub-chip-sm"
                  :class="{ active: form.add.portion === o.value }"
                  hover-class="pressed"
                  hover-stay-time="80"
                  role="radio"
                  :aria-checked="form.add.portion === o.value"
                  @tap="form.add.portion = o.value"
                >{{ o.label }}</view>
              </view>
            </view>
            <view class="opt-group">
              <text class="opt-caption">供应时段</text>
              <view class="chip-row">
                <view
                  v-for="o in servePeriodOptions"
                  :key="o.value"
                  class="sub-chip sub-chip-sm"
                  :class="{ active: form.add.servePeriod.includes(o.value) }"
                  hover-class="pressed"
                  hover-stay-time="80"
                  role="checkbox"
                  :aria-checked="form.add.servePeriod.includes(o.value)"
                  @tap="toggleServePeriod(o.value)"
                >{{ o.label }}</view>
              </view>
            </view>
            <view class="opt-group">
              <text class="opt-caption">标签</text>
              <view class="chip-row">
                <view
                  v-for="o in tagOptions"
                  :key="o.value"
                  class="sub-chip sub-chip-sm"
                  :class="{ active: form.add.tags.includes(o.value) }"
                  hover-class="pressed"
                  hover-stay-time="80"
                  role="checkbox"
                  :aria-checked="form.add.tags.includes(o.value)"
                  @tap="toggleTag(o.value)"
                >{{ o.label }}</view>
              </view>
            </view>
          </view>

          <view class="form-group">
            <text class="group-title">图片与描述</text>
            <view class="field">
              <text class="field-label">菜品图片<text class="opt">（选填）</text></text>
              <ImageUploader v-model="form.add.images" :max="3" show-counter />
            </view>

            <view class="field">
              <text class="field-label">一句话描述<text class="opt">（选填）</text></text>
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

        <!-- 信息不对（强制关联菜品 + 字段级纠错） -->
        <template v-else-if="type === 'error'">
          <!-- 关联菜品（必选） -->
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

            <!-- 未选中：搜索选择器 -->
            <view v-else class="dish-search">
              <view class="search-bar">
                <IconSvg name="search" :size="30" color="var(--text-tertiary)" />
                <input
                  v-model="dishKeyword"
                  class="search-input"
                  placeholder="搜菜名 / 食堂"
                  confirm-type="search"
                  :cursor-spacing="20"
                  :adjust-position="true"
                  @input="onDishInput"
                  @confirm="onDishSearch"
                />
              </view>
              <view v-if="dishCandidates.length" class="candidate-list">
                <view
                  v-for="d in dishCandidates"
                  :key="d.id"
                  class="candidate-item"
                  hover-class="pressed"
                  hover-stay-time="80"
                  role="button"
                  :aria-label="`选择 ${d.name}`"
                  @tap="selectDish(d)"
                >
                  <image class="candidate-thumb" :src="d.image || ''" mode="aspectFill" />
                  <view class="candidate-main">
                    <text class="candidate-name">{{ d.name }}</text>
                    <text class="candidate-meta">{{ d.canteen }} · {{ d.stallName }}</text>
                  </view>
                  <IconSvg name="check" :size="28" color="var(--text-tertiary)" />
                </view>
              </view>
              <view
                v-if="dishSearched && !dishLoading && !dishCandidates.length"
                class="dish-empty"
                hover-class="pressed"
                hover-stay-time="80"
                role="button"
                aria-label="去推荐菜品补录"
                @tap="type = 'add'"
              >
                <text class="dish-empty-text">没找到？去「推荐菜品」补录一道</text>
                <IconSvg name="arrow" :size="24" color="var(--color-primary)" />
              </view>
            </view>

            <text v-if="fieldErrors['error.dish']" class="field-error">{{ fieldErrors['error.dish'] }}</text>
          </view>

          <!-- 字段级纠错单选（竖排，选中展开对应面板） -->
          <view class="field">
            <text class="field-label">哪儿不对？</text>
            <view class="correction-list" role="radiogroup" aria-label="纠错字段">
              <view
                v-for="c in correctionPoints"
                :key="c.key"
                class="correction-row"
                :class="{ active: form.error.point === c.key }"
                hover-class="pressed"
                hover-stay-time="80"
                role="radio"
                :aria-checked="form.error.point === c.key"
                :aria-label="c.label"
                @tap="form.error.point = c.key"
              >
                <view class="correction-icon">
                  <IconSvg :name="c.icon" :size="30" :color="form.error.point === c.key ? 'var(--color-primary)' : 'var(--text-tertiary)'" />
                </view>
                <text class="correction-text">{{ c.label }}</text>
                <view v-if="form.error.point === c.key" class="correction-check">
                  <IconSvg name="check" :size="28" color="var(--color-primary)" />
                </view>
              </view>
            </view>
          </view>

          <!-- 折叠面板：非「已下架」→ 正确信息 + 说明 -->
          <view v-if="form.error.point && form.error.point !== 'removed'" class="panel enter-up">
            <view class="field">
              <text class="field-label">{{ pointFieldLabel }}<text class="opt">（选填）</text></text>
              <input
                v-model="form.error.correctValue"
                class="field-input"
                :placeholder="pointFieldPlaceholder"
                maxlength="200"
                :cursor-spacing="20"
                :adjust-position="true"
              />
            </view>
            <view class="field">
              <text class="field-label">具体哪儿不对？<text class="req">*</text></text>
              <textarea
                id="f-err-text"
                v-model="form.error.text"
                class="content-input"
                :class="{ 'input-error': fieldErrors['error.text'] }"
                placeholder="说清楚点"
                maxlength="1000"
                :auto-height="true"
                :cursor-spacing="20"
                :adjust-position="true"
                @input="clearError('error.text')"
              />
              <text class="counter">{{ form.error.text.length }}/1000</text>
              <text v-if="fieldErrors['error.text']" class="field-error">{{ fieldErrors['error.text'] }}</text>
            </view>
          </view>

          <!-- 折叠面板：「已下架」→ 作证照片 + 作证文本 -->
          <view v-else-if="form.error.point === 'removed'" class="panel enter-up">
            <view class="field">
              <text class="field-label">作证照片<text class="opt">（选填）</text></text>
              <ImageUploader v-model="form.error.evidenceImages" :max="3" show-counter />
            </view>
            <view class="field">
              <text class="field-label">作证信息<text class="opt">（选填）</text></text>
              <textarea
                v-model="form.error.evidenceText"
                class="content-input content-input-sm"
                placeholder="还能补点啥"
                maxlength="500"
                :auto-height="true"
                :cursor-spacing="20"
                :adjust-position="true"
              />
            </view>
          </view>
        </template>
      </CardSection>

      <view class="scroll-space" />
    </scroll-view>

    <!-- 固定底栏：提交 + 匿名声明（safe-area 避让） -->
    <view class="action-bar">
      <AppButton :text="submitting ? '稍等…' : '说出去'" :loading="submitting" @click="submit" />
      <text class="anonymous-tip">匿名提交 · 不记账号</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useThemeStore } from '@/stores/theme'
import { submitFeedback } from '@/api/feedback'
import type { FeedbackSubmit } from '@/types/feedback'
import { searchDishes, getDishDetail } from '@/api/dish'
import type { Dish } from '@/types/dish'
import { getCanteensWithStalls } from '@/api/canteen'
import { backToHome } from '@/utils/nav'
import Header from '@/components/header.vue'
import AppButton from '@/components/AppButton.vue'
import CardSection from '@/components/CardSection.vue'
import SectionTitle from '@/components/SectionTitle.vue'
import IconSvg from '@/components/IconSvg.vue'
import ImageUploader from '@/components/ImageUploader.vue'

const theme = useThemeStore()

// 返回：有返回栈时 navigateBack；无返回栈（redirectTo 直达）才 reLaunch 首页
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else backToHome()
}

// ---- ① 类型（3 类，图标卡片，默认「提个想法」） ----
const types: { value: FeedbackSubmit['type']; label: string; desc: string; icon: string }[] = [
  { value: 'suggestion', label: '提个想法', desc: '建议 / 问题都行', icon: 'lightbulb' },
  { value: 'add', label: '推荐菜品', desc: '补录一道菜', icon: 'dish' },
  { value: 'error', label: '信息不对', desc: '纠错 / 下架', icon: 'report' },
]
const type = ref<FeedbackSubmit['type']>('suggestion')

const currentTypeLabel = computed(() => types.find(t => t.value === type.value)?.label || '')

// ---- ② 动态字段（各类型独立状态，切换保留，提交清空） ----
const form = reactive({
  suggestion: {
    sub: 'idea' as 'idea' | 'problem',
    text: '',
    steps: '',
    images: [] as string[],
  },
  add: {
    name: '',
    price: '',
    canteen: '',
    stallName: '',
    spiceLevel: -1 as number,
    portion: -1 as number,
    servePeriod: [] as string[],
    tags: [] as string[],
    images: [] as string[],
    description: '',
  },
  error: {
    dish: null as Dish | null,
    point: '',
    correctValue: '',
    text: '',
    evidenceImages: [] as string[],
    evidenceText: '',
  },
})

// 「提个想法」细分
const suggestionSubs = [
  { value: 'idea' as const, label: '提建议' },
  { value: 'problem' as const, label: '报问题' },
]

// 口味属性选项（对齐 Dish 枚举）
const spiceOptions = [
  { value: 0, label: '不辣' },
  { value: 1, label: '微辣' },
  { value: 2, label: '中辣' },
  { value: 3, label: '重辣' },
]
const portionOptions = [
  { value: 0, label: '小份' },
  { value: 1, label: '中份' },
  { value: 2, label: '大份' },
]
const servePeriodOptions = [
  { value: 'breakfast', label: '早餐' },
  { value: 'lunch', label: '午餐' },
  { value: 'dinner', label: '晚餐' },
  { value: 'midnight', label: '夜宵' },
]
const tagOptions = [
  { value: 'recommended', label: '必吃' },
  { value: 'signature', label: '招牌' },
  { value: 'daily', label: '日常' },
  { value: 'halal', label: '清真' },
]

// ---- ③ 信息不对：关联菜品搜索 ----
const dishKeyword = ref('')
const dishCandidates = ref<Dish[]>([])
const dishLoading = ref(false)
const dishSearched = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

function onDishInput() {
  dishSearched.value = false
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(onDishSearch, 300)
}

async function onDishSearch() {
  const kw = dishKeyword.value.trim()
  if (!kw) {
    dishCandidates.value = []
    dishSearched.value = false
    return
  }
  dishLoading.value = true
  dishSearched.value = true
  try {
    dishCandidates.value = await searchDishes({ keyword: kw, page: 1, pageSize: 6 })
  } catch {
    dishCandidates.value = []
  } finally {
    dishLoading.value = false
  }
}

function selectDish(d: Dish) {
  form.error.dish = d
  dishKeyword.value = ''
  dishCandidates.value = []
  dishSearched.value = false
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

// ---- ④ 信息不对：字段级纠错点 ----
const correctionPoints = [
  { key: 'price', label: '价格不对', icon: 'price' },
  { key: 'name', label: '名字写错', icon: 'edit' },
  { key: 'location', label: '位置变了', icon: 'location' },
  { key: 'attr', label: '图片 / 属性不对', icon: 'image' },
  { key: 'removed', label: '已下架', icon: 'report' },
  { key: 'other', label: '其他', icon: 'comment' },
]

const pointFieldLabel = computed(() => {
  const map: Record<string, string> = {
    price: '正确价格',
    name: '正确名字',
    location: '正确的食堂 / 档口',
    attr: '正确的图片或属性',
    other: '补充说明',
  }
  return map[form.error.point] || ''
})
const pointFieldPlaceholder = computed(() => {
  const map: Record<string, string> = {
    price: '0.00',
    name: '写对的名字',
    location: '如：一食堂 · 面食窗口',
    attr: '补充正确的图片 / 辣度 / 分量等',
    other: '还有啥问题',
  }
  return map[form.error.point] || ''
})

// ---- ⑤ 食堂 / 档口选择（推荐菜品） ----
const canteenTree = ref<any[]>([])
const canteenNames = computed(() => canteenTree.value.map(c => c.name))
const stallNames = ref<string[]>([])

async function loadCanteens() {
  try {
    canteenTree.value = await getCanteensWithStalls()
  } catch {
    canteenTree.value = []
  }
}

function onCanteenChange(e: any) {
  const name = canteenNames.value[e.detail.value]
  form.add.canteen = name
  form.add.stallName = ''
  const c = canteenTree.value.find(x => x.name === name)
  stallNames.value = (c?.stalls || []).map((s: any) => s.name)
}

function onStallChange(e: any) {
  form.add.stallName = stallNames.value[e.detail.value] || ''
}

function toggleServePeriod(v: string) {
  const i = form.add.servePeriod.indexOf(v)
  if (i >= 0) form.add.servePeriod.splice(i, 1)
  else form.add.servePeriod.push(v)
}

function toggleTag(v: string) {
  const i = form.add.tags.indexOf(v)
  if (i >= 0) form.add.tags.splice(i, 1)
  else form.add.tags.push(v)
}

// ---- ⑥ 字段级错误定位 ----
const fieldErrors = reactive<Record<string, string>>({})
const scrollIntoView = ref('')

function clearError(key: string) {
  delete fieldErrors[key]
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
    'error.dish': 'f-dish',
    'error.text': 'f-err-text',
  }
  scrollIntoView.value = ''
  setTimeout(() => { scrollIntoView.value = idMap[first] || '' }, 50)
}

// ---- ⑦ 草稿检测（类型卡片「已填」徽标 + 分区标题提示） ----
function hasDraft(t: FeedbackSubmit['type']) {
  if (t === 'suggestion') {
    return !!(form.suggestion.text.trim() || form.suggestion.steps.trim() || form.suggestion.images.length)
  }
  if (t === 'add') {
    const a = form.add
    return !!(a.name.trim() || a.price.trim() || a.canteen || a.stallName
      || a.spiceLevel >= 0 || a.portion >= 0 || a.servePeriod.length || a.tags.length
      || a.images.length || a.description.trim())
  }
  if (t === 'error') {
    const e = form.error
    return !!(e.dish || e.point || e.correctValue.trim() || e.text.trim()
      || e.evidenceImages.length || e.evidenceText.trim())
  }
  return false
}

// ---- ⑧ 提交组装 ----
function resetForm() {
  form.suggestion.sub = 'idea'
  form.suggestion.text = ''
  form.suggestion.steps = ''
  form.suggestion.images = []
  form.add.name = ''
  form.add.price = ''
  form.add.canteen = ''
  form.add.stallName = ''
  form.add.spiceLevel = -1
  form.add.portion = -1
  form.add.servePeriod = []
  form.add.tags = []
  form.add.images = []
  form.add.description = ''
  form.error.dish = null
  form.error.point = ''
  form.error.correctValue = ''
  form.error.text = ''
  form.error.evidenceImages = []
  form.error.evidenceText = ''
  dishKeyword.value = ''
  dishCandidates.value = []
  dishSearched.value = false
}

function spiceLabel(v: number) {
  return spiceOptions.find(o => o.value === v)?.label || ''
}
function portionLabel(v: number) {
  return portionOptions.find(o => o.value === v)?.label || ''
}

async function submit() {
  if (submitting.value) return

  const t = type.value
  const errs: Record<string, string> = {}

  // 按类型动态必填校验（收集全部错误，非首个即返回）
  if (t === 'suggestion') {
    if (!form.suggestion.text.trim()) errs['suggestion.text'] = '先写两句呗'
  } else if (t === 'add') {
    if (!form.add.name.trim()) errs['add.name'] = '菜名叫啥？填一下'
  } else if (t === 'error') {
    if (!form.error.dish) errs['error.dish'] = '先选一道菜'
    if (form.error.point && form.error.point !== 'removed' && !form.error.text.trim()) {
      errs['error.text'] = '具体哪儿不对？写一下'
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
  const relatedId = form.error.dish?.id

  if (t === 'suggestion') {
    content = form.suggestion.text.trim()
    if (form.suggestion.sub === 'problem') {
      if (form.suggestion.steps.trim()) content += `\n操作步骤：${form.suggestion.steps.trim()}`
      images.push(...form.suggestion.images)
    }
  } else if (t === 'add') {
    const parts = [`【新增菜品】${form.add.name.trim()}`]
    if (form.add.price.trim()) parts.push(`价格：${form.add.price.trim()}元`)
    const loc = [form.add.canteen, form.add.stallName].filter(Boolean).join('·')
    if (loc) parts.push(`位置：${loc}`)
    const attr: string[] = []
    if (form.add.spiceLevel >= 0) attr.push(`辣度：${spiceLabel(form.add.spiceLevel)}`)
    if (form.add.portion >= 0) attr.push(`分量：${portionLabel(form.add.portion)}`)
    if (form.add.servePeriod.length) attr.push(`供应时段：${form.add.servePeriod.map(v => servePeriodOptions.find(o => o.value === v)?.label || v).join('、')}`)
    if (form.add.tags.length) attr.push(`标签：${form.add.tags.map(v => tagOptions.find(o => o.value === v)?.label || v).join('、')}`)
    if (attr.length) parts.push(attr.join('；'))
    if (form.add.description.trim()) parts.push(`描述：${form.add.description.trim()}`)
    content = parts.join('\n')
    images.push(...form.add.images)
    relatedType = 'dish'
  } else if (t === 'error') {
    if (form.error.point === 'removed') {
      content = form.error.evidenceText.trim() ? `【已下架】${form.error.evidenceText.trim()}` : '【已下架】'
      images.push(...form.error.evidenceImages)
    } else {
      const point = correctionPoints.find(c => c.key === form.error.point)?.label || ''
      const parts = [point ? `【${point}】` : '', form.error.text.trim()]
      if (form.error.correctValue.trim()) parts.push(`正确信息：${form.error.correctValue.trim()}`)
      content = parts.filter(Boolean).join('\n')
    }
    relatedType = 'dish'
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
  } catch (e: any) {
    uni.showToast({ title: e.message || '没发出去，再试一次', icon: 'none' })
  } finally {
    submitting.value = false
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
    dishKeyword.value = opts.name
    onDishSearch()
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
  padding-bottom: calc(var(--action-bar-height) + var(--spacing-xl) + env(safe-area-inset-bottom));
}

/* ===== 类型图标卡片（三列等宽铺满） ===== */
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
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.type-card:active { transform: scale(var(--press-scale)); }
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
.type-name { font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-primary); line-height: 1.2; }
.type-desc { font-size: var(--font-tiny); color: var(--text-tertiary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
/* 草稿小圆点（该类型有已填内容） */
.type-draft {
  position: absolute;
  top: var(--spacing-xs);
  right: var(--spacing-xs);
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: var(--color-accent);
  border: 2rpx solid var(--bg-card);
}

/* ===== 分区标题 + 草稿提示 ===== */
.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm) var(--spacing-lg) 0;
}
.section-head :deep(.section-title) { margin-bottom: 0; }
.draft-tip { font-size: var(--font-tiny); color: var(--text-tertiary); margin-left: var(--spacing-sm); }

/* ===== 字段分组（推荐菜品等长表单分区） ===== */
.form-group {
  padding-bottom: var(--spacing-lg);
  margin-bottom: var(--spacing-lg);
  border-bottom: 2rpx solid var(--border-color);
}
.form-group:last-child { padding-bottom: 0; margin-bottom: 0; border-bottom: none; }
.group-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  font-size: var(--font-aux);
  font-weight: var(--weight-semibold);
  color: var(--color-primary);
  margin-bottom: var(--spacing-sm);
}
.group-title::before {
  content: '';
  width: 8rpx;
  height: 24rpx;
  border-radius: 16px;
  background: var(--color-primary);
  flex-shrink: 0;
}

/* ===== 字段通用 ===== */
.field { margin-bottom: var(--spacing-md); }
.field:last-child { margin-bottom: 0; }
.field-label { display: block; font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-secondary); margin-bottom: var(--spacing-xs); }
.req { color: var(--color-error); margin-left: var(--spacing-2xs); }
.opt { color: var(--text-tertiary); font-weight: var(--weight-regular); }

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
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.sub-chip:active { transform: scale(var(--press-scale)); }
.sub-chip.active { background: var(--color-primary-soft); border-color: var(--color-primary); }
.sub-text { font-size: var(--font-body); color: var(--text-secondary); font-weight: var(--weight-medium); }
.sub-chip.active .sub-text { color: var(--color-primary); font-weight: var(--weight-semibold); }

/* 口味属性小 chip */
.sub-chip-sm {
  padding: var(--spacing-xs) var(--spacing-md);
  min-height: 56rpx;
  border-radius: var(--radius-pill);
  background: var(--bg-input);
  border: 2rpx solid var(--border-color);
  display: flex;
  align-items: center;
  font-size: var(--font-small);
  color: var(--text-secondary);
  font-weight: var(--weight-regular);
}
.sub-chip-sm.active {
  background: var(--color-primary-soft);
  border-color: var(--color-primary);
  color: var(--color-primary);
  font-weight: var(--weight-semibold);
}

/* 口味属性分组 */
.opt-group { margin-bottom: var(--spacing-sm); }
.opt-group:last-child { margin-bottom: 0; }
.opt-caption { display: block; font-size: var(--font-tiny); color: var(--text-tertiary); margin-bottom: var(--spacing-xs); }
.chip-row { display: flex; flex-wrap: wrap; gap: var(--spacing-xs); }

/* 食堂/档口 picker 行 */
.picker-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  height: 88rpx;
  padding: 0 var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-btn);
  margin-bottom: var(--spacing-sm);
  box-sizing: border-box;
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.picker-row:last-child { margin-bottom: 0; }
.picker-icon {
  width: 56rpx;
  height: 56rpx;
  flex-shrink: 0;
  border-radius: var(--radius-icon);
  background: var(--bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.picker-value { flex: 1; font-size: var(--font-body); color: var(--text-primary); }
.picker-value.placeholder { color: var(--text-tertiary); }

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
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.dish-change:active { transform: scale(var(--press-scale)); }
.dish-change-text { font-size: var(--font-aux); color: var(--text-secondary); }

.search-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  height: 88rpx;
  padding: 0 var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-btn);
  box-sizing: border-box;
}
.search-input { flex: 1; font-size: var(--font-body); color: var(--text-primary); }
.candidate-list {
  margin-top: var(--spacing-sm);
  background: var(--bg-card);
  border: 2rpx solid var(--border-color);
  border-radius: var(--radius-card);
  overflow: hidden;
}
.candidate-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  border-bottom: 2rpx solid var(--border-color);
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.candidate-item:last-child { border-bottom: none; }
.candidate-item:active { transform: scale(var(--press-scale)); }
.candidate-thumb {
  width: 72rpx;
  height: 72rpx;
  border-radius: var(--radius-icon);
  background: var(--bg-placeholder);
  flex-shrink: 0;
}
.candidate-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: var(--spacing-2xs); }
.candidate-name { font-size: var(--font-small); font-weight: var(--weight-semibold); color: var(--text-primary); }
.candidate-meta { font-size: var(--font-tiny); color: var(--text-tertiary); }
.dish-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-xs);
  margin-top: var(--spacing-sm);
  padding: var(--spacing-sm) 0;
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.dish-empty-text { font-size: var(--font-aux); color: var(--color-primary); }

/* ===== 字段级纠错：竖排单选行 + 折叠面板 ===== */
.correction-list { display: flex; flex-direction: column; gap: var(--spacing-xs); }
.correction-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  min-height: 88rpx;
  padding: var(--spacing-sm) var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-card);
  border: 2rpx solid transparent;
  box-sizing: border-box;
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.correction-row:active { transform: scale(var(--press-scale)); }
.correction-row.active { background: var(--color-primary-soft); border-color: var(--color-primary); }
.correction-icon {
  width: 56rpx;
  height: 56rpx;
  flex-shrink: 0;
  border-radius: var(--radius-icon);
  background: var(--bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.correction-row.active .correction-icon { background: var(--bg-card); }
.correction-text { flex: 1; font-size: var(--font-body); color: var(--text-secondary); font-weight: var(--weight-regular); }
.correction-row.active .correction-text { color: var(--color-primary); font-weight: var(--weight-semibold); }
.correction-check { flex-shrink: 0; }

.panel {
  margin-top: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--bg-soft);
  border-radius: var(--radius-card);
}

/* ===== 固定底栏（safe-area 避让） ===== */
.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  padding: var(--spacing-sm) var(--spacing-md) calc(var(--spacing-sm) + env(safe-area-inset-bottom));
  background: var(--bg-card);
  box-shadow: var(--shadow-bar-soft);
  border-top: 2rpx solid var(--border-color);
}
.anonymous-tip { display: block; text-align: center; font-size: var(--font-tiny); color: var(--text-quaternary); margin-top: var(--spacing-xs); }

/* 滚动区底部留白（配合固定底栏） */
.scroll-space { height: var(--spacing-lg); }

@media (prefers-reduced-motion: reduce) {
  .type-card, .sub-chip, .dish-change, .candidate-item, .correction-row, .picker-row {
    transition: none !important;
  }
  .type-card:active, .sub-chip:active, .dish-change:active, .candidate-item:active, .correction-row:active, .picker-row:active {
    transform: none !important;
  }
}
</style>
