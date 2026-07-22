from typing import Literal

from pydantic import BaseModel, ConfigDict, HttpUrl, field_validator


class CaptionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    imageUrl: HttpUrl
    title: str
    credit: str
    context: str | None

    @field_validator("title", "credit")
    @classmethod
    def require_metadata(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("metadata must not be blank")
        return normalized

    @field_validator("context")
    @classmethod
    def normalize_context(cls, value: str | None) -> str | None:
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None


class CaptionResponse(BaseModel):
    label: str
    text: str
    engine: Literal["deterministic-placeholder"] = "deterministic-placeholder"
    engineVersion: Literal["1"] = "1"
