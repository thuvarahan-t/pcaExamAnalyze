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
