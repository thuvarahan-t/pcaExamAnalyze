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
    var box = item.querySelector(".step-viewer");
    return box && window.PcaZoom ? window.PcaZoom.create(box) : null;
  });
  var index = -1;

  function loadImage(i) {
    var img = items[i] && items[i].querySelector("img[data-src]");
    if (img && !img.getAttribute("src")) img.src = img.dataset.src;
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
    loadImage(index);
    loadImage(index + 1);
    prev.disabled = index === 0;
    next.disabled = index === items.length - 1;
    if (scroll && root.getBoundingClientRect().top < 0) root.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  prev.addEventListener("click", function () { show(index - 1, true); });
  next.addEventListener("click", function () { show(index + 1, true); });
  palette.forEach(function (button, k) { button.addEventListener("click", function () { show(k, true); }); });
  root.addEventListener("keydown", function (event) {
    if (event.key === "ArrowRight") { event.preventDefault(); show(index + 1); }
    if (event.key === "ArrowLeft") { event.preventDefault(); show(index - 1); }
  });

  show(0);
})();
