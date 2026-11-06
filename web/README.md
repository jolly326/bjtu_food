# web · 运营端（管理后台）

食堂 / 档口 / 菜品维护与反馈处置；视觉口径见 [`docs/ui/web/`](../docs/ui/web/)。

- 技术栈：Vue 3 · TypeScript · Vite · Element Plus（仅 `el-select` 筛选下拉 + `ElMessage` 提示，样式按需引入）
- 弹层：自定义 `.mask` + `.dialog`（非 Element Plus Dialog）

## 目录与职责

```
src/
├── main.ts / App.vue         # App.vue 提供 #overlay-root 全局弹窗挂载点
├── router/index.ts            # 路由
├── layouts/AdminLayout.vue    # 顶部固定导航栏（全部功能入口）+ 吸顶页头 + 内容区滚动
├── views/                     # LoginView · CanteenView · StallView · DishView · FeedbackView
├── components/                # ≥2 页公共件（8）：AppIcon · ConfirmDeleteCard · DishEditDialog · ImageField · ImageZoomOverlay · ListCard · ListError · PaginationBar
├── composables/               # usePagedList · useAllPages · useImageZoom
├── directives/zoomImg.ts      # v-zoomimg（点图放大）
├── api/                       # request · auth · canteen · stall · dish · feedback · query · types
├── stores/session.ts          # 登录态
├── styles/                    # theme.css（唯一主题覆盖层）· motion.css
├── utils/                     # image（上传前压缩 / 地址处理）
└── assets/icons/              # Plump Solid 实心图标（fill=currentColor，13 枚）
```

- **依赖方向**：`views → components / composables → api → request`；`api/` 不导入 `views/`、`components/`；`components/` 不导入 `views/`。
- **组件归属**：≥2 页使用才进 `components/`；单页逻辑先放 `views/*.vue`，超过阈值再下沉。
- **图片字段**：统一 `ImageField`（16:9、选图仅本地预览、保存时上传 COS、有图可直接「更换图片」）。
- **删除确认**：统一 `ConfirmDeleteCard` 在编辑弹窗内**整卡切换**，不叠二次确认浮层。

## 脚本

```bash
cd web
npm install
npm run dev        # http://localhost:5173
npm run verify     # check:tokens · vitest · eslint · type-check · prettier · build
```

接口基址走 `VITE_API_BASE_URL`（见 `web/.env.*`），所有 mode 缺省为同源 `/api`：`npm run dev` 与 `npm run preview` 由 `vite.config.ts` 的代理转发到本机后端（默认 `127.0.0.1:8000`，可用 `VITE_API_PROXY_TARGET` 改指），部署时由网关把 `/api` 反代到后端，浏览器无跨域。需要直连独立部署的后端时，在构建前用进程环境变量 `VITE_API_BASE_URL` 设为完整域名。请求超时 15s；后端不可达、响应非 JSON 会在界面给出可读错误与当前基址。环境变量清单见仓库根 [`README.md`](../README.md)「快速开始 · 3. 配置环境变量」。

## 相关文档

- 项目约定与红线：[`docs/func/client/约定与红线.md`](../docs/func/client/约定与红线.md)
- 运营端视觉：[`docs/ui/web/`](../docs/ui/web/) ｜ 接口：[`docs/api/web/`](../docs/api/web/) ｜ 功能：[`docs/func/web/`](../docs/func/web/)
