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
  var autosaveTimer = null;
  var autosaveChain = Promise.resolve();
  var answerRevision = 0;
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

  function bulkAnswerRequest(answers) {
    var data = new URLSearchParams();
    Object.keys(answers).forEach(function (question) {
      data.append("q" + question, String(answers[question]));
    });
    if (csrf) data.append(csrf.name, csrf.value);
    return fetch("/exam/session/" + submission + "/answers", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: data.toString()
    }).then(function (response) {
      return response.text().then(function (bodyText) {
        var bodyJson = {};
        try { bodyJson = bodyText ? JSON.parse(bodyText) : {}; } catch (error) { /* Keep the fallback message. */ }
        if (!response.ok) throw new Error(bodyJson.message || "The server could not save an answer");
        return bodyJson;
      });
    });
  }

  function scheduleAutosave(delay) {
    answerRevision++;
    var revision = answerRevision;
    window.clearTimeout(autosaveTimer);
    saveState.innerHTML = '<i class="bi bi-cloud-arrow-up"></i> Saving...';
    autosaveTimer = window.setTimeout(function () {
      autosaveTimer = null;
      autosaveChain = autosaveChain.then(function () {
        return bulkAnswerRequest(current());
      }).then(function () {
        if (revision === answerRevision && !submitting) {
          saveState.innerHTML = '<i class="bi bi-cloud-check"></i> Saved';
        }
      }).catch(function () {
        if (!submitting) {
          saveState.innerHTML = '<i class="bi bi-wifi-off"></i> Answer not synced - retrying on submit';
        }
      });
    }, delay == null ? 180 : delay);
  }

  function flushAnswers(onProgress) {
    window.clearTimeout(autosaveTimer);
    autosaveTimer = null;
    answerRevision++;
    var answers = current();
    var questions = Object.keys(answers);
    return autosaveChain.then(function () {
      return bulkAnswerRequest(answers).then(function (result) {
        if (onProgress) onProgress(questions.length, questions.length);
        return result;
      });
    });
  }

  function showSubmissionLoader(answerCount) {
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
      '<h2>Submitting your paper</h2>' +
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
    var local = JSON.parse(localStorage.getItem(storageKey) || "{}");
    var restored = false;
    Object.keys(local).forEach(function (question) {
      var input = document.getElementById("q" + question + "o" + local[question]);
      if (input && !input.checked) {
        input.checked = true;
        restored = true;
      }
    });
    if (restored) scheduleAutosave(40);
  } catch (error) { /* Ignore invalid local cache. */ }

  sheet.addEventListener("change", function (event) {
    if (!event.target.matches('input[type="radio"]')) return;
    localSave();
    update();
    scheduleAutosave();
  });

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    if (submitting) return;

    var answered = Object.keys(current()).length;
    var submitButton = form.querySelector(".submit-button");
    var originalButton = submitButton.innerHTML;
    window.PcaDialog.confirm(
      "You answered " + answered + " of " + total + " questions. " + (total - answered) +
      " questions are unanswered. Your saved answers will be submitted as final.",
      { title: "Submit your paper?", acceptText: "Submit Paper" }
    ).then(function (approved) {
      if (!approved) return;
      submitting = true;
      var loader = showSubmissionLoader(answered);
      submitButton.disabled = true;
      submitButton.innerHTML = 'Saving &amp; submitting... <i class="bi bi-cloud-arrow-up-fill"></i>';
      saveState.innerHTML = '<i class="bi bi-cloud-arrow-up"></i> Saving final answers...';

      flushAnswers(loader.update).then(function () {
        try { localStorage.removeItem(storageKey); } catch (error) { /* Storage is optional. */ }
        saveState.innerHTML = '<i class="bi bi-cloud-check"></i> Answers saved';
        loader.finishing();
        HTMLFormElement.prototype.submit.call(form);
      }).catch(function (error) {
        loader.close();
        submitting = false;
        submitButton.disabled = false;
        submitButton.innerHTML = originalButton;
        saveState.innerHTML = '<i class="bi bi-wifi-off"></i> Could not submit - check your connection';
        window.PcaDialog.alert(error && error.message
          ? error.message
          : "Your answers could not be saved. Please try Submit Paper again.",
          { title: "Submission not sent", type: "error" });
      });
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
      saveState.textContent = "Time ended - submit your saved answers";
      return;
    }
    remaining--;
    window.setTimeout(tick, 1000);
  }

  update();
  tick();
})();
