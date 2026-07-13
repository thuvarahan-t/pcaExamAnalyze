# Physics Cube Academy (PCA) MCQ Examination System
## Complete Product Requirements, UI/UX Specification, Data Model, Workflows, and AI Implementation Brief

**Project name:** PCA MCQ Examination System  
**Organization:** Physics Cube Academy (PCA)  
**Primary users:** Students and Academy Administrators  
**Platform:** Responsive web application  
**Design direction:** Premium blue-and-white glassmorphism with smooth, game-like interactions  
**Main objective:** Allow students to select an exam, view the question paper PDF, mark 50 MCQ answers using a digital answer sheet, submit answers, and securely view released results.

---

# 1. Project Summary

Build a complete online MCQ examination platform similar to a simplified Google Form, but designed specifically for Physics Cube Academy.

The platform must contain two separate interfaces:

1. **Student Interface**
2. **Admin Interface**

The Student Interface must be fully optimized for mobile devices. It should feel smooth, modern, interactive, and game-like while remaining professional and easy to use.

The Admin Interface will mainly be used on laptops or desktop computers. It does not need full mobile optimization. On smaller screens, the admin area may show a message asking the administrator to use a laptop or desktop.

The MCQ answer sheet should visually follow the supplied reference sheet:

- 50 questions
- 5 answer choices for each question
- Clean OMR-style arrangement
- Students select answers by clicking a number
- The selected answer displays an animated `X` or tick-style mark over the selected option
- After results are released:
  - Correct responses appear in green
  - Incorrect responses appear in red
  - The correct answer is clearly shown

---

# 2. Recommended Technology Stack

The following stack is recommended for a modern, scalable, and maintainable implementation.

## Frontend

- **Next.js 15+**
- **TypeScript**
- **Tailwind CSS**
- **Framer Motion** for animations
- **React Hook Form**
- **Zod** for validation
- **Lucide React** for icons
- **Recharts** for admin analytics
- **React PDF Viewer** or Google Drive preview embed for paper viewing

## Backend

Choose one of the following:

### Preferred option

- **Supabase**
  - PostgreSQL database
  - Authentication
  - Row-level security
  - Realtime updates
  - Edge Functions if needed

### Alternative option

- Next.js API routes
- PostgreSQL
- Prisma ORM
- NextAuth/Auth.js for admin authentication

## Deployment

- **Vercel** for frontend and backend
- **Supabase** for database and authentication
- Optional custom domain such as:
  - `exam.physicscubeacademy.lk`
  - `mcq.physicscubeacademy.lk`

---

# 3. User Roles

## 3.1 Student

A student can:

- Enter personal details before starting
- Select batch, year, and month
- View available exams
- Open a specific exam
- Preview and download the question paper PDF
- Mark answers for 50 MCQs
- Submit answers
- View submission confirmation
- Check released results using Registration Number and NIC
- View score and corrected answer sheet

## 3.2 Admin

An admin can:

- Securely log in
- Create exams
- Edit exams
- Delete or archive exams
- Add or update correct answers
- Add a Google Drive PDF link
- Schedule exam opening time
- Schedule exam closing time if needed
- Schedule result release time
- View submissions
- View student details
- View exam analytics
- Export results
- Reopen or close exams
- Publish or unpublish results

---

# 4. Main Navigation Structure

## 4.1 Student Navigation

Recommended top navigation:

- PCA logo
- Home
- Exams
- My Result
- Help
- Admin button on desktop only

### Important responsive rule

The **Admin** button must:

- Appear only on laptop and desktop layouts
- Be hidden on mobile and small tablet layouts
- Use a subtle shield or lock icon
- Redirect to the admin login page

## 4.2 Admin Navigation

Recommended sidebar navigation:

- Dashboard
- Add Exam
- Manage Exams
- Submissions
- Students
- Analytics
- Result Publishing
- Settings
- Logout

The admin interface should use a collapsible sidebar for desktop.

---

# 5. Student Interface Workflow

## 5.1 Student Landing Page

The landing page should include:

- PCA logo
- Main heading:
  - `PCA MCQ Examination Portal`
- Supporting text:
  - `Select your examination, mark your answers, and track your performance.`
- Primary button:
  - `Start an Exam`
- Secondary button:
  - `View My Result`
- Animated physics-themed background elements:
  - Small particles
  - Orbit lines
  - Formula symbols
  - Subtle floating glass cards

The animations must be smooth and should not distract from reading.

---

## 5.2 Student Registration Form

Before viewing or starting an exam, the student must enter:

- Full Name
- Batch
- Registration Number
- Phone Number
- NIC Number

### Validation rules

#### Full Name

- Required
- Minimum 3 characters
- Letters and spaces only where possible

#### Batch

- Required
- Select dropdown
- Example values:
  - 2026 A/L
  - 2027 A/L
  - 2028 A/L
  - Repeat Batch
- Batch values should be managed by admin

#### Registration Number

- Required
- Must be unique per student where applicable
- Trim spaces
- Convert to lowercase

#### Phone Number

- Required
- Sri Lankan phone format
- Accept:
  - `0771234567`
  - `94771234567`
  - `+94771234567`
- Store in normalized format

#### NIC Number

- Required
- Support old and new Sri Lankan NIC formats
- Examples:
  - `991234567V`
  - `200012345678`
- Convert letters to uppercase
- Remove spaces

### Form behavior

- Display validation immediately but politely
- Use animated input focus states
- Show a small success check after a valid field
- Save entered information temporarily so the student does not need to re-enter it when moving between exam screens
- Never display full NIC publicly

---

## 5.3 Exam Filter Screen

After entering student details, show filters for:

- Exam Year
- Exam Month
- Batch

Example:

- Year: `2026`
- Month: `July`
- Batch: `2026 A/L`

After selecting year and month, display all exams matching the chosen criteria.

### Exam card example

**2026 Batch Physics Exam 01**

The card should show:

- Exam name
- Batch
- Month and year
- Exam date
- Opening time
- Closing time if applicable
- Total questions: 50
- Status:
  - Upcoming
  - Open
  - Closed
  - Result Released
- Action button:
  - `View Exam`
  - `Start Exam`
  - `View Result`
  - `Coming Soon`

### Empty state

When no exams exist for the selected month:

- Show a clean animated illustration
- Message:
  - `No exams are available for this month. Please select another month or check again later.`

---

# 6. Exam Status Rules

Each exam must have a clear status.

## 6.1 Draft

- Visible only to admin
- Not visible to students

## 6.2 Scheduled

- Visible to students as upcoming
- Cannot be opened before the scheduled opening time
- Show countdown timer

## 6.3 Open

- Students can access the PDF and answer sheet
- Students can submit answers

## 6.4 Closed

- Students can no longer submit
- Existing submissions remain stored
- Result may still be pending

## 6.5 Result Pending

- Exam is completed
- Correct answers or result release time is not yet available
- Student sees a waiting animation

## 6.6 Result Released

- Score becomes visible
- Corrected answer sheet becomes visible
- Correct answers are shown

---

# 7. Exam Details Page

When the student selects an exam, show:

- Exam name
- Batch
- Month and year
- Exam instructions
- Opening and closing time
- Question paper preview
- Download PDF button
- Open Full PDF button
- Start Answering button

## 7.1 PDF Preview

The admin will provide a Google Drive link.

The system must:

- Validate the Google Drive link
- Convert supported sharing links into preview links
- Display a small PDF preview inside a glass card
- Provide:
  - `Preview Paper`
  - `Open Full Paper`
  - `Download PDF`

### Fallback behavior

If the paper cannot be embedded:

- Display the paper title
- Show a PDF icon
- Provide a clear button to open it in a new browser tab

### Important

The PDF must not cover the answer sheet on mobile. Recommended layout:

#### Desktop

- Left side: PDF preview
- Right side: MCQ answer sheet
- Sticky panels where suitable

#### Mobile

- Top: Compact PDF preview card
- Buttons:
  - Open Paper
  - Download
- Below: MCQ answer sheet
- Optional floating button:
  - `View Paper`

---

# 8. MCQ Answer Sheet Design

The answer sheet must be based on the supplied OMR-style reference.

## 8.1 Structure

- Total questions: 50
- Options per question: 5
- Option values:
  - 1
  - 2
  - 3
  - 4
  - 5

## 8.2 Desktop layout

Recommended arrangement:

- 5 vertical groups
- Each group contains 10 questions

Example:

- Column 1: Questions 1 to 10
- Column 2: Questions 11 to 20
- Column 3: Questions 21 to 30
- Column 4: Questions 31 to 40
- Column 5: Questions 41 to 50

This closely matches the supplied answer sheet.

## 8.3 Tablet layout

- 2 or 3 columns depending on available width

## 8.4 Mobile layout

Recommended layout:

- One question per row
- Question number on the left
- Five circular answer buttons on the right
- Use the full available width
- Keep minimum touch target size of 44 × 44 px
- Use a sticky progress bar at the top
- Use a sticky bottom action bar with:
  - Answered count
  - Review unanswered
  - Submit

Alternative mobile layout:

- Two compact question cards per row only when screen width allows it without reducing touch accuracy

## 8.5 Answer selection interaction

When the student clicks an option:

1. The selected circle slightly scales up
2. A hand-drawn `X` animation appears over the selected number
3. The option receives a blue glowing ring
4. Any previous selection for the same question is removed
5. Progress count updates immediately
6. A subtle haptic-like visual pulse occurs

Example:

```text
Question 01

(1)   (2)   (3)   (4)   (5)
       X
```

Only one answer can be selected for each question.

## 8.6 Answer states

Each question can be:

- Unanswered
- Answered
- Flagged for review
- Correct after result release
- Incorrect after result release

## 8.7 Progress display

Show:

- `32 / 50 Answered`
- Percentage progress
- Animated progress ring or progress bar
- Number of unanswered questions
- Optional question navigator

## 8.8 Question navigator

Provide a compact grid with numbers 1 to 50.

Colors:

- Unanswered: neutral glass
- Answered: blue
- Flagged: amber
- Correct result: green
- Incorrect result: red

Clicking a number scrolls to that question.

---

# 9. Autosave and Exam Reliability

The system must protect student answers.

## 9.1 Autosave

- Save locally after every answer selection
- Sync to backend periodically when the student has started an authenticated submission session
- Display:
  - `Saved`
  - `Saving...`
  - `Offline changes saved locally`

## 9.2 Browser refresh

If the student refreshes the page:

- Restore previously marked answers
- Restore progress
- Show a message:
  - `Your saved answers have been restored.`

## 9.3 Network interruption

If internet connection is lost:

- Do not erase answers
- Display an offline indicator
- Continue saving locally
- Submit automatically or ask the student to retry after connection returns

## 9.4 Duplicate submission rule

Recommended default:

- One final submission per Registration Number, NIC, and Exam
- Admin may configure:
  - Allow only one submission
  - Allow resubmission before closing time
  - Keep latest submission
  - Keep first submission

Default recommendation:

- Allow students to edit until final submit
- Lock after final submit
- Admin can manually reopen a submission

---

# 10. Submit Process

When the student clicks `Submit Answers`:

## 10.1 Pre-submit validation

Show a confirmation modal containing:

- Number answered
- Number unanswered
- Number flagged
- Warning that submission may be final

Example:

```text
You answered 46 out of 50 questions.
4 questions are unanswered.

Do you want to submit now?
```

Buttons:

- Review Answers
- Submit Final Answers

## 10.2 Successful submission

After successful submission:

- Show animated success state
- Display:
  - Exam name
  - Student name
  - Registration Number
  - Submission time
  - Submission reference number
- Buttons:
  - `View My Result`
  - `Return to Exams`

Do not immediately reveal score unless result publishing conditions are satisfied.

---

# 11. Student Result Lookup

The result page must include:

- Registration Number
- NIC Number
- `View My Result` button

## 11.1 Validation

The system must verify:

- Registration Number matches a submission
- NIC matches the same student submission
- Result has been released
- Correct answer key is available

## 11.2 Result not found

Message:

```text
We could not find a matching submission.
Please check your Registration Number and NIC and try again.
```

## 11.3 Result pending

When the submission exists but answers are not added or the result release time has not arrived, show an animated waiting state.

Suggested message:

```text
Your submission has been received successfully.

The answer key or result has not been released yet.
Other students may still be completing the examination.

Please come back later.
```

Suggested animation:

- Rotating orbit
- Floating paper cards
- Animated clock
- Small physics particles

Also show:

- Expected result release date and time, if configured
- Countdown timer

## 11.4 Result released

Display:

- Student name
- Registration Number
- Exam name
- Batch
- Score out of 50
- Percentage
- Correct answers
- Incorrect answers
- Unanswered answers
- Rank if enabled
- Performance label

Example performance labels:

- Excellent
- Very Good
- Good
- Needs Improvement

Avoid displaying negative or discouraging language.

---

# 12. Corrected Answer Sheet

When result is released, display the same MCQ answer sheet again.

## 12.1 Correct response

- Selected option becomes green
- Show a green check or green animated ring
- Question row receives a soft green glow

## 12.2 Incorrect response

- Student's selected answer becomes red
- Correct answer becomes green
- Show a small label:
  - `Correct answer: 4`

## 12.3 Unanswered response

- Question row becomes amber or neutral
- Correct answer becomes green
- Show:
  - `Not answered`

## 12.4 Result legend

Include a small legend:

- Green: Correct
- Red: Incorrect
- Amber: Not answered
- Blue outline: Selected answer before result

## 12.5 Result summary visualization

Optional but recommended:

- Circular score animation
- Correct/incorrect bar chart
- Topic analytics only if questions are later categorized

---

# 13. Admin Authentication

The Admin Interface must be protected.

## 13.1 Login page

Fields:

- Email or username
- Password
- Remember me
- Login button

Optional:

- Two-factor authentication
- Password reset

## 13.2 Security

- Passwords must be hashed
- Use secure authentication cookies
- Rate-limit failed login attempts
- Do not expose admin routes without authentication
- Log important admin actions

---

# 14. Admin Dashboard

The dashboard should show summary cards:

- Total Exams
- Open Exams
- Upcoming Exams
- Total Submissions
- Results Pending
- Results Released
- Active Batches
- Average Score

Additional sections:

- Recent submissions
- Upcoming exam schedule
- Latest exam analytics
- Quick actions

Quick action buttons:

- Add Exam
- Manage Exams
- Add Answer Key
- Release Results
- Export Results

---

# 15. Add Exam Workflow

The admin must be able to create a new exam.

## 15.1 Required fields

- Exam Name
- Batch
- Exam Year
- Exam Month
- Exam description or instructions
- Google Drive PDF link
- Total questions
  - Default: 50
- Number of options
  - Default: 5
- Exam opening date and time
- Exam closing date and time
- Result release date and time
- Status
  - Draft
  - Scheduled
  - Open
  - Closed
  - Archived

Example exam name:

- `2026 Batch Physics Exam 01`

## 15.2 Optional fields

- Duration
- Allow resubmission
- Show countdown timer
- Show PDF download button
- Result message
- Pass mark
- Enable rank display
- Custom instructions

## 15.3 Answer key section

Below the exam form, include:

- `Add Answers Now`
- `Save Without Answers`

If the admin selects `Add Answers Now`, show the same 50-question MCQ sheet used by students.

The admin can click one correct answer for each question.

Buttons:

- Save Draft
- Save Answer Key
- Clear All
- Validate Answers

## 15.4 Answer key validation

Before publishing results:

- All 50 correct answers must be present
- No question may contain more than one correct answer
- Show missing question numbers
- Confirm before replacing an existing answer key

The admin must still be allowed to save an exam without answers.

---

# 16. Exam Scheduling Controls

The Add/Edit Exam page must contain:

## 16.1 Open Exam At

Date and time picker.

Before this time:

- Students can see the exam as upcoming
- Students cannot open the answer sheet
- Countdown is shown

At or after this time:

- Exam status automatically becomes Open

## 16.2 Close Exam At

Recommended additional field.

At or after this time:

- New submissions are blocked
- In-progress behavior follows admin configuration

## 16.3 Release Result At

Date and time picker.

Result is shown only when:

1. Result release time has arrived
2. Answer key is complete
3. Exam result publishing is enabled

## 16.4 Manual override

Admin can manually:

- Open now
- Close now
- Release now
- Pause result release
- Extend exam time

All changes should be logged.

---

# 17. Manage Exams and CRUD Operations

The `Manage Exams` page must show all saved exams.

## 17.1 Exam table columns

- Exam Name
- Batch
- Month
- Year
- Status
- Opening Time
- Closing Time
- Result Release Time
- Submissions
- Answer Key Status
- Actions

## 17.2 Actions

For each exam:

- View
- Edit
- Duplicate
- Add/Edit Answers
- Open Now
- Close Now
- Release Results
- View Analytics
- Export
- Archive
- Delete

## 17.3 Delete behavior

Recommended:

- Use soft delete or archive by default
- Permanent delete requires a second confirmation
- Do not permanently delete an exam with submissions unless a super-admin confirms

---

# 18. Submission Management

Admin must be able to view submissions by exam.

## 18.1 Submission table

Columns:

- Student Name
- Registration Number
- Batch
- Phone Number
- NIC masked
- Submitted At
- Score
- Result Status
- Submission Status

## 18.2 Filters

- Exam
- Batch
- Month
- Year
- Score range
- Submitted date
- Result released
- Search by name or registration number

## 18.3 Submission actions

- View answer sheet
- View student details
- Recalculate score
- Reopen submission
- Invalidate submission
- Delete submission
- Export individual result
- Add admin note

---

# 19. Admin Analytics

Each exam should have a complete analytics page.

## 19.1 Summary metrics

- Total submissions
- Number of unique students
- Average score
- Median score
- Highest score
- Lowest score
- Pass percentage
- Completion rate
- Result lookup count

## 19.2 Score distribution

Show a chart such as:

- 0-10
- 11-20
- 21-30
- 31-40
- 41-50

## 19.3 Question analysis

For each question:

- Correct response percentage
- Incorrect response percentage
- Unanswered percentage
- Most selected option
- Correct option
- Difficulty indicator

Suggested labels:

- Easy
- Moderate
- Difficult
- Very Difficult

## 19.4 Batch comparison

If an exam is assigned to more than one batch, compare:

- Average score
- Submission count
- Pass percentage

## 19.5 Time analytics

Optional:

- Submissions by hour
- Peak submission time
- Average completion duration

## 19.6 Export

Allow export as:

- CSV
- Excel
- PDF summary

---

# 20. Student Data and Privacy

The system collects personal information, including NIC and phone number.

Requirements:

- Use HTTPS
- Do not expose NIC in URLs
- Mask NIC in admin tables
- Restrict full student data to authorized admins
- Add rate limiting to result lookup
- Add audit logs for sensitive admin actions
- Provide a privacy notice near the registration form
- Store only necessary information
- Allow data export or deletion by authorized admin

Recommended masking:

```text
NIC: 2000******78
Phone: 077***4567
```

---

# 21. Data Model

The following database structure is recommended.

## 21.1 `admins`

```text
id
name
email
password_hash or auth_user_id
role
is_active
created_at
updated_at
last_login_at
```

Possible roles:

- super_admin
- exam_manager
- viewer

## 21.2 `batches`

```text
id
name
year
is_active
created_at
updated_at
```

Example:

```text
2026 A/L
2027 A/L
Repeat Batch
```

## 21.3 `students`

```text
id
full_name
batch_id
registration_number
phone_number
nic_normalized
nic_lookup_hash
created_at
updated_at
```

Recommended unique index:

```text
registration_number + batch_id
```

## 21.4 `exams`

```text
id
name
slug
batch_id
exam_year
exam_month
description
instructions
paper_drive_url
paper_preview_url
paper_download_url
total_questions
options_per_question
open_at
close_at
result_release_at
status
allow_resubmission
pass_mark
show_rank
created_by
created_at
updated_at
archived_at
```

## 21.5 `answer_keys`

```text
id
exam_id
question_number
correct_option
created_at
updated_at
```

Unique index:

```text
exam_id + question_number
```

## 21.6 `submissions`

```text
id
exam_id
student_id
attempt_number
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
created_at
updated_at
```

## 21.7 `submission_answers`

```text
id
submission_id
question_number
selected_option
is_flagged
is_correct
created_at
updated_at
```

Unique index:

```text
submission_id + question_number
```

## 21.8 `audit_logs`

```text
id
admin_id
action
entity_type
entity_id
old_values
new_values
ip_address
created_at
```

---

# 22. Recommended API Endpoints

The exact structure may change depending on the framework.

## 22.1 Public and student endpoints

```text
GET    /api/batches
GET    /api/exams?batch=&year=&month=
GET    /api/exams/:examId
POST   /api/students/register-or-find
POST   /api/exams/:examId/start
PATCH  /api/submissions/:submissionId/answers
POST   /api/submissions/:submissionId/submit
POST   /api/results/lookup
GET    /api/results/:submissionId
```

## 22.2 Admin endpoints

```text
POST   /api/admin/login
POST   /api/admin/logout

GET    /api/admin/exams
POST   /api/admin/exams
GET    /api/admin/exams/:examId
PATCH  /api/admin/exams/:examId
DELETE /api/admin/exams/:examId

PUT    /api/admin/exams/:examId/answer-key
POST   /api/admin/exams/:examId/open
POST   /api/admin/exams/:examId/close
POST   /api/admin/exams/:examId/release-results

GET    /api/admin/exams/:examId/submissions
GET    /api/admin/exams/:examId/analytics
GET    /api/admin/exams/:examId/export
```

---

# 23. Scoring Logic

For each question:

```text
If selected_option == correct_option:
    score += 1
    is_correct = true
Else:
    score += 0
    is_correct = false
```

Final calculations:

```text
correct_count = number of correct answers
incorrect_count = number of wrong selected answers
unanswered_count = total_questions - answered_questions
score = correct_count
percentage = (score / total_questions) * 100
```

Default scoring:

- Correct: +1
- Incorrect: 0
- Unanswered: 0

Negative marking is not required unless later enabled by admin settings.

---

# 24. Result Release Logic

A result may be shown only if all required conditions are true.

```text
submission exists
AND registration number matches
AND NIC matches
AND submission is final
AND answer key contains all required answers
AND current time >= result_release_at
AND result publication is enabled
```

If the answer key is incomplete:

```text
status = RESULT_PENDING_ANSWER_KEY
```

If release time has not arrived:

```text
status = RESULT_SCHEDULED
```

If the exam is still open:

```text
status = EXAM_IN_PROGRESS
```

The frontend must show the correct message and animation for each case.

---

# 25. Google Drive PDF Link Handling

Admin may paste a Google Drive sharing link.

The system should support links such as:

```text
https://drive.google.com/file/d/FILE_ID/view?usp=sharing
```

Generate:

```text
Preview URL:
https://drive.google.com/file/d/FILE_ID/preview

Download URL:
https://drive.google.com/uc?export=download&id=FILE_ID
```

Validation requirements:

- Confirm a file ID exists
- Reject unsupported or malformed links
- Show a preview test inside admin
- Ask admin to ensure the file is shared as:
  - `Anyone with the link can view`

Do not store private access tokens in the frontend.

---

# 26. UI Design System

## 26.1 Main theme

- Premium
- Modern
- Academic
- Energetic
- Physics-inspired
- Blue and white
- Glassmorphism
- Soft depth and motion

## 26.2 Suggested color palette

```text
Primary Blue:       #2563EB
Deep Navy:          #0B1F3A
Sky Blue:           #60A5FA
Cyan Accent:        #22D3EE
White:              #FFFFFF
Soft Background:    #EFF6FF
Glass Surface:      rgba(255, 255, 255, 0.16)
Glass Border:       rgba(255, 255, 255, 0.30)
Text Dark:          #0F172A
Text Muted:         #64748B
Success Green:      #22C55E
Error Red:          #EF4444
Warning Amber:      #F59E0B
```

## 26.3 Glass card style

```css
background: rgba(255, 255, 255, 0.16);
backdrop-filter: blur(18px);
border: 1px solid rgba(255, 255, 255, 0.30);
box-shadow: 0 20px 50px rgba(15, 23, 42, 0.12);
border-radius: 24px;
```

## 26.4 Typography

Recommended:

- Headings: `Sora`, `Manrope`, or `Poppins`
- Body: `Inter`

Use responsive font sizes with `clamp()`.

Example:

```css
font-size: clamp(1.75rem, 4vw, 3.5rem);
```

## 26.5 Spacing

Use a consistent 4px or 8px spacing system.

Examples:

- 8px
- 12px
- 16px
- 24px
- 32px
- 48px
- 64px

---

# 27. Animation Specification

Animations should make the platform feel premium and interactive, not slow or distracting.

## 27.1 Page transitions

- Fade and slide
- Duration: 250-450ms
- Use spring motion carefully

## 27.2 Exam cards

Desktop:

- Hover lift
- Soft 3D tilt
- Border glow
- Button arrow movement

Mobile:

- Tap scale
- Soft pulse
- No hover-dependent interaction

## 27.3 MCQ option selection

- Circle scales from 1.00 to 1.08
- Blue ring animates in
- Hand-drawn `X` appears using SVG path animation
- Previous selection fades out
- Progress bar updates smoothly

## 27.4 Submit animation

- Button transforms into loading state
- Paper icon moves toward server/cloud icon
- Success check animation appears
- Confetti may appear briefly after successful submission

## 27.5 Result animation

- Score counts from 0 to final score
- Progress ring fills
- Correct questions animate green
- Incorrect questions animate red
- Staggered appearance for result summary cards

## 27.6 Waiting animation

Use:

- Orbiting circles
- Animated clock
- Floating exam papers
- Text shimmer

## 27.7 Reduced motion

Respect:

```css
@media (prefers-reduced-motion: reduce)
```

Disable non-essential animation for users who prefer reduced motion.

---

# 28. Responsive Design Requirements

## 28.1 Student interface

Must support:

- 320px mobile width
- 360px mobile width
- 390px mobile width
- 414px mobile width
- Tablets
- Laptops
- Desktop monitors
- Large desktop displays

Use:

- CSS Grid
- Flexbox
- `minmax()`
- `clamp()`
- Container queries where useful
- Fluid padding

Recommended container:

```css
width: min(100% - 24px, 1440px);
margin-inline: auto;
```

## 28.2 Breakpoint behavior

### Below 640px

- Single-column content
- Full-width buttons
- Sticky mobile bottom action bar
- Admin button hidden
- PDF preview compact
- One MCQ row per line

### 640px to 1023px

- Student layout adapts to tablet
- Admin button may remain hidden
- MCQ sheet uses 2-3 columns where space permits

### 1024px and above

- Desktop navigation
- Admin button visible
- PDF and answer sheet side-by-side
- 5-column answer sheet layout

## 28.3 Admin interface

- Optimized for 1024px and above
- Below 1024px, display:
  - `The Admin Dashboard is designed for laptop and desktop use. Please open it on a larger screen.`

---

# 29. Accessibility

The system must be accessible.

Requirements:

- Keyboard navigation
- Visible focus indicators
- Proper labels for inputs
- ARIA labels for answer choices
- Color must not be the only indicator
- Minimum touch target: 44px
- Sufficient color contrast
- Screen-reader-friendly status messages
- Error messages linked to fields
- Modal focus trapping
- Semantic HTML

For result states, include both icon and text:

- Correct
- Incorrect
- Not answered

---

# 30. Error and Empty States

Create polished states for:

- No exams found
- Invalid student details
- Invalid Google Drive link
- Exam not open
- Exam closed
- Submission already exists
- Network disconnected
- Submission failed
- Result not released
- Answer key incomplete
- Unauthorized admin access
- Server error
- Maintenance mode

All error states must include:

- Clear title
- Short explanation
- Recommended next action
- Retry button where useful

---

# 31. Notifications

Use in-app toast notifications.

Examples:

- `Answer saved`
- `Exam created successfully`
- `Answer key updated`
- `Result release scheduled`
- `Submission completed`
- `Internet connection lost`
- `Connection restored`

Do not overuse notifications for every click.

---

# 32. Recommended Admin Settings

Settings page may include:

- Academy name
- PCA logo
- Support phone number
- Support WhatsApp link
- Active batches
- Default total questions
- Default options per question
- Default pass mark
- Student submission policy
- Result rank visibility
- Maintenance mode
- Privacy notice
- Terms and instructions

---

# 33. Optional Future Features

These are not required for the first version but the architecture should allow them later.

- Topic-wise question tagging
- Negative marking
- Timed exam countdown
- Randomized question order
- Randomized option order
- Multiple papers per exam
- Image-based questions
- Direct PDF upload
- Student accounts
- SMS or WhatsApp result notification
- Certificates
- Leaderboard
- District or school analytics
- Live proctoring
- Camera verification
- QR code exam access
- Multiple admin roles
- Tamil and English language switch
- Dark mode
- PWA installation
- Offline answer mode
- Payment-based exam access

---

# 34. Non-Functional Requirements

## Performance

- Initial student page load should be fast on mobile data
- Compress images
- Lazy-load PDF preview
- Use code splitting
- Avoid heavy animation libraries beyond what is required
- Target Lighthouse performance score above 85

## Reliability

- No answer loss during refresh or network interruption
- Idempotent final submission
- Server-side validation for all answers
- Database transactions for final submission and score calculation

## Scalability

The system should support:

- Hundreds of concurrent students initially
- Thousands of stored submissions
- Multiple batches and monthly exams

## Browser support

- Chrome
- Edge
- Safari
- Firefox
- Android Chrome
- iPhone Safari

---

# 35. Suggested Project Folder Structure

```text
src/
├── app/
│   ├── page.tsx
│   ├── exams/
│   │   ├── page.tsx
│   │   └── [examId]/
│   │       ├── page.tsx
│   │       └── result/
│   │           └── page.tsx
│   ├── result/
│   │   └── page.tsx
│   ├── admin/
│   │   ├── login/
│   │   ├── dashboard/
│   │   ├── exams/
│   │   ├── submissions/
│   │   ├── analytics/
│   │   └── settings/
│   └── api/
├── components/
│   ├── student/
│   │   ├── StudentRegistrationForm.tsx
│   │   ├── ExamFilter.tsx
│   │   ├── ExamCard.tsx
│   │   ├── PdfPreview.tsx
│   │   ├── McqAnswerSheet.tsx
│   │   ├── QuestionRow.tsx
│   │   ├── QuestionNavigator.tsx
│   │   ├── ExamProgress.tsx
│   │   └── ResultSheet.tsx
│   ├── admin/
│   │   ├── AdminSidebar.tsx
│   │   ├── ExamForm.tsx
│   │   ├── AnswerKeyEditor.tsx
│   │   ├── SubmissionTable.tsx
│   │   └── AnalyticsCharts.tsx
│   └── ui/
│       ├── GlassCard.tsx
│       ├── Button.tsx
│       ├── Modal.tsx
│       ├── Input.tsx
│       ├── Select.tsx
│       └── Toast.tsx
├── lib/
│   ├── auth.ts
│   ├── database.ts
│   ├── validation.ts
│   ├── scoring.ts
│   ├── googleDrive.ts
│   └── examStatus.ts
├── types/
└── styles/
```

---

# 36. Core Component Requirements

## 36.1 `McqAnswerSheet`

Props:

```ts
type McqAnswerSheetProps = {
  totalQuestions: number;
  optionsPerQuestion: number;
  answers: Record<number, number | null>;
  mode: "student" | "admin-answer-key" | "result";
  correctAnswers?: Record<number, number>;
  onAnswerChange?: (questionNumber: number, option: number) => void;
  disabled?: boolean;
};
```

Responsibilities:

- Render responsive question layout
- Mark selected options
- Animate selection
- Show result colors
- Support keyboard access
- Support admin answer entry
- Show correct answers in result mode

## 36.2 `ExamStatusBadge`

Statuses:

```ts
"DRAFT"
"SCHEDULED"
"OPEN"
"CLOSED"
"RESULT_PENDING"
"RESULT_RELEASED"
"ARCHIVED"
```

## 36.3 `PdfPreview`

Responsibilities:

- Accept Google Drive sharing URL
- Convert to preview URL
- Display preview
- Provide download and open buttons
- Show fallback UI on failure

---

# 37. Acceptance Criteria

The first version is considered complete only when all the following conditions are satisfied.

## Student side

- Student can enter full name, batch, registration number, phone, and NIC
- Student can filter exams by month and year
- Student sees exams assigned to the selected batch
- Student can preview and download the PDF
- Student can mark 50 questions with 5 options
- Selected option shows an animated `X`
- Student can change an answer before submission
- Progress updates accurately
- Student receives unanswered warning
- Student can submit answers
- Answers remain safe during refresh
- Student can check result using Registration Number and NIC
- Pending result state works
- Released result shows score out of 50
- Corrected sheet shows green, red, and correct answers
- Student interface works properly from 320px mobile width upward
- Admin button is hidden on mobile and visible on desktop

## Admin side

- Admin can log in securely
- Admin can create an exam
- Admin can select month, year, batch, and exam name
- Admin can add a Google Drive PDF link
- Admin can set open time
- Admin can set close time
- Admin can set result release time
- Admin can save an exam without answers
- Admin can add answers using the same MCQ sheet
- Admin can edit and delete or archive exams
- Admin can view submissions
- Admin can view analytics
- Admin can release results
- Admin can export results

## Quality

- Blue-and-white glassmorphism design is consistent
- Animations are smooth
- Mobile student UI has no horizontal overflow
- Forms have proper validation
- Answer data is not lost
- Sensitive data is protected
- Result conditions are enforced by the backend

---

# 38. Development Phases

## Phase 1: Foundation

- Project setup
- Database schema
- Authentication
- Design system
- Student registration form
- Batch and exam models

## Phase 2: Student Exam Experience

- Exam filters
- Exam cards
- PDF preview
- MCQ answer sheet
- Autosave
- Submission flow

## Phase 3: Admin Management

- Admin dashboard
- Add exam
- Answer key editor
- Manage exams
- Scheduling

## Phase 4: Results

- Score calculation
- Result lookup
- Pending state
- Released result sheet
- Result publishing

## Phase 5: Analytics and Export

- Submission table
- Score charts
- Question analytics
- CSV and Excel export

## Phase 6: Quality and Launch

- Mobile testing
- Security review
- Performance optimization
- Accessibility review
- Production deployment
- Backup strategy

---

# 39. Important Implementation Rules for the AI Developer

1. Do not use placeholder-only pages.
2. Build complete working forms with validation.
3. Use reusable components.
4. Keep all student pages fully responsive.
5. Hide the Admin button below desktop breakpoint.
6. Do not rely only on frontend checks for exam times or result release.
7. Validate all dates and statuses on the server.
8. Prevent duplicate final submissions.
9. Do not expose full NIC or sensitive data.
10. Ensure the selected answer displays a clear animated `X`.
11. Use the same answer sheet component for:
    - Student answering
    - Admin answer key entry
    - Student result review
12. Use green for correct, red for incorrect, and show the correct answer.
13. Support saving an exam before adding the answer key.
14. Automatically show a pending message when answers are not available.
15. Use browser width responsively without fixed-width overflow.
16. Keep animations smooth and optimized for mobile.
17. Provide loading, empty, error, offline, and success states.
18. Use TypeScript types throughout.
19. Add meaningful comments only where logic is complex.
20. Do not hard-code batches, exams, or answer keys.

---

# 40. Copy-Paste AI Build Prompt

Use the following prompt when asking a coding AI to build the application.

```text
Build a production-ready web application called "PCA MCQ Examination System" for Physics Cube Academy.

Use:
- Next.js
- TypeScript
- Tailwind CSS
- Framer Motion
- Supabase PostgreSQL and Authentication
- React Hook Form
- Zod
- Recharts
- Lucide icons

The application must have two interfaces:

1. Student Interface
2. Admin Interface

STUDENT REQUIREMENTS

The student interface must be fully mobile responsive from 320px upward.

Before starting an exam, collect:
- Full name
- Batch
- Registration number
- Phone number
- NIC number

After this, allow the student to select:
- Year
- Month
- Batch

Display all matching exams as animated glass cards.

Each exam must show:
- Exam name
- Batch
- Month and year
- Open time
- Close time
- Status
- Total questions
- Start or view button

Inside the exam page:
- Show exam details
- Show a Google Drive PDF preview
- Provide Open Full PDF and Download PDF buttons
- Show an MCQ answer sheet with 50 questions and 5 options per question

The answer sheet should look like a clean OMR sheet.
On desktop, arrange questions in five columns:
1-10, 11-20, 21-30, 31-40, 41-50.

On mobile, use one responsive row per question with five large touch-friendly circular options.

When a student selects an option:
- Animate a hand-drawn X over the selected number
- Add a blue glowing ring
- Remove the previous selection for that question
- Update the answered progress
- Autosave the answer

Add:
- Question navigator from 1 to 50
- Answered count
- Unanswered count
- Flag for review
- Sticky mobile submit bar
- Refresh recovery
- Offline local saving

Before final submission, show a confirmation modal with answered and unanswered counts.

After submission, show a success screen and a View My Result button.

RESULT REQUIREMENTS

Allow result lookup using:
- Registration number
- NIC number

If the result is not released, show a premium animated waiting screen with a message explaining that the answer key or result has not been released and other students may still be completing the exam.

If result is released:
- Show score out of 50
- Show percentage
- Show correct count
- Show incorrect count
- Show unanswered count
- Show the same MCQ answer sheet again

In result mode:
- Correct student answers must appear green
- Wrong student answers must appear red
- The correct answer must also be shown in green
- Unanswered questions must show the correct answer

ADMIN REQUIREMENTS

The student navigation must show an Admin button only on desktop.
Hide it on mobile and tablet.

The admin dashboard is desktop-focused and does not need full mobile optimization.
Below 1024px, show a message asking the admin to use a laptop or desktop.

Admin must securely log in.

Admin pages:
- Dashboard
- Add Exam
- Manage Exams
- Submissions
- Analytics
- Result Publishing
- Settings

Add Exam form must include:
- Exam name
- Batch
- Year
- Month
- Instructions
- Google Drive PDF link
- Total questions, default 50
- Options per question, default 5
- Open Exam At date and time
- Close Exam At date and time
- Release Result At date and time
- Pass mark
- Status

Below the form, show:
- Add Answers Now
- Save Without Answers

If Add Answers Now is selected, show the same 50-question MCQ sheet and let the admin select the correct answer for each question.

Allow the admin to save an exam even without the answer key.
Results must remain pending until all correct answers are available and the release time has arrived.

Manage Exams must support full CRUD:
- View
- Edit
- Duplicate
- Add or edit answers
- Open now
- Close now
- Release results
- View analytics
- Export
- Archive
- Delete

Analytics must include:
- Total submissions
- Average score
- Median score
- Highest score
- Lowest score
- Pass rate
- Score distribution
- Question-wise correct percentage
- Most selected wrong answer
- Batch comparison
- CSV and Excel export

DESIGN REQUIREMENTS

Use a premium blue-and-white glassmorphism style.

Suggested colors:
- Primary blue #2563EB
- Deep navy #0B1F3A
- Sky blue #60A5FA
- Cyan #22D3EE
- Success green #22C55E
- Error red #EF4444
- Warning amber #F59E0B

Use:
- Glass cards
- Soft blurred backgrounds
- Physics-inspired floating particles
- Smooth page transitions
- Animated buttons
- Animated X marks
- Score count animation
- Progress rings
- Subtle confetti after successful submission
- Reduced-motion support

SECURITY AND LOGIC

- Protect admin routes
- Hash passwords
- Validate data on server and client
- Normalize Sri Lankan phone and NIC formats
- Never expose full NIC publicly
- Rate-limit result lookup
- Prevent duplicate final submissions
- Use database transactions for final submission
- Calculate exam and result status on the server
- Do not reveal results before release time
- Do not reveal results without a complete answer key

Create a clean reusable component architecture.
Use the same MCQ answer sheet component in three modes:
- Student mode
- Admin answer-key mode
- Result mode

Do not hard-code exams, batches, students, or answers.
Create all database tables, migrations, API routes, validation schemas, loading states, error states, empty states, and responsive layouts.
```

---

# 41. Final Product Vision

The completed platform should feel like a dedicated PCA digital examination environment rather than a normal form.

The student experience must be:

- Fast
- Clear
- Mobile-first
- Game-like
- Reliable
- Professional
- Encouraging

The admin experience must be:

- Efficient
- Data-driven
- Secure
- Easy to manage
- Suitable for regular monthly examinations

The final visual identity should clearly represent Physics Cube Academy through a premium blue-and-white interface, smooth glassmorphism, physics-inspired motion, and a clean digital MCQ sheet based on the supplied reference.
