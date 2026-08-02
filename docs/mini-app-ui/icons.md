# 小程序图标映射源（IconSvg / ic-* 注册表）

> 本文件（`docs/mini-app-ui/icons.md`）是小程序端 **图标映射的权威来源（canonical source）**，原 `docs/mini-app-ui.md` §0.5 已迁移至此。它记录了每个 `ic-*` 图标键 → 语义 → SVG 文件 → 来源的映射，以及图标资源的使用红线与登记规则。
>
> 相关文档：
> - 各页面 UI 设计见 `docs/mini-app-ui/` 下对应 `<pagekey>.md`；目录索引见 `docs/mini-app-ui/README.md`。
> - 设计 Token / 一致性红线（rpx Token 清单、按压 scale、禁 emoji 等）见 `docs/project_spec.md` §4 与 §4.9。

## 0.5 图标资源规范（迁移自 mini-app-ui.md §0.5）

- **【全局强制红线 · 全量禁用 emoji】** 所有功能图标与情感图标一律使用 **SVG 矢量图标**；home 及全小程序**禁止 `emoji.ts` 与任何 Unicode emoji 字符当图标/语义**（原 §4.2/§4.9「禁止 emoji」强化为全局强制项，无例外）。缺失图标一律经 **Iconfont MCP** 拉取阿里云矢量库 SVG 补齐，不得回退到 emoji。
- **统一来源（矢量图标）**：来源优先级：① 本地 `Assets`（项目内已有图标优先复用）② 阿里云矢量库（Iconfont）通过 **Iconfont MCP** 拉取下载。设计师可经 MCP 直接拉取缺失图标。
- **视觉一致性（参考 apple-design + ui-ux-pro-max）**：图标统一线性/面性风格、2px 描边、24px 网格、圆角端点一致；语义清晰、克制，不做无意义装饰。
- **落地约束**：图标随下载落统一图标目录（如 `frontend/src/assets/icons`）经统一图标组件/字体类引用；新语义图标先登记后使用。`frontend/src/assets/icons` 已有 26 个线性 SVG（含本次经 Iconfont MCP 补充的 ic-lightbulb），其余为手写线性风格，统一 `fill:currentColor`、2px 描边、24px 网格、圆角端点一致。

### 图标映射表（emoji → 语义 → 图标文件）

| 原 emoji | 语义 | 图标文件 | 来源 |
|---|---|---|---|
| 🔔 | 广播通知 | ic-broadcast.svg | 本地（已登记） |
| 🔍 | 搜索 | ic-search.svg | 本地 |
| 📍 | 位置 | ic-location.svg | 本地 |
| ❤️ | 喜欢/收藏 | ic-heart.svg | 本地 |
| 👍 | 有用/点赞 | ic-thumb.svg | 本地 |
| 🔥 | 热门 | ic-fire.svg | 本地 |
| ⏰ | 限时 | ic-clock.svg | 本地 |
| 💡 | 猜你喜欢 | ic-lightbulb.svg | Iconfont MCP（本次新增）|
| 📤 | 分享 | ic-share.svg | 本地 |
| 💬 | 评价/评论 | ic-comment.svg | 本地 |
| ➕ | 发布 | ic-plus.svg | 本地 |
| ⚠️ | 举报 | ic-report.svg | 本地 |
| 👤 | 默认头像/无头像兜底 | ic-user.svg | 新增登记（community `MomentCard` / profile 用户卡无头像兜底用，Iconfont MCP 补齐）|
| 📭 | 空态兜底 | ic-empty.svg | 本地（已存在，`EmptyState` 复用）|
| 🔒 | 密码 / 验证码输入 | ic-lock.svg | Iconfont MCP 补齐（登记；`AuthForm` 密码/验证码字段，§1.4）|
| ✉️ | 邮箱输入 | ic-mail.svg | Iconfont MCP 补齐（登记；`AuthForm` 邮箱验证码路，§1.4）|
| ✕ | 弹层关闭 | ic-close.svg | Iconfont MCP 补齐（登记；各 Sheet/Modal 统一关闭按钮，§1.4 弹层规范）|
| 🏷️ | 品牌 logo | ic-logo.svg | Iconfont MCP 补齐（登记；`AuthForm` 未登录态品牌区，§1.4）|
| 📋 | 我的发布 / 列表 | ic-list.svg | Iconfont MCP 补齐（登记；profile 菜单「我的发布」等，§1.4 菜单组）|
| ✏️ | 编辑 / 我的提交 | ic-edit.svg | Iconfont MCP 补齐（登记；profile 菜单「我的提交/昵称编辑」等）|
| 🔔 | 通知 / 设置项 | ic-bell.svg | Iconfont MCP 补齐（登记；profile / settings 通知相关菜单，与广播 `ic-broadcast` 区分语义）|
| 💬 | 联系开发者 | ic-contact.svg | Iconfont MCP 补齐（登记；profile 菜单「联系开发者」）|
| ⚙️ | 设置 | ic-settings.svg | Iconfont MCP 补齐（登记；profile / settings「设置」入口）|
| ⭐ | 收藏 | ic-star.svg | Iconfont MCP 补齐（登记；`StatsRow` 收藏数 / 设置项）|
| 🏠 | 主页 / 我的主页 | ic-home.svg | Iconfont MCP 补齐（登记；菜单返回主页或主页类入口）|
| 🌶️ | 菜品属性·辣度 | ic-chili.svg | Iconfont MCP 补齐（登记；dish `TagLabel` 属性图标，§2.1）|
| 🍽️ | 菜品属性·份量 | ic-portion.svg | Iconfont MCP 补齐（登记；dish `TagLabel` 属性图标，§2.1）|
| 🍲 | 菜品/档口图兜底 | ic-dish.svg | Iconfont MCP 补齐（登记；dish/stall/canteen 食堂·档口图兜底，§2.1/§2.2/§2.3；语义＝「此图是菜品/档口」）|
| 🖼️ | 通用图片兜底 | ic-image.svg | Iconfont MCP 补齐（登记；图加载失败兜底；与 `ic-dish` 区分语义：`ic-image`＝通用/非菜品类图片失败，`ic-dish`＝菜品/档口图失败）|
| 🏪 | 档口数 / 档口标识 | ic-stall.svg | Iconfont MCP 补齐（**登记但 SVG 尚未拉取**，待经 Iconfont MCP 下载落 `assets/icons`；canteen 基础信息「档口数」行图标 §2.2，及 moment §2.4 关联对象卡 `ic-stall` 区分 dish/stall；若 MCP 无合适矢量则复用 `ic-list` 登记，二选一）|
| 🔙 | 返回（左箭头） | ic-arrow-left.svg | 本地（已登记；find 页返回键，区别于 `ic-back` 右箭头）|
| ➡️ | 右箭头（跳转/关联卡去往） | ic-arrow.svg | 本地（已登记；关联对象卡 `ic-arrow` 跳转指示，区别于 `ic-arrow-left` 返回；moment §2.4 关联卡、通用跳转行、各 Sheet 选项行右指示**一律用 `ic-arrow`**，**禁止**用 `ic-arrow-left` 旋转 180° 模拟右箭头——`ContributeSheet` / profile 贡献入口当前误用 `ic-arrow-left` 旋转，须改为 `ic-arrow`）|
| ↩️ | 后退（通用返回，区别于左箭头语义） | ic-back.svg | 本地（已登记；通用返回，语义区别于 `ic-arrow-left` 返回键）|
| 🔥 | 热门 | ic-fire.svg | 本地（已登记；首页热门菜品区/排序高热度标识）|
| 🏠 | 主页 / 我的主页 | ic-home.svg | 本地（已登记；菜单返回主页或主页类入口）|
| 💰 | 价格 / 人均 | ic-price.svg | 本地（已登记；stall/canteen 人均、FilterSheet 价格区标题图标）|
| ✅ | 勾选 / 确认 | ic-check.svg | 本地（已登记；选择态确认、Modal 确认）|
| 🗑️ | 删除 / 清除缓存 | ic-delete.svg | 本地（已登记；settings「清除缓存/账号注销」、列表删除）|
| 👤 | 账号 / 我的（设置项语义） | ic-profile.svg | 本地（已登记；settings「退出登录/账号」项，区别于 `ic-user` 头像兜底）|
| 🔍 | 筛选 | ic-filter.svg | 本地（已登记；find 排序/筛选触发图标）|
| 🍜 | 分类·面条 | ic-noodle.svg | 本地（已登记；find 分类宫格，禁止回退 `ic-dish`）|
| 🍚 | 分类·米饭 | ic-rice.svg | 本地（已登记；find 分类宫格）|
| 🌶️🍲 | 分类·麻辣烫 | ic-malatang.svg | 本地（已登记；find 分类宫格）|
| 🍳 | 分类·早餐 | ic-breakfast.svg | 本地（已登记；find 分类宫格）|
| 🌙 | 分类·夜宵 | ic-midnight.svg | 本地（已登记；find 分类宫格）|
| 🍔 | 分类·快餐 | ic-fastfood.svg | 本地（已登记；find 分类宫格）|
| 🍢 | 分类·小吃 | ic-snack.svg | 本地（已登记；find 分类宫格）|
| 🥤 | 分类·饮品 | ic-drink.svg | 本地（已登记；find 分类宫格）|

> 新增语义图标须先在此表登记，再将 SVG 下载至 `assets/icons`，禁止 emoji 字符当图标。全量禁 emoji 为强制红线，缺失图标经 Iconfont MCP 补齐。chili/portion 等属性图标若原无对应矢量，经 Iconfont MCP 拉取补齐并登记（ic-clock 已登记复用；chili/portion 本期新增登记；ic-dish/ic-image 作图兜底登记）。

### 图标语义契约（已稳定）

- `thumb`=有用/点赞、`heart`=喜欢、`star`=评分，三者互不混用。
- 中性占位必须为 `empty`（非 `dish`）：`IconSvg` 回退目标与 `ImageFallback` 等全局图片裂图兜底组件均须落 `empty` 中性占位键；破图 / 空态语境禁止用语义图标（碗 `dish`）冒充中性占位。
- 仅当组件语义**明确**为「菜品」时（如 `DishCard` 的菜品图占位）才可用 `dish` 作图片占位；食堂卡 / 档口关联 / 关于页 / 通用轮播等容器语义≠菜品的中性场景一律用 `empty`。
- 关联对象卡、通用跳转行、各 Sheet 选项行右指示**一律用 `ic-arrow`**，禁止用 `ic-arrow-left` 旋转 180° 模拟右箭头。

### 登记规则

- 新语义图标须先在本表登记（语义 + 图标文件 + 来源），再将 SVG 下载至 `frontend/src/assets/icons`，禁止 emoji 字符当图标，禁止私自引用未登记图标。
- 图标经统一图标组件 `IconSvg` 渲染（`<IconSvg name="ic-xxx" />`），`frontend/src/assets/icons` 下 SVG 统一 `fill:currentColor`、2px 描边、24px 网格、圆角端点一致。
