import os
from typing import List
from contextlib import asynccontextmanager
from fastapi import FastAPI, Depends, HTTPException
from sqlalchemy import Column, Integer, String, select
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import DeclarativeBase
from pydantic import BaseModel, EmailStr
from dotenv import load_dotenv

load_dotenv()
SQLALCHEMY_DATABASE_URL = os.getenv("DATABASE_URL")

# Create the asynchronous engine and session maker which connects to the PostgreSQL database using the provided URL. The session maker is configured to use the AsyncSession class and to not expire objects on commit, allowing for easier access to data after committing changes.
engine = create_async_engine(SQLALCHEMY_DATABASE_URL)
SessionLocal = async_sessionmaker(bind=engine, class_=AsyncSession, expire_on_commit=False)

# Base class for all database models
class Base(DeclarativeBase):
    pass

# This creates a database model for the USERS table.
class DBUser(Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True, index=True)
    username = Column(String, unique=True, index=True)
    email = Column(String, unique=True, index=True)
    full_name = Column(String)
    
# This is a pydantic model for data validation and serialization. It defines the structure of the user data that will be accepted and returned by the API endpoints. The UserBase model includes the common fields for user creation and response, while UserCreate is used for incoming data when creating a new user, and UserResponse is used for outgoing data when returning user information from the API.
class UserBase(BaseModel):
    username: str
    email: EmailStr
    full_name: str
class UserCreate(UserBase): # sends the data to the database 
    pass
class UserResponse(UserBase): # retrives the data from the database and sends it back to the user.
    id: int
    class Config:
        from_attributes = True

# once when the server starts, it will create the tables in the PostgreSQL database if they do not already exist. The async context manager ensures that the database connection is properly managed during the lifespan of the application. yield means "now run the app normally".
@asynccontextmanager
async def lifespan(app: FastAPI):
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
    yield

# Create the FastAPI application with its specific lifespan.
app = FastAPI(title="User Management API", lifespan=lifespan)

# Every API route that needs DB access gets a fresh session injected automatically via Depends(get_db). It also auto-closes the session when the request is done.
async def get_db():
    async with SessionLocal() as session:
        yield session

# responsemodel = userresponse means that the response will be validated and serialized according to the UserResponse model. The create_user endpoint checks if a user with the same username already exists in the database. If it does, it raises an HTTP 400 error. If not, it creates a new user, adds it to the database, commits the transaction, and returns the newly created user.
@app.post("/users/", response_model=UserResponse)
async def create_user(user: UserCreate, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBUser).filter(DBUser.username == user.username))
    db_user = result.scalars().first()
    
    if db_user:
        raise HTTPException(status_code=400, detail="Username already registered")    
    new_user = DBUser(**user.model_dump())

    db.add(new_user)
    await db.commit()
    await db.refresh(new_user)
    return new_user

@app.get("/users/", response_model=List[UserResponse])
async def read_users(skip: int = 0, limit: int = 10, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(DBUser).offset(skip).limit(limit))
    users = result.scalars().all()
    return users
