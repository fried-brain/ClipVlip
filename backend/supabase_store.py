"""Server-side Supabase REST and private Storage adapter for ClipVlip."""
from __future__ import annotations

import os
from typing import Any
from urllib.parse import quote

import httpx


class SupabaseStore:
    def __init__(self) -> None:
        self.url = os.getenv("SUPABASE_URL", "").rstrip("/")
        self.key = os.getenv("SUPABASE_SECRET_KEY", "")
        self.source_bucket = os.getenv("SUPABASE_SOURCE_BUCKET", "clipvlip-sources")
        self.output_bucket = os.getenv("SUPABASE_OUTPUT_BUCKET", "clipvlip-outputs")
        self.enabled = bool(self.url and self.key)
        self.timeout = httpx.Timeout(7200.0, connect=30.0, write=7200.0, read=7200.0)

    def _headers(self, prefer: str | None = None) -> dict[str, str]:
        headers = {"apikey": self.key, "Authorization": f"Bearer {self.key}"}
        if prefer:
            headers["Prefer"] = prefer
        return headers

    def _request(self, method: str, path: str, **kwargs):
        if not self.enabled:
            raise RuntimeError("Supabase is not configured. Set SUPABASE_URL and SUPABASE_SECRET_KEY.")
        headers = self._headers(kwargs.pop("prefer", None))
        headers.update(kwargs.pop("headers", {}))
        response = httpx.request(method, f"{self.url}{path}", headers=headers,
                                 timeout=self.timeout, **kwargs)
        response.raise_for_status()
        return response

    def create_job(self, job: dict[str, Any]) -> None:
        self._request("POST", "/rest/v1/video_jobs", json=job, prefer="return=minimal")

    def get_job(self, job_id: str) -> dict[str, Any] | None:
        response = self._request("GET", "/rest/v1/video_jobs",
            params={"id": f"eq.{job_id}", "select": "*", "limit": "1"})
        rows = response.json()
        return rows[0] if rows else None

    def list_jobs(self, limit: int = 50) -> list[dict[str, Any]]:
        response = self._request("GET", "/rest/v1/video_jobs",
            params={"select": "*", "order": "created_at.desc", "limit": str(limit)})
        return response.json()

    def update_job(self, job_id: str, values: dict[str, Any]) -> None:
        payload = dict(values)
        if "clips" in payload:
            payload.pop("clips")
        if payload:
            self._request("PATCH", "/rest/v1/video_jobs",
                params={"id": f"eq.{job_id}"}, json=payload, prefer="return=minimal")

    def upload(self, bucket: str, path: str, file_path: str, content_type: str) -> None:
        with open(file_path, "rb") as stream:
            self._request("POST", f"/storage/v1/object/{quote(bucket)}/{quote(path, safe='/')}",
                content=stream, headers={"Content-Type": content_type, "x-upsert": "true"})

    def download(self, bucket: str, path: str, destination: str) -> None:
        if not self.enabled:
            raise RuntimeError("Supabase is not configured.")
        url = f"{self.url}/storage/v1/object/{quote(bucket)}/{quote(path, safe='/')}"
        with httpx.stream("GET", url, headers=self._headers(), timeout=self.timeout) as response:
            response.raise_for_status()
            with open(destination, "wb") as stream:
                for chunk in response.iter_bytes(1024 * 1024):
                    stream.write(chunk)

    def save_asset(self, job_id: str, asset_type: str, path: str, filename: str,
                   content_type: str, size_bytes: int | None = None) -> None:
        self._request("POST", "/rest/v1/video_assets", json={
            "job_id": job_id, "asset_type": asset_type, "storage_path": path,
            "filename": filename, "content_type": content_type, "size_bytes": size_bytes,
        }, prefer="resolution=merge-duplicates,return=minimal")

    def replace_clips(self, job_id: str, clips: list[dict[str, Any]]) -> None:
        self._request("DELETE", "/rest/v1/video_clips", params={"job_id": f"eq.{job_id}"})
        if clips:
            rows = [{
                "job_id": job_id, "clip_index": int(c["id"]),
                "title": c.get("title", "Clip"), "reason": c.get("reason", ""),
                "start_seconds": float(c["start"]), "end_seconds": float(c["end"]),
                "score": c.get("score"), "asset_path": c.get("storage_path"),
                "metadata": {k: v for k, v in c.items() if k not in {
                    "id", "title", "reason", "start", "end", "score", "storage_path"}},
            } for c in clips]
            self._request("POST", "/rest/v1/video_clips", json=rows, prefer="return=minimal")

    def get_clips(self, job_id: str) -> list[dict[str, Any]]:
        response = self._request("GET", "/rest/v1/video_clips",
            params={"job_id": f"eq.{job_id}", "select": "*", "order": "clip_index.asc"})
        return [{
            **(row.get("metadata") or {}), "id": row["clip_index"],
            "title": row["title"], "reason": row.get("reason") or "",
            "start": row["start_seconds"], "end": row["end_seconds"],
            "score": row.get("score"), "storage_path": row.get("asset_path"),
            "filename": f"clip-{row['clip_index']:02d}.mp4",
            "url": f"/api/clips/{job_id}/clip-{row['clip_index']:02d}.mp4",
        } for row in response.json()]
