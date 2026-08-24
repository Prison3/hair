from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


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
    last_visited_at: Optional[datetime] = None
    visit_count: int = 0


class CustomerVisitIn(BaseModel):
    visited_at: datetime
    content: str = ""

    @field_validator("visited_at")
    @classmethod
    def to_minute(cls, value: datetime) -> datetime:
        return value.replace(second=0, microsecond=0)

    @field_validator("content")
    @classmethod
    def trim_content(cls, value: str) -> str:
        return (value or "").strip()


class CustomerVisitOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    customer_id: int
    visited_at: datetime
    content: str = ""
    created_at: datetime


PROJECT_UNITS = ("支", "个", "盒", "次")


def normalize_unit(value: str) -> str:
    unit = (value or "个").strip() or "个"
    if unit == "单位":
        unit = "次"
    if unit not in PROJECT_UNITS:
        raise ValueError("单位须为：支 / 个 / 盒 / 次")
    return unit


class ProjectBase(BaseModel):
    name: str = Field(min_length=1, max_length=128)
    description: str = ""
    price: Decimal = Field(ge=0)
    graft_count: int = Field(default=0, ge=0)
    unit: str = Field(default="个", max_length=16)
    active: bool = True

    @field_validator("unit")
    @classmethod
    def check_unit(cls, value: str) -> str:
        return normalize_unit(value)


class ProjectCreate(ProjectBase):
    pass


class ProjectUpdate(ProjectBase):
    pass


class ProjectOut(ProjectBase):
    model_config = ConfigDict(from_attributes=True)

    id: int
    created_at: datetime
    stock_qty: int = 0
    cost_price: Decimal = Decimal("0")


STOCK_IN = "IN"
STOCK_OUT = "OUT"


class StockItemIn(BaseModel):
    name: str = Field(min_length=1, max_length=128)

    @field_validator("name")
    @classmethod
    def trim_name(cls, value: str) -> str:
        name = (value or "").strip()
        if not name:
            raise ValueError("请填写产品名")
        return name


class StockItemOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    spec: str = ""
    unit: str = "个"
    stock_qty: int = 0
    cost_price: Decimal = Decimal("0")
    created_at: datetime


class StockInBody(BaseModel):
    item_id: Optional[int] = None
    name: str = ""
    spec: str = Field(default="", max_length=64)
    quantity: int = Field(ge=1)
    unit: str = Field(default="个", max_length=16)
    unit_cost: Decimal = Field(ge=0)
    moved_at: Optional[date] = None

    @field_validator("name")
    @classmethod
    def trim_name(cls, value: str) -> str:
        return (value or "").strip()

    @field_validator("spec")
    @classmethod
    def trim_spec(cls, value: str) -> str:
        return (value or "").strip()

    @field_validator("unit")
    @classmethod
    def check_unit(cls, value: str) -> str:
        return normalize_unit(value)

    @model_validator(mode="after")
    def need_product(self):
        if not self.item_id and not self.name:
            raise ValueError("请选择产品")
        return self


class StockOutBody(BaseModel):
    item_id: int
    quantity: int = Field(ge=1)
    remark: str = ""

    @field_validator("remark")
    @classmethod
    def trim_remark(cls, value: str) -> str:
        return (value or "").strip()


class StockMovementOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    item_id: int
    item_name: str
    kind: str
    quantity: int
    unit_cost: Decimal
    remark: str = ""
    inbound_no: str = ""
    moved_at: Optional[date] = None
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
