(function () {
  "use strict";

  // Question-by-question navigation for image-sheet exams. Saving, timer and submit
  // come from exam-session.js, which treats #answerSheet like the classic OMR sheet.
  var sheet = document.getElementById("answerSheet");
  if (!sheet) return;

  var submission = document.body.dataset.submission;
  var questions = Array.from(sheet.querySelectorAll(".step-question"));
  var paletteButtons = Array.from(document.querySelectorAll(".step-palette-grid [data-go]"));
  var numberLabel = document.getElementById("stepNumber");
  var weightBadge = document.getElementById("stepWeight");
  var flagButton = document.getElementById("stepFlag");
  var prev = document.getElementById("stepPrev");
  var next = document.getElementById("stepNext");
  var clear = document.getElementById("stepClear");
  var total = questions.length;
  var positionKey = "pca-mcq-step-" + submission;
  var flagsKey = "pca-mcq-flags-" + submission;
  var timesKey = "pca-mcq-times-" + submission;
  var index = 0;
  var viewers = [];

  function readJson(key, fallback) {
    try { return JSON.parse(localStorage.getItem(key) || "") || fallback; } catch (error) { return fallback; }
  }
  function writeJson(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (error) { /* Storage is optional. */ }
  }

  var flags = readJson(flagsKey, {});
  var times = readJson(timesKey, {});

  // Image zoom/pan comes from exam-zoom.js (shared with the result review).
  var createViewer = window.PcaZoom.create;

  questions.forEach(function (question, i) {
    var box = question.querySelector(".step-viewer");
    viewers[i] = box ? createViewer(box) : null;
  });

  // ---------------------------------------------------------------- navigation
  function loadImage(i) {
    var question = questions[i];
    if (!question) return;
    var img = question.querySelector("img[data-src]");
    if (img && !img.getAttribute("src")) img.src = img.dataset.src;
  }

  function answered(question) {
    return !!question.querySelector('input[type="radio"]:checked');
  }

  function syncPalette() {
    paletteButtons.forEach(function (button, i) {
      var q = questions[i].dataset.question;
      button.classList.toggle("answered", answered(questions[i]));
      button.classList.toggle("flagged", !!flags[q]);
      button.classList.toggle("current", i === index);
      if (i === index) button.setAttribute("aria-current", "step");
      else button.removeAttribute("aria-current");
    });
    clear.disabled = !answered(questions[index]);
    var flagged = !!flags[questions[index].dataset.question];
    flagButton.classList.toggle("on", flagged);
    flagButton.setAttribute("aria-pressed", flagged ? "true" : "false");
    flagButton.querySelector("i").className = flagged ? "bi bi-flag-fill" : "bi bi-flag";
    flagButton.querySelector("span").textContent = flagged ? "Flagged" : "Flag";
  }

  /** Traffic light 1 green → 2 lime → 3 amber → 4 orange → 5 red (matches McqExamQuestion.weightColor). */
  function weightColor(weight) {
    if (!weight) return "#94a3b8";
    var hues = [135, 90, 40, 22, 0];
    var position = Math.max(0, Math.min(4, weight - 1));
    var low = Math.floor(position);
    var high = Math.min(4, low + 1);
    return "hsl(" + Math.round(hues[low] + (hues[high] - hues[low]) * (position - low)) + " 80% 40%)";
  }

  /** Weight 1-5 in 0.5 steps: full stars, a half star for .5, traffic-light colour 1 green → 5 red. */
  function showWeight(weight) {
    weightBadge.hidden = !weight;
    if (!weight) return;
    var full = Math.floor(weight);
    var half = weight % 1 !== 0;
    weightBadge.style.setProperty("--w", weightColor(weight));
    weightBadge.title = "Question weight " + weight + " out of 5";
    weightBadge.innerHTML = new Array(full + 1).join('<i class="bi bi-star-fill"></i>') +
      (half ? '<i class="bi bi-star-half"></i>' : "") + "<b>" + weight + "/5</b>";
  }

  function show(i, focus) {
    if (!total) return;
    var target = Math.max(0, Math.min(total - 1, i));
    if (target !== index && viewers[index]) viewers[index].reset();
    var moved = index !== -1 && target !== index;
    index = target;
    questions.forEach(function (question, qi) { question.hidden = qi !== index; });
    loadImage(index);
    loadImage(index + 1);
    numberLabel.textContent = index + 1;
    showWeight(Math.round((Number(questions[index].dataset.weight) || 0) * 2) / 2);
    prev.disabled = index === 0;
    next.disabled = index === total - 1;
    syncPalette();
    try { sessionStorage.setItem(positionKey, String(index)); } catch (error) { /* Optional. */ }
    if (focus) {
      var input = questions[index].querySelector("input:checked") || questions[index].querySelector("input");
      if (input && !input.disabled) input.focus({ preventScroll: true });
    }
    if (sheet.getBoundingClientRect().top < 0) sheet.scrollIntoView({ behavior: "smooth", block: "start" });
    // Moving between questions syncs the time spent so far.
    if (moved && window.PcaExamSession) window.PcaExamSession.save();
  }

  prev.addEventListener("click", function () { show(index - 1); });
  next.addEventListener("click", function () { show(index + 1); });
  paletteButtons.forEach(function (button, i) {
    button.addEventListener("click", function () { show(i); });
  });

  flagButton.addEventListener("click", function () {
    var q = questions[index].dataset.question;
    if (flags[q]) delete flags[q]; else flags[q] = true;
    writeJson(flagsKey, flags);
    syncPalette();
  });

  clear.addEventListener("click", function () {
    var checked = questions[index].querySelector('input[type="radio"]:checked');
    if (!checked || checked.disabled) return;
    checked.checked = false;
    // exam-session.js saves the full answer set on change, so the cleared answer is dropped.
    checked.dispatchEvent(new Event("change", { bubbles: true }));
  });

  sheet.addEventListener("change", function (event) {
    if (event.target.matches('input[type="radio"]')) syncPalette();
  });

  document.addEventListener("keydown", function (event) {
    if (event.altKey || event.ctrlKey || event.metaKey) return;
    if (document.querySelector("dialog[open], .exam-submit-loader, .pca-dialog-overlay")) return;
    if (event.key === "ArrowRight") { event.preventDefault(); show(index + 1, true); }
    else if (event.key === "ArrowLeft") { event.preventDefault(); show(index - 1, true); }
    else if (/^[1-9]$/.test(event.key)) {
      var input = questions[index].querySelector('input[value="' + event.key + '"]');
      if (input && !input.disabled && !input.checked) {
        input.checked = true;
        input.dispatchEvent(new Event("change", { bubbles: true }));
      }
    }
  });

  // ---------------------------------------------------------------- time per question
  // Seconds the current question is on screen (tab visible) accumulate locally and are
  // sent with every answer save as t{n}; the teacher compares them with the expected time.
  window.PcaExamExtras = function () {
    var extra = {};
    Object.keys(times).forEach(function (q) { extra["t" + q] = Math.round(times[q]); });
    return extra;
  };

  var ticks = 0;
  window.setInterval(function () {
    if (document.visibilityState !== "visible") return;
    if (window.PcaExamSession && window.PcaExamSession.isSubmitting()) return;
    var q = questions[index] && questions[index].dataset.question;
    if (!q) return;
    times[q] = (times[q] || 0) + 1;
    writeJson(timesKey, times);
    ticks++;
    if (ticks % 45 === 0 && window.PcaExamSession) window.PcaExamSession.save();
  }, 1000);

  var start = 0;
  var hash = /^#q(\d+)$/.exec(window.location.hash);
  if (hash) start = Number(hash[1]) - 1;
  else {
    try { start = Number(sessionStorage.getItem(positionKey) || 0); } catch (error) { start = 0; }
  }
  index = -1;
  show(Number.isFinite(start) ? start : 0);
})();
