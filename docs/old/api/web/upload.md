# web/upload — 管理端素材上传

**归属**：`AdminUploadController`
**通用约定**见 [api/README.md](../README.md)
**鉴权**：全部端点需管理端 JWT（见 [web/auth.md](./auth.md)）｜ 缺 / 失效 `401`

> 管理端**唯一**上传入口。学生端上传（`/upload/*`）见 [upload.md](../client/upload.md)，不受本文档影响。

---

## POST /admin/upload

**用途**：上传单张图片（即传即得）

### 请求形态
`multipart/form-data`，字段名 `file`（**单文件**）。

### 请求字段
| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | file | **是** | 图片文件 |

### 处理约束
| 项 | 口径 |
|---|---|
| 允许类型 | `image/jpeg` / `image/png` / `image/webp`（扩展名 `jpg` / `jpeg` / `png` / `webp`，**另做文件头 magic number 校验**）；其它 → `400` |
| 大小上限 | 单张 **≤ 5MB** |
| 张数 | **由各业务字段自行约束**（[web/dishes.md](./dishes.md) 图片 ≤5 张、[web/banners.md](./banners.md) 单张）；本端点不设总量 |

### 响应 `data` = `UploadResultVO`
| 字段 | 类型 | 说明 |
|---|---|---|
| `url` | string | **可直接访问的图片地址**（COS 已配置时为 COS 绝对 URL；未配置走本地降级链路时为站内相对路径） |
| `relativeUrl` | string | **仅本地降级链路返回** |

### 错误码
| code | 条件 |
|---|---|
| `400` | 类型不支持 / 超大小 / 空文件 / 文件头非法 |
| `401` | 未带 / token 失效 |
| `500` | 存储故障（端上提示重试） |

### 入库口径
- **存上传返回值原样**（`dish.images` 的 `VARCHAR(1024)` 与 `banner.image_url` 同口径）：本地链路存站内相对路径、COS 链路存绝对 URL。
- **出参展示层**对已是绝对的地址原样返回、对站内相对路径经 `ImageUrlUtil` 转绝对。

### 备注
- **为什么两种形态并存**：本地降级链路给站内相对路径（换域名不失效、列宽也只够相对路径 + JSON 结构）；COS 链路只能给 CDN 绝对地址（对象键不在服务端拼装，换域名需重新生成历史地址 —— 已接受的权衡）。
