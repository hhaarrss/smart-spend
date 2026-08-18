# Smart Expense Tracker — Test Scenarios

> **Cloud API Base URL:** `https://expense-tracker-pk4d.onrender.com`  
> **Local API Base URL:** `http://localhost:8000`  
> **Swagger UI:** `https://expense-tracker-pk4d.onrender.com/docs` or `http://localhost:8000/docs`  
> **All JSON payloads are copy-paste ready for Swagger UI.**

---

## Table of Contents

1. [SMS Ingestion Test Cases (20 cases)](#1-sms-ingestion-test-cases)
2. [Budget Limit Test Cases (5 cases)](#2-budget-limit-test-cases)
3. [Manual Transaction Test Cases (10 cases)](#3-manual-transaction-test-cases)
4. [How to Run These Tests](#4-how-to-run-these-tests)

---

## 1. SMS Ingestion Test Cases

**Endpoint:** `POST /transactions/ingest-sms`

All SMS messages use realistic Indian banking formats with actual sender IDs.

---

### ICICI Bank (5 cases)

#### Case 1 — UPI Debit (Food delivery)

```json
{
  "raw_sms": "ICICI Bank Acct XX1234 debited for Rs 450.00 on 27-May-26; Swiggy credited. UPI:412356789012. Call 18002662 for dispute.",
  "sender": "AD-ICICIB"
}
```

| Field | Expected Value |
|---|---|
| amount | 450.00 |
| type | debit |
| category | Food |
| merchant | Swiggy |

---

#### Case 2 — UPI Credit (Received money)

```json
{
  "raw_sms": "ICICI Bank Acct XX1234 credited with Rs 2500.00 on 27-May-26; Rajesh Kumar debited. UPI:598712345678. If not done by you call 18002662.",
  "sender": "AD-ICICIB"
}
```

| Field | Expected Value |
|---|---|
| amount | 2500.00 |
| type | credit |
| category | Other |
| merchant | Rajesh Kumar |

---

#### Case 3 — Card Debit (Dining)

```json
{
  "raw_sms": "ICICI Bank Credit Card XX9087 has been used for a transaction of Rs 1850.00 on 27-May-26 at BARBEQUE NATION. If not done by you call 18002662.",
  "sender": "AD-ICICIB"
}
```

| Field | Expected Value |
|---|---|
| amount | 1850.00 |
| type | debit |
| category | Food |
| merchant | BARBEQUE NATION |

---

#### Case 4 — Large Amount UPI Debit (Rent)

```json
{
  "raw_sms": "ICICI Bank Acct XX1234 debited for Rs 50000.00 on 27-May-26; RAVI PROPERTIES credited. UPI:734561238901. Call 18002662 for dispute.",
  "sender": "AD-ICICIB"
}
```

| Field | Expected Value |
|---|---|
| amount | 50000.00 |
| type | debit |
| category | Other |
| merchant | RAVI PROPERTIES |

---

#### Case 5 — Small Amount UPI Debit (Tea stall)

```json
{
  "raw_sms": "ICICI Bank Acct XX1234 debited for Rs 1.00 on 27-May-26; TEA POST credited. UPI:100023456789. Call 18002662 for dispute.",
  "sender": "AD-ICICIB"
}
```

| Field | Expected Value |
|---|---|
| amount | 1.00 |
| type | debit |
| category | Food |
| merchant | TEA POST |

---

### HDFC Bank (4 cases)

#### Case 6 — Debit with Merchant (Groceries)

```json
{
  "raw_sms": "HDFC Bank: Rs.1200.00 debited from A/c XX5678 on 27-05-26. Info: DMART SUPERMARKET. Avl Bal: Rs.15340.00",
  "sender": "AD-HDFCBK"
}
```

| Field | Expected Value |
|---|---|
| amount | 1200.00 |
| type | debit |
| category | Groceries |
| merchant | DMART SUPERMARKET |

---

#### Case 7 — Credit Transaction (Salary)

```json
{
  "raw_sms": "HDFC Bank: Rs.45000.00 credited to A/c XX5678 on 27-05-26. Info: NEFT-TATA CONSULTANCY. Avl Bal: Rs.60340.00",
  "sender": "AD-HDFCBK"
}
```

| Field | Expected Value |
|---|---|
| amount | 45000.00 |
| type | credit |
| category | Other |
| merchant | NEFT-TATA CONSULTANCY |

---

#### Case 8 — ATM Withdrawal

```json
{
  "raw_sms": "HDFC Bank: Rs.5000.00 debited from A/c XX5678 on 27-05-26. Info: ATM WDL ANDHERI WEST. Avl Bal: Rs.10340.00",
  "sender": "AD-HDFCBK"
}
```

| Field | Expected Value |
|---|---|
| amount | 5000.00 |
| type | debit |
| category | Other |
| merchant | ATM WDL ANDHERI WEST |

---

#### Case 9 — Online Shopping Debit

```json
{
  "raw_sms": "HDFC Bank: Rs.3499.00 debited from A/c XX5678 on 27-05-26. Info: AMAZON PAY INDIA. Avl Bal: Rs.6841.00",
  "sender": "AD-HDFCBK"
}
```

| Field | Expected Value |
|---|---|
| amount | 3499.00 |
| type | debit |
| category | Shopping |
| merchant | AMAZON PAY INDIA |

---

### SBI Bank (2 cases)

#### Case 10 — Debit SMS (Bill Payment)

```json
{
  "raw_sms": "Your A/c XXXX1234 is debited by Rs.2000.00 on 27-05-26 and A/c XXXX5678 credited(UPI Ref no 412398765432). -SBI",
  "sender": "AD-SBIINB"
}
```

| Field | Expected Value |
|---|---|
| amount | 2000.00 |
| type | debit |
| category | Other |
| merchant | (extracted from context) |

---

#### Case 11 — Credit SMS (Refund)

```json
{
  "raw_sms": "Your A/c XXXX1234 is credited by Rs.899.00 on 27-05-26. IMPS Ref no 413256789012. -SBI",
  "sender": "AD-SBIINB"
}
```

| Field | Expected Value |
|---|---|
| amount | 899.00 |
| type | credit |
| category | Other |
| merchant | (extracted from context) |

---

### Axis Bank (2 cases)

#### Case 12 — Debit SMS (Grocery delivery)

```json
{
  "raw_sms": "Rs.850.00 debited from Axis Bank A/c XX9012 on 27-May-26. Info: BIGBASKET. Avl Bal: Rs.8450.00",
  "sender": "AD-AXISBK"
}
```

| Field | Expected Value |
|---|---|
| amount | 850.00 |
| type | debit |
| category | Groceries |
| merchant | BIGBASKET |

---

#### Case 13 — Credit SMS (Cashback)

```json
{
  "raw_sms": "Rs.200.00 credited to Axis Bank A/c XX9012 on 27-May-26. Info: CASHBACK REVERSAL. Avl Bal: Rs.8650.00",
  "sender": "AD-AXISBK"
}
```

| Field | Expected Value |
|---|---|
| amount | 200.00 |
| type | credit |
| category | Other |
| merchant | CASHBACK REVERSAL |

---

### Additional Edge-Case SMS (7 cases)

#### Case 14 — ICICI UPI Debit (Fuel)

```json
{
  "raw_sms": "ICICI Bank Acct XX1234 debited for Rs 2150.00 on 28-May-26; HP PETROL PUMP ANDHERI credited. UPI:512345670099. Call 18002662 for dispute.",
  "sender": "AD-ICICIB"
}
```

| Field | Expected Value |
|---|---|
| amount | 2150.00 |
| type | debit |
| category | Fuel |
| merchant | HP PETROL PUMP ANDHERI |

---

#### Case 15 — HDFC Debit (Medical)

```json
{
  "raw_sms": "HDFC Bank: Rs.3500.00 debited from A/c XX5678 on 28-05-26. Info: APOLLO PHARMACY. Avl Bal: Rs.12840.00",
  "sender": "AD-HDFCBK"
}
```

| Field | Expected Value |
|---|---|
| amount | 3500.00 |
| type | debit |
| category | Healthcare |
| merchant | APOLLO PHARMACY |

---

#### Case 16 — ICICI Card (Entertainment)

```json
{
  "raw_sms": "ICICI Bank Credit Card XX9087 has been used for a transaction of Rs 499.00 on 28-May-26 at NETFLIX.COM. If not done by you call 18002662.",
  "sender": "AD-ICICIB"
}
```

| Field | Expected Value |
|---|---|
| amount | 499.00 |
| type | debit |
| category | Entertainment |
| merchant | NETFLIX.COM |

---

#### Case 17 — HDFC Debit (Education)

```json
{
  "raw_sms": "HDFC Bank: Rs.15000.00 debited from A/c XX5678 on 28-05-26. Info: UDEMY ONLINE COURSES. Avl Bal: Rs.45340.00",
  "sender": "AD-HDFCBK"
}
```

| Field | Expected Value |
|---|---|
| amount | 15000.00 |
| type | debit |
| category | Education |
| merchant | UDEMY ONLINE COURSES |

---

#### Case 18 — Axis Debit (Travel)

```json
{
  "raw_sms": "Rs.8500.00 debited from Axis Bank A/c XX9012 on 28-May-26. Info: MAKEMYTRIP. Avl Bal: Rs.21450.00",
  "sender": "AD-AXISBK"
}
```

| Field | Expected Value |
|---|---|
| amount | 8500.00 |
| type | debit |
| category | Travel |
| merchant | MAKEMYTRIP |

---

#### Case 19 — SBI Debit (Utility)

```json
{
  "raw_sms": "Your A/c XXXX1234 is debited by Rs.1450.00 on 28-05-26 and A/c XXXX7890 credited(UPI Ref no 512367890123). TATA POWER. -SBI",
  "sender": "AD-SBIINB"
}
```

| Field | Expected Value |
|---|---|
| amount | 1450.00 |
| type | debit |
| category | Utilities |
| merchant | TATA POWER |

---

#### Case 20 — ICICI Debit (Very large — Jewellery)

```json
{
  "raw_sms": "ICICI Bank Acct XX1234 debited for Rs 125000.00 on 28-May-26; TANISHQ JEWELLERS credited. UPI:612345678901. Call 18002662 for dispute.",
  "sender": "AD-ICICIB"
}
```

| Field | Expected Value |
|---|---|
| amount | 125000.00 |
| type | debit |
| category | Shopping |
| merchant | TANISHQ JEWELLERS |

---

## 2. Budget Limit Test Cases

**Endpoint:** `POST /budget/`

These test cases set category-level monthly budgets and simulate different alert thresholds.

---

### Case B1 — Food: 80% Alert Threshold (Should trigger alert)

**Budget setup:**
```json
{
  "category": "Food",
  "monthly_limit": 5000.00,
  "alert_at_percent": 80.0,
  "is_family_limit": false
}
```

| Scenario | Value |
|---|---|
| Limit | ₹5,000 |
| Simulated Spend | ₹4,000 |
| Percentage Used | 80% |
| Expected Status | ⚠️ **Alert triggered** — exactly at threshold |

---

### Case B2 — Travel: Over Budget (Should trigger alert)

```json
{
  "category": "Travel",
  "monthly_limit": 3000.00,
  "alert_at_percent": 80.0,
  "is_family_limit": false
}
```

| Scenario | Value |
|---|---|
| Limit | ₹3,000 |
| Simulated Spend | ₹3,500 |
| Percentage Used | 116.7% |
| Expected Status | 🔴 **Over limit** — exceeded budget |

---

### Case B3 — Shopping: Healthy (Should NOT trigger alert)

```json
{
  "category": "Shopping",
  "monthly_limit": 2000.00,
  "alert_at_percent": 80.0,
  "is_family_limit": false
}
```

| Scenario | Value |
|---|---|
| Limit | ₹2,000 |
| Simulated Spend | ₹500 |
| Percentage Used | 25% |
| Expected Status | ✅ **Healthy** — well under limit |

---

### Case B4 — Utilities: Warning Zone (Should trigger alert)

```json
{
  "category": "Utilities",
  "monthly_limit": 2000.00,
  "alert_at_percent": 80.0,
  "is_family_limit": false
}
```

| Scenario | Value |
|---|---|
| Limit | ₹2,000 |
| Simulated Spend | ₹1,800 |
| Percentage Used | 90% |
| Expected Status | ⚠️ **Warning** — above 80% threshold |

---

### Case B5 — Entertainment: Exactly at Limit

```json
{
  "category": "Entertainment",
  "monthly_limit": 1000.00,
  "alert_at_percent": 80.0,
  "is_family_limit": false
}
```

| Scenario | Value |
|---|---|
| Limit | ₹1,000 |
| Simulated Spend | ₹1,000 |
| Percentage Used | 100% |
| Expected Status | 🔴 **At limit** — fully consumed |

---

## 3. Manual Transaction Test Cases

**Endpoint:** `POST /transactions/`

All dates use ISO 8601 format. Adjust the `date` field to your current month for dashboard visibility.

---

### Case T1 — Grocery Shopping

```json
{
  "amount": 850.00,
  "type": "debit",
  "category": "Food",
  "merchant": "DMart Supermarket",
  "bank": "HDFC",
  "account_last4": "5678",
  "date": "2026-05-27T10:30:00",
  "source": "manual"
}
```

---

### Case T2 — Restaurant Bill

```json
{
  "amount": 1200.00,
  "type": "debit",
  "category": "Food",
  "merchant": "Barbeque Nation",
  "bank": "ICICI",
  "account_last4": "1234",
  "date": "2026-05-26T20:15:00",
  "source": "manual"
}
```

---

### Case T3 — Petrol Fill-up

```json
{
  "amount": 2000.00,
  "type": "debit",
  "category": "Fuel",
  "merchant": "HP Petrol Pump",
  "bank": "HDFC",
  "account_last4": "5678",
  "date": "2026-05-25T08:45:00",
  "source": "manual"
}
```

---

### Case T4 — Movie Tickets

```json
{
  "amount": 600.00,
  "type": "debit",
  "category": "Entertainment",
  "merchant": "BookMyShow",
  "bank": "Axis",
  "account_last4": "9012",
  "date": "2026-05-24T18:00:00",
  "source": "manual"
}
```

---

### Case T5 — Doctor Consultation

```json
{
  "amount": 500.00,
  "type": "debit",
  "category": "Healthcare",
  "merchant": "Apollo Clinic",
  "bank": "SBI",
  "account_last4": "3456",
  "date": "2026-05-23T11:00:00",
  "source": "manual"
}
```

---

### Case T6 — Electricity Bill

```json
{
  "amount": 1500.00,
  "type": "debit",
  "category": "Utilities",
  "merchant": "Tata Power",
  "bank": "HDFC",
  "account_last4": "5678",
  "date": "2026-05-22T14:30:00",
  "source": "manual"
}
```

---

### Case T7 — School Fees

```json
{
  "amount": 5000.00,
  "type": "debit",
  "category": "Education",
  "merchant": "DPS School",
  "bank": "ICICI",
  "account_last4": "1234",
  "date": "2026-05-21T09:00:00",
  "source": "manual"
}
```

---

### Case T8 — Flight Ticket

```json
{
  "amount": 8000.00,
  "type": "debit",
  "category": "Travel",
  "merchant": "MakeMyTrip",
  "bank": "Axis",
  "account_last4": "9012",
  "date": "2026-05-20T16:45:00",
  "source": "manual"
}
```

---

### Case T9 — Amazon Purchase

```json
{
  "amount": 3500.00,
  "type": "debit",
  "category": "Shopping",
  "merchant": "Amazon India",
  "bank": "HDFC",
  "account_last4": "5678",
  "date": "2026-05-19T21:00:00",
  "source": "manual"
}
```

---

### Case T10 — Netflix Subscription

```json
{
  "amount": 649.00,
  "type": "debit",
  "category": "Entertainment",
  "merchant": "Netflix",
  "bank": "ICICI",
  "account_last4": "1234",
  "date": "2026-05-18T00:05:00",
  "source": "manual"
}
```

---

## 4. How to Run These Tests

### Prerequisites

1. **Backend running:** Start the FastAPI server
   ```bash
   cd backend
   uvicorn main:app --reload --host 0.0.0.0 --port 8000
   ```

2. **Open Swagger UI:** Navigate to [http://localhost:8000/docs](http://localhost:8000/docs) in your browser.

3. **Register a test user** (if you don't have one):
   - Expand `POST /auth/register`
   - Click **Try it out**
   - Paste this body:
     ```json
     {
       "full_name": "Test User",
       "email": "test@example.com",
       "password": "Test@1234"
     }
     ```
   - Click **Execute**

4. **Log in and get JWT token:**
   - Expand `POST /auth/login`
   - Click **Try it out**
   - Enter `username`: `test@example.com`, `password`: `Test@1234`
   - Click **Execute**
   - Copy the `access_token` value from the response

5. **Authorize Swagger UI:**
   - Click the green **Authorize 🔒** button at the top-right of the Swagger page
   - Paste the token into the **Value** field (just the token, no "Bearer " prefix)
   - Click **Authorize**, then **Close**

---

### Step A — Test Manual Transactions (Section 3)

1. Expand `POST /transactions/` in Swagger UI
2. Click **Try it out**
3. Copy-paste each JSON from [Section 3](#3-manual-transaction-test-cases) into the request body
4. Click **Execute**
5. Verify the response:
   - **Status 201** → transaction created successfully
   - **Status 409** → duplicate detected (re-running same test case)
   - **Status 422** → validation error (check field formats)
6. Repeat for all 10 test cases (T1–T10)

**Verification:** After adding all 10 transactions:
- Expand `GET /transactions/`
- Click **Try it out** → **Execute**
- You should see all 10 transactions in the response array

---

### Step B — Test SMS Ingestion (Section 1)

1. Expand `POST /transactions/ingest-sms` in Swagger UI
2. Click **Try it out**
3. Copy-paste each JSON from [Section 1](#1-sms-ingestion-test-cases) into the request body
4. Click **Execute**
5. Verify the response:
   - `"success": true` → SMS parsed and transaction created
   - `"success": false, "message": "Not a bank transaction SMS"` → SMS parser didn't recognize the format
   - `"success": false, "message": "Duplicate transaction detected"` → already ingested
6. Compare the returned `transaction` object against the **Expected Values** table
7. Repeat for all 20 test cases (Cases 1–20)

**Verification:** After ingesting SMS cases:
- Check `GET /transactions/` to see the newly created transactions
- Verify `amount`, `type`, `category`, and `merchant` match expected values
- Note which SMS formats the parser handles vs. which it doesn't

---

### Step C — Test Budget Limits (Section 2)

1. Expand `POST /budget/` in Swagger UI
2. Click **Try it out**
3. Copy-paste each JSON from [Section 2](#2-budget-limit-test-cases) into the request body
4. Click **Execute**
5. Verify the response returns the created budget with correct values
6. Repeat for all 5 test cases (B1–B5)

**Verification — Check budget alerts:**
- Expand `GET /insights/summary` → **Execute**
- The `budget_alerts` array should contain categories where spend ≥ `alert_at_percent`
- Cross-reference with the transaction amounts you added in Steps A & B

**Verification — Check from Dashboard:**
- Open `http://localhost:5173` in the browser
- The Dashboard should show budget alert cards for categories exceeding thresholds

---

### Step D — Test the Summary Endpoint

1. Expand `GET /transactions/summary` in Swagger UI
2. Click **Try it out**
3. Enter `month`: `2026-05` (current month of the test data)
4. Click **Execute**
5. Verify the response is a JSON object with category totals:
   ```json
   {
     "Food": 2050.00,
     "Fuel": 2000.00,
     "Entertainment": 1249.00,
     "Healthcare": 500.00,
     "Utilities": 1500.00,
     "Education": 5000.00,
     "Travel": 8000.00,
     "Shopping": 3500.00
   }
   ```
   *(Exact values will vary based on which SMS cases were successfully parsed)*

---

### Step E — Test Insights Engine

1. Expand `GET /insights/summary` in Swagger UI
2. Click **Try it out** → **Execute**
3. Verify the response contains:

| Key | Description |
|---|---|
| `spending_changes` | MoM comparison per category (all will show `"direction": "up"` for new test data) |
| `anomalies` | Transactions 2x above historical average (empty on first month's data) |
| `recurring` | Merchants with monthly repeat patterns (requires 2+ months of data) |
| `budget_alerts` | Categories exceeding configured thresholds |

---

### Expected Dashboard Results

After completing all test steps, the React dashboard at `http://localhost:5173` should display:

| Metric | Expected Value (approx.) |
|---|---|
| Total Spent This Month | ₹23,849+ (sum of all debit transactions) |
| Total Transactions | 20+ (10 manual + successfully parsed SMS) |
| Top Spending Category | Travel (₹8,000+) or Education (₹5,000+) |
| Budget Alerts | 3–4 alerts (Food, Travel, Utilities, Entertainment) |

---

### Troubleshooting

| Issue | Solution |
|---|---|
| `401 Unauthorized` | Re-authorize with a fresh JWT token |
| `409 Conflict` | Duplicate transaction — change the `date` slightly and retry |
| `422 Validation Error` | Check field formats — `type` must be `debit`/`credit`, `source` must be `manual`/`sms`/`aa` |
| `500 Internal Server Error` | Check the uvicorn terminal for traceback logs |
| SMS returns `"success": false` | The SMS format may not be recognized by the parser — this is expected for some formats |
| Dashboard shows ₹0 | Ensure transactions have dates within the current month |
