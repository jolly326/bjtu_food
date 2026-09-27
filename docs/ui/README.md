# UI 设计稿总览（按页面拆分）

> 本目录是「**有界面的**页面 UI 设计稿」唯一真源：每个页面（或页内承载物）一份独立文档，文件名以板块前缀开头，与 [`docs/feature/`](../feature/) **同名一一对应**（例外登记见文末）。
> **口径归属**：页面 UI（布局 / 视觉 / 交互 / 三态 / 控件）以本目录为准；功能流程 / 接口 / 字段 / 数据口径以 [`docs/feature/`](../feature/) 为准；跨端视觉规范与红线以 [`docs/project_spec.md`](../project_spec.md) §4 为准。
> **本目录只写「当前口径」**：逐轮变更过程 / 被推翻的方案 / 失败尝试一律归口 [`docs/UI_FRONTEND_LOOP.md`](../UI_FRONTEND_LOOP.md)（UI 统一 loop 的流程真源 + 轮次记录）。
> **范围**：只收**有对应界面**的设计稿 —— **无页面 / 无弹层的能力（全局登录态、静默上报、已下线功能）不在此建文档**（Round 31 起；本轮已删 3 份，见文末「本轮删除」）。

## 板块与命名规则

| 文件名前缀 | 板块 | 代码目录 | 说明 |
|---|---|---|---|
| `client-` | **学生端（微信小程序）** | `client/` | 15 份设计稿 —— UI 统一 loop 覆盖范围 |
| `web-` | **管理端（Web 后台）** | `web/` | 7 份，**当前为要点级骨架，尚未细化**（loop 范围仅 `client/src`） |

## 修正状态标记（用户验收口径）

| 标记 | 含义 |
|---|---|
| ✅ **已完成** | 该页 UI 修正**已通过用户验收**（当前仅 3 页：首页菜品浏览 · 菜品详情 · 搜索） |
| ⛔ **未完成** | **尚未完成 UI 修正**（可能已随 loop 改过多轮，但未达验收 —— 事实记在「loop 轮次」列） |
| 🖥 **页内承载物** | 非独立页面（抽屉 / 弹层 / 列表行 / 危险操作确认），**归属宿主页面**，规格与宿主页同批维护 |
| ▫️ **未细化** | web 端要点级骨架（有落点与页面要点，无交互 / 三态 / 组件清单 / 数据映射） |

> 本列只反映**修正完成度**，不代表代码落地状态（代码侧以 `vue-tsc` / 构建产物 / 真机为准）。
> 轮次定义见 [UI_FRONTEND_LOOP.md](../UI_FRONTEND_LOOP.md) §4（**范围 = `client/src`**，不含 `server/`、`web/`）。

---

## client- · 学生端（微信小程序，15 份）

| 编号 | 页面 / 承载物 | UI 设计稿 | 代码落点 | 归属功能文档 | loop 轮次（事实） | UI 修正（验收） |
|---|---|---|---|---|---|---|
| A-02 | **首页菜品浏览**（全站基准稿） | [client-首页菜品浏览.md](./client-首页菜品浏览.md) | `pages/home/index.vue` | [client-首页菜品浏览.md](../feature/client-首页菜品浏览.md) | R1·R5·R6·R9·R10·R12·R15·R18·R20·R26·R28·R29 | ✅ **已完成** |
| A-03 | **搜索** | [client-搜索.md](./client-搜索.md) | `pages/find/index.vue`、`pages/find/DishResultCard.vue` | [client-搜索.md](../feature/client-搜索.md) | R18·R20·R21·R26–R27c·R28·R31 | ✅ **已完成** |
| A-04 | **菜品详情** | [client-菜品详情.md](./client-菜品详情.md) | `pages/detail/dish/index.vue` | [client-菜品详情.md](../feature/client-菜品详情.md) | R19·R22–R25·R31 | ✅ **已完成** |
| A-06 | 写评价 / 重新评价 | [client-写评价.md](./client-写评价.md) | `pages/detail/dish/ReviewComposer.vue` | [client-写评价.md](../feature/client-写评价.md) | R13·R23·R25 | 🖥 页内承载物（属 A-04 详情页）· ⛔ 未完成 |
| A-08 | 删除本人评价 | [client-删除本人评价.md](./client-删除本人评价.md) | `pages/detail/dish/index.vue`（`ActionSheet`） | [client-删除本人评价.md](../feature/client-删除本人评价.md) | R17 | 🖥 页内承载物（属 A-04）· ⛔ 未完成 |
| A-09 | 举报评价 | [client-举报评价.md](./client-举报评价.md) | `pages/detail/dish/ReportModal.vue` | [client-举报评价.md](../feature/client-举报评价.md) | R7·R13 | 🖥 页内承载物（属 A-04）· ⛔ 未完成 |
| A-10 | 我的评价（承载于「我的主页」） | [client-我的主页.md](./client-我的主页.md) | `pages/my-reviews/index.vue` | [client-我的评价.md](../feature/client-我的评价.md) | R12·R14·R17·R24 | ⛔ 未完成 |
| A-11 | 意见反馈 | [client-意见反馈.md](./client-意见反馈.md) | `pages/feedback/index.vue` | [client-意见反馈.md](../feature/client-意见反馈.md) | R1–R3·R13·R17·R25 | ⛔ 未完成 |
| A-12 | 系统通知（处理回执） | [client-系统通知.md](./client-系统通知.md) | `pages/notifications/index.vue` | [client-系统通知.md](../feature/client-系统通知.md) | R1–R3·R12·R13·R17 | ⛔ 未完成 |
| A-13 | 个人资料（编辑于个人信息编辑页） | [client-个人信息编辑.md](./client-个人信息编辑.md) | `pages/profile/index.vue` | [client-个人资料.md](../feature/client-个人资料.md) | R1·R6·R12·R14·R17 | ⛔ 未完成 |
| A-14 | 邮箱认证 | [client-邮箱认证.md](./client-邮箱认证.md) | `pages/auth/index.vue` | [client-邮箱认证.md](../feature/client-邮箱认证.md) | R1·R12·R17·R26 | ⛔ 未完成 |
| A-15 | 注销账号（入口在「我的」页） | [client-注销账号.md](./client-注销账号.md) | `pages/mine/index.vue`（危险弱化行 + `showModal`） | [client-注销账号.md](../feature/client-注销账号.md) | — | 🖥 页内承载物（属下方「我的页」）· ⛔ 未完成（待重设计） |
| A-16 | 隐私政策 / 用户协议 | [client-隐私政策.md](./client-隐私政策.md) · [client-用户协议.md](./client-用户协议.md) | `pages/privacy/{index,agreement}.vue`、`pages/privacy/DocPage.vue` | [client-隐私政策与用户协议.md](../feature/client-隐私政策与用户协议.md) | R4 | ⛔ 未完成 |
| — | 我的页（TabBar 主页，非功能页） | [client-我的页.md](./client-我的页.md) | `pages/mine/index.vue` | —（导航壳 + 入口聚合） | R1·R5·R12·R17 | ⛔ 未完成 |

> `client-首页菜品预览.png` 为首页设计参考图（静态资源，非文档）。

## web- · 管理端（Web 后台，7 份）

| 编号 | 页面 / 承载物 | UI 设计稿 | 代码落点 | 归属功能文档 | UI 修正 |
|---|---|---|---|---|---|
| B-01 | 菜品管理（默认落地页 `/dashboard/content`） | [web-菜品管理.md](./web-菜品管理.md) | `web/src/views/content/DishManageView.vue` | [web-菜品管理.md](../feature/web-菜品管理.md) | ▫️ 未细化 |
| B-02 | 菜品详情查看 | [web-菜品详情查看.md](./web-菜品详情查看.md) | `web/src/views/content/DishDetailView.vue` | [web-菜品详情查看.md](../feature/web-菜品详情查看.md) | ▫️ 未细化 |
| B-03 | 评价管理（事后处置） | [web-评价管理.md](./web-评价管理.md) | `web/src/views/audit/ReviewManageView.vue` | [web-评价管理.md](../feature/web-评价管理.md) | ▫️ 未细化 |
| B-04 | 反馈处理 | [web-反馈处理.md](./web-反馈处理.md) | `web/src/views/audit/FeedbackView.vue` | [web-反馈处理.md](../feature/web-反馈处理.md) | ▫️ 未细化 |
| B-05 | 学生账号管理 | [web-学生账号管理.md](./web-学生账号管理.md) | `web/src/views/system/UserView.vue` | [web-学生账号管理.md](../feature/web-学生账号管理.md) | ▫️ 未细化 |
| B-06 | 图片上传（表单内，无独立页面） | [web-图片上传.md](./web-图片上传.md) | 各表单弹窗内 | [web-图片上传.md](../feature/web-图片上传.md) | ▫️ 未细化 |
| B-07 | 信息纠错处理 | [web-信息纠错.md](./web-信息纠错.md) | `web/src/views/audit/CorrectionView.vue` | [web-信息纠错.md](../feature/web-信息纠错.md) | ▫️ 未细化 |

> **web 端未细化的统一口径**：管理后台 7 份目前只有「落点 + 页面要点」，缺少管理端 UI 规范（表格 / 表单 / 弹窗 / 反馈）与三态定义 —— 细化前，**管理端视觉与交互无唯一真源**。

---

## 跨页通用口径

### 图片占位（Round 31 起全站统一）

**唯一实现**：公共 `components/ImagePlaceholder.vue` —— **灰底 `--bg-placeholder` + 居中图标**。

| 场景 | 图标 | 说明 |
|---|---|---|
| **图片缺失 / 加载失败 / 破图**（菜品图、评价配图、Banner、轮播、上传缩略图） | **`image-broken`（图片破损）** | 默认值；全站标准占位 |
| **头像无值 / 加载失败** | `user`（人形） | 唯一语义例外：「无用户」≠「图片损坏」；**底色仍与全站一致** |

- 消费方：`ImageFallback`（头像）、`DishCard`、`DishResultCard`、`ImageSwiper`、`ImagePicker`、`ReviewItem`、`HomeBanner`。
- **SHALL NOT** 各页自绘占位、**SHALL NOT** 再用 `dish` / `empty` 等图标顶替、**SHALL NOT** 自定义占位底色（先前存在 `--bg-page` / `--bg-card` / `--bg-soft` / `--bg-placeholder` 四种底色）。
- 尺寸由消费方按容器传 `size`（rpx），与容器内其它图标同档。

## 与功能文档的对应关系（例外登记）

绝大多数为 **1:1 同名对应**（`docs/ui/X.md` ↔ `docs/feature/X.md`），以下为例外：

| UI 设计稿 | 归属功能文档 | 关系 |
|---|---|---|
| `client-隐私政策.md` · `client-用户协议.md` | `client-隐私政策与用户协议.md` | **1 功能 : 2 页面**（两页共用 `DocPage` 外壳） |
| `client-个人信息编辑.md` | `client-个人资料.md` | 同一功能；**页面名与功能名不同**（查看在「我的主页」信息卡） |
| `client-我的主页.md` | `client-我的评价.md` | 功能「我的评价」**承载于**「我的主页」评价区 |
| `client-写评价.md` · `client-删除本人评价.md` · `client-举报评价.md` | 同名功能文档 | 均为**页面内承载物**（抽屉 / ActionSheet / 弹层），宿主页 = `client-菜品详情.md` |
| `client-注销账号.md` | `client-注销账号.md` | **页面内承载物**（「我的」页危险弱化行 + `showModal`），宿主页 = `client-我的页.md` |
| `client-我的页.md` | — | TabBar 主页，**无独立功能文档**（导航壳 + 入口聚合） |

### 本轮删除（Round 31，用户指示：无界面者不设设计稿）

| 已删文档 | 原因 | 原能力现在何处 |
|---|---|---|
| `client-微信静默登录与游客态.md` | 全局登录态，**无页面 / 无弹层** | 功能文档 [`client-微信静默登录与游客态.md`](../feature/client-微信静默登录与游客态.md)；游客态标识见「我的页 / 我的主页」设计稿 |
| `client-浏览计数.md` | **进详情页即上报**的静默行为，无展示 / 无交互 | 功能文档 [`client-浏览计数.md`](../feature/client-浏览计数.md) |
| `client-评价有用.md` | 能力**已全链下线**（端点 / 字段 / 表列 / 三端展示均删） | 功能文档 [`client-评价有用.md`](../feature/client-评价有用.md)（历史留痕）+ `project_spec.md` §7.30 |

> 上述三份功能文档的 `## UI` 段指针已同步改为「**无独立 UI 界面**」说明（不再指向已删文件）。

## 待办 / 未决清单

| # | 事项 | 现状 | 影响面 |
|---|---|---|---|
| 1 | **除首页 / 搜索 / 菜品详情外的 12 份设计稿修正** | ⛔ 未完成（用户口径）；loop 已改过的轮次见上表「loop 轮次」列 | 「我的页」「我的主页」「意见反馈」「系统通知」「个人信息编辑」「邮箱认证」「隐私政策 / 用户协议」等 |
| 2 | **注销入口 UI 重设计** | 待 UI/UX 出稿（`docs/feature/README.md` 待办 #5），**不动接口** | 「我的」页内布局（`client-注销账号.md` / `client-我的页.md`） |
| 3 | **web- 7 份设计稿细化** | 要点级骨架（无交互 / 三态 / 组件清单 / 数据映射） | 管理后台 7 个页面 |
| 4 | loop 候选池中的 UI 待拍板项 | 白卡壳收敛范围 / 圆角裸值定档 / 阴影三选一 / 通知未读双指示器 / `AppTitleBand` 与 `AppHeader` 是否合并 | 见 [UI_FRONTEND_LOOP.md](../UI_FRONTEND_LOOP.md) §5 |

## 与其它文档的关系

- [`docs/feature/`](../feature/)：功能 / 接口 / 字段 / 数据（其每份文档的 `## UI` 段是指向本目录的**指针**）。
- [`docs/UI_FRONTEND_LOOP.md`](../UI_FRONTEND_LOOP.md)：UI 统一 loop 的流程真源与**轮次记录**（逐轮变更归此处，本目录只留当前口径）。
- [`docs/project_spec.md`](../project_spec.md) §4：跨端 UI 视觉规范（红线）。
- `CODEBUDDY.md`：仓库约定与红线。
