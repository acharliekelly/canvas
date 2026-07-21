from fastapi import FastAPI

app = FastAPI(title="CANVAS Caption Worker")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ready"}
