# PCA Exam Analyzer

A web app for **Physics Cube Academy (PCA)** where students analyze their Physics
past-paper performance (2021–2025), track progress across attempts, and download a
personalized PDF study report. Teachers configure each year's paper, tag questions to
topics, and attach the reference materials students should study when they score low.

Built as a single **Spring Boot 4 / Java 21** monolith with **Thymeleaf**, a custom
**liquid-glass (glassmorphism)** UI, **Chart.js**, and **openhtmltopdf** for reports.

---

## Quick start

### Option A — Run locally right now (no database setup)
Uses local H2 storage so the app can run without Supabase.

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Open http://localhost:8080 and sign in with one of the seeded accounts below.

### Option B — Run against your Supabase database (production)
1. Copy the env template and fill in your Supabase values:
   ```bash
   cp .env.example .env
   ```
2. Edit `.env` and set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
   (Supabase Dashboard -> Connect -> ORMs -> **JDBC**). Use the PostgreSQL JDBC URL,
   not the `https://.../rest/v1` REST API URL.
   Also change `TEACHER_USERNAME` / `TEACHER_PASSWORD` to your real admin credentials.
3. Start the app (default profile = Supabase):
```bash
cd backend
./mvnw spring-boot:run
   ```

> The app reads secrets from `.env` via `DotenvLoader`. `.env` is git-ignored — never commit it.

---
---

## Authentication

The app uses username + password login. Students can reset forgotten passwords by
entering their username, NIC, and a new password.

---

## What each role can do

**Teacher** (`/teacher/**`)
- Dashboard with paper / question / reference counts.
- Manage papers (one per year, 2021–2025).
- Manage the question map by year and structured question number.
- Tag each question with a teacher-managed section/topic and max marks.
- Attach references per question: file/resource, video, or practical work.

**Student** (`/student/**`)
- Dashboard: attempt tabs and cross-paper marking sheet.
- Enter marks per question; marks autosave as students type.
- Generate all-attempt or per-attempt reports.
- Download a personalized **PDF report**.

---

## Classification bands (tunable)

Set in `application.properties` (`pca.analysis.*`):

- **Weak** — topic % `< 50`
- **Mid-range** — `50 ≤ % < 75`
- **Strong** — `% ≥ 75`

---

## Tech notes

- **Profiles:** default = Supabase (PostgreSQL); `dev` = local H2 (`/h2-console` enabled).
- **Security:** session-based form login, BCrypt passwords, role-based route guards,
  role-aware navbar, teacher seeded on startup.
- **Supabase tips:** prefer the direct connection (port 5432). If you must use the
  transaction pooler (6543), append `?prepareThreshold=0`. SSL is on via `?sslmode=require`.
- **Build a jar:** from `backend/`, run `./mvnw clean package` then
  `java -jar target/pcaExamAnalyze-0.0.1-SNAPSHOT.jar`.
- **Render:** build with `mvn -f backend/pom.xml clean package -DskipTests` and start with
  `java -jar backend/target/pcaExamAnalyze-0.0.1-SNAPSHOT.jar`.
- **Tests:** `./mvnw test` (boots on the H2 dev profile — no database required).
