# server · 服务端

学生端（小程序）与运营端（管理后台）共用的**唯一数据出口**。

- 技术栈：FastAPI · SQLAlchemy 2.0 · MySQL 8.0（InnoDB, utf8mb4）· 进程内限频与 TTL 缓存（无 Redis）
- 运行档位：`APP_PROFILE`（`development` 缺省 / `production`）选环境文件；`APP_ENV`（`dev` 时暴露 `/docs`）

## 目录与职责

```
app/
├── main.py          # 装配：路由聚合、中间件、异常处理、启动校验
├── core/            # 横切：config / db / deps / envelope / errors / pagination / cache / ratelimit /
│                    #       schemas / security / storage / content_security / embedding / formatters
├── models/          # SQLAlchemy 模型（库结构唯一映射）
├── admin/           # 运营端域：auth · canteen · stall · dish · feedback · image
└── student/         # 学生端域：auth · dish · feedback
```

- 每个业务域固定 `router.py`（路由 + 入/出参）· `service.py`（业务/校验/缓存失效）· `repo.py`（查询）。
- **例外**：`admin/image/` 无 `repo.py`（上传中转，不落业务表）。
- **依赖方向**：`router → service → repo → models`；`core` 只被依赖、不反向导入业务；`admin/` 与 `student/` 互不导入。

## 本地运行

```bash
cd server
pip install -r requirements.txt -r requirements-dev.txt
uvicorn app.main:app --reload --port 8000     # 接口文档 http://127.0.0.1:8000/docs
pytest                                        # 单测 + 分层护栏（importlinter）
```

环境变量与三端联调见仓库根 [`README.md`](../README.md)「快速开始 · 3. 配置环境变量」，凭据清单与部署边界见 [`docs/secur/S-04-凭据与部署边界.md`](../docs/secur/S-04-凭据与部署边界.md)。

## 库结构变更

**无 alembic、不维护 SQL 脚本**：库结构真源是 [`docs/schema/`](../docs/schema/)，变更流程为「**先改表文档 → 直连远程库执行 → 回写文档**」，远程库实况与文档必须一致。

## 运维脚本

| 脚本 | 用途 |
|---|---|
| `scripts/backfill_embeddings.py` | 切换 `EMBEDDING_*` 模型后**回填存量菜品向量**（`--dry-run` / `--limit` / `--only-missing`）；执行后需重启服务或等 30s 缓存过期 |

## 相关文档

- 项目约定与红线：[`docs/func/client/约定与红线.md`](../docs/func/client/约定与红线.md)
- 接口契约：[`docs/api/`](../docs/api/) ｜ 库表：[`docs/schema/`](../docs/schema/) ｜ 功能：[`docs/func/`](../docs/func/)
