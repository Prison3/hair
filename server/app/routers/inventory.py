from __future__ import annotations

from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from ..auth import get_current_admin
from ..database import get_db
from ..models import Admin, Project, StockMovement
from ..schemas import ProjectOut, StockMoveIn, StockMovementOut
from ..stock import stock_in, stock_out

router = APIRouter(prefix="/api/inventory", tags=["inventory"])


@router.get("", response_model=List[ProjectOut])
def list_inventory(
    q: Optional[str] = Query(None),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    query = db.query(Project).order_by(Project.id.desc())
    if q:
        like = f"%{q.strip()}%"
        query = query.filter(Project.name.like(like))
    return query.all()


@router.get("/movements", response_model=List[StockMovementOut])
def list_movements(
    project_id: Optional[int] = None,
    kind: Optional[str] = None,
    limit: int = Query(80, ge=1, le=200),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    query = db.query(StockMovement).order_by(
        StockMovement.moved_at.desc(), StockMovement.id.desc()
    )
    if project_id is not None:
        query = query.filter(StockMovement.project_id == project_id)
    if kind:
        query = query.filter(StockMovement.kind == kind.upper())
    return query.limit(limit).all()


@router.post("/in", response_model=StockMovementOut, status_code=status.HTTP_201_CREATED)
def inbound(
    body: StockMoveIn,
    db: Session = Depends(get_db),
    admin: Admin = Depends(get_current_admin),
):
    project = db.get(Project, body.project_id)
    if not project:
        raise HTTPException(status_code=404, detail="项目不存在")
    movement = stock_in(
        db,
        project,
        body.quantity,
        body.unit_cost,
        body.remark,
        admin.id,
        body.moved_at,
    )
    db.commit()
    db.refresh(movement)
    return movement


@router.post("/out", response_model=StockMovementOut, status_code=status.HTTP_201_CREATED)
def outbound(
    body: StockMoveIn,
    db: Session = Depends(get_db),
    admin: Admin = Depends(get_current_admin),
):
    project = db.get(Project, body.project_id)
    if not project:
        raise HTTPException(status_code=404, detail="项目不存在")
    movement = stock_out(db, project, body.quantity, body.remark, admin.id)
    db.commit()
    db.refresh(movement)
    return movement
