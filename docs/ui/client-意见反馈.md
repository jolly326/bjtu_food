# 意见反馈 — 页面 UI 设计稿

> 所属端：学生端（微信小程序） ｜ 归属功能文档：[client-意见反馈.md](../feature/client-意见反馈.md)
> **口径分工**：**页面 UI 设计口径以本文件为唯一真源**，功能流程 / 接口 / 字段 / 数据落库口径以功能文档为准。
> **2026-09-27 改版**：原「顶部分段控件 + 双模式（反馈问题 / 更新信息）」改为**单表单**；页面**无页签、无模式切换入口**（见下「两种形态」）。

- 入口（**两种进入方式决定形态，页面上不暴露切换**）：
  - 「我的」页宫格「意见反馈」→ `feedbackUrl()`（缺省）⇒ **单表单形态**；
  - 菜品详情页**底栏「反馈错误」按钮** → `feedbackUrl('update', dishId)` ⇒ **纠错形态**（进页即预选菜品、跳过搜索）。
- 页面：`pages/feedback/index`（页底随全局 `page{}` 底色 = 奶油米白 + `PageWallpaper` 壁纸层；页头 `AppHeader`「意见反馈」+ 返回）

## 形态一 · 单表单（默认，「我的」页进入）

白色圆角表单卡（`--radius-card` + `--shadow-card`，内距 `--spacing-lg`）自上而下：

1. **反馈类型**（必填，竖排单选；整行可点、行高 ≥88rpx）：
   选中项**左侧橙色勾**（`.type-check` 圆形描边 → 选中填 `--color-primary` + 白 `check` 图标；纯图形，对读屏隐藏，选中语义由 `role="radio"` + `aria-checked` 表达）。四项（`label` 后的括号说明为 `hint`，缺省不渲染）：

   | 顺序 | `value`（提交值） | `label` | `hint` | 描述框占位（选中后切换） |
   |---|---|---|---|---|
   | 1 | `bug` | 小程序功能Bug | 页面报错、图片加载、评价展示/提交异常等程序问题 | 请描述bug现象、复现步骤，有截图可以附上 |
   | 2 | `suggestion` | 产品功能建议 | —（无） | 描述你希望新增或改动的功能想法 |
   | 3 | `error` | 菜品信息纠错 | 菜品名称、价格、档口、配图等资料错误 | 写明菜品名称、错误内容以及正确信息 |
   | 4 | `other` | 其他平台相关问题 | —（无） | 描述你遇到的平台相关问题 |

   - `value` 必须落在服务端写入白名单内（非法值 400）；文案常量当前在端上（`types/feedback.ts` 的 `FEEDBACK_TYPES`），如需服务端下发以支持「改文案不发版」，换数据源即可、渲染结构不变。
   - 未选类型时描述框占位为通用引导「先选一个反馈类型，再描述你遇到的问题」（不得出现空占位）。

2. **具体描述**（必填，多行输入）：`maxlength=1000`、自动增高；**超 800 字**后右下角显示 `n/1000`（等宽数字）；占位文案随选中类型切换（见上表）。
3. **上传截图**（选填，**最多 1 张**）：`ImagePicker` 的单图形态（`single`）——空态 = **虚线方框**（200rpx 见方，`2rpx dashed --border-bold`）+ 加号；有图 = 缩略图 + 右上角删除叉，点图看大图（微信原生预览）；**破图 → 统一 `ImagePlaceholder`（灰底 + `image-broken`）**，且点它不进入预览；满额后不渲染 `n/n` 计数格。
4. **小字提示**（只读）：**「提交内容将由项目维护者查看」**（原「48 小时内处理 + 站内通知」属对外承诺，2026-09-27 随本页改版撤下）。
5. **【提交反馈】按钮**：未选类型 / 描述为空 ⇒ 置灰（置灰点击由外层热区 toast 缺失项）；提交中禁用防重复；成功 Toast「已提交，感谢反馈」+ 2 秒自动返回上一页。

**本地草稿**：仅缓存「类型 + 描述文本」（`uni.setStorageSync('feedback_draft')`，键内结构 `{ type, content }`）；图片是 COS 地址、重进可能失效，**不缓存**；提交成功后清除。进入页面时若有草稿则回填。

## 形态二 · 纠错表单（仅 `mode=update`，菜品详情页跳入）

与改版前**一致**（只是页面上不再有页签）：菜品选择弹层（`ListPickerSheet`）→ 按 `GET /dishes/{id}` 预填七字段（名称 / 价格 / 食堂名 / 档口 / 口味 chips / 食材 chips / 图片 ≤9）→ 用户只改差异项 → 提交 `POST /dishes/{id}/correction`；预填未完成禁止提交；提交区说明行「提交后由管理员核实，确认无误后更新菜品信息」（不暗示提交即生效）。

## 接口数据字段（UI 精修用）

**页面**：`pages/feedback/index`（分包 `pages/feedback/`；二级页，**无 TabBar**）

### 组件清单（本界面需要哪些组件）

| # | 组件 | 来源 | 在本页做什么 |
|---|---|---|---|
| 1 | `AppHeader` | 公共 `components/AppHeader.vue` | 页头：居中标题「意见反馈」+ 返回箭头（有返回栈则 `navigateBack`，否则回首页） |
| 2 | `IssueForm` | 页内私有 `pages/feedback/IssueForm.vue` | **单表单字段区**：类型竖排单选（`.type-list` / `.type-row` / `.type-check`）+ 描述 textarea + 单图截图 + 小字提示 |
| 3 | `ImagePicker` | 公共 `components/ImagePicker.vue` | 截图上传（`single` 单图形态 + `max=1`）；**纠错形态**复用同一件（≤9 张） |
| 4 | `UpdateForm` | 页内私有 `pages/feedback/UpdateForm.vue` | 纠错形态字段区（选菜行 / 名称 / 价格 / 食堂名 / 档口 / 口味·食材 chips / 图片） |
| 5 | `ListPickerSheet` | 页内私有 `pages/feedback/ListPickerSheet.vue`（骨架 = `BaseSheet`） | 纠错形态的菜品选择弹层（搜索 + 候选行 + `#empty` 空态） |
| 6 | `AppButton` | 公共 `components/AppButton.vue` | 提交按钮（「提交反馈」/「提交更新」/「提交中…」；`disabled` = 门禁不通过） |
| 7 | `IconSvg` | 公共 `components/IconSvg.vue` | 类型选中勾 `check` / 选择行箭头 `arrow` / chips 删除叉 `close` |
| 8 | 提交区 `.submit-area`（页内内联） | 页内内联 | 承接「置灰态点击」的缺失项 Toast；说明行仅纠错形态显示 |
| 9 | `useFeedback.ts` | 页内私有编排 | 单表单状态 + 草稿 + 纠错形态状态 + 搜索竞态守卫 + `canSubmit` 门禁 + 提交与 2 秒自动返回 |
| — | ~~分段控件 `.seg`（页内内联）~~ | — | **已退役（2026-09-27）**：页面无页签 |

### 有哪些数据要显示、显示在哪个组件

| # | 数据（字段） | 来源 | 中文含义 | 显示在哪个组件 | 呈现位置 / 形式 |
|---|---|---|---|---|---|
| 1 | 反馈类型四项（`value` / `label` / `hint` / `placeholder`） | **端上常量** `types/feedback.ts` 的 `FEEDBACK_TYPES`（提交值受服务端白名单校验） | 类型选项与文案 | `IssueForm` 类型竖排单选 + 描述框占位 | 选中项左侧橙色勾；括号说明为 `hint`；占位随类型切换 |
| 2 | `content`（本地表单态） | 用户输入 | 具体描述 | `IssueForm` textarea + 字数计数 | 超 800 字显示 `n/1000` |
| 3 | `images`（本地表单态） | `ImagePicker`（安检上传） | 截图 | `IssueForm` 单图虚线框 | ≤1 张；破图走统一占位 |
| 4 | 草稿（`type` + `content`） | `uni.getStorageSync('feedback_draft')` | 本地草稿 | 进入页面时回填类型与描述 | 图片不缓存 |
| 5 | `name` / `price` / `canteenName` / `stallName` | `GET /dishes/{id}`（`DishDetailVO`） | 纠错形态预填字段 | `UpdateForm` 各行 | 价格：分 → 元（`fenToYuan`），digit 键盘 |
| 6 | `flavorTags` / `ingredients` | 同上（**机器值数组**） | 口味 / 主料 | `UpdateForm` chips | 机器值经四维字典译中文；未命中回落原值 |
| 7 | `field` / `value` / `label` | `GET /dishes/attributes`（经 `stores/dish-attribute`） | 四维字典 | `UpdateForm` chips 中文文案 | 端上零硬编码映射（**仅纠错形态加载**） |
| 8 | 字段错误 `errors` | 端上门禁（`canSubmit` / 字段校验） | 缺失或非法项 | 对应字段下方 `.field-error` + 错误边框 | 首个可定位错误字段 `scroll-into-view` |
| 9 | 提交结果 | `POST /feedback` / `POST /dishes/{id}/correction` 成功（`data` = null） | 成功 | 无界面（Toast「已提交，感谢反馈」+ 2 秒自动返回；表单重置、草稿清除） | — |
| 10 | 失败提示 | `400` / `4001` 响应 `message` | 失败原因 | 无界面（Toast 直透，兜底「没发出去，再试一次」） | 停留本页保留草稿 |

**入参提交**
| 接口 | 字段 |
|---|---|
| `POST /feedback`（单表单） | `type` = `bug` / `suggestion` / `error` / `other`（服务端写入白名单校验）/ `content`（≤1000 字）/ `images`（**≤1**） |
| `POST /dishes/{id}/correction`（纠错形态） | `name` / `price`（**整数分**，端上 `yuanToFen`）/ `canteenName` / `stallName` / `flavorTags[]` / `ingredients[]` / `images[]`（≤9） |
| `GET /dishes`（纠错形态候选搜索） | `keyword` / `page` / `pageSize` |

**错误码**：`400` 反馈类型非法 / 反馈内容不能为空 / 含敏感词 / IP 限频「提交过于频繁」（提示剩余秒数）/ 文本安检 `risky`；纠错形态另有 `400` 菜品名称超 64 字 / 价格必须为大于 0 的整数（单位：分）/ 图片地址不合法 与 `4001` 菜品不存在

**控件类型**：`scroll-view`、竖排单选（`role="radiogroup"` + `role="radio"` + `aria-checked`）、`textarea`（动态占位 + 字数计数）、`ImagePicker`（单图虚线框形态）、`input`（纠错形态：名称 / 价格 digit / chips 自由输入）、菜品选择弹层（纠错形态）、字段级错误滚动定位
