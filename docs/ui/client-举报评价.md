# 举报评价 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 归属功能文档：[client-举报评价.md](../feature/client-举报评价.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。
> 落点：**无独立页面** —— 举报底部弹层 `ReportModal`（菜品详情页包内私有组件）；入口 = 评价卡三点菜单「举报评价」。


- 入口：菜品详情页评价卡片右上角**三点菜单** → 「举报评价」（危险红色态）。
- 弹层：`ReportModal` **底部弹层**（BaseSheet 统一骨架：遮罩 / grabber / 下滑关闭手势 / 安全区），内容自上而下：
  1. 处理承诺行：「请选择举报原因，举报将在 48 小时内处理」（次级浅灰小字）；
  2. **原因单选列表**：选项来自后端字典 `GET /feedback/report-reasons`（弹层打开时实时拉取，端上零硬编码；展示顺序 = 后端 `order`）；每行整行热区（浅底圆角），**单选**——选中行主色文字加粗 + 主色浅底；**无文本输入框**；
  3. 提交钮：主色实底「提交举报」，未选中原因 / 提交中禁用（半透明）。
- 字典加载失败：列表区显示「举报原因加载失败，请关闭后重试」（次级浅灰，重开弹层重拉）。
- 提交成功 → Toast「举报已提交」并关闭弹层。

## 接口数据字段（UI 精修用）

**页面**：无独立页面（`ReportModal` 底部弹层，宿主 = 菜品详情页 `pages/detail/dish/index`）

### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `ReportModal` | 页内私有 `pages/detail/dish/ReportModal.vue` | 弹层本体：处理承诺行 + 原因单选列表 + 提交钮（含字典拉取与选中态） |
| 2 | `BaseSheet` | 公共 `components/BaseSheet.vue` | 弹层骨架：遮罩 / grabber / 下滑关闭手势 / 安全区 / 标题「举报评价」/ 右上关闭钮 |
| 3 | 提交钮（页内 `view`） | `ReportModal.vue` 内联 | 主色实底「提交举报」，未选中原因 / 提交中禁用（半透明） |
| 4 | `ReviewItem`（宿主侧） | 公共 `components/ReviewItem.vue` | 入口所在：评价卡右上角常驻三点（`@more`） |
| 5 | `ActionSheet`（宿主侧） | 公共 `components/ActionSheet.vue` | 三点动作菜单，「举报评价」动作项（危险红：图标 + 文字） |
| 6 | `useReport.ts`（宿主侧编排） | 页内私有 | `openReport(reviewId)` / `submitReport(reasonValue)` 与提交中状态 |

### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `label` | `GET /feedback/report-reasons` | 原因中文标签 | `ReportModal` 原因单选行 `.rp-option-text` | 每行一个原因，整行热区、单选；选中 = 主色文字加粗 + 浅主色底 |
| 2 | `value` | 同上 | 原因机器值 | `ReportModal` 选中态判定 + 提交入参 `sub` | **端上零硬编码**（不落屏展示） |
| 3 | `order` | 同上 | 展示顺序 | `ReportModal` 列表顺序 | 按后端返回顺序渲染，**端上不排序** |
| 4 | 被举报评价 `id` | `ReviewVO.id`（宿主页经 `openReport(rv.id)` 注入） | 被举报评价 ID | 无界面（提交时作 `relatedId`） | 零可见 UI |
| 5 | 字典加载态 | 端上 `reasons` / `reasonsFailed` | 加载中 / 失败 | `ReportModal` `.rp-empty` | 「加载中…」/ 失败「举报原因加载失败，请关闭后重试」（重开弹层重拉） |
| 6 | 承诺行 | 端内静态文案 | 处理预期 | `ReportModal` `.rp-note` | 「请选择举报原因，举报将在 48 小时内处理」（次级灰小字） |
| 7 | 提交中态 | 端上 `submitting` | 提交在途 | 提交钮文案与禁用态 | 「提交中…」+ 半透明，防重复提交 |

**出参消费**：`GET /feedback/report-reasons` → `value` / `label` / `order`（三项全消费，见上表）
**入参提交**：`POST /feedback` → `type='report'` / `sub`（选中原因 `value`）/ `relatedType='review'` / `relatedId`（被举报评价 `id`）
**错误码**：`400` 举报必须指定关联对象 / 举报原因非法 / 你已举报过该内容 / 二级分类仅 `report` 有效 / 补充文本安检违规｜IP 限频 `400`「提交过于频繁」
**控件类型**：`BaseSheet` 底部弹层（遮罩 / grabber / 下滑关闭 / 安全区）、单选项行（`role="radio"`，整行热区）、禁用态提交钮
