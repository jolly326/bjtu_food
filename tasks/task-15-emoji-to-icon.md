# Task-15 小程序图标 emoji→SVG 矢量图标落地

## 背景 / 需求来源
- `docs/mini-app-ui/icons.md` 已定稿（图标映射权威源，原 `mini-app-ui.md` §0.5 已迁移），明确「禁止 emoji 字符当图标」，统一使用 SVG 矢量图标（本地 Assets 优先 + Iconfont 兜底）。
- `project_spec.md` §4.2 / §4.9 已同步更新为 SVG 图标红线（原「emoji 占位」已废除），语义唯一：ic-heart=喜欢、ic-thumb=有用/点赞。
- 资源层：`frontend/src/assets/icons` 现 26 个 SVG，其中 `ic-lightbulb.svg` 为本次经 Iconfont MCP 新增（替换 💡 猜你喜欢）；其余均已存在。

## 目标
将小程序前端（`frontend/`，uni-app 学生端）所有以 emoji 字符充当图标/语义的实现，替换为 `assets/icons` 下的 SVG 矢量图标引用，确保零 emoji 图标残留、风格一致。

## 范围
- 涉及语义与图标（权威映射见 `docs/mini-app-ui/icons.md`）：

  | 语义 | 图标文件 | 原 emoji |
  |---|---|---|
  | 搜索 | ic-search.svg | 🔍 |
  | 位置 | ic-location.svg | 📍 |
  | 喜欢/收藏 | ic-heart.svg | ❤️ |
  | 有用/点赞 | ic-thumb.svg | 👍 |
  | 热门 | ic-fire.svg | 🔥 |
  | 限时 | ic-clock.svg | ⏰ |
  | 猜你喜欢 | ic-lightbulb.svg | 💡 |
  | 分享 | ic-share.svg | 📤 |
  | 评价/评论 | ic-comment.svg | 💬 |
  | 发布 | ic-plus.svg | ➕ |
  | 举报 | ic-report.svg | ⚠️ |

- 涉及位置：CustomTabBar、Header、DishCard、MomentCard、评价区、互动栏（有用/评论/举报）、悬浮发布 FAB、空状态、筛选 Sheet、首页「猜你喜欢」推荐位等所有用到上述 emoji 之处。

## 实现约定（一致性红线）
- 统一经「统一图标组件/字体类」引用 SVG，不得散落内联 `<image>` 或裸 emoji 字符。
- 图标文件统一 `fill:currentColor`，支持主题色/状态色（如喜欢态高亮）。
- 命名 `ic-<语义>.svg`，单色、2px 描边、24px 网格、圆角端点一致。
- 新增语义图标须先登记到 `docs/mini-app-ui/icons.md` 映射表并下载至 `assets/icons`，禁止 emoji 字符当图标，禁止私自引未登记图标。
- 语义唯一：ic-heart=喜欢（不与点赞混用）、ic-thumb=有用/点赞。
- 遵循 uni-app 小程序端限制（见 `project_spec.md` §4 红线，如不支持自定义指令）。

## 依赖
- 依赖 `task-14-miniapp-ui-implementation.md`：页面骨架与基础组件（DishCard/SearchBar/WaterfallList 等）已实现，本任务在其之上替换图标。
- 资源已就位（26 个 SVG），无外部下载阻塞；`ic-lightbulb.svg` 已新增。

## 验收标准
1. 全仓 `frontend/` 下检索不到上述 11 个 emoji 作为图标/语义残留（装饰性文案 emoji 若确需保留须技术负责人确认，默认全部替换）。
2. 所有被替换位置均通过统一图标组件引用 `assets/icons` 下对应 SVG，能正常渲染并随主题变色。
3. `ic-lightbulb`（猜你喜欢）在首页「猜你喜欢」/推荐位正确显示。
4. 交互态正确：喜欢/有用乐观更新后图标高亮态符合 `task-14` 约定。
5. 不引入新的未登记图标或 emoji 占位。
6. 小程序 dev 编译通过，无控制台报错。

## 注意
- 不修改 `project_spec.md` / `tasks/`，图标语义变更先提技术负责人裁定。
- 若实现中发现 `assets/icons` 缺某语义图标（超出上表 11 项），暂停并回报技术负责人，由技术负责人经 Iconfont MCP 补下载，不得自行用 emoji 替代。
