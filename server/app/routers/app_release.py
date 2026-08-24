from __future__ import annotations

import json
import os
from datetime import datetime
from pathlib import Path

from fastapi import APIRouter, HTTPException, status
from fastapi.responses import FileResponse

from ..schemas import AppReleaseOut

BASE_DIR = Path(__file__).resolve().parent.parent.parent
DOWNLOADS_DIR = BASE_DIR / "downloads"
APP_APK_NAME = "hairclinic.apk"
APP_VERSION_FILE = "app_version.json"
APP_VERSION_NAME = os.environ.get("APP_VERSION_NAME", "1.0.0")
APP_VERSION_CODE = int(os.environ.get("APP_VERSION_CODE", "1"))
DOWNLOAD_PATH = f"/download/{APP_APK_NAME}"

router = APIRouter(tags=["app"])


def app_apk_path() -> Path:
    return DOWNLOADS_DIR / APP_APK_NAME


def app_version_meta() -> tuple[str, int]:
    path = DOWNLOADS_DIR / APP_VERSION_FILE
    if path.is_file():
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
            name = str(data.get("version_name") or APP_VERSION_NAME)
            code = int(data.get("version_code") or APP_VERSION_CODE)
            return name, code
        except (OSError, TypeError, ValueError, json.JSONDecodeError):
            pass
    return APP_VERSION_NAME, APP_VERSION_CODE


def app_release_info() -> AppReleaseOut | None:
    path = app_apk_path()
    if not path.is_file():
        return None
    stat = path.stat()
    version_name, version_code = app_version_meta()
    return AppReleaseOut(
        version_name=version_name,
        version_code=version_code,
        filename=APP_APK_NAME,
        size_bytes=stat.st_size,
        updated_at=datetime.fromtimestamp(stat.st_mtime).replace(microsecond=0).isoformat(sep=" "),
        download_url=DOWNLOAD_PATH,
    )


@router.get("/api/app/info", response_model=AppReleaseOut)
def api_app_info():
    info = app_release_info()
    if not info:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Android 安装包尚未发布。")
    return info


@router.get(DOWNLOAD_PATH)
def download_apk():
    path = app_apk_path()
    if not path.is_file():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Android 安装包尚未发布。")
    return FileResponse(
        path,
        media_type="application/vnd.android.package-archive",
        filename=APP_APK_NAME,
    )
