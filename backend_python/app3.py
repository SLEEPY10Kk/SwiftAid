import os
import hashlib
import secrets
import jwt
from uuid import UUID, uuid4
from typing import List, Optional
from dotenv import load_dotenv
from google.oauth2 import id_token
from sqlalchemy.orm import DeclarativeBase
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone
from fastapi import FastAPI, Depends, HTTPException, Header
from google.auth.transport import requests as google_requests
from sqlalchemy import Column, Integer, String, select, ForeignKey, Boolean, UUID as SQLUUID, UniqueConstraint
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from pydantic import BaseModel, EmailStr, ConfigDict, Field
from passlib.context import CryptContext

load_dotenv()

# --- Configuration from app.py ---
SECRET_KEY = os.getenv("SECRET_KEY")
ALGORITHM = "HS256"
GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID") or os.getenv("GOOGLE_WEB_CLIENT_ID")
SQLALCHEMY_DATABASE_URL = os.getenv("DATABASE_URL")
ACCESS_TOKEN_EXPIRE_MINUTES = 15
REFRESH_TOKEN_EXPIRE_DAYS = 7
PHONE_OTP_EXPIRE_MINUTES = 10
PHONE_OTP_RESEND_COOLDOWN_SECONDS = 30
PHONE_OTP_MAX_ATTEMPTS = 5
PHONE_OTP_DEBUG_MODE = os.getenv("PHONE_OTP_DEBUG_MODE", "true").lower() == "true"

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
engine = create_async_engine(SQLALCHEMY_DATABASE_URL)
SessionLocal = async_sessionmaker(bind=engine, class_=AsyncSession, expire_on_commit=False)


# --- Database Models ---
class Base(DeclarativeBase):
    pass


# User model from app.py. Auth depends on this exact shape.
class DBUser(Base):
    __tablename__ = "users"

    id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4)
    username = Column(String, unique=True, index=True, nullable=False)
    email = Column(String, unique=True, index=True, nullable=False)
    first_name = Column(String, nullable=False)
    last_name = Column(String, nullable=False)
    full_name = Column(String)
    phone_number = Column(String, unique=True, nullable=False)
    age = Column(Integer, nullable=True)
    gender = Column(String, nullable=True)
    password_hashed = Column(String, nullable=True)
    oauth_id = Column(String, unique=True, nullable=True)
    oauth_provider = Column(String, nullable=True)
    country = Column(String)
    state = Column(String)
    city = Column(String)
    area = Column(String)
    is_complete = Column(Boolean, default=False)


# Refresh-token model from app.py.
class DBRefreshToken(Base):
    __tablename__ = "refresh_tokens"

    id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id = Column(SQLUUID(as_uuid=True), ForeignKey("users.id"))
    token = Column(String, unique=True, index=True)
    revoked = Column(Boolean, default=False)


# Non-auth models from app2.py, adapted to the app.py users.id primary key.
class DBEmergencyContact(Base):
    __tablename__ = "Emergency_contacts"

    contact_id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id = Column(SQLUUID(as_uuid=True), ForeignKey("users.id"), index=True)
    contact_name = Column(String, index=True, nullable=True)
    contact_number = Column(String, index=True)
    relationship = Column(String, index=True)
    priority_order = Column(Integer, index=True)


class DBMedicalInfo(Base):
    __tablename__ = "Medical_info"

    medical_id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id = Column(SQLUUID(as_uuid=True), ForeignKey("users.id"), unique=True)
    bloodGroup = Column(String, index=True)
    allergies = Column(String, index=True)
    chronicConditions = Column(String, index=True)
    currentmedications = Column(String, index=True)


class DBInsuranceInfo(Base):
    __tablename__ = "Insurance_Info"

    insurance_id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id = Column(SQLUUID(as_uuid=True), ForeignKey("users.id"), index=True)
    insurance_type = Column(String, index=True)
    insurance_provider = Column(String, index=True)
    insurance_policy_number = Column(String, unique=True, index=True)
    policy_holder_name = Column(String, nullable=True)
    coverage_type = Column(String, nullable=True)
    expiry_date = Column(String, nullable=True)
    coverage_amount = Column(String, nullable=True)
    document_uri = Column(String, nullable=True)


class DBPhoneVerification(Base):
    __tablename__ = "phone_verifications"
    __table_args__ = (
        UniqueConstraint("user_id", "phone_number", name="uq_phone_verification_user_phone"),
    )

    verification_id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id = Column(SQLUUID(as_uuid=True), ForeignKey("users.id"), index=True, nullable=False)
    phone_number = Column(String, index=True, nullable=False)
    code_hash = Column(String, nullable=False)
    attempts = Column(Integer, default=0, nullable=False)
    verified = Column(Boolean, default=False, nullable=False)
    consumed = Column(Boolean, default=False, nullable=False)
    last_sent_at = Column(String, nullable=False)
    expires_at = Column(String, nullable=False)
    verified_at = Column(String, nullable=True)


# --- Pydantic Models for app2.py non-auth routes ---
class UserResponse(BaseModel):
    id: UUID
    username: str
    email: EmailStr
    first_name: str
    last_name: str
    full_name: Optional[str] = None
    phone_number: str
    age: Optional[int] = None
    gender: Optional[str] = None
    country: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    area: Optional[str] = None
    is_complete: bool

    model_config = ConfigDict(from_attributes=True)


class UserUpdate(BaseModel):
    username: Optional[str] = None
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    full_name: Optional[str] = None
    phone_number: Optional[str] = None
    age: Optional[int] = Field(default=None, ge=1, le=120)
    gender: Optional[str] = None
    country: Optional[str] = None
    city: Optional[str] = None
    state: Optional[str] = None
    area: Optional[str] = None


class DBEmergencyContactBase(BaseModel):
    user_id: Optional[UUID] = None
    contact_name: Optional[str] = None
    contact_number: str
    relationship: str
    priority_order: int


class EmergencyContactCreate(DBEmergencyContactBase):
    pass


class EmergencyContactUpdate(BaseModel):
    contact_name: Optional[str] = None
    contact_number: Optional[str] = None
    relationship: Optional[str] = None
    priority_order: Optional[int] = None


class EmergencyContactResponse(DBEmergencyContactBase):
    contact_id: UUID
    model_config = ConfigDict(from_attributes=True)


class DBMedicalInfoBase(BaseModel):
    bloodGroup: str
    allergies: str
    chronicConditions: str
    currentmedications: str


class MedicalInfoUpdate(BaseModel):
    bloodGroup: Optional[str] = None
    allergies: Optional[str] = None
    chronicConditions: Optional[str] = None
    currentmedications: Optional[str] = None


class MedicalInfoCreate(DBMedicalInfoBase):
    user_id: Optional[UUID] = None


class MedicalInfoResponse(DBMedicalInfoBase):
    medical_id: UUID
    user_id: UUID
    model_config = ConfigDict(from_attributes=True)


class DBInsuranceInfoBase(BaseModel):
    user_id: Optional[UUID] = None
    insurance_type: str
    insurance_provider: str
    insurance_policy_number: str
    policy_holder_name: Optional[str] = None
    coverage_type: Optional[str] = None
    expiry_date: Optional[str] = None
    coverage_amount: Optional[str] = None
    document_uri: Optional[str] = None


class InsuranceInfoUpdate(BaseModel):
    insurance_type: Optional[str] = None
    insurance_provider: Optional[str] = None
    insurance_policy_number: Optional[str] = None
    policy_holder_name: Optional[str] = None
    coverage_type: Optional[str] = None
    expiry_date: Optional[str] = None
    coverage_amount: Optional[str] = None
    document_uri: Optional[str] = None


class InsuranceInfoCreate(DBInsuranceInfoBase):
    pass


class InsuranceInfoResponse(DBInsuranceInfoBase):
    insurance_id: UUID
    model_config = ConfigDict(from_attributes=True)


class PhoneOtpSendRequest(BaseModel):
    phone_number: str


class PhoneOtpVerifyRequest(BaseModel):
    phone_number: str
    otp_code: str


class PhoneOtpResponse(BaseModel):
    detail: str
    phone_number: str
    expires_in_seconds: int
    debug_code: Optional[str] = None


# --- Auth Pydantic Models from app.py ---
class CompleteProfileRequest(BaseModel):
    username: str
    fullName: str
    age: int = Field(ge=1, le=120)
    gender: str = Field(min_length=1)
    dialCode: str
    phone: str
    country: str
    state: str
    city: str
    area: str | None
    password: str


class RefreshTokenRequest(BaseModel):
    refreshToken: str


class GoogleToken(BaseModel):
    idToken: str


class EmailLoginRequest(BaseModel):
    email: str
    password: str


class UserSettingsResponse(BaseModel):
    user: UserResponse
    emergency_contacts: List[EmergencyContactResponse]
    medical_info: List[MedicalInfoResponse]
    insurance_info: List[InsuranceInfoResponse]


# --- Helpers from app.py ---
def create_access_token(data: dict):
    to_encode = data.copy()
    expire = datetime.now(timezone.utc) + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


def create_refresh_token(data: dict):
    to_encode = data.copy()
    expire = datetime.now(timezone.utc) + timedelta(days=REFRESH_TOKEN_EXPIRE_DAYS)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


def decode_token(token: str):
    return jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])


def normalize_phone_number(phone_number: str) -> str:
    return phone_number.strip().replace(" ", "")


def hash_phone_otp(code: str) -> str:
    return hashlib.sha256(code.encode("utf-8")).hexdigest()


def generate_phone_otp() -> str:
    return f"{secrets.randbelow(1_000_000):06d}"


async def get_phone_verification(db: AsyncSession, user_id: UUID, phone_number: str):
    result = await db.execute(
        select(DBPhoneVerification).filter(
            DBPhoneVerification.user_id == user_id,
            DBPhoneVerification.phone_number == phone_number,
        )
    )
    return result.scalars().first()


async def issue_phone_verification(db: AsyncSession, user_id: UUID, phone_number: str) -> tuple[str, DBPhoneVerification]:
    normalized_phone = normalize_phone_number(phone_number)
    verification = await get_phone_verification(db, user_id, normalized_phone)
    now = datetime.now(timezone.utc)
    code = generate_phone_otp()
    expires_at = now + timedelta(minutes=PHONE_OTP_EXPIRE_MINUTES)

    if verification:
        last_sent_at = verification.last_sent_at
        if isinstance(last_sent_at, str):
            try:
                last_sent_at_dt = datetime.fromisoformat(last_sent_at)
                if last_sent_at_dt.tzinfo is None:
                    last_sent_at_dt = last_sent_at_dt.replace(tzinfo=timezone.utc)
                if now - last_sent_at_dt < timedelta(seconds=PHONE_OTP_RESEND_COOLDOWN_SECONDS):
                    raise HTTPException(status_code=429, detail="OTP_TOO_SOON")
            except ValueError:
                pass
        verification.code_hash = hash_phone_otp(code)
        verification.attempts = 0
        verification.verified = False
        verification.consumed = False
        verification.last_sent_at = now.isoformat()
        verification.expires_at = expires_at.isoformat()
        verification.verified_at = None
    else:
        verification = DBPhoneVerification(
            user_id=user_id,
            phone_number=normalized_phone,
            code_hash=hash_phone_otp(code),
            attempts=0,
            verified=False,
            consumed=False,
            last_sent_at=now.isoformat(),
            expires_at=expires_at.isoformat(),
            verified_at=None,
        )
        db.add(verification)

    await db.commit()
    await db.refresh(verification)
    return code, verification


async def require_verified_phone_otp(db: AsyncSession, user_id: UUID, phone_number: str) -> DBPhoneVerification:
    normalized_phone = normalize_phone_number(phone_number)
    verification = await get_phone_verification(db, user_id, normalized_phone)
    if not verification:
        raise HTTPException(status_code=400, detail="PHONE_NOT_VERIFIED")

    now = datetime.now(timezone.utc)
    try:
        expires_at = datetime.fromisoformat(verification.expires_at)
        if expires_at.tzinfo is None:
            expires_at = expires_at.replace(tzinfo=timezone.utc)
    except ValueError:
        raise HTTPException(status_code=400, detail="PHONE_NOT_VERIFIED")

    if verification.consumed or not verification.verified or expires_at <= now:
        raise HTTPException(status_code=400, detail="PHONE_NOT_VERIFIED")

    verification.consumed = True
    return verification


def get_user_id_from_bearer(authorization: str = Header(default=None)) -> UUID:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="UNAUTHORIZED")

    token = authorization.split(" ", 1)[1]
    try:
        payload = decode_token(token)
        user_id = payload.get("sub")
        if not user_id:
            raise HTTPException(status_code=401, detail="INVALID_TOKEN")
        return UUID(user_id)
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="TOKEN_EXPIRED")
    except Exception:
        raise HTTPException(status_code=401, detail="INVALID_TOKEN")


async def get_db():
    async with SessionLocal() as session:
        yield session


@asynccontextmanager
async def lifespan(app: FastAPI):
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield


app = FastAPI(lifespan=lifespan)


# --- User routes from app2.py, adapted to app.py user model ---
@app.get("/users/{user_id}", response_model=UserResponse)
async def read_user(user_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBUser).filter(DBUser.id == user_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user


async def build_user_settings(user_id: UUID, db: AsyncSession) -> UserSettingsResponse:
    user_result = await db.execute(select(DBUser).filter(DBUser.id == user_id))
    user = user_result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    emergency_result = await db.execute(
        select(DBEmergencyContact)
        .filter(DBEmergencyContact.user_id == user_id)
        .order_by(DBEmergencyContact.priority_order)
    )
    medical_result = await db.execute(select(DBMedicalInfo).filter(DBMedicalInfo.user_id == user_id))
    insurance_result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.user_id == user_id))

    return UserSettingsResponse(
        user=user,
        emergency_contacts=emergency_result.scalars().all(),
        medical_info=medical_result.scalars().all(),
        insurance_info=insurance_result.scalars().all(),
    )


@app.post("/me/phone/send-otp", response_model=PhoneOtpResponse)
async def send_my_phone_otp(
    data: PhoneOtpSendRequest,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    normalized_phone = normalize_phone_number(data.phone_number)
    if not normalized_phone:
        raise HTTPException(status_code=400, detail="PHONE_REQUIRED")

    existing_phone = await db.execute(
        select(DBUser).filter(
            DBUser.phone_number == normalized_phone,
            DBUser.id != current_user_id,
        )
    )
    if existing_phone.scalars().first():
        raise HTTPException(status_code=409, detail="PHONE_TAKEN")

    code, verification = await issue_phone_verification(db, current_user_id, normalized_phone)
    if PHONE_OTP_DEBUG_MODE:
        print(f"[PHONE_OTP_DEBUG] user={current_user_id} phone={normalized_phone} otp={code}")

    return PhoneOtpResponse(
        detail="OTP_SENT",
        phone_number=verification.phone_number,
        expires_in_seconds=PHONE_OTP_EXPIRE_MINUTES * 60,
        debug_code=code if PHONE_OTP_DEBUG_MODE else None,
    )


@app.post("/me/phone/verify-otp", response_model=PhoneOtpResponse)
async def verify_my_phone_otp(
    data: PhoneOtpVerifyRequest,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    normalized_phone = normalize_phone_number(data.phone_number)
    verification = await get_phone_verification(db, current_user_id, normalized_phone)
    if not verification:
        raise HTTPException(status_code=404, detail="OTP_NOT_REQUESTED")

    now = datetime.now(timezone.utc)
    try:
        expires_at = datetime.fromisoformat(verification.expires_at)
        if expires_at.tzinfo is None:
            expires_at = expires_at.replace(tzinfo=timezone.utc)
    except ValueError:
        raise HTTPException(status_code=400, detail="OTP_EXPIRED")

    if verification.consumed:
        raise HTTPException(status_code=400, detail="OTP_ALREADY_USED")
    if expires_at <= now:
        raise HTTPException(status_code=400, detail="OTP_EXPIRED")
    if verification.attempts >= PHONE_OTP_MAX_ATTEMPTS:
        raise HTTPException(status_code=429, detail="OTP_ATTEMPTS_EXCEEDED")

    if hash_phone_otp(data.otp_code.strip()) != verification.code_hash:
        verification.attempts += 1
        await db.commit()
        raise HTTPException(status_code=400, detail="OTP_INVALID")

    verification.verified = True
    verification.verified_at = now.isoformat()
    await db.commit()
    await db.refresh(verification)

    return PhoneOtpResponse(
        detail="PHONE_VERIFIED",
        phone_number=verification.phone_number,
        expires_in_seconds=PHONE_OTP_EXPIRE_MINUTES * 60,
    )


@app.get("/users/{user_id}/settings", response_model=UserSettingsResponse)
async def read_user_settings(
    user_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    if current_user_id != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")
    return await build_user_settings(user_id, db)


@app.get("/me/settings", response_model=UserSettingsResponse)
async def read_my_settings(
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    return await build_user_settings(current_user_id, db)


@app.put("/users/{user_id}", response_model=UserResponse)
async def update_user(
    user_id: UUID,
    updated_data: UserUpdate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    if current_user_id != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBUser).filter(DBUser.id == user_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    if updated_data.username and updated_data.username != user.username:
        result2 = await db.execute(select(DBUser).filter(DBUser.username == updated_data.username))
        if result2.scalars().first():
            raise HTTPException(status_code=400, detail="Username already taken")

    if updated_data.phone_number and updated_data.phone_number != user.phone_number:
        result3 = await db.execute(select(DBUser).filter(DBUser.phone_number == updated_data.phone_number))
        if result3.scalars().first():
            raise HTTPException(status_code=400, detail="Phone number already registered")
        await require_verified_phone_otp(db, user_id, updated_data.phone_number)

    if updated_data.age is not None and updated_data.age < 1:
        raise HTTPException(status_code=400, detail="INVALID_AGE")
    if updated_data.gender is not None and not updated_data.gender.strip():
        raise HTTPException(status_code=400, detail="INVALID_GENDER")

    update_fields = updated_data.model_dump(exclude_unset=True)
    for field, value in update_fields.items():
        setattr(user, field, value)

    await db.commit()
    await db.refresh(user)
    return user


@app.patch("/users/{user_id}", response_model=UserResponse)
async def patch_user(
    user_id: UUID,
    updated_data: UserUpdate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    if current_user_id != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    return await update_user(user_id, updated_data, db)


@app.put("/me/profile", response_model=UserResponse)
async def update_my_profile(
    updated_data: UserUpdate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    return await update_user(current_user_id, updated_data, db, current_user_id)


@app.delete("/users/{user_id}")
async def delete_user(
    user_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    if current_user_id != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBUser).filter(DBUser.id == user_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    await db.delete(user)
    await db.commit()
    return {"detail": "User deleted successfully"}


# --- Emergency contact routes from app2.py ---
@app.post("/emergency_contacts/", response_model=EmergencyContactResponse)
async def create_emergency_contact(
    contact: EmergencyContactCreate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    contact_user_id = contact.user_id or current_user_id
    if current_user_id != contact_user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBUser).filter(DBUser.id == contact_user_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    result2 = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.user_id == contact_user_id))
    existing_result = result2.scalars().all()
    if len(existing_result) >= 3:
        raise HTTPException(status_code=400, detail="Maximum of 3 emergency contacts allowed per user")

    result3 = await db.execute(
        select(DBEmergencyContact).filter(
            DBEmergencyContact.user_id == contact_user_id,
            DBEmergencyContact.contact_number == contact.contact_number,
        )
    )
    existing_result3 = result3.scalars().first()
    if existing_result3:
        raise HTTPException(status_code=400, detail="Contact number already exists for this user")

    new_contact = DBEmergencyContact(**contact.model_dump(exclude={"user_id"}), user_id=contact_user_id)
    db.add(new_contact)
    await db.commit()
    await db.refresh(new_contact)
    return new_contact


@app.post("/me/emergency-contacts", response_model=EmergencyContactResponse)
async def create_my_emergency_contact(
    contact: EmergencyContactCreate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    return await create_emergency_contact(contact, db, current_user_id)


@app.put("/emergency_contacts/contact_id/{contact_id}", response_model=EmergencyContactResponse)
async def update_emergency_contact(
    contact_id: UUID,
    updated_data: EmergencyContactUpdate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    result = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.contact_id == contact_id))
    contact = result.scalars().first()
    if not contact:
        raise HTTPException(status_code=404, detail="Emergency contact not found")
    if contact.user_id != current_user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    update_fields = updated_data.model_dump(exclude_unset=True)
    new_contact_number = update_fields.get("contact_number")
    if new_contact_number:
        result_dup = await db.execute(
            select(DBEmergencyContact).filter(
                DBEmergencyContact.user_id == current_user_id,
                DBEmergencyContact.contact_number == new_contact_number,
                DBEmergencyContact.contact_id != contact_id,
            )
        )
        if result_dup.scalars().first():
            raise HTTPException(status_code=400, detail="Contact number already exists for this user")

    for field, value in update_fields.items():
        setattr(contact, field, value)

    await db.commit()
    await db.refresh(contact)
    return contact


@app.put("/me/emergency-contacts/{contact_id}", response_model=EmergencyContactResponse)
async def update_my_emergency_contact(
    contact_id: UUID,
    updated_data: EmergencyContactUpdate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    return await update_emergency_contact(contact_id, updated_data, db, current_user_id)


@app.get("/emergency_contacts/{user_id}", response_model=List[EmergencyContactResponse])
async def get_emergencycontacts(user_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(DBEmergencyContact)
        .filter(DBEmergencyContact.user_id == user_id)
        .order_by(DBEmergencyContact.priority_order)
    )
    contacts = result.scalars().all()
    if not contacts:
        raise HTTPException(status_code=404, detail="Emergency contacts not found")
    return contacts


@app.get("/me/emergency-contacts", response_model=List[EmergencyContactResponse])
async def get_my_emergency_contacts(
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    result = await db.execute(
        select(DBEmergencyContact)
        .filter(DBEmergencyContact.user_id == current_user_id)
        .order_by(DBEmergencyContact.priority_order)
    )
    return result.scalars().all()


@app.delete("/emergency_contacts/{user_id}")
async def delete_emergency_contacts(
    user_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    if current_user_id != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.user_id == user_id))
    contacts = result.scalars().all()
    if not contacts:
        raise HTTPException(status_code=404, detail="Emergency contact not found")
    for contact in contacts:
        await db.delete(contact)
    await db.commit()
    return {"detail": "All Emergency contacts deleted successfully"}


@app.delete("/me/emergency-contacts/{contact_id}")
async def delete_my_emergency_contact(
    contact_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    return await delete_emergency_contact_by_id(contact_id, db, current_user_id)


@app.delete("/emergency_contacts/contact_id/{contact_id}")
async def delete_emergency_contact_by_id(
    contact_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    result = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.contact_id == contact_id))
    contact = result.scalars().first()
    if not contact:
        raise HTTPException(status_code=404, detail="Emergency contact not found")
    if contact.user_id != current_user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")
    await db.delete(contact)
    await db.commit()
    return {"detail": "Emergency contact deleted successfully"}


# --- Insurance info routes from app2.py ---
@app.post("/insurance_info/", response_model=InsuranceInfoResponse)
async def create_insurance_info(
    info: InsuranceInfoCreate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    insurance_user_id = info.user_id or current_user_id
    if current_user_id != insurance_user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBUser).filter(DBUser.id == insurance_user_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="user not found")

    new_insurance_info = DBInsuranceInfo(**info.model_dump(exclude={"user_id"}), user_id=insurance_user_id)
    db.add(new_insurance_info)
    await db.commit()
    await db.refresh(new_insurance_info)
    return new_insurance_info


@app.post("/me/insurance-info", response_model=InsuranceInfoResponse)
async def create_my_insurance_info(
    info: InsuranceInfoCreate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    return await create_insurance_info(info, db, current_user_id)


@app.put("/insurance_info/{user_id}", response_model=InsuranceInfoResponse)
async def update_insurance_info(
    user_id: UUID,
    updated_data: InsuranceInfoUpdate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    if current_user_id != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.user_id == user_id))
    info = result.scalars().first()
    if not info:
        return await create_insurance_info(
            InsuranceInfoCreate(
                user_id=user_id,
                insurance_type=updated_data.insurance_type or "",
                insurance_provider=updated_data.insurance_provider or "",
                insurance_policy_number=updated_data.insurance_policy_number or "",
                policy_holder_name=updated_data.policy_holder_name,
                coverage_type=updated_data.coverage_type,
                expiry_date=updated_data.expiry_date,
                coverage_amount=updated_data.coverage_amount,
                document_uri=updated_data.document_uri,
            ),
            db,
            current_user_id,
        )

    update_fields = updated_data.model_dump(exclude_unset=True)
    for field, value in update_fields.items():
        setattr(info, field, value)

    await db.commit()
    await db.refresh(info)
    return info


@app.put("/me/insurance-info/{insurance_id}", response_model=InsuranceInfoResponse)
async def update_my_insurance_info(
    insurance_id: UUID,
    updated_data: InsuranceInfoUpdate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.insurance_id == insurance_id))
    info = result.scalars().first()
    if not info:
        raise HTTPException(status_code=404, detail="Insurance info not found")
    if info.user_id != current_user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    update_fields = updated_data.model_dump(exclude_unset=True)
    for field, value in update_fields.items():
        setattr(info, field, value)

    await db.commit()
    await db.refresh(info)
    return info


@app.get("/insurance_info/{user_id}", response_model=List[InsuranceInfoResponse])
async def get_insurance_info(
    user_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    if current_user_id != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.user_id == user_id))
    return result.scalars().all()


@app.get("/me/insurance-info", response_model=List[InsuranceInfoResponse])
async def get_my_insurance_info(
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.user_id == current_user_id))
    return result.scalars().all()


@app.get("/insurance_info/insurance_id/{insurance_id}", response_model=InsuranceInfoResponse)
async def get_insurance_info_by_id(
    insurance_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.insurance_id == insurance_id))
    info = result.scalars().first()
    if not info:
        raise HTTPException(status_code=404, detail="Insurance info not found")
    if info.user_id != current_user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")
    return info


@app.delete("/insurance_info/{user_id}")
async def delete_insurance_info(
    user_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    if current_user_id != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.user_id == user_id))
    infos = result.scalars().all()
    if not infos:
        raise HTTPException(status_code=404, detail="Insurance info not found")
    for info in infos:
        await db.delete(info)
    await db.commit()
    return {"detail": "Insurance info deleted successfully"}


@app.delete("/me/insurance-info/{insurance_id}")
async def delete_my_insurance_info(
    insurance_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    return await delete_insurance_info_by_id(insurance_id, db, current_user_id)


@app.delete("/insurance_info/insurance_id/{insurance_id}")
async def delete_insurance_info_by_id(
    insurance_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.insurance_id == insurance_id))
    info = result.scalars().first()
    if not info:
        raise HTTPException(status_code=404, detail="Insurance info not found")
    if info.user_id != current_user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")
    await db.delete(info)
    await db.commit()
    return {"detail": "Insurance info deleted successfully"}


# --- Medical info routes from app2.py ---
@app.post("/medical_info/", response_model=MedicalInfoResponse)
async def create_medical_info(
    info: MedicalInfoCreate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    medical_user_id = info.user_id or current_user_id
    if current_user_id != medical_user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBUser).filter(DBUser.id == medical_user_id))
    if not result.scalars().first():
        raise HTTPException(status_code=404, detail="User not found")

    existing = await db.execute(select(DBMedicalInfo).filter(DBMedicalInfo.user_id == medical_user_id))
    current = existing.scalars().first()
    if current:
        update_fields = info.model_dump(exclude_unset=True, exclude={"user_id"})
        for field, value in update_fields.items():
            setattr(current, field, value)
        await db.commit()
        await db.refresh(current)
        return current

    new_medical_info = DBMedicalInfo(**info.model_dump(exclude={"user_id"}), user_id=medical_user_id)
    db.add(new_medical_info)
    await db.commit()
    await db.refresh(new_medical_info)
    return new_medical_info


@app.post("/me/medical-info", response_model=MedicalInfoResponse)
async def create_my_medical_info(
    info: MedicalInfoCreate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    return await create_medical_info(info, db, current_user_id)


@app.put("/medical_info/{user_id}", response_model=MedicalInfoResponse)
async def update_medical_info(
    user_id: UUID,
    updated_data: MedicalInfoUpdate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    if current_user_id != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBMedicalInfo).filter(DBMedicalInfo.user_id == user_id))
    medical_info = result.scalars().first()
    if not medical_info:
        return await create_medical_info(
            MedicalInfoCreate(
                user_id=user_id,
                bloodGroup=updated_data.bloodGroup or "",
                allergies=updated_data.allergies or "",
                chronicConditions=updated_data.chronicConditions or "",
                currentmedications=updated_data.currentmedications or "",
            ),
            db,
            current_user_id,
        )

    update_fields = updated_data.model_dump(exclude_unset=True)
    for field, value in update_fields.items():
        setattr(medical_info, field, value)

    await db.commit()
    await db.refresh(medical_info)
    return medical_info


@app.put("/me/medical-info", response_model=MedicalInfoResponse)
async def update_my_medical_info(
    updated_data: MedicalInfoUpdate,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    return await update_medical_info(current_user_id, updated_data, db, current_user_id)


@app.get("/medical_info/{user_id}", response_model=List[MedicalInfoResponse])
async def get_medical_info(
    user_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    if current_user_id != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBMedicalInfo).filter(DBMedicalInfo.user_id == user_id))
    return result.scalars().all()


@app.get("/me/medical-info", response_model=List[MedicalInfoResponse])
async def get_my_medical_info(
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    result = await db.execute(select(DBMedicalInfo).filter(DBMedicalInfo.user_id == current_user_id))
    return result.scalars().all()


@app.delete("/medical_info/{user_id}")
async def delete_medical_info(
    user_id: UUID,
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    if current_user_id != user_id:
        raise HTTPException(status_code=403, detail="FORBIDDEN")

    result = await db.execute(select(DBMedicalInfo).filter(DBMedicalInfo.user_id == user_id))
    medical_infos = result.scalars().all()
    if not medical_infos:
        raise HTTPException(status_code=404, detail="Medical info not found")
    for medical_info in medical_infos:
        await db.delete(medical_info)
    await db.commit()
    return {"detail": "Medical info deleted successfully"}


@app.delete("/me/medical-info")
async def delete_my_medical_info(
    db: AsyncSession = Depends(get_db),
    current_user_id: UUID = Depends(get_user_id_from_bearer),
):
    return await delete_medical_info(current_user_id, db, current_user_id)


# --- Auth routes from app.py ---
@app.post("/auth/google/register")
async def google_register(data: GoogleToken, db: AsyncSession = Depends(get_db)):
    try:
        google_user = id_token.verify_oauth2_token(
            data.idToken,
            google_requests.Request(),
            GOOGLE_CLIENT_ID,
        )
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid Google token")

    google_id = google_user["sub"]
    result = await db.execute(select(DBUser).filter(DBUser.oauth_id == google_id))
    user = result.scalars().first()

    if not user:
        user = DBUser(
            username=google_user.get("email"),
            email=google_user.get("email"),
            first_name=google_user.get("given_name", ""),
            last_name=google_user.get("family_name", ""),
            phone_number=f"G-{google_id[:8]}",
            oauth_provider="google",
            oauth_id=google_id,
        )
        db.add(user)
        await db.commit()
        await db.refresh(user)
    else:
        raise HTTPException(409, detail="This email is registered, please try logging in instead.")

    access_token = create_access_token(data={"sub": str(user.id)})
    refresh_token = create_refresh_token(data={"sub": str(user.id)})

    db.add(DBRefreshToken(user_id=user.id, token=refresh_token))
    await db.commit()

    return {
        "accessToken": access_token,
        "refreshToken": refresh_token,
        "tokenType": "bearer",
    }


@app.post("/auth/complete-profile")
async def complete_profile(
    data: CompleteProfileRequest,
    db: AsyncSession = Depends(get_db),
    authorization: str = Header(default=None),
):
    user_id = None

    if authorization and authorization.startswith("Bearer "):
        token = authorization.split(" ")[1]
        try:
            payload = decode_token(token)
            user_id = payload.get("sub")
        except jwt.ExpiredSignatureError:
            raise HTTPException(status_code=401, detail="TOKEN_EXPIRED")
        except Exception:
            raise HTTPException(status_code=401, detail="INVALID_TOKEN")

    if not user_id:
        raise HTTPException(status_code=401, detail="UNAUTHORIZED")

    result = await db.execute(select(DBUser).filter(DBUser.id == UUID(user_id)))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="USER_NOT_FOUND")

    username_check = await db.execute(
        select(DBUser).filter(
            DBUser.username == data.username,
            DBUser.id != UUID(user_id),
        )
    )
    if username_check.scalars().first():
        raise HTTPException(status_code=409, detail="USERNAME_TAKEN")

    phone_check = await db.execute(
        select(DBUser).filter(
            DBUser.phone_number == f"{data.dialCode}{data.phone}",
            DBUser.id != UUID(user_id),
        )
    )
    if phone_check.scalars().first():
        raise HTTPException(status_code=409, detail="PHONE_TAKEN")

    await require_verified_phone_otp(db, user.id, f"{data.dialCode}{data.phone}")

    user.username = data.username
    user.full_name = data.fullName
    user.age = data.age
    user.gender = data.gender
    user.phone_number = f"{data.dialCode}{data.phone}"
    user.password_hashed = pwd_context.hash(data.password)
    user.country = data.country
    user.state = data.state
    user.city = data.city
    user.area = data.area
    user.is_complete = True
    await db.commit()

    return {"detail": "PROFILE_COMPLETE"}


@app.post("/auth/refresh")
async def refresh_token(data: RefreshTokenRequest, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(DBRefreshToken).filter(
            DBRefreshToken.token == data.refreshToken,
            DBRefreshToken.revoked == False,
        )
    )
    stored = result.scalars().first()
    if not stored:
        raise HTTPException(status_code=401, detail="REFRESH_TOKEN_INVALID")

    try:
        payload = decode_token(data.refreshToken)
        user_id = payload.get("sub")

        stored.revoked = True
        await db.commit()

        new_access = create_access_token(data={"sub": user_id})
        new_refresh = create_refresh_token(data={"sub": user_id})
        db.add(DBRefreshToken(user_id=UUID(user_id), token=new_refresh))
        await db.commit()

        return {
            "accessToken": new_access,
            "refreshToken": new_refresh,
            "tokenType": "bearer",
        }

    except jwt.ExpiredSignatureError:
        payload = jwt.decode(
            data.refreshToken,
            SECRET_KEY,
            algorithms=[ALGORITHM],
            options={"verify_exp": False},
        )
        user_id = payload.get("sub")

        user_result = await db.execute(select(DBUser).filter(DBUser.id == UUID(user_id)))
        user = user_result.scalars().first()

        if not user:
            raise HTTPException(status_code=404, detail="USER_NOT_FOUND")

        if user.is_complete:
            stored.revoked = True
            await db.commit()
            raise HTTPException(status_code=401, detail="Token Expired")

        stored.revoked = True
        await db.commit()

        new_access = create_access_token(data={"sub": user_id})
        new_refresh = create_refresh_token(data={"sub": user_id})
        db.add(DBRefreshToken(user_id=UUID(user_id), token=new_refresh))
        await db.commit()

        return {
            "accessToken": new_access,
            "refreshToken": new_refresh,
            "tokenType": "bearer",
        }

    except Exception:
        raise HTTPException(status_code=401, detail="REFRESH_TOKEN_INVALID")


@app.post("/auth/google/login")
async def google_login(data: GoogleToken, db: AsyncSession = Depends(get_db)):
    try:
        google_user = id_token.verify_oauth2_token(
            data.idToken,
            google_requests.Request(),
            GOOGLE_CLIENT_ID,
        )
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid Google token")

    google_id = google_user["sub"]
    result = await db.execute(select(DBUser).filter(DBUser.oauth_id == google_id))
    user = result.scalars().first()

    if not user:
        raise HTTPException(status_code=404, detail="USER_NOT_FOUND")

    access_token = create_access_token(data={"sub": str(user.id)})
    refresh_token = create_refresh_token(data={"sub": str(user.id)})

    db.add(DBRefreshToken(user_id=user.id, token=refresh_token))
    await db.commit()

    return {
        "accessToken": access_token,
        "refreshToken": refresh_token,
        "tokenType": "bearer",
        "isComplete": user.is_complete,
    }


@app.post("/auth/validate")
async def validate_session(data: RefreshTokenRequest, db: AsyncSession = Depends(get_db)):
    result = await db.execute(
        select(DBRefreshToken).filter(
            DBRefreshToken.token == data.refreshToken,
            DBRefreshToken.revoked == False,
        )
    )
    stored = result.scalars().first()
    if not stored:
        raise HTTPException(status_code=401, detail="REFRESH_TOKEN_INVALID")

    try:
        payload = decode_token(data.refreshToken)
        user_id = payload.get("sub")
    except jwt.ExpiredSignatureError:
        stored.revoked = True
        await db.commit()
        raise HTTPException(status_code=401, detail="REFRESH_TOKEN_EXPIRED")
    except Exception:
        raise HTTPException(status_code=401, detail="REFRESH_TOKEN_INVALID")

    user_result = await db.execute(select(DBUser).filter(DBUser.id == UUID(user_id)))
    user = user_result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="USER_NOT_FOUND")

    stored.revoked = True
    await db.commit()

    new_access = create_access_token(data={"sub": user_id})
    new_refresh = create_refresh_token(data={"sub": user_id})
    db.add(DBRefreshToken(user_id=UUID(user_id), token=new_refresh))
    await db.commit()

    return {
        "accessToken": new_access,
        "refreshToken": new_refresh,
        "tokenType": "bearer",
        "isComplete": user.is_complete,
    }


@app.post("/auth/login")
async def email_login(data: EmailLoginRequest, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBUser).filter(DBUser.email == data.email))
    user = result.scalars().first()

    if not user:
        raise HTTPException(status_code=404, detail="USER_NOT_FOUND")

    if not user.password_hashed:
        raise HTTPException(status_code=400, detail="USE_GOOGLE_LOGIN")

    if not pwd_context.verify(data.password, user.password_hashed):
        raise HTTPException(status_code=401, detail="INVALID_PASSWORD")

    if not user.is_complete:
        raise HTTPException(status_code=403, detail="PROFILE_INCOMPLETE")

    access_token = create_access_token(data={"sub": str(user.id)})
    refresh_token = create_refresh_token(data={"sub": str(user.id)})

    db.add(DBRefreshToken(user_id=user.id, token=refresh_token))
    await db.commit()

    return {
        "accessToken": access_token,
        "refreshToken": refresh_token,
        "tokenType": "bearer",
    }
