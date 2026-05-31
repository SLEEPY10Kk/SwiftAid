from __future__ import annotations

import os

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy.orm import Session, sessionmaker


DATABASE_URL = os.getenv("DATABASE_URL")

if DATABASE_URL and DATABASE_URL.startswith("postgres://"):
    DATABASE_URL = DATABASE_URL.replace("postgres://", "postgresql://", 1)


class Base(DeclarativeBase):
    pass


engine = create_engine(DATABASE_URL, pool_pre_ping=True, echo=False) if DATABASE_URL else None

SessionLocal = sessionmaker(bind=engine, expire_on_commit=False, class_=Session) if engine else None


def database_enabled() -> bool:
    return engine is not None and SessionLocal is not None


def get_db() -> Session:
    if SessionLocal is None:
        raise RuntimeError("DATABASE_URL is not configured.")
    with SessionLocal() as session:
        yield session


def init_db() -> None:
    if engine is None:
        return

    from . import db_models

    Base.metadata.create_all(bind=engine)
