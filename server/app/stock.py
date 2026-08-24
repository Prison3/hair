from __future__ import annotations

from datetime import date
from decimal import Decimal, ROUND_HALF_UP

from fastapi import HTTPException
from sqlalchemy.orm import Session

from .models import StockItem, StockMovement
from .schemas import STOCK_IN, STOCK_OUT


def _money(value: Decimal) -> Decimal:
    return Decimal(value).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


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
    name: str,
    quantity: int,
    unit_cost: Decimal,
    unit: str = "个",
    spec: str = "",
    admin_id: int | None = None,
    moved_at: date | None = None,
) -> StockMovement:
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
        unit_cost=cost,
        remark="",
        moved_at=moved_at or date.today(),
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
        unit_cost=_money(item.cost_price or 0),
        remark=remark or "",
        moved_at=moved_at or date.today(),
        created_by=admin_id,
    )
    db.add(movement)
    return movement
