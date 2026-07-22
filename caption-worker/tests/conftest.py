import pytest
from fastapi.testclient import TestClient

from canvas_caption_worker.main import app


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)
