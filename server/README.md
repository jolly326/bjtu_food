# server · 服务端

学生端（小程序）与运营端（管理后台）共用的**唯一数据出口**。

- 技术栈：FastAPI · SQLAlchemy 2.0 · MySQL 8.0（InnoDB, utf8mb4）· Redis（限流/缓存，可选）
- 运行档位：`APP_PROFILE`（`development` 缺省 / `production`）选环境文件；`APP_ENV`（`dev` 时暴露 `/docs`）

## 目录与职责

```
app/
├── main.py          # 装配：路由聚合、中间件、异常处理、启动校验
├── core/            # 横切：config / db / deps / envelope / errors / pagination / cache / ratelimit / schemas / security / storage / formatters
├── models/          # SQLAlchemy 模型（库结构唯一映射）
├── admin/           # 运营端域：auth · canteen · stall · dish · issue · option · image
└── student/         # 学生端域：auth · canteen · dish · issue
```

- 每个业务域固定 `router.py`（路由 + 入/出参）· `service.py`（业务/校验/缓存失效）· `repo.py`（查询）。
- **例外**：`admin/image/` 无 `repo.py`（上传中转，不落业务表）。
- **依赖方向**：`router → service → repo → models`；`core` 只被依赖、不反向导入业务；`admin/` 与 `student/` 互不导入。

## 本地运行

```bash
cd server
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000     # 接口文档 http://127.0.0.1:8000/docs
pytest                                        # 单测（需已安装依赖）
```

环境变量与三端联调见仓库根 [`ENVIRONMENT.md`](../ENVIRONMENT.md)。

## 库结构变更

**无 alembic**：库结构真源是 [`docs/schema/`](../docs/schema/)，变更流程为「先改表文档 → 再执行手工 SQL」。一次性脚本放 `server/scripts/`（如 `migrate_add_canteen_stall_image.sql`、`cleanup_keep_today.py`）。

## 相关文档

- 架构与分包：[`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)
- 接口契约：[`docs/api/`](../docs/api/) ｜ 库表：[`docs/schema/`](../docs/schema/) ｜ 功能：[`docs/func/`](../docs/func/)
