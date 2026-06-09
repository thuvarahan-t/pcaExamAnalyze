/*
 * Cell editor popup for the teacher question map. Clicking a matrix cell opens
 * one modal to set the question's section + description and add/remove any number
 * of resources, then Save posts everything in a single request.
 *
 * Reads each cell's current data from its data-* attributes (see teacher/dashboard.html);
 * the section picker reuses the glass combobox from combo.js.
 */
(function () {
    "use strict";

    var modal = document.getElementById("cell-modal");
    if (!modal) return;

    var form = document.getElementById("cm-form");
    var title = document.getElementById("cm-title");
    var qnum = document.getElementById("cm-qnum");
    var marks = document.getElementById("cm-marks");
    var desc = document.getElementById("cm-desc");
    var refsWrap = document.getElementById("cm-refs");
    var tpl = document.getElementById("cm-ref-tpl");
    var clearBtn = document.getElementById("cm-clear");
    var saveBtn = form.querySelector('button[type="submit"]');
    var currentPaper = null;   // paper id of the cell being edited
    var currentCell = null;    // the matrix cell element being edited (patched in place on save)

    // ---- CSRF + tiny toast helpers (mirrors combo.js) ----
    function meta(name) {
        var el = document.querySelector('meta[name="' + name + '"]');
        return el ? el.getAttribute("content") : null;
    }
    function csrfHeaders(base) {
        var headers = base || {};
        var header = meta("_csrf_header"), token = meta("_csrf");
        if (header && token) headers[header] = token;
        return headers;
    }
    var toastTimer = null;
    function toast(msg, isErr) {
        var t = document.getElementById("cm-toast");
        if (!t) {
            t = document.createElement("div");
            t.id = "cm-toast";
            t.className = "cm-toast";
            document.body.appendChild(t);
        }
        t.textContent = msg;
        t.classList.toggle("err", !!isErr);
        // restart the entrance animation
        t.classList.remove("show");
        void t.offsetWidth;
        t.classList.add("show");
        if (toastTimer) clearTimeout(toastTimer);
        toastTimer = setTimeout(function () { t.classList.remove("show"); }, 2400);
    }

    // Clean glass confirm dialog (replaces the native browser confirm popup).
    // Returns a Promise<boolean> — true if the user confirms.
    function pcaConfirm(opts) {
        opts = opts || {};
        return new Promise(function (resolve) {
            var overlay = document.createElement("div");
            overlay.className = "confirm-overlay";
            overlay.innerHTML =
                '<div class="confirm-card glass" role="alertdialog" aria-modal="true">' +
                    '<div class="confirm-icon' + (opts.danger ? " danger" : "") + '">' +
                        '<i class="bi ' + (opts.icon || "bi-question-lg") + '"></i></div>' +
                    '<h3 class="confirm-title"></h3>' +
                    '<p class="confirm-msg"></p>' +
                    '<div class="confirm-actions">' +
                        '<button type="button" class="btn btn-ghost confirm-cancel"></button>' +
                        '<button type="button" class="btn ' + (opts.danger ? "btn-danger" : "btn-primary") + ' confirm-ok"></button>' +
                    '</div>' +
                '</div>';
            overlay.querySelector(".confirm-title").textContent = opts.title || "Are you sure?";
            overlay.querySelector(".confirm-msg").textContent = opts.message || "";
            overlay.querySelector(".confirm-cancel").textContent = opts.cancelText || "Cancel";
            overlay.querySelector(".confirm-ok").textContent = opts.confirmText || "Confirm";
            document.body.appendChild(overlay);
            document.body.style.overflow = "hidden";
            requestAnimationFrame(function () { overlay.classList.add("show"); });

            var okBtn = overlay.querySelector(".confirm-ok");
            function done(val) {
                overlay.classList.remove("show");
                document.removeEventListener("keydown", onKey);
                setTimeout(function () {
                    overlay.remove();
                    // Only release page scroll if no other modal/overlay is still open.
                    if (!document.querySelector(".modal-overlay:not([hidden]), .confirm-overlay")) {
                        document.body.style.overflow = "";
                    }
                    resolve(val);
                }, 180);
            }
            function onKey(e) {
                if (e.key === "Escape") { e.preventDefault(); done(false); }
                else if (e.key === "Enter") { e.preventDefault(); done(true); }
            }
            okBtn.addEventListener("click", function () { done(true); });
            overlay.querySelector(".confirm-cancel").addEventListener("click", function () { done(false); });
            overlay.addEventListener("click", function (e) { if (e.target === overlay) done(false); });
            document.addEventListener("keydown", onKey);
            setTimeout(function () { okBtn.focus(); }, 60);
        });
    }
    // Expose for reuse elsewhere on the page.
    window.pcaConfirm = pcaConfirm;
    window.pcaToast = toast;

    // Repaint a matrix cell from a server cell record (set state) or to the empty "Add" state.
    function applyCell(el, c) {
        if (!el) return;
        if (c) {
            el.dataset.sectionId = c.sectionId != null ? c.sectionId : "";
            el.dataset.section = c.section || "";
            el.dataset.description = c.description || "";
            el.dataset.marks = c.maxMarks != null ? c.maxMarks : "";
            if (c.position) el.dataset.qnum = c.position;
            el.dataset.refs = c.refsJson || "[]";
            el.classList.remove("empty");
            el.classList.add("set");
            el.style.setProperty("--c", c.color || "");
            el.innerHTML =
                '<span class="qcell-sec"></span>' +
                '<span class="qcell-meta"><i class="bi bi-link-45deg"></i> <span class="qcell-refn">0</span> refs</span>' +
                '<div class="qcell-bar"><i data-w="100"></i></div>' +
                '<span class="qcell-glare"></span>';
            el.querySelector(".qcell-sec").textContent = c.section || "";
            el.querySelector(".qcell-refn").textContent = c.refCount != null ? c.refCount : 0;
        } else {
            el.dataset.sectionId = "";
            el.dataset.section = "";
            el.dataset.description = "";
            el.dataset.refs = "[]";
            el.classList.remove("set");
            el.classList.add("empty");
            el.style.removeProperty("--c");
            el.innerHTML = '<i class="bi bi-plus-lg"></i> Add';
        }
    }

    var combo = modal.querySelector("[data-combo]");
    var comboValue = combo.querySelector(".combo-value");
    var comboLabel = combo.querySelector(".combo-label");
    var comboTrigger = combo.querySelector(".combo-trigger");

    function setSection(id, name) {
        combo.querySelectorAll(".combo-option").forEach(function (o) { o.classList.remove("selected"); });
        if (id) {
            comboValue.value = id;
            var opt = combo.querySelector('.combo-option[data-value="' + id + '"]');
            comboLabel.textContent = opt ? opt.textContent : (name || "");
            if (opt) opt.classList.add("selected");
            comboTrigger.classList.remove("is-placeholder");
        } else {
            comboValue.value = "";
            comboLabel.textContent = "Choose a section…";
            comboTrigger.classList.add("is-placeholder");
        }
        combo.classList.remove("open");
    }

    function addRefRow(r) {
        var node = tpl.content.firstElementChild.cloneNode(true);
        if (r) {
            node.querySelector('[name="refTitle"]').value = r.title || "";
            node.querySelector('[name="refResource"]').value = r.resource || "";
            node.querySelector('[name="refNote"]').value = r.note || "";
        }
        // Initialise the glass type dropdown (defaults to the first type, Files).
        if (window.setRType) window.setRType(node, (r && r.type) || "");
        node.querySelector(".cm-ref-del").addEventListener("click", function () { node.remove(); });
        refsWrap.appendChild(node);
    }

    function open(d) {
        title.textContent = d.year + " · Structure " + d.pos;
        currentPaper = d.paper;
        form.action = "/teacher/papers/" + d.paper + "/questions/save";
        qnum.value = d.qnum || ("Q" + d.pos);
        marks.value = d.marks || 20;
        desc.value = d.description || "";
        setSection(d.sectionId || "", d.section || "");

        // "Clear details" only makes sense for a cell that already holds a question.
        if (clearBtn) clearBtn.hidden = !d.sectionId;

        refsWrap.innerHTML = "";
        var refs = [];
        try { refs = JSON.parse(d.refs || "[]"); } catch (e) { refs = []; }
        if (refs.length) { refs.forEach(addRefRow); } else { addRefRow(); }

        modal.hidden = false;
        document.body.style.overflow = "hidden";
    }

    function close() {
        modal.hidden = true;
        document.body.style.overflow = "";
    }

    document.querySelectorAll(".qcell-open").forEach(function (cell) {
        cell.addEventListener("click", function (e) { e.preventDefault(); currentCell = cell; open(cell.dataset); });
    });
    document.getElementById("cm-add-ref").addEventListener("click", function () { addRefRow(); });

    // Delete an entire year/paper from the matrix (with a confirm popup), removed in place.
    document.querySelectorAll(".qmap-del").forEach(function (btn) {
        btn.addEventListener("click", function (e) {
            e.preventDefault();
            e.stopPropagation();
            var paper = btn.dataset.paper, year = btn.dataset.year;
            if (!paper) return;
            pcaConfirm({
                danger: true,
                icon: "bi-trash3",
                title: "Delete " + year + "?",
                message: "This permanently removes the " + year + " paper, all its questions, resources and any student marks for it. This can’t be undone.",
                confirmText: "Delete year",
                cancelText: "Keep it"
            }).then(function (ok) {
                if (!ok) return;
                var row = btn.closest(".qmap-row");
                btn.disabled = true;
                fetch("/teacher/papers/" + paper + "/delete", {
                    method: "POST",
                    headers: csrfHeaders({ "X-Requested-With": "XMLHttpRequest" })
                }).then(function (r) { return r.json(); })
                  .then(function (res) {
                      if (!res.ok) { toast(res.message || "Could not delete the year.", true); btn.disabled = false; return; }
                      if (row) {
                          row.style.transition = "opacity .2s ease, transform .2s ease";
                          row.style.opacity = "0";
                          row.style.transform = "translateX(-14px)";
                          setTimeout(function () {
                              row.remove();
                              // No years left → reload so the empty / add-year state shows.
                              if (!document.querySelector(".qmap-row")) location.reload();
                          }, 200);
                      }
                      toast(res.message || "Year deleted.");
                  }).catch(function () {
                      toast("Could not delete the year. Check your connection.", true);
                      btn.disabled = false;
                  });
            });
        });
    });

    // Clear details — wipe the whole cell (delete the question + its resources/marks). AJAX, patched in place.
    if (clearBtn) {
        clearBtn.addEventListener("click", function () {
            if (!currentPaper) return;
            var cellEl = currentCell, paperId = currentPaper, q = qnum.value;
            pcaConfirm({
                danger: true,
                icon: "bi-eraser",
                title: "Clear " + q + "?",
                message: "This removes the section, focus points and resources for this question.",
                confirmText: "Clear details",
                cancelText: "Cancel"
            }).then(function (ok) {
                if (!ok) return;
                clearBtn.disabled = true;
                fetch("/teacher/papers/" + paperId + "/questions/clear", {
                    method: "POST",
                    headers: csrfHeaders({ "Content-Type": "application/x-www-form-urlencoded", "X-Requested-With": "XMLHttpRequest" }),
                    body: "questionNumber=" + encodeURIComponent(q)
                }).then(function (r) { return r.json(); })
                  .then(function (res) {
                      if (!res.ok) { toast(res.message || "Could not clear the cell.", true); return; }
                      applyCell(cellEl, null);
                      close();
                      toast(res.message || "Cleared.");
                  }).catch(function () {
                      toast("Could not clear the cell. Check your connection.", true);
                  }).finally(function () { clearBtn.disabled = false; });
            });
        });
    }

    modal.querySelectorAll("[data-close]").forEach(function (b) { b.addEventListener("click", close); });
    modal.addEventListener("click", function (e) { if (e.target === modal) close(); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && !modal.hidden) close(); });

    // Save via AJAX and patch the cell in place — no full-page reload, so it feels instant.
    form.addEventListener("submit", function (e) {
        e.preventDefault();
        if (!comboValue.value) {
            combo.classList.add("open");
            toast("Please choose a section first.", true);
            return;
        }
        var cellEl = currentCell;
        saveBtn.disabled = true;
        saveBtn.classList.add("is-loading");
        fetch(form.action, {
            method: "POST",
            headers: csrfHeaders({ "X-Requested-With": "XMLHttpRequest" }),
            body: new FormData(form)
        }).then(function (r) { return r.json(); })
          .then(function (res) {
              if (!res.ok) { toast(res.message || "Could not save.", true); return; }
              applyCell(cellEl, res.cell);
              close();
              toast(res.message || "Saved.");
          }).catch(function () {
              toast("Could not save. Check your connection.", true);
          }).finally(function () {
              saveBtn.disabled = false;
              saveBtn.classList.remove("is-loading");
          });
    });
})();
