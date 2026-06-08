/*
 * Glass combobox: a searchable section picker that can also create a new
 * section on the fly (POSTs to /teacher/sections and injects it into every
 * combobox on the page). Progressive — needs no framework.
 *
 * Markup contract (see teacher/questions.html):
 *   <div class="combo" data-combo>
 *     <input type="hidden" class="combo-value" name="sectionId" value="" required>
 *     <button type="button" class="combo-trigger input is-placeholder">
 *       <span class="combo-label">Choose a section…</span><i class="bi bi-chevron-down"></i>
 *     </button>
 *     <div class="combo-panel">
 *       <input type="text" class="combo-search input" placeholder="Search or add a section…">
 *       <div class="combo-options">
 *         <button type="button" class="combo-option" data-value="1">Mechanics</button> …
 *       </div>
 *       <div class="combo-empty" hidden>No matches.</div>
 *       <button type="button" class="combo-add"><i class="bi bi-plus-circle"></i>
 *         Add &ldquo;<span class="combo-add-name"></span>&rdquo;</button>
 *     </div>
 *   </div>
 */
(function () {
    "use strict";

    var PLACEHOLDER = "Choose a section…";

    function meta(name) {
        var el = document.querySelector('meta[name="' + name + '"]');
        return el ? el.getAttribute("content") : null;
    }

    function allCombos() {
        return Array.prototype.slice.call(document.querySelectorAll("[data-combo]"));
    }

    function closeAll(except) {
        allCombos().forEach(function (c) {
            if (c !== except) c.classList.remove("open");
        });
    }

    function makeOption(id, name) {
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "combo-option";
        btn.setAttribute("data-value", id);
        btn.textContent = name;
        return btn;
    }

    /** Add a freshly-created section to every combobox so all dropdowns stay in sync. */
    function injectEverywhere(id, name) {
        allCombos().forEach(function (combo) {
            var opts = combo.querySelector(".combo-options");
            if (!opts) return;
            var exists = opts.querySelector('.combo-option[data-value="' + id + '"]');
            if (!exists) opts.appendChild(makeOption(id, name));
        });
    }

    function initCombo(combo) {
        var value = combo.querySelector(".combo-value");
        var trigger = combo.querySelector(".combo-trigger");
        var label = combo.querySelector(".combo-label");
        var search = combo.querySelector(".combo-search");
        var optionsWrap = combo.querySelector(".combo-options");
        var emptyMsg = combo.querySelector(".combo-empty");
        var addBtn = combo.querySelector(".combo-add");
        var addName = combo.querySelector(".combo-add-name");

        function options() {
            return Array.prototype.slice.call(optionsWrap.querySelectorAll(".combo-option"));
        }

        function select(opt) {
            value.value = opt.getAttribute("data-value");
            label.textContent = opt.textContent;
            trigger.classList.remove("is-placeholder");
            options().forEach(function (o) { o.classList.remove("selected"); });
            opt.classList.add("selected");
            combo.classList.remove("open");
        }

        // Reflect any server-rendered preselection (edit forms).
        if (value.value) {
            var pre = optionsWrap.querySelector('.combo-option[data-value="' + value.value + '"]');
            if (pre) { label.textContent = pre.textContent; pre.classList.add("selected"); trigger.classList.remove("is-placeholder"); }
        }

        function open() {
            closeAll(combo);
            combo.classList.add("open");
            if (search) { search.value = ""; filter(); setTimeout(function () { search.focus(); }, 30); }
        }

        function filter() {
            var q = (search ? search.value : "").trim().toLowerCase();
            var visible = 0, exact = false;
            options().forEach(function (o) {
                var name = o.textContent.toLowerCase();
                var show = !q || name.indexOf(q) !== -1;
                o.hidden = !show;
                o.classList.remove("active");
                if (show) visible++;
                if (name === q) exact = true;
            });
            if (emptyMsg) emptyMsg.hidden = visible !== 0 || !!q;
            if (addBtn) {
                if (q && !exact) {
                    addBtn.classList.add("show");
                    if (addName) addName.textContent = search.value.trim();
                } else {
                    addBtn.classList.remove("show");
                }
            }
        }

        function activeOption() { return optionsWrap.querySelector(".combo-option.active"); }
        function moveActive(dir) {
            var vis = options().filter(function (o) { return !o.hidden; });
            if (!vis.length) return;
            var idx = vis.indexOf(activeOption());
            vis.forEach(function (o) { o.classList.remove("active"); });
            idx = (idx + dir + vis.length) % vis.length;
            vis[idx].classList.add("active");
            vis[idx].scrollIntoView({ block: "nearest" });
        }

        function createSection() {
            var name = search.value.trim();
            if (!name) return;
            addBtn.disabled = true;
            var header = meta("_csrf_header"), token = meta("_csrf");
            var headers = { "Content-Type": "application/x-www-form-urlencoded" };
            if (header && token) headers[header] = token;
            fetch("/teacher/sections", {
                method: "POST",
                headers: headers,
                body: "name=" + encodeURIComponent(name)
            }).then(function (r) {
                if (!r.ok) throw new Error("Failed to add section");
                return r.json();
            }).then(function (sec) {
                injectEverywhere(sec.id, sec.name);
                var opt = optionsWrap.querySelector('.combo-option[data-value="' + sec.id + '"]');
                if (opt) select(opt);
            }).catch(function () {
                alert("Could not add the section. Please try again.");
            }).finally(function () {
                addBtn.disabled = false;
            });
        }

        trigger.addEventListener("click", function (e) {
            e.preventDefault();
            combo.classList.contains("open") ? combo.classList.remove("open") : open();
        });

        optionsWrap.addEventListener("click", function (e) {
            var opt = e.target.closest(".combo-option");
            if (opt) select(opt);
        });

        if (search) search.addEventListener("input", filter);
        if (addBtn) addBtn.addEventListener("click", createSection);

        if (search) search.addEventListener("keydown", function (e) {
            if (e.key === "ArrowDown") { e.preventDefault(); moveActive(1); }
            else if (e.key === "ArrowUp") { e.preventDefault(); moveActive(-1); }
            else if (e.key === "Enter") {
                e.preventDefault();
                var act = activeOption();
                if (act && !act.hidden) select(act);
                else if (addBtn && addBtn.classList.contains("show")) createSection();
            } else if (e.key === "Escape") {
                combo.classList.remove("open");
                trigger.focus();
            }
        });
    }

    document.addEventListener("click", function (e) {
        if (!e.target.closest("[data-combo]")) closeAll(null);
    });

    document.addEventListener("DOMContentLoaded", function () {
        allCombos().forEach(initCombo);
    });
})();
