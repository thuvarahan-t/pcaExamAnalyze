(function () {
  "use strict";

  // Result page: question-by-question review of an image-sheet paper (image + answers).
  var root = document.getElementById("questionReview");
  if (!root) return;

  var items = Array.from(root.querySelectorAll(".q-review-item"));
  var palette = Array.from(root.querySelectorAll(".q-review-palette-grid [data-go]"));
  var prev = document.getElementById("reviewPrev");
  var next = document.getElementById("reviewNext");
  var viewers = items.map(function (item) {
    var box = item.querySelector(".q-review-viewer");
    return box && window.PcaZoom ? window.PcaZoom.create(box) : null;
  });
  var index = -1;

  // Direct Cloudflare links first (data-src), the app endpoint as a fallback (data-fallback).
  function startImage(img) {
    if (!img || img.getAttribute("src")) return null;
    return new Promise(function (resolve) {
      img.addEventListener("load", function () { resolve(); }, { once: true });
      img.addEventListener("error", function () {
        if (img.dataset.fallback && img.getAttribute("src") !== img.dataset.fallback) {
          img.addEventListener("load", function () { resolve(); }, { once: true });
          img.addEventListener("error", function () { resolve(); }, { once: true });
          img.src = img.dataset.fallback;
        } else {
          resolve();
        }
      }, { once: true });
      img.src = img.dataset.src || img.dataset.fallback;
    });
  }

  function imagesOf(i) {
    return items[i] ? Array.from(items[i].querySelectorAll("img[data-src], img[data-fallback]")) : [];
  }

  function preloadAll(from) {
    var queue = [];
    for (var d = 0; d < items.length; d++) {
      if (from + d < items.length) imagesOf(from + d).forEach(function (img) { queue.push(img); });
      if (d && from - d >= 0) imagesOf(from - d).forEach(function (img) { queue.push(img); });
    }
    var running = 0;
    (function pump() {
      while (running < 4 && queue.length) {
        var job = startImage(queue.shift());
        if (!job) continue;
        running++;
        job.then(function () { running--; pump(); });
      }
    })();
  }

  function show(i, scroll) {
    var target = Math.max(0, Math.min(items.length - 1, i));
    if (target === index) return;
    if (index >= 0 && viewers[index]) viewers[index].reset();
    index = target;
    items.forEach(function (item, k) { item.hidden = k !== index; });
    palette.forEach(function (button, k) {
      button.classList.toggle("current", k === index);
      if (k === index) button.setAttribute("aria-current", "step"); else button.removeAttribute("aria-current");
    });
    imagesOf(index).forEach(startImage);
    prev.disabled = index === 0;
    next.disabled = index === items.length - 1;
    if (scroll && root.getBoundingClientRect().top < 0) root.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  prev.addEventListener("click", function () { show(index - 1, true); });
  next.addEventListener("click", function () { show(index + 1, true); });
  palette.forEach(function (button, k) { button.addEventListener("click", function () { show(k, true); }); });
  root.addEventListener("keydown", function (event) {
    if (event.target.closest("dialog")) return;
    if (event.key === "ArrowRight") { event.preventDefault(); show(index + 1); }
    if (event.key === "ArrowLeft") { event.preventDefault(); show(index - 1); }
  });

  // Supporting images open in a zoomable viewer.
  var lightbox = document.getElementById("subLightbox");
  if (lightbox && window.PcaZoom) {
    var lightImg = lightbox.querySelector("img");
    var title = document.getElementById("subLightboxTitle");
    var viewer = window.PcaZoom.create(lightbox.querySelector(".step-viewer"));
    var list = [];
    var at = 0;
    var showSub = function (k) {
      at = (k + list.length) % list.length;
      var source = list[at];
      viewer.reset();
      lightImg.src = source.currentSrc || source.src || source.dataset.src || source.dataset.fallback;
      title.textContent = "Supporting image " + (at + 1) + " of " + list.length;
      document.getElementById("subPrev").hidden = list.length < 2;
      document.getElementById("subNext").hidden = list.length < 2;
    };
    root.addEventListener("click", function (event) {
      var button = event.target.closest(".step-sub");
      if (!button) return;
      list = Array.from(button.closest(".step-subs").querySelectorAll("img"));
      list.forEach(startImage);
      lightbox.showModal();
      showSub(Number(button.dataset.subIndex) || 0);
    });
    document.getElementById("subPrev").addEventListener("click", function () { showSub(at - 1); });
    document.getElementById("subNext").addEventListener("click", function () { showSub(at + 1); });
    document.getElementById("subClose").addEventListener("click", function () { lightbox.close(); });
    lightbox.addEventListener("click", function (event) { if (event.target === lightbox) lightbox.close(); });
    lightbox.addEventListener("keydown", function (event) {
      if (event.key === "ArrowRight") showSub(at + 1);
      if (event.key === "ArrowLeft") showSub(at - 1);
    });
  }

  show(0);
  var warm = function () { preloadAll(0); };
  if ("requestIdleCallback" in window) window.requestIdleCallback(warm, { timeout: 800 });
  else window.setTimeout(warm, 300);
})();
