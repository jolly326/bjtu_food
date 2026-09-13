<template>
  <!-- 推荐菜品（基本信息 / 位置 / 图片与描述）；位置选择行只上抛事件，弹层由父页统一管理 -->
  <view>
    <view class="form-group">
      <view class="row-fields">
        <view class="col">
          <text class="field-label">菜名<text class="req">*</text></text>
          <input
            id="f-add-name"
            v-model="model.name"
            class="field-input"
            :class="{ 'input-error': errors['add.name'] }"
            placeholder="必填"
            maxlength="50"
            :cursor-spacing="40"
            :adjust-position="true"
            @input="emit('clear', 'add.name')"
          />
          <text v-if="errors['add.name']" class="field-error">{{ errors['add.name'] }}</text>
        </view>
        <view class="col">
          <text class="field-label">价格（元）</text>
          <input
            id="f-add-price"
            v-model="model.price"
            class="field-input"
            :class="{ 'input-error': errors['add.price'] }"
            type="digit"
            placeholder="0.00"
            maxlength="7"
            :cursor-spacing="40"
            :adjust-position="true"
            @input="emit('clear', 'add.price')"
          />
          <text v-if="errors['add.price']" class="field-error">{{ errors['add.price'] }}</text>
        </view>
      </view>
    </view>

    <view class="form-group">
      <view class="row-fields">
        <view class="col">
          <text class="field-label">食堂</text>
          <view
            class="picker-row"
            hover-class="pressed"
            hover-stay-time="80"
            role="button"
            aria-label="选择食堂"
            @tap="emit('open-location', 'canteen')"
          >
            <text class="picker-value" :class="{ placeholder: !displayCanteen }">{{ displayCanteen || '选择食堂' }}</text>
            <IconSvg name="arrow" :size="26" color="var(--text-tertiary)" />
          </view>
        </view>
        <view class="col">
          <text class="field-label">档口</text>
          <!-- A1：未选食堂时禁用 + 引导（父页 onStallRowTap 处理 toast/弹层） -->
          <view
            class="picker-row"
            :class="{ disabled: !displayCanteen }"
            :hover-class="displayCanteen ? 'pressed' : 'none'"
            hover-stay-time="80"
            role="button"
            :aria-label="displayCanteen ? '选择档口' : '请先选择食堂'"
            @tap="emit('stall-tap')"
          >
            <text class="picker-value" :class="{ placeholder: !displayStall }">{{ displayStall || (displayCanteen ? '选择档口' : '先选食堂') }}</text>
            <IconSvg name="arrow" :size="26" color="var(--text-tertiary)" />
          </view>
        </view>
      </view>

      <view class="field-gap" />

      <text class="field-label">楼层<text class="req">*</text></text>
      <view
        id="f-add-floor"
        class="picker-row"
        hover-class="pressed"
        hover-stay-time="80"
        role="button"
        aria-label="选择楼层"
        @tap="emit('open-floor')"
      >
        <text class="picker-value" :class="{ placeholder: !model.floor }">{{ model.floor ? `${model.floor} 楼` : '选择' }}</text>
        <IconSvg name="arrow" :size="26" color="var(--text-tertiary)" />
      </view>
      <text v-if="errors['add.floor']" class="field-error">{{ errors['add.floor'] }}</text>
    </view>

    <view class="form-group">
      <view class="field">
        <text class="field-label">一句话描述</text>
        <textarea
          v-model="model.description"
          class="content-input content-input-sm"
          placeholder="口味 / 特色"
          maxlength="200"
          :auto-height="true"
          :cursor-spacing="40"
          :adjust-position="true"
        />
      </view>
    </view>

    <!-- 配图（选填 ≤3 张）：统一 ImagePicker（安检上传），随 model.images 交给父页提交；
         标签统一「配图」（评审 m2）；提交中禁选（评审 m1） -->
    <view class="form-group">
      <view class="field">
        <text class="field-label">配图</text>
        <ImagePicker v-model="model.images" :max="3" :disabled="submitting" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import IconSvg from '@/components/IconSvg.vue'
import ImagePicker from '@/components/ImagePicker.vue'

/** AddForm（feedback 包内私有）：「推荐菜品」字段区（基本信息 + 位置 + 描述 + 配图） */
const props = defineProps<{
  model: {
    name: string
    price: string
    canteen: string
    canteenCustom: string
    stallName: string
    stallCustom: string
    floor: string
    description: string
    images: string[]
  }
  errors: Record<string, string>
  /** 提交中：禁选配图（评审 m1，与 ReviewComposer 一致） */
  submitting?: boolean
}>()
const emit = defineEmits<{
  (e: 'clear', key: string): void
  (e: 'open-location', step: 'canteen' | 'stall'): void
  (e: 'open-floor'): void
  (e: 'stall-tap'): void
}>()

/** 食堂显示名：选「其他」且有自定义名时显示自定义名（与父页旧口径一致） */
const displayCanteen = computed(() =>
  props.model.canteen === '其他' ? props.model.canteenCustom.trim() || '其他' : props.model.canteen,
)
/** 档口显示名 */
const displayStall = computed(() =>
  props.model.stallName === '其他' ? props.model.stallCustom.trim() || '其他' : props.model.stallName,
)
</script>

<style scoped>
.form-group { margin-bottom: var(--spacing-lg); }
.form-group:last-child { margin-bottom: 0; }
.field { margin-bottom: var(--spacing-md); }
.field:last-child { margin-bottom: 0; }
.field-label { display: block; font-size: var(--font-aux); font-weight: var(--weight-semibold); color: var(--text-secondary); margin-bottom: var(--spacing-xs); }
.req { color: var(--color-error); margin-left: var(--spacing-2xs); font-size: var(--font-small); font-weight: var(--weight-heavy); }
.field-gap { height: var(--spacing-sm); }
.row-fields { display: flex; gap: var(--spacing-sm); }
.row-fields .col { flex: 1; min-width: 0; }
.field-input {
  width: 100%;
  height: 88rpx;
  background: var(--bg-input);
  border-radius: var(--radius-icon);
  padding: 0 var(--spacing-md);
  font-size: var(--font-body);
  color: var(--text-primary);
  box-sizing: border-box;
  border: 2rpx solid transparent;
}
.content-input {
  width: 100%;
  min-height: 300rpx;
  font-size: var(--font-body);
  color: var(--text-primary);
  line-height: 1.6;
  padding: var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-icon);
  box-sizing: border-box;
  border: 2rpx solid transparent;
}
.content-input-sm { min-height: 140rpx; }
.input-error { border-color: var(--color-error); }
.field-error { display: block; margin-top: var(--spacing-xs); font-size: var(--font-tiny); color: var(--color-error); }
.picker-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-xs);
  height: 88rpx;
  padding: 0 var(--spacing-md);
  background: var(--bg-input);
  border-radius: var(--radius-icon);
  box-sizing: border-box;
  -webkit-tap-highlight-color: transparent;
}
.picker-row.disabled { opacity: 0.5; }
.picker-value { font-size: var(--font-body); color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.picker-value.placeholder { color: var(--text-tertiary); }
</style>
