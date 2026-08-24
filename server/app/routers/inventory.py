from __future__ import annotations

from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import or_
from sqlalchemy.orm import Session

from ..auth import get_current_admin
from ..database import get_db
from ..models import Admin, StockItem, StockMovement
from ..schemas import StockInBody, StockItemIn, StockItemOut, StockMovementOut, StockOutBody
from ..stock import delete_inbound, stock_in, stock_out

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


def _duplicate_name(db: Session, name: str, exclude_id: Optional[int] = None) -> bool:
    query = db.query(StockItem).filter(StockItem.name == name)
    if exclude_id is not None:
        query = query.filter(StockItem.id != exclude_id)
    return query.first() is not None


@router.post("", response_model=StockItemOut, status_code=status.HTTP_201_CREATED)
def create_item(
    body: StockItemIn,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    if _duplicate_name(db, body.name):
        raise HTTPException(status_code=400, detail="已有同名产品")
    item = StockItem(name=body.name, stock_qty=0, cost_price=0)
    db.add(item)
    db.commit()
    db.refresh(item)
    return item


@router.get("/movements", response_model=List[StockMovementOut])
def list_movements(
    item_id: Optional[int] = None,
    kind: Optional[str] = None,
    q: Optional[str] = Query(None),
    limit: int = Query(200, ge=1, le=500),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    query = db.query(StockMovement).order_by(
        StockMovement.created_at.desc(), StockMovement.id.desc()
    )
    if item_id is not None:
        query = query.filter(StockMovement.item_id == item_id)
    if kind:
        query = query.filter(StockMovement.kind == kind.upper())
    if q:
        like = f"%{q.strip()}%"
        query = query.filter(
            or_(StockMovement.item_name.like(like), StockMovement.inbound_no.like(like))
        )
    return query.limit(limit).all()


@router.delete("/movements/{movement_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_movement(
    movement_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    movement = db.get(StockMovement, movement_id)
    if not movement:
        raise HTTPException(status_code=404, detail="入库记录不存在")
    delete_inbound(db, movement)
    db.commit()


@router.get("/{item_id}", response_model=StockItemOut)
def get_item(
    item_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    item = db.get(StockItem, item_id)
    if not item:
        raise HTTPException(status_code=404, detail="产品不存在")
    return item


@router.put("/{item_id}", response_model=StockItemOut)
def update_item(
    item_id: int,
    body: StockItemIn,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    item = db.get(StockItem, item_id)
    if not item:
        raise HTTPException(status_code=404, detail="产品不存在")
    if _duplicate_name(db, body.name, exclude_id=item_id):
        raise HTTPException(status_code=400, detail="已有同名产品")
    item.name = body.name
    db.commit()
    db.refresh(item)
    return item


@router.delete("/{item_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_item(
    item_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    item = db.get(StockItem, item_id)
    if not item:
        raise HTTPException(status_code=404, detail="产品不存在")
    db.query(StockMovement).filter(StockMovement.item_id == item_id).delete(
        synchronize_session=False
    )
    db.delete(item)
    db.commit()


@router.post("/in", response_model=StockMovementOut, status_code=status.HTTP_201_CREATED)
def inbound(
    body: StockInBody,
    db: Session = Depends(get_db),
    admin: Admin = Depends(get_current_admin),
):
    movement = stock_in(
        db,
        item_id=body.item_id,
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
        raise HTTPException(status_code=404, detail="产品不存在")
    movement = stock_out(db, item, body.quantity, body.remark, admin.id)
    db.commit()
    db.refresh(movement)
    return movement
