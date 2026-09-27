# 意见反馈 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 归属功能文档：[client-意见反馈.md](../feature/client-意见反馈.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。
> **2026-09-27 定稿**：页面为**一张表单、无页签**，固定「类型（**3 选 1**）+ 具体描述 + 截图」一套字段；
> **菜品信息纠错已迁出为独立页面**（[client-菜品纠错.md](./client-菜品纠错.md)，仅菜品详情页底栏「反馈错误」进入）——本页不再包含该类型与其结构化表单。

- 入口：
  - 「我的」页宫格「意见反馈」→ `feedbackUrl()`（缺省）⇒ 类型待用户选、恢复本地草稿；
  - ~~菜品详情页底栏「反馈错误」~~ ⇒ **已迁至独立页面** [client-菜品纠错.md](./client-菜品纠错.md)（`correctionUrl(dishId)`）；本页不再承接菜品纠错。
- 页面：`pages/feedback/index`（页底随全局 `page{}` 底色 = 奶油米白 + `PageWallpaper` 壁纸层；页头 `AppHeader`「意见反馈」+ 返回）

## 表单卡（白色大卡片：圆角 **16rpx**（`--radius-btn`）+ `--shadow-card`，内距 `--spacing-lg`，页面左右 `--spacing-md` 留边）自上而下

### 1. 反馈类型（必填，竖排单选；整行可点、行高 ≥88rpx）

选中项**左侧橙色勾**（`.type-check` 圆形描边 → 选中填 `--color-primary` + 白 `check` 图标；纯图形，对读屏隐藏，选中语义由 `role="radio"` + `aria-checked` 表达）。四项（`label` 后括号内为 `hint`，缺省不渲染）：

| 顺序 | `value`（提交值） | `label` | `hint`（卡片副标题） |
|---|---|---|---|
| 1 | `bug` | 程序功能Bug | 页面、图片、评价等程序异常 |
| 2 | `suggestion` | 产品功能建议 | 新功能、交互优化好点子 |
| 3 | `other` | 其他相关问题 | 其余平台相关问题反馈 |

> `value` 必须落在服务端写入白名单内（非法值 400）；文案常量在端上（`types/feedback.ts` 的 `FEEDBACK_TYPES`），若后续要「改文案不发版」，改为服务端下发即可、渲染结构不变。

### 2. 字段区（固定一套，不随类型切换结构）

**A. 类型 ≠「菜品信息纠错」**

2.1 **具体描述**（必填，多行输入）：上限 **600 字**；**字数 `n/600` 常显在「具体描述」标题行右上角**（等宽数字）。占位文案随选中类型切换：

| 类型 | 占位文案 |
|---|---|
| `bug` | 请描述bug现象、复现步骤 |
| `suggestion` | 描述你希望新增或改动的功能想法 |
| `other` | 描述你遇到的平台相关问题 |

（未选类型时占位为通用引导「先选一个反馈类型，再描述你遇到的问题」。）

2.2 **上传截图**（选填，**最多 1 张**）：`ImagePicker` 单图形态（`single`）—— 空态 = **虚线方框**（200rpx 见方，`2rpx dashed --border-bold`）+ 加号；有图 = 缩略图 + 右上角删除叉，点图看大图（微信原生预览）；**破图 → 统一 `ImagePlaceholder`（灰底 + `image-broken`）**，点它不进入预览。

> **菜品纠错**（选菜 + 七字段预填那套）**已迁出为独立页面** → [client-菜品纠错.md](./client-菜品纠错.md)；
> 本页只保留上面这一套通用反馈字段，**不再有第二套表单、不再随类型切换结构**。

### 3. 【提交反馈】按钮

未选类型 / 描述为空（或纠错表单未填完）⇒ 置灰（置灰点击由外层热区 toast 缺失项）；提交中禁用防重复；成功 Toast「已提交，感谢反馈」+ 2 秒自动返回上一页。

**本地草稿**：仅缓存「类型 + 描述文本」（`uni.setStorageSync('feedback_draft')`）；图片是 COS 地址、重进可能失效，**不缓存**；提交成功后清除。

## 接口数据字段（UI 精修用）

**页面**：`pages/feedback/index`（分包 `pages/feedback/`；二级页，**无 TabBar**）

### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `AppHeader` | 公共 `components/AppHeader.vue` | 页头：居中标题「意见反馈」+ 返回（有返回栈则 `navigateBack`，否则回首页） |
| 2 | `IssueForm` | 页内私有 `pages/feedback/IssueForm.vue` | 类型竖排单选 +（非纠错类型时）具体描述（600 字 + 右上角计数）+ 单图截图 + 小字提示 |

| 5 | `ImagePicker` | 公共 `components/ImagePicker.vue` | 图片上传（单图形态：`single` 单图虚线框 + `max=1`）；**纠错类型复用同一件，同为 ≤1 张** |
| 6 | `AppButton` | 公共 `components/AppButton.vue` | 提交按钮（「提交反馈」/「提交中…」；`disabled` = 门禁不通过） |
| 7 | `IconSvg` | 公共 `components/IconSvg.vue` | 类型选中勾 `check` / 选择行箭头 `arrow` / chips 删除叉 `close` |
| 8 | 提交区 `.submit-area`（页内内联） | 页内内联 | 承接「置灰态点击」的缺失项 Toast |
| 9 | `useFeedback.ts` | 页内私有编排 | 类型驱动的字段区状态 + 草稿 + 纠错预填 + 搜索竞态守卫 + `canSubmit` 门禁 + 提交与 2 秒自动返回 |
| — | ~~分段控件 `.seg`~~ | — | **已退役（2026-09-27）**：页面无页签 |

### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | 类型四项（`value`/`label`/`hint`/`placeholder`） | **端上常量** `types/feedback.ts` 的 `FEEDBACK_TYPES`（提交值受服务端白名单校验） | 类型选项与占位文案 | `IssueForm` 单选 + 描述框占位 | 选中项左侧橙色勾；占位随类型切换 |
| 2 | `content`（本地表单态） | 用户输入 | 具体描述 | `IssueForm` textarea + 计数 | **上限 600 字**，`n/600` 常显标题行右上角 |
| 3 | `images`（本地表单态） | `ImagePicker`（安检上传） | 截图 | `IssueForm` 单图虚线框 | ≤1 张；破图走统一占位 |
| 4 | 草稿（`type` + `content`） | `uni.getStorageSync('feedback_draft')` | 本地草稿 | 进页回填类型与描述 | 图片不缓存 |
| 8 | 字段错误 `errors` | 端上门禁 | 缺失或非法项 | 对应字段下方 `.field-error` + 错误边框 | 首个可定位错误字段 `scroll-into-view` |
| 9 | 提交结果 | `POST /feedback` / `POST /dishes/{id}/correction` 成功 | 成功 | 无界面（Toast「已提交，感谢反馈」+ 2 秒自动返回；表单重置、草稿清除） | — |
| 10 | 失败提示 | `400` / `4001` 响应 `message` | 失败原因 | 无界面（Toast 直透，兜底「没发出去，再试一次」） | 停留本页保留草稿 |

**入参提交**
| 接口 | 字段 |
|---|---|
| `POST /feedback` | `type` = `bug` / `suggestion` / `other`（服务端白名单校验）/ `content`（端上 ≤600 字、服务端 ≤1000）/ `images`（**≤1**） |

**错误码**：`400` 反馈类型非法 / 反馈内容不能为空 / 含敏感词 / IP 限频「提交过于频繁」/ 文本安检 `risky`
（菜品纠错的错误码见 [client-菜品纠错.md](./client-菜品纠错.md)）

**控件类型**：`scroll-view`、竖排单选（`role="radiogroup"` + `role="radio"` + `aria-checked`）、`textarea`（600 字 + 右上角计数 + 动态占位）、`ImagePicker`（单图虚线框形态）、字段级错误滚动定位
