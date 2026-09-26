(function () {
  "use strict";

  // Instant feedback for normal form posts: a form with data-busy="Saving…" shows a spinner on
  // the button that was pressed and blocks double submits while the next page loads.
  document.addEventListener("submit", function (event) {
    var form = event.target;
    if (!form.matches || !form.matches("form[data-busy]")) return;
    var button = event.submitter || form.querySelector("[type=submit]");
    // Run after every other submit handler, and only if the submit really goes ahead.
    window.setTimeout(function () {
      if (event.defaultPrevented) return;
      form.querySelectorAll("[type=submit]").forEach(function (b) {
        if (!b.dataset.idleHtml) b.dataset.idleHtml = b.innerHTML;
        b.disabled = true;
      });
      if (button) button.innerHTML = '<i class="bi bi-arrow-repeat busy-spin"></i> ' + form.dataset.busy;
    }, 0);
  });

  // Coming back with the browser's Back button: make the buttons usable again.
  window.addEventListener("pageshow", function () {
    document.querySelectorAll("form[data-busy] [type=submit]").forEach(function (b) {
      if (b.dataset.idleHtml) b.innerHTML = b.dataset.idleHtml;
      b.disabled = false;
    });
  });
})();
