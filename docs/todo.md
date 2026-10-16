# 待决事项清单

> **用途**：集中登记需要项目所有者拍板的开放议题，便于逐条过。
> **本文件不是设计真源** —— 每条议题一经拍板，结论写入对应真源（[`README.md`](./README.md) · [`schema/`](./schema/README.md) · [`api/`](./api/README.md) · [`func/client/`](./func/client/README.md) · [`func/web/`](./func/web/README.md) · [`ui/client/`](./ui/client/README.md) · [`ui/web/`](./ui/web/README.md) · [`secur/`](./secur/README.md)），并先从本文件移除。
> 各文档内的零散未决项以 `⏳` 就地标记，不重复登记在此；此处只收**跨文档的架构级**待决。

---

## 1. `docs/old/` 归档区的处置

- **现状**：旧体系文档整体归档于 `docs/old/`，当前**未纳入 git 跟踪**。
- **选项**：**A** 保留并纳入 git 跟踪，立规「`old/` 非真源、不参与一致性与命名约束」｜ **B** 改名为 `docs/.archive/` ｜ **C** 从工作区移除。
- **我的建议**：**A**。**连带项**：门禁脚本（`check:doc` / `check:ui`）的扫描范围需排除归档区（属代码改动，需明示后执行）。
