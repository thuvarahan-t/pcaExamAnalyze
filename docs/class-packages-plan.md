# Class Packages — implementation plan and progress

## Agreed experience

A public Class Packages page inside the existing Spring Boot / Thymeleaf website. Admins publish posters and confirm purchases; students browse packages and prepare a WhatsApp enquiry using editable saved details.

## Defaults and decisions

- Public URL: `/exam/packages`; administration: `/exam/admin/packages`.
- One campaign countdown at the top, updated every second against server time. Initial deadline: 22 November 2026, 23:59:59 Asia/Colombo (from the supplied posters). Admin can change it.
- Default WhatsApp destination: `94779179035` (poster number 0779179035). Admin can change it.
- Default page headline: **PCA Mega Offer Packages**. Countdown heading: **Your offer ends in**, replacing “Make your next move.” with no duplicate caption. Earlier default/mistaken main headlines display the corrected main title; custom admin headlines remain editable.
- Each package starts with a capacity of 111 and **zero** confirmed purchases. No invented purchases or claims of remaining availability.
- Initial packages: Theory (Rs. 32,000 → 8,000), Self Study (Rs. 36,000 → 9,000), Practical (Rs. 4,400 → 1,100), using the three supplied posters.
- Expired campaigns and full packages remain visible but cannot start a new enquiry. Admin can still record already completed payments, up to capacity.
- A WhatsApp enquiry is not a purchase. Counts change only when an authenticated teacher records a confirmed purchase. No payment gateway or automatic WhatsApp sending.
- Confirmed purchases are unique by package + normalized registration ID. Admin can correct mistakes by removing a purchase. Updates use a package row lock to enforce capacity under concurrent requests.
- Registration IDs are private to admins. Since 2026-11-13 (user request), the public page shows a **Recent purchases** bar: masked student name + package name + relative time for the 30 newest confirmed purchases on published packages, newest first.
- Existing exam session/cookie details prefill the popup. Package-only edits are saved separately in the session/browser cookie, without altering the exam profile or identity used for exam results.

## Student design and interaction

- Responsive navy / blue / teal PCA design, clean light canvas, campaign banner and large readable countdown tiles.
- Three poster cards on desktop, a single column on mobile. Full uncropped poster, original/offer prices, confirmed count out of capacity and progress bar. Batch and description rows are omitted from student cards at the user's request; admins can still maintain them.
- Clicking the poster or card registration button opens the editable registration dialog directly on desktop/mobile (a bottom sheet on mobile). Desktop retains a separate View full poster link with a registration action in the preview. Expired/full packages disable enquiry.
- Poster preview fits the entire uncropped image and WhatsApp action into a viewport-height dialog on desktop/mobile without scrolling. The image scales using `object-fit: contain`; the action footer and close button remain visible.
- Mobile only (up to 760px): hide package-page navigation links, the study-collection eyebrow, hero subtitle and Find your study pack link. Desktop keeps these elements.
- The package mobile view includes the usual home-style bottom navigation: My Details / raised Home button / My Result. Footer spacing keeps content clear of the fixed bar; desktop does not display this mobile bar.
- Mobile poster viewing stays inline in the card, with full-image preview links hidden at up to 760px. Clicking the inline poster opens registration, just like the card WhatsApp button. Desktop retains the separate full-image preview link.
- Mobile registration opens as a bottom sheet with rounded top corners, a grab-handle visual, dimmed backdrop and slide-up animation. It respects safe-area padding and reduced-motion preferences; focus starts on the heading so the keyboard does not open immediately. Desktop retains its centered dialog.
- Mobile shows package names in cards and the registration summary, restoring the earlier layout at the user's request.
- The details popup has a single “Send message in WhatsApp” action. It validates/saves details and opens WhatsApp directly, without a Continue step or message-preview screen, with exactly these labelled details:
  `Hi, my name is [name]. I'd like to purchase the [package name].` followed by a blank line and WhatsApp bold-labelled `Name`, `Registration No`, and `Package` details.
- Live public count refresh periodically and on returning to the tab; countdown expiry/full state disables enquiry immediately. Network errors do not invent counts.
- Empty, expired, sold-out and validation/error states are explicit.

## Admin design and interaction

- Existing admin navigation links to package management; student portal links to Class Packages.
- Campaign settings: title, deadline in Sri Lanka local time and WhatsApp number.
- Package create/edit: poster upload (validated JPEG/PNG, up to 5 MB and 20 megapixels), name, category, original and offer prices, capacity and publication state. New packages append automatically. JPEG/PNG use the existing JDK image decoder for actual content validation.
- Draft / published visibility; hide by unpublishing without losing purchase history.
- Per-package confirmed purchase list with registration ID, name and confirmation timestamp. Add by registration ID and name (existing exam records can supply the name when available); remove mistaken entries with confirmation.
- Store package records, uploaded image bytes and purchases durably in the existing database. Bundled sample posters are included in the app; seed once without overwriting admin changes or resurrecting removed data.
- All admin mutations use existing teacher authentication and CSRF protection. Validate on the server; handle duplicate IDs, missing records, capacity and malformed uploads gracefully.

## Implementation checklist

- [x] Inspect posters, existing security, student cookie and project structure.
- [x] Write complete plan before implementation.
- [x] Add persistent campaign, package, poster and purchase models/repositories.
- [x] Add services: seeding, validation, image storage, counts and purchase capacity locking.
- [x] Add public/admin routes and safe saved-details / WhatsApp handoff.
- [x] Build responsive student page, countdown, registration dialog and live counts.
- [x] Build admin settings, package editor, poster preview and purchase management.
- [x] Connect existing student/admin navigation.
- [x] Verify authorization, CSRF, prefill, WhatsApp encoding, duplicate/capacity rules, publication/expiry, image validation and rendered templates.
- [x] Run relevant regression checks and validate JavaScript syntax.
- [ ] Visually inspect desktop/mobile layout in a browser (computer-use tool blocked; see below).
- [x] Record final verification and any outstanding limitations here.

## Verification record

Implementation and automated verification are complete. No external WhatsApp messages were sent during verification.

- Compilation passed.
- First integration pass found and corrected H2/PostgreSQL binary column compatibility (`bytea`) and missing option values in the Thymeleaf editor.
- Added nine integration tests for public/admin rendering, count privacy, saved details, Tamil WhatsApp encoding, authentication/CSRF, duplicate/capacity enforcement including simultaneous last-seat purchases, publication/expiry, seed idempotence, image validation, real multipart creation, editing and purchase removal.
- `mvn -B -f backend/pom.xml verify`: BUILD SUCCESS; 97 tests reported, 96 passed and 1 skipped. The existing real-PostgreSQL concurrency test requires `PCA_TEST_POSTGRES_URL`, which was not configured. Verification built the application jar and generated coverage reports.
- After adding the ninth package integration test: `mvn -B -f backend/pom.xml -Dtest=ClassPackagesIntegrationTest test`: 9 passed, no failures or errors.
- Both new JavaScript files passed syntax validation using Node's `vm.Script`.
- Existing exam portal links include desktop navigation and the mobile bottom navigation. Counts poll every 15 seconds and refresh when returning to the tab.
- Desktop home navigation uses a compact **Offer** tab. Mobile keeps the original three bottom tabs (My Details / Home / My Result), with a small gift icon in the top bar instead. Mobile brand text is hidden to keep the header compact. The offer icon uses warm gold styling and a gentle blinking/pulsing accent; reduced-motion preferences disable animation.
- Purchase counts are prominently displayed in a contrasting navy/teal panel: large confirmed count, capacity, remaining places badge and thicker progress bar. These still reflect only actual admin-confirmed purchases.
- Layout cleanup: equal-height cards per row, aligned title/price/count/button sections, consistent poster-label heights and spacing, compact mobile title spacing, and a full-height page with a consistent footer. Removed unused introduction, batch, description, steps and preview-screen CSS.
- Further compacted the hero/countdown and cards based on the supplied screenshot: reduced hero vertical padding (28–32px desktop, 24px mobile), heading size, timer tiles (58px desktop / 54px mobile), price-row minimum height, purchase-panel padding and action spacing. Kept uncropped posters, prominent purchase counts and touch-friendly buttons.
- Offer hero motion: slow aurora/ring movement, subtle title sheen and badge glow, pointer-responsive countdown lighting, gentle animation only when digits change, and tap feedback on timer tiles. Reduced-motion preferences disable animations; compact layout and real server-based deadline are retained.
- Package cards use a frosted glass surface with subtle translucent highlights, blurred backdrop, soft borders and hover/focus glow. A slow pastel teal/blue/lilac background glow animates behind the collection; reduced-motion disables it and unsupported browsers get an opaque readable fallback.
- Admin navigation labels the section **Offer** and places it last after Syllabus. All Offer pages share the existing admin sidebar fragment, teacher/logout area, mobile menu, admin heading, blue buttons, glass panels, form controls and tables.
- Simplified Offer admin: show only student-visible package fields (poster, name, category, prices, capacity and publish state), campaign title/deadline/WhatsApp destination and purchase management. Removed batch, description and display-order inputs plus explanatory page copy. New packages append automatically; edits preserve existing hidden metadata and order.
- Search is shown only inside the confirmed-students list and filters by name/registration ID. Purchase entry keeps the simple manual name/ID form without student-search suggestions.
- Removing a confirmed purchase uses the existing styled admin confirmation popup (Cancel / Remove), followed by the updated list without a success notification.
- Final review removed the unused registered-student autocomplete endpoint, query and styles. Only confirmed-list filtering is retained.
- Live catalog review: changing a package name, category, price or poster disables an open stale enquiry and refreshes the catalog after the dialog closes. A normal count-only update continues without reloading.
- The package CTA reads **Get this package** and uses a gentle green glow and occasional shine sweep, with hover/tap feedback. Full/expired actions do not animate; reduced-motion preferences disable animation.
- Purchase-count panels use a light mint background, muted progress bar and no shadow, keeping the large count readable while the animated Get this package button remains the primary action.
- The page background uses a deeper blue-grey/teal shade and slightly stronger card shadows so the light glass cards stand out clearly.
- Final local regression run: 99 Maven tests reported, 98 passed and the real-PostgreSQL test skipped because no local PostgreSQL/Docker server is available. Seven Node interaction tests passed, covering image registration, WhatsApp submission, full/expired offers, count refresh, edited offers, stale responses and confirmed-student filtering.
- CI now runs on pull requests to `main` as well as `test`, including the frontend interaction suite and the existing H2/PostgreSQL Maven verification jobs.
- Removed the three instructional steps and purchase footnote at the user's request.
- Browser visual review was attempted with the computer-use tool, but the tool stopped because it could not determine the current browser URL confidently. No browser inputs or external messages were sent. Desktop/mobile visual inspection remains outstanding; it is not claimed as passed.

- Recent purchases bar (2026-11-13): frosted pill at the top of the package list (`#buyFeed`, `offer-feed.js`). Shows one purchase at a time ("**Name** purchased **Package** · 5 mins ago"), cycling newest → oldest every 4.5 s and wrapping. The first render comes from the page; `/exam/packages/status` now also returns `recent`, so a purchase the admin adds appears next within ~15 s, and removed ones disappear. Pauses on mouse hover and in hidden tabs; hidden when there are no purchases; reduced-motion swaps without sliding. Names are masked on the server (`ClassPackageService.maskName`), per word: up to 7 letters keeps the first and last letter (`K***a`), longer keeps the first two and last two (`Th******an`); the full name never reaches the browser. Desktop bar is up to 1000px wide with the original compact height. Admin can enter School and District (the same search list + clickable Sri Lanka map picker as the student details form, `district-map-init.js`, light admin styling in `offer-admin.css`, with an "Auto-fill if available" first option) when confirming a purchase; blank ones auto-fill from the student's latest exam submission (invalid old values are skipped). The bar then reads "**th\*\*\*\*\*\*an** from Jaffna Hindu College, Jaffna purchased **Theory Study Pack**"; either part may be missing. Mobile allows up to 3 lines. Tapping the bar (or Enter/Space) opens a **Recent purchases** popup (`#purchasesDialog`; bottom sheet on mobile): the same notifications as a scrollable list, newest first, 20 at a time from `GET /exam/packages/purchases?size=&beforeAt=&beforeId=` (cursor on confirmedAt+id, so new purchases never cause duplicates), with **Load more** until "That's everyone so far." Names stay masked; registration IDs are never sent. The bar pauses while the popup is open. Covered by the updated Java privacy test (names visible, registration IDs never) and a Node cycling test.

## Operational notes

- The feature is delivered in source; it has not been deployed to a live hosting environment.
- On the next normal application startup, Hibernate creates the new tables and the initial three posters seed once. Existing exam data and settings are not rewritten.
- Local preview uses an isolated in-memory H2 database at `http://localhost:8093/exam/packages`. Preview purchases/settings are disposable; the normal application uses its configured persistent database.
- Admin entry: `/exam/admin/packages`; existing teacher login and role checks apply.
- Default deadline/number are editable in Campaign settings. Offers close automatically at expiry; full and expired packs remain visible for reference.
