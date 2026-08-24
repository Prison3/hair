from __future__ import annotations

from typing import Dict, List, Optional, Tuple

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import func
from sqlalchemy.orm import Session

from ..auth import get_current_admin
from ..database import get_db
from ..models import Admin, Customer, CustomerVisit
from ..schemas import (
    CustomerCreate,
    CustomerOut,
    CustomerUpdate,
    CustomerVisitIn,
    CustomerVisitOut,
)

router = APIRouter(prefix="/api/customers", tags=["customers"])


def _visit_stats(db: Session, customer_ids: List[int]) -> Dict[int, Tuple[object, int]]:
    if not customer_ids:
        return {}
    rows = (
        db.query(
            CustomerVisit.customer_id,
            func.max(CustomerVisit.visited_at),
            func.count(CustomerVisit.id),
        )
        .filter(CustomerVisit.customer_id.in_(customer_ids))
        .group_by(CustomerVisit.customer_id)
        .all()
    )
    return {cid: (last, count) for cid, last, count in rows}


def _customer_out(customer: Customer, last_visited_at=None, visit_count: int = 0) -> CustomerOut:
    return CustomerOut(
        id=customer.id,
        name=customer.name,
        phone=customer.phone,
        gender=customer.gender,
        birthday=customer.birthday,
        notes=customer.notes,
        created_at=customer.created_at,
        last_visited_at=last_visited_at,
        visit_count=visit_count,
    )


def _get_customer(db: Session, customer_id: int) -> Customer:
    customer = db.get(Customer, customer_id)
    if not customer:
        raise HTTPException(status_code=404, detail="客户不存在")
    return customer


@router.get("", response_model=List[CustomerOut])
def list_customers(
    q: Optional[str] = Query(None, description="按姓名或手机号搜索"),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    query = db.query(Customer).order_by(Customer.id.desc())
    if q:
        like = f"%{q.strip()}%"
        query = query.filter((Customer.name.like(like)) | (Customer.phone.like(like)))
    customers = query.all()
    stats = _visit_stats(db, [c.id for c in customers])
    return [
        _customer_out(c, *stats.get(c.id, (None, 0)))
        for c in customers
    ]


@router.post("", response_model=CustomerOut, status_code=status.HTTP_201_CREATED)
def create_customer(
    body: CustomerCreate,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    customer = Customer(**body.model_dump())
    db.add(customer)
    db.commit()
    db.refresh(customer)
    return _customer_out(customer)


@router.get("/{customer_id}/visits", response_model=List[CustomerVisitOut])
def list_visits(
    customer_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    _get_customer(db, customer_id)
    return (
        db.query(CustomerVisit)
        .filter(CustomerVisit.customer_id == customer_id)
        .order_by(CustomerVisit.visited_at.desc(), CustomerVisit.id.desc())
        .all()
    )


@router.post("/{customer_id}/visits", response_model=CustomerVisitOut, status_code=status.HTTP_201_CREATED)
def create_visit(
    customer_id: int,
    body: CustomerVisitIn,
    db: Session = Depends(get_db),
    admin: Admin = Depends(get_current_admin),
):
    _get_customer(db, customer_id)
    visit = CustomerVisit(
        customer_id=customer_id,
        visited_at=body.visited_at,
        content=body.content,
        created_by=admin.id,
    )
    db.add(visit)
    db.commit()
    db.refresh(visit)
    return visit


@router.put("/{customer_id}/visits/{visit_id}", response_model=CustomerVisitOut)
def update_visit(
    customer_id: int,
    visit_id: int,
    body: CustomerVisitIn,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    visit = db.get(CustomerVisit, visit_id)
    if not visit or visit.customer_id != customer_id:
        raise HTTPException(status_code=404, detail="回访记录不存在")
    visit.visited_at = body.visited_at
    visit.content = body.content
    db.commit()
    db.refresh(visit)
    return visit


@router.delete("/{customer_id}/visits/{visit_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_visit(
    customer_id: int,
    visit_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    visit = db.get(CustomerVisit, visit_id)
    if not visit or visit.customer_id != customer_id:
        raise HTTPException(status_code=404, detail="回访记录不存在")
    db.delete(visit)
    db.commit()
    return None


@router.get("/{customer_id}", response_model=CustomerOut)
def get_customer(
    customer_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    customer = _get_customer(db, customer_id)
    stats = _visit_stats(db, [customer.id])
    last, count = stats.get(customer.id, (None, 0))
    return _customer_out(customer, last, count)


@router.put("/{customer_id}", response_model=CustomerOut)
def update_customer(
    customer_id: int,
    body: CustomerUpdate,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    customer = _get_customer(db, customer_id)
    for key, value in body.model_dump().items():
        setattr(customer, key, value)
    db.commit()
    db.refresh(customer)
    stats = _visit_stats(db, [customer.id])
    last, count = stats.get(customer.id, (None, 0))
    return _customer_out(customer, last, count)


@router.delete("/{customer_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_customer(
    customer_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    customer = _get_customer(db, customer_id)
    if customer.orders:
        raise HTTPException(status_code=400, detail="该客户已有订单，无法删除")
    db.query(CustomerVisit).filter(CustomerVisit.customer_id == customer_id).delete()
    db.delete(customer)
    db.commit()
    return None
