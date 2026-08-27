from __future__ import annotations

import re
from datetime import date, datetime
from decimal import Decimal
from typing import List, Optional

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"
    username: str = ""
    role: str = "admin"
    role_label: str = "管理员"


class LoginIn(BaseModel):
    username: str
    password: str


class MeOut(BaseModel):
    id: int
    username: str
    role: str = "admin"
    role_label: str = "管理员"


class AccountUpdateIn(BaseModel):
    current_password: str = Field(min_length=1, max_length=64)
    username: Optional[str] = Field(default=None, min_length=2, max_length=64)
    new_password: Optional[str] = Field(default=None, min_length=6, max_length=64)


class AccountUpdateOut(BaseModel):
    id: int
    username: str
    access_token: str
    token_type: str = "bearer"
    role: str = "admin"
    role_label: str = "管理员"


class StaffCreate(BaseModel):
    username: str = Field(min_length=2, max_length=64)
    password: str = Field(min_length=6, max_length=64)
    role: str = "manager"

    @field_validator("username")
    @classmethod
    def trim_username(cls, value: str) -> str:
        name = (value or "").strip()
        if not name:
            raise ValueError("请填写用户名")
        return name

    @field_validator("role")
    @classmethod
    def check_role(cls, value: str) -> str:
        role = (value or "manager").strip()
        if role not in ("admin", "manager"):
            raise ValueError("角色须为管理员或店长")
        return role


class StaffUpdate(BaseModel):
    username: Optional[str] = Field(default=None, min_length=2, max_length=64)
    password: Optional[str] = Field(default=None, min_length=6, max_length=64)
    role: Optional[str] = None

    @field_validator("username")
    @classmethod
    def trim_username(cls, value: Optional[str]) -> Optional[str]:
        if value is None:
            return None
        name = value.strip()
        if not name:
            raise ValueError("请填写用户名")
        return name

    @field_validator("role")
    @classmethod
    def check_role(cls, value: Optional[str]) -> Optional[str]:
        if value is None:
            return None
        role = value.strip()
        if role not in ("admin", "manager"):
            raise ValueError("角色须为管理员或店长")
        return role


class StaffOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    role: str = "manager"
    role_label: str = "店长"
    created_at: datetime


class CustomerBase(BaseModel):
    name: str = Field(min_length=1, max_length=64)
    phone: str = Field(min_length=1, max_length=32)
    gender: str = ""
    birthday: Optional[date] = None
    wechat: str = Field(default="", max_length=64)
    address: str = Field(default="", max_length=255)
    intention: str = Field(default="", max_length=16)
    notes: str = ""
    assigned_to: Optional[int] = None

    @field_validator("wechat", "address", "notes", mode="before")
    @classmethod
    def trim_optional(cls, value) -> str:
        return (value or "").strip() if value is not None else ""


class CustomerCreate(CustomerBase):
    @field_validator("phone")
    @classmethod
    def check_phone(cls, value: str) -> str:
        phone = (value or "").strip().replace(" ", "").replace("-", "")
        if not re.fullmatch(r"1[3-9]\d{9}", phone):
            raise ValueError("请输入正确的11位手机号")
        return phone

    @field_validator("intention")
    @classmethod
    def check_intention(cls, value: str) -> str:
        intention = (value or "").strip()
        if intention and intention not in ("高", "中", "低"):
            raise ValueError("意向度可选：高 / 中 / 低")
        return intention


class CustomerUpdate(CustomerCreate):
    pass


class CustomerOut(CustomerBase):
    model_config = ConfigDict(from_attributes=True)

    id: int
    created_at: datetime
    last_visited_at: Optional[datetime] = None
    visit_count: int = 0
    assigned_to_username: str = ""
    assigned_to_role_label: str = ""


class StaffOptionOut(BaseModel):
    id: int
    username: str
    role_label: str = ""

    @field_validator("phone", mode="before")
    @classmethod
    def keep_phone(cls, value) -> str:
        # 列表/详情不强制校验历史脏数据，避免整表 500
        return (value or "").strip() if value is not None else ""

    @field_validator("intention", mode="before")
    @classmethod
    def keep_intention(cls, value) -> str:
        return (value or "").strip() if value is not None else ""


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


class CustomerPhotoOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    customer_id: int
    kind: str
    url: str
    original_name: str = ""
    created_at: datetime


PROJECT_UNITS = ("支", "个", "盒", "次")
STOCK_UNITS = ("支", "个", "盒", "次", "套")


def normalize_unit(value: str, allowed: tuple[str, ...] = PROJECT_UNITS) -> str:
    unit = (value or "个").strip() or "个"
    if unit == "单位":
        unit = "次"
    if unit not in allowed:
        raise ValueError("单位须为：" + " / ".join(allowed))
    return unit


class ProjectMedicineIn(BaseModel):
    item_id: int
    quantity: int = Field(ge=1)
    unit: str = Field(default="个", max_length=16)

    @field_validator("unit")
    @classmethod
    def check_unit(cls, value: str) -> str:
        return normalize_unit(value, STOCK_UNITS)


class ProjectMedicineOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    item_id: int
    item_name: str
    quantity: int
    unit: str = "个"


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
    medicines: List[ProjectMedicineIn] = Field(default_factory=list)


class ProjectUpdate(ProjectBase):
    medicines: List[ProjectMedicineIn] = Field(default_factory=list)


class ProjectOut(ProjectBase):
    model_config = ConfigDict(from_attributes=True)

    id: int
    created_at: datetime
    stock_qty: int = 0
    cost_price: Decimal = Decimal("0")
    medicines: List[ProjectMedicineOut] = Field(default_factory=list)


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
    sale_price: Decimal = Decimal("0")
    created_at: datetime

    @field_validator("spec", mode="before")
    @classmethod
    def fill_spec(cls, value):
        return value or ""

    @field_validator("unit", mode="before")
    @classmethod
    def fill_unit(cls, value):
        unit = (value or "").strip()
        return unit if unit and unit != "单位" else "个"


class StockInBody(BaseModel):
    item_id: Optional[int] = None
    name: str = ""
    spec: str = Field(default="", max_length=64)
    quantity: int = Field(ge=1)
    unit: str = Field(default="个", max_length=16)
    unit_cost: Decimal = Field(ge=0, description="进货单价")
    sale_price: Decimal = Field(ge=0, description="售价")
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
        return normalize_unit(value, STOCK_UNITS)

    @model_validator(mode="after")
    def need_product(self):
        if not self.item_id and not self.name:
            raise ValueError("请选择产品")
        return self


class StockOutBody(BaseModel):
    item_id: int
    quantity: int = Field(ge=1)
    remark: str = Field(min_length=1, max_length=255, description="出库原因")

    @field_validator("remark")
    @classmethod
    def trim_remark(cls, value: str) -> str:
        reason = (value or "").strip()
        if not reason:
            raise ValueError("请填写出库原因")
        return reason


class StockMovementOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    item_id: int
    item_name: str
    kind: str
    quantity: int
    unit: str = "个"
    unit_cost: Decimal
    remark: str = ""
    inbound_no: str = ""
    moved_at: Optional[date] = None
    created_at: datetime

    @field_validator("unit", mode="before")
    @classmethod
    def fill_unit(cls, value):
        unit = (value or "").strip()
        return unit if unit and unit != "单位" else "个"

    @field_validator("remark", "inbound_no", mode="before")
    @classmethod
    def fill_text(cls, value):
        return value or ""


class OrderItemIn(BaseModel):
    project_id: Optional[int] = None
    item_id: Optional[int] = None
    quantity: int = Field(default=1, ge=1)

    @model_validator(mode="after")
    def exactly_one_line(self):
        has_project = self.project_id is not None
        has_product = self.item_id is not None
        if has_project == has_product:
            raise ValueError("每条明细须指定项目或产品其一")
        return self


class OrderCreate(BaseModel):
    customer_id: int
    items: List[OrderItemIn] = Field(min_length=1)
    deal_price: Decimal = Field(ge=0, description="最终成交价格")
    remark: str = ""


class OrderUpdate(BaseModel):
    deal_price: Decimal = Field(ge=0, description="最终成交价格")
    remark: str = ""
    items: List[OrderItemIn] = Field(min_length=1)


class OrderItemOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    project_id: Optional[int] = None
    item_id: Optional[int] = None
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
    created_by: Optional[int] = None
    created_by_username: Optional[str] = None
    created_by_role_label: Optional[str] = None
    created_at: datetime
    items: List[OrderItemOut] = []


class OrderStatusUpdate(BaseModel):
    status: str


class OrderStockNeedOut(BaseModel):
    item_id: int
    item_name: str
    unit: str = "个"
    need: int
    on_hand: int
    enough: bool


class OrderStockPreviewIn(BaseModel):
    items: List[OrderItemIn] = Field(min_length=1)


class OrderStockPreviewOut(BaseModel):
    items: List[OrderStockNeedOut] = Field(default_factory=list)
    enough: bool = True


class RevenueDayOut(BaseModel):
    date: str  # YYYY-MM-DD
    day: int
    revenue: Decimal
    order_count: int
    cost: Decimal
    inbound_count: int
    profit: Decimal


class RevenueSummaryOut(BaseModel):
    year: int
    month: int
    revenue: Decimal
    order_count: int
    cost: Decimal
    inbound_count: int
    profit: Decimal
    days: List[RevenueDayOut] = Field(default_factory=list)


class AppReleaseOut(BaseModel):
    version_name: str
    version_code: int
    filename: str
    size_bytes: int
    updated_at: str
    download_url: str
