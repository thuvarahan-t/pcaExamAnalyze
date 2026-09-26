(function () {
  "use strict";

  // Shared question-image viewer (exam paper + result review): zoom and pan only the image,
  // never the page. Usage: PcaZoom.create(boxElement) -> { reset() }.
  // Buttons in the corner, mouse drag, Ctrl/trackpad-pinch wheel, double click/tap and two-finger pinch.
  var MIN_SCALE = 1;
  var MAX_SCALE = 5;

  function createViewer(box) {
    var img = box.querySelector("img");
    var level = box.querySelector(".zoom-level");
    var state = { scale: 1, x: 0, y: 0 };
    var pointers = new Map();
    var pinch = null;
    var lastTap = 0;
    var levelTimer = null;

    function clamp() {
      var w = img.offsetWidth * state.scale;
      var h = img.offsetHeight * state.scale;
      var maxX = Math.max(0, (w - box.clientWidth) / 2);
      var maxY = Math.max(0, (h - box.clientHeight) / 2);
      state.x = Math.max(-maxX, Math.min(maxX, state.x));
      state.y = Math.max(-maxY, Math.min(maxY, state.y));
    }

    function render(animate, showLevel) {
      clamp();
      img.style.transition = animate ? "transform .18s ease" : "none";
      img.style.transform = "translate(" + state.x + "px," + state.y + "px) scale(" + state.scale + ")";
      box.classList.toggle("zoomed", state.scale > 1.001);
      if (level && showLevel) {
        level.textContent = Math.round(state.scale * 100) + "%";
        level.classList.add("show");
        window.clearTimeout(levelTimer);
        levelTimer = window.setTimeout(function () { level.classList.remove("show"); }, 900);
      }
    }

    /** Zoom to `scale`, keeping the point (px, py) — relative to the box centre — fixed. */
    function zoomTo(scale, px, py, animate) {
      var next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
      var ratio = next / state.scale;
      state.x = px - (px - state.x) * ratio;
      state.y = py - (py - state.y) * ratio;
      state.scale = next;
      if (next === 1) { state.x = 0; state.y = 0; }
      render(animate, true);
    }

    function localPoint(clientX, clientY) {
      var rect = box.getBoundingClientRect();
      return { x: clientX - rect.left - rect.width / 2, y: clientY - rect.top - rect.height / 2 };
    }

    box.querySelectorAll("[data-zoom]").forEach(function (button) {
      button.addEventListener("pointerdown", function (event) { event.stopPropagation(); });
      button.addEventListener("click", function (event) {
        event.stopPropagation();
        var action = button.dataset.zoom;
        if (action === "in") zoomTo(state.scale * 1.5, 0, 0, true);
        else if (action === "out") zoomTo(state.scale / 1.5, 0, 0, true);
        else zoomTo(1, 0, 0, true);
      });
    });

    box.addEventListener("wheel", function (event) {
      // Plain wheel keeps scrolling the page; Ctrl+wheel and trackpad pinch zoom the image.
      if (!event.ctrlKey) return;
      event.preventDefault();
      var p = localPoint(event.clientX, event.clientY);
      zoomTo(state.scale * Math.exp(-event.deltaY * 0.01), p.x, p.y, false);
    }, { passive: false });

    box.addEventListener("dblclick", function (event) {
      if (event.target.closest(".zoom-tools")) return;
      var p = localPoint(event.clientX, event.clientY);
      zoomTo(state.scale > 1.001 ? 1 : 2.5, p.x, p.y, true);
    });

    box.addEventListener("pointerdown", function (event) {
      if (event.target.closest(".zoom-tools")) return;
      box.setPointerCapture(event.pointerId);
      pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (event.pointerType === "touch" && pointers.size === 1) {
        var now = Date.now();
        if (now - lastTap < 280) {
          var p = localPoint(event.clientX, event.clientY);
          zoomTo(state.scale > 1.001 ? 1 : 2.5, p.x, p.y, true);
          lastTap = 0;
        } else {
          lastTap = now;
        }
      }
      if (pointers.size === 2) {
        var pts = Array.from(pointers.values());
        pinch = {
          distance: Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y) || 1,
          scale: state.scale
        };
      }
    });

    box.addEventListener("pointermove", function (event) {
      var last = pointers.get(event.pointerId);
      if (!last) return;
      var dx = event.clientX - last.x;
      var dy = event.clientY - last.y;
      pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (pointers.size === 2 && pinch) {
        var pts = Array.from(pointers.values());
        var distance = Math.hypot(pts[0].x - pts[1].x, pts[0].y - pts[1].y) || 1;
        var mid = localPoint((pts[0].x + pts[1].x) / 2, (pts[0].y + pts[1].y) / 2);
        // Half of each finger's movement pans the image along with the pinch.
        state.x += dx / 2;
        state.y += dy / 2;
        zoomTo(pinch.scale * distance / pinch.distance, mid.x, mid.y, false);
        return;
      }
      if (pointers.size !== 1) return;
      if (state.scale > 1.001) {
        state.x += dx;
        state.y += dy;
        render(false);
      } else if (event.pointerType === "touch") {
        // touch-action is none on the viewer, so scroll the page by hand when not zoomed.
        window.scrollBy(0, -dy);
      }
    });

    function release(event) {
      pointers.delete(event.pointerId);
      if (pointers.size < 2) pinch = null;
    }
    box.addEventListener("pointerup", release);
    box.addEventListener("pointercancel", release);
    img.addEventListener("load", function () { render(false); });
    window.addEventListener("resize", function () { render(false); });

    return { reset: function () { zoomTo(1, 0, 0, false); } };
  }

  window.PcaZoom = { create: createViewer };
})();
