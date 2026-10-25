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

## Student exam PDF reports

After results are released, students can choose **Download complete exam report (PDF)** on either result page. A progress dialog shows preparation and PDF transfer progress, then starts the download automatically after the complete PDF arrives. Failed downloads offer a retry. Background report jobs are scoped to the requesting browser session; release and student ownership are rechecked when polling or downloading. The report opens with a PCA-branded book-style cover and linked section navigation. The corrected MCQ answer sheet starts on page 2. A high-resolution answer-breakdown donut and subject accuracy bars follow, using the question syllabus tags; free marks are shown separately and untagged coverage is explicit. The report includes a question-by-question review with attached question/supporting images, their answer and accepted answers. Each exam report also includes the student's marks-based rank, submitted-paper count, highest marks, exam average, and the percentage of submitted papers answering each question correctly. Equal marks share a competition rank (1, 2, 2, 4); free marks are labelled separately. Free marks and multiple accepted answers are supported. Missing question images are clearly identified; the original paper link is included when available.

The download verifies the current browser session against the submission Registration ID and NIC, requires a submitted paper and released results, and sends private no-store responses. PDF marks are recomputed from the current answer key.

## Subject analysis report (several exams)

On **My Results**, students choose **Create report**, pick released exams (Last 5 is preselected; Last 10, All released, or any mix up to 50), and download one PDF. Each question's subject (unit) tag groups the answers under subjects such as Measurement and Mechanics; detailed competency, content and learning-outcome breakdowns are omitted. Every subject is marked Weak (below 50%), Needs practice (50–74%) or Strong (75%+), with the three weakest subjects as revision priorities, the strongest subjects, a score history and a per-question appendix. Free marks are left out of subject accuracy. Code: `report/StudentPerformanceReportService`, template `report/student-performance.html`, endpoints `/exam/results/analysis/*` (session-scoped jobs; ownership and release are rechecked on download).

The individual report cover expresses the student's score and highest score as percentages. Rank remains a position out of submitted papers.

Run `mvn -f backend/pom.xml verify` to build and run the regression suite. Tests use an isolated in-memory H2 database and a random HTTP port to check dashboard states, withheld scores, private report sessions, withdrawn-result access, PDF cover percentages, and the corrected sheet starting on page 2. Chart and background-job tests cover separate free marks and progress completion.
