# client · 学生端（微信小程序）

单页「菜品首页」：常驻结果条 + 2 列结果信息卡（卡内就地展开）+ 底部悬浮搜索胶囊 + 文字反馈。视觉口径见 [`docs/ui/client/`](../docs/ui/client/)。

- 技术栈：uni-app · Vue 3 · TypeScript · 构建目标 `mp-weixin`

## 目录与职责

```
src/
├── pages/home/index.vue           # 唯一页面
│   └── components/                # 页面私有：HomeHeader · ResultBar · DishGrid · DishResultCard · SearchDock · FeedbackPanel
├── components/                    # ≥2 页公共件（5）：AppIcon · ImagePlaceholder · RetryBlock · PageLoading · StatusBlock
├── hooks/                         # useImageSource · useTimers（统一出口 hooks/index.ts）
├── utils/                         # backoff · image · request
├── theme/                         # tokens.ts · generated-colors.css · design-tokens.css · icons.ts · index.ts
├── api/                           # dish · feedback · types
├── types/generated/api.d.ts       # OpenAPI 生成类型（勿手改）
├── constants.ts                   # 尺寸 / 时长 / 阈值 / 存储键
└── styles/                        # global.scss（基础样式）· motion.scss（动效与减弱动效兜底）
```

- **依赖方向**：`pages → pages/components → components / hooks / utils / theme / api`；页面私有件不跨页引用；`utils` / `theme` 不导入 `pages`。
- **归属判定**：≥2 页使用才进 `components/`，否则下沉 `pages/home/components/`。
- **图标**：`assets/icons/<key>.svg` 为 Plump **Solid** 实心，`fill` 为单一中性色（`<image>` 不支持 `currentColor`），键表与 CC BY 4.0 署名见 `src/theme/icons.ts`。
- **设计 token**：颜色只改 `theme/tokens.ts`（`generated-colors.css` 与其逐键一致）；非颜色 token 在 `theme/design-tokens.css`；🔥 零消费即删。

## 脚本

```bash
cd client
npm install
npm run dev:mp-weixin          # 连本机后端（.env.development）
npm run dev:mp-weixin:cloud    # 连云端（.env.production）
npm run build:mp-weixin        # 发布构建
npm run gen:api:fresh          # 后端起服务后刷新契约快照（openapi.json + api.d.ts）
npm run verify                 # check:contract · check:icon · check:ui · check:events · check:doc · lint · type-check · test · build
```

环境变量**构建期内联**，改 env 必须重新编译；真机联调用 `.env.development.local` 写局域网 IP（不入库）。环境变量清单见仓库根 [`README.md`](../README.md)「快速开始 · 3. 配置环境变量」。

## 相关文档

- 项目约定与红线：[`docs/func/client/约定与红线.md`](../docs/func/client/约定与红线.md)
- 学生端视觉：[`docs/ui/client/`](../docs/ui/client/) ｜ 接口：[`docs/api/client/`](../docs/api/client/) ｜ 功能：[`docs/func/client/`](../docs/func/client/)
