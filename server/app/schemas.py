from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"


class LoginIn(BaseModel):
    username: str
    password: str


class MeOut(BaseModel):
    id: int
    username: str


class AccountUpdateIn(BaseModel):
    current_password: str = Field(min_length=1, max_length=64)
    username: Optional[str] = Field(default=None, min_length=2, max_length=64)
    new_password: Optional[str] = Field(default=None, min_length=6, max_length=64)


class AccountUpdateOut(BaseModel):
    id: int
    username: str
    access_token: str
    token_type: str = "bearer"


class CustomerBase(BaseModel):
    name: str = Field(min_length=1, max_length=64)
    phone: str = Field(min_length=1, max_length=32)
    gender: str = ""
    birthday: Optional[date] = None
    notes: str = ""


class CustomerCreate(CustomerBase):
    pass


class CustomerUpdate(CustomerBase):
    pass


class CustomerOut(CustomerBase):
    model_config = ConfigDict(from_attributes=True)

    id: int
    created_at: datetime


class ProjectBase(BaseModel):
    name: str = Field(min_length=1, max_length=128)
    description: str = ""
    price: Decimal = Field(ge=0)
    graft_count: int = Field(default=0, ge=0)
    active: bool = True


class ProjectCreate(ProjectBase):
    pass


class ProjectUpdate(ProjectBase):
    pass


class ProjectOut(ProjectBase):
    model_config = ConfigDict(from_attributes=True)

    id: int
    created_at: datetime


class OrderItemIn(BaseModel):
    project_id: int
    quantity: int = Field(default=1, ge=1)


class OrderCreate(BaseModel):
    customer_id: int
    items: List[OrderItemIn] = Field(min_length=1)
    remark: str = ""


class OrderItemOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    project_id: int
    project_name: str
    unit_price: Decimal
    quantity: int


class OrderOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    order_no: str
    customer_id: int
    customer_name: Optional[str] = None
    customer_phone: Optional[str] = None
    total_amount: Decimal
    status: str
    remark: str
    created_at: datetime
    items: List[OrderItemOut] = []


class OrderStatusUpdate(BaseModel):
    status: str


class AppReleaseOut(BaseModel):
    version_name: str
    version_code: int
    filename: str
    size_bytes: int
    updated_at: str
    download_url: str
