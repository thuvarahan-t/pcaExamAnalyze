(function () {
  "use strict";
  var countdown = document.getElementById("examStartCountdown");
  if (!countdown) return;
  var output = countdown.querySelector("[data-countdown-value]");
  var startAt = Number(countdown.getAttribute("data-starts-at"));
  var form = document.querySelector('form[action$="/start"]');
  var started = false;

  function pad(value) { return String(value).padStart(2, "0"); }
  function tick() {
    var remaining = Math.max(0, Math.ceil((startAt - Date.now()) / 1000));
    var hours = Math.floor(remaining / 3600);
    var minutes = Math.floor((remaining % 3600) / 60);
    var seconds = remaining % 60;
    output.textContent = pad(hours) + ":" + pad(minutes) + ":" + pad(seconds);
    if (remaining > 0 || started) return;
    started = true;
    output.textContent = "STARTING…";
    if (form) form.requestSubmit(); else window.location.reload();
  }

  tick();
  window.setInterval(tick, 1000);
})();
