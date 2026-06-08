/*
 * 3D tilt + glare for the teacher question-map cells. Each filled cell tilts
 * toward the cursor and a soft glare follows the pointer; the accent bars sweep
 * to their width once on load. Purely decorative — clicking still opens the
 * cell editor (cell-modal.js), which only listens for click events.
 */
(function () {
    "use strict";

    var MAX_TILT = 12; // degrees

    document.querySelectorAll(".qmap-cell.set").forEach(function (cell) {
        var glare = cell.querySelector(".qcell-glare");

        cell.addEventListener("pointermove", function (e) {
            var r = cell.getBoundingClientRect();
            var px = (e.clientX - r.left) / r.width;
            var py = (e.clientY - r.top) / r.height;
            var ry = (px - 0.5) * 2 * MAX_TILT;
            var rx = (0.5 - py) * 2 * MAX_TILT;
            cell.style.transform =
                "rotateX(" + rx + "deg) rotateY(" + ry + "deg) translateZ(6px) scale(1.03)";
            if (glare) {
                glare.style.setProperty("--gx", px * 100 + "%");
                glare.style.setProperty("--gy", py * 100 + "%");
            }
        });

        cell.addEventListener("pointerleave", function () {
            cell.style.transform = "";
        });
    });

    // Sweep the accent bars in after first paint.
    requestAnimationFrame(function () {
        setTimeout(function () {
            document.querySelectorAll(".qcell-bar > i").forEach(function (bar) {
                bar.style.width = (bar.dataset.w || "100") + "%";
            });
        }, 350);
    });
})();
