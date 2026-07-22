from fastapi import FastAPI

from canvas_caption_worker.contracts import CaptionRequest, CaptionResponse

app = FastAPI(title="CANVAS Caption Worker")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ready"}


@app.post("/captions")
async def caption(request: CaptionRequest) -> CaptionResponse:
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
