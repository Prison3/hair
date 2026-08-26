from __future__ import annotations

from collections import defaultdict
from datetime import datetime, timedelta
from decimal import Decimal
from typing import Dict, List, Optional
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session, joinedload, selectinload

from ..auth import get_current_admin, role_label
from ..database import get_db
from ..models import Admin, Customer, Order, OrderItem, Project, StockItem, StockMovement
from ..schemas import (
    STOCK_OUT,
    OrderCreate,
    OrderOut,
    OrderStatusUpdate,
    OrderStockNeedOut,
    OrderStockPreviewIn,
    OrderStockPreviewOut,
    OrderUpdate,
)
from ..stock import stock_out

router = APIRouter(prefix="/api", tags=["orders"])

VALID_STATUSES = {"PENDING", "PAID", "DONE", "CANCELLED"}
ACTIVE_STATUSES = {"PENDING", "PAID", "DONE"}
CANCEL_WINDOW = timedelta(hours=24)
TZ = ZoneInfo("Asia/Shanghai")


def _order_no() -> str:
    """订单号时间与 App 展示的下单时间一致（北京时间）。"""
    return datetime.now(TZ).strftime("HC%Y%m%d%H%M%S%f")[:-3]


def _order_load_options():
    return (
        joinedload(Order.customer),
        joinedload(Order.items),
        joinedload(Order.creator),
    )


def _out_remark(order_no: str) -> str:
    """出库原因：订单号。"""
    return f"订单号 {order_no}"


def _order_out_remarks(order_no: str) -> List[str]:
    # 兼容历史备注格式
    return [
        f"订单号 {order_no}",
        f"出库 · 订单 {order_no}",
        f"订单 {order_no}",
    ]


def serialize_order(order: Order) -> OrderOut:
    creator = order.creator
    return OrderOut(
        id=order.id,
        order_no=order.order_no,
        customer_id=order.customer_id,
        customer_name=order.customer.name if order.customer else None,
        customer_phone=order.customer.phone if order.customer else None,
        total_amount=order.total_amount,
        status=order.status,
        remark=order.remark or "",
        created_by=order.created_by,
        created_by_username=creator.username if creator else None,
        created_by_role_label=role_label(creator.role) if creator else None,
        created_at=order.created_at,
        items=order.items,
    )


def _collect_stock_needs(db: Session, body_items: List) -> Dict[int, int]:
    """汇总出库：项目关联产品用量×数量，以及直接选中的产品数量。"""
    needs: Dict[int, int] = defaultdict(int)
    for item in body_items:
        qty = int(getattr(item, "quantity", 0) or 0)
        project_id = getattr(item, "project_id", None)
        product_id = getattr(item, "item_id", None)
        if project_id is not None:
            project = (
                db.query(Project)
                .options(selectinload(Project.medicines))
                .filter(Project.id == project_id)
                .first()
            )
            if not project or not project.active:
                raise HTTPException(status_code=400, detail=f"项目不可用: {project_id}")
            for med in project.medicines or []:
                needs[med.item_id] += int(med.quantity) * qty
        elif product_id is not None:
            needs[int(product_id)] += qty
        else:
            raise HTTPException(status_code=400, detail="明细缺少项目或产品")
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


def _preview_stock(db: Session, body_items: List) -> OrderStockPreviewOut:
    needs = _collect_stock_needs(db, body_items)
    rows: List[OrderStockNeedOut] = []
    enough = True
    for item_id, need in sorted(needs.items(), key=lambda x: x[0]):
        if need <= 0:
            continue
        stock = db.get(StockItem, item_id)
        if not stock:
            raise HTTPException(status_code=400, detail=f"项目关联产品不存在: {item_id}")
        on_hand = int(stock.stock_qty or 0)
        ok = on_hand >= need
        if not ok:
            enough = False
        rows.append(
            OrderStockNeedOut(
                item_id=stock.id,
                item_name=stock.name,
                unit=stock.unit or "个",
                need=need,
                on_hand=on_hand,
                enough=ok,
            )
        )
    return OrderStockPreviewOut(items=rows, enough=enough)


def _order_out_movements(db: Session, order_no: str) -> List[StockMovement]:
    return (
        db.query(StockMovement)
        .filter(StockMovement.kind == STOCK_OUT)
        .filter(StockMovement.remark.in_(_order_out_remarks(order_no)))
        .all()
    )


def _restore_order_stock(db: Session, order: Order) -> None:
    """撤销/删除时回滚该订单对应出库：库存加回并删除 OUT 流水。"""
    for movement in _order_out_movements(db, order.order_no):
        item = db.get(StockItem, movement.item_id)
        qty = int(movement.quantity or 0)
        if item and qty > 0:
            item.stock_qty = int(item.stock_qty or 0) + qty
        db.delete(movement)


def _deduct_order_stock(
    db: Session,
    order_no: str,
    needs: Dict[int, int],
    stocks: Dict[int, StockItem],
    admin_id: int,
) -> None:
    for item_id, qty in needs.items():
        if qty <= 0:
            continue
        stock_out(
            db,
            stocks[item_id],
            qty,
            remark=_out_remark(order_no),
            admin_id=admin_id,
        )


def _build_order_items(db: Session, body_items: List) -> List[OrderItem]:
    items: List[OrderItem] = []
    for item in body_items:
        if item.project_id is not None:
            project = db.get(Project, item.project_id)
            if not project or not project.active:
                raise HTTPException(status_code=400, detail=f"项目不可用: {item.project_id}")
            items.append(
                OrderItem(
                    project_id=project.id,
                    item_id=None,
                    project_name=project.name,
                    unit_price=project.price,
                    quantity=item.quantity,
                )
            )
        else:
            stock = db.get(StockItem, item.item_id)
            if not stock:
                raise HTTPException(status_code=400, detail=f"产品不存在: {item.item_id}")
            items.append(
                OrderItem(
                    project_id=None,
                    item_id=stock.id,
                    project_name=stock.name,
                    unit_price=stock.sale_price or stock.cost_price or Decimal("0"),
                    quantity=item.quantity,
                )
            )
    return items


def _get_order(db: Session, order_id: int) -> Order:
    order = (
        db.query(Order)
        .options(*_order_load_options())
        .filter(Order.id == order_id)
        .first()
    )
    if not order:
        raise HTTPException(status_code=404, detail="订单不存在")
    return order


def _within_cancel_window(order: Order) -> bool:
    created = order.created_at
    if created is None:
        return False
    return created >= datetime.utcnow() - CANCEL_WINDOW


def _ensure_cancellable(order: Order) -> None:
    if order.status == "CANCELLED":
        return
    if order.status not in ACTIVE_STATUSES:
        raise HTTPException(status_code=400, detail="当前状态不可撤销")
    if not _within_cancel_window(order):
        raise HTTPException(status_code=400, detail="只能撤销 24 小时内的订单")


@router.post("/orders/preview-stock", response_model=OrderStockPreviewOut)
def preview_order_stock(
    body: OrderStockPreviewIn,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    return _preview_stock(db, body.items)


@router.get("/orders", response_model=List[OrderOut])
def list_orders(
    customer_id: Optional[int] = None,
    status_filter: Optional[str] = Query(None, alias="status"),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    query = (
        db.query(Order)
        .options(*_order_load_options())
        .order_by(Order.created_at.desc(), Order.id.desc())
    )
    if customer_id is not None:
        query = query.filter(Order.customer_id == customer_id)
    if status_filter:
        query = query.filter(Order.status == status_filter.upper())
    return [serialize_order(o) for o in query.all()]


@router.get("/orders/by-no/{order_no}", response_model=OrderOut)
def get_order_by_no(
    order_no: str,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    order = (
        db.query(Order)
        .options(*_order_load_options())
        .filter(Order.order_no == order_no.strip())
        .first()
    )
    if not order:
        raise HTTPException(status_code=404, detail="订单不存在")
    return serialize_order(order)


@router.get("/orders/{order_id}", response_model=OrderOut)
def get_order(
    order_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    return serialize_order(_get_order(db, order_id))


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
        .options(*_order_load_options())
        .filter(Order.customer_id == customer_id)
        .order_by(Order.created_at.desc(), Order.id.desc())
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
    items = _build_order_items(db, body.items)
    order_no = _order_no()
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
    _deduct_order_stock(db, order_no, needs, stocks, admin.id)
    db.commit()
    return serialize_order(_get_order(db, order.id))


@router.put("/orders/{order_id}", response_model=OrderOut)
def update_order(
    order_id: int,
    body: OrderUpdate,
    db: Session = Depends(get_db),
    admin: Admin = Depends(get_current_admin),
):
    order = _get_order(db, order_id)
    if order.status == "CANCELLED":
        raise HTTPException(status_code=400, detail="已撤销订单不可修改")

    needs = _collect_stock_needs(db, body.items)
    # 先回滚旧出库，再按新明细校验并扣减
    _restore_order_stock(db, order)
    db.flush()
    stocks = _ensure_stock(db, needs)

    order.items.clear()
    db.flush()
    order.items.extend(_build_order_items(db, body.items))
    order.total_amount = Decimal(body.deal_price).quantize(Decimal("0.01"))
    order.remark = body.remark or ""
    db.flush()
    _deduct_order_stock(db, order.order_no, needs, stocks, admin.id)
    db.commit()
    return serialize_order(_get_order(db, order.id))


@router.post("/orders/{order_id}/cancel", response_model=OrderOut)
def cancel_order(
    order_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    order = _get_order(db, order_id)
    if order.status == "CANCELLED":
        return serialize_order(order)
    _ensure_cancellable(order)
    _restore_order_stock(db, order)
    order.status = "CANCELLED"
    db.commit()
    return serialize_order(_get_order(db, order.id))


@router.delete("/orders/{order_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_order(
    order_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    order = _get_order(db, order_id)
    _restore_order_stock(db, order)
    db.delete(order)
    db.commit()


@router.patch("/orders/{order_id}/status", response_model=OrderOut)
def update_order_status(
    order_id: int,
    body: OrderStatusUpdate,
    db: Session = Depends(get_db),
    admin: Admin = Depends(get_current_admin),
):
    status_value = body.status.upper()
    if status_value not in VALID_STATUSES:
        raise HTTPException(status_code=400, detail=f"状态无效，可选: {', '.join(sorted(VALID_STATUSES))}")

    order = _get_order(db, order_id)
    old = order.status
    if old == status_value:
        return serialize_order(order)

    if status_value == "CANCELLED":
        _ensure_cancellable(order)
        _restore_order_stock(db, order)
        order.status = "CANCELLED"
    elif old == "CANCELLED":
        # 从已撤销恢复：重新扣库存
        needs = _collect_stock_needs(db, order.items)
        stocks = _ensure_stock(db, needs)
        _deduct_order_stock(db, order.order_no, needs, stocks, admin.id)
        order.status = status_value
    else:
        order.status = status_value

    db.commit()
    return serialize_order(_get_order(db, order.id))
