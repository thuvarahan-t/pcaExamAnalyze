(function () {
    "use strict";

    // Question Editor for image-sheet exams: one question on screen, each saved on its own
    // (AJAX), so leaving some images out never clears the others.
    var root = document.getElementById("questionEditor");
    if (!root) return;

    var examId = root.dataset.exam;
    var form = document.getElementById("qeForm");
    var palette = Array.from(root.querySelectorAll(".qe-num"));
    var numberLabel = document.getElementById("qeNumber");
    var status = document.getElementById("qeStatus");
    var readyLabel = document.getElementById("qeReady");
    var drop = document.getElementById("qeDrop");
    var image = document.getElementById("qeImage");
    var dropEmpty = document.getElementById("qeDropEmpty");
    var fileInput = document.getElementById("qeFile");
    var removeButton = document.getElementById("qeRemoveImage");
    var weightInput = document.getElementById("qeWeight");
    var weightLabel = document.getElementById("qeWeightLabel");
    var stars = Array.from(document.querySelectorAll("#qeStars button"));
    var weightGroup = weightInput.closest(".qe-weight-group");
    var weightNumber = document.getElementById("qeWeightInput");
    var weightRange = document.getElementById("qeWeightRange");
    var weightClear = document.getElementById("qeWeightClear");
    var prev = document.getElementById("qePrev");
    var next = document.getElementById("qeNext");
    var saveButton = document.getElementById("qeSave");
    var scheduleForm = document.getElementById("qeScheduleForm");

    var published = root.dataset.published === "true";
    var subList = document.getElementById("qeSubList");
    var subFile = document.getElementById("qeSubFile");
    var subAdd = document.getElementById("qeSubAdd");
    var freeButton = document.getElementById("qeFreeMark");
    var freeValue = document.getElementById("qeFreeMarkValue");
    var freeNote = document.getElementById("qeFreeNote");
    var MAX_SUBS = 4;
    var subExisting = [];   // saved supporting image ids of the question on screen
    var subRemoved = [];    // saved ids marked for removal
    var subPending = [];    // {file, url} picked but not yet saved
    var subPreviews = {};   // question -> object URLs still uploading in the background
    var MAX_BYTES = 5 * 1024 * 1024;
    var SHRINK_ABOVE = 900 * 1024;
    var MAX_EDGE = 1800;

    // ------------------------------------------------ syllabus tags
    // Each "unit block": Unit → its Competency (shown automatically) → Competency Level →
    // Content (the level's tree, tick items) + Learning Outcomes (tick items). A question can
    // have several blocks because it may cover 2-3 units.
    var PATH_SEP = " › ";
    var MAX_TAGS = 10;
    var tagsBox = document.getElementById("qeTags");
    var addTagButton = document.getElementById("qeAddTag");
    var legacyNote = document.getElementById("qeLegacy");
    var syllabus = [];
    var tagMap = {};
    var tagState = [];

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text;
        return node;
    }

    function findById(list, id) {
        for (var i = 0; i < (list || []).length; i++) if (list[i].id === id) return list[i];
        return null;
    }

    function blankTag() { return { unitId: null, levelId: null, unitName: "", competency: "", levelName: "", contents: [], outcomes: [] }; }

    function cloneTags(list) {
        return (list || []).map(function (t) {
            return { unitId: t.unitId, levelId: t.levelId, unitName: t.unitName || "", competency: t.competency || "",
                levelName: t.levelName || "", contents: (t.contents || []).slice(), outcomes: (t.outcomes || []).slice() };
        });
    }

    function levelPaths(level) {
        var out = [];
        (function walk(nodes, prefix) {
            (nodes || []).forEach(function (n) {
                var path = prefix ? prefix + PATH_SEP + n.text : n.text;
                out.push(path);
                walk(n.children, path);
            });
        })(level ? level.contents : [], "");
        return out;
    }

    function closeAllDropdowns(except) {
        tagsBox.querySelectorAll(".qe-dd.open").forEach(function (dd) { if (dd !== except) dd.closeDropdown(); });
    }

    /** Custom glass dropdown; `fill(panel, close)` builds the panel each time it opens. */
    function dropdown(text, placeholder, disabled, fill) {
        var wrap = el("div", "qe-dd");
        var trigger = el("button", "form-input qe-dd-trigger" + (text ? "" : " empty"));
        trigger.type = "button";
        trigger.disabled = !!disabled;
        trigger.setAttribute("aria-haspopup", "listbox");
        trigger.setAttribute("aria-expanded", "false");
        var label = el("span", "qe-dd-text", text || placeholder);
        trigger.appendChild(label);
        trigger.appendChild(el("i", "bi bi-chevron-down qe-dd-caret"));
        var panel = el("div", "qe-dd-panel");
        panel.hidden = true;
        function close() {
            panel.hidden = true;
            wrap.classList.remove("open");
            trigger.setAttribute("aria-expanded", "false");
        }
        wrap.closeDropdown = close;
        wrap.setText = function (value) {
            label.textContent = value || placeholder;
            trigger.classList.toggle("empty", !value);
        };
        trigger.addEventListener("click", function () {
            if (!panel.hidden) { close(); return; }
            closeAllDropdowns(wrap);
            panel.innerHTML = "";
            fill(panel, close);
            panel.hidden = false;
            wrap.classList.add("open");
            trigger.setAttribute("aria-expanded", "true");
            placePanel(panel, trigger);
        });
        wrap.appendChild(trigger);
        wrap.appendChild(panel);
        return wrap;
    }

    /**
     * Keeps a dropdown panel fully on screen: never wider than the viewport, shifted left when it
     * would overflow the right edge, and opened upwards when there is more room above.
     * (Positioned inside its own dropdown box, so the glass cards' blur does not affect it.)
     */
    function placePanel(panel, trigger) {
        var margin = 8;
        var rect = trigger.getBoundingClientRect();
        panel.style.left = "0px";
        panel.style.right = "auto";
        panel.style.top = "calc(100% + 6px)";
        panel.style.bottom = "auto";
        panel.style.maxHeight = "none";
        panel.style.maxWidth = Math.max(220, window.innerWidth - margin * 2) + "px";
        var box = panel.getBoundingClientRect();
        var below = window.innerHeight - rect.bottom - margin - 6;
        var above = rect.top - margin - 6;
        if (box.height > below && above > below) {
            panel.style.top = "auto";
            panel.style.bottom = "calc(100% + 6px)";
            panel.style.maxHeight = Math.max(160, above) + "px";
        } else {
            panel.style.maxHeight = Math.max(160, below) + "px";
        }
        box = panel.getBoundingClientRect();
        var shift = 0;
        if (box.right > window.innerWidth - margin) shift = window.innerWidth - margin - box.right;
        if (box.left + shift < margin) shift = margin - box.left;
        panel.style.left = shift + "px";
    }

    window.addEventListener("resize", function () { closeAllDropdowns(null); });

    function option(text, small, active, onPick) {
        var button = el("button", "qe-dd-option" + (active ? " active" : ""));
        button.type = "button";
        button.setAttribute("role", "option");
        button.appendChild(el("span", null, text));
        if (small) button.appendChild(el("small", null, small));
        button.addEventListener("click", onPick);
        return button;
    }

    function chips(container, values, known, labelOf, onRemove) {
        container.innerHTML = "";
        values.forEach(function (value) {
            var stale = known && known.indexOf(value) < 0;
            var chip = el("span", "qe-chip" + (stale ? " stale" : ""));
            chip.title = stale ? value + " (no longer in the syllabus; dropped when saved)" : value;
            chip.appendChild(el("span", null, labelOf(value)));
            var x = el("button");
            x.type = "button";
            x.setAttribute("aria-label", "Remove " + value);
            x.innerHTML = '<i class="bi bi-x-lg"></i>';
            x.addEventListener("click", function () { onRemove(value); });
            chip.appendChild(x);
            container.appendChild(chip);
        });
    }

    function toggle(list, value, on) {
        var at = list.indexOf(value);
        if (on && at < 0) list.push(value);
        if (!on && at >= 0) list.splice(at, 1);
    }

    function countText(n, singular) {
        return n ? n + " " + singular + (n === 1 ? "" : "s") + " selected" : "";
    }

    function renderTags() {
        tagsBox.innerHTML = "";
        tagState.forEach(function (tag, i) { tagsBox.appendChild(renderTag(tag, i)); });
        addTagButton.hidden = tagState.length >= MAX_TAGS;
    }

    function renderTag(tag, i) {
        var unit = findById(syllabus, tag.unitId);
        var level = unit ? findById(unit.levels, tag.levelId) : null;
        var card = el("div", "qe-tag");

        var head = el("div", "qe-tag-head");
        var no = el("span", "qe-tag-no");
        no.innerHTML = '<i class="bi bi-bookmark"></i> ';
        no.appendChild(document.createTextNode("Unit " + (i + 1)));
        head.appendChild(no);
        var remove = el("button", "qe-tag-remove");
        remove.type = "button";
        remove.innerHTML = '<i class="bi bi-trash3"></i> Remove';
        remove.addEventListener("click", function () {
            var name = unit ? unit.name : tag.unitName;
            var ask = !tag.unitId ? Promise.resolve(true) : window.PcaDialog
                ? window.PcaDialog.confirm("“" + (name || "This unit") + "” and its selected content and learning outcomes will be removed from this question.",
                    { title: "Remove this unit from the question?", acceptText: "Remove", type: "danger" })
                : Promise.resolve(window.confirm("Remove this unit from the question?"));
            ask.then(function (ok) {
                if (!ok) return;
                tagState.splice(i, 1);
                if (!tagState.length) tagState.push(blankTag());
                markDirty();
                renderTags();
            });
        });
        head.appendChild(remove);
        card.appendChild(head);

        // Row 1: Unit | Competency (automatic) | Competency Level
        var row = el("div", "qe-tag-grid");
        var unitField = el("div", "qe-tag-field");
        unitField.appendChild(el("span", "qe-tag-label", "Unit"));
        var unitText = unit ? unit.name : tag.unitId ? (tag.unitName || "Unit") + " (removed from syllabus)" : "";
        unitField.appendChild(dropdown(unitText, "Select unit", false, function (panel, close) {
            if (!syllabus.length) {
                panel.appendChild(el("div", "qe-dd-empty", "No units yet. Add them in Admin → Syllabus first."));
                return;
            }
            syllabus.forEach(function (u) {
                panel.appendChild(option(u.name, u.levels.length + " level" + (u.levels.length === 1 ? "" : "s"), u.id === tag.unitId, function () {
                    close();
                    if (u.id === tag.unitId) return;
                    tag.unitId = u.id;
                    tag.unitName = u.name;
                    tag.competency = u.competency || "";
                    var only = u.levels.length === 1 ? u.levels[0] : null;
                    tag.levelId = only ? only.id : null;
                    tag.levelName = only ? only.name : "";
                    tag.contents = [];
                    tag.outcomes = [];
                    markDirty();
                    renderTags();
                }));
            });
        }));
        row.appendChild(unitField);

        var compField = el("div", "qe-tag-field");
        compField.appendChild(el("span", "qe-tag-label", "Competency"));
        var competencyText = unit ? unit.competency : tag.competency;
        compField.appendChild(el("div", "qe-tag-readonly" + (competencyText ? "" : " empty"),
            competencyText || (tag.unitId ? "No competency set for this unit" : "Shown after choosing a unit")));
        row.appendChild(compField);

        var levelField = el("div", "qe-tag-field");
        levelField.appendChild(el("span", "qe-tag-label", "Competency Level"));
        var levelText = level ? level.name : tag.levelId ? (tag.levelName || "Level") + " (removed)" : "";
        levelField.appendChild(dropdown(levelText, unit ? "Select competency level" : "Choose a unit first", !unit, function (panel, close) {
            if (!unit.levels.length) {
                panel.appendChild(el("div", "qe-dd-empty", "This unit has no competency levels yet. Add them in Admin → Syllabus."));
                return;
            }
            unit.levels.forEach(function (l) {
                panel.appendChild(option(l.name, l.periods != null ? l.periods + " periods" : "", l.id === tag.levelId, function () {
                    close();
                    if (l.id === tag.levelId) return;
                    tag.levelId = l.id;
                    tag.levelName = l.name;
                    tag.contents = [];
                    tag.outcomes = [];
                    markDirty();
                    renderTags();
                }));
            });
        }));
        row.appendChild(levelField);
        card.appendChild(row);

        // Row 2: Content (tree, tick) | Learning Outcomes (tick)
        var row2 = el("div", "qe-tag-grid two");
        var knownPaths = level ? levelPaths(level) : null;
        var knownOutcomes = level ? level.outcomes : null;

        var contentField = el("div", "qe-tag-field");
        contentField.appendChild(el("span", "qe-tag-label", "Content"));
        var contentChips = el("div", "qe-chips");
        var contentDd = dropdown(countText(tag.contents.length, "item"), level ? "Select content" : "Choose a competency level first", !level, function (panel) {
            if (!level.contents.length) {
                panel.appendChild(el("div", "qe-dd-empty", "This competency level has no content yet."));
                return;
            }
            var tools = el("div", "qe-dd-tools");
            tools.appendChild(el("span", null, "Tick one or more"));
            var clear = el("button", null, "Clear");
            clear.type = "button";
            clear.addEventListener("click", function () {
                tag.contents = [];
                panel.querySelectorAll("input[type=checkbox]").forEach(function (box) { box.checked = false; });
                refreshContent();
                markDirty();
            });
            tools.appendChild(clear);
            panel.appendChild(tools);
            var tree = el("ul", "qe-ctree");
            (function build(nodes, prefix, container) {
                nodes.forEach(function (n) {
                    var path = prefix ? prefix + PATH_SEP + n.text : n.text;
                    var li = el("li");
                    var line = el("div", "qe-crow");
                    var hasChildren = n.children && n.children.length > 0;
                    var caret = el("button", "qe-ccaret open" + (hasChildren ? "" : " leaf"));
                    caret.type = "button";
                    caret.innerHTML = hasChildren ? '<i class="bi bi-chevron-right"></i>' : '<i class="bi bi-dot"></i>';
                    caret.tabIndex = hasChildren ? 0 : -1;
                    var check = el("label", "qe-check");
                    var box = el("input");
                    box.type = "checkbox";
                    box.checked = tag.contents.indexOf(path) >= 0;
                    box.addEventListener("change", function () {
                        toggle(tag.contents, path, box.checked);
                        refreshContent();
                        markDirty();
                    });
                    check.appendChild(box);
                    check.appendChild(el("span", null, n.text));
                    line.appendChild(caret);
                    line.appendChild(check);
                    li.appendChild(line);
                    if (hasChildren) {
                        var ul = el("ul");
                        build(n.children, path, ul);
                        li.appendChild(ul);
                        caret.addEventListener("click", function () {
                            ul.hidden = !ul.hidden;
                            caret.classList.toggle("open", !ul.hidden);
                        });
                    }
                    container.appendChild(li);
                });
            })(level.contents, "", tree);
            panel.appendChild(tree);
        });
        function refreshContent() {
            contentDd.setText(countText(tag.contents.length, "item"));
            chips(contentChips, tag.contents, knownPaths, function (path) {
                var parts = path.split(PATH_SEP);
                return parts[parts.length - 1];
            }, function (path) {
                toggle(tag.contents, path, false);
                refreshContent();
                markDirty();
            });
        }
        contentField.appendChild(contentDd);
        contentField.appendChild(contentChips);
        row2.appendChild(contentField);

        var outcomeField = el("div", "qe-tag-field");
        outcomeField.appendChild(el("span", "qe-tag-label", "Learning Outcomes"));
        var outcomeChips = el("div", "qe-chips");
        var outcomeDd = dropdown(countText(tag.outcomes.length, "outcome"), level ? "Select learning outcomes" : "Choose a competency level first", !level, function (panel) {
            if (!level.outcomes.length) {
                panel.appendChild(el("div", "qe-dd-empty", "This competency level has no learning outcomes yet."));
                return;
            }
            var tools = el("div", "qe-dd-tools");
            tools.appendChild(el("span", null, "Tick one or more"));
            var clear = el("button", null, "Clear");
            clear.type = "button";
            clear.addEventListener("click", function () {
                tag.outcomes = [];
                panel.querySelectorAll("input[type=checkbox]").forEach(function (box) { box.checked = false; });
                refreshOutcomes();
                markDirty();
            });
            tools.appendChild(clear);
            panel.appendChild(tools);
            level.outcomes.forEach(function (text) {
                var check = el("label", "qe-check");
                var box = el("input");
                box.type = "checkbox";
                box.checked = tag.outcomes.indexOf(text) >= 0;
                box.addEventListener("change", function () {
                    toggle(tag.outcomes, text, box.checked);
                    refreshOutcomes();
                    markDirty();
                });
                check.appendChild(box);
                check.appendChild(el("span", null, text));
                panel.appendChild(check);
            });
        });
        function refreshOutcomes() {
            outcomeDd.setText(countText(tag.outcomes.length, "outcome"));
            chips(outcomeChips, tag.outcomes, knownOutcomes, function (text) { return text; }, function (text) {
                toggle(tag.outcomes, text, false);
                refreshOutcomes();
                markDirty();
            });
        }
        outcomeField.appendChild(outcomeDd);
        outcomeField.appendChild(outcomeChips);
        row2.appendChild(outcomeField);
        card.appendChild(row2);

        refreshContent();
        refreshOutcomes();
        if (tag.unitId && !unit) {
            card.appendChild(el("div", "qe-tag-warning", "This unit was removed from the syllabus. Choose another unit, or this block is dropped when you save."));
        }
        return card;
    }

    function loadTags(q, data) {
        tagState = cloneTags(tagMap[q]);
        if (!tagState.length) tagState.push(blankTag());
        renderTags();
        // Free-text values typed before questions were linked to the syllabus.
        var legacy = [["Competency", data.units], ["Competency level", data.levels], ["Content", data.contents], ["Learning outcome", data.outcomes]]
            .filter(function (pair) { return pair[1]; })
            .map(function (pair) { return pair[0] + ": " + pair[1].split("\n").join(", "); });
        legacyNote.hidden = !legacy.length;
        legacyNote.textContent = legacy.length ? "Typed earlier (before the syllabus link): " + legacy.join(" · ") : "";
    }

    function appendTags(body) {
        body.append("tagsSent", "1");
        tagState.forEach(function (tag) {
            if (!tag.unitId) return;
            body.append("tagUnit", String(tag.unitId));
            body.append("tagLevel", tag.levelId ? String(tag.levelId) : "");
            body.append("tagContents", tag.contents.join("\n"));
            body.append("tagOutcomes", tag.outcomes.join("\n"));
        });
    }

    addTagButton.addEventListener("click", function () {
        if (tagState.length >= MAX_TAGS) return;
        tagState.push(blankTag());
        markDirty();
        renderTags();
    });
    document.addEventListener("pointerdown", function (event) {
        tagsBox.querySelectorAll(".qe-dd.open").forEach(function (dd) { if (!dd.contains(event.target)) dd.closeDropdown(); });
    }, true);
    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape") closeAllDropdowns(null);
    });

    // ------------------------------------------------ time dropdown (custom, not a native select)
    var timeSelect = document.getElementById("qeTime");
    var timeTrigger = timeSelect.querySelector(".qe-select-trigger");
    var timePanel = timeSelect.querySelector(".qe-select-panel");
    var timeText = timeSelect.querySelector(".qe-select-text");

    function syncTimeSelect() {
        var checked = timePanel.querySelector("input:checked") || timePanel.querySelector('input[value=""]');
        checked.checked = true;
        timeText.textContent = checked.nextElementSibling.textContent;
        timeTrigger.classList.toggle("empty", !checked.value);
    }

    function openTimeSelect(open) {
        timePanel.hidden = !open;
        timeTrigger.setAttribute("aria-expanded", open ? "true" : "false");
        if (open) (timePanel.querySelector("input:checked") || timePanel.querySelector("input")).focus();
    }

    timeTrigger.addEventListener("click", function () { openTimeSelect(timePanel.hidden); });
    timePanel.addEventListener("change", function () { syncTimeSelect(); });
    timePanel.addEventListener("click", function (event) {
        if (event.target.closest(".qe-select-option") && event.detail > 0) window.setTimeout(function () { openTimeSelect(false); timeTrigger.focus(); }, 0);
    });
    timePanel.addEventListener("keydown", function (event) {
        if (event.key === "Enter" || event.key === "Escape") {
            event.preventDefault();
            openTimeSelect(false);
            timeTrigger.focus();
        }
    });
    // Close on any press outside the dropdown. Capture phase + pointerdown so nothing else on
    // the page (labels, the image picker, list buttons) can swallow or delay the event.
    document.addEventListener("pointerdown", function (event) {
        if (!timePanel.hidden && !timeSelect.contains(event.target)) openTimeSelect(false);
    }, true);
    timeSelect.addEventListener("focusout", function (event) {
        if (!timePanel.hidden && event.relatedTarget && !timeSelect.contains(event.relatedTarget)) openTimeSelect(false);
    });
    document.addEventListener("keydown", function (event) {
        if (event.key === "Escape" && !timePanel.hidden) openTimeSelect(false);
    });

    var index = 0;
    var dirty = false;
    var pendingFile = null;
    var removeImage = false;
    var objectUrl = null;
    // Background saving: moving to another question never waits for the server. Each
    // question's saves run in order; a failed one offers Retry and marks the question red.
    var queues = {};
    var inFlight = 0;
    var failed = {};
    var previews = {};

    function current() { return palette[index]; }

    function warn(message, title) {
        if (window.PcaDialog) window.PcaDialog.alert(message, { title: title || "Question not saved", type: "error" });
    }

    function setStatus(kind, text) {
        var icons = { saved: "bi-cloud-check", dirty: "bi-pencil", saving: "bi-cloud-arrow-up", error: "bi-exclamation-triangle" };
        status.className = "qe-status " + kind;
        status.innerHTML = '<i class="bi ' + icons[kind] + '"></i> ';
        status.appendChild(document.createTextNode(text));
    }

    function refreshStatus() {
        var failedList = Object.keys(failed);
        if (dirty) setStatus("dirty", "Unsaved changes");
        else if (inFlight) setStatus("saving", inFlight > 1 ? "Saving " + inFlight + " questions..." : "Saving...");
        else if (failedList.length) setStatus("error", "Not saved: Q" + failedList.join(", Q"));
        else setStatus("saved", "Saved");
    }

    function markDirty() {
        dirty = true;
        refreshStatus();
    }

    function paintPalette() {
        var ready = 0;
        palette.forEach(function (button, i) {
            var hasImage = !!button.dataset.version;
            var free = button.dataset.freemark === "true";
            var hasKey = !!button.dataset.correct || free;
            button.classList.toggle("free", free);
            if (hasImage) ready++;
            button.classList.toggle("done", hasImage && hasKey);
            button.classList.toggle("part", (hasImage || hasKey || !!button.dataset.weight || !!button.dataset.time) && !(hasImage && hasKey));
            button.classList.toggle("current", i === index);
            button.classList.toggle("error", !!failed[button.dataset.q]);
        });
        readyLabel.textContent = ready + " / " + palette.length + " images";
    }

    /** 1-5 rounded to the nearest 0.5, or 0 for "not set". */
    function normalizeWeight(value) {
        var number = Number(value);
        if (!Number.isFinite(number) || number < 1 || number > 5) return 0;
        return Math.round(number * 2) / 2;
    }

    /** Traffic light 1 green → 2 lime → 3 amber → 4 orange → 5 red (matches McqExamQuestion.weightColor). */
    function weightColor(weight) {
        if (!weight) return "#94a3b8";
        var hues = [135, 90, 40, 22, 0];
        var position = Math.max(0, Math.min(4, weight - 1));
        var low = Math.floor(position);
        var high = Math.min(4, low + 1);
        return "hsl(" + Math.round(hues[low] + (hues[high] - hues[low]) * (position - low)) + " 80% 40%)";
    }

    function weightName(weight) {
        if (weight <= 1.5) return "Easy";
        if (weight <= 2.5) return "Fairly easy";
        if (weight <= 3.5) return "Medium";
        if (weight <= 4.5) return "Hard";
        return "Very hard";
    }

    function paintWeight(weight, source) {
        weightInput.value = weight ? String(weight) : "";
        weightGroup.style.setProperty("--w", weightColor(weight));
        stars.forEach(function (star, i) {
            var n = i + 1;
            var full = weight >= n;
            var half = !full && weight === n - 0.5;
            star.className = full ? "on" : half ? "half" : "";
            star.querySelector("i").className = half ? "bi bi-star-half" : "bi bi-star-fill";
        });
        if (source !== "number") weightNumber.value = weight ? String(weight) : "";
        if (source !== "range") weightRange.value = String(weight || 1);
        weightRange.classList.toggle("unset", !weight);
        weightClear.hidden = !weight;
        weightLabel.textContent = weight ? weight + " / 5 · " + weightName(weight) : "Not set";
    }

    function isPreview(url) {
        return Object.keys(previews).some(function (q) { return previews[q] === url; });
    }

    function showImage(src) {
        if (objectUrl && !isPreview(objectUrl)) URL.revokeObjectURL(objectUrl);
        objectUrl = null;
        image.hidden = !src;
        dropEmpty.hidden = !!src;
        removeButton.disabled = !src;
        if (src) image.src = src; else image.removeAttribute("src");
    }

    function load(i) {
        index = i;
        var data = current().dataset;
        numberLabel.textContent = data.q;
        pendingFile = null;
        removeImage = false;
        fileInput.value = "";
        showImage(previews[data.q] || (data.version && data.version !== "pending"
            ? "/exam/admin/exams/" + examId + "/questions/" + data.q + "/image?v=" + data.version : null));
        var correct = (data.correct || "").split(",").filter(Boolean);
        form.querySelectorAll('input[name="correct"]').forEach(function (box) { box.checked = correct.indexOf(box.value) >= 0; });
        paintWeight(normalizeWeight(data.weight));
        form.querySelectorAll('input[name="timeSeconds"]').forEach(function (radio) { radio.checked = radio.value === (data.time || ""); });
        syncTimeSelect();
        loadTags(data.q, data);
        subExisting = (data.subimages || "").split(",").filter(Boolean);
        subRemoved = [];
        subPending.forEach(function (item) { URL.revokeObjectURL(item.url); });
        subPending = [];
        renderSubs();
        setFree(data.freemark === "true");
        prev.disabled = index === 0;
        next.innerHTML = index === palette.length - 1
            ? '<i class="bi bi-check2-circle"></i> Save &amp; Finish'
            : 'Save &amp; Next <i class="bi bi-chevron-right"></i>';
        dirty = false;
        refreshStatus();
        paintPalette();
        try { sessionStorage.setItem("pca-qe-" + examId, String(index)); } catch (error) { /* Optional. */ }
    }

    function put(data, key, value) {
        if (value === null || value === undefined || value === "") delete data[key];
        else data[key] = String(value);
    }

    function store(button, saved) {
        var data = button.dataset;
        put(data, "version", saved.imageVersion);
        put(data, "correct", saved.correct);
        put(data, "weight", saved.weight);
        put(data, "time", saved.timeSeconds);
        put(data, "units", (saved.units || []).join("\n"));
        put(data, "levels", (saved.competencyLevels || []).join("\n"));
        put(data, "contents", (saved.contents || []).join("\n"));
        put(data, "outcomes", (saved.learningOutcomes || []).join("\n"));
        put(data, "subimages", (saved.subImageIds || []).join(","));
        put(data, "freemark", saved.freeMark ? "true" : "");
    }

    /**
     * Snapshots the question on screen into a save job and applies it locally straight away,
     * so coming back to this question before the server answers shows what was entered.
     */
    function capture() {
        var button = current();
        var q = button.dataset.q;
        var body = new FormData(form);
        if (pendingFile) body.append("image", pendingFile);
        if (removeImage) body.append("removeImage", "true");
        appendTags(body);
        subPending.forEach(function (item) { body.append("subImages", item.file); });
        subRemoved.forEach(function (id) { body.append("removeSubImages", id); });

        var data = button.dataset;
        put(data, "subimages", subExisting.filter(function (id) { return subRemoved.indexOf(id) < 0; }).join(","));
        put(data, "freemark", freeValue.value === "true" ? "true" : "");
        if (subPending.length) {
            (subPreviews[q] || []).forEach(function (url) { URL.revokeObjectURL(url); });
            subPreviews[q] = subPending.map(function (item) { return item.url; });
        }
        subPending = [];
        subRemoved = [];
        put(data, "correct", Array.from(form.querySelectorAll('input[name="correct"]:checked'))
            .map(function (box) { return box.value; }).join(","));
        put(data, "weight", weightInput.value);
        var time = form.querySelector('input[name="timeSeconds"]:checked');
        put(data, "time", time ? time.value : "");
        tagMap[q] = cloneTags(tagState).filter(function (tag) { return tag.unitId; });
        if (pendingFile) {
            if (previews[q] && previews[q] !== objectUrl) URL.revokeObjectURL(previews[q]);
            previews[q] = objectUrl;
            objectUrl = null;
            if (!data.version) data.version = "pending";
        } else if (removeImage) {
            if (previews[q]) { URL.revokeObjectURL(previews[q]); delete previews[q]; }
            delete data.version;
        }
        pendingFile = null;
        removeImage = false;
        fileInput.value = "";
        dirty = false;
        return { q: q, button: button, body: body };
    }

    function send(job) {
        inFlight++;
        delete failed[job.q];
        refreshStatus();
        paintPalette();
        return fetch("/exam/admin/exams/" + examId + "/questions/" + job.q, { method: "POST", body: job.body })
            .then(function (response) {
                return response.json().catch(function () { return {}; }).then(function (json) {
                    if (!response.ok) throw new Error(json.message || "The server could not save question " + job.q);
                    return json;
                });
            })
            .then(function (result) {
                store(job.button, result.question);
                tagMap[job.q] = result.tags || [];
                if (previews[job.q] && image.getAttribute("src") !== previews[job.q]) {
                    URL.revokeObjectURL(previews[job.q]);
                }
                delete previews[job.q];
                (subPreviews[job.q] || []).forEach(function (url) { URL.revokeObjectURL(url); });
                delete subPreviews[job.q];
                if (current() === job.button && !dirty) {
                    subExisting = (job.button.dataset.subimages || "").split(",").filter(Boolean);
                    renderSubs();
                }
                if (current() === job.button && !dirty) {
                    tagState = cloneTags(tagMap[job.q]);
                    if (!tagState.length) tagState.push(blankTag());
                    renderTags();
                }
            }, function (error) {
                failed[job.q] = true;
                throw error;
            })
            .finally(function () {
                inFlight--;
                refreshStatus();
                paintPalette();
            });
    }

    function enqueue(job) {
        var run = (queues[job.q] || Promise.resolve()).catch(function () {}).then(function () { return send(job); });
        queues[job.q] = run;
        run.catch(function (error) {
            if (queues[job.q] !== run) return; // a newer save of this question replaced it
            if (!window.PcaDialog) return;
            window.PcaDialog.confirm("Question " + job.q + " could not be saved: " + error.message
                + ". Your changes are still on this page.", { title: "Question " + job.q + " not saved", acceptText: "Retry", type: "error" })
                .then(function (retry) { if (retry) enqueue(job); });
        });
        return run;
    }

    /** Resolves once every queued save has finished; rejects if any question is still unsaved. */
    function allSaved() {
        var chains = Object.keys(queues).map(function (q) { return queues[q].catch(function () {}); });
        return Promise.all(chains).then(function () {
            if (Object.keys(failed).length) throw new Error("Some questions are not saved yet");
        });
    }

    function save() {
        if (dirty) enqueue(capture());
        refreshStatus();
        return allSaved();
    }

    function go(i) {
        if (i < 0 || i >= palette.length || i === index) return;
        if (dirty) enqueue(capture());
        load(i);
    }

    // ------------------------------------------------ supporting images
    function subCount() {
        return subExisting.filter(function (id) { return subRemoved.indexOf(id) < 0; }).length + subPending.length
            + (subPreviews[current().dataset.q] || []).length;
    }

    function subThumb(src, label, pending, onRemove) {
        var box = el("div", "qe-sub" + (pending ? " pending" : ""));
        var img = el("img");
        img.src = src;
        img.alt = label;
        box.appendChild(img);
        box.appendChild(el("span", null, label));
        if (onRemove) {
            var x = el("button");
            x.type = "button";
            x.setAttribute("aria-label", "Remove " + label);
            x.innerHTML = '<i class="bi bi-x-lg"></i>';
            x.addEventListener("click", onRemove);
            box.appendChild(x);
        }
        return box;
    }

    function renderSubs() {
        var q = current().dataset.q;
        subList.innerHTML = "";
        var n = 0;
        subExisting.forEach(function (id) {
            if (subRemoved.indexOf(id) >= 0) return;
            n++;
            subList.appendChild(subThumb("/exam/admin/exams/" + examId + "/questions/" + q + "/sub-images/" + id,
                "Image " + n, false, function () {
                    var ask = window.PcaDialog ? window.PcaDialog.confirm("This supporting image will be removed from question " + q + ".",
                        { title: "Remove supporting image?", acceptText: "Remove", type: "danger" }) : Promise.resolve(true);
                    ask.then(function (ok) {
                        if (!ok) return;
                        subRemoved.push(id);
                        markDirty();
                        renderSubs();
                    });
                }));
        });
        (subPreviews[q] || []).forEach(function (url) {
            n++;
            subList.appendChild(subThumb(url, "Image " + n + " · saving", true, null));
        });
        subPending.forEach(function (item, i) {
            n++;
            subList.appendChild(subThumb(item.url, "Image " + n + " · new", true, function () {
                URL.revokeObjectURL(item.url);
                subPending.splice(i, 1);
                renderSubs();
            }));
        });
        if (!n) subList.appendChild(el("div", "qe-sub-empty", "No supporting images. Add a graph or chart only if the question needs one."));
        subAdd.classList.toggle("disabled", subCount() >= MAX_SUBS);
    }

    subFile.addEventListener("change", function () {
        var files = Array.from(subFile.files || []);
        subFile.value = "";
        var room = MAX_SUBS - subCount();
        if (files.length > room) warn("A question can have up to " + MAX_SUBS + " supporting images.", "Too many images");
        files.slice(0, Math.max(0, room)).forEach(function (file) {
            if (!/^image\/(png|jpeg|webp|gif)$/.test(file.type)) { warn("Use a PNG, JPG, WEBP or GIF image.", "Unsupported file"); return; }
            shrink(file).then(function (ready) {
                if (ready.size > MAX_BYTES) { warn("This image is larger than 5 MB.", "Image too large"); return; }
                if (subCount() >= MAX_SUBS) return;
                subPending.push({ file: ready, url: URL.createObjectURL(ready) });
                markDirty();
                renderSubs();
            });
        });
    });

    // ------------------------------------------------ wrong question → free mark
    function setFree(on) {
        freeValue.value = on ? "true" : "false";
        freeButton.classList.toggle("on", on);
        freeButton.setAttribute("aria-pressed", on ? "true" : "false");
        freeButton.querySelector("span").textContent = on ? "Free mark ON: undo" : "Wrong question? Give everyone the mark";
        freeNote.hidden = !on;
    }

    freeButton.addEventListener("click", function () {
        var q = current().dataset.q;
        var turningOn = freeValue.value !== "true";
        var message = turningOn
            ? "Question " + q + " will be treated as a wrong question: every student gets its mark, whatever they answered. Scores update when you save."
            : "Question " + q + " will be marked normally again using its correct answer.";
        var ask = window.PcaDialog ? window.PcaDialog.confirm(message,
            { title: turningOn ? "Give everyone the mark?" : "Remove the free mark?", acceptText: turningOn ? "Give free mark" : "Remove free mark" })
            : Promise.resolve(true);
        ask.then(function (ok) {
            if (!ok) return;
            setFree(turningOn);
            markDirty();
        });
    });

    /** Large photos are re-encoded as JPEG (max 1800px edge) so uploads stay quick. */
    function shrink(file) {
        if (file.size <= SHRINK_ABOVE || file.type === "image/gif") return Promise.resolve(file);
        return new Promise(function (resolve) {
            var url = URL.createObjectURL(file);
            var img = new Image();
            img.onload = function () {
                var scale = Math.min(1, MAX_EDGE / Math.max(img.naturalWidth, img.naturalHeight));
                var canvas = document.createElement("canvas");
                canvas.width = Math.round(img.naturalWidth * scale);
                canvas.height = Math.round(img.naturalHeight * scale);
                var ctx = canvas.getContext("2d");
                ctx.fillStyle = "#fff";
                ctx.fillRect(0, 0, canvas.width, canvas.height);
                ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
                URL.revokeObjectURL(url);
                canvas.toBlob(function (blob) {
                    if (!blob || blob.size >= file.size) return resolve(file);
                    resolve(new File([blob], file.name.replace(/\.\w+$/, "") + ".jpg", { type: "image/jpeg" }));
                }, "image/jpeg", 0.88);
            };
            img.onerror = function () { URL.revokeObjectURL(url); resolve(file); };
            img.src = url;
        });
    }

    function useFile(file) {
        if (!file || !/^image\/(png|jpeg|webp|gif)$/.test(file.type)) {
            warn("Use a PNG, JPG, WEBP or GIF image.", "Unsupported file");
            return;
        }
        shrink(file).then(function (ready) {
            if (ready.size > MAX_BYTES) {
                warn("This image is larger than 5 MB.", "Image too large");
                return;
            }
            pendingFile = ready;
            removeImage = false;
            showImage(null);
            objectUrl = URL.createObjectURL(ready);
            image.src = objectUrl;
            image.hidden = false;
            dropEmpty.hidden = true;
            removeButton.disabled = false;
            markDirty();
        });
    }

    fileInput.addEventListener("change", function () {
        if (fileInput.files && fileInput.files[0]) useFile(fileInput.files[0]);
    });
    drop.addEventListener("click", function () { fileInput.click(); });
    drop.addEventListener("keydown", function (event) {
        if (event.key === "Enter" || event.key === " ") { event.preventDefault(); fileInput.click(); }
    });
    drop.addEventListener("dragover", function (event) { event.preventDefault(); drop.classList.add("dragging"); });
    drop.addEventListener("dragleave", function () { drop.classList.remove("dragging"); });
    drop.addEventListener("drop", function (event) {
        event.preventDefault();
        drop.classList.remove("dragging");
        var file = event.dataTransfer.files && event.dataTransfer.files[0];
        if (file) useFile(file);
    });
    document.addEventListener("paste", function (event) {
        if (event.target.matches && event.target.matches("input[type=text], input:not([type]), textarea")) return;
        var items = Array.from((event.clipboardData && event.clipboardData.items) || []);
        var item = items.find(function (entry) { return entry.kind === "file" && entry.type.indexOf("image/") === 0; });
        if (!item) return;
        event.preventDefault();
        var file = item.getAsFile();
        useFile(new File([file], "question-" + current().dataset.q + ".png", { type: file.type }));
    });
    removeButton.addEventListener("click", function () {
        pendingFile = null;
        fileInput.value = "";
        removeImage = !!current().dataset.version;
        showImage(null);
        markDirty();
    });

    stars.forEach(function (star) {
        star.addEventListener("click", function (event) {
            // Left half of a star gives n - 0.5, right half gives n.
            var rect = star.getBoundingClientRect();
            var n = Number(star.dataset.weight);
            var leftHalf = event.clientX && event.clientX < rect.left + rect.width / 2;
            paintWeight(Math.max(1, leftHalf ? n - 0.5 : n));
            markDirty();
        });
    });
    weightNumber.addEventListener("input", function () {
        var weight = normalizeWeight(weightNumber.value);
        if (weightNumber.value === "" || weight) paintWeight(weight, "number");
    });
    weightNumber.addEventListener("change", function () { paintWeight(normalizeWeight(weightNumber.value)); });
    weightRange.addEventListener("input", function () { paintWeight(normalizeWeight(weightRange.value), "range"); });
    weightClear.addEventListener("click", function () { paintWeight(0); markDirty(); });
    form.addEventListener("input", markDirty);
    form.addEventListener("change", function (event) { if (event.target !== fileInput) markDirty(); });
    form.addEventListener("submit", function (event) { event.preventDefault(); save().catch(function () {}); });

    prev.addEventListener("click", function () { go(index - 1); });
    saveButton.addEventListener("click", function () { save().catch(function () {}); });
    next.addEventListener("click", function () {
        if (index === palette.length - 1) {
            // Instant feedback while the last saves finish, then Manage Exams opens.
            var idle = next.innerHTML;
            var restore = function () { next.disabled = false; next.innerHTML = idle; };
            next.disabled = true;
            next.innerHTML = '<i class="bi bi-arrow-repeat busy-spin"></i> Saving…';
            save().then(function () { finish(restore); }, restore);
        }
        else go(index + 1);
    });

    function postFinish(schedule) {
        next.disabled = true;
        next.innerHTML = '<i class="bi bi-arrow-repeat busy-spin"></i> Opening Manage Exams…';
        var out = document.createElement("form");
        out.method = "post";
        out.action = "/exam/admin/exams/" + examId + "/questions/finish";
        var token = form.querySelector('input[name="_csrf"]');
        [["schedule", schedule ? "true" : "false"], token ? [token.name, token.value] : null].forEach(function (pair) {
            if (!pair) return;
            var input = document.createElement("input");
            input.type = "hidden";
            input.name = pair[0];
            input.value = pair[1];
            out.appendChild(input);
        });
        document.body.appendChild(out);
        out.submit();
    }

    /** All questions need an image and a correct answer before leaving for Manage Exams. */
    function finish(onStay) {
        var missingImage = [];
        var missingKey = [];
        palette.forEach(function (button) {
            if (!button.dataset.version) missingImage.push("Q" + button.dataset.q);
            if (!button.dataset.correct && button.dataset.freemark !== "true") missingKey.push("Q" + button.dataset.q);
        });
        // Images are needed before students can sit the paper; correct answers only before
        // results are released, so they do not block scheduling.
        if (missingImage.length) {
            warn("Upload an image for: " + missingImage.join(", ") + ". Everything else is saved.", "Some questions have no image");
            if (onStay) onStay();
            return;
        }
        if (published || !window.PcaDialog) { postFinish(false); return; }
        var answersNote = missingKey.length
            ? " Correct answers are still missing for " + missingKey.join(", ") + ". You can add them any time before releasing results."
            : "";
        window.PcaDialog.confirm("All " + palette.length + " questions have their images." + answersNote + " Schedule this exam for students now?",
            { title: "All questions saved", acceptText: "Schedule Exam", cancelText: "Keep as Draft" })
            .then(function (approved) { postFinish(approved); });
    }
    palette.forEach(function (button, i) { button.addEventListener("click", function () { go(i); }); });

    if (scheduleForm) scheduleForm.addEventListener("submit", function (event) {
        if (!dirty && !inFlight && !Object.keys(failed).length) return;
        event.preventDefault();
        save().then(function () { HTMLFormElement.prototype.submit.call(scheduleForm); }, function () {});
    });
    window.addEventListener("beforeunload", function (event) {
        if (dirty || inFlight || Object.keys(failed).length) { event.preventDefault(); event.returnValue = ""; }
    });

    function getJson(url) {
        return fetch(url, { headers: { Accept: "application/json" } }).then(function (response) {
            if (!response.ok) throw new Error("Could not load " + url);
            return response.json();
        });
    }

    var start = 0;
    try { start = Number(sessionStorage.getItem("pca-qe-" + examId) || 0); } catch (error) { start = 0; }
    Promise.all([
        getJson("/exam/admin/syllabus/units").catch(function () {
            warn("The syllabus could not be loaded, so unit dropdowns are empty. Reload the page to try again.", "Syllabus not loaded");
            return [];
        }),
        getJson("/exam/admin/exams/" + examId + "/questions/tags").catch(function () { return {}; })
    ]).then(function (loaded) {
        syllabus = loaded[0] || [];
        tagMap = loaded[1] || {};
        load(Number.isFinite(start) && start < palette.length ? start : 0);
    });
})();
