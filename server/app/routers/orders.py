from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session, joinedload

from ..auth import get_current_admin
from ..database import get_db
from ..models import Admin, Customer, Order, OrderItem, Project
from ..schemas import OrderCreate, OrderOut, OrderStatusUpdate

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

    items = []  # type: List[OrderItem]
    total = Decimal("0.00")
    for item in body.items:
        project = db.get(Project, item.project_id)
        if not project or not project.active:
            raise HTTPException(status_code=400, detail=f"项目不可用: {item.project_id}")
        line_total = Decimal(project.price) * item.quantity
        total += line_total
        items.append(
            OrderItem(
                project_id=project.id,
                project_name=project.name,
                unit_price=project.price,
                quantity=item.quantity,
            )
        )

    order = Order(
        order_no=_order_no(),
        customer_id=customer.id,
        total_amount=total,
        status="PAID",
        remark=body.remark or "",
        created_by=admin.id,
        items=items,
    )
    db.add(order)
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
