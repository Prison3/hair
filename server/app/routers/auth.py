from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy.orm import Session

from ..auth import authenticate_admin, create_access_token, get_current_admin, hash_password, verify_password
from ..database import get_db
from ..models import Admin
from ..schemas import AccountUpdateIn, AccountUpdateOut, LoginIn, MeOut, TokenOut

router = APIRouter(prefix="/api/auth", tags=["auth"])


@router.post("/login", response_model=TokenOut)
def login_json(body: LoginIn, db: Session = Depends(get_db)):
    admin = authenticate_admin(db, body.username, body.password)
    if not admin:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="用户名或密码错误")
    return TokenOut(access_token=create_access_token(admin.username))


@router.post("/token", response_model=TokenOut)
def login_form(
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: Session = Depends(get_db),
):
    admin = authenticate_admin(db, form_data.username, form_data.password)
    if not admin:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="用户名或密码错误")
    return TokenOut(access_token=create_access_token(admin.username))


@router.get("/me", response_model=MeOut)
def me(admin: Admin = Depends(get_current_admin)):
    return MeOut(id=admin.id, username=admin.username)


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
    return AccountUpdateOut(
        id=admin.id,
        username=admin.username,
        access_token=create_access_token(admin.username),
    )
