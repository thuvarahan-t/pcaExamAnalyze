# PCA MCQ — 1,300 student concurrency and durability validation

## Resource ceiling (Render Free)

Render Free is **0.1 CPU and 512 MB RAM**. This is a *single-instance, non-production* tier that sleeps on inactivity and can restart. Neither a passing unit-test suite nor this PR can guarantee simultaneous availability for 1,300 actively saving students. A free instance cannot be treated as a guaranteed exam-day service.

The risk is amplified by 1,300 x 50 = **65,000 individual autosaves**, plus session initiation, question/image requests, timers, and final submissions. Use a staging deployment and staging Supabase database. **Never point the load generator at the live academy exam database.**

## What the PR protects

- Each autosave/clear acquires a lock on its own parent submission row **before** changing answer rows; final submission acquires the same lock **before** reading/scoring answers. This prevents the SQL/Hibernate race that could otherwise overwrite or allow late answers.
- Answer saves and clears remain idempotent; failed requests should be retried by the browser and final submissions include all currently selected answers.
- Timed auto-submission sweeps only metadata, processes a maximum of 100 overdue papers each run, and executes each finalize in a separately locked transaction.
- Free-tier defaults constrain Tomcat threads, Hikari connections and PDF rendering. Configure JVM heap separately in Render.
- Real PostgreSQL integration regression covers the production SQL; H2 regressions cover 50 answers, duplicate submissions, invalid options, lock races and independent concurrent submissions.

## CI validation

The workflow `.github/workflows/exam-concurrency-ci.yml` runs `bash ./mvnw -B -ntp verify` with a PostgreSQL 16 service. JaCoCo coverage HTML/XML, JUnit/Surefire results are uploaded to the workflow artifact. This reports **actual coverage**; 100% coverage is **not** an assertion or a substitute for end-to-end concurrency testing.

Do not merge while the CI workflow is red, or before running the staging load and examining operational metrics.

## Load test (manual, destructive to test database)

1. Provision an isolated staging Supabase PostgreSQL database with a published, open 50-question exam, a complete answer key, and an active batch. Disable result publication if that is useful for the fixture.
2. Give the staging web service its own DB credentials and pre-warm it. Never reuse the production DB credentials. Ensure there is room for 1,300 synthetic submissions.
3. Install [k6](https://grafana.com/docs/k6/latest/set-up/install-k6/) on a separate load-generator machine. The generator must **not** run inside the Render Free instance.
4. Run:

```bash
ALLOW_DESTRUCTIVE_LOAD_TEST=yes \
BASE_URL="https://your-staging-host.example" \
EXAM_SLUG="your-test-exam-slug" \
BATCH="2028 A/L" \
k6 run docs/load-testing/1300-student-submit.js
```

5. Check status code distribution, HTTP P95/P99, rejected writes, `final_submissions` rate, memory/restarter events, Hikari pool wait and PostgreSQL connection utilization. Then query the staging DB for exact counts:

```sql
SELECT s.status, COUNT(*) AS papers
FROM mcq_submissions s JOIN mcq_exams e ON e.id = s.exam_id
WHERE e.slug = 'your-test-exam-slug'
  AND s.registration_id LIKE 'LOAD%'
GROUP BY s.status;

SELECT COUNT(DISTINCT a.submission_id) AS complete_papers
FROM mcq_submission_answers a
JOIN mcq_submissions s ON s.id = a.submission_id
JOIN mcq_exams e ON e.id = s.exam_id
WHERE e.slug = 'your-test-exam-slug'
  AND s.registration_id LIKE 'LOAD%'
GROUP BY a.submission_id
HAVING COUNT(*) = 50;
```

The second query returns **one row per complete submission**; count its rows, not its first result. Compare to 1,300 unique test registrations. Check records with fewer than 50 rows, wrong answer values or non-SUBMITTED status separately.

## Acceptance criteria for a real exam

- Every submitted student has 50 persisted answers and a receipt, with zero overwrites/missing updates, confirmed by database queries.
- All 1,300 simulated users complete or receive clear retriable failures; NO unexplained acknowledged data loss.
- No app restarts/OOM, no SQL deadlocks, no pool exhaustion or excessive connection wait.
- Test both OMR and question-image exam modes, release gates, answer clearing, expired timers, free marks, repeated clicks, reconnects, resuming sessions, 503s and retries.
- Record meaningful P95/P99 and success rate targets before launch. Failures require a capacity upgrade or architectural changes, not weakening test thresholds.

### Production readiness warning

Passing at 1,300 concurrent users on 0.1 CPU is not assured. For a high-stakes assessment, budget a load-tested paid instance or split static content and writes so the database durably accepts submissions independently of PDF generation. Avoid running expensive PDF/report workloads during the final-submission peak. Retain off-platform database backups and an outage recovery plan.
