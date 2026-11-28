from fastapi.testclient import TestClient

from backend import app as app_module


def test_health_reports_task_auth_and_billing_disabled():
    response = TestClient(app_module.app).get("/api/health")
    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "name": "ClipVlip",
        "auth": "required_for_tasks",
        "billing": "disabled",
    }


def test_create_task_persists_caption_preferences_with_worker_key(tmp_path, monkeypatch):
    monkeypatch.setattr(app_module, "DATA", tmp_path)
    monkeypatch.setattr(app_module, "UPLOADS", tmp_path / "uploads")
    monkeypatch.setattr(app_module, "OUTPUTS", tmp_path / "outputs")
    monkeypatch.setattr(app_module, "DB", tmp_path / "clipvlip.db")
    monkeypatch.setenv("CLIPVLIP_ACCESS_KEY", "test-worker-key")
    app_module.UPLOADS.mkdir()
    app_module.OUTPUTS.mkdir()
    monkeypatch.setattr(app_module, "process", lambda task_id, settings: None)

    response = TestClient(app_module.app).post(
        "/api/tasks",
        headers={"X-ClipVlip-Key": "test-worker-key"},
        files={"file": ("sample.mp4", b"fake-video-content", "video/mp4")},
        data={
            "captions": "true",
            "caption_style": "neon",
            "aspect_ratio": "9:16",
            "min_duration": "15",
            "max_duration": "45",
            "clip_count": "3",
        },
    )

    assert response.status_code == 200
    task = app_module.get_task(response.json()["id"])
    assert task["settings"]["captions"] is True
    assert task["settings"]["caption_style"] == "neon"
    assert task["settings"]["clip_count"] == 3
    assert task["source"].startswith("file:")


def test_cloud_job_api_requires_worker_key(monkeypatch):
    monkeypatch.setenv("CLIPVLIP_ACCESS_KEY", "a-long-test-key")
    client = TestClient(app_module.app)
    assert client.get("/api/tasks").status_code == 401
    assert client.get("/api/tasks", headers={"X-ClipVlip-Key": "wrong"}).status_code == 401
    assert client.get("/api/tasks", headers={"X-ClipVlip-Key": "a-long-test-key"}).status_code == 200


def test_cloud_job_api_fails_closed_without_configured_key(monkeypatch):
    monkeypatch.delenv("CLIPVLIP_ACCESS_KEY", raising=False)
    response = TestClient(app_module.app).get("/api/tasks")
    assert response.status_code == 503
