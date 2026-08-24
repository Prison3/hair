from __future__ import annotations

from datetime import datetime, timedelta
from typing import Optional

from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError, jwt
from passlib.context import CryptContext
from sqlalchemy.orm import Session

from .database import get_db
from .models import Admin

ROLE_ADMIN = "admin"
ROLE_MANAGER = "manager"


def role_label(role: str) -> str:
    return "管理员" if (role or ROLE_ADMIN) == ROLE_ADMIN else "店长"


def is_admin(admin: Admin) -> bool:
    return (admin.role or ROLE_ADMIN) == ROLE_ADMIN

SECRET_KEY = "hair-clinic-dev-secret-change-me"
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_HOURS = 72

pwd_context = CryptContext(schemes=["pbkdf2_sha256", "bcrypt"], deprecated="auto")
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/auth/login")


def hash_password(password: str) -> str:
    return pwd_context.hash(password)


def verify_password(plain: str, hashed: str) -> bool:
    return pwd_context.verify(plain, hashed)


def create_access_token(subject: str) -> str:
    expire = datetime.utcnow() + timedelta(hours=ACCESS_TOKEN_EXPIRE_HOURS)
    return jwt.encode(
        {"sub": subject, "exp": expire},
        SECRET_KEY,
        algorithm=ALGORITHM,
    )


def token_out(admin: Admin):
    from .schemas import TokenOut

    role = admin.role or ROLE_ADMIN
    return TokenOut(
        access_token=create_access_token(admin.username),
        username=admin.username,
        role=role,
        role_label=role_label(role),
    )


def me_out(admin: Admin):
    from .schemas import MeOut

    role = admin.role or ROLE_ADMIN
    return MeOut(
        id=admin.id,
        username=admin.username,
        role=role,
        role_label=role_label(role),
    )


def authenticate_admin(db: Session, username: str, password: str) -> Optional[Admin]:
    admin = db.query(Admin).filter(Admin.username == username).first()
    if not admin or not verify_password(password, admin.password_hash):
        return None
    return admin


def get_current_admin(
    token: str = Depends(oauth2_scheme),
    db: Session = Depends(get_db),
) -> Admin:
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="未登录或凭证无效",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username = payload.get("sub")
        if not username:
            raise credentials_exception
    except JWTError as exc:
        raise credentials_exception from exc

    admin = db.query(Admin).filter(Admin.username == username).first()
    if not admin:
        raise credentials_exception
    return admin


def require_admin(admin: Admin = Depends(get_current_admin)) -> Admin:
    if not is_admin(admin):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="仅管理员可操作")
    return admin
