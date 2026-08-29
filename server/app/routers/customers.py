from __future__ import annotations

from datetime import datetime
from typing import Dict, List, Optional, Tuple

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, UploadFile, status
from fastapi.responses import FileResponse
from sqlalchemy import func
from sqlalchemy.orm import Session, joinedload

from ..auth import get_current_admin, is_admin, role_label
from ..customer_photos import (
    delete_customer_photo_files,
    delete_photo_file,
    photo_file_path,
    save_upload,
    validate_kind,
)
from ..database import get_db
from ..models import Admin, Customer, CustomerPhoto, CustomerVisit
from ..schemas import (
    CustomerCreate,
    CustomerOut,
    CustomerPhotoOut,
    CustomerUpdate,
    CustomerVisitIn,
    CustomerVisitOut,
    StaffOptionOut,
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
    assignee = customer.assignee
    return CustomerOut(
        id=customer.id,
        name=customer.name,
        phone=customer.phone,
        gender=customer.gender,
        birthday=customer.birthday,
        wechat=customer.wechat or "",
        address=customer.address or "",
        intention=customer.intention or "",
        notes=customer.notes,
        assigned_to=customer.assigned_to,
        created_at=customer.created_at,
        last_visited_at=last_visited_at,
        visit_count=visit_count,
        assigned_to_username=assignee.username if assignee else "",
        assigned_to_role_label=role_label(assignee.role) if assignee else "",
    )


def _get_customer(db: Session, customer_id: int) -> Customer:
    customer = (
        db.query(Customer)
        .options(joinedload(Customer.assignee))
        .filter(Customer.id == customer_id)
        .first()
    )
    if not customer:
        raise HTTPException(status_code=404, detail="客户不存在")
    return customer


def _validate_assigned_to(db: Session, assigned_to: Optional[int]) -> None:
    if assigned_to is None:
        return
    staff = db.get(Admin, assigned_to)
    if not staff:
        raise HTTPException(status_code=400, detail="归属业务员不存在")


def _photo_out(photo: CustomerPhoto) -> CustomerPhotoOut:
    return CustomerPhotoOut(
        id=photo.id,
        customer_id=photo.customer_id,
        kind=photo.kind,
        url=f"/api/customers/{photo.customer_id}/photos/{photo.id}/content",
        original_name=photo.original_name or "",
        taken_at=photo.taken_at,
        created_at=photo.created_at,
    )


@router.get("/staff-options", response_model=List[StaffOptionOut])
def list_staff_options(
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    rows = db.query(Admin).order_by(Admin.id.asc()).all()
    return [
        StaffOptionOut(
            id=row.id,
            username=row.username,
            role_label=role_label(row.role),
        )
        for row in rows
    ]


@router.get("", response_model=List[CustomerOut])
def list_customers(
    q: Optional[str] = Query(None, description="按姓名或手机号搜索"),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    query = (
        db.query(Customer)
        .options(joinedload(Customer.assignee))
        .order_by(Customer.id.desc())
    )
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
    admin: Admin = Depends(get_current_admin),
):
    data = body.model_dump()
    if not is_admin(admin) or data.get("assigned_to") is None:
        data["assigned_to"] = admin.id
    else:
        _validate_assigned_to(db, data["assigned_to"])
    customer = Customer(**data)
    db.add(customer)
    db.commit()
    db.refresh(customer)
    customer = _get_customer(db, customer.id)
    return _customer_out(customer)


@router.get("/{customer_id}/photos", response_model=List[CustomerPhotoOut])
def list_photos(
    customer_id: int,
    kind: Optional[str] = Query(None, description="BEFORE=前照片, AFTER=后照片"),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    _get_customer(db, customer_id)
    query = (
        db.query(CustomerPhoto)
        .filter(CustomerPhoto.customer_id == customer_id)
        .order_by(CustomerPhoto.created_at.asc(), CustomerPhoto.id.asc())
    )
    if kind:
        query = query.filter(CustomerPhoto.kind == validate_kind(kind))
    return [_photo_out(row) for row in query.all()]


@router.post("/{customer_id}/photos", response_model=CustomerPhotoOut, status_code=status.HTTP_201_CREATED)
async def upload_photo(
    customer_id: int,
    kind: str = Form(...),
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    _get_customer(db, customer_id)
    kind_value = validate_kind(kind)
    stored_name, original_name, mime_type = await save_upload(customer_id, kind_value, file)
    now = datetime.utcnow()
    photo = CustomerPhoto(
        customer_id=customer_id,
        kind=kind_value,
        stored_name=stored_name,
        original_name=original_name,
        mime_type=mime_type,
        taken_at=now,
        created_at=now,
    )
    db.add(photo)
    db.commit()
    db.refresh(photo)
    return _photo_out(photo)


@router.get("/{customer_id}/photos/{photo_id}/content")
def get_photo_content(
    customer_id: int,
    photo_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    photo = db.get(CustomerPhoto, photo_id)
    if not photo or photo.customer_id != customer_id:
        raise HTTPException(status_code=404, detail="照片不存在")
    path = photo_file_path(customer_id, photo.stored_name)
    if not path.is_file():
        raise HTTPException(status_code=404, detail="照片文件不存在")
    return FileResponse(path, media_type=photo.mime_type or "image/jpeg")


@router.delete("/{customer_id}/photos/{photo_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_photo(
    customer_id: int,
    photo_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    photo = db.get(CustomerPhoto, photo_id)
    if not photo or photo.customer_id != customer_id:
        raise HTTPException(status_code=404, detail="照片不存在")
    delete_photo_file(customer_id, photo.stored_name)
    db.delete(photo)
    db.commit()
    return None


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
    admin: Admin = Depends(get_current_admin),
):
    customer = _get_customer(db, customer_id)
    data = body.model_dump()
    if not is_admin(admin):
        data.pop("assigned_to", None)
    else:
        _validate_assigned_to(db, data.get("assigned_to"))
    for key, value in data.items():
        setattr(customer, key, value)
    db.commit()
    customer = _get_customer(db, customer_id)
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
    delete_customer_photo_files(customer_id)
    db.delete(customer)
    db.commit()
    return None
