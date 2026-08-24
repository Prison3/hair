from __future__ import annotations

from datetime import date
from decimal import Decimal, ROUND_HALF_UP

from fastapi import HTTPException
from sqlalchemy.orm import Session

from .models import Project, StockMovement
from .schemas import PHYSICAL_UNITS, STOCK_IN, STOCK_OUT


def _money(value: Decimal) -> Decimal:
    return Decimal(value).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def is_physical(project: Project) -> bool:
    return (project.unit or "") in PHYSICAL_UNITS


def stock_in(
    db: Session,
    project: Project,
    quantity: int,
    unit_cost: Decimal,
    remark: str = "",
    admin_id: int | None = None,
    moved_at: date | None = None,
) -> StockMovement:
    qty = int(quantity)
    cost = _money(unit_cost or 0)
    old_qty = int(project.stock_qty or 0)
    old_cost = _money(project.cost_price or 0)
    if old_qty > 0 and cost > 0:
        project.cost_price = _money((old_cost * old_qty + cost * qty) / (old_qty + qty))
    elif cost > 0:
        project.cost_price = cost
    project.stock_qty = old_qty + qty
    movement = StockMovement(
        project_id=project.id,
        project_name=project.name,
        kind=STOCK_IN,
        quantity=qty,
        unit_cost=cost,
        remark=remark or "",
        moved_at=moved_at or date.today(),
        created_by=admin_id,
    )
    db.add(movement)
    return movement


def stock_out(
    db: Session,
    project: Project,
    quantity: int,
    remark: str = "",
    admin_id: int | None = None,
    moved_at: date | None = None,
) -> StockMovement:
    qty = int(quantity)
    on_hand = int(project.stock_qty or 0)
    if on_hand < qty:
        raise HTTPException(
            status_code=400,
            detail=f"库存不足：{project.name} 仅剩 {on_hand} {project.unit or ''}".strip(),
        )
    project.stock_qty = on_hand - qty
    movement = StockMovement(
        project_id=project.id,
        project_name=project.name,
        kind=STOCK_OUT,
        quantity=qty,
        unit_cost=_money(project.cost_price or 0),
        remark=remark or "",
        moved_at=moved_at or date.today(),
        created_by=admin_id,
    )
    db.add(movement)
    return movement
