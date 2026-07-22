from fastapi.testclient import TestClient


def test_caption_is_deterministic(client: TestClient) -> None:
    payload = {
        "imageUrl": "http://backend/internal/a",
        "title": "Blue Study",
        "credit": "A. Artist",
        "context": None,
    }

    first = client.post("/captions", json=payload)
    second = client.post("/captions", json=payload)

    assert first.status_code == 200
    assert first.json() == second.json()
    assert first.json() == {
        "label": "Placeholder draft",
        "text": (
            'Deterministic demo text based only on submitted metadata: "Blue Study", '
            "credited to A. Artist. No image content was analyzed."
        ),
        "engine": "deterministic-placeholder",
        "engineVersion": "1",
    }


def test_caption_includes_optional_editorial_context_as_metadata(client: TestClient) -> None:
    response = client.post(
        "/captions",
        json={
            "imageUrl": "https://backend/internal/a",
            "title": "Blue Study",
            "credit": "A. Artist",
            "context": "Gallery note",
        },
    )

    assert response.status_code == 200
    assert response.json()["text"].endswith(
        "Submitted editorial context: Gallery note. No image content was analyzed."
    )


def test_caption_rejects_missing_title(client: TestClient) -> None:
    response = client.post(
        "/captions",
        json={
            "imageUrl": "http://backend/internal/a",
            "credit": "A. Artist",
            "context": None,
        },
    )

    assert response.status_code == 422


def test_caption_rejects_unsupported_url_scheme(client: TestClient) -> None:
    response = client.post(
        "/captions",
        json={
            "imageUrl": "file:///tmp/art.png",
            "title": "Blue Study",
            "credit": "A. Artist",
            "context": None,
        },
    )

    assert response.status_code == 422


def test_caption_rejects_extra_fields(client: TestClient) -> None:
    response = client.post(
        "/captions",
        json={
            "imageUrl": "http://backend/internal/a",
            "title": "Blue Study",
            "credit": "A. Artist",
            "context": None,
            "prompt": "Invent a description",
        },
    )

    assert response.status_code == 422
