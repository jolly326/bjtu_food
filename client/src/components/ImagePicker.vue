<template>
  <!--
    ImagePicker —— UGC 配图选择 / 压缩 / 上传云存储 / 预览统一组件。
    复用点（跨分包公用，按组件组织规范驻留 components/）：
    写评价（ReviewComposer）/ 意见反馈（IssueForm）/ 菜品纠错（CorrectionForm、GoneForm）。

    **提交时，才把本地文件送上云**（详见 components/ugcImage.ts）：
    · 选图时：wx.chooseMedia → wx.compressImage(quality 80) → wx.getImageInfo 校验最长边 ≤1334
      → wx.getFileSystemManager 校验 ≤1MB（超限逐张 toast 跳过该张）
      → **只保留本地临时路径**（缩略图 / 预览 / 删除都在本地完成），**不写云存储、不调任何后端端点**；
    · 提交时：由调用方经 submitUgcImages 逐张「先 wx.cloud.uploadFile 拿 fileID → 再 `POST /upload/cloud-image`
      （机审 + 转存 COS）」，报错文案带「第 N 张图片」定位；已成功张数保留，重试只传剩余。
    ⇒ **用户不提交 ⇒ 云存储桶零写入**（本地临时文件由微信自行回收，不占用云存储空间）。
    ⇒ 组件对外契约 = `UgcImageItem[]`（preview / fileId / url），**不含任何后端 URL 语义**。

    ⚠️ 为何不自带来源弹层：ActionSheet → BaseSheet 内部是 position: fixed，而本组件在
    意见反馈 / 菜品问题反馈两处位于 <scroll-view> 之内（fixed 层级会被压扁/裁剪）。
    故与 ReviewItem 一致：弹层由页面根级持有，本组件只抛意图 + 经 ref 暴露 startPick。

    UI 红线：可点元素 @tap；按压反馈 opacity（禁 scale）；颜色全语义 token；
    图标走 AppIcon（image=添加图片语义、close=删除）。
  -->
  <view class="ip-grid">
    <!-- 已选缩略图行：点击预览大图，右上角删除；
         破图切统一占位（评审 m4，与展示侧 ReviewItem 同构） -->
    <view v-for="(u, i) in items" :key="u.preview + i" class="ip-cell">
      <!-- 按压反馈：`<image>` 不支持 `hover-class` ⇒ 由外层等比盒承载（视觉与热区不变） -->
      <view class="ip-box" hover-class="pressed">
        <image
          v-if="!brokenImages.has(i)"
          class="ip-thumb"
          :src="u.preview"
          mode="aspectFill"
          @tap="onPreview(i)"
          @error="onImageError(i)"
        />
        <view v-else class="ip-thumb ip-thumb-fallback">
          <ImagePlaceholder :size="36" aria-label="图片已失效" />
        </view>
        <view
          class="ip-remove"
          :class="{ 'ip-remove--off': disabled }"
          role="button"
          :aria-label="`删除第 ${i + 1} 张图片`"
          hover-class="pressed"
          hover-stop-propagation
          @tap.stop="onRemove(i)"
        >
          <AppIcon name="close" :size="22" :color="COLOR_MAP['text-white']" />
        </view>
      </view>
    </view>

    <!-- 添加格：未达上限时展示；处理中 loading 态（评审 m3）；提交中/禁用弱化（评审 m1）。 -->
    <view v-if="items.length < max" class="ip-cell">
      <view
        class="ip-box ip-add"
        :class="{ processing, disabled }"
        role="button"
        :aria-label="processing ? '图片处理中' : '添加图片'"
        hover-class="pressed"
        @tap="onAdd"
      >
        <view v-if="processing" class="ip-loading" />
        <AppIcon v-else name="image" :size="48" :color="COLOR_MAP['text-tertiary']" />
        <text class="ip-add-text">{{ processing ? '处理中…' : '添加图片' }}</text>
      </view>
    </view>
    <!-- 满额计数格：轻量 n/n 占位（评审 m3，替代添加格直接消失，保留网格与已选感知）。 -->
    <view v-else class="ip-cell">
      <view class="ip-box ip-count" role="img" :aria-label="`已选满 ${max} 张图片`">
        <text class="ip-count-text">{{ items.length }}/{{ max }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'
import ImagePlaceholder from './ImagePlaceholder.vue'
import { useBrokenImages } from '@/composables/useBrokenImages'
import { toastError } from '@/utils/error'
import { COLOR_MAP } from '@/theme/tokens'
import { getWxApi } from '@/utils/device'
import {
  normalizeImage,
  assertSizeWithinLimit,
  type ImageNormalizeAdapters,
} from './imageNormalize'
/**
 * 配图来源类型（camera / album）与来源弹层动作项的**唯一真源**在 `imagePickSource.ts`——
 * 本组件只消费类型，具体弹层与动作项由页面根级持有（见 onAdd 注释）。
 */
import type { PickSource } from './imagePickSource'
import type { UgcImageItem } from './ugcImage'

defineOptions({ name: 'ImagePicker' })

const props = withDefaults(defineProps<{
  /** 已选配图（v-model：`UgcImageItem[]`，≤max 张；组件**不解释** `url` 语义，只做选择 / 预览 / 删除） */
  modelValue: UgcImageItem[]
  /** 最多张数（评价/反馈契约 ≤3） */
  max?: number
  /** 禁用（如表单提交中） */
  disabled?: boolean
}>(), {
  max: 3,
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', items: UgcImageItem[]): void
  /** 点加号：上抛「请求选择配图来源」意图，由页面根级弹层承接（见 onAdd 注释） */
  (e: 'pick'): void
}>()

/* 本地镜像为唯一写者：避免同一轮上传循环内多次 emit 时读到未刷新的 props 造成丢张 */
const items = ref<UgcImageItem[]>([...props.modelValue])
/** 破图下标集合：`error` 后切统一占位 + 预览过滤；配图变化（外部重置/删增）时清空 */
const { broken: brokenImages, markBroken: onImageError, clear, previewAt } = useBrokenImages()
watch(
  () => props.modelValue,
  (v) => {
    // 本地处理在途时不回灌：本轮追加写在本地镜像 `items` 上，
    // 若此刻用外部值覆盖，可能丢掉「已处理完成、但尚未随父级值回来」的那几张。
    // 处理期间每次追加都会 emit，结束后父级值与本地镜像自然对齐。
    if (processing.value) return
    items.value = [...(v || [])]
    clear()
  },
)

/* ===== 平台 API Promise 化（wx 句柄为微信运行时对象，平台例外未纳入项目 TS 类型，同 http.ts） ===== */
/**
 * 选图（微信端 wx.chooseMedia；H5 回退 uni.chooseImage），返回 临时路径 + 初始大小（字节）
 * <p>
 * <b>sourceType 由父页传入</b>：本组件不自带来源选择弹层（见 onAdd 注释），故来源是外部决定的单值。
 * 拍照时微信一次只返回 1 张，故 camera 分支强制 count=1，避免端上承诺多选却只回一张。
 */
function pick(count: number, source: PickSource): Promise<{ path: string; size: number }[]> {
  const sourceType = source === 'camera' ? ['camera'] : ['album']
  // 拍照不可多选：微信相机每次仅产出 1 张，此时放宽 count 只会造成「选了 N 张却回 1 张」的困惑
  const effectiveCount = source === 'camera' ? 1 : count
  // #ifdef MP-WEIXIN
  return new Promise((resolve, reject) => {
    const wxApi = getWxApi()
    if (!wxApi || !wxApi.chooseMedia) {
      reject(new Error('当前环境不支持选择图片'))
      return
    }
    wxApi.chooseMedia({
      count: effectiveCount,
      mediaType: ['image'],
      sourceType,
      sizeType: ['compressed'],
      // 平台例外：微信回调透传（wx 句柄无 TS 声明，按结构类型取所需字段）
      success: (r: { tempFiles?: Array<{ tempFilePath?: string; size?: number }> }) => {
        resolve((r.tempFiles || []).map((f) => ({ path: String(f.tempFilePath || ''), size: Number(f.size || 0) }))
          .filter((f: { path: string }) => f.path))
      },
      fail: (err: { errMsg?: string }) => {
        // 用户取消选图不算错误，静默结束
        if (/cancel/i.test(err?.errMsg || '')) { resolve([]); return }
        reject(new Error(err?.errMsg || '选择图片失败'))
      },
    })
  })
  // #endif
  // #ifndef MP-WEIXIN
  /* eslint-disable-next-line no-unreachable -- uni-app 条件编译（#ifdef/#ifndef）对 ESLint 不可见，
     两个平台分支会被连成线性代码而误判「不可达」 */
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count: effectiveCount,
      sizeType: ['compressed'],
      sourceType,
      success: (res) => {
        const paths = res.tempFilePaths || []
        // 平台例外：uni 回调 tempFiles 类型跨端不一致，仅取 size 字段（结构类型收窄，非 any）
        const tempFiles = (res.tempFiles ?? []) as unknown as Array<{ size?: unknown }>
        const files = tempFiles.map((f, i) => ({
          path: paths[i] || '',
          size: Number(f?.size || 0),
        })).filter((f: { path: string }) => f.path)
        resolve(files)
      },
      fail: (err) => {
        if (/cancel/i.test(err?.errMsg || '')) { resolve([]); return }
        reject(new Error(err?.errMsg || '选择图片失败'))
      },
    })
  })
  // #endif
}

/** 压缩：quality 降质 / compressedWidth+compressedHeight 等比缩边（微信端；低基础库缺参 fail 由调用方回退） */
function compressImage(src: string, opts: { quality?: number; compressedWidth?: number; compressedHeight?: number }): Promise<string> {
  return new Promise((resolve, reject) => {
    // 平台例外：同上
    const wxApi = getWxApi()
    if (!wxApi || !wxApi.compressImage) {
      reject(new Error('当前环境不支持图片压缩'))
      return
    }
    wxApi.compressImage({
      src,
      quality: opts.quality,
      compressedWidth: opts.compressedWidth,
      compressedHeight: opts.compressedHeight,
      success: (r: { tempFilePath?: string }) => resolve(String(r?.tempFilePath || '')),
      fail: (err: { errMsg?: string }) => reject(new Error(err?.errMsg || '图片压缩失败')),
    })
  })
}

/** 读取图片宽高（校验最长边 ≤1334） */
function getImageInfo(src: string): Promise<{ width: number; height: number }> {
  return new Promise((resolve, reject) => {
    uni.getImageInfo({
      src,
      success: (r) => resolve({ width: r.width, height: r.height }),
      fail: (err) => reject(new Error(err?.errMsg || '读取图片信息失败')),
    })
  })
}

/** 读取本地临时文件大小（字节，微信端 FileSystemManager） */
function getFileSize(filePath: string): Promise<number> {
  return new Promise((resolve, reject) => {
    // 平台例外：同上
    const wxApi = getWxApi()
    if (!wxApi || !wxApi.getFileSystemManager) {
      reject(new Error('无法读取文件大小'))
      return
    }
    wxApi.getFileSystemManager().getFileInfo({
      filePath,
      success: (r: { size?: number }) => resolve(Number(r?.size || 0)),
      fail: (err: { errMsg?: string }) => reject(new Error(err?.errMsg || '读取文件大小失败')),
    })
  })
}

/**
 * 收敛策略（压几轮 / 缩到多少 / 何时判失败）已抽至 imageNormalize.ts：
 * 那里是无副作用逻辑 + 平台适配器注入，可被单测直接覆盖；此处只做适配器接线。
 */
const normalizeAdapters: ImageNormalizeAdapters = {
  compress: compressImage,
  getSize: getImageInfo,
  getFileSize,
}

/**
 * 选图后的规格收敛入口（平台分派，赋值模式避免条件编译 unreachable）：
 * - 微信端：压缩 + 尺寸/大小校验（normalizeImage + 平台适配器）；
 * - H5 端：无 wx 压缩链路，仅做大小门禁后直传。
 */
async function normalizeForPlatform(input: { path: string; size: number }): Promise<string> {
  let path: string
  // #ifdef MP-WEIXIN
  path = await normalizeImage(input, normalizeAdapters)
  // #endif
  // #ifndef MP-WEIXIN
  assertSizeWithinLimit(input.size)
  path = input.path
  // #endif
  return path
}

/* ===== 添加图片：来源由父页决定 → 逐张 本地规格收敛 → 追加本地临时路径；单张失败不中断其余 ===== */
const processing = ref(false)

/** 可否继续加图：未禁用、未在途、且未达张数上限 */
function canAdd(): boolean {
  if (props.disabled || processing.value) return false
  return props.max - items.value.length > 0
}

/**
 * 点「添加图片」：**只抛意图，不自己弹层**。
 *
 * 原因：来源选择弹层（ActionSheet → BaseSheet）内部是 `position: fixed`，而本组件位于
 * `<scroll-view>` 之内 —— 小程序 scroll-view 内的 fixed 层级会被压扁/裁剪。
 * 故弹层由**页面根级**持有，本组件经事件把意图上抛，父页拿到来源后再调用 {@link startPick}。
 */
function onAdd() {
  if (canAdd()) emit('pick')
}

/**
 * 按父页给定的来源真正拉起选图（父页在 ActionSheet 选中项后调用）。
 * 内部为「拉起 → 逐张本地规格收敛（压缩 + 尺寸/大小校验）→ 追加本地临时路径」全流程；拍照一次仅回 1 张，由 {@link pick} 收口。
 *
 * **不写云存储、不调后端**：图片此时只在**本地临时文件**里（缩略图 / 预览 / 删除都本地完成）。
 * 上云（微信云存储）与内容安检 + 转存 COS 全部留到用户**确认提交**时（见 `submitUgcImages`）——
 * 用户不提交时云存储桶零写入，既省空间，也不需要靠「未提交中间产物过期清理」兜底。
 */
async function startPick(source: PickSource) {
  if (!canAdd()) return
  const remain = props.max - items.value.length
  processing.value = true
  try {
    const files = await pick(remain, source)
    for (const f of files) {
      try {
        const preview = await normalizeForPlatform(f)
        // 仅落本地临时路径：`fileId`（云存储）留到提交时才产生
        items.value = [...items.value, { preview, fileId: '', url: '' }]
        emit('update:modelValue', [...items.value])
      } catch (e) {
        // 超大 / 压缩失败：toast 透出，跳过该张（机审与上传都不在本阶段）
        toastError(e, '图片处理失败')
      }
    }
  } catch (e) {
    toastError(e, '选择图片失败')
  } finally {
    processing.value = false
  }
}

/** 供父页经 ref 调用（来源弹层选完后落地） */
defineExpose({ startPick })

/** 删除已选（纯本地移除 —— 此刻尚未上传，云存储与后端均无痕迹，无需任何清理动作） */
function onRemove(i: number) {
  if (props.disabled || processing.value) return
  items.value = items.value.filter((_, idx) => idx !== i)
  emit('update:modelValue', [...items.value])
}

/** 预览大图（仅未破图可进入，破图项被过滤，current 定位到点击那张；预览地址 = `preview`） */
function onPreview(i: number) {
  previewAt(items.value.map((it) => it.preview), i)
}
</script>

<style scoped lang="scss">
/* 3 列方形图网格：网格语言来自共享 partial（与 ReviewItem 同源），此处只做类名绑定 */
@use '../styles/media-grid' as grid;

.ip-grid {
  @include grid.list;
}
.ip-cell {
  @include grid.cell;
}
.ip-box {
  @include grid.box;
}
.ip-thumb {
  @include grid.media;
}
/* 破图兜底（评审 m4）：empty 中性占位（浅底居中），与展示侧 ReviewItem 同构 */
.ip-thumb-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-placeholder);
}

/* 删除钮：右上角半透明黑圆底 + 白叉（--badge-dark-bg：App.vue 已登记的「图片移除」暗底语义 token，评审 B2）；
   热区经 ::after 扩至 88rpx（对齐 my-reviews delete-link 的 Apple 44pt 触达下限模式） */
.ip-remove {
  position: absolute;
  top: var(--spacing-xs);
  right: var(--spacing-xs);
  width: 44rpx;
  height: 44rpx;
  border-radius: var(--radius-circle);
  background: var(--badge-dark-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-tap-highlight-color: transparent;
  transition: opacity var(--duration-fast) var(--ease-out);
}
.ip-remove::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: var(--tap-target-size);
  height: var(--tap-target-size);
  transform: translate(-50%, -50%);
}

/* 添加格：浅底虚线框 + 图片语义 icon + 引导文案；上传中降透明度 */
.ip-add {
  /* HIG 轻量化：取消白底、虚线降透明度且非粗线（1rpx --border-color） */
  background: transparent;
  border: 1rpx dashed var(--border-color);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2xs);
  transition: opacity var(--duration-fast) var(--ease-out);
}
.ip-add.processing { opacity: 0.6; }
/* 禁用态（评审 m1）：提交中等 disabled 弱化（非主色可点件保留透明档），按压不改变观感（onAdd 已拦截点击） */
.ip-add.disabled { opacity: 0.5; }
/* 禁用态（复审 MINOR）：提交中删除钮同步弱化（onRemove 已拦截点击） */
.ip-remove--off { opacity: 0.5; }
/* 按压反馈：删除钮属「小件」档（行内图标钮）⇒ 0.6；
   提交中同时按压时保持禁用档 0.5（禁用弱化不纳入按压三档） */
.ip-remove.pressed { opacity: 0.6; }
.ip-remove--off.pressed { opacity: 0.5; }
.ip-add-text {
  font-size: var(--font-aux);
  color: var(--text-tertiary);
}
/* 上传中 loading 态（评审 m3）：轻量圆环旋转（加载指示非图标语义，颜色走语义 token） */
.ip-loading {
  width: 44rpx;
  height: 44rpx;
  border-radius: var(--radius-circle);
  border: 4rpx solid var(--border-color);
  border-top-color: var(--color-primary);
  animation: ip-spin 0.8s linear infinite;
}
@keyframes ip-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
@media (prefers-reduced-motion: reduce) {
  .ip-loading { animation: none; }   /* 停止持续旋转（全局基线是保留静态环） */
}
/* 满额计数格（评审 m3）：浅底虚线 + 轻量 n/n（绝对定位居中，不依赖 0 高等比盒的 flex） */
.ip-count { background: var(--module-input-bg); border: 2rpx dashed var(--border-color); box-sizing: border-box; }
.ip-count-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--font-aux);
  color: var(--text-tertiary);
  font-variant-numeric: tabular-nums;
}
</style>
