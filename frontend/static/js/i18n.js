/*
 * English / Tamil language switch for the student-facing pages.
 *
 * English is the source text of every template, so English needs no work at all.
 * When Tamil is picked (cookie "pca_lang=ta"), this script translates text nodes and a
 * few attributes as the page is parsed, using the TA dictionary below. A MutationObserver
 * also catches text that page scripts set later (autosave status, dialogs, timers...).
 *
 * Load it synchronously in <head> so Tamil text is swapped in before the first paint.
 * Put <span data-lang-slot></span> wherever the EN / தமிழ் switch should appear.
 */
(function () {
  "use strict";

  var COOKIE = "pca_lang";

  function readLang() {
    var m = document.cookie.match(/(?:^|;\s*)pca_lang=(en|ta)/);
    if (m) return m[1];
    try { return localStorage.getItem(COOKIE) === "ta" ? "ta" : "en"; } catch (e) { return "en"; }
  }

  function setLang(lang) {
    document.cookie = COOKIE + "=" + lang + ";path=/;max-age=31536000;SameSite=Lax";
    try { localStorage.setItem(COOKIE, lang); } catch (e) { /* private mode */ }
    location.reload();
  }

  var lang = readLang();
  window.PcaLang = { current: lang, set: setLang, t: function (s) { return s; } };

  // ---------------------------------------------------------------- switch UI
  function mountSwitches() {
    var slots = document.querySelectorAll("[data-lang-slot]:not([data-lang-ready])");
    for (var i = 0; i < slots.length; i++) {
      var slot = slots[i];
      slot.setAttribute("data-lang-ready", "1");
      // The landing page shows login/register in an iframe; its own switch covers that.
      if (slot.classList.contains("lang-float") && window.self !== window.top) { slot.remove(); continue; }
      slot.className = (slot.className ? slot.className + " " : "") + "lang-switch";
      slot.setAttribute("role", "group");
      slot.setAttribute("aria-label", lang === "ta" ? "மொழி" : "Language");
      slot.innerHTML =
        '<button type="button" data-lang="en" lang="en" aria-pressed="' + (lang === "en") + '">EN</button>' +
        '<button type="button" data-lang="ta" lang="ta" aria-pressed="' + (lang === "ta") + '">தமிழ்</button>';
    }
  }

  document.addEventListener("click", function (e) {
    var btn = e.target.closest && e.target.closest(".lang-switch button[data-lang]");
    if (!btn) return;
    e.preventDefault();
    if (btn.getAttribute("data-lang") !== lang) setLang(btn.getAttribute("data-lang"));
  });

  var style = document.createElement("style");
  style.textContent =
    ".lang-switch{display:inline-flex;align-items:center;gap:2px;padding:3px;border-radius:999px;" +
    "background:rgba(127,140,180,.14);border:1px solid rgba(127,140,180,.3);" +
    "backdrop-filter:blur(10px);-webkit-backdrop-filter:blur(10px);flex:0 0 auto;vertical-align:middle}" +
    ".lang-switch button{all:unset;cursor:pointer;font:600 12px/1 Inter,system-ui,sans-serif;" +
    "padding:6px 10px;border-radius:999px;color:inherit;opacity:.72;transition:background .15s,opacity .15s}" +
    ".lang-switch button[lang=ta]{font-family:'Noto Sans Tamil','Nirmala UI',Latha,sans-serif}" +
    ".lang-switch button:hover{opacity:1}" +
    ".lang-switch button[aria-pressed=true]{opacity:1;background:linear-gradient(135deg,#1a13ac,#0eb3fe);color:#fff;" +
    "box-shadow:0 2px 8px rgba(26,19,172,.28)}" +
    ".lang-switch.lang-float{position:fixed;top:14px;right:14px;z-index:60}" +
    ".lang-switch button:focus-visible{outline:2px solid #0eb3fe;outline-offset:1px}" +
    "@media(max-width:430px){.lang-switch button{padding:5px 8px;font-size:11px}}" +
    "html.lang-ta body{font-family:Inter,'Noto Sans Tamil','Nirmala UI',Latha,system-ui,sans-serif}";
  (document.head || document.documentElement).appendChild(style);

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", mountSwitches);
  } else {
    mountSwitches();
  }
  new MutationObserver(function () {
    if (document.querySelector("[data-lang-slot]:not([data-lang-ready])")) mountSwitches();
  }).observe(document.documentElement, { childList: true, subtree: true });

  if (lang !== "ta") return;

  // ---------------------------------------------------------------- Tamil
  document.documentElement.lang = "ta";
  document.documentElement.classList.add("lang-ta");
  var font = document.createElement("link");
  font.rel = "stylesheet";
  font.href = "https://fonts.googleapis.com/css2?family=Noto+Sans+Tamil:wght@400;500;600;700&display=swap";
  (document.head || document.documentElement).appendChild(font);

  // Only text that needs Tamil is listed. Anything not listed (Login, Submit, Saved,
  // Next, Question, Batch, month names...) stays in English on purpose: students
  // already know those words, so they read more naturally than a Tamil translation.
  var TA = {
    // --- common
    "My Attempts": "எனது Attempts",
    "Physics Cube Academy (PCA) - Built for smarter revision": "Physics Cube Academy (PCA) - சிறந்த மீட்டலுக்காக உருவாக்கப்பட்டது",
    "Please confirm": "உறுதிப்படுத்துங்கள்",
    "Confirmation": "உறுதிப்படுத்தல்",
    "Unable to complete": "நிறைவுசெய்ய முடியவில்லை",
    "Completed": "நிறைவடைந்தது",

    // --- landing page
    "Choose your PCA portal": "உங்கள் PCA portal-ஐத் தெரிவுசெய்யுங்கள்",
    "Two tools. One path to": "இரண்டு tools. ஒரே இலக்கு —",
    "better results.": "சிறந்த results.",
    "Analyze your past-paper performance or enter the passwordless PCA MCQ examination portal.":
      "உங்கள் past paper performance-ஐ analyze செய்யுங்கள், அல்லது password இல்லாமலே PCA MCQ exam portal-இற்குச் செல்லுங்கள்.",
    "Record past-paper marks, understand your topic performance, and generate a personalized revision report.":
      "Past paper marks-ஐப் பதிவுசெய்து, ஒவ்வொரு topic-இலும் உங்கள் நிலையை அறிந்து, உங்களுக்கென்றே ஒரு revision report-ஐப் பெறுங்கள்.",
    "Open Analyzer": "Analyzer-ஐத் திறக்க",
    "Open Student Dashboard": "Student Dashboard-ஐத் திறக்க",
    "Open Teacher Dashboard": "Teacher Dashboard-ஐத் திறக்க",
    "Analyzer account login is required.": "Analyzer-ஐப் பயன்படுத்த account login அவசியம்.",
    "Browse available exam papers, enter your student details, complete the digital answer sheet, and check released results.":
      "உள்ள exam papers-ஐப் பார்வையிட்டு, உங்கள் விபரங்களை உள்ளிட்டு, online answer sheet-இல் விடையளித்து, வெளியான results-ஐப் பாருங்கள்.",
    "View Exam Papers": "Exam Papers-ஐப் பார்க்க",
    "Students do not need an account or password.": "மாணவர்களுக்கு account அல்லது password தேவையில்லை.",
    "Know exactly": "எதை மீட்ட வேண்டும் என்பதைத்",
    "what to revise.": "துல்லியமாக அறியுங்கள்.",
    "Analyze your Physics past-paper performance, track every attempt, and get a personalized study report that pinpoints your weak, mid-range and strong topics, question by question.":
      "உங்கள் Physics past paper performance-ஐ analyze செய்து, ஒவ்வொரு attempt-ஐயும் கண்காணியுங்கள். எந்த topics weak, mid, strong என்பதை question வாரியாகக் காட்டும் report ஒன்றைப் பெறுவீர்கள்.",
    "Get started": "ஆரம்பியுங்கள்",
    "Open dashboard": "Dashboard-ஐத் திறக்க",
    "View analysis": "Analysis-ஐப் பார்க்க",
    "Analyzer highlights": "Analyzer சிறப்பம்சங்கள்",
    "Years of papers": "ஆண்டு papers",
    "Topics tracked": "கண்காணிக்கப்படும் topics",
    "Personalized": "உங்களுக்கேற்றது",
    "Sample topic report preview": "Topic report மாதிரி",
    "Your topic report": "உங்கள் topic report",
    "How it works": "இது எவ்வாறு செயல்படுகிறது",
    "Three steps to a sharper revision plan": "மூன்று படிகளில் ஒரு சிறந்த revision plan",
    "No guesswork. Enter your marks, let the analyzer do the maths, and walk away knowing exactly where to spend your time.":
      "ஊகம் தேவையில்லை. உங்கள் marks-ஐ உள்ளிடுங்கள்; கணிப்புகளை analyzer செய்யும். எங்கே நேரம் செலவிட வேண்டும் என்பது தெளிவாகத் தெரியும்.",
    "Enter your marks": "உங்கள் marks-ஐ உள்ளிடுங்கள்",
    "Pick a year, type your score per question. We validate every entry against that question's max marks.":
      "ஓர் ஆண்டைத் தெரிவுசெய்து, ஒவ்வொரு question-இற்கும் பெற்ற marks-ஐ உள்ளிடுங்கள். ஒவ்வொரு பதிவும் அந்த question-இன் max marks-இற்குள் உள்ளதா என நாம் சரிபார்ப்போம்.",
    "See your weak areas": "உங்கள் பலவீனமான பகுதிகளைப் பாருங்கள்",
    "A topic-wise breakdown with clear weak / mid / strong colour coding and live charts, so patterns jump out instantly.":
      "Topic வாரியாக weak / mid / strong என நிறங்களாலும் charts மூலமும் காட்டப்படும். எங்கே சிக்கல் என்பது உடனே புரியும்.",
    "Download your report": "உங்கள் report-ஐ download செய்யுங்கள்",
    "A personalized PDF telling you what to study and which exact questions to redo before your next attempt.":
      "அடுத்த attempt-இற்கு முன் எதைப் படிக்க வேண்டும், எந்த questions-ஐ மீண்டும் செய்ய வேண்டும் என்பதைக் கூறும் PDF.",
    "The analysis": "பகுப்பாய்வு",
    "Every topic, ranked by where you stand": "ஒவ்வொரு topic-இலும் உங்கள் நிலை",
    "This is the heart of the analyzer: a clear read on each topic so you revise the right things, not everything.":
      "இதுவே analyzer-இன் மையப்பகுதி: ஒவ்வொரு topic பற்றியும் தெளிவான பார்வை தருவதால், எல்லாவற்றையும் அல்ல, தேவையானவற்றை மட்டும் மீட்டலாம்.",
    "Topic mastery": "Topic தேர்ச்சி",
    "2024 - all papers": "2024 - அனைத்து papers",
    "Weak - revise first": "Weak - முதலில் மீட்டுங்கள்",
    "Below 45%. These topics cost you the most marks. Start here with worked examples and the exact past questions you missed.":
      "45%-இற்குக் குறைவு. இந்த topics-இலேயே அதிக marks இழக்கப்படுகின்றன. Worked examples மற்றும் தவறவிட்ட past questions உடன் இங்கிருந்து ஆரம்பியுங்கள்.",
    "Mid - almost there": "Mid - கிட்டத்தட்ட வந்துவிட்டீர்கள்",
    "45-75%. A little polish converts these to easy marks. Quick wins for your next attempt.":
      "45-75%. சிறிது பயிற்சி செய்தால் இவை இலகுவான marks ஆகும். அடுத்த attempt-இல் விரைவாக marks-ஐ உயர்த்தலாம்.",
    "Strong - keep warm": "Strong - இதைத் தொடருங்கள்",
    "Above 75%. You've got these. Light review only, so you can focus your energy where it counts.":
      "75%-இற்கு மேல். இவை உங்களுக்கு நன்றாகத் தெரியும். சிறிது மீட்டினால் போதும்; மீதி நேரத்தைத் தேவையான இடத்தில் செலவிடுங்கள்.",
    "Ready to find your weak spots?": "உங்கள் பலவீனங்களைக் கண்டறியத் தயாரா?",
    "Create a free account, enter one paper, and see your personalized topic report in minutes.":
      "இலவச account ஒன்றை உருவாக்கி, ஒரு paper-இன் marks-ஐ உள்ளிட்டால், சில நிமிடங்களில் உங்கள் topic report தயாராகும்.",
    "Create free account": "இலவச account உருவாக்க",
    "Close login popup": "Login popup-ஐ மூடுக",
    "Close register popup": "Register popup-ஐ மூடுக",

    // --- login / register / forgot password
    "Welcome back": "மீண்டும் வருக",
    "Sign in to continue your Physics revision plan.": "உங்கள் Physics revision-ஐத் தொடர sign in செய்யுங்கள்.",
    "Invalid username or password. Please try again.": "Username அல்லது password தவறு. மீண்டும் முயற்சிக்கவும்.",
    "You have been logged out.": "நீங்கள் logout செய்யப்பட்டுள்ளீர்கள்.",
    "Account created. Please sign in.": "Account உருவாக்கப்பட்டது. தயவுசெய்து sign in செய்யுங்கள்.",
    "Account created — please sign in.": "Account உருவாக்கப்பட்டது — தயவுசெய்து sign in செய்யுங்கள்.",
    "Password updated. Sign in with your new password.": "Password மாற்றப்பட்டது. புதிய password மூலம் sign in செய்யுங்கள்.",
    "Password updated — sign in with your new password.": "Password மாற்றப்பட்டது — புதிய password மூலம் sign in செய்யுங்கள்.",
    "Your username": "உங்கள் username",
    "Forgot?": "மறந்துவிட்டீர்களா?",
    "New student?": "புதிய மாணவரா?",
    "Create an account": "Account ஒன்றை உருவாக்குங்கள்",
    "Create your account": "உங்கள் account-ஐ உருவாக்குங்கள்",
    "Join the correct batch and start tracking every attempt.": "சரியான batch-இல் இணைந்து, ஒவ்வொரு attempt-ஐயும் கண்காணிக்க ஆரம்பியுங்கள்.",
    "Join the correct batch and start tracking your exam performance.": "சரியான batch-இல் இணைந்து, உங்கள் exam performance-ஐக் கண்காணிக்க ஆரம்பியுங்கள்.",
    "Small letters and numbers only.": "சிறிய எழுத்துகளும் இலக்கங்களும் மட்டும்.",
    "Small letters and numbers only. We will check availability.": "சிறிய எழுத்துகளும் இலக்கங்களும் மட்டும். இந்த username கிடைக்குமா என நாம் சரிபார்ப்போம்.",
    "Name": "பெயர்",
    "Your full name": "உங்கள் முழுப் பெயர்",
    "Do not start with 0.": "0 இல் ஆரம்பிக்க வேண்டாம்.",
    "Type like 74 104 6208. Do not start with 0.": "74 104 6208 என்பது போல உள்ளிடுங்கள். 0 இல் ஆரம்பிக்க வேண்டாம்.",
    "Type like 74 104 6208.": "74 104 6208 என்பது போல உள்ளிடுங்கள்.",
    "Looks good.": "சரியாக உள்ளது.",
    "New NIC only: exactly 12 numbers.": "புதிய NIC மட்டும்: சரியாக 12 இலக்கங்கள்.",
    "Select your batch": "உங்கள் batch-ஐத் தெரிவுசெய்யுங்கள்",
    "District": "மாவட்டம்",
    "Select your district": "மாவட்டத்தைத் தெரிவுசெய்க",
    "Search district...": "மாவட்டத்தைத் தேடுங்கள்...",
    "Sri Lanka - click district": "இலங்கை - மாவட்டத்தை click செய்யுங்கள்",
    "Tap your district on the map": "Map-இல் உங்கள் மாவட்டத்தைத் தொடுங்கள்",
    "Tap your district": "உங்கள் மாவட்டத்தைத் தொடுங்கள்",
    "Class and security": "Class மற்றும் பாதுகாப்பு",
    "Confirm password": "Password-ஐ உறுதிப்படுத்துங்கள்",
    "Re-enter password": "Password-ஐ மீண்டும் உள்ளிடுங்கள்",
    "A stronger password is better, but normal passwords are allowed.": "வலுவான password சிறந்தது, ஆனால் சாதாரண password-உம் ஏற்கப்படும்.",
    "Both passwords must match.": "இரண்டு password-உம் ஒன்றாக இருக்க வேண்டும்.",
    "Passwords match.": "Passwords பொருந்துகின்றன.",
    "Passwords do not match": "Passwords பொருந்தவில்லை",
    "Create account": "Account உருவாக்க",
    "Already have an account?": "ஏற்கனவே account உள்ளதா?",
    "Use at least 3 lowercase letters or numbers.": "குறைந்தது 3 சிறிய எழுத்துகள் அல்லது இலக்கங்களைப் பயன்படுத்துங்கள்.",
    "Checking username availability...": "Username கிடைக்குமா எனச் சரிபார்க்கப்படுகிறது...",
    "Checking availability...": "கிடைக்குமா எனச் சரிபார்க்கப்படுகிறது...",
    "Username is available.": "இந்த username கிடைக்கிறது.",
    "This username is already taken.": "இந்த username ஏற்கனவே பயன்பாட்டில் உள்ளது.",
    "That username is already taken": "அந்த username ஏற்கனவே பயன்பாட்டில் உள்ளது",
    "That username is already taken. Please choose another.": "அந்த username ஏற்கனவே பயன்பாட்டில் உள்ளது. வேறொன்றைத் தெரிவுசெய்யுங்கள்.",
    "Could not check availability. Try again.": "சரிபார்க்க முடியவில்லை. மீண்டும் முயற்சிக்கவும்.",
    "Search failed": "தேடல் தோல்வியடைந்தது",
    "Enter a valid username — 3 to 30 lowercase letters or numbers.": "சரியான username-ஐ உள்ளிடுங்கள் — 3 முதல் 30 சிறிய எழுத்துகள் அல்லது இலக்கங்கள்.",
    "Enter a valid WhatsApp number, e.g. 74 104 6208 (do not start with 0).": "சரியான WhatsApp number-ஐ உள்ளிடுங்கள், உதா. 74 104 6208 (0 இல் ஆரம்பிக்க வேண்டாம்).",
    "WhatsApp number should not start with 0 after +94.": "+94 இற்குப் பின் WhatsApp number 0 இல் ஆரம்பிக்கக் கூடாது.",
    "NIC must be exactly 12 digits.": "NIC சரியாக 12 இலக்கங்களாக இருக்க வேண்டும்.",
    "Password must be at least 6 characters.": "Password குறைந்தது 6 எழுத்துகளாக இருக்க வேண்டும்.",
    "Reset password": "Password-ஐ reset செய்யுங்கள்",
    "Enter your username and NIC to set a new password.": "புதிய password அமைக்க உங்கள் username மற்றும் NIC-ஐ உள்ளிடுங்கள்.",
    "The NIC you registered with": "பதிவுசெய்தபோது வழங்கிய NIC",
    "New password": "புதிய password",
    "At least 6 characters": "குறைந்தது 6 எழுத்துகள்",
    "Confirm new password": "புதிய password-ஐ உறுதிப்படுத்துங்கள்",
    "Re-enter new password": "புதிய password-ஐ மீண்டும் உள்ளிடுங்கள்",
    "Remembered it?": "நினைவுக்கு வந்துவிட்டதா?",
    "Back to sign in": "Sign in பக்கத்திற்குத் திரும்ப",

    // --- server validation messages (register / details forms)
    "Batch is required": "Batch அவசியம்",
    "Choose a valid batch": "சரியான batch-ஐத் தெரிவுசெய்யுங்கள்",
    "District is required": "மாவட்டம் அவசியம்",
    "Email address is required": "Email address அவசியம்",
    "Email address is too long": "Email address மிக நீளமாக உள்ளது",
    "Enter a valid WhatsApp number like +94 74 104 6208": "+94 74 104 6208 போன்ற சரியான WhatsApp number-ஐ உள்ளிடுங்கள்",
    "Enter a valid email address": "சரியான email address-ஐ உள்ளிடுங்கள்",
    "Enter a valid email address.": "சரியான email address-ஐ உள்ளிடுங்கள்.",
    "Enter your 12 digit new NIC number": "உங்கள் 12 இலக்கப் புதிய NIC number-ஐ உள்ளிடுங்கள்",
    "Full name can contain letters only": "பெயரில் எழுத்துகள் மட்டுமே இருக்க வேண்டும்",
    "Full name can contain letters only.": "பெயரில் எழுத்துகள் மட்டுமே இருக்க வேண்டும்.",
    "Full name is required": "முழுப் பெயர் அவசியம்",
    "Full name must contain 3–150 characters": "பெயர் 3–150 எழுத்துகளுக்குள் இருக்க வேண்டும்",
    "Mobile number is required": "Mobile number அவசியம்",
    "NIC is required": "NIC அவசியம்",
    "NIC must contain exactly 12 digits": "NIC சரியாக 12 இலக்கங்களாக இருக்க வேண்டும்",
    "NIC must contain exactly 12 digits.": "NIC சரியாக 12 இலக்கங்களாக இருக்க வேண்டும்.",
    "NIC number is required": "NIC number அவசியம்",
    "Password is required": "Password அவசியம்",
    "Password must be at least 6 characters": "Password குறைந்தது 6 எழுத்துகளாக இருக்க வேண்டும்",
    "Please confirm your password": "உங்கள் password-ஐ உறுதிப்படுத்துங்கள்",
    "Registration ID is required": "Registration ID அவசியம்",
    "School is required": "School அவசியம்",
    "School must contain 2–200 characters": "School பெயர் 2–200 எழுத்துகளுக்குள் இருக்க வேண்டும்",
    "Select a batch": "ஒரு batch-ஐத் தெரிவுசெய்யுங்கள்",
    "Select a district": "ஒரு மாவட்டத்தைத் தெரிவுசெய்யுங்கள்",
    "Select a stream": "ஒரு stream-ஐத் தெரிவுசெய்யுங்கள்",
    "Use 3-30 characters: lowercase letters and numbers only": "3-30 எழுத்துகள்: சிறிய எழுத்துகளும் இலக்கங்களும் மட்டும்",
    "Username is required": "Username அவசியம்",

    // --- student analyzer dashboard + report
    "Add attempt": "புதிய attempt",
    "Generate Report": "Report தயாரிக்க",
    "Full report — all attempts": "முழு report — அனைத்து attempts",
    "Report for this attempt": "இந்த attempt-இற்கான report",
    "Your teacher hasn't added any questions yet. Check back soon.": "உங்கள் ஆசிரியர் இன்னும் questions-ஐச் சேர்க்கவில்லை. சிறிது நேரத்தின் பின் பாருங்கள்.",
    "Year": "ஆண்டு",
    "Marks save automatically. Leave any question blank — only the marks you enter are used in the report.":
      "Marks தானாகவே save ஆகும். எந்த question-ஐயும் வெறுமையாக விடலாம் — நீங்கள் உள்ளிடும் marks மட்டுமே report-இல் பயன்படுத்தப்படும்.",
    "Only this attempt and its saved marks will be removed.": "இந்த attempt-உம் அதன் marks-உம் மட்டுமே நீக்கப்படும்.",
    "Could not save marks. Please try again.": "Marks-ஐ save செய்ய முடியவில்லை. மீண்டும் முயற்சிக்கவும்.",
    "Attempt not found.": "அந்த attempt கிடைக்கவில்லை.",
    "Nothing to save": "Save செய்ய எதுவும் இல்லை",
    "No marks entered for this scope yet.": "இதற்கு இன்னும் marks உள்ளிடப்படவில்லை.",
    "Enter some marks on your attempt sheet first, then generate the report.": "முதலில் attempt sheet-இல் marks-ஐ உள்ளிட்டு, பின்னர் report-ஐத் தயாரியுங்கள்.",
    "Back to my attempts": "எனது attempts-இற்குத் திரும்ப",
    "Focus here — weak sections": "இங்கே கவனம் செலுத்துங்கள் — weak sections",
    "Use the resources your teacher attached to rebuild these topics.": "இந்த topics-ஐ மீண்டும் கற்க உங்கள் ஆசிரியர் வழங்கிய resources-ஐப் பயன்படுத்துங்கள்.",
    "Mid-range — push these up": "Mid-range — இவற்றை மேம்படுத்துங்கள்",
    "Strong — keep it up": "Strong — இதே போலத் தொடருங்கள்",
    "What to study to improve": "மேம்பட என்ன படிக்க வேண்டும்",
    "No resources attached by your teacher for this section yet.": "இந்த section-இற்கு ஆசிரியர் இன்னும் resources எதையும் சேர்க்கவில்லை.",

    // --- MCQ exam portal: list + details
    "Hi,": "வணக்கம்,",
    "Hi": "வணக்கம்",
    "Details saved": "விபரங்கள் save செய்யப்பட்டுள்ளன",
    "My Details": "எனது Details",
    "My Result": "எனது Result",
    "My Results": "எனது Results",
    "Search exam name...": "Exam பெயரைத் தேடுங்கள்...",
    "Month": "மாதம்",
    "All batches": "அனைத்து batches",
    "All months": "அனைத்து மாதங்களும்",
    "All years": "அனைத்து ஆண்டுகளும்",
    "Exam name": "Exam பெயர்",
    "Eligible batch": "தகுதியான batch",
    "Opening time": "ஆரம்ப நேரம்",
    "Duration": "கால அளவு",
    "Select Paper": "Paper-ஐத் தெரிவுசெய்க",
    "View Result": "Result பார்க்க",
    "No matching exam papers": "பொருத்தமான exam papers இல்லை",
    "Try changing the search, batch, month or year filters.": "Search, batch, மாதம் அல்லது ஆண்டு filters-ஐ மாற்றிப் பாருங்கள்.",
    "Update your details": "உங்கள் விபரங்களைப் புதுப்பியுங்கள்",
    "Enter your details": "உங்கள் விபரங்களை உள்ளிடுங்கள்",
    "Fill in the required details once before starting PCA examinations.": "PCA exams-ஐ ஆரம்பிக்கும் முன் தேவையான விபரங்களை ஒருமுறை நிரப்புங்கள்.",
    "Personal details": "தனிப்பட்ட விபரங்கள்",
    "Full Name": "முழுப் பெயர்",
    "Academic details": "கல்வி விபரங்கள்",
    "Search batch...": "Batch-ஐத் தேடுங்கள்...",
    "Your school name": "உங்கள் school பெயர்",
    "Select your stream": "உங்கள் stream-ஐத் தெரிவுசெய்யுங்கள்",
    "Search stream...": "Stream-ஐத் தேடுங்கள்...",
    "Your saved exam details will be removed from this browser.": "Save செய்யப்பட்ட exam விபரங்கள் இந்த browser-இலிருந்து நீக்கப்படும்.",
    "Clear saved details": "Save செய்த விபரங்களை நீக்குக",
    "Enter the PCA teacher username and password to manage examinations.": "Exams-ஐ நிர்வகிக்க PCA ஆசிரியரின் username மற்றும் password-ஐ உள்ளிடுங்கள்.",
    "Invalid admin username or password.": "Admin username அல்லது password தவறு.",
    "Enter your Registration ID and NIC number to view all your PCA exam results.": "உங்கள் அனைத்து PCA exam results-ஐயும் பார்க்க Registration ID மற்றும் NIC number-ஐ உள்ளிடுங்கள்.",
    "Enter Registration ID": "Registration ID-ஐ உள்ளிடுங்கள்",
    "Enter 12 digit NIC": "12 இலக்க NIC-ஐ உள்ளிடுங்கள்",
    "All Exams": "அனைத்து Exams",
    "Back to exam papers": "Exam papers-இற்குத் திரும்ப",
    "No fixed limit": "நேர வரம்பு இல்லை",
    "minutes": "நிமிடங்கள்",
    "Available from": "கிடைக்கும் நேரம்",
    "Availability": "கிடைக்கும் காலம்",
    "No closing time": "முடிவு நேரம் இல்லை",
    "Batch category": "Batch வகை",
    "One question at a time": "ஒரு நேரத்தில் ஒரு question",
    "PDF preview available": "PDF preview உள்ளது",
    "Your details": "உங்கள் விபரங்கள்",
    "Opening your paper…": "உங்கள் paper திறக்கப்படுகிறது…",
    "Start / Continue MCQ Paper": "MCQ Paper-ஐ ஆரம்பிக்க / தொடர",
    "Paper is not open": "Paper இன்னும் திறக்கப்படவில்லை",
    "Enter your details once. They will be saved for this and your next PCA examinations.": "உங்கள் விபரங்களை ஒருமுறை உள்ளிடுங்கள். இந்த exam-இற்கும் அடுத்த PCA exams-இற்கும் அவை save செய்யப்படும்.",
    "Enter a valid Registration ID and 12 digit NIC number": "சரியான Registration ID மற்றும் 12 இலக்க NIC number-ஐ உள்ளிடுங்கள்",
    "Enter and save your details before starting the paper": "Paper-ஐ ஆரம்பிக்கும் முன் உங்கள் விபரங்களை உள்ளிட்டு save செய்யுங்கள்",
    "Enter your details to view this result": "இந்த result-ஐப் பார்க்க உங்கள் விபரங்களை உள்ளிடுங்கள்",
    "Exam not found": "Exam கிடைக்கவில்லை",
    "Exam session not found": "Exam session கிடைக்கவில்லை",
    "Result not found": "Result கிடைக்கவில்லை",
    "Submission not found": "Submission கிடைக்கவில்லை",
    "Invalid option": "தவறான option",
    "Invalid question": "தவறான question",
    "Select a valid batch": "சரியான batch-ஐத் தெரிவுசெய்யுங்கள்",
    "Select a valid month": "சரியான மாதத்தைத் தெரிவுசெய்யுங்கள்",
    "Select a valid option": "சரியான option-ஐத் தெரிவுசெய்யுங்கள்",
    "The answering time has ended": "விடையளிக்கும் நேரம் முடிவடைந்துவிட்டது",
    "This exam session does not belong to the saved student details": "இந்த exam session, save செய்யப்பட்ட மாணவர் விபரங்களுக்குரியது அல்ல",
    "This examination has not been submitted yet": "இந்த exam இன்னும் submit செய்யப்படவில்லை",
    "This examination is no longer available": "இந்த exam தற்போது கிடைக்கவில்லை",
    "This examination is not open for answers yet": "இந்த exam இன்னும் விடையளிக்கத் திறக்கப்படவில்லை",
    "This paper has already been submitted": "இந்த paper ஏற்கனவே submit செய்யப்பட்டுவிட்டது",
    "This result does not match the entered Registration ID and NIC number": "இந்த result, உள்ளிடப்பட்ட Registration ID மற்றும் NIC number உடன் பொருந்தவில்லை",
    "We could not find a matching result. Check the Registration ID and NIC number": "பொருத்தமான result கிடைக்கவில்லை. Registration ID மற்றும் NIC number-ஐச் சரிபாருங்கள்",
    "Your saved student details are required": "உங்கள் மாணவர் விபரங்கள் save செய்யப்பட்டிருக்க வேண்டும்",
    "Your time was up, so your paper was submitted automatically.": "நேரம் முடிவடைந்ததால் உங்கள் paper தானாகவே submit செய்யப்பட்டது.",

    // --- answering a paper
    "No limit": "வரம்பு இல்லை",
    "Question navigator": "Questions பட்டியல்",
    "left": "மீதம்",
    "Not yet": "இன்னும் இல்லை",
    "Submit your paper?": "உங்கள் paper-ஐ submit செய்யவா?",
    "Submitting your paper": "உங்கள் paper submit செய்யப்படுகிறது",
    "Time is up - submitting your paper": "நேரம் முடிந்தது - உங்கள் paper submit செய்யப்படுகிறது",
    "Preparing your answers...": "உங்கள் answers தயாராகின்றன...",
    "Preparing your result page...": "உங்கள் result பக்கம் தயாராகிறது...",
    "Please keep this page open.": "தயவுசெய்து இந்தப் பக்கத்தை மூட வேண்டாம்.",
    "Stay calm - your marked answers are being secured.": "பதற்றம் வேண்டாம் - நீங்கள் குறித்த answers பாதுகாப்பாகச் சேமிக்கப்படுகின்றன.",
    "A careful final review is more valuable than a rushed change.": "அவசரமாக மாற்றுவதை விட, இறுதியாகக் கவனமாக ஒருமுறை சரிபார்ப்பதே சிறந்தது.",
    "Read command words closely: calculate, identify and explain are different.": "Calculate, identify, explain போன்ற சொற்களைக் கவனமாக வாசியுங்கள் — ஒவ்வொன்றும் வேறுபட்டவை.",
    "Eliminating impossible options is a powerful MCQ strategy.": "சாத்தியமற்ற options-ஐ முதலில் நீக்குவது MCQ-இற்கு ஒரு சிறந்த உத்தி.",
    "Consistent practice turns difficult questions into familiar patterns.": "தொடர்ச்சியான பயிற்சி கடினமான questions-ஐயும் பழக்கமானவையாக மாற்றும்.",
    "Answer not synced - retrying on submit": "Answer இன்னும் sync ஆகவில்லை - submit செய்யும்போது மீண்டும் முயற்சிக்கப்படும்",
    "Still sending - check your connection and press Submit again": "இன்னும் அனுப்பப்படுகிறது - internet connection-ஐச் சரிபார்த்து மீண்டும் Submit அழுத்துங்கள்",
    "Answers are being saved. Please wait.": "Answers save செய்யப்படுகின்றன. சிறிது காத்திருங்கள்.",
    "Could not submit right now. Please press Submit again.": "இப்போது submit செய்ய முடியவில்லை. மீண்டும் Submit அழுத்துங்கள்.",
    "The server could not save an answer": "Server-இல் answer-ஐ save செய்ய முடியவில்லை",
    "Image for this question is not available.": "இந்த question-இற்கான படம் கிடைக்கவில்லை.",
    "Supporting images": "துணைப் படங்கள்",
    "Supporting image": "துணைப் படம்",
    "Previous image": "முந்தைய படம்",
    "Next image": "அடுத்த படம்",

    // --- results + review
    "Your answers, question by question": "உங்கள் answers, question வாரியாக",
    "Your answer": "உங்கள் answer",
    "Your wrong answer": "உங்கள் தவறான answer",
    "Correct answers appear here once the teacher releases the result.": "ஆசிரியர் result-ஐ வெளியிட்டதும் சரியான answers இங்கே தோன்றும்.",
    "FREE_MARK": "FREE MARK",
    "UNANSWERED": "NOT ANSWERED",
    "This question was withdrawn by the teacher, so everyone receives its mark.": "இந்த question ஆசிரியரால் நீக்கப்பட்டதால், அனைவருக்கும் அதற்கான mark வழங்கப்படும்.",
    "Yours ✓": "உங்களுடையது ✓",
    "Yours": "உங்களுடையது",
    "You did not answer this question.": "இந்த question-இற்கு நீங்கள் விடையளிக்கவில்லை.",
    "or": "அல்லது",
    "RESULT RELEASED": "RESULT வெளியாகிவிட்டது",
    "RESULTS COMING SOON": "RESULT விரைவில்",
    "Your marked result and corrected MCQ sheet are ready.": "உங்கள் result-உம் திருத்தப்பட்ட MCQ sheet-உம் தயார்.",
    "Your answers are submitted. The result will appear here after the teacher releases it.": "உங்கள் answers submit செய்யப்பட்டன. ஆசிரியர் வெளியிட்டதும் result இங்கே தோன்றும்.",
    "Percentage": "சதவீதம்",
    "Correct answers": "சரியான answers",
    "Results coming soon": "Result விரைவில் வெளியாகும்",
    "You can revisit My Results anytime. Your corrected sheet will be enabled automatically after release.": "எந்நேரமும் My Results-இற்கு வந்து பார்க்கலாம். Result வெளியானதும் திருத்தப்பட்ட sheet தானாகவே கிடைக்கும்.",
    "Loading question paper...": "Question paper load ஆகிறது...",
    "Loading paper preview...": "Paper preview load ஆகிறது...",
    "Preview paper": "Paper-ஐப் பார்க்க",
    "Exam and student details": "Exam மற்றும் மாணவர் விபரங்கள்",
    "Student name": "மாணவர் பெயர்",
    "Started": "ஆரம்பித்த நேரம்",
    "Submitted": "Submit செய்த நேரம்",
    "Corrected MCQ Sheet": "திருத்தப்பட்ட MCQ Sheet",
    "Blue X: your selection · Green: accepted answer · Red: incorrect selection": "நீல X: உங்கள் தெரிவு · பச்சை: சரியான answer · சிவப்பு: தவறான தெரிவு",
    "Open separately": "தனியாகத் திறக்க",
    "View your result": "உங்கள் result-ஐப் பாருங்கள்",
    "Find My Result": "எனது Result-ஐத் தேடுக",
    "Paper submitted": "Paper submit செய்யப்பட்டது",
    "Result is not released yet.": "Result இன்னும் வெளியிடப்படவில்லை.",
    "Your answers are safely submitted. Your score and corrected MCQ sheet will appear here when the teacher releases the result.":
      "உங்கள் answers பாதுகாப்பாக submit செய்யப்பட்டன. ஆசிரியர் result-ஐ வெளியிட்டதும் உங்கள் score-உம் திருத்தப்பட்ட MCQ sheet-உம் இங்கே தோன்றும்.",
    "Change ID": "ID-ஐ மாற்றுக",
    "PERSONAL PERFORMANCE": "உங்கள் PERFORMANCE",
    ", your latest PCA examinations are shown first.": ", உங்கள் அண்மைய PCA exams முதலில் காட்டப்படுகின்றன.",
    "Exams completed": "நிறைவுசெய்த exams",
    "Average percentage": "சராசரி சதவீதம்",
    "Last 5 average": "இறுதி 5 சராசரி",
    "Results released": "வெளியான results",
    "Last 5 exam percentages": "இறுதி 5 exam சதவீதங்கள்",
    "Released results will build your graph here.": "Results வெளியாக வெளியாக உங்கள் graph இங்கே உருவாகும்.",
    "All completed papers": "நிறைவுசெய்த அனைத்து papers",
    "Search exam": "Exam-ஐத் தேடுக",
    "Type an exam name...": "Exam பெயரை உள்ளிடுங்கள்...",
    "Question-by-question paper": "Question வாரியாக வரும் paper",
    "Your paper is submitted safely. The score will appear after release.": "உங்கள் paper பாதுகாப்பாக submit செய்யப்பட்டது. Result வெளியானதும் score தோன்றும்.",
    "View corrected result": "திருத்தப்பட்ட result-ஐப் பார்க்க",
    "View submission details": "Submission விபரங்களைப் பார்க்க",
    "No matching results": "பொருத்தமான results இல்லை",
    "Change the exam name, batch, month or year filters.": "Exam பெயர், batch, மாதம் அல்லது ஆண்டு filters-ஐ மாற்றுங்கள்.",
    "No submitted examinations yet": "இன்னும் submit செய்த exams இல்லை",
    "Completed exam papers matching this Registration ID and NIC will appear here.": "இந்த Registration ID மற்றும் NIC-இற்குரிய நிறைவுசெய்த exam papers இங்கே தோன்றும்.",
    "Browse Exams": "Exams-ஐப் பார்வையிட"
  };

  // Text with numbers or names in it. Each entry: [regex on the English text, Tamil builder].
  var PATTERNS = [
    [/^Attempt (\d+) added — enter your marks\.$/, "Attempt $1 சேர்க்கப்பட்டது — உங்கள் marks-ஐ உள்ளிடுங்கள்."],
    [/^Attempt (\d+) deleted\.$/, "Attempt $1 நீக்கப்பட்டது."],
    [/^Saved (\d+) marks? for Attempt (\d+)\.$/, "Attempt $2-இற்கு $1 marks save செய்யப்பட்டன."],
    [/^Report for this attempt \(Attempt (\d+)\)$/, "இந்த attempt-இற்கான report (Attempt $1)"],
    [/^Report for Attempt (\d+)$/, "Attempt $1-இற்கான report"],
    [/^Delete Attempt (\d+)\?$/, "Attempt $1-ஐ நீக்கவா?"],
    [/^Only Attempt (\d+) and its saved marks will be removed\. Other attempts will stay unchanged\.$/,
      "Attempt $1-உம் அதன் marks-உம் மட்டுமே நீக்கப்படும். ஏனைய attempts மாற்றமின்றி இருக்கும்."],
    [/^(\d+) questions? answered across (\d+) section\(s\)$/, "$2 sections-இல் $1 questions-இற்கு விடையளிக்கப்பட்டுள்ளது"],
    [/^You answered (\d+) of (\d+) questions\. (-?\d+) questions are unanswered\. Your saved answers will be submitted as final\.$/,
      "$2 questions-இல் $1-இற்கு விடையளித்துள்ளீர்கள். $3 questions-இற்கு விடையளிக்கவில்லை. Save செய்யப்பட்ட answers இறுதியாக submit செய்யப்படும்."],
    [/^Supporting image (\d+) of (\d+)$/, "துணைப் படம் $1 / $2"],
    [/^Open supporting image (\d+)$/, "துணைப் படம் $1-ஐத் திறக்க"],
    [/^Supporting image (\d+)$/, "துணைப் படம் $1"],
    [/^Question weight (.+) out of 5$/, "Question weight: 5-இற்கு $1"],
    [/^(\d+) minutes$/, "$1 நிமிடங்கள்"],
    [/^Opens (.+)$/, "ஆரம்பம்: $1"],
    [/^Your answer: (.+)$/, "உங்கள் answer: $1"],
    [/^Correct answer: (.+)$/, "சரியான answer: $1"],
    [/^Hi, (.+)$/, "வணக்கம், $1"],
    [/^(.+) · My Result$/, "$1 · எனது Result"],
    [/^My Results · PCA MCQ$/, "எனது Results · PCA MCQ"]
  ];

  function translate(text) {
    var key = text.replace(/[\s ]+/g, " ").trim().replace(/[‘’]/g, "'");
    if (!key || !/[A-Za-z]/.test(key)) return null;
    var hit = TA[key];
    if (hit === undefined && key.slice(-3) === "...") hit = TA[key.slice(0, -3) + "…"];
    if (hit === undefined) {
      for (var i = 0; i < PATTERNS.length; i++) {
        if (PATTERNS[i][0].test(key)) { hit = key.replace(PATTERNS[i][0], PATTERNS[i][1]); break; }
      }
    }
    if (hit === undefined || hit === key) return null;
    // Keep the surrounding whitespace so inline layout (icon + label) is unchanged.
    var lead = text.match(/^\s*/)[0], trail = text.match(/\s*$/)[0];
    return lead + hit + trail;
  }
  window.PcaLang.t = function (s) { var r = translate(String(s)); return r === null ? s : r.trim(); };

  var SKIP = { SCRIPT: 1, STYLE: 1, TEXTAREA: 1, CODE: 1, PRE: 1, NOSCRIPT: 1 };
  var ATTRS = ["placeholder", "title", "aria-label"];

  function skipped(el) {
    return !el || SKIP[el.nodeName] || (el.closest && el.closest("[data-no-i18n],.lang-switch"));
  }

  function doText(node) {
    if (skipped(node.parentNode)) return;
    var out = translate(node.data);
    if (out !== null) node.data = out;
  }

  function doElement(el) {
    if (skipped(el)) return;
    for (var i = 0; i < ATTRS.length; i++) {
      var v = el.getAttribute(ATTRS[i]);
      if (v) { var out = translate(v); if (out !== null) el.setAttribute(ATTRS[i], out); }
    }
    if (el.nodeName === "INPUT" && (el.type === "submit" || el.type === "button") && el.value) {
      var val = translate(el.value);
      if (val !== null) el.value = val;
    }
  }

  function walk(root) {
    if (root.nodeType === 3) { doText(root); return; }
    if (root.nodeType !== 1 || skipped(root)) return;
    doElement(root);
    var tw = document.createTreeWalker(root, 5 /* elements + text */, null);
    var n;
    while ((n = tw.nextNode())) {
      if (n.nodeType === 3) doText(n); else doElement(n);
    }
  }

  new MutationObserver(function (records) {
    for (var i = 0; i < records.length; i++) {
      var r = records[i];
      if (r.type === "characterData") doText(r.target);
      else if (r.type === "attributes") doElement(r.target);
      else for (var j = 0; j < r.addedNodes.length; j++) walk(r.addedNodes[j]);
    }
  }).observe(document.documentElement, {
    childList: true, subtree: true, characterData: true,
    attributes: true, attributeFilter: ATTRS
  });
  if (document.body) walk(document.body);
  document.addEventListener("DOMContentLoaded", function () {
    var t = translate(document.title);
    if (t !== null) document.title = t.trim();
  });

  // Native alert / confirm popups from page scripts.
  var nativeAlert = window.alert, nativeConfirm = window.confirm;
  window.alert = function (msg) { return nativeAlert.call(window, window.PcaLang.t(msg)); };
  window.confirm = function (msg) { return nativeConfirm.call(window, window.PcaLang.t(msg)); };
})();
