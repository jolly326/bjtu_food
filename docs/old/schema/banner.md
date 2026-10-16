# banner — 首页轮播运营位

**用途**：首页顶部轮播图。静态运营位，无推荐算法、无跳转。
**对应实体**：`com.bjtufood.banner.entity.Banner`

## 列定义

| 列 | 类型 | 可空 | 默认 | 键 | 说明 |
|---|---|---|---|---|---|
| `id` | BIGINT | NO | AUTO_INCREMENT | PK | Banner ID（端上作轮播项稳定 key） |
| `image_url` | VARCHAR(500) | NO | `''` | | 轮播图地址；出参经 `ImageUrlUtil` 转绝对 URL |
| `sort_order` | INT | NO | 0 | | 展示顺序（**升序**）；**不出参** |
| `status` | VARCHAR(10) | NO | `'on'` | KEY | `on` 启用 / `off` 停用；**不出参** |
| `created_at` | DATETIME | NO | `CURRENT_TIMESTAMP` | | |
| `updated_at` | DATETIME | NO | `CURRENT_TIMESTAMP ON UPDATE` | | |

## 索引

| 索引名 | 列 | 类型 | 用途 |
|---|---|---|---|
| `PRIMARY` | `id` | 主键 | |
| `idx_banner_status_sort` | `status`, `sort_order` | 复合 | 公开查询：`WHERE status='on' ORDER BY sort_order` 完全走索引 |

## 关键设计

### 静态运营位，无个性化
- **不引入推荐算法** —— 轮播内容由运营位顺序决定，与用户无关
- **无分页**：运营位数量级极小，返回 `List<T>` 而非 `PageResult`

### 仅出参 2 个字段
`sort_order` / `status` 是服务端内部字段（排序与过滤用），端上零消费 → **不出参**。

**无跳转字段**：`targetType` / `targetId` / `targetUrl` **均不存在** —— v1 **无点击交互**。将来要做跳转，须先由 UI 文档定义交互再扩字段。

### 录入入口 = 管理后台
Banner 由**管理后台**维护（[A5 首页 Banner 管理](../func/web/A-主数据维护/A5-首页Banner管理.md)：新增 / 换图 / 拖拽排序 / 启停 / 删除；**启用上限 6 张**）。
> 种子脚本仅作**初始素材来源**，日常维护一律走管理后台。

## 空集合语义
无启用 Banner 时返回**空数组 `[]`** —— 不返回 404、不返回 null。端上退化为占位图，**不阻塞首屏**（请求与菜品列表并行）。

## 素材规格
| 项 | 规格 | 原因 |
|---|---|---|
| 宽高比 | **统一 16:10** | 混比例会导致轮播切换时块高抖动、吸顶阈值漂移 |
| 加载中 / 失败 | 同高占位 | 保持块高不变 |

## 关联

无外键 —— 本表不引用任何业务表。

接口契约见 [api/client/banner.md](../api/client/banner.md)；页面形态见 [client/ui/首页菜品浏览.md](../ui/client/首页菜品浏览.md)。
