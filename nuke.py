import asyncio, os
from dotenv import load_dotenv
from sqlalchemy.ext.asyncio import create_async_engine
from sqlalchemy import text

load_dotenv()
engine = create_async_engine(os.getenv('DATABASE_URL'))

async def drop():
    async with engine.begin() as conn:
        await conn.execute(text('DROP TABLE IF EXISTS "Insurance_Info" CASCADE'))
        await conn.execute(text('DROP TABLE IF EXISTS "Medical_info" CASCADE'))
        await conn.execute(text('DROP TABLE IF EXISTS "Emergency_contacts" CASCADE'))
        await conn.execute(text('DROP TABLE IF EXISTS users CASCADE'))
    print('Done')

asyncio.run(drop())