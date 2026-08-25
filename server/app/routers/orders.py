from __future__ import annotations

from collections import defaultdict
from datetime import datetime
from decimal import Decimal
from typing import Dict, List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session, joinedload, selectinload

from ..auth import get_current_admin
from ..database import get_db
from ..models import Admin, Customer, Order, OrderItem, Project, StockItem
from ..schemas import OrderCreate, OrderOut, OrderStatusUpdate
from ..stock import stock_out

router = APIRouter(prefix="/api", tags=["orders"])

VALID_STATUSES = {"PENDING", "PAID", "DONE", "CANCELLED"}


def _order_no() -> str:
    return datetime.utcnow().strftime("HC%Y%m%d%H%M%S%f")[:-3]


def serialize_order(order: Order) -> OrderOut:
    return OrderOut(
        id=order.id,
        order_no=order.order_no,
        customer_id=order.customer_id,
        customer_name=order.customer.name if order.customer else None,
        customer_phone=order.customer.phone if order.customer else None,
        total_amount=order.total_amount,
        status=order.status,
        remark=order.remark or "",
        created_at=order.created_at,
        items=order.items,
    )


def _collect_stock_needs(db: Session, body_items: List) -> Dict[int, int]:
    """按项目关联产品汇总出库数量：产品用量 × 项目数量。"""
    needs: Dict[int, int] = defaultdict(int)
    for item in body_items:
        project = (
            db.query(Project)
            .options(selectinload(Project.medicines))
            .filter(Project.id == item.project_id)
            .first()
        )
        if not project or not project.active:
            raise HTTPException(status_code=400, detail=f"项目不可用: {item.project_id}")
        for med in project.medicines or []:
            needs[med.item_id] += int(med.quantity) * int(item.quantity)
    return dict(needs)


def _ensure_stock(db: Session, needs: Dict[int, int]) -> Dict[int, StockItem]:
    stocks: Dict[int, StockItem] = {}
    for item_id, qty in needs.items():
        if qty <= 0:
            continue
        stock = db.get(StockItem, item_id)
        if not stock:
            raise HTTPException(status_code=400, detail=f"项目关联产品不存在: {item_id}")
        on_hand = int(stock.stock_qty or 0)
        if on_hand < qty:
            raise HTTPException(
                status_code=400,
                detail=f"库存不足：{stock.name} 需要 {qty}，仅剩 {on_hand}",
            )
        stocks[item_id] = stock
    return stocks


@router.get("/orders", response_model=List[OrderOut])
def list_orders(
    customer_id: Optional[int] = None,
    status_filter: Optional[str] = Query(None, alias="status"),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    query = (
        db.query(Order)
        .options(joinedload(Order.customer), joinedload(Order.items))
        .order_by(Order.id.desc())
    )
    if customer_id is not None:
        query = query.filter(Order.customer_id == customer_id)
    if status_filter:
        query = query.filter(Order.status == status_filter.upper())
    return [serialize_order(o) for o in query.all()]


@router.get("/orders/{order_id}", response_model=OrderOut)
def get_order(
    order_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    order = (
        db.query(Order)
        .options(joinedload(Order.customer), joinedload(Order.items))
        .filter(Order.id == order_id)
        .first()
    )
    if not order:
        raise HTTPException(status_code=404, detail="订单不存在")
    return serialize_order(order)


@router.get("/customers/{customer_id}/orders", response_model=List[OrderOut])
def list_customer_orders(
    customer_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    customer = db.get(Customer, customer_id)
    if not customer:
        raise HTTPException(status_code=404, detail="客户不存在")
    orders = (
        db.query(Order)
        .options(joinedload(Order.customer), joinedload(Order.items))
        .filter(Order.customer_id == customer_id)
        .order_by(Order.id.desc())
        .all()
    )
    return [serialize_order(o) for o in orders]


@router.post("/orders", response_model=OrderOut, status_code=status.HTTP_201_CREATED)
def create_order(
    body: OrderCreate,
    db: Session = Depends(get_db),
    admin: Admin = Depends(get_current_admin),
):
    customer = db.get(Customer, body.customer_id)
    if not customer:
        raise HTTPException(status_code=404, detail="客户不存在")

    needs = _collect_stock_needs(db, body.items)
    stocks = _ensure_stock(db, needs)

    items: List[OrderItem] = []
    order_no = _order_no()
    for item in body.items:
        project = db.get(Project, item.project_id)
        if not project or not project.active:
            raise HTTPException(status_code=400, detail=f"项目不可用: {item.project_id}")
        items.append(
            OrderItem(
                project_id=project.id,
                project_name=project.name,
                unit_price=project.price,
                quantity=item.quantity,
            )
        )

    deal_price = Decimal(body.deal_price).quantize(Decimal("0.01"))
    order = Order(
        order_no=order_no,
        customer_id=customer.id,
        total_amount=deal_price,
        status="PAID",
        remark=body.remark or "",
        created_by=admin.id,
        items=items,
    )
    db.add(order)
    db.flush()

    for item_id, qty in needs.items():
        if qty <= 0:
            continue
        stock_out(
            db,
            stocks[item_id],
            qty,
            remark=f"订单 {order_no}",
            admin_id=admin.id,
        )

    db.commit()
    order = (
        db.query(Order)
        .options(joinedload(Order.customer), joinedload(Order.items))
        .filter(Order.id == order.id)
        .first()
    )
    return serialize_order(order)


@router.patch("/orders/{order_id}/status", response_model=OrderOut)
def update_order_status(
    order_id: int,
    body: OrderStatusUpdate,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    status_value = body.status.upper()
    if status_value not in VALID_STATUSES:
        raise HTTPException(status_code=400, detail=f"状态无效，可选: {', '.join(sorted(VALID_STATUSES))}")

    order = (
        db.query(Order)
        .options(joinedload(Order.customer), joinedload(Order.items))
        .filter(Order.id == order_id)
        .first()
    )
    if not order:
        raise HTTPException(status_code=404, detail="订单不存在")
    order.status = status_value
    db.commit()
    db.refresh(order)
    return serialize_order(order)
