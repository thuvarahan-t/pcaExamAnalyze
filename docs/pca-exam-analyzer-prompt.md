# Build Prompt — PCA Physics Past Paper Mark Analyzer

> Paste this whole file into Claude Code as your build instruction. It contains full context, data model, features, and a build order.

---

## 1. Project Overview

Build a web application called **PCA Exam Analyzer** where **students** analyze their Physics past-paper performance (years **2021–2025**), track progress across multiple attempts, and automatically discover their **weak / mid-range / strong** areas. The app then generates a **personalized PDF study report** telling each student exactly which topics to revise and which reference materials to study.

A **teacher** (single admin account) configures each year's paper structure, tags every question to a Physics topic, and attaches reference materials that students should study when they score low on that question.

The whole thing is **one Spring Boot project** (monolith). Use **Thymeleaf + Bootstrap 5 (CDN) + Chart.js (CDN)** for the frontend so everything runs from a single `mvn spring-boot:run`. (If you prefer, a REST API + React frontend is an acceptable alternative, but default to the Thymeleaf monolith for simplicity.)

---

## 2. Tech Stack (already set up)

- **Spring Boot 4.0.6**, **Java 21**, **Maven**
- Base package: `com.example.pcaExamAnalyze`
- **Database: Supabase (PostgreSQL)**
- Existing dependencies: Spring Web, Spring Data JPA, PostgreSQL Driver, Validation, Lombok, OAuth2 Resource Server

**Add to `pom.xml`:**
- `spring-boot-starter-thymeleaf` (server-side views)
- `spring-boot-starter-security` (form-based login + sessions)
- A current **HTML-to-PDF** library (e.g. `openhtmltopdf-pdfbox`) to render a Thymeleaf HTML template into a downloadable PDF
- `me.paulschwarz:spring-dotenv` (so secrets can be read from a `.env` file)

Use **session-based form login** with Spring Security (simpler than JWT for a Thymeleaf app). The OAuth2 Resource Server starter is only needed if you switch to a REST + JWT approach — you can remove it for the monolith.

---

## 3. Configuration & Secrets (must use ENV — do NOT hardcode)

Create a `.env` file in the project root (and add it to `.gitignore`). Read it via `spring-dotenv`, and reference values in `application.properties` using `${...}` placeholders.

**`.env`**
```
# Teacher (admin) seed account
TEACHER_EMAIL=teacher@pca.lk
TEACHER_PASSWORD=teacher123@

# Supabase database
DB_URL=jdbc:postgresql://db.<your-ref>.supabase.co:5432/postgres
DB_USERNAME=postgres
DB_PASSWORD=<your-supabase-password>
```

**`application.properties`**
```
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
server.port=8080
```

**On application startup**, run a data initializer (`CommandLineRunner` or `ApplicationRunner`) that:
- Checks if a user with `TEACHER_EMAIL` exists.
- If not, creates the teacher account with role `TEACHER`, password **BCrypt-hashed** from `TEACHER_PASSWORD`.
- Never store plaintext passwords anywhere.

---

## 4. Data Model (JPA entities — let Hibernate generate the schema)

**User**
- `id`, `email` (unique), `passwordHash`, `fullName`, `role` (enum: `STUDENT`, `TEACHER`), `createdAt`

**PaperStructure** — one per year, created by teacher
- `id`, `year` (int, 2021–2025), `subject` (default "Physics"), `title`
- One-to-many → `Question`

**Question** — created by teacher
- `id`, `paperStructure` (FK), `questionNumber` (string, e.g. "Q1", "Q3(b)"), `topic` (string — the Physics chapter/topic this question tests), `section` (enum: `MCQ`, `STRUCTURED`, `ESSAY`), `maxMarks` (int), `description` (optional)
- One-to-many → `QuestionReference`

**QuestionReference** — study material a teacher attaches to a question (the "what to study if weak" data)
- `id`, `question` (FK), `title`, `type` (enum: `BOOK`, `VIDEO`, `NOTES`, `LINK`, `PRACTICE`), `resource` (e.g. "Physics Resource Book — pg 112–118" or a URL), `note` (optional)

**Attempt** — a student attempting one year's paper
- `id`, `student` (FK → User), `paperStructure` (FK), `attemptNumber` (int, 1,2,3… — auto-increment per student+paper), `attemptDate`, `totalObtained` (computed), `totalMax` (computed), `percentage` (computed)
- One-to-many → `QuestionScore`

**QuestionScore** — marks a student entered for one question in one attempt
- `id`, `attempt` (FK), `question` (FK), `marksObtained` (int)
- **Validation:** `0 ≤ marksObtained ≤ question.maxMarks` (reject otherwise)

---

## 5. Roles & Auth Flow

- **Login page** at `/login` (Spring Security form login).
- **Student self-registration** at `/register` (creates a `STUDENT` user).
- **Teacher** is NOT self-registered — it's the seeded ENV account.
- After login, **redirect by role**:
  - `TEACHER` → `/teacher/dashboard`
  - `STUDENT` → `/student/dashboard`
- **Navbar shows a "Dashboard" link based on role** — when the teacher logs in, show a **"Teacher Dashboard"** option in the top menu; students see "My Dashboard".
- Secure routes: `/teacher/**` requires `TEACHER`; `/student/**` requires `STUDENT`. Public: `/login`, `/register`, static assets.
- Logout button in navbar.

---

## 6. Teacher Features (`/teacher/**`)

1. **Teacher Dashboard** (`/teacher/dashboard`) — overview: number of papers configured per year, total questions tagged, total references added, quick links.
2. **Manage Papers** — create a `PaperStructure` for each year 2021–2025 (CRUD).
3. **Manage Questions** for a selected paper — add/edit/delete questions with: question number, **topic**, section, max marks. (This is the "intha year, intha question ethu related aanathu" tagging.)
4. **Manage References** for each question — add/edit/delete `QuestionReference` entries describing what a student should study/practice if they score low on that question. Teacher must be able to **keep adding** references over time.

---

## 7. Student Features (`/student/**`)

1. **Student Dashboard** (`/student/dashboard`) — list of years available, the student's attempts, an overall progress snapshot (best/latest percentage, weakest topic), and quick links.
2. **Start / Enter Attempt** — pick a year → see that paper's questions → enter marks for each question (validated against max marks) → save. Auto-assign the next `attemptNumber` for that student+paper (so attempt 1, 2, 3, and beyond are tracked).
3. **Attempt Analysis** (`/student/attempts/{id}`) — after saving, show:
   - Overall score, percentage, and classification.
   - **Topic-wise breakdown** with color-coded status (weak / mid / strong).
   - A **doughnut/bar chart** (Chart.js) of marks per topic.
   - An **"Areas needing attention"** section listing each weak topic + the teacher's reference materials for those questions.
4. **Progress View** (`/student/progress`) — compare **Attempt 1 vs 2 vs 3 (and more)**:
   - **Line chart** of overall percentage across attempts.
   - **Grouped bar chart** of per-topic percentage across attempts.
   - Show **average** percentage, current weak / mid / strong topics, and improvement deltas (e.g. "Waves: 35% → 60%, +25%").
5. **Download PDF Report** (`/student/attempts/{id}/report.pdf`) — see Section 9.

---

## 8. Analysis & Classification Logic

For each question score, compute `percentage = marksObtained / maxMarks * 100`. Aggregate by **topic** (sum obtained / sum max across all questions of that topic in the attempt).

**Default classification thresholds** (keep them as easily-changeable constants; optionally make teacher-configurable later):
- **WEAK** → topic percentage `< 50%`
- **MID-RANGE** → `50% ≤ percentage < 75%`
- **STRONG / PERFECT** → `percentage ≥ 75%`

Example: a student scoring **7/20 (35%)** on a topic is flagged **WEAK** → the report must surface that topic's questions and the teacher's references. A student at **15/20 (75%)** is **STRONG**.

Also compute: overall percentage, average across all attempts, weakest topic, strongest topic, and per-topic improvement between attempts.

---

## 9. PDF Report Specification

Render a clean Thymeleaf HTML template → convert to PDF. Contents:

1. **Header** — "PCA Physics — Performance Report", student name, year, attempt number, date.
2. **Overall summary** — Total X / Y (Z%), overall status, average across attempts.
3. **Topic-wise table** — columns: Topic | Marks | % | Status — with color coding (red = weak, amber = mid, green = strong).
4. **Areas Needing Attention** — for each WEAK topic/question:
   - Topic name + question number(s) + the student's score.
   - **"What to study"** → the teacher's `QuestionReference` items (books + page numbers, links, notes).
   - **"Questions to practice"** → other questions on the same topic (and any `PRACTICE`-type references).
5. **Your Strengths** — list strong topics for encouragement.
6. **Progress section** (only if the student has more than one attempt) — per-topic and overall comparison across attempts with improvement deltas.
7. **Footer** — generated date.

The PDF must be the "complete knowledge" deliverable: weak area → why → exactly what to read → which questions to redo.

---

## 10. Frontend / Pages (Thymeleaf + Bootstrap 5 + Chart.js)

Pages: `login`, `register`, `student/dashboard`, `student/attempt-form`, `student/analysis`, `student/progress`, `teacher/dashboard`, `teacher/papers`, `teacher/questions`, `teacher/references`, plus the PDF template.

- Use a shared layout/fragment for the navbar (role-aware) and footer.
- Bootstrap 5 and Chart.js via CDN.
- Mobile-responsive (students mostly use phones).
- Clean, simple, education-friendly UI — not generic; use clear color coding for weak/mid/strong.

---

## 11. Suggested Build Order

1. Add dependencies to `pom.xml`; set up `.env` + `application.properties`; confirm the app connects to Supabase and starts.
2. Create all JPA entities, enums, and repositories.
3. Spring Security config (form login, role-based access, BCrypt) + the teacher seed initializer.
4. Auth pages (login, register) + role-based post-login redirect + role-aware navbar.
5. Teacher side: papers → questions → references CRUD.
6. Student side: attempt entry form (with validation) → save attempt + question scores.
7. Analysis service (classification, aggregation, progress deltas).
8. Student analysis + progress pages with Chart.js graphs.
9. PDF report generation.
10. Polish UI, add dashboards, test the full flow end-to-end.

---

## 12. Important Notes / Gotchas

- **Supabase + Hibernate:** Prefer the **direct connection (port 5432)**. If you must use Supabase's **transaction pooler (port 6543)**, disable prepared-statement caching by adding `?prepareThreshold=0` to the JDBC URL, or Hibernate will throw prepared-statement errors. Supabase requires SSL — add `?sslmode=require` if you hit SSL errors.
- **Secrets:** Everything sensitive (teacher creds, DB password) lives in `.env`, referenced via `${...}`. Add `.env` to `.gitignore`. Change `teacher123@` before any real deployment.
- **Passwords:** Always BCrypt-hashed. Never log or store plaintext.
- **Marks validation:** Enforce `0 ≤ marksObtained ≤ maxMarks` on the server, not just the UI.
- **Attempt numbering:** Compute the next attempt number server-side per (student, paper) so it can't be spoofed.
- Keep classification thresholds in one constants/config place so they're easy to tune later.
