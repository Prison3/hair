from __future__ import annotations

import re
import uuid
from pathlib import Path

from fastapi import HTTPException, UploadFile

from .database import DATA_DIR

UPLOAD_ROOT = DATA_DIR / "uploads" / "customers"
PHOTO_BEFORE = "BEFORE"
PHOTO_AFTER = "AFTER"
PHOTO_KINDS = {PHOTO_BEFORE, PHOTO_AFTER}
MAX_PHOTO_BYTES = 10 * 1024 * 1024
ALLOWED_MIME = {
    "image/jpeg": ".jpg",
    "image/jpg": ".jpg",
    "image/png": ".png",
    "image/webp": ".webp",
    "image/heic": ".heic",
    "image/heif": ".heif",
}


def customer_photo_dir(customer_id: int) -> Path:
    return UPLOAD_ROOT / str(customer_id)


def photo_file_path(customer_id: int, stored_name: str) -> Path:
    return customer_photo_dir(customer_id) / stored_name


def ensure_upload_root() -> None:
    UPLOAD_ROOT.mkdir(parents=True, exist_ok=True)


def validate_kind(kind: str) -> str:
    value = (kind or "").strip().upper()
    if value not in PHOTO_KINDS:
        raise HTTPException(status_code=400, detail="照片类型无效，可选：BEFORE / AFTER")
    return value


def guess_ext(filename: str, content_type: str) -> str:
    mime = (content_type or "").split(";", 1)[0].strip().lower()
    if mime in ALLOWED_MIME:
        return ALLOWED_MIME[mime]
    name = (filename or "").lower()
    for ext in (".jpg", ".jpeg", ".png", ".webp", ".heic", ".heif"):
        if name.endswith(ext):
            return ext if ext != ".jpeg" else ".jpg"
    return ".jpg"


async def save_upload(customer_id: int, kind: str, upload: UploadFile) -> tuple[str, str, str]:
    ensure_upload_root()
    content_type = (upload.content_type or "").split(";", 1)[0].strip().lower()
    if content_type and content_type not in ALLOWED_MIME:
        raise HTTPException(status_code=400, detail="仅支持 JPG / PNG / WEBP 图片")
    data = await upload.read()
    if not data:
        raise HTTPException(status_code=400, detail="图片为空")
    if len(data) > MAX_PHOTO_BYTES:
        raise HTTPException(status_code=400, detail="单张图片不能超过 10MB")
    ext = guess_ext(upload.filename or "", content_type or "image/jpeg")
    stored_name = f"{kind.lower()}_{uuid.uuid4().hex}{ext}"
    folder = customer_photo_dir(customer_id)
    folder.mkdir(parents=True, exist_ok=True)
    target = folder / stored_name
    target.write_bytes(data)
    mime = content_type or "image/jpeg"
    original = re.sub(r"[^\w.\-()\u4e00-\u9fff ]", "_", (upload.filename or "").strip())[:200]
    return stored_name, original, mime


def delete_photo_file(customer_id: int, stored_name: str) -> None:
    path = photo_file_path(customer_id, stored_name)
    if path.is_file():
        path.unlink(missing_ok=True)


def delete_customer_photo_files(customer_id: int) -> None:
    folder = customer_photo_dir(customer_id)
    if folder.is_dir():
        for child in folder.iterdir():
            if child.is_file():
                child.unlink(missing_ok=True)
        try:
            folder.rmdir()
        except OSError:
            pass
