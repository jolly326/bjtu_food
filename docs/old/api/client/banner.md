# client/banner — 首页轮播

**归属**：`BannerController`
**通用约定**见 [api/README.md](../README.md)

---

## GET /banners

**鉴权**：🔓 公开 ｜ **用途**：首页顶部轮播图数据源

### 请求参数
无。

### 响应 `data` = `BannerVO[]`
| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| `id` | number | 否 | Banner ID（端上作轮播项的稳定 key） |
| `imageUrl` | string | 否 | 轮播图**绝对 URL**（素材统一 16:10） |

### 排序与过滤
- 仅返回**启用中**（`status='on'`）的 Banner
- 按 `sort_order` **升序**
- **无分页**（运营位数量级极小，返回 `List<T>` 而非分页结构）

### 空集合语义
无启用 Banner 时返回**空数组 `[]`** —— **不返回 404、不返回 null**，端上退化为占位图。

### 错误码
无（公开只读）。

### 备注
- **仅出参 2 个字段**：`sort_order` / `status` 为服务端内部字段，端上零消费，**不出参**。
- **无跳转字段**（`targetType` / `targetId` / `targetUrl` 均不存在）—— v1 **无点击交互**。将来要做跳转，须先由 UI 文档定义交互再扩字段。
- 请求与菜品列表**并行**发出，Banner 失败**不阻塞首屏**；单张图加载失败只该张降级为占位。
- **录入入口在管理后台**（见 [A5 首页 Banner 管理](../../func/web/A-主数据维护/A5-首页Banner管理.md)）；本端点在公开侧**只读**，管理端写端点见 [web/banners.md](../web/banners.md)。

存储映射见 [schema/banner.md](../../schema/banner.md)。
