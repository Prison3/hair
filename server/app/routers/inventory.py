from __future__ import annotations

from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from ..auth import get_current_admin
from ..database import get_db
from ..models import Admin, StockItem, StockMovement
from ..schemas import StockInBody, StockItemOut, StockMovementOut, StockOutBody
from ..stock import stock_in, stock_out

router = APIRouter(prefix="/api/inventory", tags=["inventory"])


@router.get("", response_model=List[StockItemOut])
def list_inventory(
    q: Optional[str] = Query(None),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    query = db.query(StockItem).order_by(StockItem.id.desc())
    if q:
        like = f"%{q.strip()}%"
        query = query.filter(StockItem.name.like(like))
    return query.all()


@router.get("/movements", response_model=List[StockMovementOut])
def list_movements(
    item_id: Optional[int] = None,
    kind: Optional[str] = None,
    limit: int = Query(80, ge=1, le=200),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    query = db.query(StockMovement).order_by(
        StockMovement.moved_at.desc(), StockMovement.id.desc()
    )
    if item_id is not None:
        query = query.filter(StockMovement.item_id == item_id)
    if kind:
        query = query.filter(StockMovement.kind == kind.upper())
    return query.limit(limit).all()


@router.get("/{item_id}", response_model=StockItemOut)
def get_item(
    item_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    item = db.get(StockItem, item_id)
    if not item:
        raise HTTPException(status_code=404, detail="库存商品不存在")
    return item


@router.post("/in", response_model=StockMovementOut, status_code=status.HTTP_201_CREATED)
def inbound(
    body: StockInBody,
    db: Session = Depends(get_db),
    admin: Admin = Depends(get_current_admin),
):
    movement = stock_in(
        db,
        name=body.name,
        quantity=body.quantity,
        unit_cost=body.unit_cost,
        unit=body.unit,
        spec=body.spec,
        admin_id=admin.id,
        moved_at=body.moved_at,
    )
    db.commit()
    db.refresh(movement)
    return movement


@router.post("/out", response_model=StockMovementOut, status_code=status.HTTP_201_CREATED)
def outbound(
    body: StockOutBody,
    db: Session = Depends(get_db),
    admin: Admin = Depends(get_current_admin),
):
    item = db.get(StockItem, body.item_id)
    if not item:
        raise HTTPException(status_code=404, detail="库存商品不存在")
    movement = stock_out(db, item, body.quantity, body.remark, admin.id)
    db.commit()
    db.refresh(movement)
    return movement
