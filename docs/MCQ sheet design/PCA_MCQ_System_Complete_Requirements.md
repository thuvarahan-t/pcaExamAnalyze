# Physics Cube Academy (PCA) — Two-Portal Platform and MCQ Exam System

## Complete Product Requirements, User Flows, Security Rules, Data Model, and Implementation Plan

**Project:** PCA Exam Platform  
**Existing module:** PCA Exam Analyzer  
**New module:** PCA MCQ Exam System  
**Primary users:** Students and PCA exam administrators  
**Implementation stack:** Existing Spring Boot 4, Java 21, Thymeleaf, PostgreSQL/Supabase, H2 development profile  
**Student authentication rule:** Students do not create an account and do not log in to the MCQ Exam System  
**Admin authentication rule:** Every MCQ admin page and action requires a secure admin login

---

# 1. Product Decision

The PCA website must open with a portal-selection home page. It must not immediately send a visitor into one existing module.

The home page contains two primary choices:

1. **PCA Exam Analyzer**
   - The existing authenticated application.
   - Students enter past-paper marks and receive performance analysis.
   - Teachers manage paper structures, questions, topics, and references.

2. **PCA MCQ Exam System**
   - A separate public student experience inside the same PCA application.
   - Students browse available exam papers without logging in.
   - A student selects an exam, provides identity and academic details, and starts the exam.
   - Admin users log in separately to create and control exams.

The modules share the PCA brand and deployment, but their routes, domain models, workflows, and data must remain clearly separated.

---

# 2. Top-Level Navigation and Routes

Recommended route structure:

```text
GET  /                         Two-portal PCA home page

GET  /login                    Existing Exam Analyzer login
GET  /register                 Existing Exam Analyzer registration
GET  /student/**               Existing student analyzer area
GET  /teacher/**               Existing teacher analyzer area

GET  /exam                     Public MCQ Exam System landing and exam list
GET  /exam/details             Reusable passwordless student-details form
GET  /exam/exams/{examId}      Public exam details
POST /exam/exams/{examId}/start
GET  /exam/session/{token}     Active student exam session
GET  /exam/result              Public result/resume lookup

GET  /exam/admin/login         MCQ admin login
GET  /exam/admin/**            Protected MCQ administration area
```

The root page `/` must remain available even when an Exam Analyzer user is already signed in. A signed-in user may use the Analyzer portal card to open their appropriate dashboard.

---

# 3. Complete Student Flow

```text
PCA home page
    ↓
Select “PCA MCQ Exam System”
    ↓
View all student-visible exam papers
    ↓
Filter by year, month, batch, stream, or status
    ↓
Select an exam
    ↓
View exam details, availability, instructions, and paper information
    ↓
Enter student details
    ↓
Server validates details and creates/resumes a secure exam session
    ↓
View PDF question paper and MCQ answer sheet
    ↓
Answers save locally and sync to the backend
    ↓
Review unanswered/flagged questions
    ↓
Confirm final submission
    ↓
View submission receipt
    ↓
View pending result or released corrected answer sheet
```

No student password is requested at any point in this flow.

---

# 4. PCA Home Page Requirements

The first page must communicate that PCA provides two different tools.

## 4.1 Portal cards

Each portal card must contain:

- Portal name
- One-sentence explanation
- Clear primary action
- Distinct visual icon
- Keyboard focus state
- Minimum 44 px touch target
- Responsive layout with no horizontal overflow

### Analyzer card

Suggested copy:

```text
PCA Exam Analyzer
Record your past-paper marks, understand topic performance, and receive a personalized study report.
```

Actions:

- Signed-out visitor: `Open Analyzer`
- Signed-in student: `Open Student Dashboard`
- Signed-in teacher: `Open Teacher Dashboard`

### MCQ Exam System card

Suggested copy:

```text
PCA MCQ Exam System
Browse available PCA examinations, complete the digital MCQ answer sheet, and check released results.
```

Action:

- `View Exam Papers`

The MCQ action must never redirect a student to the existing Analyzer login page.

---

# 5. MCQ Exam Landing Page

The `/exam` page is public and must show every exam that is eligible for student visibility.

## 5.1 Exam visibility

- `DRAFT`: never visible to students
- `SCHEDULED`: visible as upcoming, with opening time/countdown
- `OPEN`: visible and can be started
- `CLOSED`: visible but cannot accept a new/final submission
- `RESULT_PENDING`: visible with a pending-result state
- `RESULT_RELEASED`: visible and allows result lookup
- `ARCHIVED`: hidden by default; optionally available through history

## 5.2 Filters

- Exam year
- Exam month
- Batch
- Stream
- Status
- Search by exam name

Filters must be populated from stored data, not hard-coded in frontend JavaScript.

## 5.3 Exam card

Each card shows:

- Exam name
- Batch or eligible batches
- Stream or eligible streams
- Year and month
- Opening time
- Closing time
- Status badge
- Duration, when configured
- Result release information, when appropriate
- `View Exam` action

## 5.4 Empty state

When no exam matches the filters:

```text
No exam papers match your selection. Change the filters or check again later.
```

---

# 6. Enter Details Form

The Exam Portal header must use a liquid-glass navigation surface and show actions in this order: **My Details**, **My Result**, icon-only **Home**, and icon-only **Admin Login**. The saved-name greeting `Hi, {Full Name}` is a separate navigation element and must not appear inside My Details. Hide Admin Login on mobile. Selecting My Details opens a modal over the Exam Portal; it must not navigate to a visually separate details page. A student may save details before selecting an exam. The same saved values are used after a student selects an exam and before an exam session starts.

The modal must visually follow the existing Exam Analyzer registration popup:

- Centered white/glass card over a dark blurred overlay
- PCA logo and short heading
- Two-column field grid on desktop
- Single-column, vertically scrollable form on mobile
- Same input, validation, select, and district-dropdown styling as Analyzer registration
- Batch and Stream must use the same searchable glass dropdown style as District
- District panel must always be clamped inside the visible viewport
- Desktop district map click must select and submit the district reliably
- Mobile uses the searchable district list without rendering the map
- Close button, outside-click close, and Escape-key close
- Only MCQ-required fields; no Analyzer username, password, WhatsApp, or account controls

When valid details already exist in the current secure server session:

- Mark the My Details tab with a saved indicator.
- Autofill the details for every selected exam.
- Let the student review or edit the values before starting.
- Do not require the student to type the same profile for every exam.
- Provide a clear action for shared devices.

Required fields:

| Field | Rule |
|---|---|
| Email Address | Valid email with a complete domain; trim spaces; store lowercase |
| Registration ID | Required; trimmed; normalized; stable student identifier |
| NIC Number | Exactly 12 digits; letters and other characters are not accepted |
| Full Name | Required; 3–150 letters; automatically format every word in Title Case |
| Batch | Required; select from admin-managed values |
| School | Required; 2–200 characters or managed searchable selection |
| Stream | Required; select from admin-managed values |
| District | Required; select from Sri Lanka’s 25 districts |

Version 1 Stream values are limited to `Bio Science` and `Physical Science`.

## 6.1 Form behavior

- Validate fields on blur and on submit.
- Enforce Email, NIC, and Full Name rules in both the browser and backend.
- Display field-specific errors.
- Never include identity values in the URL.
- Never expose a complete NIC in a receipt, result page, analytics table, log, or exported filename.
- Require acknowledgement of the exam rules and privacy notice.
- Do not ask the student to create a username or password.
- Store reusable details in the server session; do not place NIC or contact details in browser local storage.
- Close the modal after a successful save, show a quiet saved indicator on My Details, and show the saved student name separately in the navigation.
- Do not show technical storage/privacy notification banners inside the form.
- Provide `Clear saved details`, which removes the profile from the current server session.
- Do not use the provided example student record as production seed data.

## 6.2 Student matching

Recommended matching sequence:

1. Normalize Registration ID and NIC.
2. Look up an existing MCQ student by normalized Registration ID.
3. Verify the NIC using a secure lookup hash.
4. If no student exists, create one.
5. If identity matches, update allowed profile fields such as school, stream, district, batch, and email.
6. If Registration ID exists but NIC does not match, reject the start request without revealing which value was wrong.

Recommended user-facing error:

```text
We could not verify these student details. Check the information and try again.
```

---

# 7. Passwordless Exam Session

Although there is no student login, the system must create a secure temporary exam session.

## 7.1 Start session

After successful validation, the backend must:

- Confirm that the exam can currently be opened.
- Find or create the MCQ student.
- Find or create a draft submission.
- Generate a cryptographically random, opaque session token.
- Store only a hash of the session token in the database.
- Send the token through a secure, HttpOnly, SameSite cookie or an equally secure mechanism.
- Set a sensible expiry based on exam closing time and a short recovery window.

## 7.2 Resume session

A valid session can restore:

- Student identity reference
- Exam reference
- Saved answers
- Flagged questions
- Progress
- Submission status

An expired or invalid session must not reveal student answers.

## 7.3 Resume without the original browser

Provide a recovery form requiring:

- Registration ID
- NIC Number
- Exam selection

Rate-limit this endpoint. Return a generic failure message for all identity mismatches.

---

# 8. Exam Details and Start Rules

Before showing the student-details form, display:

- Exam title
- Description
- Batch and stream eligibility
- Number of questions
- Number of answer options
- Opening and closing time
- Duration, if configured
- Submission policy
- Result policy
- PDF availability
- Instructions

The backend must re-check all rules when the student starts the exam. Frontend countdowns are informational only.

A student cannot start when:

- The exam is a draft or archived
- The opening time has not arrived
- The closing time has passed
- The student’s batch or stream is not eligible
- A locked final submission already exists and resubmission is disabled

---

# 9. Question Paper PDF

The exam may use a validated Google Drive link initially.

Supported source example:

```text
https://drive.google.com/file/d/FILE_ID/view?usp=sharing
```

Derived URLs:

```text
Preview:  https://drive.google.com/file/d/FILE_ID/preview
Download: https://drive.google.com/uc?export=download&id=FILE_ID
```

Requirements:

- Validate and extract the Drive file ID on the server.
- Do not store private Drive access tokens in HTML or JavaScript.
- Lazy-load the embedded preview.
- Provide an explicit download/open fallback.
- Display a helpful error when preview loading fails.
- On desktop, show PDF and answer sheet side-by-side.
- On mobile, show a collapsible/sticky paper control followed by the answer sheet.
- The PDF must never cover the answer sheet or submit controls.

Direct managed PDF upload may be added later.

---

# 10. MCQ Answer Sheet

The answer sheet follows the supplied OMR-style reference.

Default exam configuration:

- 50 questions
- 5 options per question
- Option values 1–5

The database and component may support configurable counts, but Version 1 UI and validation must guarantee the PCA default correctly.

## 10.1 Layout

Desktop:

- Five groups of ten questions
- Questions 1–10, 11–20, 21–30, 31–40, and 41–50

Tablet:

- Two or three responsive groups

Mobile:

- One question per row
- Question number on the left
- Five answer buttons on the right
- Minimum 44 × 44 px touch targets where space permits
- Sticky progress header
- Sticky bottom action bar

## 10.2 Selection interaction

When an option is selected:

1. Remove the previous selection for that question.
2. Apply a blue selected ring.
3. Animate a clear hand-drawn `X` over the selected number.
4. Update answered/unanswered totals.
5. Save locally immediately.
6. Queue a backend autosave.

Only one selected option is allowed per question.

## 10.3 States

- Unanswered
- Answered
- Flagged for review
- Correct after release
- Incorrect after release
- Correct answer for an unanswered question

## 10.4 Navigator

Provide a 1–50 navigator:

- Neutral: unanswered
- Blue: answered
- Amber: flagged
- Green: correct result
- Red: incorrect result

Selecting a navigator number scrolls/focuses the corresponding question.

---

# 11. Autosave and Offline Reliability

## 11.1 Local save

- Save answer changes to browser storage immediately.
- Scope storage keys by exam and anonymous session, never by NIC.
- Do not store email, full name, or NIC in local storage.
- Restore progress after refresh.

## 11.2 Backend sync

- Debounce answer updates to avoid one full submission request per click.
- Send only changed answers where practical.
- Validate session ownership, question number, and option range server-side.
- Use an update version or equivalent concurrency control.

Status labels:

- `Saving…`
- `Saved`
- `Offline changes saved on this device`
- `Could not sync — retrying`

## 11.3 Network recovery

- Never erase locally saved answers when a request fails.
- Display an offline indicator.
- Retry pending changes after connectivity returns.
- Require confirmed server synchronization before final submission.
- Do not claim final submission success from a client-only state.

---

# 12. Final Submission

## 12.1 Pre-submit dialog

Show:

- Answered count
- Unanswered count
- Flagged count
- Closing time
- Warning that the final submission may be locked

Example:

```text
You answered 46 of 50 questions.
4 questions remain unanswered.
```

Actions:

- Review answers
- Review unanswered
- Submit final answers

## 12.2 Server transaction

Final submission must be transactional and idempotent:

1. Lock or version-check the submission.
2. Verify the session and student ownership.
3. Verify the exam is open for submission.
4. Validate every stored answer.
5. Apply the configured resubmission policy.
6. Mark the submission final.
7. Record the submitted timestamp.
8. Calculate the result only when a complete answer key is available.
9. Commit once.

A repeated request with the same idempotency key must return the original successful result rather than creating another submission.

## 12.3 Receipt

Show:

- Submission receipt number
- Exam name
- Masked Registration ID when appropriate
- Submission time
- Answered count
- Current result status

Never show the complete NIC.

---

# 13. Duplicate and Resubmission Rules

Default:

- One student record per Registration ID.
- One active submission per student and exam.
- Student can edit until final submission.
- Final submission is locked.

Configurable admin policies:

- Allow resubmission before closing time
- Keep first final submission
- Keep latest final submission
- Admin reopens a submission with an audit reason

Required unique constraint:

```text
exam_id + student_id
```

Application checks alone are not sufficient; the database must enforce uniqueness.

---

# 14. Scoring and Results

Default scoring:

- Correct: +1
- Incorrect: 0
- Unanswered: 0
- No negative marking in Version 1

Calculations:

```text
correct_count   = correct selected answers
incorrect_count = wrong selected answers
unanswered_count = total_questions - answered_count
score           = correct_count
percentage      = score / total_questions × 100
```

The backend is the only scoring authority.

## 14.1 Release conditions

A corrected result may be shown only when:

```text
submission is final
AND student identity/session is verified
AND answer key contains every required answer
AND current time is at or after result_release_at
AND result publication is enabled
AND submission is not invalidated
```

## 14.2 Pending result

Show a pending state when:

- The answer key is incomplete
- The release time has not arrived
- Publication has not been enabled
- The exam is still in progress and results are intentionally withheld

Do not reveal the answer key, score, or correctness before release.

## 14.3 Released result

Show:

- Score and percentage
- Correct, incorrect, and unanswered totals
- Pass/fail when a pass mark exists
- Corrected answer sheet
- Student selection in blue/red as appropriate
- Correct answer in green
- Clear legend

Rank must remain disabled unless explicitly configured and privacy-reviewed.

---

# 15. Result and Session Lookup

Students do not log in. Result or session recovery requires:

- Registration ID
- NIC Number
- Selected exam

Security rules:

- Normalize before comparison.
- Compare NIC through a secure server-side lookup mechanism.
- Rate-limit by IP and Registration ID hash.
- Use generic errors.
- Do not include identity data in query strings.
- Log suspicious repeated failures without logging raw NIC values.

Possible responses:

- Submission not found
- Draft submission restored
- Submission received, result pending
- Result released
- Submission invalidated — contact PCA

---

# 16. Admin Authentication and Authorization

Students are passwordless, but admins must authenticate.

Admin login fields:

- Username or email
- Password
- Remember me, when securely configured

Requirements:

- BCrypt or stronger password hashing
- Secure session cookie
- CSRF protection
- Failed-login rate limiting
- Role-based authorization
- No public access to `/exam/admin/**`
- Audit important changes
- Generic authentication errors

Initial roles:

- `MCQ_ADMIN`: full exam management
- `MCQ_VIEWER`: read-only submissions and analytics, optional later

The existing PCA teacher may be granted `MCQ_ADMIN`, but MCQ permissions must be checked explicitly rather than assumed from URL visibility.

---

# 17. Admin Dashboard

Summary cards:

- Total exams
- Open exams
- Upcoming exams
- Total submissions
- Results pending
- Results released
- Active batches
- Average score

Sections:

- Recent submissions
- Upcoming schedule
- Latest analytics
- Quick actions

Quick actions:

- Create exam
- Manage exams
- Add answer key
- Release results
- Export results

Admin UI is optimized for desktop, while still providing a readable small-screen warning/fallback.

---

# 18. Create and Manage Exams

Required fields:

- Exam name
- Slug generated server-side
- Description/instructions
- Eligible batch or batches
- Eligible stream or streams
- Exam year
- Exam month
- Google Drive PDF link
- Total questions, default 50
- Options per question, default 5
- Opening date/time
- Closing date/time
- Result release date/time
- Publication state

Optional fields:

- Duration
- Pass mark
- Allow resubmission
- Show countdown
- Allow PDF download
- Result message
- Show rank

Validation:

- `close_at` must be later than `open_at`.
- `result_release_at` must not unintentionally expose results before closing.
- Question and option counts must be within configured safe ranges.
- Duplicate slugs must be rejected/regenerated.
- Drive links must be validated.
- Eligibility must contain at least one batch and stream, unless explicitly marked open to all.

Admin actions:

- Save draft without answer key
- Schedule/publish
- Edit
- Archive
- Close early
- Extend closing time
- Publish/unpublish results
- Duplicate an exam without submissions

Do not permanently delete an exam that has submissions. Archive it.

---

# 19. Answer Key Editor

Use the same answer-sheet visual component in `admin-answer-key` mode.

Capabilities:

- Choose one correct option per question
- Save partial key while exam remains draft/pending
- Clear all with confirmation
- Show missing question numbers
- Validate completeness
- Confirm before replacing a complete key

Result publication must fail if the answer key is incomplete.

Changing an answer key after results were calculated must:

1. Require explicit confirmation and reason.
2. Create an audit event.
3. Recalculate affected results transactionally.
4. Clearly notify the admin of the number of changed results.

---

# 20. Submission Management

Submission table columns:

- Receipt number
- Student name
- Registration ID
- Masked NIC
- Email
- Batch
- School
- Stream
- District
- Started time
- Submitted time
- Status
- Score, only when permitted

Filters:

- Exam
- Submission status
- Batch
- Stream
- District
- Score range
- Submitted date
- Search by name or Registration ID

Actions:

- View answer sheet
- View student profile
- Reopen with reason
- Invalidate with reason
- Recalculate result
- Export permitted data

Admin lists must never show complete NIC values.

---

# 21. Analytics and Export

Exam analytics:

- Submission count
- Completion rate
- Average, median, highest, and lowest score
- Pass rate
- Score distribution
- Correct/incorrect/unanswered percentage per question
- Most difficult questions
- Batch comparison
- Stream comparison
- District comparison when privacy thresholds are met
- Start-to-submit duration

Exports:

- CSV is required for Version 1
- Excel may follow
- Apply role checks and audit exports
- Do not include raw NIC by default
- Use UTF-8 and stable column names
- Prevent spreadsheet formula injection by escaping risky cell prefixes

---

# 22. Data Model

The MCQ schema is separate from the existing Analyzer `Attempt` and `QuestionScore` entities.

## 22.1 `mcq_students`

```text
id
registration_id_normalized
email_normalized
full_name
nic_lookup_hash
nic_encrypted_optional
batch_id
school
stream_id
district
created_at
updated_at
```

Indexes:

```text
UNIQUE registration_id_normalized
INDEX email_normalized
INDEX batch_id
```

## 22.2 `mcq_batches`

```text
id
name
year
is_active
created_at
updated_at
```

## 22.3 `mcq_streams`

```text
id
name
is_active
created_at
updated_at
```

## 22.4 `mcq_exams`

```text
id
name
slug
description
instructions
exam_year
exam_month
paper_drive_url
paper_preview_url
paper_download_url
total_questions
options_per_question
open_at
close_at
result_release_at
publication_state
manual_close
results_published
allow_resubmission
duration_minutes
pass_mark
show_rank
created_by_user_id
created_at
updated_at
archived_at
version
```

Use timezone-aware instants. Display all PCA operational times in `Asia/Colombo` unless configured otherwise.

## 22.5 Eligibility mappings

```text
mcq_exam_batches(exam_id, batch_id)
mcq_exam_streams(exam_id, stream_id)
```

Both mappings require composite unique constraints.

## 22.6 `mcq_answer_keys`

```text
id
exam_id
question_number
correct_option
created_at
updated_at
```

Constraint:

```text
UNIQUE (exam_id, question_number)
```

## 22.7 `mcq_submissions`

```text
id
receipt_number
exam_id
student_id
status
started_at
submitted_at
score
percentage
correct_count
incorrect_count
unanswered_count
result_calculated_at
is_invalidated
admin_note
reopened_at
created_at
updated_at
version
```

Constraint:

```text
UNIQUE (exam_id, student_id)
```

## 22.8 `mcq_submission_answers`

```text
id
submission_id
question_number
selected_option nullable
is_flagged
is_correct nullable until calculation
created_at
updated_at
```

Constraint:

```text
UNIQUE (submission_id, question_number)
```

## 22.9 `mcq_exam_sessions`

```text
id
submission_id
token_hash
expires_at
last_seen_at
revoked_at
created_at
```

Never store the raw bearer/session token.

## 22.10 `mcq_audit_logs`

```text
id
admin_user_id
action
entity_type
entity_id
old_values_json
new_values_json
reason
ip_address
created_at
```

Sensitive fields must be redacted before writing audit JSON.

---

# 23. Recommended Endpoints

Public/student:

```text
GET    /exam
GET    /exam/exams?year=&month=&batch=&stream=&status=&q=
GET    /exam/exams/{examId}
POST   /exam/exams/{examId}/start
GET    /exam/session
PATCH  /exam/session/answers
PATCH  /exam/session/flags
POST   /exam/session/submit
POST   /exam/session/recover
POST   /exam/result/lookup
GET    /exam/result/{receiptNumber}
```

Admin:

```text
GET    /exam/admin
GET    /exam/admin/exams
POST   /exam/admin/exams
GET    /exam/admin/exams/{examId}
POST   /exam/admin/exams/{examId}/update
POST   /exam/admin/exams/{examId}/archive
PUT    /exam/admin/exams/{examId}/answer-key
POST   /exam/admin/exams/{examId}/close
POST   /exam/admin/exams/{examId}/release-results
POST   /exam/admin/exams/{examId}/unpublish-results
GET    /exam/admin/exams/{examId}/submissions
GET    /exam/admin/exams/{examId}/analytics
GET    /exam/admin/exams/{examId}/export.csv
POST   /exam/admin/submissions/{submissionId}/reopen
POST   /exam/admin/submissions/{submissionId}/invalidate
```

Use CSRF protection for all state-changing browser requests.

---

# 24. Status Calculation

Avoid storing multiple contradictory status values. Store publication/manual override fields and derive the student-facing status.

Suggested priority:

```text
if archived_at exists                   => ARCHIVED
else if publication_state == DRAFT      => DRAFT
else if now < open_at                   => SCHEDULED
else if manual_close or now >= close_at => CLOSED or RESULT_PENDING
else                                    => OPEN

if submission final and results_published
   and now >= result_release_at
   and answer key complete              => RESULT_RELEASED
```

All status checks must be repeated on the server during start, autosave, submit, lookup, and result display.

---

# 25. Security and Privacy

Student identity information is sensitive.

Mandatory controls:

- TLS/HTTPS in production
- CSRF protection
- Secure, HttpOnly, SameSite cookies
- Strong random exam-session tokens
- Hash session tokens at rest
- Hash normalized NIC for lookup using a server-side pepper/HMAC
- Encrypt raw NIC only if a genuine operational need requires retention
- Mask NIC everywhere outside restricted profile handling
- Rate-limit start, recovery, lookup, and admin login endpoints
- Generic identity errors
- Server-side exam-time and eligibility validation
- Database uniqueness constraints
- Idempotent final submission
- Transactional scoring
- Output encoding and input validation
- Audit admin actions and exports
- Retention/deletion policy for student PII
- Backups and tested restore process

Do not:

- Treat NIC as a password without rate limiting
- Store NIC, email, or names in browser local storage
- Put student data in URLs
- Trust frontend scores or status
- Expose answer keys before release
- Log request bodies containing NIC

---

# 26. UI Design System

Use the supplied prototype as a visual reference, not as production application code.

Theme:

- PCA dark/navy or blue-and-white glass surfaces
- Strong blue primary actions
- Cyan highlights
- Green correct state
- Red incorrect state
- Amber warning/flag state
- Neutral unanswered state

Typography:

- Sora for headings where already available
- Inter or Plus Jakarta Sans for body text

Accessibility:

- Semantic forms and labels
- Keyboard-operable answer choices
- Visible focus
- ARIA label such as `Question 12, option 4`
- Status must not rely on color alone
- Reduced-motion support
- WCAG-compatible contrast
- Error summary plus field errors

Animation:

- Small selection scale/pulse
- Hand-drawn `X`
- Smooth progress changes
- Lightweight success state
- Respect `prefers-reduced-motion`

Avoid heavy animation libraries for Version 1.

---

# 27. Performance and Scalability

Initial target:

- Hundreds of concurrent students
- Thousands of stored submissions
- Multiple batches, streams, and monthly exams

Requirements:

- Fast mobile landing page
- Lazy-loaded PDF
- Compressed/static cached assets
- Indexed student, exam, and submission queries
- Debounced incremental autosaves
- No N+1 query loops in admin submission views
- Paginated admin tables
- Server-side analytics queries
- Connection pool sized for deployment limits
- Load testing for start, autosave, and final-submit bursts

Target Lighthouse mobile performance score: above 85 after production optimization.

---

# 28. Error and Empty States

Required states:

- No exams available
- Exam not open yet
- Exam closed
- Student is not eligible
- Identity verification failed
- Existing final submission
- Session expired
- Session restored
- PDF unavailable
- Offline
- Autosave failed/retrying
- Submission failed safely
- Submission successful
- Result pending
- Result released
- Result unavailable/invalidated
- Admin table empty
- Export failed

Every error must state what the student can safely do next without exposing internal details.

---

# 29. Version 1 Acceptance Criteria

## Home and navigation

- `/` displays two clearly distinct portal choices.
- Analyzer choice opens the existing Analyzer authentication/dashboard flow.
- MCQ choice opens `/exam` without requesting Analyzer login.
- Exam Portal header provides My Details, My Result, Home, and Admin Login actions in a responsive liquid-glass navigation bar.
- The saved-name greeting is separate from the buttons, and Admin Login is hidden on mobile.
- My Details, My Result, and Admin Login open as responsive popups over the Exam Portal.
- Admin Login authenticates against the environment-seeded teacher account and routes successful teacher authentication to the teacher dashboard.
- On application startup, the configured teacher username, password hash, display name, role, and enabled state are synchronized from the environment configuration.
- Student can save, edit, and clear reusable details before selecting an exam.
- Saved details autofill each exam start form during the secure server session.
- Root page remains accessible to authenticated Analyzer users.
- Both choices work from 320 px mobile width upward.

## Student exam system

- Student can view all student-visible exam papers without login.
- Draft/archived exams are not leaked.
- Filters work using persisted exam data.
- Student can select an exam and see its instructions/status.
- Student can enter Email, Registration ID, NIC, Full Name, Batch, School, Stream, and District.
- Form values are normalized and validated server-side.
- Student receives a secure passwordless exam session.
- Student can preview/open the PDF.
- Student can answer 50 questions with 5 options.
- Selected option displays the animated `X`.
- Student can flag and review questions.
- Progress is accurate.
- Answers survive refresh and temporary network failure.
- Backend autosave validates every answer.
- Final submission is transactional and idempotent.
- Duplicate submissions are prevented by database constraint.
- Student receives a receipt.
- Result remains hidden until every release condition passes.
- Released result shows score and corrected answer sheet.
- Full NIC is never exposed.

## Admin

- Public users cannot access `/exam/admin/**`.
- Authorized admin can create and edit an exam.
- Admin can manage batch and stream eligibility.
- Admin can validate a PDF link.
- Admin can set open, close, and release times.
- Admin can save a draft without an answer key.
- Admin can enter and validate the complete answer key.
- Admin can view and filter submissions.
- Admin can publish/unpublish results safely.
- Admin can see basic exam and question analytics.
- Admin can export a safe CSV.
- Sensitive actions create audit events.

## Quality

- No student page has horizontal overflow at 320 px.
- Forms are keyboard accessible.
- Reduced-motion mode works.
- No answer is lost during an ordinary refresh.
- No score or answer key is trusted from the browser.
- Status and result conditions are enforced server-side.
- H2 development and PostgreSQL production profiles both pass tests.

---

# 30. Implementation Phases

## Phase 1 — Portal gateway and foundation

- Two-choice PCA home page
- Public `/exam` route and exam-list shell
- Separate MCQ package/module boundaries
- Database migration strategy
- MCQ exam, batch, stream, and eligibility models
- Admin authorization boundary

## Phase 2 — Admin exam management

- Admin dashboard
- Create/edit/archive exam
- Scheduling
- PDF validation/preview
- Answer-key editor
- Server-derived status

## Phase 3 — Passwordless student identity and session

- Student details form with eight required fields
- Normalization and safe identity matching
- Session-token creation and recovery
- Eligibility checks
- Privacy controls and rate limiting

## Phase 4 — Exam experience

- PDF/answer-sheet layout
- Reusable MCQ answer component
- Local save
- Backend autosave
- Offline recovery
- Navigator, flags, and progress

## Phase 5 — Submission and results

- Transactional/idempotent submission
- Scoring
- Receipt
- Pending state
- Publication controls
- Corrected answer sheet

## Phase 6 — Operations and launch

- Submission management
- Analytics
- Safe CSV export
- Audit logs
- Accessibility testing
- Mobile/browser testing
- Load/security testing
- Backup and deployment validation

---

# 31. Non-Negotiable Implementation Rules

1. Do not require a student account or password for the MCQ Exam System.
2. Do not send the MCQ portal button to the existing Analyzer login.
3. Keep existing Analyzer attempts separate from MCQ submissions.
4. Do not trust frontend times, scores, eligibility, or result state.
5. Do not expose full NIC values.
6. Do not store raw exam-session tokens.
7. Do not create duplicate final submissions.
8. Use one reusable answer sheet in student, answer-key, and result modes.
9. Allow an exam draft to exist without an answer key.
10. Never release a result with an incomplete key.
11. Preserve answers during refresh and temporary disconnection.
12. Require backend confirmation before displaying submission success.
13. Do not hard-code batches, streams, exams, answer keys, or analytics.
14. Archive exams with submissions instead of deleting them.
15. Maintain mobile usability from 320 px upward.
16. Keep all admin actions authenticated, authorized, CSRF-protected, and auditable.

---

# 32. Definition of the Confirmed Product

The confirmed PCA experience is:

```text
One PCA website
  ├── Existing PCA Exam Analyzer (account login required)
  └── New PCA MCQ Exam System
        ├── Students: no login, enter verified details after choosing an exam
        └── Admins: secure login required
```

This document supersedes the earlier proposal that asked every MCQ student to log in or enter details before seeing exam papers. Students first browse/select an exam, then provide their details, then receive a secure temporary exam session.

---

# 33. Implemented Admin Portal (13 July 2026)

The prototype's admin workflow is now implemented as a server-backed Spring MVC portal.

## Access and authorization

- `/exam/admin-login` authenticates with the teacher credentials configured in `backend/.env`.
- Successful portal login redirects teachers to `/exam/admin`.
- Every `/exam/admin/**` route requires `ROLE_TEACHER`; public and student sessions cannot open it.
- Mutating actions use POST forms with Spring Security CSRF protection.

## Implemented screens and operations

- Clean dashboard with total/open exam counts, clickable active-batch management, clickable last-24-hour submissions grouped by exam, and the latest five exams. Prototype graphs and dashboard quick-action buttons are intentionally removed.
- Batches are database-managed: admins can add, rename, activate/deactivate, and delete unused batches from the dashboard popup. Active batches feed both admin and student dropdowns.
- Add/Edit Exam with a batch category, year/month, Google Drive paper link, schedule, duration, pass mark, instructions, and resubmission policy. Batch is discovery/filter metadata only; neither batch nor stream restricts a student from attempting any published paper.
- Reusable answer-key editor for up to 50 questions with 5 options, count, clear, and validation controls.
- Admin chooses the paper question count from 0 to 50; the answer-key editor and result denominator adapt to that count. Pass mark is not collected.
- Every exam receives a stable `/exam/p/{slug}` public link that admins can copy and share. Published links open and highlight the matching paper.
- Draft and published states, unique slugs, scheduled/open/closed/result-released status calculation in Asia/Colombo time.
- Manage Exams actions for edit, manual close, archive, release/unpublish results, and per-exam CSV export.
- Result release is blocked until the exam is closed and all 50 answer-key entries are present.
- Submissions screen with exam/search filters, masked NIC display, and masked CSV export.
- Manage Exams uses 10 records per page with an opening-date range filter. Submissions uses 20 records per page with a submitted-date range filter; search, exam and date filters persist across pagination links.
- Published (non-draft, non-archived) exams are loaded from the database into the public `/exam` paper list.
- The prototype's Analytics and Result Publishing navigation leads to the implemented dashboard charts and exam-management controls. Its Settings item remains informational because the supplied prototype does not define settings behavior.

## Persistence

- `mcq_exams` stores MCQ exam configuration independently from Analyzer paper structures.
- `mcq_exam_batches`, `mcq_exam_streams`, and `mcq_answer_keys` store eligibility and answer-key data.
- `mcq_submissions` stores the student snapshot, lifecycle status, receipt, and score used by admin reports.

Student answering, autosave, final submission, scoring, receipt, and result lookup remain the next product phases described above; the completed admin portal is ready to configure and publish their exams.
