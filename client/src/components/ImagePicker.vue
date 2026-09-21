<template>
  <!--
    ImagePicker —— UGC 配图选择/压缩/上传/预览统一组件（2026-09 恢复 UGC 配图）。
    复用点（≥3 处，跨分包公用，按组件组织规范驻留 components/）：
    写评价（ReviewComposer）/ 提个想法（SuggestionForm）/ 推荐菜品（AddForm）/ 信息不对（ErrorForm）。

    流程（微信端）：wx.chooseMedia(count≤max, image) → 逐张 wx.compressImage(quality 80) 压缩
    → wx.getImageInfo 校验最长边 ≤1334（超出按比例再压）→ 文件大小 ≤1MB（超限 toast 跳过该张）
    → uploadUgcImage（云存储 fileID → POST /upload/images 后端安检转存 COS）→ 追加 COS URL 至 v-model。
    违规图片后端 400「图片包含违规内容，无法上传」由 http 层抛 message，此处逐张 toast 透出。

    UI 红线（spec §4.9）：可点元素 @tap；按压反馈 opacity（禁 scale）；颜色全语义 token；
    图标走 IconSvg（image=添加图片语义、close=删除）。
  -->
  <view class="ip-grid">
    <!-- 已上传缩略图行：点击预览大图，右上角删除重选；
         破图切 empty 中性占位（评审 m4，与展示侧 ReviewItem 同构） -->
    <view v-for="(u, i) in urls" :key="u" class="ip-cell">
      <view class="ip-box">
        <image
          v-if="!brokenImages.has(i)"
          class="ip-thumb"
          :src="u"
          mode="aspectFill"
          @tap="onPreview(i)"
          @error="onImageError(i)"
        />
        <view v-else class="ip-thumb ip-thumb-fallback">
          <IconSvg name="empty" :size="36" :color="COLOR_MAP['text-tertiary']" />
        </view>
        <view
          class="ip-remove"
          :class="{ 'ip-remove--off': disabled }"
          role="button"
          :aria-label="`删除第 ${i + 1} 张图片`"
          @tap.stop="onRemove(i)"
        >
          <IconSvg name="close" :size="22" :color="COLOR_MAP['text-white']" />
        </view>
      </view>
    </view>

    <!-- 添加格：未达上限时展示；上传中 loading 态（评审 m3）；提交中/禁用弱化（评审 m1） -->
    <view v-if="urls.length < max" class="ip-cell">
      <view
        class="ip-box ip-add"
        :class="{ uploading, disabled }"
        role="button"
        :aria-label="uploading ? '图片上传中' : '添加图片'"
        @tap="onAdd"
      >
        <view v-if="uploading" class="ip-loading" />
        <IconSvg v-else name="image" :size="48" :color="COLOR_MAP['text-tertiary']" />
        <text class="ip-add-text">{{ uploading ? '上传中…' : '添加图片' }}</text>
      </view>
    </view>
    <!-- 满额计数格：轻量 n/n 占位（评审 m3，替代添加格直接消失，保留网格与已选感知） -->
    <view v-else class="ip-cell">
      <view class="ip-box ip-count" role="img" :aria-label="`已选满 ${max} 张图片`">
        <text class="ip-count-text">{{ urls.length }}/{{ max }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import IconSvg from './IconSvg.vue'
import { uploadUgcImage } from '@/api/upload'
import { COLOR_MAP } from '@/theme/tokens'

defineOptions({ name: 'ImagePicker' })

const props = withDefaults(defineProps<{
  /** 已上传图片 URL 列表（v-model：COS URL，≤max 张） */
  modelValue: string[]
  /** 最多张数（评价/反馈契约 ≤3） */
  max?: number
  /** 禁用（如表单提交中） */
  disabled?: boolean
}>(), {
  max: 3,
  disabled: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', urls: string[]): void
}>()

/* 本地镜像为唯一写者：避免同一轮上传循环内多次 emit 时读到未刷新的 props 造成丢张 */
const urls = ref<string[]>([...props.modelValue])
/** 破图下标集合（评审 m4）：error 后切 empty 占位 + 预览过滤；urls 变化（外部重置/删增）时清空 */
const brokenImages = ref<Set<number>>(new Set())
watch(
  () => props.modelValue,
  (v) => {
    urls.value = [...(v || [])]
    brokenImages.value = new Set()
  },
)
function onImageError(i: number) {
  const next = new Set(brokenImages.value)
  next.add(i)
  brokenImages.value = next
}

/* ===== 校验常量（后端契约：最长边 ≤1334px；文件 ≤1MB） ===== */
const MAX_EDGE = 1334
const MAX_SIZE = 1024 * 1024

/* ===== 平台 API Promise 化（wx 句柄为微信运行时对象，平台例外未纳入项目 TS 类型，同 http.ts） ===== */
/** 选图（微信端 wx.chooseMedia；H5 回退 uni.chooseImage），返回 临时路径 + 初始大小（字节） */
function pick(count: number): Promise<{ path: string; size: number }[]> {
  // #ifdef MP-WEIXIN
  return new Promise((resolve, reject) => {
    const wxApi: any = (globalThis as any).wx
    if (!wxApi || !wxApi.chooseMedia) {
      reject(new Error('当前环境不支持选择图片'))
      return
    }
    wxApi.chooseMedia({
      count,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      sizeType: ['compressed'],
      // 平台例外：微信回调透传
      success: (r: any) => {
        resolve((r.tempFiles || []).map((f: any) => ({ path: String(f.tempFilePath || ''), size: Number(f.size || 0) }))
          .filter((f: { path: string }) => f.path))
      },
      fail: (err: any) => {
        // 用户取消选图不算错误，静默结束
        if (/cancel/i.test(err?.errMsg || '')) { resolve([]); return }
        reject(new Error(err?.errMsg || '选择图片失败'))
      },
    })
  })
  // #endif
  // #ifndef MP-WEIXIN
  return new Promise((resolve, reject) => {
    uni.chooseImage({
      count,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const paths = res.tempFilePaths || []
        // 平台例外：uni 回调 tempFiles 类型跨端不一致，仅取 size 字段
        const files = (res.tempFiles as any[] || []).map((f: any, i: number) => ({
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
    const wxApi: any = (globalThis as any).wx
    if (!wxApi || !wxApi.compressImage) {
      reject(new Error('当前环境不支持图片压缩'))
      return
    }
    wxApi.compressImage({
      src,
      quality: opts.quality,
      compressedWidth: opts.compressedWidth,
      compressedHeight: opts.compressedHeight,
      success: (r: any) => resolve(String(r?.tempFilePath || '')),
      fail: (err: any) => reject(new Error(err?.errMsg || '图片压缩失败')),
    })
  })
}

/** 读取图片宽高（校验最长边 ≤1334） */
function getImageInfo(src: string): Promise<{ width: number; height: number }> {
  return new Promise((resolve, reject) => {
    uni.getImageInfo({
      src,
      success: (r) => resolve({ width: r.width, height: r.height }),
      fail: (err: any) => reject(new Error(err?.errMsg || '读取图片信息失败')),
    })
  })
}

/** 读取本地临时文件大小（字节，微信端 FileSystemManager） */
function getFileSize(filePath: string): Promise<number> {
  return new Promise((resolve, reject) => {
    // 平台例外：同上
    const wxApi: any = (globalThis as any).wx
    if (!wxApi || !wxApi.getFileSystemManager) {
      reject(new Error('无法读取文件大小'))
      return
    }
    wxApi.getFileSystemManager().getFileInfo({
      filePath,
      success: (r: any) => resolve(Number(r?.size || 0)),
      fail: (err: any) => reject(new Error(err?.errMsg || '读取文件大小失败')),
    })
  })
}

/**
 * 单张图收敛至规格内（微信端）：首压 → 尺寸超限按比例再压 → 大小超限降质再压（最多 3 轮）。
 * 超出规格（如仍 >1MB）抛错，由调用方 toast 并跳过该张。
 */
async function normalizeMp(input: { path: string; size: number }): Promise<string> {
  // ① 首次压缩（quality 80）：压缩失败回退原图（仍可走后续校验/上传）
  let path = input.path
  try {
    const first = await compressImage(input.path, { quality: 80 })
    if (first) path = first
  } catch { path = input.path }

  // ② 最长边 ≤1334：超出按比例等比缩边（低基础库不支持 compressedWidth 时保持当前结果）
  try {
    const info = await getImageInfo(path)
    if (info.width > 0 && info.height > 0 && Math.max(info.width, info.height) > MAX_EDGE) {
      const scale = MAX_EDGE / Math.max(info.width, info.height)
      const scaled = await compressImage(path, {
        compressedWidth: Math.round(info.width * scale),
        compressedHeight: Math.round(info.height * scale),
      })
      if (scaled) path = scaled
    }
  } catch { /* 尺寸读取失败不阻断：交由大小校验兜底 */ }

  // ③ 文件 ≤1MB：超限降质再压（60 → 40），压缩不再收敛即停，仍超限抛错跳过
  let size = await getFileSize(path)
  let quality = 60
  while (size > MAX_SIZE && quality >= 40) {
    const next = await compressImage(path, { quality }).catch(() => '')
    if (!next) break
    const nextSize = await getFileSize(next).catch(() => Number.MAX_SAFE_INTEGER)
    if (nextSize >= size) break
    path = next
    size = nextSize
    quality -= 20
  }
  if (size > MAX_SIZE) throw new Error('图片过大（超1MB），已跳过')
  return path
}

/**
 * 选图后的规格收敛入口（平台分派，赋值模式避免条件编译 unreachable）：
 * - 微信端：压缩 + 尺寸/大小校验（normalizeMp）；
 * - H5 端：无 wx 压缩链路，仅做大小门禁后直传。
 */
async function normalizeForPlatform(input: { path: string; size: number }): Promise<string> {
  let path: string
  // #ifdef MP-WEIXIN
  path = await normalizeMp(input)
  // #endif
  // #ifndef MP-WEIXIN
  if (input.size > MAX_SIZE) throw new Error('图片过大（超1MB），已跳过')
  path = input.path
  // #endif
  return path
}

/* ===== 添加图片：逐张 校验→上传→追加；单张失败不中断其余 ===== */
const uploading = ref(false)

async function onAdd() {
  if (props.disabled || uploading.value) return
  const remain = props.max - urls.value.length
  if (remain <= 0) return
  uploading.value = true
  try {
    const files = await pick(remain)
    for (const f of files) {
      try {
        const path = await normalizeForPlatform(f)
        const { url } = await uploadUgcImage(path)
        if (url) {
          urls.value = [...urls.value, url]
          emit('update:modelValue', [...urls.value])
        }
      } catch (e: any) {
        // 违规图片（后端 400「图片包含违规内容，无法上传」）/ 过大 / 网络失败：toast 透出，跳过该张
        uni.showToast({ title: e?.message || '图片上传失败', icon: 'none' })
      }
    }
  } catch (e: any) {
    uni.showToast({ title: e?.message || '选择图片失败', icon: 'none' })
  } finally {
    uploading.value = false
  }
}

/** 删除已选（本地移除；后端不做回收，孤儿 COS 文件由后端定期清理策略兜底） */
function onRemove(i: number) {
  if (props.disabled || uploading.value) return
  urls.value = urls.value.filter((_, idx) => idx !== i)
  emit('update:modelValue', [...urls.value])
}

/** 预览大图（评审 m4：仅未破图进入预览，urls 过滤破图；current 定位到点击那张） */
function onPreview(i: number) {
  if (brokenImages.value.has(i)) return
  const okIdx = urls.value.map((_, idx) => idx).filter((idx) => !brokenImages.value.has(idx))
  if (!okIdx.length) return
  const okUrls = okIdx.map((idx) => urls.value[idx])
  uni.previewImage({ urls: okUrls, current: okUrls[Math.max(okIdx.indexOf(i), 0)] })
}
</script>

<style scoped>
/* 3 列自适应网格：格子宽度随容器等分（sheet / 表单卡宽度不同均可复用） */
.ip-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.ip-cell {
  width: calc((100% - 32rpx) / 3);
}
/* 正方形容器：padding-bottom 撑高（小程序对 aspect-ratio 支持不稳，用经典等比盒） */
.ip-box {
  position: relative;
  width: 100%;
  height: 0;
  padding-bottom: 100%;
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--bg-placeholder);
  -webkit-tap-highlight-color: transparent;
}
.ip-thumb {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  transition: opacity var(--duration-fast) var(--ease-out);
}
.ip-thumb:active { opacity: 0.6; }
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
  top: 8rpx;
  right: 8rpx;
  width: 44rpx;
  height: 44rpx;
  border-radius: var(--radius-circle);
  background: var(--badge-dark-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  -webkit-tap-highlight-color: transparent;
  transition: opacity var(--duration-fast) ease;
}
.ip-remove::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 88rpx;
  height: 88rpx;
  transform: translate(-50%, -50%);
}
.ip-remove:active { opacity: 0.7; }

/* 添加格：浅底虚线框 + 图片语义 icon + 引导文案；上传中降透明度 */
.ip-add {
  background: var(--bg-input);
  border: 2rpx dashed var(--border-bold);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-2xs);
  transition: opacity var(--duration-fast) ease;
}
.ip-add:active { opacity: 0.6; }
.ip-add.uploading { opacity: 0.55; }
/* 禁用态（评审 m1）：提交中等 disabled 弱化，按压不再提亮（onAdd 已拦截点击） */
.ip-add.disabled,
.ip-add.disabled:active { opacity: 0.5; }
/* 禁用态（复审 MINOR）：提交中删除钮同步弱化（onRemove 已拦截点击） */
.ip-remove--off { opacity: 0.5; }
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
  .ip-loading { animation-duration: 1.6s; }
}
/* 满额计数格（评审 m3）：浅底虚线 + 轻量 n/n（绝对定位居中，不依赖 0 高等比盒的 flex） */
.ip-count { background: var(--bg-input); border: 2rpx dashed var(--border-color); box-sizing: border-box; }
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
