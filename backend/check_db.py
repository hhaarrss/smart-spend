import asyncio
from sqlalchemy.ext.asyncio import create_async_engine
from sqlalchemy import text
async def main():
    engine = create_async_engine('postgresql://neondb_owner:n8.dQk2r6LOMxXjV7fP5R@ep-floral-grass-a5s8r23e.us-east-2.aws.neon.tech/neondb?sslmode=require', echo=False)
    async with engine.connect() as conn:
        result = await conn.execute(text('SELECT amount, merchant_raw, created_at FROM transactions ORDER BY created_at DESC LIMIT 5'))
        for row in result:
            print(row)
    await engine.dispose()
asyncio.run(main())
