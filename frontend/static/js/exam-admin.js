(function () {
    "use strict";

    var answerInputs = Array.from(document.querySelectorAll(".answer-option input"));
    var answerRows = Array.from(document.querySelectorAll(".answer-row"));
    var totalInput = document.getElementById("totalQuestions");
    var count = document.getElementById("key-count");
    var message = document.getElementById("key-message");

    function totalQuestions() {
        if (!totalInput) return 50;
        var value = Number.parseInt(totalInput.value, 10);
        if (Number.isNaN(value)) return 0;
        return Math.max(0, Math.min(50, value));
    }

    function selectedQuestions() {
        var selected = new Set();
        answerInputs.forEach(function (input) {
            if (input.checked && Number(input.getAttribute("data-question")) <= totalQuestions()) {
                selected.add(input.getAttribute("data-question"));
            }
        });
        return selected;
    }

    function updateCount() {
        if (count) count.textContent = selectedQuestions().size + " / " + totalQuestions() + " set";
        if (message) message.className = "key-message";
    }

    function updateQuestionRows() {
        var total = totalQuestions();
        answerRows.forEach(function (row, index) {
            var active = index < total;
            row.hidden = false;
            row.classList.toggle("unavailable", !active);
            row.setAttribute("aria-disabled", active ? "false" : "true");
            row.querySelectorAll("input").forEach(function (input) { input.disabled = !active; });
        });
        var sheet = document.querySelector(".answer-sheet");
        if (sheet) sheet.setAttribute("data-visible-questions", String(total));
        updateCount();
    }

    answerInputs.forEach(function (input) { input.addEventListener("change", updateCount); });
    if (totalInput) {
        totalInput.addEventListener("input", updateQuestionRows);
        totalInput.addEventListener("change", updateQuestionRows);
    }

    var clear = document.getElementById("clear-key");
    if (clear) clear.addEventListener("click", function () {
        window.PcaDialog.confirm("Every selected answer in the current answer key will be cleared.", {
            title: "Clear answer key?", acceptText: "Clear All", type: "danger"
        }).then(function (approved) {
            if (!approved) return;
            answerInputs.forEach(function (input) { input.checked = false; });
            updateCount();
        });
    });

    var validate = document.getElementById("validate-key");
    if (validate) validate.addEventListener("click", function () {
        var selected = selectedQuestions();
        var missing = [];
        var total = totalQuestions();
        for (var q = 1; q <= total; q++) if (!selected.has(String(q))) missing.push(q);
        if (!message) return;
        if (!missing.length) {
            message.textContent = "Answer key complete - all " + total + " answers are ready for result publishing.";
            message.className = "key-message show good";
        } else {
            message.textContent = missing.length + " answers missing: " + missing.slice(0, 10).map(function (q) { return "Q" + q; }).join(", ") + (missing.length > 10 ? "..." : "");
            message.className = "key-message show bad";
        }
    });

    document.querySelectorAll(".batch-rename-form").forEach(function (form) {
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            window.PcaDialog.prompt("Enter a new name for this exam batch.", form.getAttribute("data-current-name") || "", {
                title: "Rename batch", acceptText: "Save Name"
            }).then(function (name) {
                if (!name) return;
                form.querySelector('input[name="name"]').value = name;
                form.submit();
            });
        });
    });

    document.querySelectorAll(".admin-modal-overlay").forEach(function (overlay) {
        overlay.addEventListener("click", function (event) {
            if (event.target !== overlay) return;
            var close = overlay.querySelector(".popup-close");
            window.location.href = close ? close.href : "/exam/admin";
        });
    });
    document.addEventListener("keydown", function (event) {
        if (event.key !== "Escape") return;
        var overlay = document.querySelector(".admin-modal-overlay");
        if (!overlay) return;
        var close = overlay.querySelector(".popup-close");
        window.location.href = close ? close.href : "/exam/admin";
    });

    document.querySelectorAll("[data-copy-path]").forEach(function (button) {
        button.addEventListener("click", function () {
            var link = window.location.origin + button.getAttribute("data-copy-path");
            var copied = function () {
                var original = button.textContent;
                button.textContent = "Copied!";
                window.setTimeout(function () { button.textContent = original; }, 1600);
            };
            if (navigator.clipboard && window.isSecureContext) {
                navigator.clipboard.writeText(link).then(copied);
                return;
            }
            var helper = document.createElement("textarea");
            helper.value = link;
            helper.style.position = "fixed";
            helper.style.opacity = "0";
            document.body.appendChild(helper);
            helper.select();
            document.execCommand("copy");
            helper.remove();
            copied();
        });
    });

    document.querySelectorAll("[data-row-href]").forEach(function (row) {
        row.addEventListener("click", function (event) {
            if (event.target.closest("a,button,form,input,select,textarea,label")) return;
            window.location.href = row.getAttribute("data-row-href");
        });
        row.addEventListener("keydown", function (event) {
            if (event.key === "Enter" && !event.target.closest("a,button,input,select,textarea")) {
                window.location.href = row.getAttribute("data-row-href");
            }
        });
        row.setAttribute("tabindex", "0");
    });

    var submissionExamBox = document.getElementById("submissionExamSelect");
    var submissionExam = document.getElementById("submissionExam");
    var submissionExamTrigger = document.getElementById("submissionExamTrigger");
    var submissionExamText = document.getElementById("submissionExamText");
    var submissionExamPanel = document.getElementById("submissionExamPanel");
    var examOptionSearch = document.getElementById("submissionExamSearch");
    if (submissionExamBox && submissionExam && submissionExamTrigger && submissionExamPanel) {
        var examOptions = Array.from(submissionExamPanel.querySelectorAll(".admin-gselect-option"));
        var examEmpty = submissionExamPanel.querySelector(".admin-gselect-empty");
        var submissionFilter = submissionExamBox.closest(".submission-filter");

        function reserveExamPanelSpace() {
            if (!submissionFilter || submissionExamPanel.hidden) return;
            submissionFilter.style.setProperty("--exam-dropdown-space", (submissionExamPanel.offsetHeight + 10) + "px");
            submissionFilter.classList.add("dropdown-expanded");
        }

        function setExam(value) {
            var selected = examOptions.find(function (option) {
                return option.getAttribute("data-value") === String(value || "");
            }) || examOptions[0];
            submissionExam.value = selected ? selected.getAttribute("data-value") : "";
            submissionExamText.textContent = selected ? selected.querySelector("span:nth-child(2)").textContent : "All exams";
            examOptions.forEach(function (option) {
                var active = option === selected;
                option.classList.toggle("active", active);
                option.setAttribute("aria-selected", active ? "true" : "false");
            });
        }

        function filterExams() {
            var needle = examOptionSearch.value.trim().toLowerCase();
            var visible = 0;
            examOptions.forEach(function (option) {
                var match = option.textContent.trim().toLowerCase().includes(needle);
                option.classList.toggle("is-hidden", !match);
                if (match) visible++;
            });
            if (examEmpty) examEmpty.hidden = visible !== 0;
        }

        function openExamSelect(open) {
            submissionExamBox.classList.toggle("open", open);
            submissionExamPanel.hidden = !open;
            submissionExamTrigger.setAttribute("aria-expanded", open ? "true" : "false");
            if (open) {
                examOptionSearch.value = "";
                filterExams();
                window.requestAnimationFrame(function () {
                    reserveExamPanelSpace();
                    examOptionSearch.focus();
                });
            } else if (submissionFilter) {
                submissionFilter.classList.remove("dropdown-expanded");
                submissionFilter.style.removeProperty("--exam-dropdown-space");
            }
        }

        submissionExamTrigger.addEventListener("click", function () { openExamSelect(submissionExamPanel.hidden); });
        examOptionSearch.addEventListener("input", filterExams);
        examOptions.forEach(function (option) {
            option.addEventListener("click", function () {
                setExam(option.getAttribute("data-value"));
                openExamSelect(false);
            });
        });
        document.addEventListener("click", function (event) {
            if (!submissionExamBox.contains(event.target)) openExamSelect(false);
        });
        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape") openExamSelect(false);
        });
        window.addEventListener("resize", reserveExamPanelSpace, { passive: true });
        setExam(submissionExam.value);
    }

    updateQuestionRows();
})();

// PDF exam report. The server builds it in the background; this shows live progress and downloads the file
// when it is ready, so a slow report can never time out or fail silently. The link's own href still works
// as a plain fallback (if JavaScript is blocked).
(function () {
    var MAX_WAIT_MS = 10 * 60 * 1000;

    function csrf() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        var headers = { "X-Requested-With": "XMLHttpRequest" };
        if (token) headers[header ? header.content : "X-CSRF-TOKEN"] = token.content;
        return headers;
    }

    function fail(message) {
        if (window.PcaDialog && window.PcaDialog.alert) {
            window.PcaDialog.alert(message, { title: "Report not created", type: "error" });
        } else {
            window.alert(message);
        }
    }

    document.addEventListener("click", function (event) {
        var link = event.target.closest && event.target.closest("a[data-report]");
        if (!link) return;
        var startUrl = link.getAttribute("data-report-start");
        if (!startUrl || !window.fetch) return; // fall back to the plain download link
        event.preventDefault();
        if (link.classList.contains("is-busy")) return;

        var label = link.querySelector("span");
        var icon = link.querySelector("i");
        var oldLabel = label ? label.textContent : "";
        var oldIcon = icon ? icon.className : "";
        var base = startUrl.replace(/\/exams\/\d+\/report\/start.*$/, "");

        function busy(text) {
            link.classList.add("is-busy");
            if (label) label.textContent = text;
            if (icon) icon.className = "bi bi-arrow-repeat busy-spin";
        }
        function idle(text, iconClass) {
            if (label) label.textContent = text || oldLabel;
            if (icon) icon.className = iconClass || oldIcon;
            if (text) {
                window.setTimeout(function () { link.classList.remove("is-busy"); if (label) label.textContent = oldLabel; if (icon) icon.className = oldIcon; }, 2500);
            } else {
                link.classList.remove("is-busy");
            }
        }

        busy("Starting…");
        var started = Date.now();
        fetch(startUrl, { method: "POST", headers: csrf(), credentials: "same-origin" })
            .then(function (response) {
                if (!response.ok) throw new Error(response.status === 403 || response.status === 401
                    ? "Your session has expired. Please sign in again." : "The server could not start the report (error " + response.status + ").");
                return response.json();
            })
            .then(function (data) { poll(data.job); })
            .catch(function (error) { idle(); fail(error.message || "The report could not be started."); });

        function poll(job) {
            fetch(base + "/reports/" + job + "/status", { credentials: "same-origin", cache: "no-store" })
                .then(function (response) { return response.json().catch(function () { return { state: "FAILED", error: "Unexpected server response." }; }); })
                .then(function (status) {
                    if (status.state === "DONE") {
                        var anchor = document.createElement("a");
                        anchor.href = base + "/reports/" + job + "/file";
                        anchor.download = "";
                        anchor.style.display = "none";
                        document.body.appendChild(anchor);
                        anchor.click();
                        window.setTimeout(function () { anchor.remove(); }, 1000);
                        idle("Downloaded", "bi bi-check2-circle");
                    } else if (status.state === "RUNNING") {
                        if (Date.now() - started > MAX_WAIT_MS) { idle(); fail("The report is taking too long. Please try again."); return; }
                        if (label) label.textContent = (status.stage || "Preparing") + "…";
                        window.setTimeout(function () { poll(job); }, 1200);
                    } else {
                        idle();
                        fail(status.error || "The report could not be created. Please try again.");
                    }
                })
                .catch(function () {
                    // A dropped request is not a failure of the job: keep waiting.
                    if (Date.now() - started > MAX_WAIT_MS) { idle(); fail("Lost connection to the server. Please try again."); return; }
                    window.setTimeout(function () { poll(job); }, 2000);
                });
        }
    });
})();
