from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    """camelCase on the wire (matches the Java side), snake_case in Python, unknown fields rejected."""
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="forbid")