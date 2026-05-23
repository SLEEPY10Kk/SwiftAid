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
from sqlalchemy import Column, Integer, String, select, ForeignKey, Float
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker

import httpx
from twilio.rest import Client 
from sendgrid import SendGridAPIClient
from sendgrid.helpers.mail import Mail 

load_dotenv()
SECRET_KEY = os.getenv("SECRET_KEY")
GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID")
SQLALCHEMY_DATABASE_URL = os.getenv("DATABASE_URL")
ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 15
REFRESH_TOKEN_EXPIRE_DAYS = 7

TWILIO_ACCOUNT_SID = os.getenv("TWILIO_ACCOUNT_SID")
TWILIO_AUTH_TOKEN = os.getenv("TWILIO_AUTH_TOKEN")
TWILIO_PHONE_NUMBER = os.getenv("TWILIO_PHONE_NUMBER")
TWILIO_WHATSAPP_NUMBER = os.getenv("TWILIO_WHATSAPP_NUMBER")
SENDGRID_API_KEY = os.getenv("SENDGRID_API_KEY")
HOSPITAL_EMAIL = os.getenv("HOSPITAL_EMAIL")
FROM_EMAIL = os.getenv("FROM_EMAIL")

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

class DBcrashrecords(Base):
    __tablename__ = "crash_records"
    crash_id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id = Column(SQLUUID(as_uuid=True), ForeignKey("users.user_id"), index=True)
    g_force = Column(String, index=True)
    severity_score = Column(String, index=True)
    speed_at_impact = Column(String, index=True)
    latitude = Column(Float, index=True)
    longitude = Column(Float, index=True)
    hospital_name = Column(String, index=True)
    dispatch_hospital_email = Column(String, index=True)
    dispatch_hospital_number = Column(String, index=True)
    hospital_latitude = Column(Float, index=True)
    hospital_longitude = Column(Float, index=True)
    hospital_distance_km = Column(Float, index=True)    
    triggered_at = Column(String, default=lambda: datetime.now(timezone.utc).isoformat(), index=True)
    status = Column(String, index=True)

class DBPoliceRecords(Base):
    __tablename__ = "police_records"

    record_id = Column(SQLUUID(as_uuid=True), primary_key=True, default=uuid4)
    crash_id = Column(SQLUUID(as_uuid=True), ForeignKey("crash_records.crash_id"), index=True)
    user_id = Column(SQLUUID(as_uuid=True), ForeignKey("users.user_id"), index=True)

    station_address = Column(String, index=True)
    station_state = Column(String, index=True)
    station_district = Column(String, index=True)

    station_name = Column(String, index=True)
    station_code = Column(String, index=True)
    officer_name = Column(String, index=True)
    officer_contact = Column(String, index=True)
    
    latitude = Column(Float, index=True)
    longitude = Column(Float, index=True)

    call_type   = Column(String, index=True)  # self called, called for other and .
    status = Column(String, index=True) # active, pending and resolved.

    created_at = Column(String, default=lambda: datetime.now(timezone.utc).isoformat())
    responded_at = Column(String, index=True)
    resolved_at = Column(String, index=True)

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
class EmergencyContactUpdate(BaseModel):
    contact_number : Optional[str] = None 
    relationship : Optional[str] = None 
    priority_order : Optional[int] = None 

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
class MedicalInfoUpdate(BaseModel):
    bloodGroup : Optional[str] = None
    allergies : Optional[str] = None
    chronicConditions : Optional[str] = None
    currentmedications : Optional[str] = None

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
class InsuranceInfoUpdate(BaseModel):
    insurance_type : Optional[str] = None 
    insurance_provider : Optional[str] = None
    insurance_policy_number : Optional[str] = None


class PoliceRecordCreate(BaseModel):
    crash_id: UUID
    user_id: UUID
    latitude: float
    longitude: float
    call_type: str     
class PoliceRecordResponse(BaseModel): 
    record_id: UUID
    crash_id: UUID
    user_id: UUID
    call_type: str
    status: str
    latitude: float
    longitude: float
    officer_name: Optional[str] = None
    station_code: Optional[str] = None
    station_name: Optional[str] = None
    created_at: str
    responded_at: Optional[str] = None
    resolved_at: Optional[str] = None
    model_config = ConfigDict(from_attributes=True)
class PoliceRecordUpdate(BaseModel):
    status: Optional[str] = None                    
    officer_name: Optional[str] = None
    station_code: Optional[str] = None
    station_name: Optional[str] = None
    
class CrashTriggerPayload(BaseModel):
    user_id : UUID 
    latitude : float
    longitude : float
    g_force : str
    severity_score : str
    speed_at_impact : str

class GoogleToken(BaseModel):
    token: str

# ════════════════════════════════════════════════════════════════════════════════
# App Initialization and Session Management
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

def direct_sms(to: str, message: str):
    try: 
        client = Client(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN)
        client.message.create(
            body = message,
            from_ = TWILIO_PHONE_NUMBER,
            to = to
        )
        print(f"SMS sent to {to}")
    except Exception as e:
        print(f"Failed to send SMS to {to} : {e}")
    
def whatsapp_sms(to: str, message: str):
    client = Client(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN)
    
    sent_message = client.messages.create(
        body=message,
        from_="whatsapp:+14155238886",   # hardcode the sandbox number directly
        to=f"whatsapp:{to}"
    )
    print(f"WhatsApp sent to {to} — ID: {sent_message.sid}")


def hospital_email(user, medical, insurance, crash, maps_links: str, hospital_email: str):
    recipient  = hospital_email

    email_body = f"""
ROADSOS : EMERGENCY ALERT 
INCOMING PATIENT : URGENT ATTENTION REQUIRED

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CRITICAL MEDICAL INFORMATION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Blood Group         : {medical.bloodGroup}
Allergies           : {medical.allergies}
Chronic Conditions  : {medical.chronicConditions}
Current Medications : {medical.currentmedications}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CRASH DETAILS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Time of Crash       : {crash.triggered_at}
Crash Location      : {maps_links}
G-Force at Impact   : {crash.g_force} g
Severity Score      : {crash.severity_score}
Speed at Impact     : {crash.speed_at_impact} km/h


This message was generated and sent automatically by ROADSOS APP
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""
    email = Mail(
        from_email = FROM_EMAIL,
        to_email = recipient,
        subject=f"EMERGENCY — Incoming Patient: {user.FullName} | RoadSOS",
        plain_text_content=email_body
    )
    try: 
        sendgrid_client = SendGridAPIClient(SENDGRID_API_KEY)
        sendgrid_client.send(email) 
        print(f"Email sent to hospital at {recipient}")
    except Exception as e:
        print(f"Failed to send email to hospital at {recipient} : {e}")

def notify_contacts(to: str, message: str):
    print(f"\nNotifying {to}...")
    
    try:
        whatsapp_sms(to, message)
    except Exception as whatsapp_error:
        print(f"WhatsApp to {to} failed: {whatsapp_error}")
    
    print(f"Finished notifying {to}\n")


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
    # setarribute is a built in function in python that takes 3 arguments, the object, the field name and the value and it updates the fields of the object with the value.

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

@app.get("/emergency_contacts/{user_id}/", response_model=List[EmergencyContactResponse])
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

@app.put("/emergency_contacts/{user_id}/{contact_id}", response_model=EmergencyContactResponse)
async def update_emergency_contacts(user_id: UUID, contact_id: UUID, updated_data: EmergencyContactUpdate ,db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.user_id == user_id, DBEmergencyContact.contact_id == contact_id))
    contacts = result.scalars().first()
    if not contacts: 
        raise HTTPException(status_code=404, detail="Emergency Contacts not found")
    
    if updated_data.contact_number and updated_data.contact_number != contacts.contact_number:
        result2 = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.user_id == user_id, DBEmergencyContact.contact_number == updated_data.contact_number))
        
        existing_contact = result2.scalars().first()
        if existing_contact:
            raise HTTPException(status_code=400, detail="Contact Number already exists")
    
    if updated_data.priority_order and updated_data.priority_order != contacts.priority_order:
        result2 = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.user_id == user_id, DBEmergencyContact.priority_order == updated_data.priority_order))
        existing_priority = result2.scalars().first()
        if existing_priority:
            raise HTTPException(status_code=400, detail="Priority already has been set")

    updated_fields = updated_data.model_dump(exclude_unset=True)
    for field, value in updated_fields.items():
        setattr(contacts, field, value)
    await db.commit()
    await db.refresh(contacts)
    return contacts

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

# update the insurances of the user
@app.put("/insurance_info/{user_id}/{insurance_id}", response_model=InsuranceInfoResponse)
async def update_insurance_info(user_id:UUID, insurance_id:UUID, updated_data : InsuranceInfoUpdate, db:AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.user_id == user_id, DBInsuranceInfo.insurance_id == insurance_id))
    insurance = result.scalars().first()
    if not insurance:
        raise HTTPException(status_code=400, detail="Insurance detail not found")

    if updated_data.insurance_policy_number and updated_data.insurance_policy_number != insurance.insurance_policy_number: 
        result2 = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.user_id == user_id, DBInsuranceInfo.insurance_policy_number == updated_data.insurance_policy_number))
        existing_policy_number = result2.scalars().first()
        if existing_policy_number:
            raise HTTPException(status_code=400, detail="Insurance Policy Number already exists")
    
    updated_fields = updated_data.model_dump(exclude_unset=True)
    for field, value in updated_fields.items():
        setattr(insurance, field, value)
    await db.commit()
    await db.refresh(insurance)
    return insurance

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

@app.put("/medical_info/{user_id}", response_model=MedicalInfoResponse)
async def update_medical_info(user_id:UUID, updated_data:MedicalInfoUpdate, db:AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBMedicalInfo).filter(DBMedicalInfo.user_id == user_id))
    medical = result.scalars().first()
    if not medical: 
        raise HTTPException(status_code=400, detail="Medical details not fOund")
    
    updated_fields = updated_data.model_dump(exclude_unset=True)
    for field, value in updated_fields.items():
        setattr(medical, field, value)
    await db.commit()
    await db.refresh(medical)
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


# ════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════════
# police record endpoints 
@app.post("/police_records/", response_model=PoliceRecordResponse)
async def create_police_record(record: PoliceRecordCreate, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBPoliceRecords).filter(DBPoliceRecords.crash_id == record.crash_id))
    if result.scalars().first():
        raise HTTPException(status_code=400, detail="Police record for this crash already exists")
    
    call_types = ["app_called", "self_called", "other_called"]
    if record.call_type not in call_types:
        raise HTTPException(status_code=400, detail= f"call type must be {call_types}" )
    
    crash_result = await db.execute(select(DBcrashrecords).filter(DBcrashrecords.crash_id == record.crash_id))
    if not crash_result.scalars().first():
        raise HTTPException(status_code=404, detail = "Crash record Not Found")
    
    new_record = DBPoliceRecords(**record.model_dump())
    db.add(new_record)
    await db.commit()
    await db.refresh(new_record)
    return new_record

@app.get("/police_records/pending", response_model=List[PoliceRecordResponse])
async def get_pending_records(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBPoliceRecords).filter(DBPoliceRecords.status == "pending"))
    records = result.scalars().all()

    if not records:
        raise HTTPException(status_code=404, detail = "No Pending Cases Found")
    return records

@app.get("/police_records/resolved", response_model=List[PoliceRecordResponse])
async def get_resolved_records(db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBPoliceRecords).filter(DBPoliceRecords.status == "resolved"))
    records = result.scalars().all()
    if not records:
        raise HTTPException(status_code=404, detail = "No Resolved cases found")
    return records  

@app.put("/police_records/{record_id}", response_model=PoliceRecordResponse)
async def update_police_record(record_id: UUID, updated_data: PoliceRecordUpdate, db: AsyncSession = Depends(get_db)):

    result = await db.execute(select(DBPoliceRecords).filter(DBPoliceRecords.record_id == record_id))
    record = result.scalars().first()
    if not record:
        raise HTTPException(status_code = 404, detail="Police record not found")

    if updated_data.status:
        status_types = ["pending","active","resolved"]
        if updated_data.status not in status_types:
            raise HTTPException(status_code=400, detail=f"Status must be one of these {status_types}")

    if updated_data.status == "active" and record.status == "pending":
        record.responded_at = datetime.now(timezone.utc).isoformat()
    if updated_data.status == "resolved" and record.status != "resolved":
        record.resolved_at = datetime.now(timezone.utc).isoformat()

    updated_fields = updated_data.model_dump(exclude_unset=True)
    for field, value in updated_fields.items():
        setattr(record, field, value)

    await db.commit()
    await db.refresh(record)
    return record

@app.delete("/police_records/{record_id}")
async def delete_police_records(record_id: UUID, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBPoliceRecords).filter(DBPoliceRecords.record_id == record_id))
    record = result.scalars().first()
    if not record:
        raise HTTPException(status_code=404, detail="Police record not found")
    await db.delete(record)
    await db.commit()
    return {"detail" : "Police record deleted successfully"}

# ════════════════════════════════════════════════════════════════════════════════════════════════
# COMPLETE emergency trigger endpoint
@app.post("/crash/trigger/{user_id}")
async def emergency_trigger(user_id: UUID, payload: CrashTriggerPayload, db: AsyncSession = Depends(get_db)):

    user_result = await db.execute(select(DBUser).filter(DBUser.user_id == user_id))
    user = user_result.scalars().first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    contact_result = await db.execute(select(DBEmergencyContact).filter(DBEmergencyContact.user_id == user_id).order_by(DBEmergencyContact.priority_order))
    contacts = contact_result.scalars().all()
    if not contacts:
        raise HTTPException(status_code=404, detail="Emergency contacts not found")

    medical_result = await db.execute(select(DBMedicalInfo).filter(DBMedicalInfo.user_id == user_id))
    medical_info = medical_result.scalars().first()
    if not medical_info:
        raise HTTPException(status_code=404, detail="Medical info not found")

    insurance_result = await db.execute(select(DBInsuranceInfo).filter(DBInsuranceInfo.user_id == user_id))
    insurance_info = insurance_result.scalars().first()

    h_name     = "Unknown"
    h_email    = HOSPITAL_EMAIL    
    h_number   = ""
    h_distance = 0.0
    h_lat      = None
    h_lon      = None

    try:
        async with httpx.AsyncClient(timeout=10) as client:
            response = await client.get(
                "http://localhost:8001/crash/pois", 
                params={"lat": payload.latitude, "lon": payload.longitude, "radius": 10000}
            )
            poi_data = response.json()
            places = poi_data.get("places", [])
            hospitals = [
                p for p in places
                if "hospital" in str(p.get("amenity", "")).lower()
                or "hospital" in str(p.get("type", "")).lower()
                or "hospital" in str(p.get("category", "")).lower()
                or "hospital" in str(p.get("name", "")).lower()
            ]
            if hospitals:
                nearest = hospitals[0]
                h_name     = nearest.get("name", "Unknown")
                h_distance = nearest.get("distance_m", 0) / 1000
                h_lat      = nearest.get("lat")
                h_lon      = nearest.get("lon")
    except Exception as e:
        print(f"POI API request failed: {e}")
  
    crash_record = DBcrashrecords(
        user_id=payload.user_id,
        g_force=payload.g_force,
        severity_score=payload.severity_score,
        speed_at_impact=payload.speed_at_impact,
        latitude=payload.latitude,
        longitude=payload.longitude,
        hospital_name=h_name,                
        dispatch_hospital_email=h_email,
        dispatch_hospital_number=h_number,
        hospital_distance_km=str(h_distance),
        hospital_latitude=h_lat,
        hospital_longitude=h_lon,
        status="active"
    )
    db.add(crash_record)
    await db.commit()
    await db.refresh(crash_record)

    maps_link = f"https://maps.google.com/?q={payload.latitude},{payload.longitude}"

    contact_message = (
        f"ROADSOS EMERGENCY ALERT\n"
        f"{user.FullName} has been in a road accident.\n"
        f"Location: {maps_link}\n"
        f"Nearest Hospital: {h_name} ({h_distance:.2f} km away)\n"
        f"Please contact them immediately.\n\n"
        f"This message was generated automatically by ROADSOS APP"
    )

    for contact in contacts:
        notify_contacts(
            to=contact.contact_number,
            message=contact_message
        )

    hospital_email(
        user=user,
        medical=medical_info,
        insurance=insurance_info,
        crash=crash_record,
        maps_links=maps_link,
        hospital_email=h_email
    )

    police_record = DBPoliceRecords(
        crash_id=crash_record.crash_id,
        user_id=payload.user_id,
        latitude=payload.latitude,
        longitude=payload.longitude,
        call_type="app_called",
        status="pending"
    )
    db.add(police_record)
    await db.commit()
    await db.refresh(police_record)

    return {
        "status": "emergency triggered",
        "crash_id": str(crash_record.crash_id),
        "police_record_id": str(police_record.record_id),
        "victim_location": maps_link,
        "nearest_hospital": h_name,
        "hospital_distance_km": round(h_distance, 2),
        "contacts_notified": len(contacts)
    }


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