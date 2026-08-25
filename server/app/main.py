from __future__ import annotations

from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from sqlalchemy import text

from .auth import ROLE_ADMIN, ROLE_MANAGER, hash_password
from .database import Base, SessionLocal, engine
from .models import Admin, Project
from .stock import backfill_inbound_nos
from .routers import app_release, auth, customers, inventory, orders, projects, revenue

STATIC_DIR = Path(__file__).resolve().parent.parent / "static"


def migrate_schema() -> None:
    """SQLite 轻量迁移：birthday、项目单位。"""
    with engine.begin() as conn:
        cols = [row[1] for row in conn.execute(text("PRAGMA table_info(customers)")).fetchall()]
        if cols and "birthday" not in cols:
            conn.execute(text("ALTER TABLE customers ADD COLUMN birthday DATE"))
        if cols and "wechat" not in cols:
            conn.execute(text("ALTER TABLE customers ADD COLUMN wechat VARCHAR(64) DEFAULT ''"))
        if cols and "address" not in cols:
            conn.execute(text("ALTER TABLE customers ADD COLUMN address VARCHAR(255) DEFAULT ''"))
        if cols and "intention" not in cols:
            conn.execute(text("ALTER TABLE customers ADD COLUMN intention VARCHAR(16) DEFAULT ''"))
        pcols = [row[1] for row in conn.execute(text("PRAGMA table_info(projects)")).fetchall()]
        if pcols and "unit" not in pcols:
            conn.execute(text("ALTER TABLE projects ADD COLUMN unit VARCHAR(16) DEFAULT '个'"))
        if pcols:
            conn.execute(
                text("UPDATE projects SET unit = '次' WHERE unit IS NULL OR unit = '' OR unit = '单位'")
            )
            if "stock_qty" not in pcols:
                conn.execute(text("ALTER TABLE projects ADD COLUMN stock_qty INTEGER DEFAULT 0"))
            if "cost_price" not in pcols:
                conn.execute(text("ALTER TABLE projects ADD COLUMN cost_price NUMERIC(12, 2) DEFAULT 0"))
        mcols = [row[1] for row in conn.execute(text("PRAGMA table_info(stock_movements)")).fetchall()]
        if mcols and "item_id" not in mcols:
            conn.execute(text("DROP TABLE stock_movements"))
        icols = [row[1] for row in conn.execute(text("PRAGMA table_info(stock_items)")).fetchall()]
        if icols and "unit" not in icols:
            conn.execute(text("ALTER TABLE stock_items ADD COLUMN unit VARCHAR(16) DEFAULT '个'"))
        if icols and "spec" not in icols:
            conn.execute(text("ALTER TABLE stock_items ADD COLUMN spec VARCHAR(64) DEFAULT ''"))
        if icols:
            conn.execute(
                text("UPDATE stock_items SET unit = '个' WHERE unit IS NULL OR unit = '' OR unit = '单位'")
            )
        mcols = [row[1] for row in conn.execute(text("PRAGMA table_info(stock_movements)")).fetchall()]
        if mcols and "inbound_no" not in mcols:
            conn.execute(text("ALTER TABLE stock_movements ADD COLUMN inbound_no VARCHAR(32) DEFAULT ''"))
        mcols = [row[1] for row in conn.execute(text("PRAGMA table_info(stock_movements)")).fetchall()]
        if mcols and "unit" not in mcols:
            conn.execute(text("ALTER TABLE stock_movements ADD COLUMN unit VARCHAR(16) DEFAULT '个'"))
        if mcols:
            conn.execute(
                text("UPDATE stock_movements SET unit = '个' WHERE unit IS NULL OR unit = '' OR unit = '单位'")
            )
        acols = [row[1] for row in conn.execute(text("PRAGMA table_info(admins)")).fetchall()]
        if acols and "role" not in acols:
            conn.execute(text("ALTER TABLE admins ADD COLUMN role VARCHAR(16) DEFAULT 'admin'"))
        if acols:
            conn.execute(text("UPDATE admins SET role = 'admin' WHERE role IS NULL OR role = ''"))


def seed_data() -> None:
    db = SessionLocal()
    try:
        if not db.query(Admin).filter(Admin.username == "admin").first():
            db.add(
                Admin(
                    username="admin",
                    password_hash=hash_password("admin123"),
                    role=ROLE_ADMIN,
                )
            )
        for row in db.query(Admin).all():
            if not (row.role or "").strip():
                row.role = ROLE_ADMIN
        if not db.query(Admin).filter(Admin.username == "manager").first():
            db.add(
                Admin(
                    username="manager",
                    password_hash=hash_password("manager123"),
                    role=ROLE_MANAGER,
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
                        unit="次",
                        active=True,
                    ),
                    Project(
                        name="头顶加密套餐",
                        description="头顶稀疏区域加密",
                        price=16800,
                        graft_count=2000,
                        unit="次",
                        active=True,
                    ),
                    Project(
                        name="全头植发尊享套餐",
                        description="大面积脱发综合方案",
                        price=29800,
                        graft_count=4000,
                        unit="次",
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
    Base.metadata.create_all(bind=engine)
    backfill_inbound_nos()
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
    app.include_router(inventory.router)
    app.include_router(orders.router)
    app.include_router(revenue.router)
    app.include_router(app_release.router)

    if STATIC_DIR.exists():
        app.mount("/", StaticFiles(directory=str(STATIC_DIR), html=True), name="static")

    return app


app = create_app()
