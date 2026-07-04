# Web 管理端 - 设计文档

> 版本：v1.0 | 更新日期：2026-07
>
> 仅系统管理员（admin）使用。

---

## 一、页面路由

所有页面在 `/dashboard` 路径下，使用 AdminLayout 布局：

| 路由 | 页面 |
|------|------|
| `/login` | 登录页 |
| `/dashboard` | 数据概览看板 |
| `/dashboard/canteens` | 食堂列表 |
| `/dashboard/canteens/:id` | 食堂详情（含档口管理） |
| `/dashboard/canteens/:id/stalls/:id` | 档口详情（含菜品管理） |
| `/dashboard/canteens/:id/stalls/:id/dishes/:id` | 菜品详情（含评论管理） |
| `/dashboard/content-review` | 内容审核 |
| `/dashboard/users` | 用户管理 |
| `/dashboard/banners` | 轮播管理 |
| `/dashboard/admins` | 账号设置 |

---

## 二、布局结构

```
AdminLayout
├── 侧边栏（深红 #6B1010 背景）
│   ├── 数据概览
│   ├── 食堂管理
│   ├── 内容审核
│   ├── 轮播管理
│   ├── 用户管理
│   └── 账号设置（底部）
├── 顶栏
│   ├── 折叠按钮
│   ├── 面包屑导航
│   └── 全局搜索
└── 内容区（路由页面）
```

---

## 三、组件规划

| 组件 | 用途 |
|------|------|
| AdminLayout | 整体布局框架 |
| StatsCard | 统计指标卡片 |
| TrendChart | ECharts 评论趋势图 |
| DataTable | 通用数据表格 |
| FormDialog | 通用表单弹窗 |
| ImageUpload | 图片上传组件 |
| ConfirmDialog | 确认弹窗 |
| StatusTag | 状态标签（审核/用户状态） |
| UserSearch | 用户搜索筛选 |

---

## 四、数据流

```
Web 管理端 → http.ts → API_BASE_URL/api → 后端
                           ↑
                     token (localStorage)
```

所有请求统一走 `web/src/api/http.ts`，自动注入 admin 的 JWT Token。
