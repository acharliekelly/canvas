"""FastAPI endpoints for placeholder readiness and metadata-derived caption drafts."""

from fastapi import FastAPI

from canvas_caption_worker.contracts import CaptionRequest, CaptionResponse

app = FastAPI(title="CANVAS Caption Worker")


@app.get("/health")
def health() -> dict[str, str]:
    """Report process readiness only; this does not validate caption inference capability."""
    return {"status": "ready"}


@app.post("/captions")
async def caption(request: CaptionRequest) -> CaptionResponse:
    """Return deterministic draft text from metadata without fetching or analyzing the image."""
    metadata = (
        f'Deterministic demo text based only on submitted metadata: "{request.title}", '
        f"credited to {request.credit}."
    )
    if request.context:
        metadata += f" Submitted editorial context: {request.context}."
    return CaptionResponse(
        label="Placeholder draft",
        text=f"{metadata} No image content was analyzed.",
    )
