# web · 运营端（管理后台）

食堂 / 档口 / 菜品的维护与纠错处置；视觉口径见 [`docs/ui/web/`](../docs/ui/web/)。

- 技术栈：Vue 3 · TypeScript · Vite · Element Plus（仅主题变量与程序式提示）
- 弹层：自定义 `.mask` + `.dialog`（非 Element Plus Dialog）

## 目录与职责

```
src/
├── main.ts / App.vue
├── router/index.ts            # 路由
├── layouts/AdminLayout.vue    # 顶栏 + 底部应用坞（Dock）
├── views/                     # LoginView · CanteenView · StallView · DishView · IssueView
├── components/                # ≥2 页公共件：AppIcon · CardActionSheet · ConfirmDeleteView · DishEditDialog · ImageField · ImageZoomOverlay · ListError · PaginationBar
├── composables/               # usePagedList · useBatchSelection · useCardMenu · useImageZoom · useOptions
├── directives/zoomImg.ts      # v-zoomimg
├── api/                       # request · canteen · stall · dish · issue · option · session · query · types
├── stores/session.ts          # 登录态
├── styles/theme.css           # 唯一主题覆盖层
├── utils/                     # image · datetime
└── assets/icons/              # Plump Solid 实心图标（fill=currentColor，14 枚）
```

- **依赖方向**：`views → components / composables → api → request`；`api/` 不导入 `views/`、`components/`；`components/` 不导入 `views/`。
- **组件归属**：≥2 页使用才进 `components/`；单页逻辑先放 `views/*.vue`，超过阈值再下沉。
- **图片字段**：统一 `ImageField`（16:9、选图仅本地预览、保存时上传 COS、有图可直接「更换图片」）。
- **删除确认**：统一 `ConfirmDeleteView` 在编辑弹窗内**整卡切换**，不用 `ElMessageBox`（选项字典除外）。

## 脚本

```bash
cd web
npm install
npm run dev        # http://localhost:5173
npm run verify     # check:tokens · vitest · eslint · type-check · prettier · build
```

接口基址走 `VITE_API_BASE_URL`（见 `web/.env.*`）；环境说明见仓库根 [`ENVIRONMENT.md`](../ENVIRONMENT.md)。

## 相关文档

- 架构与分包：[`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)
- 运营端视觉：[`docs/ui/web/`](../docs/ui/web/) ｜ 接口：[`docs/api/web/`](../docs/api/web/) ｜ 功能：[`docs/func/web/`](../docs/func/web/)
