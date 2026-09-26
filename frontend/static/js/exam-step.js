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
  // ---------------------------------------------------------------- image loading
  // Every question is already in the page; images use direct Cloudflare links signed by the
  // server (data-src) and fall back to the app endpoint (data-fallback) if a link fails.
  function startImage(img) {
    if (!img || img.getAttribute("src")) return null;
    return new Promise(function (resolve) {
      img.addEventListener("load", function () { resolve(); }, { once: true });
      img.addEventListener("error", function () {
        if (img.dataset.fallback && img.getAttribute("src") !== img.dataset.fallback) {
          img.src = img.dataset.fallback;
          img.addEventListener("load", function () { resolve(); }, { once: true });
          img.addEventListener("error", function () { resolve(); }, { once: true });
        } else {
          resolve();
        }
      }, { once: true });
      img.src = img.dataset.src || img.dataset.fallback;
    });
  }

  function questionImages(i) {
    var question = questions[i];
    return question ? Array.from(question.querySelectorAll("img[data-src], img[data-fallback]")) : [];
  }

  function loadImage(i) {
    questionImages(i).forEach(startImage);
  }

  /** Warms every question's images in the background (4 at a time), nearest questions first. */
  function preloadAll(from) {
    var order = [];
    for (var d = 0; d < total; d++) {
      if (from + d < total) order.push(from + d);
      if (d && from - d >= 0) order.push(from - d);
    }
    var queue = [];
    order.forEach(function (i) { questionImages(i).forEach(function (img) { queue.push(img); }); });
    var running = 0;
    function pump() {
      while (running < 4 && queue.length) {
        var job = startImage(queue.shift());
        if (!job) continue;
        running++;
        job.then(function () { running--; pump(); });
      }
    }
    pump();
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
    // On the last question "Next" becomes "Submit Paper" (it asks for confirmation first).
    var last = index === total - 1;
    next.disabled = false;
    next.classList.toggle("submit", last);
    next.innerHTML = last ? '<span>Submit Paper</span> <i class="bi bi-send-check-fill"></i>'
      : '<span>Next</span> <i class="bi bi-chevron-right"></i>';
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
  next.addEventListener("click", function () {
    if (index < total - 1) { show(index + 1); return; }
    var paper = document.getElementById("submitForm");
    if (!paper) return;
    if (typeof paper.requestSubmit === "function") paper.requestSubmit();
    else paper.dispatchEvent(new Event("submit", { cancelable: true }));
  });
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

  // ---------------------------------------------------------------- supporting images
  var lightbox = document.getElementById("subLightbox");
  if (lightbox) {
    var lightImg = lightbox.querySelector("img");
    var lightTitle = document.getElementById("subLightboxTitle");
    var lightViewer = window.PcaZoom.create(lightbox.querySelector(".step-viewer"));
    var lightList = [];
    var lightIndex = 0;

    var showSub = function (k) {
      lightIndex = (k + lightList.length) % lightList.length;
      var source = lightList[lightIndex];
      lightViewer.reset();
      lightImg.src = source.currentSrc || source.src || source.dataset.src || source.dataset.fallback;
      lightTitle.textContent = "Supporting image " + (lightIndex + 1) + " of " + lightList.length;
      document.getElementById("subPrev").hidden = lightList.length < 2;
      document.getElementById("subNext").hidden = lightList.length < 2;
    };

    sheet.addEventListener("click", function (event) {
      var button = event.target.closest(".step-sub");
      if (!button) return;
      lightList = Array.from(button.closest(".step-subs").querySelectorAll("img"));
      lightList.forEach(startImage);
      lightbox.showModal();
      showSub(Number(button.dataset.subIndex) || 0);
    });
    document.getElementById("subPrev").addEventListener("click", function () { showSub(lightIndex - 1); });
    document.getElementById("subNext").addEventListener("click", function () { showSub(lightIndex + 1); });
    document.getElementById("subClose").addEventListener("click", function () { lightbox.close(); });
    lightbox.addEventListener("click", function (event) { if (event.target === lightbox) lightbox.close(); });
    lightbox.addEventListener("keydown", function (event) {
      if (event.key === "ArrowRight") showSub(lightIndex + 1);
      if (event.key === "ArrowLeft") showSub(lightIndex - 1);
    });
  }

  var start = 0;
  var hash = /^#q(\d+)$/.exec(window.location.hash);
  if (hash) start = Number(hash[1]) - 1;
  else {
    try { start = Number(sessionStorage.getItem(positionKey) || 0); } catch (error) { start = 0; }
  }
  index = -1;
  show(Number.isFinite(start) ? start : 0);
  // Once the first question is on screen, fetch the rest so every "Next" is instant.
  var warm = function () { preloadAll(Math.max(0, index)); };
  if ("requestIdleCallback" in window) window.requestIdleCallback(warm, { timeout: 800 });
  else window.setTimeout(warm, 300);
})();
