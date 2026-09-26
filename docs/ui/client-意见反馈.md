# 意见反馈 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 归属功能文档：[client-意见反馈.md](../feature/client-意见反馈.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。

- 入口：
  - 「我的」页宫格「意见反馈」→ `feedbackUrl()`（缺省「反馈问题」模式）；
  - 菜品详情页信息卡位置行右侧「**信息有误？**」（三级灰小字 + arrow 图标，与「展开」同文本档位、不单独占行）→ `feedbackUrl('update', dishId)`（预选当前菜品，跳过搜索）。
- 页面：`pages/feedback/index`（页底 `--bg-warm` 奶油米白；页头「意见反馈」）
  - 顶部**分段控件**（两段等宽）：`--radius-pill` 浅底轨道（`--bg-soft` + `--spacing-2xs` 内距）内两段；选中段白卡浮起（`--bg-card` + `--shadow-warm`，文字主色加粗），未选中 `--text-secondary`；按压 opacity 0.7。「反馈问题」/「更新信息」；**切换互不清空**——两模式各自保留草稿。
  - 表单外层 Q 卡（`--radius-card` + `--shadow-warm`，内部靠间距分层），动态表单随模式切换：
    - **反馈问题表单**（包内私有 `IssueForm.vue`）：
      - 「想说啥」textarea（必填，≤1000 字，超 800 字右上计数 `n/1000`；占位示例文案引导）；
      - 配图（选填，`ImagePicker` ≤3 张，安检上传，提交中禁选）。
    - **更新信息表单**（包内私有 `UpdateForm.vue`；纯表单、**无文字说明输入框**）：
      1. 「要更新哪道菜」选择行 → 包内私有 `ListPickerSheet` **菜品选择弹层**（标题「选择菜品」+ 搜索框，防抖搜 `GET /dishes?keyword=`、竞态守卫丢弃过期响应；行 = 菜名 + 「食堂 · 档口」副行；空态 = 「输入关键词搜索菜品」/「没搜到…换个关键词试试」；**页面内唯一弹层**）；已选展示菜品卡（名称 + 位置 + 「重选」）与详情加载态「正在载入菜品信息…」；
      2. 选定后按 `GET /dishes/{id}` **预填全量表单**：名称 input（必填）/ 价格（元，digit 键盘，占位「如 12.5」）/ **食堂名**（文本输入，必填，预填详情 `canteenName`，可改）/ **档口**（文本输入，必填，预填详情 `stallName`，可改）/ 口味标签 chips / 食材 chips（预填详情机器值经四维字典译中文、未命中回落原值；自由输入添加、去重、≤12 项）/ 图片（`ImagePicker` ≤9 张，预填菜品现有图可增删）；预填未完成禁止提交；
      3. 字段错误 = 错误边框 + 行内文案；首错字段 `scroll-into-view` 定位。
  - 提交区（表单最下方，随内容滚动）：处理承诺小字（issue「我们会在 48 小时内处理你的反馈，处理结果将通过站内通知告知」/ update「提交后由管理员核实，确认无误后更新菜品信息」）+ `AppButton`（文案「提交反馈」/「提交更新」；提交中「提交中…」+ loading）；`canSubmit` 门禁，置灰点击由外层热区 toast 缺失项；提交中防重复提交。
  - 成功：Toast「已提交，感谢反馈」+ 2 秒无操作自动返回；两模式表单统一重置。
- 落点参数：`mode=issue|update`（缺省 issue）、`dishId`（update 模式深链预选，直接拉详情预填）。

## 接口数据字段（UI 精修用）

**页面**：`pages/feedback/index`（`mode=issue|update` 双模式；分包 `pages/feedback/`；二级页，**无 TabBar**）

### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `AppHeader` | 公共 `components/AppHeader.vue` | 页头：居中标题「意见反馈」+ 返回箭头（有返回栈则 `navigateBack`，否则回首页） |
| 2 | 分段控件 `.seg`（页内内联） | `pages/feedback/index.vue` 内联 | 「反馈问题 / 更新信息」两段等宽切换（选中白卡浮起；**切换互不清空、各自保留草稿**） |
| 3 | `IssueForm` | 页内私有 `pages/feedback/IssueForm.vue` | issue 字段区：「想说啥」textarea（≤1000 字，超 800 字显 `n/1000`）+ 配图 |
| 4 | `UpdateForm` | 页内私有 `pages/feedback/UpdateForm.vue` | update 字段区：菜品选择行 / 已选菜品卡 + 名称 / 价格 / 食堂名 / 档口 + 口味 chips / 食材 chips + 图片 |
| 5 | `ListPickerSheet` | 页内私有 `pages/feedback/ListPickerSheet.vue`（骨架 = `BaseSheet`） | 菜品选择弹层：搜索框（内部防抖） + 候选行（缩略图 + 菜名 + 「食堂 · 档口」）+ `#empty` 空态 |
| 6 | `ImagePicker` | 公共 `components/ImagePicker.vue` | issue 配图（≤3）/ update 菜品图（≤9，预填现有图可增删） |
| 7 | `AppButton` | 公共 `components/AppButton.vue` | 提交按钮（「提交反馈」/「提交更新」/「提交中…」；`disabled` = 门禁不通过） |
| 8 | `IconSvg` | 公共 `components/IconSvg.vue` | 选择行箭头 `arrow` / chips 删除叉 `close` |
| 9 | 提交说明行 `.submit-note` + 外层热区 `.submit-area`（页内内联） | 页内内联 | 处理承诺文案 + 承接「置灰态点击」的缺失项 Toast |
| 10 | `useFeedback.ts` | 页内私有编排 | 双模式状态、草稿、`canSubmit` 门禁、搜索竞态守卫、提交与 2 秒自动返回 |

### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | `records[].id` | `GET /dishes?keyword=`（搜索候选） | 菜品 ID | `ListPickerSheet` 选项 `key` / 提交路径 `{id}` | 零可见 UI |
| 2 | `records[].name` | 同上（端上 `DishListItem.name`） | 菜名 | `ListPickerSheet` 行主文案 + 已选菜品卡名称 | 单行省略 |
| 3 | `records[].canteenName` / `records[].stallName` | 同上（端上别名 `canteen` / `stallName`） | 食堂 / 档口 | `ListPickerSheet` 行副文案（`sub` = 「食堂 · 档口」）+ 已选菜品卡位置行 | 次级灰小字 |
| 4 | `records[].coverImage` | 同上 | 封面图 | `ListPickerSheet` 行左侧缩略图（`plain` 行样式） | 72rpx 圆角图；空则不渲染 |
| 5 | `name` / `price` / `canteenName` / `stallName` | `GET /dishes/{id}`（`DishDetailVO`） | 菜品名称 / 现价 / 食堂名 / 档口名 | `UpdateForm` 名称行 / 价格行 / 食堂名行 / 档口行（**预填**） | 价格：分 → 元展示（`fenToYuan`），digit 键盘，占位「如 12.5」 |
| 6 | `flavorTags` / `ingredients` | 同上（**机器值数组**） | 口味 / 主料 | `UpdateForm` chips（预填 + 用户增删） | 机器值经四维字典译中文；未命中回落原值 |
| 7 | `images` | 同上 | 菜品现有图 | `ImagePicker` 网格（update 模式，≤9） | 可增删，经安检上传 |
| 8 | `field` / `value` / `label` | `GET /dishes/attributes`（经 `stores/dish-attribute`） | 四维字典 | `UpdateForm` chips 中文文案 | 端上零硬编码映射 |
| 9 | `text` / `images`（本地草稿） | 用户输入 / `ImagePicker` | issue 正文与配图 | `IssueForm` textarea + 字数计数 + 图片网格 | 切换模式不清空 |
| 10 | 字段错误 `errors` | 端上门禁（`canSubmit` / 字段校验） | 缺失或非法项 | 对应字段下方 `.field-error` + 错误边框 | 首个错误字段 `scroll-into-view` 定位 |
| 11 | 提交说明 | 端内静态文案 | 处理承诺 | `.submit-note` | issue：「我们会在 48 小时内处理你的反馈，处理结果将通过站内通知告知」；update：「提交后由管理员核实，确认无误后更新菜品信息」（**不暗示提交即生效**） |
| 12 | 提交结果 | `POST /feedback` / `POST /dishes/{id}/correction` 成功（`data` = null） | 成功 | 无界面（Toast「已提交，感谢反馈」+ 2 秒自动返回；两模式表单统一重置） | — |
| 13 | 失败提示 | `400` / `4001` 响应 `message` | 失败原因 | 无界面（Toast 直透，兜底「没发出去，再试一次」） | 停留本页保留草稿 |

**入参提交**
| 接口 | 字段 |
|---|---|
| `POST /feedback`（issue） | `type='issue'` / `content`（≤1000 字）/ `images`（≤3） |
| `POST /dishes/{id}/correction`（update） | `name` / `price`（**整数分**，端上 `yuanToFen`）/ `canteenName` / `stallName` / `flavorTags[]` / `ingredients[]` / `images[]`（≤9） |
| `GET /dishes`（候选搜索） | `keyword` / `page` / `pageSize` |

**错误码**：`400` 反馈类型非法 / 反馈内容不能为空 / 菜品名称不能为空或超 64 字 / 含敏感词 / 价格必须为大于 0 的整数（单位：分）/ 菜品图片最多 N 张 / 图片地址不合法 / IP 限频「提交过于频繁」（提示剩余秒数）/ 文本安检 `risky`（含未知 / 缺失态）｜`4001` 菜品不存在（纠错）
**控件类型**：`scroll-view`、分段控件 `seg`（互不清空草稿）、`textarea`、`input`（名称 / 价格 digit / chips 自由输入）、chips 增删、菜品选择弹层（搜索 + 列表 + 空态）、字段级错误滚动定位
