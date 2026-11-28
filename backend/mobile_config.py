from __future__ import annotations
import os
from fastapi import APIRouter

router = APIRouter(prefix="/api/mobile", tags=["mobile"])

@router.get("/config")
def mobile_config():
    """Public mobile settings only; never return credentials or private keys."""
    return {
        "schema_version": 1,
        "app": {
            "minimum_supported_version_code": int(os.getenv("CLIPVLIP_MIN_APP_VERSION", "1")),
            "maintenance": os.getenv("CLIPVLIP_MAINTENANCE_MODE", "false").lower() == "true",
            "maintenance_message": os.getenv("CLIPVLIP_MAINTENANCE_MESSAGE", ""),
        },
        "features": {
            "direct_updates": os.getenv("CLIPVLIP_DIRECT_UPDATES", "true").lower() == "true",
            "google_play_updates": os.getenv("CLIPVLIP_PLAY_UPDATES", "true").lower() == "true",
        },
        "announcements": [],
    }
