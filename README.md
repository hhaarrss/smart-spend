# expense-tracker
Set-Content README.md "# Smart Expense Tracker

Family expense analytics platform with SMS parsing,
category-wise budgets, and real-time insights.

## Stack
- Backend: Python FastAPI + PostgreSQL
- Frontend: React + Tailwind + Recharts
- Mobile: Android (Kotlin)
- Data: SMS parsing + Account Aggregator (Setu)

## Setup
1. Clone the repo
2. Copy .env.example to .env and fill values
3. Run docker-compose up to start postgres + redis
4. cd backend and pip install -r requirements.txt
5. uvicorn main:app --reload

## Phases
- [ ] Phase 1 - Backend scaffold
- [ ] Phase 2 - SMS parser + transaction engine
- [ ] Phase 3 - React dashboard
- [ ] Phase 4 - Android app
- [ ] Phase 5 - Insights engine"