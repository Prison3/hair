from __future__ import annotations

from typing import List

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session

from ..auth import (
    ROLE_ADMIN,
    authenticate_admin,
    get_current_admin,
    hash_password,
    is_admin,
    me_out,
    require_admin,
    role_label,
    token_out,
    verify_password,
    create_access_token,
)
from ..database import get_db
from ..models import Admin
from ..schemas import AccountUpdateIn, AccountUpdateOut, LoginIn, MeOut, StaffCreate, StaffOut, StaffUpdate, TokenOut

router = APIRouter(prefix="/api/auth", tags=["auth"])


def _staff_out(row: Admin) -> StaffOut:
    role = row.role or ROLE_ADMIN
    return StaffOut(
        id=row.id,
        username=row.username,
        role=role,
        role_label=role_label(role),
        created_at=row.created_at,
    )


@router.post("/login", response_model=TokenOut)
def login_json(body: LoginIn, db: Session = Depends(get_db)):
    admin = authenticate_admin(db, body.username, body.password)
    if not admin:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="用户名或密码错误")
    return token_out(admin)


@router.post("/token", response_model=TokenOut)
def login_form(
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: Session = Depends(get_db),
):
    admin = authenticate_admin(db, form_data.username, form_data.password)
    if not admin:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="用户名或密码错误")
    return token_out(admin)


@router.get("/me", response_model=MeOut)
def me(admin: Admin = Depends(get_current_admin)):
    return me_out(admin)


@router.patch("/me", response_model=AccountUpdateOut)
def update_account(
    body: AccountUpdateIn,
    db: Session = Depends(get_db),
    admin: Admin = Depends(get_current_admin),
):
    if not verify_password(body.current_password, admin.password_hash):
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="当前密码不正确")

    username = (body.username or "").strip()
    new_password = body.new_password
    changed = False

    if username and username != admin.username:
        taken = (
            db.query(Admin)
            .filter(Admin.username == username, Admin.id != admin.id)
            .first()
        )
        if taken:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="用户名已被占用")
        admin.username = username
        changed = True

    if new_password:
        admin.password_hash = hash_password(new_password)
        changed = True

    if not changed:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="请修改用户名或设置新密码")

    db.commit()
    db.refresh(admin)
    role = admin.role or ROLE_ADMIN
    return AccountUpdateOut(
        id=admin.id,
        username=admin.username,
        access_token=create_access_token(admin.username),
        role=role,
        role_label=role_label(role),
    )


@router.get("/staff", response_model=List[StaffOut])
def list_staff(
    db: Session = Depends(get_db),
    _: Admin = Depends(require_admin),
):
    rows = db.query(Admin).order_by(Admin.id.asc()).all()
    return [_staff_out(row) for row in rows]


@router.post("/staff", response_model=StaffOut, status_code=status.HTTP_201_CREATED)
def create_staff(
    body: StaffCreate,
    db: Session = Depends(get_db),
    _: Admin = Depends(require_admin),
):
    if db.query(Admin).filter(Admin.username == body.username).first():
        raise HTTPException(status_code=400, detail="用户名已被占用")
    row = Admin(
        username=body.username,
        password_hash=hash_password(body.password),
        role=body.role,
    )
    db.add(row)
    db.commit()
    db.refresh(row)
    return _staff_out(row)


@router.patch("/staff/{staff_id}", response_model=StaffOut)
def update_staff(
    staff_id: int,
    body: StaffUpdate,
    db: Session = Depends(get_db),
    current: Admin = Depends(require_admin),
):
    row = db.get(Admin, staff_id)
    if not row:
        raise HTTPException(status_code=404, detail="用户不存在")
    username = body.username
    if username and username != row.username:
        taken = db.query(Admin).filter(Admin.username == username, Admin.id != staff_id).first()
        if taken:
            raise HTTPException(status_code=400, detail="用户名已被占用")
        row.username = username
    if body.password:
        row.password_hash = hash_password(body.password)
    if body.role and body.role != (row.role or ROLE_ADMIN):
        if row.id == current.id:
            raise HTTPException(status_code=400, detail="不能修改自己的角色")
        admins = db.query(Admin).filter(Admin.role == ROLE_ADMIN).count()
        if is_admin(row) and body.role != ROLE_ADMIN and admins <= 1:
            raise HTTPException(status_code=400, detail="至少保留一名管理员")
        row.role = body.role
    db.commit()
    db.refresh(row)
    return _staff_out(row)


@router.delete("/staff/{staff_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_staff(
    staff_id: int,
    db: Session = Depends(get_db),
    current: Admin = Depends(require_admin),
):
    row = db.get(Admin, staff_id)
    if not row:
        raise HTTPException(status_code=404, detail="用户不存在")
    if row.id == current.id:
        raise HTTPException(status_code=400, detail="不能删除当前登录账号")
    if is_admin(row):
        admins = db.query(Admin).filter(Admin.role == ROLE_ADMIN).count()
        if admins <= 1:
            raise HTTPException(status_code=400, detail="至少保留一名管理员")
    db.delete(row)
    db.commit()
    return None
