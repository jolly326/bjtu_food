# client · 学生端（微信小程序）

单页沉浸式「美食地图」：浏览菜品、点赞、纠错。视觉口径见 [`docs/ui/client/`](../docs/ui/client/)。

- 技术栈：uni-app · Vue 3 · TypeScript · 构建目标 `mp-weixin`

## 目录与职责

```
src/
├── pages/map/index.vue            # 唯一页面
│   └── components/                # 页面私有：FoodMap · ThemeTotem · DishSheet · DishInfo · FeedbackPanel
├── components/                    # ≥2 页公共件（7）：AppIcon · ImagePlaceholder · RetryBlock · PageLoading · BaseSheet · DashedRule · StatusBlock
├── hooks/                         # useMapScene · useDishFeedback · useTimers
├── utils/                         # viewport · mapLayout · mapMotion · mapCamera · mapPan · backoff · dishBatch · image · issueLocal · request
├── theme/                         # tokens.ts · generated-colors.css · design-tokens.css · icons.ts · useTheme.ts
├── api/                           # dish · issue · types
├── types/generated/api.d.ts       # OpenAPI 生成类型
├── constants.ts                   # 尺寸/时长/阈值/存储键
└── styles/global.scss
```

- **依赖方向**：`pages → pages/components → components / hooks / utils / theme / api`；页面私有件不跨页引用；`utils` / `theme` 不导入 `pages`。
- **归属判定**：≥2 页使用才进 `components/`，否则下沉 `pages/map/components/`。
- **图标**：`assets/icons/<key>.svg` 为 Plump **Solid** 实心，`fill` 写死中性棕（`<image>` 不支持 `currentColor`），见 [`docs/ui/client/图标资源.md`](../docs/ui/client/图标资源.md)。

## 脚本

```bash
cd client
npm install
npm run dev:mp-weixin          # 连本机后端（.env.development）
npm run dev:mp-weixin:cloud    # 连云端（.env.production）
npm run build:mp-weixin        # 发布构建
npm run verify                 # check:contract · check:icon · check:ui · check:events · check:doc · lint · type-check · test · build
```

环境变量**构建期内联**，改 env 必须重新编译；真机联调用 `.env.development.local` 写局域网 IP。详见仓库根 [`ENVIRONMENT.md`](../ENVIRONMENT.md)。

## 相关文档

- 架构与分包：[`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)
- 学生端视觉：[`docs/ui/client/`](../docs/ui/client/) ｜ 接口：[`docs/api/client/`](../docs/api/client/) ｜ 功能：[`docs/func/client/`](../docs/func/client/)
