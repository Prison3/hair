from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from sqlalchemy import text

from .auth import hash_password
from .database import Base, SessionLocal, engine
from .models import Admin, Project
from .routers import app_release, auth, customers, orders, projects

STATIC_DIR = Path(__file__).resolve().parent.parent / "static"


def migrate_schema() -> None:
    """SQLite 轻量迁移：age -> birthday。"""
    with engine.begin() as conn:
        cols = [row[1] for row in conn.execute(text("PRAGMA table_info(customers)")).fetchall()]
        if cols and "birthday" not in cols:
            conn.execute(text("ALTER TABLE customers ADD COLUMN birthday DATE"))


def seed_data() -> None:
    db = SessionLocal()
    try:
        if not db.query(Admin).filter(Admin.username == "admin").first():
            db.add(
                Admin(
                    username="admin",
                    password_hash=hash_password("admin123"),
                )
            )
        if db.query(Project).count() == 0:
            db.add_all(
                [
                    Project(
                        name="前额植发基础套餐",
                        description="适合轻度发际线后退",
                        price=12800,
                        graft_count=1500,
                        active=True,
                    ),
                    Project(
                        name="头顶加密套餐",
                        description="头顶稀疏区域加密",
                        price=16800,
                        graft_count=2000,
                        active=True,
                    ),
                    Project(
                        name="全头植发尊享套餐",
                        description="大面积脱发综合方案",
                        price=29800,
                        graft_count=4000,
                        active=True,
                    ),
                ]
            )
        db.commit()
    finally:
        db.close()


def create_app() -> FastAPI:
    Base.metadata.create_all(bind=engine)
    migrate_schema()
    seed_data()

    app = FastAPI(title="心尚植发", version="1.0.0")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(auth.router)
    app.include_router(customers.router)
    app.include_router(projects.router)
    app.include_router(orders.router)
    app.include_router(app_release.router)

    if STATIC_DIR.exists():
        app.mount("/", StaticFiles(directory=str(STATIC_DIR), html=True), name="static")

    return app


app = create_app()
