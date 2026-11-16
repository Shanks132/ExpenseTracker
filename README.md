# Money Tracker

A personal finance tracker: a Spring Boot REST API with JWT auth, plus a React + TypeScript frontend. Tracks income and expenses, shows a monthly dashboard with a spending-by-category pie chart, and keeps a paginated history you can add to, edit and delete from.

Built as a personal project with a $0 budget: everything runs locally or on free tiers (Neon for Postgres, Render for the backend, Redis in a container).

> Last updated: November 2026

---

## Current Status

**Working end to end (backend + frontend):**
- Register, login, logout (token blacklisted in Redis)
- Home dashboard: income, expenses and balance for a chosen month, plus a pie chart of spending by category
- History page: paginated list, newest first
- Add, edit and delete transactions from the UI (delete uses an inline confirmation)
- Profile endpoints on the backend (get, update, delete account)
- CORS config driven by an environment variable
- Backend containerized (Dockerfile + Compose) and **deployed to Render** as a Docker service, using Neon for Postgres
- Public liveness check: `GET /ping` on the backend and a `/ping` status page in the frontend
- Automated backend tests: unit tests for the JWT utility, services and DTO, plus an HTTP-level integration suite (see Testing)

**Placeholder only:**
- AI finance review panel on Home ("Coming soon")

**Not built yet:**
- Frontend deployment
- Profile page in the UI
- Hosted Redis for the deployed backend, rate limiting, and CI (GitHub Actions)

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Spring Security, Spring Data JPA |
| Auth | JWT (jjwt 0.11.5; `jjwt-api`, `jjwt-impl`, `jjwt-jackson` must share one version) |
| Database | PostgreSQL on Neon (local Postgres or the Compose one also work) |
| Token blacklist | Redis |
| Frontend | React, TypeScript, Vite, React Router, Axios |
| Charts | Plain CSS (`conic-gradient`), no chart library |
| Containers | Docker, Docker Compose |
| Hosting | Render (backend, free tier), Neon (database) |
| Schema | `ddl-auto=update` (Flyway planned) |
| Testing | JUnit 5, Mockito, AssertJ, MockMvc, H2 (in-memory) for automated tests; Postman for manual checks |

---

## Backend

### API Reference

All routes except `/auth/**` and `/ping` need `Authorization: Bearer <token>`. Transaction routes share the base path `/transaction` (singular).

| Method | Route | Description |
|---|---|---|
| GET | `/ping` | Public liveness check, returns `{"status":"ok"}` (no database or Redis access) |
| POST | `/auth/register` | Create account |
| POST | `/auth/login` | Returns a JWT |
| POST | `/auth/logout` | Blacklists the current token in Redis |
| GET | `/users/me` | Own profile |
| PUT | `/users/me` | Partial update (username, email, password) |
| DELETE | `/users/me` | Delete account and all its transactions |
| POST | `/transaction` | Add a transaction |
| GET | `/transaction?page=0&size=10` | Own transactions, paginated, newest first (max 50 per page) |
| GET | `/transaction/summary?month=YYYY-MM` | Monthly totals and expense breakdown by category (defaults to the current month) |
| PUT | `/transaction/{id}` | Partial update (ownership checked) |
| DELETE | `/transaction/{id}` | Delete (ownership checked) |

Summary response shape:

```json
{
  "totalIncome": 2500.00,
  "totalExpense": 1000.65,
  "balance": 1499.35,
  "expenseByCategory": [
    { "category": "Rent", "total": 900.00 },
    { "category": "Food", "total": 100.65 }
  ]
}
```

Transactions without a category are grouped as "Uncategorized".

### Status codes

| Code | Meaning |
|---|---|
| 200 / 204 | Success / success with no body |
| 400 | Validation failed; body is a map of field to message |
| 401 | Token expired, blacklisted or invalid; login failure |
| 403 | No token sent, or a path that doesn't exist (see Gotchas) |
| 404 | Not found |
| 409 | Conflict (duplicate username or email), once the fix below is in |
| 500 | Unexpected error |

### Data models

**User:** id, username (unique, 3-30 chars), email (unique), password (BCrypt hash, never serialized), createdAt.

**Transaction:** id, user (ManyToOne), amount (`BigDecimal(19,4)`), description, type (`INCOME` or `EXPENSE`), category (optional), transactionDate, createdAt.

### Package layout

```
src/main/java/shanks/personal/demo/
├── controller/   AuthController, UserController, TransactionController, PingController
├── service/      UserService, TransactionService
├── repository/   UserRepository, TransactionRepository
├── entity/       User, Transaction, TransactionType
├── security/     JwtUtil, JwtFilter, SecurityConfig, CorsConfig, TokenBlacklistService
├── dto/          RegisterRequest, LoginRequest, AuthResponse, UpdateUserRequest,
│                 CreateTransactionRequest, UpdateTransactionRequest,
│                 TransactionResponse, CategoryTotal, SummaryResponse
└── exception/    NotFoundException, UnauthorizedException, GlobalExceptionHandler
```

### How auth works

1. `POST /auth/login` checks the password against the BCrypt hash and returns a JWT containing `userId` and `username`.
2. The client sends the token on every request.
3. `JwtFilter` checks the Redis blacklist, validates signature and expiry, and puts the verified `userId` on the request.
4. Controllers read `userId` from the request, never from the URL.
5. `POST /auth/logout` stores the token in Redis with a TTL equal to its remaining lifetime, so Redis cleans it up on its own.

The JWT **secret** (server-side signing key, from `JWT_SECRET`) is different from the JWT **tokens** (created at login, kept by the browser). The secret never goes in the repo.

### CORS

`CorsConfig` builds the allowed-origins list from `app.cors.allowed-origins`, which reads the `CORS_ALLOWED_ORIGINS` environment variable and defaults to `http://localhost:5173`. `SecurityConfig` enables it with `.cors(Customizer.withDefaults())`. Origins must match exactly (scheme, host, port, no trailing slash); separate several with commas.

---

## Frontend (`money-tracker-ui`)

### Screens

| Route | Screen |
|---|---|
| `/login`, `/register` | Auth forms, with the backend's per-field validation messages |
| `/` | Home: month picker, totals, pie chart, AI review placeholder |
| `/history` | Paginated table with Edit and Delete per row |
| `/transactions/new` | Add form |
| `/transactions/:id/edit` | Edit form, prefilled from the History row |
| `/ping` | Public backend status page: online or offline, response time, and a note when a sleeping free-tier server is waking up |

### Layout

```
src/
├── main.tsx, App.tsx, index.css, app.css, ping.css
├── types.ts, format.ts
├── api/          client.ts (Axios + token handling), auth.ts, transactions.ts, errors.ts
├── components/   AppShell.tsx, AuthLayout.tsx, Field.tsx, PieChart.tsx
└── pages/        Login, Register, Home, History, TransactionForm, Ping
```

### How it talks to the backend

- In development, requests go to `/api/...` on the Vite dev server, which proxies them to `http://localhost:8080` and strips `/api`. The browser sees one origin, so CORS never triggers.
- If `VITE_API_URL` is set, Axios calls that URL directly instead (this is how a deployed frontend works, and it exercises CORS).
- Axios attaches the token to every request except `/auth/login` and `/auth/register`. On a `401` while holding a token, it clears the token and redirects to login.
- The `/ping` page uses plain `fetch` with no token, so a stale token can never interfere with a status check.
- The token is stored in localStorage.
- Frontend request paths must match the backend controller mappings exactly, including the singular `/transaction`.
- `VITE_` variables are baked into the built JavaScript and visible to anyone. Never put secrets in them.

---

## Local Setup

### Option A: Docker Compose (everything in containers)

Needs the `Dockerfile`, `.dockerignore` and `compose.yaml` in the backend folder (next to `pom.xml`). `.env.example` lists the variables.

```bash
export JWT_SECRET=$(openssl rand -base64 32)   # or put it in a .env file (gitignored)
docker compose up --build
```

- Starts the app on `localhost:8080`, plus Postgres and Redis (both health-checked, Postgres data on a named volume).
- **Database choice:** if `DB_URL` is set (shell or `.env`), the app uses it, for example a Neon JDBC URL, which carries its own user, password and `sslmode=require`. If not, the app uses the local Postgres container. Quote the URL in `.env`, because it contains `&` and `?`.
- The local container database is separate from Neon and starts empty, so register a new account.
- `docker compose down` keeps data; `docker compose down -v` wipes it.
- Stop anything else on port 8080 first (`sudo lsof -i :8080`).
- The Dockerfile builds with `-DskipTests`; tests run separately (see Testing and CI).

### Option B: run from the IDE

1. Create a Postgres database (Neon, or local Postgres with a user that owns the `public` schema).
2. Start Redis (`sudo systemctl start redis-server`; `redis-cli ping` should say `PONG`).
3. Set environment variables in the run configuration, one row per variable:

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | JDBC URL. For Neon, credentials and `sslmode=require` are inside the URL. |
   | `DATABASE_USERNAME` / `DATABASE_PASSWORD` | Used with a local Postgres URL; ignored when the URL carries credentials |
   | `JWT_SECRET` | Long random string, e.g. `openssl rand -base64 32` |
   | `CORS_ALLOWED_ORIGINS` | Optional; defaults to `http://localhost:5173` |

### Frontend

```bash
npm install
npm run dev     # http://localhost:5173
```

To point the dev proxy at a different backend, change `target` in `vite.config.ts` and restart Vite (it only reads the file at startup). To test CORS locally, create `.env.local` with `VITE_API_URL=http://localhost:8080`, restart Vite, and check the Network tab for `OPTIONS` preflights.

---

## Testing

Run everything with `./mvnw test`. The suite needs no Docker, Postgres or Redis, so it also runs on a CI machine.

| Test class | Kind | What it checks |
|---|---|---|
| `JwtUtilTest` | Unit | Token round trip (username and user ID); rejects expired, wrongly signed, tampered and malformed tokens; remaining-lifetime calculation |
| `UserServiceTest` | Unit (Mockito) | Password is hashed before saving; partial updates change only the given fields; a new password is re-hashed; account deletion removes transactions, then the user, then blacklists the token, in that order |
| `TransactionServiceTest` | Unit (Mockito) | Create, list, update and delete; ownership checks reject another user's transaction; monthly summary totals, balance, and zeros for an empty month |
| `TransactionResponseTest` | Unit | The response copies the right fields and has no `user` or `password` field that could leak |
| `ApiIntegrationTest` | Integration (MockMvc + H2) | Register and login; validation errors; missing, garbage, expired and blacklisted tokens; logout; account deletion; transaction create, list, update and delete; one user can't change another's data; pagination order; monthly summary |

### How it's set up

- **Unit tests** replace the repositories (and other collaborators) with Mockito mocks, so they test only the service logic, in milliseconds.
- **The integration test** boots the whole Spring context and sends real HTTP-style requests through the security filters, validation, controllers, services and JPA. It uses an in-memory H2 database in PostgreSQL mode, and replaces `TokenBlacklistService` with a mock so Redis isn't required.
- **Test configuration** lives in `src/test/resources/application.properties`, which replaces the main file during tests (its own JWT secret, H2 URL and CORS value).
- **Isolation:** each integration test registers its own uniquely named users, so tests don't depend on each other or on execution order.
- **Spring Boot 4 specifics:** the tests need the `spring-boot-starter-webmvc-test` dependency, import `AutoConfigureMockMvc` from `org.springframework.boot.webmvc.test.autoconfigure`, and use `@MockitoBean` in place of the removed `@MockBean`.

### Things to know

- A few tests pin current behavior: a bad token returns `401`, a missing token returns `403`, and another user's transaction is rejected with any 4xx (it is currently a 401 and should become a 403).
- One test, duplicate registration returning `409`, is marked `@Disabled` until that fix exists.
- If the controller's method-level paths differ from `/transaction`, change the three path constants at the top of `ApiIntegrationTest`.
- H2 is not Postgres. PostgreSQL mode smooths most differences, but Postgres-specific SQL could behave differently, so consider Testcontainers if the queries get more advanced.
- Not covered yet: real Redis behavior (blacklist TTLs), CORS, and the frontend.

---

## Deployment

| Piece | Where | Status |
|---|---|---|
| Backend | Render web service, built from the Dockerfile | Live |
| Postgres | Neon (the same database is used locally and on Render) | In use |
| Redis | Hosted Redis such as Upstash (free tier) | To do for the deployed backend |
| Frontend | Static host: Cloudflare Pages, Netlify or Vercel, with a fallback to `index.html` for client-side routes | To do |

**Render environment variables:** `DB_URL` (Neon JDBC URL), `JWT_SECRET`, and `CORS_ALLOWED_ORIGINS` (the frontend's URL, once it exists). A hosted Redis also needs its connection settings wired into the app, because the app defaults to `localhost:6379`, where nothing runs on Render.

**Free-tier behavior:** free web services sleep when idle, so the first request after a quiet spell can take about a minute, and they have little CPU and memory. Opening the `/ping` page wakes the service and shows how long it took. Free-tier limits change often, so check each host's pricing page.

**When the frontend goes live:** build it with `VITE_API_URL` set to the Render URL (no trailing `/api`), and set `CORS_ALLOWED_ORIGINS` on Render to the frontend's exact URL. Use HTTPS for both.

### Production hardening checklist
- [ ] Use a fresh, production-only `JWT_SECRET` (never reuse a development one)
- [ ] Rate limiting on `/auth/login` and `/auth/register`
- [ ] Remove debug output: `System.out.println` in `JwtFilter`, `show-sql=true`, and the DEBUG/TRACE logging lines in `application.properties` (they flood Render's logs)
- [ ] Confirm no secrets are committed (`.env` gitignored; nothing sensitive in `application.properties`)
- [ ] Hosted Redis connected, so logout and the blacklist check work in production
- [ ] Return `409` for duplicate username or email (see below)

### CI (planned)
GitHub Actions on every push and pull request: build the backend, build the frontend, run the backend test suite (`./mvnw test`, which needs no database or Redis). The Dockerfile keeps `-DskipTests`, so the tests belong in CI, and branch protection can require a green run before merging to `main`. Free and unmetered for public repositories on standard runners. The repo is public, so secrets live only in the host's environment variables and GitHub Secrets, never in files or git history.

---

## Known Issues and Next Steps

### Backend
- [ ] Duplicate username or email currently surfaces as a 500. Fix: `existsByUsername` / `existsByEmail` checks in `registerUser`, a `ConflictException`, and handlers returning `409` (also for `DataIntegrityViolationException` as a backstop). Then add a `409` message in `Register.tsx` and enable the disabled test.
- [ ] Confirm `UserController` has no `GET /users/{userId}` route. Without it, any logged-in user could read another user's profile; profile reads should go through `/users/me`.
- [ ] Ownership failures return `401`; should be `403` (the frontend treats any 401 as an expired session and logs out)
- [ ] `JwtFilter` rejects expired tokens on `/auth/**` too; override `shouldNotFilter` to skip those paths (the frontend works around it by not sending a token there)
- [ ] Allow `/error` and `/ping` in `SecurityConfig` (`.requestMatchers("/auth/**", "/ping", "/error").permitAll()`), so unknown paths return a clear 404 instead of a misleading 403
- [ ] A category can't be cleared once set (partial update treats a missing field as "unchanged")
- [ ] Switch `ddl-auto=update` to Flyway migrations
- [ ] Replace `System.out.println` with SLF4J
- [ ] Refresh tokens (1 hour expiry)
- [ ] Test the Redis blacklist against a real Redis (the integration suite mocks it)

### Frontend
- [ ] Profile page (edit details, change password, delete account)
- [ ] Frontend tests (the backend already has unit and integration tests; see Testing)
- [ ] The edit page needs the row passed from History; opening it directly redirects to History
- [ ] "Waking up the server" message on login for cold starts on free hosting
- [ ] localStorage token is fine for now; consider an HttpOnly cookie plus refresh tokens if this becomes public-facing

### Features
- [ ] AI finance review (placeholder panel exists on Home)
- [ ] Income breakdown or trend charts
- [ ] Accounts (Cash, Bank, Card) with running balances
- [ ] Recurring transactions, per-category budget limits

---

## Design Decisions

- **Revocable JWTs without a session store.** Tokens are stateless, yet logout and account deletion still invalidate them immediately. The token goes into a Redis blacklist with a TTL equal to its remaining lifetime, so entries delete themselves when the token would have expired anyway.
- **Identity comes from the verified token, never from the URL.** Endpoints like `/users/me` take no user ID, and update and delete check that the transaction belongs to the caller. This closes off one user reading or changing another's data by guessing IDs.
- **Aggregation happens in the database.** The dashboard's totals and category breakdown come from grouped queries, because totals computed from a paginated list would be wrong.
- **Bounded, minimal responses.** Pagination is capped server-side (50 per page), and DTOs keep entities and the password hash out of responses.
- **Same origin in development, explicit CORS in production.** The Vite proxy avoids CORS locally, while deployed builds call the API directly with allowed origins set per environment.
- **Safer containers.** Multi-stage Docker build, health-checked dependencies, secrets from the environment, and Compose refuses to start without the JWT secret.
- **No chart dependency.** The pie chart is plain CSS.

## Gotchas

- **A 403 on a deployed request usually means the path is wrong, not the login.** Unknown paths fall through to Spring's internal `/error` route, which security blocks, so they show up as a bare 403. Check the first `Securing ...` line in the logs to see which path actually arrived. A missing `/auth` in the login URL cost real time here.
- jjwt splits into API, implementation and Jackson jars. A missing `jjwt-impl` gives `UnknownClassException`; mismatched versions give `NoClassDefFoundError`.
- A bare 403 with `Securing GET /error` in the logs usually means a request failed inside the app (or hit an unmapped path) and Spring's `/error` route was blocked. Check the path and the real exception first.
- Environment variables override `application.properties` through Spring's relaxed binding (`SPRING_DATASOURCE_URL` maps to `spring.datasource.url`), which is how Compose configures the app.
- Closing the IDE can leave the backend running on port 8080. Check with `sudo lsof -i :8080` before blaming the code.

---

## Session Context (for continuing in a new chat)

Built so far, in order: Spring Boot setup and layering; User and Transaction entities; JWT auth with Spring Security; Redis token blacklist and logout; `@Valid` validation and a global exception handler; `TransactionResponse` DTO; pagination; Neon database; React + Vite + TypeScript frontend (login, register, Home dashboard with pie chart, History, add/edit/delete); monthly summary endpoint; CORS config; Dockerfile and Compose setup (Neon or local database); backend test suite (unit and integration); backend deployed to Render; `/ping` endpoint and status page.

Recent topics: a 403 on Render caused by calling `/login` instead of `/auth/login`; the jjwt classpath error; why tests stay out of the Dockerfile and belong in CI; keeping secrets with a public repo.

Next logical steps: wire a hosted Redis into the deployed backend, deploy the frontend (set `VITE_API_URL` and `CORS_ALLOWED_ORIGINS`), the `409` and `403` status fixes, rate limiting, a CI workflow, then the profile page and the AI review feature.