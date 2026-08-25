from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal, ROUND_HALF_UP

from fastapi import HTTPException
from sqlalchemy.orm import Session

from .models import StockItem, StockMovement
from .schemas import STOCK_IN, STOCK_OUT


def _money(value: Decimal) -> Decimal:
    return Decimal(value).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def make_movement_no(db: Session, when: datetime | None = None) -> str:
    """出入库编号：YYYYMMDDHHMMSS，冲突时追加 -n。"""
    base = (when or datetime.now()).strftime("%Y%m%d%H%M%S")
    no = base
    n = 1
    while db.query(StockMovement).filter(StockMovement.inbound_no == no).first():
        n += 1
        no = f"{base}-{n}"
    return no


# 兼容旧调用名
make_inbound_no = make_movement_no


def backfill_inbound_nos() -> None:
    from .database import SessionLocal

    db = SessionLocal()
    try:
        rows = (
            db.query(StockMovement)
            .filter((StockMovement.inbound_no == "") | (StockMovement.inbound_no.is_(None)))
            .order_by(StockMovement.id.asc())
            .all()
        )
        for movement in rows:
            movement.inbound_no = make_movement_no(db, movement.created_at or datetime.now())
            db.flush()
        db.commit()
    finally:
        db.close()


def get_or_create_item(db: Session, name: str, unit: str, spec: str) -> StockItem:
    item = db.query(StockItem).filter(StockItem.name == name).first()
    if item:
        if unit:
            item.unit = unit
        if spec:
            item.spec = spec
        return item
    item = StockItem(
        name=name,
        spec=spec or "",
        unit=unit or "个",
        stock_qty=0,
        cost_price=Decimal("0"),
    )
    db.add(item)
    db.flush()
    return item


def stock_in(
    db: Session,
    quantity: int,
    unit_cost: Decimal,
    item_id: int | None = None,
    name: str = "",
    unit: str = "个",
    spec: str = "",
    admin_id: int | None = None,
    moved_at: date | None = None,
) -> StockMovement:
    if item_id:
        item = db.get(StockItem, item_id)
        if not item:
            raise HTTPException(status_code=404, detail="产品不存在")
        if unit:
            item.unit = unit
    else:
        name = (name or "").strip()
        if not name:
            raise HTTPException(status_code=400, detail="请选择产品")
        item = get_or_create_item(db, name, unit, spec)
    qty = int(quantity)
    cost = _money(unit_cost or 0)
    old_qty = int(item.stock_qty or 0)
    old_cost = _money(item.cost_price or 0)
    if old_qty > 0 and cost > 0:
        item.cost_price = _money((old_cost * old_qty + cost * qty) / (old_qty + qty))
    elif cost > 0:
        item.cost_price = cost
    item.stock_qty = old_qty + qty
    movement = StockMovement(
        item_id=item.id,
        item_name=item.name,
        kind=STOCK_IN,
        quantity=qty,
        unit=unit or item.unit or "个",
        unit_cost=cost,
        remark="",
        moved_at=moved_at or date.today(),
        inbound_no=make_movement_no(db),
        created_by=admin_id,
    )
    db.add(movement)
    return movement


def stock_out(
    db: Session,
    item: StockItem,
    quantity: int,
    remark: str = "",
    admin_id: int | None = None,
    moved_at: date | None = None,
) -> StockMovement:
    qty = int(quantity)
    on_hand = int(item.stock_qty or 0)
    if on_hand < qty:
        raise HTTPException(
            status_code=400,
            detail=f"库存不足：{item.name} 仅剩 {on_hand} {item.unit or ''}".strip(),
        )
    item.stock_qty = on_hand - qty
    movement = StockMovement(
        item_id=item.id,
        item_name=item.name,
        kind=STOCK_OUT,
        quantity=qty,
        unit=item.unit or "个",
        unit_cost=_money(item.cost_price or 0),
        remark=remark or "",
        moved_at=moved_at or date.today(),
        inbound_no=make_movement_no(db),
        created_by=admin_id,
    )
    db.add(movement)
    return movement


def delete_inbound(
    db: Session,
    movement: StockMovement,
) -> None:
    if movement.kind != STOCK_IN:
        raise HTTPException(status_code=400, detail="只能删除入库记录")
    item = db.get(StockItem, movement.item_id)
    qty = int(movement.quantity or 0)
    if item:
        on_hand = int(item.stock_qty or 0)
        if on_hand < qty:
            raise HTTPException(status_code=400, detail="该入库已出货，无法删除")
        item.stock_qty = on_hand - qty
        if item.stock_qty <= 0:
            item.stock_qty = 0
            item.cost_price = Decimal("0")
    db.delete(movement)
