# PCA MCQ Exam System

A web app for **Physics Cube Academy (PCA)** to run MCQ examinations. Students take
papers without an account (they just enter their details); the teacher/admin builds exams
(answer-sheet or question-image papers), manages the syllabus, schedules and releases
results, and reviews analytics.

Built as a single **Spring Boot 4 / Java 21** monolith with **Thymeleaf** and a custom
glassmorphism UI. `/` redirects to the exam portal (`/exam`); the admin signs in at
`/exam/admin-login`.

---

## Quick start

### Option A — Run locally right now (no database setup)
Uses local H2 storage so the app can run without Supabase.

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Open http://localhost:8080 (the exam portal). Admin login is at `/exam/admin-login` with the seeded teacher account below.

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

## Authentication

Only the admin/teacher logs in (username + password, seeded from `.env`). Students take
exams without an account.

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

---

## Exam analysis PDF report

Admin portal -> **Manage Exams -> Report** (or **Download PDF Report** on an exam's analysis page) downloads a complete
report for one exam: cover page, hyperlinked table of contents (+ PDF bookmarks), executive summary and insights,
exam details, participation analysis, **district analysis** (students and average marks per district), question
overview (difficulty, discrimination, time), syllabus-unit performance, **one page per question** (image, answer
distribution graph, statistics, insight), the full **rank list**, and an appendix of definitions.

Code: `report/` package (`ExamReportService` = analysis, `ExamReportPdfService` = PDF), template
`frontend/templates/report/exam-report.html`, fonts in `backend/src/main/resources/report/fonts`.
A sample is in `docs/samples/sample-exam-report.pdf`.
Tamil student names/schools are drawn as shaped images because openhtmltopdf cannot shape Tamil text.
