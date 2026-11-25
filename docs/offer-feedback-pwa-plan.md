# Offer feedback and PCA Exam PWA

Work starts on `feat/offer-package-admin-purchase-feed` at `41dca5a`. The existing untracked seminar photos and Offer progress notes are outside this change. The implementation uses the current Offer moderation workflow, Spring Security, R2 image client, and student exam routes.

## Implementation and automated verification

- [ ] Document optional voice settings. Verify the tracked example contains blank credentials and safe defaults, the local file is ignored, and the app starts with no voice credentials.
- [ ] Validate and compress feedback photos. Verify decoded dimensions, supported formats, EXIF orientation, metadata removal, input limits, and a final JPEG no larger than 100,000 bytes.
- [ ] Store feedback photos in the private R2 bucket. Verify form validation precedes storage, nullable metadata preserves older records, upload errors preserve text-only submission, and rollback deletes the uploaded object. Use a mocked R2 client.
- [ ] Add the photo picker, preview and removal, plus teacher and public images. Verify anonymous uploads enter `PENDING`, drafts survive recoverable failures, teacher previews require authentication, a public read succeeds only while published, and responses cannot be cached. Use isolated HTTP tests and mocked R2.
- [ ] Add the PCA Exam manifest, PCA icons, service worker, and shared student head metadata. Verify the real paper and Offer routes, both session views, results, public PWA assets, offline fallback, and the static asset cache allowlist.
- [ ] Add in-site installation controls. Verify native prompt handling, iPhone and in-app browser guidance, and standalone visibility using frontend tests and an available desktop browser.
- [ ] Add the Groq transcription endpoint. Verify CSRF, bounded and validated audio, request limits, credential-free startup, timeouts, and Tamil transcription request/response handling with a mock provider.
- [ ] Add optional Gemini cleanup. Verify default-off configuration, transcript-only requests, preservation checks, and fallback for failed or rewritten output with mock providers.
- [ ] Add Offer voice recording and editable transcript insertion. Verify microphone denial, unsupported browsers, retry, automatic stop, typed text preservation, and the 600-character limit using frontend tests.
- [ ] Run `mvn -f backend/pom.xml verify`, the frontend suite, and available browser checks. Verify timing, pending-answer recovery, submission, results and PDF access using the existing isolated tests and browser coverage. Review committed changes and the final working tree.

## Live provider checks

- [ ] R2 smoke test. Prerequisite: confirmed non-production R2 configuration. Compress a disposable image, upload it under `offer-feedback/`, read it back and verify the JPEG is at most 100,000 bytes, then delete it and verify the object is absent.
- [ ] Groq Tamil pilot. Prerequisite: `GROQ_API_KEY`, `PCA_FEEDBACK_VOICE_ENABLED=true`, and eligible pilot participants. Record Sri Lankan Tamil, Tamil-English mixed speech, names, and academy terms on Offer. Compare editable transcripts against the spoken samples and verify expected formatting and retry behavior.
- [ ] Gemini pilot. Prerequisite: eligible adult-only deployment, `GEMINI_API_KEY`, `GEMINI_MODEL` and `PCA_FEEDBACK_GEMINI_ENABLED=true` in addition to enabled Groq. Repeat the Tamil samples with cleanup enabled. Verify cleanup preserves names, numbers, English terms, dialect and meaning, and that the raw transcript remains available on cleanup failure.

## Physical-device checks

- [ ] Android Chrome. Prerequisite: Android device and HTTPS deployment. Open exam home and Offer, install via the native option, relaunch, then open WhatsApp links to a valid `/exam/p/{slug}` and `/exam/packages`. Verify both URLs load in Chrome, record whether the OS hands them to the installed app, and check a timed exam through a temporary network interruption, reconnect, submission and result access.
- [ ] iPhone Safari. Prerequisite: iPhone, HTTPS deployment and a WhatsApp account. Follow Share, Add to Home Screen, and Open as Web App, then launch from the Home Screen. Separately open the same paper and Offer links from WhatsApp and verify navigation and the external-browser guidance. Verify microphone permission denial and a valid recording on Offer when Groq is configured.
