from fastapi.testclient import TestClient

from canvas_caption_worker.main import app


def test_health(client: TestClient) -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ready"}
