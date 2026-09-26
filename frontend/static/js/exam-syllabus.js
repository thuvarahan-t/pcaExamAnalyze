(function () {
    "use strict";

    // Admin Syllabus tab: units → one competency → competency levels (periods, content tree,
    // learning outcomes). Data comes from /exam/admin/syllabus/units as JSON; a whole unit is
    // saved at once.
    var app = document.getElementById("syllabusApp");
    if (!app) return;

    var API = "/exam/admin/syllabus/units";
    var tokenInput = document.querySelector('#syToken input[name="_csrf"]');
    var $ = function (id) { return document.getElementById(id); };
    var tabs = $("syUnitTabs");
    var editor = $("syEditor");
    var empty = $("syEmpty");
    var status = $("syStatus");
    var unitName = $("syUnitName");
    var competency = $("syCompetency");
    var levelName = $("syLevelName");
    var levelToggle = $("syLevelToggle");
    var levelPanel = $("syLevelPanel");
    var levelOptions = $("syLevelOptions");
    var deleteLevel = $("syDeleteLevel");
    var totalPeriods = $("syTotalPeriods");
    var alloc = $("syAlloc");
    var allocLine = $("syAllocLine");
    var noLevel = $("syNoLevel");
    var panels = $("syPanels");
    var tree = $("syTree");
    var outcomes = $("syOutcomes");

    var units = [];
    var unitIndex = -1;
    var levelIndex = -1;
    var dirty = false;
    var saving = null;

    function unit() { return units[unitIndex]; }
    function level() { var u = unit(); return u && u.levels[levelIndex]; }

    function warn(message, title) {
        if (window.PcaDialog) window.PcaDialog.alert(message, { title: title || "Not saved", type: "error" });
    }

    /** Every delete asks first. `twice` adds a second "are you sure" step for big deletes. */
    function confirmDelete(message, title, acceptText, twice) {
        if (!window.PcaDialog) return Promise.resolve(window.confirm(message));
        return window.PcaDialog.confirm(message, { title: title, acceptText: acceptText || "Delete", type: "danger" })
            .then(function (ok) {
                if (!ok || !twice) return ok;
                return window.PcaDialog.confirm("This cannot be undone once saved. Delete it permanently?",
                    { title: "Are you sure?", acceptText: "Yes, delete", type: "danger" });
            });
    }

    function setStatus(kind, text) {
        var icons = { saved: "bi-cloud-check", dirty: "bi-pencil", saving: "bi-cloud-arrow-up", error: "bi-exclamation-triangle" };
        status.className = "qe-status " + kind;
        status.innerHTML = '<i class="bi ' + icons[kind] + '"></i> ';
        status.appendChild(document.createTextNode(text));
    }

    function markDirty() {
        dirty = true;
        setStatus("dirty", "Unsaved changes");
    }

    function request(url, body) {
        var headers = { "Content-Type": "application/json" };
        if (tokenInput) headers["X-CSRF-TOKEN"] = tokenInput.value;
        return fetch(url, { method: body === undefined ? "GET" : "POST", headers: headers, body: body === undefined ? undefined : JSON.stringify(body) })
            .then(function (response) {
                return response.json().catch(function () { return {}; }).then(function (json) {
                    if (!response.ok) throw new Error(json.message || "The server could not save the syllabus");
                    return json;
                });
            });
    }

    // ------------------------------------------------------------ state helpers
    function node(text) { return { text: text || "", children: [], open: true }; }

    function prepare(u) {
        (u.levels || []).forEach(function (l) {
            l.contents = l.contents || [];
            l.outcomes = l.outcomes || [];
            (function walk(list) { list.forEach(function (n) { n.open = true; n.children = n.children || []; walk(n.children); }); })(l.contents);
        });
        u.levels = u.levels || [];
        return u;
    }

    function payload(u) {
        function strip(list) {
            return list.map(function (n) { return { text: n.text, children: strip(n.children) }; });
        }
        return {
            name: u.name,
            competency: u.competency,
            totalPeriods: u.totalPeriods === "" || u.totalPeriods == null ? null : Number(u.totalPeriods),
            levels: u.levels.map(function (l) {
                return { id: l.id || null, name: l.name, periods: l.periods === "" || l.periods == null ? null : Number(l.periods), contents: strip(l.contents), outcomes: l.outcomes };
            })
        };
    }

    // ------------------------------------------------------------ rendering
    function renderTabs() {
        tabs.innerHTML = "";
        units.forEach(function (u, i) {
            var button = document.createElement("button");
            button.type = "button";
            button.className = "syllabus-unit" + (i === unitIndex ? " active" : "");
            button.setAttribute("role", "tab");
            button.setAttribute("aria-selected", i === unitIndex ? "true" : "false");
            button.innerHTML = "<b></b><span></span>";
            button.querySelector("b").textContent = String(i + 1).padStart(2, "0");
            button.querySelector("span").textContent = u.name || "Untitled unit";
            button.addEventListener("click", function () { if (i !== unitIndex) switchUnit(i); });
            tabs.appendChild(button);
        });
        empty.hidden = units.length > 0;
        editor.hidden = unitIndex < 0;
    }

    function renderUnit() {
        var u = unit();
        if (!u) { renderTabs(); return; }
        unitName.value = u.name || "";
        competency.value = u.competency || "";
        totalPeriods.value = u.totalPeriods != null ? u.totalPeriods : "";
        if (levelIndex >= u.levels.length) levelIndex = u.levels.length - 1;
        if (levelIndex < 0 && u.levels.length) levelIndex = 0;
        renderLevel();
        renderTabs();
    }

    function number(value) {
        var n = Number(value);
        return value === "" || value == null || !Number.isFinite(n) ? 0 : n;
    }

    /** Compare the editable competency total with its level-by-level allocation. */
    function renderAlloc() {
        var u = unit();
        if (!u) return;
        var total = u.totalPeriods === "" || u.totalPeriods == null ? null : number(u.totalPeriods);
        var used = u.levels.reduce(function (sum, l) { return sum + number(l.periods); }, 0);
        var assigned = u.levels.filter(function (l) { return number(l.periods) > 0; }).length;
        var remaining = total == null ? null : total - used;
        var state = total == null ? "" : remaining < 0 ? "over" : remaining === 0 ? "full" : "";
        alloc.className = "sy-alloc" + (state ? " " + state : "");
        alloc.innerHTML = '<div class="sy-alloc-row"><span>Allocated to levels</span><b>' + used + (total == null ? " periods" : " / " + total) + '</b></div>' +
            '<span class="sy-alloc-empty">' + assigned + ' of ' + u.levels.length + ' competency levels have periods.</span>' +
            (total == null ? "" : '<div class="sy-alloc-row sy-remaining"><span>' + (remaining < 0 ? "Over by" : "Remaining") + '</span><b>' + Math.abs(remaining) + '</b></div>');
        allocLine.className = "sy-alloc-line" + (state ? " " + state : "");
        allocLine.textContent = total == null ? "Enter Assigned periods, then divide them between the competency levels."
            : remaining < 0 ? used + " allocated · " + (-remaining) + " over the assigned total"
            : used + " of " + total + " allocated · " + remaining + " remaining";
    }

    function renderLevel() {
        var u = unit();
        var l = level();
        levelName.disabled = !l;
        levelName.value = l ? l.name : "";
        noLevel.hidden = !!l;
        panels.hidden = !l;
        deleteLevel.hidden = !l;
        levelOptions.innerHTML = "";
        u.levels.forEach(function (item, i) {
            var option = document.createElement("div");
            option.className = "sy-level-option" + (i === levelIndex ? " active" : "");
            var choose = document.createElement("button");
            choose.type = "button";
            choose.className = "sy-level-choice";
            choose.setAttribute("role", "option");
            choose.setAttribute("aria-selected", i === levelIndex ? "true" : "false");
            choose.textContent = item.name || "Untitled level";
            choose.addEventListener("click", function () {
                levelIndex = i;
                openLevels(false);
                renderLevel();
            });
            var periodWrap = document.createElement("label");
            periodWrap.className = "sy-level-period";
            periodWrap.innerHTML = "<span>Periods</span>";
            var periodInput = document.createElement("input");
            periodInput.type = "number";
            periodInput.min = "0";
            periodInput.max = "999";
            periodInput.inputMode = "numeric";
            periodInput.placeholder = "0";
            periodInput.value = item.periods != null ? item.periods : "";
            periodInput.setAttribute("aria-label", "Periods for " + (item.name || "competency level"));
            periodInput.addEventListener("input", function () {
                item.periods = periodInput.value;
                renderAlloc();
                markDirty();
            });
            periodWrap.appendChild(periodInput);
            option.appendChild(choose);
            option.appendChild(periodWrap);
            levelOptions.appendChild(option);
        });
        renderAlloc();
        if (l) {
            renderTree();
            renderOutcomes();
        }
    }

    // Content tree: unlimited depth, each node can expand/collapse.
    function renderTree(focusNode) {
        var l = level();
        tree.innerHTML = "";
        if (!l.contents.length) {
            var hint = document.createElement("li");
            hint.className = "sy-tree-empty";
            hint.textContent = "No content yet. Use “Add content”, then the ↳ button to add sub-content.";
            tree.appendChild(hint);
        }
        var focusInput = null;

        function build(list, container) {
            list.forEach(function (n, i) {
                var li = document.createElement("li");
                var row = document.createElement("div");
                row.className = "sy-node-row";

                var caret = document.createElement("button");
                caret.type = "button";
                var hasChildren = n.children.length > 0;
                caret.className = "sy-caret" + (hasChildren ? (n.open ? " open" : "") : " leaf");
                caret.innerHTML = hasChildren ? '<i class="bi bi-chevron-right"></i>' : '<i class="bi bi-dot"></i>';
                caret.setAttribute("aria-label", hasChildren ? (n.open ? "Collapse" : "Expand") : "No sub-content");
                caret.tabIndex = hasChildren ? 0 : -1;
                caret.addEventListener("click", function () {
                    if (!n.children.length) return;
                    n.open = !n.open;
                    renderTree();
                });

                var input = document.createElement("input");
                input.value = n.text;
                input.maxLength = 500;
                input.placeholder = "Content";
                input.setAttribute("aria-label", "Content");
                input.addEventListener("input", function () { n.text = input.value; markDirty(); });
                input.addEventListener("keydown", function (event) {
                    if (event.key !== "Enter") return;
                    event.preventDefault();
                    var sibling = node("");
                    list.splice(i + 1, 0, sibling);
                    markDirty();
                    renderTree(sibling);
                });

                var addChild = document.createElement("button");
                addChild.type = "button";
                addChild.className = "sy-node-btn";
                addChild.title = "Add sub-content";
                addChild.setAttribute("aria-label", "Add sub-content");
                addChild.innerHTML = '<i class="bi bi-arrow-return-right"></i>';
                addChild.addEventListener("click", function () {
                    var child = node("");
                    n.children.push(child);
                    n.open = true;
                    markDirty();
                    renderTree(child);
                });

                var remove = document.createElement("button");
                remove.type = "button";
                remove.className = "sy-node-btn remove";
                remove.title = "Remove";
                remove.setAttribute("aria-label", "Remove content");
                remove.innerHTML = '<i class="bi bi-x-lg"></i>';
                remove.addEventListener("click", function () {
                    var label = n.text.trim() ? "“" + n.text.trim() + "”" : "This empty content row";
                    var extra = n.children.length ? " and everything under it" : "";
                    confirmDelete(label + extra + " will be removed.", "Delete this content?", "Delete", n.children.length > 0)
                        .then(function (ok) {
                            if (!ok) return;
                            list.splice(i, 1);
                            markDirty();
                            renderTree();
                        });
                });

                row.appendChild(caret);
                row.appendChild(input);
                row.appendChild(addChild);
                row.appendChild(remove);
                li.appendChild(row);
                if (hasChildren) {
                    var ul = document.createElement("ul");
                    ul.hidden = !n.open;
                    build(n.children, ul);
                    li.appendChild(ul);
                }
                container.appendChild(li);
                if (n === focusNode) focusInput = input;
            });
        }

        build(l.contents, tree);
        if (focusInput) focusInput.focus();
    }

    function setAllOpen(open) {
        (function walk(list) { list.forEach(function (n) { n.open = open; walk(n.children); }); })(level().contents);
        renderTree();
    }

    function renderOutcomes(focusIndex) {
        var l = level();
        outcomes.innerHTML = "";
        var list = l.outcomes.length ? l.outcomes : [""];
        if (!l.outcomes.length) l.outcomes = list;
        list.forEach(function (text, i) {
            var row = document.createElement("div");
            row.className = "qe-list-row";
            var input = document.createElement("input");
            input.className = "form-input";
            input.value = text;
            input.maxLength = 500;
            input.placeholder = "Learning outcome";
            input.setAttribute("aria-label", "Learning outcome");
            input.addEventListener("input", function () { l.outcomes[i] = input.value; markDirty(); });
            input.addEventListener("keydown", function (event) {
                if (event.key !== "Enter") return;
                event.preventDefault();
                l.outcomes.splice(i + 1, 0, "");
                markDirty();
                renderOutcomes(i + 1);
            });
            var remove = document.createElement("button");
            remove.type = "button";
            remove.className = "qe-list-remove" + (list.length === 1 ? " only" : "");
            remove.setAttribute("aria-label", "Remove learning outcome");
            remove.innerHTML = '<i class="bi bi-x-lg"></i>';
            remove.addEventListener("click", function () {
                var text = (l.outcomes[i] || "").trim();
                confirmDelete((text ? "“" + text + "”" : "This empty learning outcome row") + " will be removed.",
                    "Delete this learning outcome?", "Delete", false)
                    .then(function (ok) {
                        if (!ok) return;
                        if (l.outcomes.length > 1) l.outcomes.splice(i, 1); else l.outcomes[0] = "";
                        markDirty();
                        renderOutcomes();
                    });
            });
            row.appendChild(input);
            row.appendChild(remove);
            outcomes.appendChild(row);
            if (i === focusIndex) input.focus();
        });
    }

    // ------------------------------------------------------------ level dropdown
    function openLevels(open) {
        levelPanel.hidden = !open;
        levelToggle.setAttribute("aria-expanded", open ? "true" : "false");
    }

    function addLevel() {
        var u = unit();
        u.levels.push({ id: null, name: "Competency level " + (u.levels.length + 1), periods: null, contents: [], outcomes: [] });
        levelIndex = u.levels.length - 1;
        openLevels(false);
        markDirty();
        renderLevel();
        levelName.focus();
        levelName.select();
    }

    levelToggle.addEventListener("click", function () { openLevels(levelPanel.hidden); });
    $("syAddLevel").addEventListener("click", addLevel);
    $("syFirstLevel").addEventListener("click", addLevel);
    deleteLevel.addEventListener("click", function () {
        var l = level();
        if (!l) return;
        openLevels(false);
        var go = function () {
            unit().levels.splice(levelIndex, 1);
            levelIndex = Math.min(levelIndex, unit().levels.length - 1);
            markDirty();
            renderLevel();
        };
        confirmDelete("“" + (l.name || "This level") + "”, its periods, content and learning outcomes will be removed.",
            "Delete this competency level?", "Delete level", true)
            .then(function (ok) { if (ok) go(); });
    });
    document.addEventListener("pointerdown", function (event) {
        if (!levelPanel.hidden && !$("syLevel").contains(event.target)) openLevels(false);
    }, true);
    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && !levelPanel.hidden) openLevels(false);
    });

    // ------------------------------------------------------------ field bindings
    unitName.addEventListener("input", function () {
        unit().name = unitName.value;
        var label = tabs.children[unitIndex] && tabs.children[unitIndex].querySelector("span");
        if (label) label.textContent = unitName.value || "Untitled unit";
        markDirty();
    });
    competency.addEventListener("input", function () { unit().competency = competency.value; markDirty(); });
    totalPeriods.addEventListener("input", function () {
        unit().totalPeriods = totalPeriods.value;
        renderAlloc();
        markDirty();
    });
    levelName.addEventListener("input", function () {
        level().name = levelName.value;
        var active = levelOptions.children[levelIndex];
        if (active) active.querySelector(".sy-level-choice").textContent = levelName.value || "Untitled level";
        markDirty();
    });
    $("syAddTopic").addEventListener("click", function () {
        var n = node("");
        level().contents.push(n);
        markDirty();
        renderTree(n);
    });
    $("syAddOutcome").addEventListener("click", function () {
        level().outcomes.push("");
        markDirty();
        renderOutcomes(level().outcomes.length - 1);
    });
    $("syExpandAll").addEventListener("click", function () { setAllOpen(true); });
    $("syCollapseAll").addEventListener("click", function () { setAllOpen(false); });

    // ------------------------------------------------------------ save / switch / add / delete
    function save() {
        if (saving) return saving;
        var u = unit();
        if (!u || !dirty) return Promise.resolve();
        if (!(u.name || "").trim()) {
            warn("Enter the unit name before saving.");
            unitName.focus();
            return Promise.reject(new Error("name"));
        }
        setStatus("saving", "Saving...");
        var keepLevel = levelIndex;
        saving = request(u.id ? API + "/" + u.id : API, payload(u))
            .then(function (saved) {
                // Keep the expand/collapse state of the tree the teacher is looking at.
                var openState = [];
                u.levels.forEach(function (l) {
                    var flags = [];
                    (function walk(list) { list.forEach(function (n) { flags.push(n.open); walk(n.children); }); })(l.contents);
                    openState.push(flags);
                });
                units[unitIndex] = prepare(saved);
                units[unitIndex].levels.forEach(function (l, li) {
                    var flags = openState[li] || [];
                    var k = 0;
                    (function walk(list) { list.forEach(function (n) { if (k < flags.length) n.open = flags[k]; k++; walk(n.children); }); })(l.contents);
                });
                levelIndex = Math.min(keepLevel, units[unitIndex].levels.length - 1);
                dirty = false;
                setStatus("saved", "Saved");
                renderUnit();
            })
            .catch(function (error) {
                setStatus("error", "Not saved");
                if (error.message !== "name") warn(error.message);
                throw error;
            })
            .finally(function () { saving = null; });
        return saving;
    }

    function switchUnit(i) {
        save().then(function () {
            unitIndex = i;
            levelIndex = 0;
            dirty = false;
            setStatus("saved", "Saved");
            renderUnit();
        }, function () { /* Stay on the unit that could not be saved. */ });
    }

    $("sySave").addEventListener("click", function () {
        if (!dirty) { setStatus("saved", "Saved"); return; }
        save().catch(function () {});
    });

    $("syAddUnit").addEventListener("click", function () {
        save().then(function () {
            units.push(prepare({ id: null, name: "Unit " + (units.length + 1), competency: "", totalPeriods: null, levels: [] }));
            unitIndex = units.length - 1;
            levelIndex = -1;
            renderUnit();
            markDirty();
            unitName.focus();
            unitName.select();
            editor.scrollIntoView({ behavior: "smooth", block: "start" });
        }, function () {});
    });

    $("syDeleteUnit").addEventListener("click", function () {
        var u = unit();
        if (!u) return;
        var go = function () {
            (u.id ? request(API + "/" + u.id + "/delete", {}) : Promise.resolve()).then(function () {
                units.splice(unitIndex, 1);
                unitIndex = Math.min(unitIndex, units.length - 1);
                levelIndex = 0;
                dirty = false;
                setStatus("saved", "Saved");
                renderUnit();
                if (unitIndex < 0) renderTabs();
            }, function (error) { warn(error.message, "Unit not deleted"); });
        };
        confirmDelete("“" + (u.name || "This unit") + "”, its competency levels, content and learning outcomes will be deleted.",
            "Delete this unit?", "Delete unit", true)
            .then(function (ok) { if (ok) go(); });
    });

    document.addEventListener("keydown", function (event) {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s" && unit()) {
            event.preventDefault();
            save().catch(function () {});
        }
    });
    window.addEventListener("beforeunload", function (event) {
        if (dirty) { event.preventDefault(); event.returnValue = ""; }
    });

    function showUnits(list) {
        units = (list || []).map(prepare);
        unitIndex = units.length ? 0 : -1;
        levelIndex = 0;
        renderUnit();
        if (unitIndex < 0) renderTabs();
    }

    if (Array.isArray(window.PCA_INITIAL_SYLLABUS)) {
        showUnits(window.PCA_INITIAL_SYLLABUS);
    } else {
        request(API).then(showUnits, function (error) {
            renderTabs();
            warn(error.message, "Syllabus could not be loaded");
        });
    }
})();
