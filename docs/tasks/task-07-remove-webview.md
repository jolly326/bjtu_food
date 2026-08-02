# task-07 · 移除 webview 页面（页面清单评议 → 决议：业务只跳外部公众号文章）

> 文档性质：技术负责人派工任务（**✅ 已完成，commit 7dcba46**）。
> 权威顺序：`docs/project_spec.md` §2.1（页面架构）/ §4 > 本任务 > 代码现状。
> ⚠️ `docs/mini-app-ui/webview.md` 已随页面移除一并删除（与之前砍页处理一致）。

## 背景 / 决议
页面清单评议确定：小程序业务只跳**外部公众号文章 / H5 外链**，无独立 webview 承载诉求，且 `pages/webview/index`（主包页）与「仅跳外链」的场景重复。**决议：移除 webview 路由与页面**，外链跳转改为更轻量的「复制链接」方案。

## 目标
砍掉 `pages/webview/index` 路由与页面，评估并处置其跳转来源（Banner `targetType=URL`、广播条外链），确保移除后外链仍可触达、无孤儿路由/死链。

## 涉及文件
- `frontend/src/pages/webview/index.vue`（删除）
- `frontend/src/pages.json`（移除 webview 页面注册；主包 8 → 7）
- `frontend/src/pages/home/index.vue`（Banner `targetType=URL` 跳转、广播条外链跳转的 webview 调用改写为复制链接）
- `docs/project_spec.md` §2.1（主包列表去 webview，8→7；同步页面数）、`docs/mini-app-ui/README.md` + `webview.md`（移除文档/索引）
- 其他调用 `/pages/webview` 处（`grep` 全仓排查）

## 任务
1. 删除 `pages/webview/index.vue`，从 `pages.json` 主包移除 webview 注册。
2. **跳转来源处置**（关键）：
   - `home/index.vue:182`（Banner `targetType=URL` → `uni.navigateTo /pages/webview`）→ 改为 `uni.setClipboardData` 复制 `targetUrl` + Toast「已复制链接，请到浏览器打开」（**不跳独立页**）。
   - `home/index.vue:289`（广播条外链 → `/pages/webview`）→ 同上改复制链接。
3. 全仓 `grep '/pages/webview'` 清零；无孤儿路由/死链。
4. 同步文档：`project_spec.md` §2.1 主包 8→7（去掉 webview）、mini-app-ui README 页数与 webview.md 移除。
5. `docs/web-ui.md` / `docs/web-ui/` 无 webview 相关引用（复核）。

## 验收标准
- [ ] `pages/webview` 路由与页面删除；主包变 7 页（17 → 16）。
- [ ] Banner `URL` 与广播条外链点击后复制链接 + Toast，可在外置浏览器打开（**不跳独立 webview 页**）。
- [ ] `grep '/pages/webview'` = 0 处，无孤儿路由/死链。
- [ ] `project_spec.md` §2.1 / mini-app-ui README 页面数与清单同步更新。
- [ ] 小程序 dev 编译通过，无控制台报错。

## 依赖
- 依赖 task-02（页面架构，减少一页）。
- 与 task-06 §7（Banner 外链跳转改写）联动——URL 跳转统一改为复制链接。
