import os
import jwt
import bcrypt
from uuid import UUID, uuid4
from dotenv import load_dotenv
from typing import List, Optional
from google.oauth2 import id_token
from contextlib import asynccontextmanager
from sqlalchemy.orm import DeclarativeBase
from datetime import datetime, timedelta, timezone
from fastapi import FastAPI, Depends, HTTPException
from pydantic import BaseModel, EmailStr, ConfigDict
from sqlalchemy.dialects.postgresql import UUID as SQLUUID
from google.auth.transport import requests as google_requests
from sqlalchemy import Column, Integer, String, select, ForeignKey
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker

load_dotenv()
SECRET_KEY = os.getenv("SECRET_KEY")
GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID")
SQLALCHEMY_DATABASE_URL = os.getenv("DATABASE_URL")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 15
REFRESH_TOKEN_EXPIRE_DAYS = 7

# ════════════════════════════════════════════════════════════════════════════════
# Database Connection Setup
# ════════════════════════════════════════════════════════════════════════════════
engine = create_async_engine(SQLALCHEMY_DATABASE_URL)
SessionLocal = async_sessionmaker(bind=engine, class_=AsyncSession, expire_on_commit=False)

class Base(DeclarativeBase):
    pass

# ════════════════════════════════════════════════════════════════════════════════
# SQLAlchemy Database Models (Tables)
# ════════════════════════════════════════════════════════════════════════════════
class DBUser(Base):
    __tablename__ = "users"
    user_id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4, unique=True, index=True)
    username = Column(String, unique=True, index=True, nullable=True)
    email = Column(String, unique=True, index=True, nullable=False)
    FirstName = Column(String, index=True, nullable=False)
    LastName = Column(String, index=True, nullable=False)
    FullName = Column(String)
    PhoneNumber = Column(String, unique=True, index=True, nullable=True)
    password_hashed = Column(String, nullable=True) 
    country = Column(String)
    city = Column(String)
    state = Column(String)
    area = Column(String)
    oauth_provider = Column(String, nullable=True)
    oauth_id = Column(String, unique=True, nullable=True)

class DBEmergencyContact(Base):
    __tablename__ = "Emergency_contacts"
    contact_id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id = Column(SQLUUID(as_uuid=True), ForeignKey("users.user_id"), index=True)
    contact_number = Column(String, unique=True, index=True)
    relationship = Column(String, index=True)
    priority_order = Column(Integer, index=True)

class DBMedicalInfo(Base):
    __tablename__ = "Medical_info"
    medical_id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id = Column(SQLUUID(as_uuid=True), ForeignKey("users.user_id"), unique=True)
    bloodGroup = Column(String, index=True)
    allergies = Column(String, index=True)
    chronicConditions = Column(String, index=True)
    currentmedications = Column(String, index=True)

class DBInsuranceInfo(Base):
    __tablename__ = "Insurance_Info"
    insurance_id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id = Column(SQLUUID(as_uuid=True), ForeignKey("users.user_id"), unique=True, index=True)
    insurance_type = Column(String, index=True)
    insurance_provider = Column(String, index=True)
    insurance_policy_number = Column(String, unique=True, index=True)

# ════════════════════════════════════════════════════════════════════════════════
# Pydantic Validation Models
# ════════════════════════════════════════════════════════════════════════════════
class UserBase(BaseModel):   
    username: str
    email: EmailStr
    FirstName: str
    LastName: str
    FullName: str
    PhoneNumber: str
    country: str
    city: str
    state: str
    area: str
class UserCreate(UserBase):  
    password: str 
class UserResponse(UserBase): 
    user_id: UUID
    model_config = ConfigDict(from_attributes=True)
class UserUpdate(BaseModel):
    username : Optional[str] = None 
    FirstName : Optional[str] = None
    LastName : Optional[str] = None
    FullName : Optional[str] = None
    PhoneNumber : Optional[str] = None
    country : Optional[str] = None
    city : Optional[str] = None
    state : Optional[str] = None
    area : Optional[str] = None


class DBEmergencyContactBase(BaseModel):
    user_id: UUID
    contact_number: str
    relationship: str
    priority_order: int
class EmergencyContactCreate(DBEmergencyContactBase):
    pass 
class EmergencyContactResponse(DBEmergencyContactBase):
    contact_id: UUID
    model_config = ConfigDict(from_attributes=True)


class DBMedicalInfoBase(BaseModel):
    bloodGroup: str
    allergies: str
    chronicConditions: str
    currentmedications: str
class MedicalInfoCreate(DBMedicalInfoBase):
    user_id: UUID
class MedicalInfoResponse(DBMedicalInfoBase):
    medical_id: UUID 
    user_id: UUID 
    model_config = ConfigDict(from_attributes=True)


class DBInsuranceInfoBase(BaseModel):
    user_id: UUID
    insurance_type: str
    insurance_provider: str
    insurance_policy_number: str
class InsuranceInfoCreate(DBInsuranceInfoBase):
    pass 
class InsuranceInfoResponse(DBInsuranceInfoBase):
    insurance_id: UUID 
    model_config = ConfigDict(from_attributes=True)


class GoogleToken(BaseModel):
    token: str

# ════════════════════════════════════════════════════════════════════════════════
# App Lifespan and Dependencies
# ════════════════════════════════════════════════════════════════════════════════
@asynccontextmanager
async def lifespan(app: FastAPI):
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield

app = FastAPI(lifespan=lifespan)

async def get_db():
    async with SessionLocal() as session:
        yield session

# ════════════════════════════════════════════════════════════════════════════════
# Helper Functions 
# ════════════════════════════════════════════════════════════════════════════════
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

# ════════════════════════════════════════════════════════════════════════════════
# API Endpoints 
# ════════════════════════════════════════════════════════════════════════════════

"""
LOL CONCEPTS:
-- Note: Always use scalars().all() for queries that return multiple records (lists of medical info, insurance info, or emergency contacts for one single user) and scalars().first() for queries that return a single record (fetching a user by primary key).

-- Note : Without async, your server handles one request at a time. If one request is waiting for the database, every other request is stuck waiting too. Async lets the server say "while I'm waiting for the DB, let me handle other requests" and come back when the DB responds.
"""

# ════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# User endpoints
@app.post("/users/", response_model=UserResponse) 
async def create_user(user: UserCreate, db: AsyncSession = Depends(get_db)): 
    
    result = await db.execute(select(DBUser).filter(DBUser.username == user.username))  
    existing_user = result.scalars().first() 
    if existing_user: 
        raise HTTPException(status_code=400, detail="Username already registered") 

    result2 = await db.execute(select(DBUser).filter(DBUser.email == user.email))
    existing_email = result2.scalars().first()
    if existing_email: 
        raise HTTPException(status_code=400, detail="Email already registered") 

    hashed_pwd = bcrypt.hashpw(user.password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")
    user_data = user.model_dump(exclude={"password"})
    new_user = DBUser(**user_data, password_hashed=hashed_pwd)
    
    db.add(new_user)
    await db.commit()
    await db.refresh(new_user)
    return new_user

@app.get("/users/{user_id}", response_model=UserResponse)
async def read_user(user_id: UUID, db: AsyncSession = Depends(get_db)):

    result = await db.execute(select(DBUser).filter(DBUser.user_id == user_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user

@app.put("/users/{user_id}", response_model=UserResponse)
async def update_user(user_id: UUID, updated_data: UserUpdate, db: AsyncSession = Depends(get_db)):
    
    result = await db.execute(select(DBUser).filter(DBUser.user_id == user_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    # check if the username that is to be updated is already taken or not.
    if updated_data.username and updated_data.username != user.username:
        result2 = await db.execute(select(DBUser).filter(DBUser.username == updated_data.username))
        if result2.scalars().first():
            raise HTTPException(status_code=400, detail="Username already taken")
    
    # do the same for the phone number, check if the phone number that is to be updated is already taken or not.
    if updated_data.PhoneNumber and updated_data.PhoneNumber != user.PhoneNumber:
        result3 = await db.execute(select(DBUser).filter(DBUser.PhoneNumber == updated_data.PhoneNumber))
        if result3.scalars().first():
            raise HTTPException(status_code=400, detail="Phone number already registered")

    # exclude_unset=True does the job of patch method, it only updates the fields that the user wants to and it gets sent in the request body and then gets updated back in the server and remaining fields remain unchanged.
    # setarribute is a built in function in python that takes 3 arguments, the object, the field name and the value and it updates the field of the object with the value.

    update_fields = updated_data.model_dump(exclude_unset=True) 
    for field, value in update_fields.items(): 
        setattr(user, field, value) 
    await db.commit()
    await db.refresh(user)
    return user

@app.delete("/users/{user_id}")
async def delete_user(user_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBUser).filter(DBUser.user_id == user_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    await db.delete(user)
    await db.commit()
    return {"detail" : "User deleted successfully"}

# ════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# Emergency contact endpoints 
@app.post("/emergency_contacts/", response_model=EmergencyContactResponse)
async def create_emergency_contact(contact: EmergencyContactCreate, db: AsyncSession = Depends(get_db)):    
    result = await db.execute(select(DBUser).filter(DBUser.user_id == contact.user_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    # allowing only 3 emergency contacts per user.
    result2 = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.user_id == contact.user_id))
    existing_result = result2.scalars().all()
    if len(existing_result) >= 3:
        raise HTTPException(status_code=400, detail="Maximum of 3 emergency contacts allowed per user")

    # checking if the phone number is unique across the database.
    result3 = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.contact_number == contact.contact_number))
    existing_result3 = result3.scalars().first()
    if existing_result3:
        raise HTTPException(status_code=400, detail="Contact number already exists in the database")

    new_contact = DBEmergencyContact(**contact.model_dump()) 
    db.add(new_contact)
    await db.commit()
    await db.refresh(new_contact)
    return new_contact 

@app.get("/emergency_contacts/{user_id}", response_model=List[EmergencyContactResponse])
async def get_emergencycontacts(user_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.user_id == user_id).order_by(DBEmergencyContact.priority_order))
    contacts = result.scalars().all()
    if not contacts:
        raise HTTPException(status_code=404, detail="Emergency contacts not found")
    return contacts

# deleteing ALL the emergency contacts of a user through the user_id.
@app.delete("/emergency_contacts/{user_id}")
async def delete_emergency_contacts(user_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.user_id == user_id))
    contact = result.scalars().all()
    if not contact:
        raise HTTPException(status_code=404, detail="Emergency contact not found")
    for c in contact:
        await db.delete(c)
    await db.commit()
    return {"detail" : "All Emergency contacts deleted successfully"}

# deleteing a SINGLE emergency contact through a contact_id through a user_id. 
@app.delete("/emergency_contacts/contact_id/{contact_id}")
async def delete_emergency_contact_by_id(contact_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.contact_id == contact_id))
    contact = result.scalars().first()
    if not contact:
        raise HTTPException(status_code=404, detail="Emergency contact not found")
    await db.delete(contact)
    await db.commit()
    return {"detail" : "Emergency contact deleted successfully"}


# ════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# insurance info endpoints
@app.post("/insurance_info/", response_model=InsuranceInfoResponse)
async def create_insurance_info(info: InsuranceInfoCreate, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBUser).filter(DBUser.user_id == info.user_id))
    user = result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="user not found")
    
    new_insurance_info = DBInsuranceInfo(**info.model_dump())
    db.add(new_insurance_info)
    await db.commit()
    await db.refresh(new_insurance_info)
    return new_insurance_info 

# getting the info of ALL the insurance of a user through the user id.
@app.get("/insurance_info/{user_id}", response_model=List[InsuranceInfoResponse])
async def get_insurance_info(user_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.user_id == user_id))
    infos = result.scalars().all()
    return infos

# getting the info of a SINGLE insurance of a user
@app.get("/insurance_info/insurance_id/{insurance_id}", response_model=InsuranceInfoResponse)
async def get_insurance_info_by_id(insurance_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.insurance_id == insurance_id))
    info = result.scalars().first()
    if not info:
        raise HTTPException(status_code=404, detail="Insurance info not found")
    return info

# deletes ALL the insurances of a user
@app.delete("/insurance_info/{user_id}")
async def delete_insurance_info(user_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.user_id == user_id))
    info = result.scalars().all()
    if not info:
        raise HTTPException(status_code=404, detail="Insurance info not found")
    for i in info:
        await db.delete(i)
    await db.commit()
    return {"detail" : "Insurance info deleted successfully"}

# deletes SINGLE insurance info through insurance id 
@app.delete("/insurance_info/insurance_id/{insurance_id}")
async def delete_insurance_info_by_id(insurance_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.insurance_id == insurance_id))
    info = result.scalars().first()
    if not info:
        raise HTTPException(status_code=404, detail="Insurance info not found")
    await db.delete(info)
    await db.commit()
    return {"detail" : "Insurance info deleted successfully"}

# ════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# medical info endpoints
@app.post("/medical_info/", response_model=MedicalInfoResponse)
async def create_medical_info(info: MedicalInfoCreate, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBUser).filter(DBUser.user_id == info.user_id))
    if not result.scalars().first():
        raise HTTPException(status_code=404, detail="User not found")
    
    new_medical_info = DBMedicalInfo(**info.model_dump())
    db.add(new_medical_info)
    await db.commit()
    await db.refresh(new_medical_info)
    return new_medical_info

@app.get("/medical_info/{user_id}", response_model=List[MedicalInfoResponse])
async def get_medical_info(user_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBMedicalInfo).filter(DBMedicalInfo.user_id == user_id))
    medical = result.scalars().all()
    return medical 

@app.delete("/medical_info/{user_id}")
async def delete_medical_info(user_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBMedicalInfo).filter(DBMedicalInfo.user_id == user_id))
    medical = result.scalars().all()
    if not medical:
        raise HTTPException(status_code=404, detail="Medical info not found")
    for m in medical:
        await db.delete(m)
    await db.commit()
    return {"detail" : "Medical info deleted successfully"}


# authentication using oauth 2.0 with google
@app.post("/auth/google/register")
async def google_auth(data: GoogleToken, db: AsyncSession = Depends(get_db)):
    try:
        google_user = id_token.verify_oauth2_token(
            data.token,
            google_requests.Request(),
            GOOGLE_CLIENT_ID
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
            FirstName=google_user.get("given_name", ""),
            LastName=google_user.get("family_name", ""),
            FullName=google_user.get("name", ""),
            PhoneNumber=f"G-{google_id[:8]}",
            country="",
            city="",
            state="",
            area="",
            oauth_provider="google",
            oauth_id=google_id
        )
        db.add(user)
        await db.commit()
        await db.refresh(user)
        
    access_token = create_access_token(data={"sub": str(user.user_id)})
    refresh_token = create_refresh_token(data={"sub": str(user.user_id)})
    return {"accessToken": access_token, "refreshToken": refresh_token, "tokenType": "bearer"}