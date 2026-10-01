# 首页 Banner 管理（B-07）

> 所属端：**管理后台（Web）** ｜ 鉴权：🔑 管理员
> 返回：[管理后台功能总览](./README.md)
>
> ⚠️ 本管理后台（Web 后台）已软冻结，待后期整体重构移除（详见 [管理后台功能总览](./README.md) 顶部声明）。

## 介绍

维护学生端首页顶部的**轮播 Banner**（静态运营位）。

**定位边界（遵守产品定型「轻运营」）**：
- Banner 是**静态展示位**，**不做推荐算法、不做个性化、不做投放策略**；
- 学生端按 `sortOrder` 升序**原样渲染**，不排序、不写死张数（张数由本功能决定）；
- **无跳转目标**——点击不响应。若将来需要「点 Banner 跳某菜品」，须同时修订学生端 `BannerVO` 与渲染逻辑，属演进预留（做之前须重新拍板）。

**素材规格**：固定 **16:10**。混比例会导致轮播切换时块高抖动、吸顶阈值漂移，故上传即校验比例；不符合的素材在本功能拒绝保存。

**启停**：停用项学生端 `GET /banners` 不下发；无结果时学生端退化为占位图（**不返回 404 / null**，返回空数组 `[]`）。

**删除**：物理删除，不留软删标记。

## 接口

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/admin/banners` | 🔑 | Banner 列表（含停用，按 `sortOrder` 升序；**不分页**） |
| POST | `/admin/banners` | 🔑 | 新增 Banner |
| PUT | `/admin/banners/{id}` | 🔑 | 修改 Banner（不存在返回 `4001`） |
| PUT | `/admin/banners/{id}/status` | 🔑 | 启停切换（幂等） |
| DELETE | `/admin/banners/{id}` | 🔑 | 删除 Banner |

## 字段

### 请求 · 新增 / 修改（`BannerSaveReq`）

| 字段名 | 类型 | 必填 | 中文解释 |
|---|---|---|---|
| `imageUrl` | string | 是 | 图片绝对 URL（经 `POST /admin/upload/image` 上传取得；**须为 16:10**） |
| `sortOrder` | number | 否 | 展示顺序（升序，默认 0） |

### 响应 · `BannerAdminVO`

| 字段名 | 类型 | 中文解释 |
|---|---|---|
| `id` | number | Banner ID |
| `imageUrl` | string | 图片绝对 URL |
| `sortOrder` | number | 展示顺序 |
| `status` | string | `on` / `off` |
| `createdAt` | string | 创建时间 |
| `updatedAt` | string | 更新时间 |

### 错误码

| 码 | 场景 |
|---|---|
| `400` | 图片地址为空 / 非法；素材非 16:10 |
| `4001` | Banner 不存在 |

## 数据（读写）

| 表 | 读写什么 | 中文解释 |
|---|---|---|
| `banner` | 增改删查 + `sort_order` / `status` | Banner 本体 |

## 与当前代码的差异

> 本节集中登记与现有代码的差异；清零即写「无」。

- Banner 管理端增删改：`POST / PUT / DELETE`。
- **新增 16:10 比例校验**：现有实现不校验素材比例（依靠人工出图）。
