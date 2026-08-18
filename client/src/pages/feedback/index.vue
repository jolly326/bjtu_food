<template>
  <view class="page feedback-page" :class="{ 'theme-dark': theme.isDark }">
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
        <AppButton :text="submitting ? '提交中…' : '提交反馈'" :loading="submitting" @click="submit" />
      </view>
    </scroll-view>

    <!-- ===== 底部弹窗：选择位置（食堂 → 档口 两级联动） ===== -->
    <view v-if="locSheetOpen" class="sheet-mask" @tap="closeLocationSheet" @touchmove.stop.prevent="noop" />
    <view
      class="loc-sheet"
      :class="{ open: locSheetOpen }"
      :style="locSheetDrag.style.value"
      @touchstart="locSheetDrag.onStart"
      @touchmove="locSheetDrag.onMove"
      @touchend="locSheetDrag.onEnd(closeLocationSheet)"
      @touchcancel="locSheetDrag.onEnd(closeLocationSheet)"
    >
      <view class="sheet-grabber" />
      <view class="sheet-head">
        <view class="sheet-head-left">
          <view v-if="locStep === 'stall'" class="sheet-back" hover-class="pressed" hover-stay-time="80" role="button" aria-label="返回选择食堂" @tap="locStep = 'canteen'">
            <IconSvg name="arrow" :size="30" color="var(--text-secondary)" />
          </view>
          <text class="sheet-title">{{ locStep === 'canteen' ? '选择食堂' : '选择档口' }}</text>
        </view>
        <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" @tap="closeLocationSheet" />
      </view>

      <scroll-view class="sheet-list" scroll-y>
        <!-- 食堂级：列表 + 其他 -->
        <template v-if="locStep === 'canteen'">
          <view
            v-for="c in canteenTree"
            :key="c.id || c.name"
            class="sheet-item"
            :class="{ on: form.add.canteen === c.name }"
            hover-class="pressed"
            hover-stay-time="80"
            role="button"
            :aria-label="c.name"
            @tap="pickCanteen(c.name)"
          >
            <view class="sheet-item-icon"><IconSvg name="canteen" :size="32" color="var(--text-tertiary)" /></view>
            <text class="sheet-item-name">{{ c.name }}</text>
            <IconSvg v-if="form.add.canteen === c.name" name="check" :size="32" color="var(--color-primary)" />
          </view>
          <view
            class="sheet-item"
            :class="{ on: form.add.canteen === '其他' }"
            hover-class="pressed"
            hover-stay-time="80"
            role="button"
            aria-label="其他食堂"
            @tap="pickCanteen('其他')"
          >
            <view class="sheet-item-icon"><IconSvg name="add" :size="32" color="var(--text-tertiary)" /></view>
            <text class="sheet-item-name">其他</text>
          </view>
          <view v-if="form.add.canteen === '其他'" class="sheet-custom">
            <input
              v-model="form.add.canteenCustom"
              class="sheet-custom-input"
              placeholder="写一下食堂名"
              maxlength="50"
              :cursor-spacing="20"
              :adjust-position="true"
            />
          </view>
        </template>
        <!-- 档口级：列表 + 其他 -->
        <template v-else>
          <view
            v-for="s in stallOptions"
            :key="s.name"
            class="sheet-item"
            :class="{ on: form.add.stallName === s.name }"
            hover-class="pressed"
            hover-stay-time="80"
            role="button"
            :aria-label="s.name"
            @tap="pickStall(s.name)"
          >
            <view class="sheet-item-icon"><IconSvg name="stall" :size="32" color="var(--text-tertiary)" /></view>
            <text class="sheet-item-name">{{ s.name }}</text>
            <IconSvg v-if="form.add.stallName === s.name" name="check" :size="32" color="var(--color-primary)" />
          </view>
          <view
            class="sheet-item"
            :class="{ on: form.add.stallName === '其他' }"
            hover-class="pressed"
            hover-stay-time="80"
            role="button"
            aria-label="其他档口"
            @tap="pickStall('其他')"
          >
            <view class="sheet-item-icon"><IconSvg name="add" :size="32" color="var(--text-tertiary)" /></view>
            <text class="sheet-item-name">其他</text>
          </view>
          <view v-if="form.add.stallName === '其他'" class="sheet-custom">
            <input
              v-model="form.add.stallCustom"
              class="sheet-custom-input"
              placeholder="写一下档口名"
              maxlength="50"
              :cursor-spacing="20"
              :adjust-position="true"
            />
          </view>
        </template>
      </scroll-view>
    </view>

    <!-- ===== 底部弹窗：选择楼层 ===== -->
    <view v-if="floorSheetOpen" class="sheet-mask" @tap="closeFloorSheet" @touchmove.stop.prevent="noop" />
    <view
      class="loc-sheet"
      :class="{ open: floorSheetOpen }"
      :style="floorSheetDrag.style.value"
      @touchstart="floorSheetDrag.onStart"
      @touchmove="floorSheetDrag.onMove"
      @touchend="floorSheetDrag.onEnd(closeFloorSheet)"
      @touchcancel="floorSheetDrag.onEnd(closeFloorSheet)"
    >
      <view class="sheet-grabber" />
      <view class="sheet-head">
        <view class="sheet-head-left">
          <text class="sheet-title">选择楼层</text>
        </view>
        <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" @tap="closeFloorSheet" />
      </view>

      <scroll-view class="sheet-list" scroll-y>
        <view
          v-for="f in floorOptions"
          :key="f"
          class="sheet-item"
          :class="{ on: form.add.floor === f }"
          hover-class="pressed"
          hover-stay-time="80"
          role="button"
          :aria-label="`${f} 楼`"
          @tap="pickFloor(f)"
        >
          <view class="sheet-item-icon"><IconSvg name="canteen" :size="32" color="var(--text-tertiary)" /></view>
          <text class="sheet-item-name">{{ f }} 楼</text>
          <IconSvg v-if="form.add.floor === f" name="check" :size="32" color="var(--color-primary)" />
        </view>
      </scroll-view>
    </view>

    <!-- ===== 底部弹窗：选择菜品（搜索 + 列表 + 空态去补录） ===== -->
    <view v-if="dishSheetOpen" class="sheet-mask" @tap="closeDishSheet" @touchmove.stop.prevent="noop" />
    <view
      class="loc-sheet"
      :class="{ open: dishSheetOpen }"
      :style="dishSheetDrag.style.value"
      @touchstart="dishSheetDrag.onStart"
      @touchmove="dishSheetDrag.onMove"
      @touchend="dishSheetDrag.onEnd(closeDishSheet)"
      @touchcancel="dishSheetDrag.onEnd(closeDishSheet)"
    >
      <view class="sheet-grabber" />
      <view class="sheet-head">
        <view class="sheet-head-left">
          <text class="sheet-title">选择菜品</text>
        </view>
        <IconSvg class="sheet-close" name="close" :size="36" color="var(--text-tertiary)" @tap="closeDishSheet" />
      </view>

      <view class="sheet-search">
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
      </view>

      <scroll-view class="sheet-list" scroll-y>
        <!-- 搜索中 -->
        <view v-if="dishLoading" class="sheet-empty">
          <view class="footer-spinner" />
        </view>
        <!-- 无结果：去补录 -->
        <view v-else-if="dishSearched && !dishCandidates.length" class="sheet-empty">
          <text class="sheet-empty-text">没搜到「{{ dishKeyword }}」</text>
          <view
            class="sheet-goto-add"
            hover-class="pressed"
            hover-stay-time="80"
            role="button"
            aria-label="去推荐菜品补录"
            @tap="gotoAdd"
          ><text class="sheet-goto-add-text">去补录一道</text></view>
        </view>
        <!-- 初始引导 -->
        <view v-else-if="!dishKeyword" class="sheet-empty">
          <text class="sheet-empty-text">输入关键词搜索菜品</text>
        </view>
        <!-- 候选列表 -->
        <view v-else class="candidate-list">
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
      </scroll-view>
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
import IconSvg from '@/components/IconSvg.vue'
import ImageUploader from '@/components/ImageUploader.vue'

const theme = useThemeStore()

// 返回：有返回栈时 navigateBack；无返回栈（redirectTo 直达）才 reLaunch 首页
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack()
  else backToHome()
}

/** 空处理器：遮罩 touchmove 防背景滚动穿透 */
function noop() {}

// ===== 底部弹窗：下拉关闭手势（每个弹窗独立拖拽实例，互不串扰） =====
function createSheetDrag() {
  const drag = ref(0) // 当前拖拽位移（px）
  const dragging = ref(false)
  let startY = 0
  let lastY = 0
  let lastTime = 0
  let velocity = 0

  const style = computed(() =>
    dragging.value
      ? { transform: `translateY(${drag.value}px)`, transition: 'none' }
      : {},
  )

  function onStart(e: any) {
    startY = e.touches?.[0]?.clientY ?? 0
    lastY = startY
    lastTime = Date.now()
    velocity = 0
    dragging.value = true
  }

  function onMove(e: any) {
    if (!dragging.value) return
    const y = e.touches?.[0]?.clientY ?? 0
    const now = Date.now()
    const dt = Math.max(now - lastTime, 1)
    velocity = ((y - lastY) / dt) * 1000
    lastY = y
    lastTime = now
    const delta = y - startY
    // 仅允许向下拖拽
    drag.value = delta > 0 ? delta : 0
  }

  function onEnd(close: () => void) {
    const wasDragging = dragging.value
    dragging.value = false
    // 松手速度 > 480px/s 或位移 > 60px（≈120rpx）关闭，否则回弹
    if (wasDragging && (velocity > 480 || drag.value > 60)) close()
    drag.value = 0
  }

  function reset() {
    dragging.value = false
    drag.value = 0
  }

  return { style, onStart, onMove, onEnd, reset }
}

// 位置 / 楼层 / 菜品 三个弹窗各自的独立拖拽
const locSheetDrag = createSheetDrag()
const floorSheetDrag = createSheetDrag()
const dishSheetDrag = createSheetDrag()

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
const dishLoading = ref(false)
const dishSearched = ref(false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

function openDishSheet() {
  dishSheetDrag.reset()
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

function onDishInput() {
  cancelAutoBack()
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

// ---- ⑤ 位置选择：底部弹窗（食堂 → 档口 两级联动，含「其他」自定义） ----
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

/** 当前食堂下的档口选项（含「其他」由模板追加） */
const stallOptions = computed(() => {
  const c = canteenTree.value.find(x => x.name === form.add.canteen)
  return (c?.stalls || []).map((s: any) => ({ name: s.name }))
})

// ---- ⑤.5 楼层选择：底部弹窗（1/2/3） ----
const floorSheetOpen = ref(false)
const floorOptions = ['1', '2', '3']

function openFloorSheet() {
  floorSheetDrag.reset()
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

async function loadCanteens() {
  try {
    canteenTree.value = await getCanteensWithStalls()
  } catch {
    canteenTree.value = []
  }
}

function openLocationSheet(step: 'canteen' | 'stall') {
  locSheetDrag.reset()
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
  setTimeout(() => { scrollIntoView.value = target }, 50)
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
  const relatedId = form.error.dish?.id

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
let backTimer: ReturnType<typeof setTimeout> | null = null

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

/* 「其他」自定义输入框 */
.custom-box { margin-top: var(--spacing-sm); }

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
  transition: var(--press-transition);
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
/* ===== 底部弹窗（位置选择 / 菜品搜索） ===== */
.sheet-mask {
  position: fixed;
  inset: 0;
  background: var(--overlay-scrim);
  z-index: 90;
  opacity: 1;
}
.loc-sheet {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--bg-card);
  border-radius: var(--radius-modal) var(--radius-modal) 0 0;
  box-shadow: var(--shadow-modal);
  z-index: 100;
  transform: translateY(100%);
  transition: transform 0.3s cubic-bezier(0.32, 0.72, 0, 1);
  display: flex;
  flex-direction: column;
  height: 60vh;
  padding-bottom: calc(var(--spacing-md) + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.loc-sheet.open { transform: translateY(0); }
.sheet-grabber { width: 72rpx; height: 8rpx; border-radius: 999rpx; background: var(--overlay-dark-soft); margin: var(--spacing-sm) auto 0; flex-shrink: 0; }
.sheet-head { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-md); border-bottom: 2rpx solid var(--border-color); flex-shrink: 0; }
.sheet-head-left { display: flex; align-items: center; gap: var(--spacing-xs); }
.sheet-back { width: 48rpx; height: 48rpx; display: flex; align-items: center; justify-content: center; transform: scaleX(-1); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.sheet-back:active { transform: scaleX(-1) scale(var(--press-scale)); }
.sheet-title { font-size: var(--font-h3); font-weight: var(--weight-bold); color: var(--text-primary); }
.sheet-close { padding: var(--spacing-xs); }
.sheet-search { padding: var(--spacing-md); flex-shrink: 0; }
.sheet-list { flex: 1; overflow-y: auto; padding: 0 var(--spacing-md) var(--spacing-sm); }
.sheet-item { display: flex; align-items: center; gap: var(--spacing-sm); padding: var(--spacing-sm) 0; border-bottom: 2rpx solid var(--border-color); transition: var(--press-transition); -webkit-tap-highlight-color: transparent; }
.sheet-item:active { transform: scale(var(--press-scale)); }
.sheet-item.on { background: var(--bg-soft); }
.sheet-item-icon {
  width: 64rpx;
  height: 64rpx;
  flex-shrink: 0;
  border-radius: var(--radius-icon);
  background: var(--bg-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}
.sheet-item-name { flex: 1; font-size: var(--font-body); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sheet-custom { padding: var(--spacing-sm) 0 var(--spacing-md); }
.sheet-custom-input {
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
.sheet-empty { padding: var(--spacing-xl) 0; display: flex; flex-direction: column; align-items: center; gap: var(--spacing-sm); }
.sheet-empty-text { font-size: var(--font-aux); color: var(--text-tertiary); }
.sheet-goto-add {
  min-width: 200rpx;
  height: 68rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--spacing-lg);
  background: var(--color-primary);
  border-radius: var(--radius-btn);
  box-sizing: border-box;
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.sheet-goto-add:active { transform: scale(var(--press-scale)); }
.sheet-goto-add-text { font-size: var(--font-small); color: var(--bg-card); font-weight: var(--weight-semibold); }
.footer-spinner { width: 28rpx; height: 28rpx; border: 4rpx solid var(--border-color); border-top-color: var(--color-primary); border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

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
  transition: var(--press-transition);
  -webkit-tap-highlight-color: transparent;
}
.point-option:active { transform: scale(var(--press-scale)); }
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


@media (prefers-reduced-motion: reduce) {
  .type-card, .sub-chip, .dish-change, .candidate-item, .point-option, .picker-row,
  .sheet-item, .sheet-goto-add, .sheet-back {
    transition: none !important;
  }
  .type-card:active, .sub-chip:active, .dish-change:active, .candidate-item:active,
  .point-option:active, .picker-row:active, .sheet-item:active, .sheet-goto-add:active,
  .sheet-back:active {
    transform: none !important;
  }
  .loc-sheet { transition: none !important; }
  .footer-spinner { animation: none; }
}
</style>
