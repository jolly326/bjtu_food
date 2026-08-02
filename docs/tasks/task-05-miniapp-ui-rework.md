# task-05 · 小程序 UI 整改（原 task-14 W1~W16，吸收未完成项）

> 文档性质：技术负责人派工任务（部分完成，剩余待办项为当前重点）。
> 权威顺序：`docs/project_spec.md` §4（Apple Design / §4.9 红线，尤其 slot、emoji、图标、Sheet、Token）> `docs/mini-app-ui/` 各页文档 > 本任务 > 代码现状。
> 来源：原 `task-14-miniapp-ui-implementation.md`（W1~W16）+ `task-13-miniapp-ui-design.md`（定样总册 T1~T29 / §5 验收门禁）。**内容要点已全部吸收进本任务。**

## 目标
落实小程序 UI 整改：图标体系、组件抽取、P0 红线清零、各页视觉/交互升级。多数项已随多轮 UI 审计/整改（10 轮，commit 见 §git）收口；**本任务剩余待办 = 尚未完成的 W 项**。

## 状态
🔄 **进行中**（W1/W2/W3/W4/W5/W6/W7/W8 及多数详情页项已随多轮整改基本落地；剩余项见 §剩余待办）。

## 完成情况（git 轨迹）
- W1 图标体系：`IconSvg` + 46 SVG + `empty` 中性占位 + `ImageFallback`（见 task-04）。✅
- W2 组件抽取：`ImageUploader`/`SegmentTabs`/`ReviewItem`/`ApplySheet`/`FeedbackForm`/`SettingGroup`+`SettingCell`/`RelatedPickerSheet`/`SubmissionItem`/`CanteenCard`/`StallCardSingle`/`FilterSheet`/`InteractBar`/`CommentItem`/`ReportModal`/`AuthForm`/`ContributeSheet`/`StatsRow` 均已落地（`frontend/src/components/` 可见）。✅
- W3 find P0（slot 空白 + 搜索防抖 + 分类宫格去「发现」字 + 热搜配图 + 筛选 tab 升级 + EmptyState）：`find` 已卡片化重做（commit **5b051fd**、**80fb4c0**、**9f19fad**）。✅
- W4 canteen P0（V2 slot + 重构单列档口流）：并入 task-03（commit **ea70d9f**）。✅
- W5 publish-moment P0（V3 档口 id 伪造 → 走正式 API）：`RelatedPickerSheet` 已走正式 API（见 publish-moment 源码）。✅
- W6 home（广播多类型 + 食堂卡去营业态 + Banner targetType 分发 + 无横向滚动）：已落地。✅
- W7 community（MomentCard + FAB 缩放 + EmptyState + SegmentTabs）：已落地。✅
- W8 profile（SettingGroup + AuthForm 登录注册重构 + settings scroll-view + 去虚假控制）：已落地。✅
- W9 dish（SegmentTabs + 评价入口 + 爱心喜欢）：`dish` 已用 ReviewItem + 底部 action-bar，评价互动为爱心喜欢。⚠️ 详见 §剩余待办（task-10 改底部弹层）。
- W10 review-list：该页已**整体下线**（评价改详情内联，见 task-02/task-03）。✅（取消）
- W11 stall（CardSection 分组 + 菜品图圆角方图）：已落地。⚠️ 三 tab 重构见 task-09。
- W12 moment（InteractBar/CommentItem/ReportModal 组件化 + 举报弱化）：已落地。✅
- W13 review（Rating + ImageUploader + scroll-view）：已落地。✅
- W14 publish-dish / submit-stall（表单 + TagSelector + ImageUploader + scroll-view）：已落地。✅
- W15 my-moments（MomentCard + SegmentTabs）：已落地。✅（my-publish/my-submissions 已并入 messages-services）
- W16 settings/feedback/contact/webview：settings/feedback 已落地；contact 已并入 feedback；**webview 页待移除（见 task-07）**。

## §剩余待办（当前未完成项）
| 项 | 归属新任务 | 说明 |
|---|---|---|
| dish 菜品详情改底部弹层（原 W9 演化） | task-10 | 独立页 → 底部 sheet，入参 query→prop、onLoad→watch、复用 ApplySheet 抽屉范式 |
| stall 档口详情三 tab 重构（原 W11 演化） | task-09 | 横幅 + hero 卡 + 底部 tabBar（菜品/评价/档口介绍），美团外卖式 |
| webview 页移除（原 W16d 演化） | task-07 | 业务只跳外部公众号文章，砍 webview 路由与页，评估跳转来源（Banner）改如何处理 |
| moment 动态详情后端排查 | task-08 | GET /moments/{id} 返回非 200/404/401，需后端确认数据/审核可见性 |

## 收口 · 全端门禁对齐（task-13 §5，作为本任务验收基线）
1. P0 零残留：W3(V1)/W4(V2)/W5(V3) 修复且真机验证。
2. 全端列表三态（Loading/EmptyState/正常）齐备。
3. AppButton `type` 全端对齐白名单，无裸 `<button>` 主操作。
4. 表单页根容器均为 scroll-view。
5. 无 `v-for :key="index"`、无 `v-if="true"`、无硬编码 action-bar 高度、无虚假控制。
6. 图标全端矢量，无 emoji 当图标。

## 依赖
- 依赖 task-01（组件/Token）、task-04（图标）、task-02（页面架构）。
- 剩余待办依赖 task-07/08/09/10 各自独立推进。
