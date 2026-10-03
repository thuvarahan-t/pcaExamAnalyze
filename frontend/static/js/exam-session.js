(function () {
  "use strict";

  var body = document.body;
  var sheet = document.getElementById("answerSheet");
  var form = document.getElementById("submitForm");
  if (!sheet || !form) return;

  var submission = body.dataset.submission;
  var total = Number(body.dataset.total || 0);
  var remaining = Number(body.dataset.remaining || -1);
  var csrf = form.querySelector('input[name="_csrf"]');
  var saveState = document.getElementById("saveState");
  var timer = document.getElementById("examTimer");
  var storageKey = "pca-mcq-session-" + submission;
  var submitting = false;

  var paperDialog = document.getElementById("paperPreviewDialog");
  var paperOpen = document.getElementById("paperPreviewOpen");
  var paperClose = document.getElementById("paperPreviewClose");
  if (paperDialog && paperOpen) paperOpen.addEventListener("click", function () {
    var frame = paperDialog.querySelector("iframe[data-src]");
    if (frame && !frame.src) frame.src = frame.dataset.src;
    paperDialog.showModal();
  });
  if (paperDialog && paperClose) paperClose.addEventListener("click", function () { paperDialog.close(); });
  if (paperDialog) paperDialog.addEventListener("click", function (event) {
    if (event.target === paperDialog) paperDialog.close();
  });

  function current() {
    var answers = {};
    sheet.querySelectorAll('input[type="radio"]:checked').forEach(function (input) {
      answers[input.name.substring(1)] = Number(input.value);
    });
    return answers;
  }

  function update() {
    var answered = Object.keys(current()).length;
    var percentage = total ? Math.round(answered * 100 / total) : 0;
    document.getElementById("progressCopy").textContent = answered + " of " + total + " answered";
    document.getElementById("progressPercent").textContent = percentage + "%";
    document.getElementById("progressFill").style.width = percentage + "%";
    document.getElementById("dockAnswered").textContent = answered;
    document.getElementById("dockUnanswered").textContent = Math.max(0, total - answered);
  }

  function localSave() {
    try { localStorage.setItem(storageKey, JSON.stringify(current())); } catch (error) { /* Storage is optional. */ }
  }

  // ---- Pending queue: the browser is the source of truth until the server confirms each answer. ----
  // Every click is written to localStorage first, then sent on its own. An entry is removed only
  // when the server confirms that exact version, and failures are retried with backoff.
  var pendingKey = "pca-mcq-pending-" + submission;
  var BACKOFF = [1000, 2000, 4000, 8000, 10000];
  var pending = loadPending();
  var versionCounter = 0;
  var flushing = false;
  var retryTimer = null;
  var retryStep = 0;
  var rejectedCount = 0;

  function loadPending() {
    try {
      var raw = JSON.parse(localStorage.getItem(pendingKey) || "{}");
      return raw && typeof raw === "object" ? raw : {};
    } catch (error) { return {}; }
  }

  function savePending() {
    try { localStorage.setItem(pendingKey, JSON.stringify(pending)); } catch (error) { /* Storage is optional. */ }
  }

  function pendingCount() { return Object.keys(pending).length; }

  function showSaveState() {
    if (submitting) return;
    var count = pendingCount();
    saveState.classList.toggle("save-warn", count > 0 || rejectedCount > 0);
    if (count > 0) {
      saveState.innerHTML = '<i class="bi bi-cloud-slash"></i> ' + count + (count === 1 ? " answer" : " answers") +
        ' not saved yet' + (retryTimer || flushing ? " - saving..." : " - check your connection");
    } else if (rejectedCount > 0) {
      saveState.innerHTML = '<i class="bi bi-exclamation-triangle"></i> Some answers were not accepted (time may be over)';
    } else {
      saveState.innerHTML = '<i class="bi bi-cloud-check"></i> Saved';
    }
  }

  function singleAnswerRequest(question, option) {
    var data = new URLSearchParams();
    data.append("question", String(question));
    if (option == null) data.append("clear", "true"); else data.append("option", String(option));
    if (csrf) data.append(csrf.name, csrf.value);
    return fetch("/exam/session/" + submission + "/answer", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: data.toString()
    });
  }

  function scheduleRetry() {
    window.clearTimeout(retryTimer);
    var delay = BACKOFF[Math.min(retryStep, BACKOFF.length - 1)];
    retryStep++;
    retryTimer = window.setTimeout(function () { retryTimer = null; flush(); }, delay);
    showSaveState();
  }

  function flush() {
    if (flushing) return;
    var keys = Object.keys(pending);
    if (!keys.length) { showSaveState(); return; }
    window.clearTimeout(retryTimer);
    retryTimer = null;
    flushing = true;
    var question = keys[0];
    var entry = pending[question];
    showSaveState();
    singleAnswerRequest(question, entry.o).then(function (response) {
      // 200 = stored. 422 = definitive refusal (paper closed / invalid): retrying cannot help.
      if (response.status !== 200 && response.status !== 422) throw new Error("status " + response.status);
      if (response.status === 422) rejectedCount++;
      // Remove only if the student has not changed this question while the request was in flight.
      if (pending[question] && pending[question].v === entry.v) {
        delete pending[question];
        savePending();
      }
      retryStep = 0;
      flushing = false;
      flush();
    }).catch(function () {
      flushing = false;
      scheduleRetry();
    });
  }

  function enqueue(question, option) {
    versionCounter++;
    pending[question] = { o: option, v: versionCounter };
    savePending();
    showSaveState();
    flush();
  }

  window.addEventListener("online", function () { retryStep = 0; flush(); });
  document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "visible" && pendingCount()) { retryStep = 0; flush(); }
  });

  // Time per question (step-mode exams only, from exam-step.js) goes through its own cheap endpoint, at most
  // once a minute and never while answers are waiting to be saved. It is analytics only and best effort:
  // the final submit carries the full totals, so a missed sync loses nothing.
  var TIMES_INTERVAL_MS = 60000;
  var lastTimesSync = 0;
  var timesInFlight = false;

  function syncTimes(force) {
    if (submitting || timesInFlight || typeof window.PcaExamExtras !== "function") return;
    if (!force && (Date.now() - lastTimesSync < TIMES_INTERVAL_MS || pendingCount() > 0 || flushing)) return;
    var extras = window.PcaExamExtras();
    if (!extras || !Object.keys(extras).length) return;
    var data = new URLSearchParams();
    Object.keys(extras).forEach(function (key) { data.append(key, String(extras[key])); });
    if (csrf) data.append(csrf.name, csrf.value);
    timesInFlight = true;
    lastTimesSync = Date.now();
    fetch("/exam/session/" + submission + "/times", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: data.toString()
    }).catch(function () { /* Best effort. */ }).then(function () { timesInFlight = false; });
  }

  function showSubmissionLoader(answerCount, timeUp) {
    var tips = [
      "Stay calm - your marked answers are being secured.",
      "A careful final review is more valuable than a rushed change.",
      "Read command words closely: calculate, identify and explain are different.",
      "Eliminating impossible options is a powerful MCQ strategy.",
      "Consistent practice turns difficult questions into familiar patterns."
    ];
    var tipIndex = Math.floor(Math.random() * tips.length);
    var overlay = document.createElement("div");
    overlay.className = "exam-submit-loader";
    overlay.setAttribute("role", "status");
    overlay.setAttribute("aria-live", "polite");
    overlay.innerHTML = '<section class="exam-submit-loader-card">' +
      '<div class="submit-loader-orbit" aria-hidden="true"><span></span><i class="bi bi-check2-square"></i></div>' +
      '<h2>' + (timeUp ? "Time is up - submitting your paper" : "Submitting your paper") + '</h2>' +
      '<p class="submit-loader-progress">Preparing your answers...</p>' +
      '<div class="submit-loader-track"><span></span></div>' +
      '<div class="submit-loader-tip"><i class="bi bi-lightbulb-fill"></i><div><strong>Quick exam tip</strong><span></span></div></div>' +
      '<small>Please keep this page open.</small></section>';
    var progress = overlay.querySelector(".submit-loader-progress");
    var fill = overlay.querySelector(".submit-loader-track span");
    var tip = overlay.querySelector(".submit-loader-tip span");
    tip.textContent = tips[tipIndex];
    document.body.appendChild(overlay);
    document.body.classList.add("exam-submit-busy");
    var tipTimer = window.setInterval(function () {
      tipIndex = (tipIndex + 1) % tips.length;
      tip.classList.remove("tip-in");
      window.requestAnimationFrame(function () {
        tip.textContent = tips[tipIndex];
        tip.classList.add("tip-in");
      });
    }, 2400);
    return {
      update: function (saved, count) {
        var safeCount = Math.max(1, count || answerCount || 1);
        progress.textContent = "Saving answer " + saved + " of " + (count || answerCount) + "...";
        fill.style.width = Math.min(96, Math.round(saved * 96 / safeCount)) + "%";
      },
      message: function (text) {
        progress.textContent = text;
      },
      finishing: function () {
        progress.textContent = "Preparing your result page...";
        fill.style.width = "100%";
      },
      close: function () {
        window.clearInterval(tipTimer);
        overlay.remove();
        document.body.classList.remove("exam-submit-busy");
      }
    };
  }

  try {
    // Re-apply answers that were never confirmed (page reload / lost connection), then send them.
    var local = JSON.parse(localStorage.getItem(storageKey) || "{}");
    Object.keys(local).forEach(function (question) {
      var input = document.getElementById("q" + question + "o" + local[question]);
      if (input && !input.checked) {
        input.checked = true;
        if (!pending[question]) {
          versionCounter++;
          pending[question] = { o: Number(local[question]), v: versionCounter };
        }
      }
    });
    Object.keys(pending).forEach(function (question) {
      if (pending[question].o == null) return;
      var input = document.getElementById("q" + question + "o" + pending[question].o);
      if (input && !input.checked) input.checked = true;
    });
    savePending();
  } catch (error) { /* Ignore invalid local cache. */ }

  sheet.addEventListener("change", function (event) {
    if (!event.target.matches('input[type="radio"]')) return;
    localSave();
    update();
    enqueue(event.target.name.substring(1), event.target.checked ? Number(event.target.value) : null);
  });

  function finishRequest(answers, expected) {
    var data = new URLSearchParams();
    Object.keys(answers).forEach(function (question) { data.append("q" + question, String(answers[question])); });
    var extras = typeof window.PcaExamExtras === "function" ? window.PcaExamExtras() : null;
    if (extras) Object.keys(extras).forEach(function (key) { data.append(key, String(extras[key])); });
    data.append("expected", String(expected));
    if (csrf) data.append(csrf.name, csrf.value);
    return fetch("/exam/session/" + submission + "/finish", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", "Accept": "application/json" },
      body: data.toString()
    }).then(function (response) {
      return response.text().then(function (bodyText) {
        var json = {};
        try { json = bodyText ? JSON.parse(bodyText) : {}; } catch (error) { /* Not JSON (e.g. a proxy error page). */ }
        return { status: response.status, json: json };
      });
    });
  }

  /**
   * Submits through fetch so failures are visible and retried. The request carries every answer and the
   * count the page expects; the server answers with how many it stored. A shortfall or a network error
   * re-sends automatically (the call is idempotent). The local backup is kept until the result page loads.
   */
  function submitNow(auto) {
    if (submitting) return;
    var submitButton = form.querySelector(".submit-button");
    var originalButton = submitButton.innerHTML;
    submitting = true;
    var loader = showSubmissionLoader(Object.keys(current()).length, auto);
    submitButton.disabled = true;
    submitButton.innerHTML = 'Saving &amp; submitting... <i class="bi bi-cloud-arrow-up-fill"></i>';
    saveState.innerHTML = '<i class="bi bi-cloud-arrow-up"></i> Saving final answers...';

    var attempts = 0;
    var MAX_ATTEMPTS = 12;

    function giveUp() {
      loader.close();
      submitting = false;
      submitButton.disabled = false;
      submitButton.innerHTML = originalButton;
      saveState.className = (saveState.className + " save-warn").trim();
      saveState.innerHTML = '<i class="bi bi-wifi-off"></i> Could not confirm your answers were saved - check your connection and press Submit again';
    }

    function retry(message) {
      attempts++;
      if (attempts >= MAX_ATTEMPTS) { giveUp(); return; }
      loader.message(message);
      window.setTimeout(attempt, BACKOFF[Math.min(attempts - 1, BACKOFF.length - 1)]);
    }

    function attempt() {
      var answers = current();
      var expected = Object.keys(answers).length;
      loader.message("Saving " + expected + " answers...");
      finishRequest(answers, expected).then(function (result) {
        if (result.status === 200 && result.json && typeof result.json.saved === "number") {
          if (result.json.saved >= expected) {
            loader.finishing();
            window.location.href = result.json.redirect;
            return;
          }
          retry("Only " + result.json.saved + " of " + expected + " answers confirmed - sending again...");
          return;
        }
        if (result.status === 400 && result.json && result.json.message) {
          // Definitive refusal (e.g. the saved student details are gone): retrying cannot help.
          giveUp();
          saveState.innerHTML = '<i class="bi bi-exclamation-triangle"></i> ' + result.json.message;
          return;
        }
        retry("Connection problem - retrying...");
      }).catch(function () {
        retry("Connection problem - retrying...");
      });
    }

    attempt();
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    if (submitting) return;

    var answered = Object.keys(current()).length;
    window.PcaDialog.confirm(
      "You answered " + answered + " of " + total + " questions. " + (total - answered) +
      " questions are unanswered. Your saved answers will be submitted as final.",
      { title: "Submit your paper?", acceptText: "Submit Paper" }
    ).then(function (approved) {
      if (approved) submitNow(false);
    });
  });

  function tick() {
    if (remaining < 0) {
      document.getElementById("timerText").textContent = "No limit";
      return;
    }
    var minutes = Math.floor(remaining / 60);
    var seconds = remaining % 60;
    document.getElementById("timerText").textContent = String(minutes).padStart(2, "0") + ":" + String(seconds).padStart(2, "0");
    timer.classList.toggle("warning", remaining <= 600 && remaining > 120);
    timer.classList.toggle("danger", remaining <= 120);
    if (remaining <= 0) {
      sheet.querySelectorAll("input").forEach(function (input) { input.disabled = true; });
      saveState.textContent = "Time is up - submitting your paper";
      // Close any open dialog (e.g. the submit confirmation) and submit automatically.
      document.querySelectorAll(".pca-dialog-overlay").forEach(function (overlay) { overlay.remove(); });
      // Spread the end-of-exam submit spike: each browser waits a random 0-3 s (answers are already locked).
      window.setTimeout(function () { submitNow(true); }, Math.floor(Math.random() * 3000));
      return;
    }
    remaining--;
    window.setTimeout(tick, 1000);
  }

  window.PcaExamSession = {
    save: function () { syncTimes(false); },
    isSubmitting: function () { return submitting; }
  };

  update();
  flush();
  tick();
})();
