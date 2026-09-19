# SmartSpend — Project Notes

Read this file fully before starting any task. It contains architectural
decisions, current status, and hard rules — do not deviate from these
without explicit confirmation from the developer.

## What this project is

Android expense tracker for the Indian UPI/banking ecosystem. Reads bank
SMS passively (on-device), categorizes transactions automatically via a
5-layer waterfall, and gives budgeting/insights. Backend: FastAPI +
PostgreSQL. Mobile: Kotlin + Jetpack Compose. Web dashboard exists but is
NOT the current priority — this project is mobile-first.

## Ownership split — DO NOT CROSS THESE LINES

- **Colleague owns:** Welcome → Login → OTP entry (mobile UI),
  `/auth/login`, `/auth/register`, JWT issuance, rate limiting on those
  two endpoints. **Do not modify these files or endpoints unless
  explicitly told to in this session's task.**
- **Developer (primary user) owns:** everything else — Home, Add
  Transaction, Budget, Categories, Trends, Insights, Account, SMS
  parsing/permission flow, deployment, migrations.

## Current status (update this section after every completed phase)

Done and verified:
- Backend data layer: single source of truth service layer
  (`transaction_aggregates.py`), category dedup, MIN_MEANINGFUL_BASELINE
  guard (₹100) for MoM %, "Needs Review" excluded from category
  breakdowns, credit-side categories added (Salary, Refund, Interest,
  Bank Deposit, Investment Return, Reimbursement, Cashback, Other Credit)
- Category normalization happens at WRITE time (not just read time) —
  `@validates("category")` on Transaction and MerchantMapping models,
  plus normalization in every router write path. Read-time normalization
  in aggregates is kept as a defensive fallback, not removed.
- Android: Home, Add Transaction, Budget, Categories (+ Merchants tab +
  drill-down), Trends (Chart + Insights tabs, filter panel), Account
  page (Privacy Policy/Terms/Support/Delete links, working delete flow)
  — all built and device-verified with screenshots
- `DELETE /users/me` — cascades across transactions, budget limits,
  merchant mappings. Tested end-to-end against real DB rows.
- On-device SMS parser (`SmsTransactionParser.kt`) — ported from real
  `backend/utils/sms_parser.py` regex patterns, not invented. Backend
  `ingest-sms` schema uses `extra="forbid"`, rejects any raw SMS field.
  `raw_sms` column already dropped from DB (dev only, not production yet).
- Hosted compliance pages (GitHub Pages): Privacy Policy, Terms of
  Service, Support/FAQ, Account Deletion — all live, HTTP 200.
- Security audit pass 1: CORS fixed (was `["*"]` + credentials, invalid
  per spec), hardcoded JWT_SECRET_KEY removed from docker-compose.yml
  — **but the OLD secret was committed twice to git history and must
  be treated as permanently compromised. A new JWT_SECRET_KEY must be
  generated and used in Render, never reuse the old value.**

In progress / not yet done:
- Fixed: dev backend base URL was hardcoded to `http://127.0.0.1:8000/`
  in `BackendService.kt`, which only works when the app and the backend
  run on the same machine (emulator + `adb reverse`, or a desktop
  build). On a real physical phone, 127.0.0.1 is the phone itself, so
  it always failed with "failed to connect to /127.0.0.1:8000" —
  this is what "Could not load Home" traced back to. Now configurable:
  `android/app/build.gradle.kts` injects `BuildConfig.DEV_BACKEND_BASE_URL`
  from (in order) a `-PdevBackendBaseUrl` flag, `dev.backend.base.url`
  in `android/local.properties` (gitignored, per-machine), or a LAN
  fallback. `BackendService.kt` now reads that field instead of a
  hardcoded string. Still requires: the backend actually running and
  reachable (`docker-compose up` in `backend/`, binds `0.0.0.0:8000`
  already) and phone + dev machine on the same Wi-Fi network with the
  firewall allowing port 8000 — set `dev.backend.base.url` in
  `local.properties` if the dev machine's LAN IP isn't
  `192.168.29.227` (the fallback baked in, taken from a prior working
  value already present in the app's network security config).
- SMS auto-sync consent/permission screen — **code written, NOT yet
  build- or device-verified.** Lives at
  `android/app/src/main/java/com/smartspend/app/ui/permission/SmsConsentScreen.kt`
  on branch `claude/sms-consent-auto-sync-vlewm3` (not merged, no PR
  opened yet). Implements: inline disclosure copy + Privacy Policy link,
  skip-if-already-granted check via `ContextCompat.checkSelfPermission`,
  and all three permission outcomes (granted → brief "Scanning your
  messages..." state → Home with auto-sync active; first denial → Home
  in manual-entry mode; permanently denied →
  `shouldShowRequestPermissionRationale == false` after a prior request →
  in-screen message + deep link to the app's system permission page).
  Home now has a live "SMS auto-sync" card wired to launch this screen
  (`HomeScreen.kt`, `MainActivity.kt` `DevRoute.SmsConsent`). **Could not
  build or screenshot in this cloud dev sandbox** — its egress
  policy blocks `dl.google.com`, which hosts the Android Gradle Plugin,
  so no Gradle build is possible there at all. Per this project's
  verification standard, this must be built and screenshotted on a real
  device/emulator for all 3 outcomes, and `SmsReceiver` confirmed firing
  on a real incoming bank SMS, before it counts as done.
- Auth stub (`AUTH_STUB=true`) still used for local dev — real auth is
  colleague's work, not yet integrated
- Render production deployment — not yet updated with Phases 1-6
- Production fingerprint purge + raw-SMS purge migrations — MUST NOT
  run until Render deployment is confirmed working AND colleague's
  auth work is stable
- Second security audit pass — scheduled for after colleague's auth
  work lands
- CSV export — backend endpoint does not exist yet
- App name — "SmartSpend" is taken on Play Store by 6+ apps, needs a
  final decision before Play Store submission (candidates discussed:
  Paisa Pilot, AutoLedger, PocketPilot, SpendSync, NudgeSpend,
  TrackLess, RupeeRadar — none verified for availability)

## Hard rules — never do these without explicit confirmation

1. **Never run the raw-SMS purge or fingerprint purge migrations**
   against production without a fresh backup immediately before, and
   explicit approval for each step separately (purge, verify, THEN
   drop column — never chain them).
2. **Never modify auth/login/register/JWT-issuance code** — colleague's
   scope. Flag if a task seems to require touching it instead of doing so.
3. **Never invent new regex/parsing logic for SMS** — the real Python
   source in `backend/utils/sms_parser.py` is the source of truth. Port
   from it, don't approximate, unless explicitly told the source doesn't
   cover a case and a new pattern is needed (then flag it explicitly,
   don't silently add it).
4. **Never add a new backend endpoint without flagging it** — most
   screens should reuse existing service-layer functions
   (`transaction_aggregates.py`). If a new endpoint is genuinely needed,
   say so explicitly in the report rather than quietly creating one.
5. **Never assume `AUTH_STUB=true` is safe** — it must hard-fail if
   `APP_ENV=production` is also set. Don't weaken or remove this guard.
6. **Category list is canonical** — do not introduce new category
   strings outside `CANONICAL_CATEGORY_MAP` without updating that map
   first. Both debit and credit category sets exist; validate
   `category` against `transaction_type` at write time.

## Architecture patterns to follow

- **Single source of truth:** any number shown on more than one screen
  (spend totals, category breakdowns, budget utilization) must come
  from the same `transaction_aggregates.py` function, called by both
  screens — never two independent queries computing "the same" number.
- **Write-time, not read-time, normalization:** category normalization
  happens when data is saved, not just when it's displayed. This
  applies to any new write path added in the future too.
- **No raw SMS ever reaches the backend or gets stored.** Parsing is
  on-device only. If a task involves SMS in any way, confirm this
  constraint is preserved.

## Verification standard — apply this to every task

Do not report a task as "done" based on a passing build or test count
alone. For any UI change: take a screenshot on a real device/emulator
and confirm visually, not just that it compiled. For any data-layer
change: query the actual database to confirm the real row-level effect,
not just that an endpoint returned 200. For any claim about what code
does: quote the actual relevant lines in the report, not a paraphrase.

If the current execution environment cannot build or run the app (e.g.
network egress restrictions), say so explicitly and mark the change as
unverified rather than reporting it as done.

## Where to find more detail

- `docs/` folder (repo root) — hosted compliance pages source (privacy
  policy, terms, support, delete-account HTML)
- Ask the developer for the full master specification doc if deeper
  context on any past decision is needed — this file is a condensed
  summary, not the complete history.
