/*
 * Glass combobox: a searchable section picker that can also create, rename and
 * delete sections on the fly. All edits POST to /teacher/sections/* and sync into
 * every combobox on the page so each dropdown stays consistent. Progressive — no framework.
 *
 * Each option is a row:
 *   <div class="combo-option" data-value="1">
 *     <span class="combo-label-text">Mechanics</span>
 *     <span class="combo-actions">
 *       <button class="combo-edit"><i class="bi bi-pencil"></i></button>
 *       <button class="combo-del"><i class="bi bi-trash"></i></button>
 *     </span>
 *   </div>
 * Clicking the row selects it; the two action buttons rename / delete the section.
 */
(function () {
    "use strict";

    var PLACEHOLDER = "Choose a section…";

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

    function allCombos() {
        return Array.prototype.slice.call(document.querySelectorAll("[data-combo]"));
    }

    function closeAll(except) {
        allCombos().forEach(function (c) {
            if (c !== except) c.classList.remove("open");
        });
    }

    function labelOf(row) {
        var span = row.querySelector(".combo-label-text");
        return span ? span.textContent : row.textContent;
    }

    function makeOption(id, name) {
        var row = document.createElement("div");
        row.className = "combo-option";
        row.setAttribute("data-value", id);
        var label = document.createElement("span");
        label.className = "combo-label-text";
        label.textContent = name;
        row.appendChild(label);
        var actions = document.createElement("span");
        actions.className = "combo-actions";
        actions.innerHTML =
            '<button type="button" class="combo-edit" title="Rename" aria-label="Rename section"><i class="bi bi-pencil"></i></button>' +
            '<button type="button" class="combo-del" title="Delete" aria-label="Delete section"><i class="bi bi-trash"></i></button>';
        row.appendChild(actions);
        return row;
    }

    /** Add a freshly-created section to every combobox so all dropdowns stay in sync. */
    function injectEverywhere(id, name) {
        allCombos().forEach(function (combo) {
            var opts = combo.querySelector(".combo-options");
            if (!opts) return;
            if (!opts.querySelector('.combo-option[data-value="' + id + '"]')) {
                opts.appendChild(makeOption(id, name));
            }
        });
    }

    /** Reflect a rename across every combobox (options + any trigger showing it). */
    function renameEverywhere(id, name) {
        allCombos().forEach(function (combo) {
            var row = combo.querySelector('.combo-option[data-value="' + id + '"]');
            if (row) { var l = row.querySelector(".combo-label-text"); if (l) l.textContent = name; }
            var value = combo.querySelector(".combo-value");
            if (value && value.value === String(id)) {
                var label = combo.querySelector(".combo-label");
                if (label) label.textContent = name;
            }
        });
    }

    /** Remove a deleted section everywhere; reset any combo that had it selected. */
    function removeEverywhere(id) {
        allCombos().forEach(function (combo) {
            var row = combo.querySelector('.combo-option[data-value="' + id + '"]');
            if (row) row.remove();
            var value = combo.querySelector(".combo-value");
            if (value && value.value === String(id)) {
                value.value = "";
                var label = combo.querySelector(".combo-label");
                if (label) label.textContent = PLACEHOLDER;
                var trigger = combo.querySelector(".combo-trigger");
                if (trigger) trigger.classList.add("is-placeholder");
            }
        });
    }

    function renameSection(id, current) {
        var next = window.prompt("Rename section", current);
        if (next === null) return;                 // cancelled
        next = next.trim();
        if (!next || next === current) return;
        fetch("/teacher/sections/" + id + "/rename", {
            method: "POST",
            headers: csrfHeaders({ "Content-Type": "application/x-www-form-urlencoded" }),
            body: "name=" + encodeURIComponent(next)
        }).then(function (r) {
            if (!r.ok) throw new Error("rename failed");
            return r.json();
        }).then(function (sec) {
            renameEverywhere(sec.id, sec.name);
        }).catch(function () {
            alert("Could not rename the section. The name may already be in use.");
        });
    }

    function deleteSection(id, name) {
        if (!confirm("Delete the section “" + name + "”? (only possible if no question uses it)")) return;
        fetch("/teacher/sections/" + id + "/remove", {
            method: "POST",
            headers: csrfHeaders()
        }).then(function (r) {
            return r.json();
        }).then(function (res) {
            if (res.ok) removeEverywhere(id);
            else alert(res.message || "Could not delete the section.");
        }).catch(function () {
            alert("Could not delete the section. Please try again.");
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
            label.textContent = labelOf(opt);
            trigger.classList.remove("is-placeholder");
            options().forEach(function (o) { o.classList.remove("selected"); });
            opt.classList.add("selected");
            combo.classList.remove("open");
        }

        // Reflect any server-rendered preselection (edit forms).
        if (value.value) {
            var pre = optionsWrap.querySelector('.combo-option[data-value="' + value.value + '"]');
            if (pre) { label.textContent = labelOf(pre); pre.classList.add("selected"); trigger.classList.remove("is-placeholder"); }
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
                var name = labelOf(o).toLowerCase();
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
            fetch("/teacher/sections", {
                method: "POST",
                headers: csrfHeaders({ "Content-Type": "application/x-www-form-urlencoded" }),
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
            var editBtn = e.target.closest(".combo-edit");
            if (editBtn) {
                e.preventDefault(); e.stopPropagation();
                var row = editBtn.closest(".combo-option");
                renameSection(row.getAttribute("data-value"), labelOf(row));
                return;
            }
            var delBtn = e.target.closest(".combo-del");
            if (delBtn) {
                e.preventDefault(); e.stopPropagation();
                var drow = delBtn.closest(".combo-option");
                deleteSection(drow.getAttribute("data-value"), labelOf(drow));
                return;
            }
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
