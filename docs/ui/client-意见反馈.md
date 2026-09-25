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

**页面**：`pages/feedback/index`（`mode=issue|update` 双模式）

**出参消费**
| 接口 | 字段 | 端上用途 |
|---|---|---|
| `GET /dishes`（`PageResult<DishListItemVO>`） | `records[].id` / `name` / `canteen` / `stallName` / `coverImage` | 菜品选择弹层：主键 / 菜名 / 位置副行 / 缩略图 |
| `GET /dishes/{id}`（`DishDetailVO`） | `name` / `price` / `originalPrice` / `canteen` / `stallName` / `flavorTags` / `ingredients` / `images` / `image` / `rating` | update 模式**预填全量表单**（价格按元展示、图可增删） |
| `GET /dishes/attributes` | `field` / `value` / `label` | 口味 / 食材 chips 中文（机器值 → 中文，未命中回落原值） |

**入参提交**
| 接口 | 字段 |
|---|---|
| `POST /feedback`（issue） | `type='issue'` / `content`（≤1000 字）/ `images`（≤3） |
| `POST /dishes/{id}/correction`（update） | `name` / `price`（**整数分**）/ `canteenName` / `stallName` / `flavorTags[]` / `ingredients[]` / `images[]` |
| `GET /dishes`（搜索候选） | `keyword` / `page` / `pageSize` |

**错误码**：`400` 反馈类型非法 / 反馈内容不能为空 / 菜品名称不能为空 / 价格必须为大于 0 的整数（单位：分）/ 菜品图片最多 N 张 / 图片地址不合法 / IP 限频「提交过于频繁」｜`4001` 菜品不存在（纠错）

**UI 组件**：公共 `AppHeader` / `AppButton` / `ImagePicker` / `IconSvg` / `BaseSheet`（经 ListPickerSheet）；页内私有 `IssueForm` / `UpdateForm` / `ListPickerSheet`
**控件类型**：`scroll-view`、分段控件 `seg`（互不清空草稿）、`textarea`、`input`（名称 / 价格 digit / chips 自由输入）、chips 增删、菜品选择弹层（搜索 + 列表 + 空态）、字段级错误滚动定位
