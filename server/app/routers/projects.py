from __future__ import annotations

from typing import List

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session, selectinload

from ..auth import get_current_admin, require_admin
from ..database import get_db
from ..models import Admin, OrderItem, Project, ProjectMedicine, StockItem
from ..schemas import STOCK_UNITS, ProjectCreate, ProjectMedicineIn, ProjectOut, ProjectUpdate, normalize_unit

router = APIRouter(prefix="/api/projects", tags=["projects"])


def _project_query(db: Session):
    return db.query(Project).options(selectinload(Project.medicines))


def _replace_medicines(db: Session, project: Project, medicines: List[ProjectMedicineIn]) -> None:
    seen: set[int] = set()
    rows: List[ProjectMedicine] = []
    for item in medicines:
        if item.item_id in seen:
            raise HTTPException(status_code=400, detail="同一产品不能重复添加")
        seen.add(item.item_id)
        stock = db.get(StockItem, item.item_id)
        if not stock:
            raise HTTPException(status_code=404, detail="产品不存在")
        rows.append(
            ProjectMedicine(
                item_id=stock.id,
                item_name=stock.name,
                quantity=item.quantity,
                unit=normalize_unit(item.unit, STOCK_UNITS),
            )
        )
    project.medicines.clear()
    db.flush()
    project.medicines.extend(rows)


@router.get("", response_model=List[ProjectOut])
def list_projects(
    active_only: bool = Query(False),
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    query = _project_query(db).order_by(Project.id.desc())
    if active_only:
        query = query.filter(Project.active.is_(True))
    return query.all()


@router.post("", response_model=ProjectOut, status_code=status.HTTP_201_CREATED)
def create_project(
    body: ProjectCreate,
    db: Session = Depends(get_db),
    _: Admin = Depends(require_admin),
):
    project = Project(**body.model_dump(exclude={"medicines"}))
    db.add(project)
    db.flush()
    _replace_medicines(db, project, body.medicines)
    db.commit()
    return _project_query(db).filter(Project.id == project.id).one()


@router.get("/{project_id}", response_model=ProjectOut)
def get_project(
    project_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(get_current_admin),
):
    project = _project_query(db).filter(Project.id == project_id).first()
    if not project:
        raise HTTPException(status_code=404, detail="项目不存在")
    return project


@router.put("/{project_id}", response_model=ProjectOut)
def update_project(
    project_id: int,
    body: ProjectUpdate,
    db: Session = Depends(get_db),
    _: Admin = Depends(require_admin),
):
    project = _project_query(db).filter(Project.id == project_id).first()
    if not project:
        raise HTTPException(status_code=404, detail="项目不存在")
    for key, value in body.model_dump(exclude={"medicines"}).items():
        setattr(project, key, value)
    _replace_medicines(db, project, body.medicines)
    db.commit()
    return _project_query(db).filter(Project.id == project.id).one()


@router.delete("/{project_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_project(
    project_id: int,
    db: Session = Depends(get_db),
    _: Admin = Depends(require_admin),
):
    project = db.get(Project, project_id)
    if not project:
        raise HTTPException(status_code=404, detail="项目不存在")
    used = db.query(OrderItem).filter(OrderItem.project_id == project_id).first()
    if used:
        raise HTTPException(status_code=400, detail="该项目已有订单，无法删除")
    db.delete(project)
    db.commit()
    return None
