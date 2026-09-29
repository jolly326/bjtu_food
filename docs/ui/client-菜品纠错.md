# 菜品信息纠错 — 页面 UI 设计稿

> 所属端：学生端（微信小程序）
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。
> **2026-09-27 定稿**：本页自意见反馈页**迁出为独立页面**（[`pages/correction/`](../../client/src/pages/correction/)）。
> 边界：**菜品纠错 = 依附某条菜品数据的专项修正**（自带 `dishId`、字段结构化）；**意见反馈 = 面向小程序本身的通用反馈**（见 [client-意见反馈.md](./client-意见反馈.md)）。两者互不耦合。

- **入口（唯一）**：菜品详情页**底栏「反馈错误」按钮** → `correctionUrl(dishId)`（`utils/routes`，禁止手拼 URL）⇒ 进页即按 `dishId` 拉详情**预填**。
- 页面：`pages/correction/index`（独立分包 `pages/correction/`；二级页，**无 TabBar**；页底随全局 `page{}` 奶油米白 + `PageWallpaper`；页头 `AppHeader`「菜品信息纠错」+ 返回）

## 结构与字段（自上而下）

白色大卡片（圆角 **16rpx**（`--radius-btn`）+ `--shadow-card`，内距 `--spacing-lg`，页面左右 `--spacing-md` 留边）内为**纠错字段区**：

| # | 字段 | 必填 | 呈现 | 说明 |
|---|---|---|---|---|
| 1 | 要更新哪道菜 | ✅ | 选择行 → `ListPickerSheet` 弹层（搜索防抖 + 竞态守卫 + `#empty` 空态）；已选展示菜品卡（名称 + 「食堂 · 档口」）+ 重选 | 深链进入时**已自动选定并预填**，通常无需操作 |
| 2 | 名称 | ✅ | 单行 input（预填详情 `name`，可改） | 敏感词由后端 400 message 直透 |
| 3 | 价格（元） | ✅ | 单行 input，digit 键盘，占位「如 12.5」（预填详情 `price`） | 提交前经 `yuanToFen` 转**分**（金额红线） |
| 4 | 食堂名 | ✅ | 单行 input（预填详情 `canteenName`，可改，自由文本） | — |
| 5 | 档口 | ✅ | 单行 input（预填详情 `stallName`，可改） | 无档口字典，自由文本 |
| 6 | 口味标签 / 食材 | — | chips（预填详情机器值经四维字典译中文、未命中回落原值；自由输入增删、去重） | 字典 `GET /dishes/attributes`（失败静默） |
| 7 | 图片 | — | `ImagePicker`（`single` 单图虚线框 + `max=1`）：预填菜品**首图**，可替换/删除 | 破图走统一 `ImagePlaceholder` |

**提交校验**：预填未完成禁止提交；字段错误 = 错误边框 + 行内文案 + 首错 `scroll-into-view` 定位。

**提交区**（表单最下方，随内容滚动）：说明行「提交后由管理员核实，确认无误后更新菜品信息」（**不暗示提交即生效**）+ 主按钮「**提交纠错**」（未填完置灰、置灰点击 toast 缺失项；提交中禁用防重复）；成功 Toast「已提交，感谢反馈」+ 1.5 秒返回上一页。

## 接口数据字段（UI 精修用）

**页面**：`pages/correction/index`（独立分包；二级页，无 TabBar）

### 组件清单

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `AppHeader` | 公共 `components/AppHeader.vue` | 页头：居中标题「菜品信息纠错」+ 返回 |
| 2 | `UpdateForm` | 页内私有 `pages/correction/UpdateForm.vue` | 纠错字段区（选菜行 / 名称 / 价格 / 食堂名 / 档口 / 口味·食材 chips / 图片） |
| 3 | `ListPickerSheet` | 页内私有 `pages/correction/ListPickerSheet.vue`（骨架 = `BaseSheet`） | 菜品选择弹层（搜索 + 候选行 + `#empty` 空态） |
| 4 | `ImagePicker` | 公共 `components/ImagePicker.vue` | 图片（`single` + `max=1`） |
| 5 | `AppButton` | 公共 `components/AppButton.vue` | 提交按钮（「提交纠错」/「提交中…」） |
| 6 | `IconSvg` | 公共 `components/IconSvg.vue` | 选择行箭头 `arrow` / chips 删除叉 `close` |
| 7 | `useCorrection.ts` | 页内私有编排 | 深链预填 + 搜索竞态守卫 + `canSubmit` 门禁 + 提交与自动返回 |

### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示位置 | 呈现 |
|---|---|---|---|---|---|
| 1 | `name` / `price` / `canteenName` / `stallName` | `GET /dishes/{id}`（`DishDetailVO`） | 名称 / 现价 / 食堂 / 档口 | `UpdateForm` 各行（**预填**） | 价格分 → 元（`fenToYuan`） |
| 2 | `flavorTags` / `ingredients` | 同上（机器值数组） | 口味 / 主料 | chips | 经四维字典译中文 |
| 3 | `images` | 同上 | 菜品图 | `ImagePicker`（≤1） | **只取首图**，可替换 |
| 4 | `field` / `value` / `label` | `GET /dishes/attributes`（经 `stores/dish-attribute`） | 四维字典 | chips 文案 | 端上零硬编码映射 |
| 5 | `records[].id` / `name` / `coverImage` / `canteen`+`stallName` | `GET /dishes?keyword=`（换菜搜索） | 候选菜品 | `ListPickerSheet` 行 | 名称 + 「食堂 · 档口」+ 72rpx 缩略图 |
| 6 | 提交结果 | `POST /dishes/{id}/correction` 成功（`data` = null） | 成功 | 无界面（Toast + 1.5 秒返回） | — |
| 7 | 失败提示 | `400` / `4001` 响应 `message` | 失败原因 | 无界面（Toast 直透，兜底「没发出去，再试一次」） | `4001` 菜品不存在 |

**入参提交**：`POST /dishes/{id}/correction` —— `name` / `price`（**整数分**，端上 `yuanToFen`）/ `canteenName` / `stallName` / `flavorTags[]` / `ingredients[]` / `images[]`（**≤1**，服务端上限 9）；`dishId` 在路径中；公开匿名、IP 限频。

**错误码**：`400` 菜品名称不能为空或超 64 字 / 含敏感词 / 价格必须为大于 0 的整数（单位：分）/ 图片地址不合法 / IP 限频「提交过于频繁」｜`4001` 菜品不存在

**控件类型**：`scroll-view`、`input`（名称 / 价格 digit / chips 自由输入）、chips 增删、菜品选择弹层（搜索 + 列表 + 空态）、`ImagePicker`（单图虚线框）、字段级错误滚动定位
