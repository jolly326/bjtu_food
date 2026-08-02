# task-01 · 小程序端基础设施（前端地基）

> 文档性质：技术负责人派工任务（已完成项归档）。
> 权威顺序：`docs/project_spec.md` §4（Apple Design 视觉规范 / §4.9 红线）> 本任务 > 代码现状。

## 目标
在 `frontend/`（uni-app 学生端）落地全局前端基础设施，使后续所有任务只复用、不新造：统一 request 封装、UI 视觉 Token、spring 动效工具、基础组件（DishCard/CategoryTabs/WaterfallList/EmptyState 等），并落实 §4.9 组件渲染红线（禁止 wx:for 内具名 slot 分发）。

## 状态
✅ **已完成**（随项目早期 task-01~05 落地，组件与 Token 已收敛入库）。

## 关键交付（现状可查）
- 统一请求层：`frontend/src/api/http.ts`（`code!==200` 抛异常、401 统一 `uni.$emit('auth:unauthorized')` + 清 token + Toast）。
- UI Token：`frontend/src/uni.scss`（间距 4pt 栅格、圆角、字号、语义色 token、`--press-scale` 0.97、动效 `--ease-out`/`--ease-drawer`、`--action-bar-height` 120rpx 等）+ `App.vue` 全局工具类（`.page`/`.scroll-wrap`/`.glass`/`.press`/`.enter-up`/`.skeleton`/reduced-motion 降级）。
- 基础组件（`frontend/src/components/`）：`AppButton`/`CustomTabBar`/`DishCard`/`WaterfallList`/`EmptyState`/`Rating`/`TagLabel`/`CardSection`/`SectionTitle`/`ImageSwiper`/`ImageUploader`/`SearchBar`/`StatusBadge`/`UsefulButton`/`MomentCard`/`StallCardSingle`/`StallDishRow`/`IconSvg`/`ImageFallback`/`ApplySheet`/`ContributeSheet`/`FilterSheet`/`ReviewItem`/`RelatedPickerSheet`/`SettingGroup`/`SettingCell`/`SegmentTabs`/`InteractBar`/`CommentItem`/`ReportModal`/`AuthForm`/`StatsRow`/`NicknameSheet`/`FeedbackForm` 等。
- 依赖 `CategoryTabs`/`Loading` 组件已于清理提交 **f9560c6** 删除（加载态改内联骨架屏，分类切换由 `SegmentTabs`/筛选条替代）。
- `WaterfallList` 内部直接 `import DishCard/StallCardSingle` 渲染，父级只经 `@card-click`/`@stall-click` 上抛，**禁止再传具名 slot**（§4.9 红线，实测阻断级 bug）。

## 验收
- [x] 统一请求封装、UI Token、动效工具、基础组件齐备且被后续页面复用。
- [x] `WaterfallList` 无任何父级具名 slot 残留（grep `#card` / `slot="card"` = 0）。
- [x] 全端图标统一经 `<IconSvg name="…" />` 渲染（无 emoji 当图标）。
- [x] 组件渲染红线（§4.9）不回归。

## 依赖
- 无（本项目地基，其余任务依赖本任务组件/Token）。
