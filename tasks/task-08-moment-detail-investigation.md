# task-08 · moment 动态详情后端排查（页面清单评议 → 决议：需后端参与）

> 文档性质：技术负责人派工任务（新，待办）。
> 权威顺序：`docs/project_spec.md` §3（API）/ §5 开发约束 > 本任务 > `backend/src/main/java/com/bjtufood/moment/**` > 代码现状。

## 背景 / 决议
页面清单评议发现：`GET /moments/{id}`（动态详情）返回**非 200/404/401** 的可疑状态，需要后端参与确认数据是否存在、审核可见性是否合理。本任务产出排查清单 + 验收，交由后端确认并修复。

## 已初查（技术负责人静态定位，供后端核实）
`backend/.../moment/service/impl/MomentServiceImpl.java`：
```java
public MomentVO detail(Long id, Long currentUserId) {
    Moment m = momentMapper.selectById(id);
    if (m == null) throw new BusinessException("动态不存在");   // ← 关键疑点
    MomentVO vo = toVO(m);
    if (currentUserId != null && currentUserId.equals(m.getUserId())) vo.setRejectReason(m.getRejectReason());
    else vo.setRejectReason(null);
    return enrich(vo);
}
```
- **疑点 A（非 404 根因）**：动态不存在时抛 `BusinessException("动态不存在")`。而 `BusinessException(String)` 默认 `code=400`（`common/exception/BusinessException.java`），`GlobalExceptionHandler` 经 `Result.of(e.getCode(),...)` 返回 **`code=400`（HTTP 200 体 400）**，并非 HTTP 404。前端 `getMomentDetail` 判定 `code!==200` 抛错，故「不存在」→ 前端收到 400 业务码（HTTP 200）。
- **疑点 B（审核可见性）**：`detail()` 仅 `selectById` 判空，**未按 `auditStatus=approved` 过滤**，也未校验 `status`（上/下线）。即：pending/rejected 或已下架(off) 动态，非作者本人也可经 `/moments/{id}` 详情读到（仅作者可见 rejectReason）。与「小程序只展示 approved 且未隐藏」（§5 审核流）不一致。
- **疑点 C（白名单）**：`GET /moments/{id}` 属 `GET /moments/**` 白名单（PUB），401 不应出现；若出现 401 多为 token 过期或鉴权链路异常，需后端核实。

## 排查清单（交付物）
1. 确认 `GET /moments/{id}` 对「不存在 / 存在 / 已下线 / 待审 / 已退回」五类输入的实际 HTTP 状态码与 `code` 体。
2. 确认 `BusinessException(String)` 默认 code 是否应为 404（或前端按 message 识别）；**决策**：是否将「动态不存在」改为 HTTP 404（`BusinessException(404,...)`），使语义正确。
3. 确认详情接口是否应过滤 `audit_status=approved` 且 `status` 正常（仅作者本人可看自己的 pending/rejected，且作者可看 rejectReason）；补过滤后同步「作者本人可见性」豁免逻辑。
4. 确认白名单 `GET /moments/**` 是否误放行需要登录的写操作子路径；确认 401 是否可能出现在详情。
5. 前端 `frontend/src/api/moment.ts` `getMomentDetail` 对非 200 的错误处理是否友好（Toast 文案/重试）。

## 验收标准
- [ ] 排查清单逐项有后端确认结论（代码/测试佐证）。
- [ ] 修复后：`GET /moments/{id}` 对不存在返回 HTTP 404；存在且 approved+normal 返回 200；作者本人看自己待审/退回返回 200 且带 rejectReason；他人看不到非 approved/已下架动态（返回 404 或 400）。
- [ ] 前端详情页错误态友好（`EmptyState` 文案「动态不存在或已下架」+ 返回）。
- [ ] 与 task-06 §4（关联动态聚合）联动后，三详情页关联动态点击可正确进详情。
- [ ] 小程序 dev 编译通过。

## 依赖
- 后端 `moment` 模块；前端 `getMomentDetail`/`pages-detail/moment.vue`。
- 与 task-06 §5 审核可见性、§4 关联动态联动。
