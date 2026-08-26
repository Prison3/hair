from __future__ import annotations

from calendar import monthrange
from datetime import date, datetime, timezone
from decimal import Decimal
from typing import Dict, List, Optional, Set
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session, selectinload

from ..auth import require_admin
from ..database import get_db
from ..models import Admin, Order, Project, StockItem, StockMovement
from ..schemas import STOCK_IN, RevenueDayOut, RevenueSummaryOut

router = APIRouter(prefix="/api/revenue", tags=["revenue"])

TZ = ZoneInfo("Asia/Shanghai")
REVENUE_STATUSES = ("PAID", "DONE")
ZERO = Decimal("0.00")


def _month_bounds(year: int, month: int) -> tuple[datetime, datetime, date, date]:
    """Return naive-UTC datetimes for order filter and local dates for inbound filter."""
    start_local = datetime(year, month, 1, tzinfo=TZ)
    if month == 12:
        end_local = datetime(year + 1, 1, 1, tzinfo=TZ)
    else:
        end_local = datetime(year, month + 1, 1, tzinfo=TZ)
    last_day = monthrange(year, month)[1]
    start_utc = start_local.astimezone(timezone.utc).replace(tzinfo=None)
    end_utc = end_local.astimezone(timezone.utc).replace(tzinfo=None)
    return start_utc, end_utc, start_local.date(), date(year, month, last_day)


def _order_local_day(created_at: datetime) -> date:
    if created_at.tzinfo is None:
        created_at = created_at.replace(tzinfo=timezone.utc)
    return created_at.astimezone(TZ).date()


def _empty_day(d: date) -> Dict[str, object]:
    return {
        "revenue": ZERO,
        "order_count": 0,
        "cost": ZERO,
        "inbound_count": 0,
    }


def _load_stock_costs(db: Session, item_ids: Set[int]) -> Dict[int, Decimal]:
    if not item_ids:
        return {}
    rows = db.query(StockItem).filter(StockItem.id.in_(item_ids)).all()
    return {row.id: Decimal(str(row.cost_price or 0)) for row in rows}


def _load_project_medicines(db: Session, project_ids: Set[int]) -> Dict[int, list]:
    if not project_ids:
        return {}
    rows = (
        db.query(Project)
        .options(selectinload(Project.medicines))
        .filter(Project.id.in_(project_ids))
        .all()
    )
    return {row.id: list(row.medicines or []) for row in rows}


def _order_cogs(
    order: Order,
    stock_costs: Dict[int, Decimal],
    project_meds: Dict[int, list],
) -> Decimal:
    """订单销售成本：产品/项目耗材按进货单价（cost_price）汇总。"""
    total = ZERO
    for line in order.items or []:
        qty = int(line.quantity or 0)
        if qty <= 0:
            continue
        if line.item_id:
            total += stock_costs.get(int(line.item_id), ZERO) * qty
            continue
        if line.project_id:
            for med in project_meds.get(int(line.project_id), []):
                med_qty = int(med.quantity or 0)
                if med_qty <= 0:
                    continue
                total += stock_costs.get(int(med.item_id), ZERO) * med_qty * qty
    return total


@router.get("/summary", response_model=RevenueSummaryOut)
def revenue_summary(
    year: Optional[int] = Query(None, ge=2000, le=2100),
    month: Optional[int] = Query(None, ge=1, le=12),
    db: Session = Depends(get_db),
    _: Admin = Depends(require_admin),
):
    now = datetime.now(TZ)
    y = year or now.year
    m = month or now.month
    if year is None and month is not None:
        raise HTTPException(status_code=400, detail="请同时指定 year 与 month")
    if month is None and year is not None:
        raise HTTPException(status_code=400, detail="请同时指定 year 与 month")

    start_utc, end_utc, start_day, end_day = _month_bounds(y, m)
    last_day = end_day.day
    by_day: Dict[date, Dict[str, object]] = {
        date(y, m, d): _empty_day(date(y, m, d)) for d in range(1, last_day + 1)
    }

    orders = (
        db.query(Order)
        .options(selectinload(Order.items))
        .filter(Order.status.in_(REVENUE_STATUSES))
        .filter(Order.created_at >= start_utc)
        .filter(Order.created_at < end_utc)
        .all()
    )

    project_ids: Set[int] = set()
    item_ids: Set[int] = set()
    for order in orders:
        for line in order.items or []:
            if line.item_id:
                item_ids.add(int(line.item_id))
            elif line.project_id:
                project_ids.add(int(line.project_id))

    project_meds = _load_project_medicines(db, project_ids)
    for meds in project_meds.values():
        for med in meds:
            item_ids.add(int(med.item_id))
    stock_costs = _load_stock_costs(db, item_ids)

    for order in orders:
        day = _order_local_day(order.created_at)
        if day not in by_day:
            continue
        bucket = by_day[day]
        bucket["revenue"] = Decimal(str(bucket["revenue"])) + Decimal(str(order.total_amount or 0))
        bucket["cost"] = Decimal(str(bucket["cost"])) + _order_cogs(order, stock_costs, project_meds)
        bucket["order_count"] = int(bucket["order_count"]) + 1

    inbounds = (
        db.query(StockMovement)
        .filter(StockMovement.kind == STOCK_IN)
        .filter(StockMovement.moved_at >= start_day)
        .filter(StockMovement.moved_at <= end_day)
        .all()
    )
    for movement in inbounds:
        day = movement.moved_at
        if day not in by_day:
            continue
        by_day[day]["inbound_count"] = int(by_day[day]["inbound_count"]) + 1

    days: List[RevenueDayOut] = []
    month_revenue = ZERO
    month_cost = ZERO
    month_orders = 0
    month_inbounds = 0
    for d in range(1, last_day + 1):
        key = date(y, m, d)
        bucket = by_day[key]
        revenue = Decimal(str(bucket["revenue"])).quantize(Decimal("0.01"))
        cost = Decimal(str(bucket["cost"])).quantize(Decimal("0.01"))
        order_count = int(bucket["order_count"])
        inbound_count = int(bucket["inbound_count"])
        month_revenue += revenue
        month_cost += cost
        month_orders += order_count
        month_inbounds += inbound_count
        days.append(
            RevenueDayOut(
                date=key.isoformat(),
                day=d,
                revenue=revenue,
                order_count=order_count,
                cost=cost,
                inbound_count=inbound_count,
                profit=(revenue - cost).quantize(Decimal("0.01")),
            )
        )

    return RevenueSummaryOut(
        year=y,
        month=m,
        revenue=month_revenue.quantize(Decimal("0.01")),
        order_count=month_orders,
        cost=month_cost.quantize(Decimal("0.01")),
        inbound_count=month_inbounds,
        profit=(month_revenue - month_cost).quantize(Decimal("0.01")),
        days=days,
    )
