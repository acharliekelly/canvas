"""Typed metadata-only caption contract for the deterministic placeholder worker."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, HttpUrl, field_validator


class CaptionRequest(BaseModel):
    """Forbid unknown fields and normalize metadata.

    ``imageUrl`` is syntactically validated, but the placeholder does not fetch it or decode or
    inspect the image.
    """

    model_config = ConfigDict(extra="forbid")

    imageUrl: HttpUrl
    title: str
    credit: str
    context: str | None

    @field_validator("title", "credit")
    @classmethod
    def require_metadata(cls, value: str) -> str:
        """Trim required metadata and reject blank values."""
        normalized = value.strip()
        if not normalized:
            raise ValueError("metadata must not be blank")
        return normalized

    @field_validator("context")
    @classmethod
    def normalize_context(cls, value: str | None) -> str | None:
        """Trim optional context to ``None`` when it is blank."""
        if value is None:
            return None
        normalized = value.strip()
        return normalized or None


class CaptionResponse(BaseModel):
    """Identify deterministic placeholder provenance with its fixed engine and version."""

    label: str
    text: str
    engine: Literal["deterministic-placeholder"] = "deterministic-placeholder"
    engineVersion: Literal["1"] = "1"
