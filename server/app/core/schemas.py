"""出参模型基类。

🔴 **契约单源**：所有出参字段一律 **camelCase**（由 ``alias_generator=to_camel`` 生成），
与 ``docs/api/`` 各资源文档的字段名逐字一致；``check:contract`` 即该一致性的判据。
入参同样走本基类：**出 camelCase、入双向兼容**（``populate_by_name=True`` ——
camelCase 与 snake_case 都接受），但文档与运营端类型一律只写 camelCase 形态。
"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class OutModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class InModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)
