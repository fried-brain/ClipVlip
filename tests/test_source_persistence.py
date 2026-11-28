from fastapi.testclient import TestClient

from backend import app as app_module


def test_uploaded_file_source_is_persisted_for_processing(tmp_path, monkeypatch):
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
    )

    assert response.status_code == 200
    task = app_module.get_task(response.json()["id"])
    assert task["source"].startswith("file:")
    assert task["source"].endswith(".mp4")
