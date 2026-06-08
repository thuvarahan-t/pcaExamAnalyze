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
        form.action = "/teacher/papers/" + d.paper + "/questions/save";
        qnum.value = d.qnum || ("Q" + d.pos);
        marks.value = d.marks || 20;
        desc.value = d.description || "";
        setSection(d.sectionId || "", d.section || "");

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
        cell.addEventListener("click", function (e) { e.preventDefault(); open(cell.dataset); });
    });
    document.getElementById("cm-add-ref").addEventListener("click", function () { addRefRow(); });
    modal.querySelectorAll("[data-close]").forEach(function (b) { b.addEventListener("click", close); });
    modal.addEventListener("click", function (e) { if (e.target === modal) close(); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && !modal.hidden) close(); });

    form.addEventListener("submit", function (e) {
        if (!comboValue.value) {
            e.preventDefault();
            combo.classList.add("open");
            alert("Please choose a section first.");
        }
    });
})();
