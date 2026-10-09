/*
 * Countdown to the exam's start time.
 *
 * Accuracy: the remaining time is measured against the SERVER clock, not the device clock.
 * A clock offset is taken from /exam/time (round-trip compensated, best sample wins) and
 * refreshed every 30 s and whenever the tab becomes visible again, so a wrong phone clock,
 * a throttled background tab or a long-open page cannot drift the timer.
 * The display ticks exactly on second boundaries (no setInterval drift) and, when it reaches
 * zero, opens the paper automatically.
 */
(function () {
  "use strict";
  var root = document.getElementById("examStartCountdown");
  if (!root) return;

  var startAt = Number(root.getAttribute("data-starts-at"));
  var form = document.getElementById("examStartForm") || document.querySelector('form[action$="/start"]');
  var note = root.querySelector("[data-cd-note]");
  var progress = root.querySelector("[data-cd-bar]");
  var units = {};
  ["d", "h", "m", "s"].forEach(function (u) {
    var box = root.querySelector('[data-unit="' + u + '"]');
    units[u] = { box: box, value: box.querySelector("[data-value]"), last: null };
  });

  // Server clock offset (server time - device time). The page already carries the server's
  // time at render; /exam/time then refines it.
  var offset = Number(root.getAttribute("data-server-now")) - Date.now();
  if (!isFinite(offset)) offset = 0;
  var bestRtt = Infinity;

  function sync() {
    var t0 = performance.now();
    fetch("/exam/time", { cache: "no-store", credentials: "same-origin" })
      .then(function (r) { return r.json(); })
      .then(function (json) {
        var rtt = performance.now() - t0;
        // The server stamped "now" roughly half a round trip before we received it.
        // Prefer low-latency samples; still accept a worse one occasionally so the
        // offset can follow a real clock change.
        if (rtt <= bestRtt + 120 || rtt < 400) {
          offset = Number(json.now) + rtt / 2 - (Date.now());
          bestRtt = Math.min(bestRtt, rtt);
        }
        schedule();
      })
      .catch(function () { /* keep the current offset */ });
  }

  function now() { return Date.now() + offset; }
  function pad(n) { return String(n).padStart(2, "0"); }

  var totalMs = Math.max(1, startAt - now());   // for the progress bar (full at first paint)
  var fired = false;
  var timer = 0;

  function setUnit(key, text) {
    var u = units[key];
    if (u.last === text) return;
    u.last = text;
    u.value.textContent = text;
    if (key === "s" && u.box) {
      u.box.classList.remove("tick");
      void u.box.offsetWidth;            // restart the animation
      u.box.classList.add("tick");
    }
  }

  function render() {
    var remainingMs = startAt - now();
    var remaining = Math.max(0, Math.ceil(remainingMs / 1000));
    var days = Math.floor(remaining / 86400);
    var hours = Math.floor((remaining % 86400) / 3600);
    var minutes = Math.floor((remaining % 3600) / 60);
    var seconds = remaining % 60;

    units.d.box.hidden = days === 0;
    setUnit("d", pad(days));
    setUnit("h", pad(hours));
    setUnit("m", pad(minutes));
    setUnit("s", pad(seconds));

    root.classList.toggle("soon", remaining <= 60 && remaining > 10);
    root.classList.toggle("now", remaining <= 10 && remaining > 0);
    if (progress) progress.style.width = Math.min(100, Math.max(0, (1 - remainingMs / totalMs) * 100)).toFixed(2) + "%";

    if (remaining > 0 || fired) return remainingMs;
    fired = true;
    root.classList.add("done");
    if (note) note.innerHTML = '<i class="bi bi-rocket-takeoff-fill"></i> Starting your paper…';
    // Open the paper once. If the server still refuses (not eligible, clock edge), do not loop:
    // the guard stops a second automatic attempt within 30 s and asks for a manual refresh.
    var guardKey = "pca_autostart_" + startAt, last = 0;
    try { last = Number(sessionStorage.getItem(guardKey)) || 0; } catch (e) { /* storage blocked */ }
    if (Date.now() - last < 30000) {
      if (note) note.innerHTML = '<i class="bi bi-arrow-clockwise"></i> Could not open automatically. <a href="" onclick="location.reload();return false;">Refresh</a> and press Start.';
      return remainingMs;
    }
    try { sessionStorage.setItem(guardKey, String(Date.now())); } catch (e) { /* storage blocked */ }
    // A short grace so the server clock is certainly past the start time.
    window.setTimeout(function () {
      if (form) form.requestSubmit(); else window.location.reload();
    }, 700);
    return remainingMs;
  }

  function schedule() {
    window.clearTimeout(timer);
    var remainingMs = render();
    if (fired) return;
    // Wake just after the next whole-second boundary of the remaining time.
    var wait = remainingMs % 1000;
    if (wait <= 0) wait += 1000;
    timer = window.setTimeout(schedule, wait + 8);
  }

  document.addEventListener("visibilitychange", function () {
    if (!document.hidden) { sync(); schedule(); }
  });
  window.addEventListener("pageshow", function (e) { if (e.persisted) { fired = false; sync(); schedule(); } });

  schedule();
  sync();
  window.setInterval(sync, 30000);
})();
